package com.populstay.populife.helper;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.activity.AddDeviceSuccessActivity;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.string.StringUtil;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

/**
 * 首页「未同步设备」兜底弹窗。
 *
 * 触发时机由首页决定(见 {@link #shouldShow}):自动同步失败后不立即打扰用户,
 * 等用户下次进入首页 / 首页重新可见时再提醒。
 *
 * 行为(按产品规格):
 *  - 立即同步:走统一同步入口,期间禁用按钮防重复点击;
 *      成功 → 关闭弹窗并进入设备结果/设置页(可设名称、选空间、看管理员密码);
 *      失败 → 弹窗不关闭,在弹窗内展示红色错误文字(不使用 Toast)。
 *  - 稍后:关闭弹窗,保留本地记录,不影响后台自动同步。
 *  - 勾选"不再提醒":仅屏蔽该设备的弹窗,Me 页入口与后台自动同步照常。
 */
public class UnsyncedDeviceDialog {

	private static final String TAG = "UnsyncedDeviceDialog";

	private static AlertDialog sDialog;

	private UnsyncedDeviceDialog() {
	}

	/**
	 * 判断该未同步设备是否需要弹窗提醒。
	 *
	 * 判据是"设备仍未同步",而非"上次同步失败过"—— 记录仍留在未同步表里,本身就说明
	 * 它还没成功同步到账号(补传成功会立即删除记录)。
	 * 注意不能用 hasSyncFailed 作为前提:断网时会直接跳过同步、根本不会产生失败标记,
	 * 那样最常见的断网场景将永远不弹窗。
	 * 失败标记只用于决定弹窗内展示哪种错误文案,不作为是否提醒的条件。
	 */
	public static boolean shouldShow(OfflineLock lock) {
		if (lock == null) {
			return false;
		}
		return !OfflineLockUploader.isDoNotRemind(lock.getUserId(), lock.getLockMac());
	}

	/** 是否已有弹窗在展示,避免重复弹 */
	public static boolean isShowing() {
		return sDialog != null && sDialog.isShowing();
	}

	public static void show(final Activity activity, final OfflineLock lock) {
		if (activity == null || activity.isFinishing() || lock == null) {
			return;
		}
		if (isShowing()) {
			return;
		}

		View view = LayoutInflater.from(activity).inflate(R.layout.dialog_unsynced_device, null);
		final TextView tvError = view.findViewById(R.id.tv_unsync_error);
		final CheckBox cbNoRemind = view.findViewById(R.id.cb_unsync_no_remind);
		final AppCompatButton btnLater = view.findViewById(R.id.btn_unsync_later);
		final AppCompatButton btnSync = view.findViewById(R.id.btn_unsync_sync);

		final AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}
		sDialog = dialog;

