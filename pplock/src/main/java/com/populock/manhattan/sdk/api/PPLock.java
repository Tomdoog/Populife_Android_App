package com.populock.manhattan.sdk.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.media.VolumeShaper;
import android.widget.Toast;
//import android.support.annotation.NonNull;
//import android.support.annotation.RequiresPermission;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.orhanobut.logger.Logger;
import com.populock.manhattan.sdk.BleDevice;
import com.populock.manhattan.sdk.callback.AddCardCallback;
import com.populock.manhattan.sdk.callback.AddFingerprintCallback;
import com.populock.manhattan.sdk.callback.AddKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.AdminLockCallback;
import com.populock.manhattan.sdk.callback.AdminUnlockCallback;
import com.populock.manhattan.sdk.callback.AuthVerifyCallback;
import com.populock.manhattan.sdk.callback.ClearCardsCallback;
import com.populock.manhattan.sdk.callback.ClearFingersCallback;
import com.populock.manhattan.sdk.callback.DeleteCardCallback;
import com.populock.manhattan.sdk.callback.DeleteFingerprintCallback;
import com.populock.manhattan.sdk.callback.DeleteKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.DeleteLockCallback;
import com.populock.manhattan.sdk.callback.EnterAddFingerprintCallback;
import com.populock.manhattan.sdk.callback.FindMyDeviceCallback;
import com.populock.manhattan.sdk.callback.GetAutoLockTimeCallback;
import com.populock.manhattan.sdk.callback.GetBatteryLevelCallback;
import com.populock.manhattan.sdk.callback.GetFirmwareVersionCallback;
import com.populock.manhattan.sdk.callback.GetLockInfoCallback;
import com.populock.manhattan.sdk.callback.GetLockOperateLogCallback;
import com.populock.manhattan.sdk.callback.GetLockStatusCallback;
import com.populock.manhattan.sdk.callback.GetLockTimeCallback;
import com.populock.manhattan.sdk.callback.GetLockVersionCallback;
import com.populock.manhattan.sdk.callback.InitLockRequestCallback;
import com.populock.manhattan.sdk.callback.InitLockVerifyCallback;
import com.populock.manhattan.sdk.callback.ModifyCardPeriodCallback;
import com.populock.manhattan.sdk.callback.ModifyFingerprintPeriodCallback;
import com.populock.manhattan.sdk.callback.ModifyUserKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.ModifyUserKeyboardPwdPeriodCallback;
import com.populock.manhattan.sdk.callback.OnStartBleServiceListener;
import com.populock.manhattan.sdk.callback.PPLockCallback;
import com.populock.manhattan.sdk.callback.ResetEkeyCallback;
import com.populock.manhattan.sdk.callback.ResetKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.SetAdminKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.SetAutoLockTimeCallback;
import com.populock.manhattan.sdk.callback.SetLockTimeCallback;
import com.populock.manhattan.sdk.callback.UserLockCallback;
import com.populock.manhattan.sdk.callback.UserUnlockCallback;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populock.manhattan.sdk.entity.LockError;
import com.populock.manhattan.sdk.service.BleService;
import com.populock.manhattan.sdk.util.LogUtil;

/**
 * PPLock SDK entity
 * Created by Jerry
 */
public class PPLock {

	/**
	 * RequestCode to enable Bluetooth
	 */
	public static final int REQUEST_ENABLE_BT = 1;
	public static boolean sIsScan;
	public static OnStartBleServiceListener mOnStartBleServiceListener;
	private static PPLockCallback mPPLockCallback;

	@SuppressLint("StaticFieldLeak")
	private static PPLock sPPLock; // PPLock instance (Singleton)
	private Context mContext;
	private BleService mBleService;
	private int mInitLockRetryCount = 0; // 初始化锁重试次数
	private int mAuthRetryCount = 0; // 身份认证重试次数

	private PPLock(Context context, @NonNull PPLockCallback callback) {
		this.mContext = context;
		mPPLockCallback = callback;
	}

	/**
	 * get PPLock instance of SDK (Singleton)
	 *
	 * @param context  context
	 * @param callback PPLockCallback
	 * @return PPLock
	 */
	public static PPLock getInstance(Context context, @NonNull PPLockCallback callback) {
		if (sPPLock == null) {
			synchronized (PPLock.class) {
				if (sPPLock == null) {
					sPPLock = new PPLock(context, callback);
				}
			}
		}
		return sPPLock;
	}

	public static PPLockCallback getPPLockCallback() {
		return mPPLockCallback;
	}

	public static void setmPPLockCallback(PPLockCallback mPPLockCallback) {
		PPLock.mPPLockCallback = mPPLockCallback;
	}

	public static OnStartBleServiceListener getOnStartBleServiceListener() {
		return mOnStartBleServiceListener;
	}

	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public boolean isBleEnable(Context context) {
		BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
		return adapter != null && adapter.isEnabled();
	}

