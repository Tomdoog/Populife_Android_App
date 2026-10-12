package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.adapter.OfflineLockListAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.Gateway;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.helper.DeviceDeleteRecoveryDialog;
import com.populstay.populife.helper.OfflineLockUploader;
import com.populstay.populife.helper.UnsyncedDeviceDialog;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public class OfflineLockListActivity extends BaseActivity {

	private static final String KEY_GATEWAY = "key_gateway";

	private ListView mListView;
	private OfflineLockListAdapter mAdapter;
	private List<OfflineLock> mLockList = new ArrayList<>();
	private Gateway mGateway;
	private static String TAG = "OfflineLockListActivity";

	/**
	 * 启动当前 activity
	 */
	public static void actionStart(Context context) {
		Intent intent = new Intent(context, OfflineLockListActivity.class);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_offline_lock_list);

		getIntentData();
		initView();
		initListener();
	}

	private void initListener() {
		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				/*OfflineLock lock = mLockList.get(i);

				if (lock.getLockType() < 0){
					initializeMHTLock(lock.getLockDataJson());
				}else {
					initializeKJXLock(lock.getLockDataJson());
				}*/
			}
		});

		// 在 Activity 中设置适配器后添加
		mAdapter.setOnSyncButtonClickListener(new OfflineLockListAdapter.OnSyncButtonClickListener() {
			@Override
			public void onSyncClick(int position, OfflineLock lock) {
				// 统一走同步入口:与后台自动同步共享并发锁与去重逻辑,避免重复初始化
				showLoading();
				OfflineLockUploader.getInstance().syncOne(lock, new OfflineLockUploader.SyncCallback() {
					@Override
					public void onSuccess(OfflineLock l, JSONObject data) {
						stopLoading();
						toast(R.string.sync_successfully);
						refreshLocalList();
						if (data != null) {
							// 同步成功:进入设备结果/设置页(设名称、选空间、看管理员密码)
							UnsyncedDeviceDialog.openSuccessPage(OfflineLockListActivity.this, l, data);
						}
					}

					@Override
					public void onFail(OfflineLock l, int failType) {
						stopLoading();
						toast(OfflineLockUploader.failureMessage(failType));
					}
				});
			}
		});

	}

	private Key mKey;
	private int mLockId, mKeyId, mBattery;
	private String mLockName;
	private HomeDevice mHomeDevice = new HomeDevice();

	/**
	 * 请求服务器，初始化锁
	 */
	private void initializeKJXLock(final String lockDataJson) {
		final WeakHashMap<String, Object> requestParams = parseLockData(lockDataJson);
		PeachLogger.d(TAG + " initializeLock 开始提交服务器 " + requestParams.toString());
		/*final CustomProgress customProgress = CustomProgress.show(this,
				getString(R.string.note_lock_init_ing), false, null);*/
		showLoading();
		RestClient.builder()
				.url(Urls.LOCK_INIT)
				.params(requestParams)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						//customProgress.cancel();
						stopLoading();
						PeachLogger.d(TAG + " initializeLock response=" + response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							PopulifeDBUtil.getInstance(BaseApplication.getApplication()).deleteByUserAndMac(mKey.getUserId(), mKey.getLockMac());
							PeachLogger.d(TAG + " initializeLock 提交成功 ");
							JSONObject data = result.getJSONObject("data");
							mLockId = data.getInteger("lockId");
							String lockVersion = mKey.getLockVersion();
							JSONObject lockInfo = JSON.parseObject(lockVersion);
							if (null != lockInfo) {
								lockInfo.put("lockId", mLockId);
								mKey.setLockVersion(lockInfo.toJSONString());
							}

							// 免钥匙密码(成功页展示的"管理员密码")必须从云端响应里取:
							// 本地 lockDataJson 中没有该字段,漏取会导致成功页密码为空(与首次绑定流程保持一致)
							if (data.containsKey("noKeyPwd")) {
								mKey.setNoKeyPwd(data.getString("noKeyPwd"));
							}
							mKey.setLockId(mLockId);
							// 这把锁已重新绑到账号上,之前那笔没删干净的记录作废,否则新设备一进详情页就被判成"待账号移除"
							DeviceDeleteRecoveryDialog.onSameLockReAdded(mKey.getUserId(), mLockId, mKey.getLockMac());
							mBattery = (int) requestParams.get("electricQuantity");
							mHomeDevice.setDeviceId(String.valueOf(mLockId));
							mHomeDevice.setName(mLockName);
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							toast(R.string.sync_successfully);
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							AddDeviceSuccessActivity.actionStart(OfflineLockListActivity.this, HomeDeviceInfo.IDeviceName.NAME_LOCK_DEADBOLT, mHomeDevice, mKey);
						} else {
							PeachLogger.d(TAG + " initializeLock 提交失败 ");
							toastBackendError(result, R.string.sync_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLogger.d(TAG + " initializeLock 提交失败 onFailure");
						stopLoading();
						//customProgress.cancel();
						toast(R.string.sync_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLogger.d(TAG + " initializeLock 提交失败 onError");
						stopLoading();
						//customProgress.cancel();
						toast(R.string.sync_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 曼哈顿请求服务器，初始化锁
	 */
	private void initializeMHTLock(final String lockDataJson) {
		final WeakHashMap<String, Object> requestParams = mhParseLockData(lockDataJson);
		PeachLogger.d(TAG + " initializeLock 开始提交服务器 " + requestParams.toString());
		showLoading();
		RestClient.builder()
				.url(Urls.MH_LOCK_INIT)
				.params(requestParams)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						//customProgress.cancel();
						stopLoading();
						PeachLogger.d(TAG + " initializeLock response=" + response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							PeachLogger.d(TAG + " initializeLock 提交成功 ");
							PopulifeDBUtil.getInstance(BaseApplication.getApplication()).deleteByUserAndMac(mKey.getUserId(), mKey.getLockMac());
							JSONObject data = result.getJSONObject("data");
							mLockId = data.getInteger("lockId");
							mKeyId = data.getInteger("keyId");
							String noKeyPwd = data.getString("noKeyPwd");
							String k1 = data.getString("k1");
							String k2 = data.getString("k2");

//							String lockVersion = mKey.getLockVersion();
//							JSONObject lockInfo = JSON.parseObject(lockVersion);
//							if (null != lockInfo) {
//								lockInfo.put("lockId", mLockId);
//								mKey.setLockVersion(lockInfo.toJSONString());
//							}

							mKey.setLockVersion("11111");
							mKey.setLockId(mLockId);
							// 这把锁已重新绑到账号上,之前那笔没删干净的记录作废,否则新设备一进详情页就被判成"待账号移除"
							DeviceDeleteRecoveryDialog.onSameLockReAdded(mKey.getUserId(), mLockId, mKey.getLockMac());
							mKey.setKeyId(mKeyId);
							mKey.setNoKeyPwd(noKeyPwd);
							mKey.setK1(k1);
							mKey.setK2(k2);
							mBattery = (int) requestParams.get("electricQuantity");
							mHomeDevice.setDeviceId(String.valueOf(mLockId));
							mHomeDevice.setName(mLockName);
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							toast(R.string.sync_successfully);
							MyApplication.pplBleSession.setOperation(LockOperation.GET_BATTERY_LEVEL);
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							AddDeviceSuccessActivity.actionStart(OfflineLockListActivity.this, HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3, mHomeDevice, mKey);
						} else {
							PeachLogger.d(TAG + " initializeLock 提交失败 ");
							toastBackendError(result, R.string.sync_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLogger.d(TAG + " initializeLock 提交失败 onFailure");
						stopLoading();
						//customProgress.cancel();
						toast(R.string.sync_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLogger.d(TAG + " initializeLock 提交失败 onError");
						stopLoading();
						//customProgress.cancel();
						toast(R.string.sync_fail);
					}
				})
				.build()
				.post();
	}


	/**
	 * 解析锁的数据
	 */
	private WeakHashMap<String, Object> parseLockData(String lockDataJson) {

		JSONObject lockInfo = JSON.parseObject(lockDataJson);
		final WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("userId", PeachPreference.readUserId());
		String lockName = lockInfo.getString("lockName");

		//todo
		params.put("name", lockName);
		params.put("lockName", lockName);
		mLockName = lockName;
		params.put("mac", lockInfo.getString("lockMac"));
        params.put("lockData", lockInfo.getString("lockData"));
		params.put("key", lockInfo.getString("lockKey"));
		params.put("flagPos", lockInfo.getInteger("lockFlagPos"));
		params.put("aesKey", lockInfo.getString("aesKeyStr"));
		params.put("adminPwd", lockInfo.getString("adminPwd"));
		params.put("noKeyPwd", lockInfo.getString("noKeyPwd"));
		String deletePwd = lockInfo.getString("deletePwd");
		params.put("deletePwd", StringUtil.isBlank(deletePwd) ? "" : deletePwd);
		params.put("pwdInfo", lockInfo.getString("pwdInfo"));
		params.put("timestamp", lockInfo.getString("timestamp"));
		params.put("specialValue", lockInfo.getInteger("specialValue"));
		params.put("electricQuantity", lockInfo.getInteger("electricQuantity"));
		params.put("timezoneRawOffSet", String.valueOf(lockInfo.getInteger("timezoneRawOffset")));
		params.put("modelNum", lockInfo.getString("modelNum"));
		params.put("hardwareRevision", lockInfo.getString("hardwareRevision"));
		params.put("firmwareRevision", lockInfo.getString("firmwareRevision"));
		JSONObject lockVersion = lockInfo.getJSONObject("lockVersion");
		params.put("protocolType", lockVersion.getInteger("protocolType"));
		params.put("protocolVersion", lockVersion.getInteger("protocolVersion"));
		params.put("scene", lockVersion.getInteger("scene"));
		params.put("groupId", lockVersion.getInteger("groupId"));
		params.put("orgId", lockVersion.getInteger("orgId"));


		// 这些数据用于重置锁
		mKey = new Key();
		lockVersion.put("lockId", lockInfo.getInteger("lockId"));
		lockVersion.put("showAdminKbpwdFlag", null);
		lockVersion.put("showAdminKbpwdFlag", null);
		mKey.setLockVersion(lockVersion.toJSONString());
		mKey.setAdminPwd(lockInfo.getString("adminPwd"));
		if (lockInfo.containsKey("noKeyPwd")) {
			mKey.setNoKeyPwd(lockInfo.getString("noKeyPwd"));
		}
		mKey.setLockKey(lockInfo.getString("lockKey"));
		mKey.setLockFlagPos(lockInfo.getInteger("lockFlagPos"));
		mKey.setAesKeyStr(lockInfo.getString("aesKeyStr"));
		mKey.setLockMac(lockInfo.getString("lockMac"));
		mKey.setUserId(PeachPreference.readUserId());
		if (lockInfo.containsKey("keyId")) {
			mKey.setKeyId(lockInfo.getInteger("keyId"));
		}
		if (lockInfo.containsKey("userKeyId")) {
			mKey.setUserKeyId(lockInfo.getInteger("userKeyId"));
		}

		// Retrofit 的 FieldMap 不接受 null 值(否则抛 "Field map contained null value" 走 onFailure),剔除 null 项再提交
		params.values().removeAll(Collections.singleton(null));
		return params;
	}

	/**
	 * 解析锁的数据
	 */
	private WeakHashMap<String, Object> mhParseLockData(String lockDataJson) {

		JSONObject lockInfo = JSON.parseObject(lockDataJson);
		final WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("userId", PeachPreference.readUserId());
		String lockName = lockInfo.getString("lockName");

		//todo
		params.put("name", lockName);
		params.put("lockName", lockName);
		mLockName = lockName;
		params.put("alias", lockName);
		params.put("mac", lockInfo.getString("lockMac"));
		//params.put("key", lockInfo.getString("lockKey"));
		//params.put("flagPos", lockInfo.getInteger("lockFlagPos"));
		params.put("aesKey", lockInfo.getString("aesKey"));
		params.put("timestamp", lockInfo.getString("timestamp"));
		params.put("specialValue", 1230);
		params.put("electricQuantity", lockInfo.getInteger("batteryLevel"));//batteryLevel,electricQuantity
		params.put("timezoneRawOffSet", String.valueOf(lockInfo.getInteger("timezoneRawOffset")));
		params.put("modelNum", lockInfo.getString("modelNum"));
		mHomeDevice.setAlias(lockName);
		mHomeDevice.setModelNum(lockInfo.getString("modelNum"));
		params.put("hardwareRevision", lockInfo.getString("hardwareVersion"));
		params.put("firmwareRevision", lockInfo.getString("firmwareVersion"));//firmwareVersion -> 1.0.0.1108
		params.put("protocolVersion", 1);


		// 这些数据用于重置锁
		mKey = new Key();
//		lockVersion.put("lockId", lockInfo.getInteger("lockId"));
//		lockVersion.put("showAdminKbpwdFlag", null);
//		lockVersion.put("showAdminKbpwdFlag", null);
//		mKey.setLockVersion(lockVersion.toJSONString());
//		mKey.setAdminPwd(lockInfo.getString("adminPwd"));
		mKey.setLockVersion("11111");
		mKey.setAdminPwd("111111");
		if (lockInfo.containsKey("noKeyPwd")) {
			mKey.setNoKeyPwd(lockInfo.getString("noKeyPwd"));
		}
		mKey.setLockKey(lockInfo.getString("aesKey"));
		//mKey.setLockFlagPos(lockInfo.getInteger("lockFlagPos"));
		mKey.setLockFlagPos(1);
		mKey.setAesKeyStr(lockInfo.getString("aesKey"));
		mKey.setLockMac(lockInfo.getString("lockMac"));
		mKey.setUserId(PeachPreference.readUserId());
		if (lockInfo.containsKey("keyId")) {
			mKey.setKeyId(lockInfo.getInteger("keyId"));
		}
		if (lockInfo.containsKey("userKeyId")) {
			mKey.setUserKeyId(lockInfo.getInteger("userKeyId"));
		}
		mKey.setUserKeyId(2222);
		// Retrofit 的 FieldMap 不接受 null 值(否则抛 "Field map contained null value" 走 onFailure),剔除 null 项再提交
		params.values().removeAll(Collections.singleton(null));
		return params;
	}


	/** 同步完成后刷新未同步设备列表(已成功上传的记录会被移除) */
	private void refreshLocalList() {
		mLockList.clear();
		List<OfflineLock> list = PopulifeDBUtil.getInstance(getApplicationContext())
				.queryByUserId(PeachPreference.readUserId());
		if (null != list) {
			mLockList.addAll(list);
		}
		if (null != mAdapter) {
			mAdapter.notifyDataSetChanged();
		}
	}

	private void getIntentData() {
		Intent data = getIntent();
		mGateway = data.getParcelableExtra(KEY_GATEWAY);
		PeachLogger.d(mGateway);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_left_title)).setText(R.string.me_list_item_name_unsynced_devices);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(OfflineLockListActivity.this);
			}
		});


		List<OfflineLock> lockList = PopulifeDBUtil.getInstance(this.getApplicationContext()).queryByUserId(PeachPreference.readUserId());
		if (null != lockList && !lockList.isEmpty()) {
			mLockList.addAll(lockList);
		}

		mListView = findViewById(R.id.lv_gateway_binded_lock_list);
		mAdapter = new OfflineLockListAdapter(this, mLockList);
		mListView.setAdapter(mAdapter);

	}

}
