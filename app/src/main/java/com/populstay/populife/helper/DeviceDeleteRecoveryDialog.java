package com.populstay.populife.helper;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import androidx.appcompat.app.AlertDialog;

/**
 * 设备删除异常态的详情页恢复弹窗(见产品《设备删除一致性与异常恢复》第七节)。
 *
 * 两种弹窗,只在用户主动进入对应设备详情时出现,不在首页/后台自动打扰:
 *  A. 状态"物理删除已确认、等待账号移除"(PENDING_ACCOUNT):锁已重置、云端还留着,引导用户完成账号移除;
 *  B. 状态"删除结果待确认"(UNKNOWN/DELETING):不知锁到底删没删,引导用户靠近设备实测。
 *     —— B 的"原钥匙能开则自动恢复"由详情页的开锁成功回调处理(见 LockDetailFragment),
 *        本弹窗只负责"用户确认初始密码能开 → Remove"这条移除路径。
 *
 * Remove 一律要求重新输入 App 密码(原删除准备的授权可能已跨会话失效):
 *   输密码 → lock/user/check 校验 → lock/del/v2 完成账号移除(不重发物理删除)。
 */
public class DeviceDeleteRecoveryDialog {

	private static final String TAG = "DeviceDeleteRecovery";

	private static AlertDialog sDialog;

	public interface OnRemovedListener {
		void onRemoved();
	}

	public static boolean isShowing() {
		return sDialog != null && sDialog.isShowing();
	}

	public static void dismiss() {
		try {
			if (sDialog != null && sDialog.isShowing()) {
				sDialog.dismiss();
			}
		} catch (Exception ignored) {
		}
		sDialog = null;
	}

	/**
	 * @param pendingAccount true=状态A(完成设备移除);false=状态B(确认设备状态)
	 * @param onRemoved      账号移除成功后的回调(详情页据此刷新)
	 */
	public static void show(final Activity activity, final boolean pendingAccount,
							final String userId, final int lockId, final OnRemovedListener onRemoved) {
		if (activity == null || activity.isFinishing()) {
			return;
		}
		if (isShowing()) {
			return;
		}
		String title = activity.getString(pendingAccount ? R.string.delete_dlg_a_title : R.string.delete_dlg_b_title);
		String body = activity.getString(pendingAccount ? R.string.delete_dlg_a_body : R.string.delete_dlg_b_body);

		DialogUtil.showCommonDialog(activity, title, body,
				activity.getString(R.string.delete_dlg_remove),
				activity.getString(R.string.delete_dlg_later),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Remove:先输入 App 密码,再完成账号移除
						promptPasswordThenRemove(activity, userId, lockId, onRemoved);
					}
				},
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Later:保留记录,不移除;下次进详情再提示
					}
				});
	}

	private static void promptPasswordThenRemove(final Activity activity, final String userId,
												 final int lockId, final OnRemovedListener onRemoved) {
		final AlertDialog dialog = new AlertDialog.Builder(activity).create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		window.setContentView(R.layout.dialog_input);
		window.setBackgroundDrawableResource(android.R.color.transparent);
		sDialog = dialog;

		TextView tvTitle = window.findViewById(R.id.tv_dialog_input_title);
		final EditText etInput = window.findViewById(R.id.et_dialog_input_content);
		View btnCancel = window.findViewById(R.id.btn_dialog_input_cancel);
		View btnOk = window.findViewById(R.id.btn_dialog_input_ok);

		tvTitle.setText(R.string.enter_account_passwprd);
		etInput.setHint(R.string.enter_pwd);
		etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		btnCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		btnOk.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String pwd = etInput.getText().toString();
				if (TextUtils.isEmpty(pwd)) {
					Toast.makeText(activity, R.string.enter_pwd, Toast.LENGTH_SHORT).show();
					return;
				}
				dialog.dismiss();
				verifyThenRemove(activity, userId, lockId, pwd, onRemoved);
			}
		});
	}

	/** 删除准备(user/check 验密码)→ 账号移除(del/v2) */
	private static void verifyThenRemove(final Activity activity, final String userId,
										 final int lockId, String password, final OnRemovedListener onRemoved) {
		RestClient.builder()
				.url(Urls.LOCK_USER_CHECK)
				.loader(activity)
				.params("userId", userId)
				.params("lockId", lockId)
				.params("password", password)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						boolean ok = false;
						String msg = null;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							ok = code != null && code == 200;
							msg = result.getString("msg");
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (ok) {
							requestAccountRemove(activity, userId, lockId, onRemoved);
						} else {
							Toast.makeText(activity, StringUtil.isBlank(msg) ? activity.getString(R.string.unsync_fail_business) : msg, Toast.LENGTH_SHORT).show();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						Toast.makeText(activity, R.string.unsync_fail_network, Toast.LENGTH_SHORT).show();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						Toast.makeText(activity, StringUtil.isBlank(msg) ? activity.getString(R.string.unsync_fail_business) : msg, Toast.LENGTH_SHORT).show();
					}
				})
				.build()
				.post();
	}

	private static void requestAccountRemove(final Activity activity, final String userId,
											 final int lockId, final OnRemovedListener onRemoved) {
		RestClient.builder()
				.url(Urls.LOCK_ADMIN_DELETE_V2)
				.loader(activity)
				.params("userId", userId)
				.params("lockId", lockId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d(TAG + " del/v2 " + response);
						boolean ok = false;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							Boolean success = result.getBoolean("success");
							ok = code != null && code == 200 && Boolean.TRUE.equals(success);
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (ok) {
							DeviceDeleteState.clear(userId, lockId);
							int lockNum = PeachPreference.getAccountLockNum(userId);
							PeachPreference.setAccountLockNum(userId, lockNum - 1);
							PeachPreference.setLastUnlockTimeAndType(lockId, 0, 0);
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							Toast.makeText(activity, R.string.delete_removed_success, Toast.LENGTH_SHORT).show();
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_LOCK_SUCCESS));
							if (onRemoved != null) {
								onRemoved.onRemoved();
							}
						} else {
							// 保留记录,下次可重试
							Toast.makeText(activity, R.string.unsync_fail_business, Toast.LENGTH_SHORT).show();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						Toast.makeText(activity, R.string.unsync_fail_network, Toast.LENGTH_SHORT).show();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						Toast.makeText(activity, R.string.unsync_fail_business, Toast.LENGTH_SHORT).show();
					}
				})
				.build()
				.post();
	}
}