	/**
	 * Request to enable Bluetooth through the system settings (without stopping your application)
	 *
	 * @param activity the App's current activity
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public void requestBleEnable(Activity activity) {
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}

	/**
	 * start BleService
	 *
	 * @param context context
	 */
	public void startBleService(Context context) {
		this.startBleService(context, null);
	}

	public void startBleService(Context context, OnStartBleServiceListener onStartBleServiceListener) {
		mOnStartBleServiceListener = onStartBleServiceListener;
		Intent intent = new Intent(context, BleService.class);
		context.startService(intent);
	}

	/**
	 * stop BleService
	 *
	 * @param context context
	 */
	public void stopBleService(Context context) {
		Intent intent = new Intent(context, BleService.class);
		LogUtil.d("stop: " + context.stopService(intent));
		this.mBleService = null;
	}

	/**
	 * start scanning Bluetooth devices
	 */
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public void startScan() {
		sIsScan = true;
		mBleService = BleService.getBleService();
		if (mBleService == null) {
			LogUtil.d("start Bluetooth scan after Bluetooth service initialized");
		} else {
			LogUtil.d("start Bluetooth scan");
			mBleService.setScan(true);
			mBleService.startScan();
		}
	}

	/**
	 * stop scanning Bluetooth devices
	 */
	public void stopScan() {
		LogUtil.d("stop scan");
		if (mBleService == null) {
			mBleService = BleService.getBleService();
			LogUtil.d("mBleService = " + mBleService);
		}

		if (mBleService == null) {
			LogUtil.w("mBleService is null");
		} else {
			mBleService.setScan(false);
			mBleService.stopScan();
		}
	}

	/**
	 * check whether the BleDevice is connected or not
	 */
	public boolean isConnected(BleDevice bleDevice) {
		return this.isConnected(bleDevice.getAddress());
	}

	/**
	 * check whether the BleDevice whose Mac is address is connected or not
	 */
	public boolean isConnected(String address) {
		if (mBleService == null) {
			mBleService = BleService.getBleService();
			LogUtil.d("mBleService = " + mBleService);
		}

		if (mBleService == null) {
			LogUtil.w("mBleService is null");
			return false;
		} else {
			return mBleService.isConnected(address);
		}
	}

	@RequiresPermission("android.permission.BLUETOOTH")
	public synchronized void connect(final String address) {
		LogUtil.d("connect...");
		mBleService = BleService.getBleService();
		LogUtil.d("mBleService = " + mBleService);
		if (mBleService == null) {
			if (mContext != null) {
				startBleService(mContext, new OnStartBleServiceListener() {
					public void onStart() {
						mBleService = BleService.getBleService();
						LogUtil.d("service start success: " + mBleService);
						if (mBleService != null) {
							mBleService.connect(address);
						} else {
							LogUtil.w("mBleService is null");
						}
					}
				});
			}
		} else {
			mBleService.setNeedReCon(true);
			mBleService.setConnectCnt(0);
			mBleService.connect(address);
		}
	}

	public synchronized void connect(final BleDevice device) {
		LogUtil.d("connect...");
//		LogUtil.d(Thread.currentThread().toString());
		mBleService = BleService.getBleService();
		LogUtil.d("mBleService = " + mBleService);
		if (mBleService == null) {
			if (mContext != null) {
				startBleService(mContext, new OnStartBleServiceListener() {
					public void onStart() {
						mBleService = BleService.getBleService();
						LogUtil.d("service start success: " + mBleService);
						if (mBleService != null) {
							mBleService.connect(device);
						} else {
							LogUtil.w("mBleService is null");
						}
					}
				});
			}
		} else {
			mBleService.setNeedReCon(true);
			mBleService.setConnectCnt(0);
			mBleService.connect(device);
		}
	}

	public void disconnect() {
		mBleService = BleService.getBleService();
		if (mBleService != null) {
			mBleService.clearTask();
			mBleService.disconnect();
		}
	}

	/**
	 * 初始化锁
	 */
	public void initLock(final BleDevice bleDevice, final String userId) {
		LogUtil.d("bleDevice = " + bleDevice.toString());
		if (!bleDevice.isSettingMode()) {
			LockError lockError = LockError.LOCK_NOT_IN_SETTING_MODE;
			lockError.setLockMac(bleDevice.getAddress());
			mPPLockCallback.onInitLock(bleDevice, null, lockError);
		} else {
			LogUtil.d("initLock");
			BleService.sLockBleSession.setLockOperation(LockOperation.INIT_LOCK_REQUEST);
			BleService.sLockBleSession.setInitLockRequestCallback(new InitLockRequestCallback() {
				@Override
				public void onSuccess(final String C1) {
					initLockVerify(C1);
				}

				@Override
				public void onFail(LockError error) {
					mInitLockRetryCount++;
					if (mInitLockRetryCount < 2) {
						LogUtil.e("首次初始化锁请求失败，尝试二次请求");
						initLock(bleDevice,userId);
					} else {
						LogUtil.e("初始化锁二次请求失败");
						mInitLockRetryCount = 0; // 重置“重试次数”
						disconnect(); // 断开蓝牙连接
						// 进行失败回调
						mPPLockCallback.onInitLock(bleDevice, null, LockError.LOCK_NO_PERMISSION);
					}
				}
			});
			BleService.getBleService().initLockRequest(userId);
		}
	}