		btnLater.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applyNoRemind(cbNoRemind, lock);
				dismiss();
			}
		});

		btnSync.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// 进入同步中状态,避免重复点击
				btnSync.setEnabled(false);
				btnLater.setEnabled(false);
				tvError.setVisibility(View.GONE);

				OfflineLockUploader.getInstance().syncOne(lock, new OfflineLockUploader.SyncCallback() {
					@Override
					public void onSuccess(OfflineLock l, JSONObject data) {
						applyNoRemind(cbNoRemind, l);
						dismiss();
						if (data != null) {
							openSuccessPage(activity, l, data);
						} else {
							// data 为空表示云端已存在(去重命中),本地残留已清理,刷新首页即可
							PeachLogger.d(TAG + " already in cloud, no success page " + l.getLockMac());
						}
					}

					@Override
					public void onFail(OfflineLock l, int failType) {
						if (!dialog.isShowing()) {
							return;
						}
						btnSync.setEnabled(true);
						btnLater.setEnabled(true);
						tvError.setText(failType == OfflineLockUploader.FAIL_NETWORK
								? R.string.unsync_fail_network
								: R.string.unsync_fail_business);
						tvError.setVisibility(View.VISIBLE);
					}
				});
			}
		});

		dialog.show();
	}

	private static void applyNoRemind(CheckBox cb, OfflineLock lock) {
		if (cb != null && cb.isChecked() && lock != null) {
			OfflineLockUploader.setDoNotRemind(lock.getUserId(), lock.getLockMac(), true);
			PeachLogger.d(TAG + " do-not-remind set for " + lock.getLockMac());
		}
	}

	public static void dismiss() {
		try {
			if (sDialog != null && sDialog.isShowing()) {
				sDialog.dismiss();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		sDialog = null;
	}

	/**
	 * 同步成功后进入设备结果/设置页。
	 *
	 * 注意:结果页不只是展示,用户还能在页面内直接改管理员密码 —— 那要通过蓝牙操作锁,
	 * 依赖 lockData + lockMac(科技侠)或 userId/keyId/k1(曼哈顿)。
	 * 因此这里必须构造完整的 Key,只塞 lockId 与密码会导致"Modify code failed"。
	 */
	public static void openSuccessPage(Activity activity, OfflineLock lock, JSONObject data) {
		try {
			Key key = buildKey(lock, data);

			HomeDevice device = new HomeDevice();
			device.setDeviceId(key.getLockId() > 0 ? String.valueOf(key.getLockId()) : "");
			device.setName(lock.getLockName());

			String deviceType = lock.getLockType() < 0
					? HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3
					: HomeDeviceInfo.IDeviceName.NAME_LOCK_DEADBOLT;

			AddDeviceSuccessActivity.actionStart(activity, deviceType, device, key);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 组装结果页所需的完整 Key:
	 *  - 蓝牙密钥(lockData / lockMac / aesKey / adminPwd 等)以本地记录为准,云端缺失时兜底;
	 *  - 账号侧标识(lockId / keyId / noKeyPwd / k1 / k2)以云端返回为准。
	 *
	 * 云端 data 有两种来源、字段名不同,故逐个字段做兼容:
	 *  - lock/init/v3 上传成功 → lockMac / lockKey / lockFlagPos / aesKeyStr / lockVersion(对象)
	 *  - lock/get 去重命中   → mac / key / flagPos / aesKey / lockData
	 */
	private static Key buildKey(OfflineLock lock, JSONObject data) {
		JSONObject local = null;
		try {
			local = JSON.parseObject(lock.getLockDataJson());
		} catch (Exception e) {
			e.printStackTrace();
		}

		Key key = new Key();
		key.setUserId(lock.getUserId());
		key.setLockName(lock.getLockName());
		key.setLockAlias(lock.getLockName());

		// mac:本地记录一定有,最可靠
		key.setLockMac(firstNonBlank(lock.getLockMac(), str(data, "lockMac"), str(data, "mac")));
		// lockData:科技侠改密码必需
		key.setLockData(firstNonBlank(str(local, "lockData"), str(data, "lockData")));
		key.setAesKeyStr(firstNonBlank(str(local, "aesKeyStr"), str(data, "aesKeyStr"), str(data, "aesKey")));
		key.setLockKey(firstNonBlank(str(local, "lockKey"), str(data, "lockKey"), str(data, "key")));
		key.setAdminPwd(firstNonBlank(str(local, "adminPwd"), str(data, "adminPwd")));
		key.setPwdInfo(firstNonBlank(str(local, "pwdInfo"), str(data, "pwdInfo")));
		key.setDeletePwd(firstNonBlank(str(local, "deletePwd"), str(data, "deletePwd")));
		key.setLockFlagPos(intOf(num(local, "lockFlagPos"), num(data, "lockFlagPos"), num(data, "flagPos")));

		// 账号侧标识以云端为准
		key.setLockId(intOf(num(data, "lockId")));
		key.setKeyId(intOf(num(data, "keyId")));
		key.setNoKeyPwd(firstNonBlank(str(data, "noKeyPwd"), str(local, "noKeyPwd")));
		key.setK1(str(data, "k1"));
		key.setK2(str(data, "k2"));

		// lockVersion:init 返回是对象;lock/get 是平铺字段;都没有时退回本地
		String lockVersion = null;
		try {
			JSONObject fromInit = data == null ? null : data.getJSONObject("lockVersion");
			if (fromInit != null) {
				lockVersion = fromInit.toJSONString();
			} else if (data != null && data.containsKey("protocolType")) {
				JSONObject v = new JSONObject();
				v.put("protocolType", data.getInteger("protocolType"));
				v.put("protocolVersion", data.getInteger("protocolVersion"));
				v.put("scene", data.getInteger("scene"));
				v.put("groupId", data.getInteger("groupId"));
				v.put("orgId", data.getInteger("orgId"));
				lockVersion = v.toJSONString();
			} else if (local != null && local.getJSONObject("lockVersion") != null) {
				lockVersion = local.getJSONObject("lockVersion").toJSONString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!StringUtil.isBlank(lockVersion)) {
			key.setLockVersion(lockVersion);
		}

		PeachLogger.d(TAG + " buildKey mac=" + key.getLockMac() + " lockId=" + key.getLockId()
				+ " hasLockData=" + !StringUtil.isBlank(key.getLockData()));
		return key;
	}

	private static String str(JSONObject o, String k) {
		return o == null ? null : o.getString(k);
	}

	private static Integer num(JSONObject o, String k) {
		try {
			return o == null ? null : o.getInteger(k);
		} catch (Exception e) {
			return null;
		}
	}

	private static String firstNonBlank(String... values) {
		for (String v : values) {
			if (!StringUtil.isBlank(v)) {
				return v;
			}
		}
		return null;
	}

	private static int intOf(Integer... values) {
		for (Integer v : values) {
			if (v != null) {
				return v;
			}
		}
		return 0;
	}
}