	/**
	 * 初始化锁认证
	 */
	private void initLockVerify(final String C1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.INIT_LOCK_VERIFY);
		BleService.sLockBleSession.setInitLockVerifyCallback(new InitLockVerifyCallback() {
			@Override
			public void onSuccess() {
				LogUtil.e("initLockVerify success");
//				mPPLockCallback.onInitLock();
				getBatteryLevel(0);
			}

			@Override
			public void onFail(LockError error) {
				LogUtil.e("initLockVerify fail");
			}
		});
		BleService.getBleService().initLockVerify(C1);
	}

	/**
	 * （管理员）删除锁
	 */
	public void deleteLock(String userId, String lockId, String keyId, String k1) {

		BleService.sLockBleSession.setDeleteLockCallback(new DeleteLockCallback() {
			@Override
			public void onSuccess(BleDevice device) {
				mPPLockCallback.onDeleteLock(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onDeleteLock(mBleService.mBleDevice, error);
				disconnect();
			}
		});

		if((LockOperation.INIT_LOCK_DELETE).equals(BleService.sLockBleSession.getLockOperation())) {

			BleService.sLockBleSession.setLockOperation(LockOperation.DELETE_LOCK);
			BleService.getBleService().adminDeleteLock();

		}else {

			BleService.sLockBleSession.setLockOperation(LockOperation.DELETE_LOCK);
			// 先进行身份认证
			authVerify(true, userId, lockId, keyId,k1);

		}



	}

	/**
	 * 初始化失败回滚：把刚初始化成功、但云端绑定失败的锁恢复出厂态。
	 *
	 * 不能复用 {@link #deleteLock}：那条路要先 authVerify，而 lockId / keyId / k1 都是服务端
	 * 初始化成功才下发的，此刻根本没有，认证必然失败。刚初始化完的这条连接本身就持管理员权限，
	 * 直接下发删除指令即可（与 deleteLock 内部 INIT_LOCK_DELETE 分支同一条路径）。
	 *
	 * 调用前需确保与锁的连接仍在。
	 */
	public void resetLockAfterInitFail() {

		BleService.sLockBleSession.setDeleteLockCallback(new DeleteLockCallback() {
			@Override
			public void onSuccess(BleDevice device) {
				mPPLockCallback.onDeleteLock(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onDeleteLock(mBleService.mBleDevice, error);
				disconnect();
			}
		});

		BleService.sLockBleSession.setLockOperation(LockOperation.DELETE_LOCK);
		BleService.getBleService().adminDeleteLock();
	}

	/**
	 * 查询锁版本
	 */
	public void getLockVersion() {
		if (!(LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
			BleService.sLockBleSession.setLockOperation(LockOperation.GET_LOCK_VERSION);
		}
		BleService.sLockBleSession.setGetLockVersionCallback(new GetLockVersionCallback() {
			@Override
			public void onSuccess(String lockVersion) {
				LogUtil.e("lockVersion: " + lockVersion);
				if ((LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
					getLockInfo();
				}
			}

			@Override
			public void onFail(LockError error) {

			}
		});
		BleService.getBleService().getLockVersion();
	}

	/**
	 * 查询锁信息
	 */
	public void getLockInfo() {
		if (!(LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
			BleService.sLockBleSession.setLockOperation(LockOperation.GET_LOCK_INFO);
		}
		BleService.sLockBleSession.setGetLockInfoCallback(new GetLockInfoCallback() {
			@Override
			public void onSuccess(String lockInfo) {
				LogUtil.e("lockInfo: " + lockInfo);
				//如果不为初始化锁时读取锁信息后不要断开连接，待上传设备信息到服务器后再断开，用于上传锁失败后做删除回滚锁操作
				if (!(LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
					disconnect();
				}
			}

			@Override
			public void onFail(LockError error) {
				disconnect();
			}
		});
		BleService.getBleService().getLockInfo();
	}

	/**
	 * 查询锁电量
	 */
	public void getBatteryLevel(final int isUnlock) {
		if (!(LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
			BleService.sLockBleSession.setLockOperation(LockOperation.GET_BATTERY_LEVEL);
		}
		BleService.sLockBleSession.setGetBatteryLevelCallback(new GetBatteryLevelCallback() {
			@Override
			public void onSuccess(int batteryLevel) {
				LogUtil.e("battery: " + batteryLevel);
				if (isUnlock==1) {
					mPPLockCallback.onUnlock(mBleService.mBleDevice,batteryLevel, LockError.SUCCESS);
					disconnect();
				}else if (isUnlock == 2){
					mPPLockCallback.onLock(mBleService.mBleDevice,batteryLevel, LockError.SUCCESS);
					disconnect();
				}else {
					if ((LockOperation.INIT_LOCK_VERIFY).equals(BleService.sLockBleSession.getLockOperation())) {
						getLockVersion();
					} else if ((LockOperation.GET_BATTERY_LEVEL).equals(BleService.sLockBleSession.getLockOperation())) {
						mPPLockCallback.onGetBatteryLevel(mBleService.mBleDevice, batteryLevel, LockError.SUCCESS);
						disconnect();
					}
				}
			}

			@Override
			public void onFail(LockError error) {
				if ((LockOperation.GET_BATTERY_LEVEL).equals(BleService.sLockBleSession.getLockOperation())) {
					mPPLockCallback.onGetBatteryLevel(mBleService.mBleDevice, -1, LockError.FAIL);
					disconnect();
				}
			}
		});
		BleService.getBleService().getBatteryLevel();
	}

	/**
	 * 查询锁时间
	 */
	public void getLockTime() {
		BleService.sLockBleSession.setLockOperation(LockOperation.GET_LOCK_TIME);
		BleService.sLockBleSession.setGetLockTimeCallback(new GetLockTimeCallback() {
			@Override
			public void onSuccess(long lockTime) {
				mPPLockCallback.onGetLockTime(mBleService.mBleDevice, lockTime, LockError.SUCCESS);
				LogUtil.e("lockTime: " + lockTime);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onGetLockTime(mBleService.mBleDevice, 0, LockError.FAIL);
				disconnect();
			}
		});
		BleService.getBleService().getLockTime();
	}

	/**
	 * 查询锁操作记录
	 */
	public void getOperateLog(String userId, String lockId, String keyId, String k1,String lockName) {
		BleService.sLockBleSession.setLockOperation(LockOperation.GET_OPERATE_LOG);
		BleService.sLockBleSession.setGetLockOperateLogCallback(new GetLockOperateLogCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onGetLockOperateLog(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onGetLockOperateLog(mBleService.mBleDevice, LockError.FAIL);
				disconnect();
			}
		});
		BleService.lockName = lockName;
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 查询锁固件版本
	 */
	public void getFirmwareVersion() {
		BleService.sLockBleSession.setLockOperation(LockOperation.GET_FIRMWARE_VERSION);
		BleService.sLockBleSession.setGetFirmwareVersionCallback(new GetFirmwareVersionCallback() {
			@Override
			public void onSuccess(String firmwareVersion) {
				LogUtil.e("firmwareVersion: " + firmwareVersion);
			}

			@Override
			public void onFail(LockError error) {

			}
		});
		BleService.getBleService().getFirmwareVersion();
	}

	/**
	 * 查询自动上锁时间
	 */
	public void getAutoLockTime() {
		BleService.sLockBleSession.setLockOperation(LockOperation.GET_AUTO_LOCK_TIME);
		BleService.sLockBleSession.setGetAutoLockTimeCallback(new GetAutoLockTimeCallback() {
			@Override
			public void onSuccess(int seconds) {
				mPPLockCallback.onGetAutoLockTime(mBleService.mBleDevice, seconds, LockError.SUCCESS);
				LogUtil.e("autoLockTime: " + seconds);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onGetAutoLockTime(mBleService.mBleDevice, -1, LockError.FAIL);
				disconnect();
			}
		});
		BleService.getBleService().getAutoLockTime();
	}

	/**
	 * 查询上锁状态（上锁/打开）
	 */
	public void getLockStatus() {
		BleService.sLockBleSession.setLockOperation(LockOperation.GET_LOCK_STATUS);
		BleService.sLockBleSession.setGetLockStatusCallback(new GetLockStatusCallback() {
			@Override
			public void onSuccess(boolean isLocked) {
				mPPLockCallback.onGetLockStatus(mBleService.mBleDevice, isLocked, LockError.SUCCESS);
				LogUtil.e("isLocked: " + isLocked);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onGetLockStatus(mBleService.mBleDevice, false, LockError.FAIL);
				disconnect();
			}
		});
		BleService.getBleService().getLockStatus();
	}

	/**
	 * 身份认证
	 */
	private void authVerify(final boolean isAdmin, final String userId, final String lockId, final String keyId, final String k1) {
//		BleService.sLockBleSession.setLockOperation(LockOperation.AUTH_VERIFY);
		BleService.sLockBleSession.setAuthVerifyCallback(new AuthVerifyCallback() {
			@Override
			public void onSuccess() {
				Logger.t("PPLock").e("authVerify onSuccess");
				switch (BleService.sLockBleSession.getLockOperation()) {
					case DELETE_LOCK:
						LogUtil.e("身份认证成功，删除锁");
						BleService.getBleService().adminDeleteLock();
						break;

					case ADMIN_UNLOCK:
						LogUtil.e("身份认证成功，管理员开锁");
						BleService.getBleService().adminUnlock(userId);
						break;

					case ADMIN_LOCK:
						LogUtil.e("身份认证成功，管理员闭锁");
						BleService.getBleService().adminLock(userId);
						break;

					case USER_UNLOCK:
						LogUtil.e("身份认证成功，普通用户开锁");
						BleService.getBleService().userUnlockRequest(userId, lockId, keyId, BleService.sLockBleSession.getKeyType(),
								BleService.sLockBleSession.getStartDate(), BleService.sLockBleSession.getEndDate(),
								BleService.sLockBleSession.getCyclicStartHour(), BleService.sLockBleSession.getCyclicEndHour(),
								BleService.sLockBleSession.getCreateDate());
						break;

					case USER_LOCK:
						LogUtil.e("身份认证成功，普通用户闭锁");
						BleService.getBleService().userLock(userId);
						break;

					case SET_LOCK_TIME:
						LogUtil.e("身份认证成功，设置锁时间");
						BleService.getBleService().setLockTime(BleService.sLockBleSession.getLockTime());
						break;

					case GET_OPERATE_LOG:
						LogUtil.e("身份认证成功，查询锁操作记录");
						BleService.getBleService().getLockOperateLog(lockId, keyId,userId);
						break;

					case SET_AUTO_LOCK_TIME:
						LogUtil.e("身份认证成功，设置自动闭锁");
						BleService.getBleService().setAutoLocking(BleService.sLockBleSession.getAutoLockTime());
						break;

					case SET_ADMIN_KEYBOARD_PWD:
						LogUtil.e("身份认证成功，修改管理员密码");
						BleService.getBleService().setAdminKeyboardPwd(lockId, BleService.sLockBleSession.getOriginalPwd(),
								BleService.sLockBleSession.getNewPwd());
						break;

					case MODIFY_KEYBOARD_PWD:
						LogUtil.e("身份认证成功，修改普通密码");
						BleService.getBleService().modifyKeyboardPwd(lockId, BleService.sLockBleSession.getOriginalPwd(),
								BleService.sLockBleSession.getNewPwd(),BleService.sLockBleSession.getPwdType(),
								BleService.sLockBleSession.getStartDate(),BleService.sLockBleSession.getEndDate());
						break;

					case MODIFY_KEYBOARD_PWD_VALID:
						LogUtil.e("身份认证成功，修改普通密码有效期");
						BleService.getBleService().modifyKeyboardPwdValid(lockId, BleService.sLockBleSession.getOriginalPwd(),
								BleService.sLockBleSession.getNewPwd(),BleService.sLockBleSession.getPwdType(),
								BleService.sLockBleSession.getStartDate(),BleService.sLockBleSession.getEndDate());
						break;

					case DELETE_KEYBOARD_PWD:
						LogUtil.e("身份认证成功，删除键盘密码");
						BleService.getBleService().deleteKeyboardPwd(lockId,BleService.sLockBleSession.getPwdType(),
								BleService.sLockBleSession.getOriginalPwd());
						break;

					case RESET_EKEY:
						LogUtil.e("身份认证成功，重置电子钥匙");
						BleService.getBleService().resetEKey(userId, lockId);
						break;


					case RESET_KEYBOARD_PWD:
						LogUtil.e("身份认证成功，重置键盘密码");
						BleService.getBleService().resetKeyboardPwd(userId, lockId);
						break;

					case ADD_KEYBOARD_PWD:
						LogUtil.e("身份认证成功，添加自定义键盘密码");
						BleService.getBleService().addKeyboardPwd(lockId, BleService.sLockBleSession.getOriginalPwd(),
								BleService.sLockBleSession.getStartDate(), BleService.sLockBleSession.getEndDate());
						break;

					case FIND_MY_DEVICE:
						LogUtil.e("身份认证成功，查找我的设备");
						BleService.getBleService().findMyDevice(1);

						// 调试 DFU
						//mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, LockError.SUCCESS);
						break;

					case ADD_FINGERPRINT:
						LogUtil.e("身份认证成功，添加指纹");
						BleService.getBleService().addFingerprint(lockId, BleService.sLockBleSession.getFingerprintType(),
								BleService.sLockBleSession.getFingerprintPriority(),
								BleService.sLockBleSession.getStartDate(), BleService.sLockBleSession.getEndDate());
						break;

					case DEL_FINGERPRINT:
						LogUtil.e("身份认证成功，删除单个指纹");
						BleService.getBleService().delFingerprint(lockId, BleService.sLockBleSession.getFingerId());
						break;
					case ADD_CARD:
						LogUtil.e("身份认证成功，添加卡片");
						BleService.getBleService().addICCard(lockId, BleService.sLockBleSession.getCardType(),
								BleService.sLockBleSession.getCardPriority(),
								BleService.sLockBleSession.getStartDate(), BleService.sLockBleSession.getEndDate());
						break;

					case DEL_CARD:
						LogUtil.e("身份认证成功，删除单张卡片");
						BleService.getBleService().delICCard(lockId, BleService.sLockBleSession.getCardId());
						break;
					case CLEAR_FINGERS:
						LogUtil.e("身份认证成功，清空所有指纹");
						BleService.getBleService().clearFingers(lockId);
						break;
					case CLEAR_CARDS:
						LogUtil.e("身份认证成功，清空所有卡片");
						BleService.getBleService().clearICCard(lockId);
						break;
					case MODIFY_FINGERPRINT_VALID:
						LogUtil.e("身份认证成功，修改指纹有效期");
						BleService.getBleService().modifyFingerValid(lockId,BleService.sLockBleSession.getFingerId(),
								BleService.sLockBleSession.getFingerprintType(),BleService.sLockBleSession.getStartDate(),
								BleService.sLockBleSession.getEndDate());
						break;
					case MODIFY_CARD_VALID:
						LogUtil.e("身份认证成功，修改卡片有效期");
						BleService.getBleService().modifyCardValid(lockId,BleService.sLockBleSession.getCardId(),
								BleService.sLockBleSession.getCardType(),BleService.sLockBleSession.getStartDate(),
								BleService.sLockBleSession.getEndDate());
						break;
					default:
						break;
				}
			}

			@Override
			public void onFail(LockError error) {
				Logger.t("PPLock").e("authVerify onFail getErrorCode = " + error.getErrorCode() + ", getErrorMsg = " + error.getErrorMsg());
				mAuthRetryCount++;
				if (mAuthRetryCount < 2) {
					LogUtil.e("首次身份认证失败，尝试二次认证");
					authVerify(isAdmin, userId, lockId, keyId, k1);
				} else {
					LogUtil.e("二次身份认证失败");
					mAuthRetryCount = 0; // 重置“重试次数”
					disconnect(); // 断开蓝牙连接
					// 进行失败回调
					switch (BleService.sLockBleSession.getLockOperation()) {
						case DELETE_LOCK:
							mPPLockCallback.onDeleteLock(mBleService.mBleDevice, error);
							break;

						case ADMIN_UNLOCK:
						case USER_UNLOCK:
							mPPLockCallback.onUnlock(mBleService.mBleDevice,0, error);
							break;
						case ADMIN_LOCK:
						case USER_LOCK:
							mPPLockCallback.onLock(mBleService.mBleDevice,0, error);
							break;

						case SET_LOCK_TIME:
							mPPLockCallback.onSetLockTime(mBleService.mBleDevice, error);
							break;

						case GET_OPERATE_LOG:
							mPPLockCallback.onGetLockOperateLog(mBleService.mBleDevice, error);
							break;

						case SET_AUTO_LOCK_TIME:
							mPPLockCallback.onSetAutoLockTime(mBleService.mBleDevice, error);
							break;

						case SET_ADMIN_KEYBOARD_PWD:
							mPPLockCallback.onSetAdminKeyboardPwd(mBleService.mBleDevice, error);
							break;

						case DELETE_KEYBOARD_PWD:
							mPPLockCallback.onDeleteKeyboardPwd(mBleService.mBleDevice, error);
							break;

						case RESET_EKEY:
							mPPLockCallback.onResetEKey(mBleService.mBleDevice, error);
							break;

						case RESET_KEYBOARD_PWD:
							mPPLockCallback.onResetKeyboardPwd(mBleService.mBleDevice, error);
							break;

						case ADD_KEYBOARD_PWD:
							mPPLockCallback.onAddKeyboardPwd(mBleService.mBleDevice, error);
							break;

						case FIND_MY_DEVICE:
							mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, error);
							break;

						case ADD_FINGERPRINT:
//							mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, error);
							break;

						case DEL_FINGERPRINT:
//							mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, error);
							break;

						default:
							break;
					}
				}
			}
		});
		BleService.getBleService().authVerifyRequest(isAdmin, userId, lockId, keyId, k1);
	}

	/**
	 * 管理员开锁
	 */
	public void adminUnlock(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.ADMIN_UNLOCK);
		BleService.sLockBleSession.setAdminUnlockCallback(new AdminUnlockCallback() {
			@Override
			public void onSuccess() {
				Logger.t("PPLock").e("操作unlock sPPLOCK adminUnlock onSuccess");
//				disconnect();
				getBatteryLevel(1);
			}

			@Override
			public void onFail(LockError error) {
				Logger.t("PPLock").e("操作unlock sPPLOCK adminUnlock onFail");
				mPPLockCallback.onUnlock(mBleService.mBleDevice,0, error);
				disconnect();
			}
		});
		Logger.t("PPLock").e("操作unlock sPPLOCK adminUnlock authVerify");
		authVerify(true, userId, lockId, keyId, k1);
	}

	/**
	 * 管理员闭锁
	 */
	public void adminLock(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.ADMIN_LOCK);
		BleService.sLockBleSession.setAdminLockCallback(new AdminLockCallback() {
			@Override
			public void onSuccess() {
				 getBatteryLevel(2);
				//disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onLock(mBleService.mBleDevice,0, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 普通用户开锁
	 *
	 * @param keyType 电子钥匙类型
	 *                限时：1
	 *                永久：2
	 *                单次：3
	 *                循环：4周一循环，5周二循环，6周三循环，7周四循环，8周五循环，9周六循环，10周日循环，11每日循环，12工作日循环，13周末循环
	 */
	public void userUnlock(String userId, String lockId, String keyId, int keyType, long startDate,
						   long endDate, int cyclicStartHour, int cyclicEndHour, long createDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.USER_UNLOCK);
		BleService.sLockBleSession.setKeyType(keyType);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setCyclicStartHour(cyclicStartHour);
		BleService.sLockBleSession.setCyclicEndHour(cyclicEndHour);
		BleService.sLockBleSession.setCreateDate(createDate);
		BleService.sLockBleSession.setUserUnlockCallback(new UserUnlockCallback() {
			@Override
			public void onSuccess() {
	//			mPPLockCallback.onUnlock(mBleService.mBleDevice,, LockError.SUCCESS);
//				disconnect();
    			getBatteryLevel(1);
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onUnlock(mBleService.mBleDevice,0, error);
				disconnect();
			}
		});
		authVerify(false, userId, lockId, keyId,k1);
	}

	/**
	 * 普通用户闭锁
	 */
	public void userLock(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.USER_LOCK);
		BleService.sLockBleSession.setUserLockCallback(new UserLockCallback() {
			@Override
			public void onSuccess() {
				//mPPLockCallback.onLock(mBleService.mBleDevice,, LockError.SUCCESS);
				//disconnect();
				getBatteryLevel(2);
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onLock(mBleService.mBleDevice,0, error);
				disconnect();
			}
		});
		authVerify(false, userId, lockId, keyId,k1);
	}

	/**
	 * 设置锁时间
	 *
	 * @param time 时间毫秒值
	 */
	public void setLockTime(String userId, String lockId, String keyId, long time, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.SET_LOCK_TIME);
		BleService.sLockBleSession.setLockTime(time);
		BleService.sLockBleSession.setSetLockTimeCallback(new SetLockTimeCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onSetLockTime(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onSetLockTime(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 设置自动闭锁
	 *
	 * @param time 时间（秒）
	 */
	public void setAutoLocking(String userId, String lockId, String keyId, int time, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.SET_AUTO_LOCK_TIME);
		BleService.sLockBleSession.setAutoLockTime(time);
		BleService.sLockBleSession.setSetAutoLockTimeCallback(new SetAutoLockTimeCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onSetAutoLockTime(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onSetAutoLockTime(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 修改管理员键盘密码
	 */
	public void setAdminKeyboardPwd(String userId, String lockId, String keyId, String originalPwd, String newPwd, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.SET_ADMIN_KEYBOARD_PWD);
		BleService.sLockBleSession.setOriginalPwd(originalPwd);
		BleService.sLockBleSession.setNewPwd(newPwd);
		BleService.sLockBleSession.setSetAdminKeyboardPwdCallback(new SetAdminKeyboardPwdCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onSetAdminKeyboardPwd(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onSetAdminKeyboardPwd(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 修改普通键盘密码
	 */
	public void modifyUserKeyboardPwd(String userId, String lockId, String keyId, String originalPwd, String newPwd, int pwdType, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.MODIFY_KEYBOARD_PWD);
		BleService.sLockBleSession.setOriginalPwd(originalPwd);
		BleService.sLockBleSession.setNewPwd(newPwd);
		BleService.sLockBleSession.setPwdType(pwdType);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmModifyUserKeyboardPwdCallback(new ModifyUserKeyboardPwdCallback() {

			@Override
			public void onSuccess() {
				mPPLockCallback.onModifyKeyboardPwd(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onModifyKeyboardPwd(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 修改键盘密码有效期
	 */
	public void modifyUserKeyboardPwdPeriod(String userId, String lockId, String keyId, String originalPwd, int pwdType, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.MODIFY_KEYBOARD_PWD_VALID);
		BleService.sLockBleSession.setOriginalPwd(originalPwd);
		//BleService.sLockBleSession.setNewPwd(newPwd);
		BleService.sLockBleSession.setPwdType(pwdType);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmModifyUserKeyboardPwdPeriodCallback(new ModifyUserKeyboardPwdPeriodCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onModifyKeyboardPwdPeriod(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onModifyKeyboardPwdPeriod(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 修改指纹有效期
	 */
	public void modifyFingerprintPeriod(String userId, String lockId, String keyId, String fingerId, String fingerType, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.MODIFY_FINGERPRINT_VALID);
		BleService.sLockBleSession.setFingerId(fingerId);
		BleService.sLockBleSession.setFingerprintType(fingerType);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmModifyFingerprintPeriodCallback(new ModifyFingerprintPeriodCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onModifyFingerprintPeriod(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onModifyFingerprintPeriod(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 修改卡片有效期
	 */
	public void modifyCardPeriod(String userId, String lockId, String keyId, String cardId, String cardType, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.MODIFY_CARD_VALID);
		BleService.sLockBleSession.setCardId(cardId);
		BleService.sLockBleSession.setCardType(cardType);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmModifyCardPeriodCallback(new ModifyCardPeriodCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onModifyCardPeriod(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onModifyCardPeriod(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 删除键盘密码
	 *
	 * @param pwdType 密码类型，1:单次密码，2: 永久密码 3: 循环密码 4: 限时密码 5:管理员密码 6:清空密码
	 */
	public void deleteKeyboardPwd(String userId, String lockId, String keyId, int pwdType, String pwd, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.DELETE_KEYBOARD_PWD);
		BleService.sLockBleSession.setPwdType(pwdType);
		BleService.sLockBleSession.setOriginalPwd(pwd);
		BleService.sLockBleSession.setDeleteKeyboardPwdCallback(new DeleteKeyboardPwdCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onDeleteKeyboardPwd(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onDeleteKeyboardPwd(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 重置电子钥匙
	 */
	public void resetEKey(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.RESET_EKEY);
		BleService.sLockBleSession.setResetEkeyCallback(new ResetEkeyCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onResetEKey(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onResetEKey(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 重置键盘密码
	 */
	public void resetKeyboardPwd(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.RESET_KEYBOARD_PWD);
		BleService.sLockBleSession.setResetKeyboardPwdCallback(new ResetKeyboardPwdCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onResetKeyboardPwd(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onResetKeyboardPwd(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 添加自定义键盘密码（限时密码）
	 */
	public void addKeyboardPwd(String userId, String lockId, String keyId, String pwd, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.ADD_KEYBOARD_PWD);
		BleService.sLockBleSession.setOriginalPwd(pwd);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setAddKeyboardPwdCallback(new AddKeyboardPwdCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onAddKeyboardPwd(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onAddKeyboardPwd(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 添加指纹
	 */
	public void addFingerprint(String userId, String lockId, String keyId, String type, String priority, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.ADD_FINGERPRINT);
		BleService.sLockBleSession.setFingerprintType(type);
		BleService.sLockBleSession.setFingerprintPriority(priority);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmAddFingerprintCallback(new AddFingerprintCallback() {
			@Override
			public void onSuccess(int step, String fingerId) {
				mPPLockCallback.onAddFingerprint(step, fingerId, LockError.SUCCESS);
				if (step == 5) {
					disconnect();
				}
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onAddFingerprint(0,null, error);
				disconnect();

			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}
	/**
	 * 删除单个指纹
	 */
	public void delFingerprint(String userId, String lockId, String keyId, String fingerprintId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.DEL_FINGERPRINT);
		BleService.sLockBleSession.setFingerId(fingerprintId);
		BleService.sLockBleSession.setmDeleteFingerprintCallback(new DeleteFingerprintCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onDeleteFingerprint(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onDeleteFingerprint(mBleService.mBleDevice, error);
				disconnect();

			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}


	/**
	 * 添加ic卡
	 */
	public void addCard(String userId, String lockId, String keyId, String type, String priority, long startDate, long endDate, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.ADD_CARD);
		BleService.sLockBleSession.setCardType(type);
		BleService.sLockBleSession.setCardPriority(priority);
		BleService.sLockBleSession.setStartDate(startDate);
		BleService.sLockBleSession.setEndDate(endDate);
		BleService.sLockBleSession.setmAddCardCallback(new AddCardCallback() {
			@Override
			public void onSuccess(String cardId) {
				mPPLockCallback.onAddCard(cardId, LockError.SUCCESS);
				if (cardId!=null){
					disconnect();
				}
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onAddCard(null, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 删除单张卡片
	 */
	public void delCard(String userId, String lockId, String keyId, String cardId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.DEL_CARD);
		BleService.sLockBleSession.setCardId(cardId);
		BleService.sLockBleSession.setmDeleteCardCallback(new DeleteCardCallback(){
			@Override
			public void onSuccess() {
				mPPLockCallback.onDeleteCard(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onDeleteCard(mBleService.mBleDevice, error);
				disconnect();

			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 清空指纹
	 */
	public void clearFingers(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.CLEAR_FINGERS);
		BleService.sLockBleSession.setmClearFingersCallback(new ClearFingersCallback(){
			@Override
			public void onSuccess() {
				mPPLockCallback.onClearFingers(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onClearFingers(mBleService.mBleDevice, error);
				disconnect();

			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 清空卡片
	 */
	public void clearCards(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.CLEAR_CARDS);
		BleService.sLockBleSession.setmClearCardsCallback(new ClearCardsCallback(){
			@Override
			public void onSuccess() {
				mPPLockCallback.onClearCards(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onClearCards(mBleService.mBleDevice, error);
				disconnect();

			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * 寻找我的设备
	 */
	public void findMyDevice(String userId, String lockId, String keyId, String k1) {
		BleService.sLockBleSession.setLockOperation(LockOperation.FIND_MY_DEVICE);
		BleService.sLockBleSession.setFindMyDeviceCallback(new FindMyDeviceCallback() {
			@Override
			public void onSuccess() {
				mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, LockError.SUCCESS);
				disconnect();
			}

			@Override
			public void onFail(LockError error) {
				mPPLockCallback.onFindMyDevice(mBleService.mBleDevice, error);
				disconnect();
			}
		});
		authVerify(true, userId, lockId, keyId,k1);
	}

	/**
	 * DFU 身份认证
	 */
	public void dfuAuthVerify(String userId, String lockId, String keyId, String k1) {
		authVerify(true, userId, lockId, keyId,k1);
	}
}
