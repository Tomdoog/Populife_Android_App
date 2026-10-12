package com.populstay.populife.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.view.WheelTime;
import com.orhanobut.logger.Logger;
import com.populock.manhattan.sdk.BleDevice;
import com.populock.manhattan.sdk.api.PPLock;
import com.populock.manhattan.sdk.callback.PPLockCallback;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populock.manhattan.sdk.entity.LockError;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.util.date.TimePickerDateFormat;
import com.populstay.populife.constant.BleConstant;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.entity.PPLBleSession;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.helper.OfflineLockUploader;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.manhattanlock.MHILockDeleteLock;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.api.TTLockClient;
import com.ttlock.bl.sdk.entity.DeviceInfo;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockData;

import org.greenrobot.eventbus.EventBus;

import java.util.TimeZone;

import androidx.multidex.MultiDex;
import cn.ittiger.player.Config;
import cn.ittiger.player.PlayerManager;
import cn.ittiger.player.factory.MediaPlayerFactory;

/**
 * Created by Jerry
 */

public class MyApplication extends BaseApplication {

	public static final String TAG = "MyApplication";

	/**
	 * TTLockAPI
	 */
//	@SuppressLint("StaticFieldLeak")
	public static TTLockClient mTTLockAPI = TTLockClient.getDefault();
	/**
	 * current used key
	 */
	public static Key CURRENT_KEY = new Key(); // 全局当前正在使用的 key
	/**
	 * bluetooth operation
	 */
	public static PPLBleSession pplBleSession = PPLBleSession.getInstance(LockOperation.GET_BATTERY_LEVEL, null);
	/**
	 * PPLock instance
	 */
	@SuppressLint("StaticFieldLeak")
	public static PPLock sPPLOCK;
	private Activity curActivity;
	/**
	 * PPLockCallback
	 */
	private PPLockCallback mPPLockCallback = new PPLockCallback() {
		@Override
		public void onFoundDevice(BleDevice device) {
			Logger.t("PPLock").e("onFoundDevice");
			String actionType = PeachPreference.getStr(PeachPreference.KEY_LOCK_ACTION_TYPE);
			if (PeachPreference.VAL_LOCK_ACTION_TYPE_INIT.equals(actionType)) {
				Logger.t("PPLock").e("onFoundDevice1");
				// 初始化绑定锁：扫描到设备后，广播已扫描到的设备
				broadcastUpdate(BleConstant.ACTION_BLE_DEVICE, BleConstant.DEVICE, device);
			} else { // 常规操作：判断进行连接
				final String mac = CURRENT_KEY.getLockMac();
				Logger.t("PPLock").e("onFoundDevice2 mac = " + mac +",device.getAddress() = " + device.getAddress());
				if (!StringUtil.isBlank(mac) && mac.equalsIgnoreCase(device.getAddress())) {
					Logger.t("PPLock").e("onFoundDevice3");
					sPPLOCK.stopScan();
					getHandler().postDelayed(new Runnable() {
						@Override
						public void run() {
							Logger.t("PPLock").e("onFoundDevice45");
							sPPLOCK.connect(mac);
						}
					}, 500);
				}
			}
		}

		@Override
		public void onDeviceConnected(BleDevice device) {
            com.populock.manhattan.sdk.service.BleService.sLockBleSession.setTimezoneRawOffset(
                    CURRENT_KEY != null && device.getAddress().equalsIgnoreCase(CURRENT_KEY.getLockMac())
                            ? CURRENT_KEY.getTimezoneRawOffset() : null);
			Log.e("", "connected");
			switch (pplBleSession.getOperation()) {
				case INIT_LOCK_TIME:
                    com.populstay.populife.helper.BindingTimeSync.onMhtConnected(device.getAddress());
                    break;

				case ADD_ADMIN:
					sPPLOCK.initLock(device, PeachPreference.readUserId());
					PeachLogger.e("aaa", "连接成功");
					break;

				case ADMIN_UNLOCK:
					if (CURRENT_KEY != null)
//						if (bleSession.isAdmin())
						Logger.t(BaseApplication.TAG).e("操作unlock sPPLOCK ADMIN_UNLOCK");
						sPPLOCK.adminUnlock(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
//						else
//							sPPLOCK.userUnlock(sCURRENT_KEY.getUserId(), sCURRENT_KEY.getLockId(), sCURRENT_KEY.getKeyId(),
//									sCURRENT_KEY.getKeyType(), sCURRENT_KEY.getStartDate(), sCURRENT_KEY.getEndDate(),
//									StringUtil.isBlank(sCURRENT_KEY.getStartTime()) ? -1 : Integer.parseInt(sCURRENT_KEY.getStartTime().substring(0, 2)),
//									StringUtil.isBlank(sCURRENT_KEY.getEndTime()) ? -1 : Integer.parseInt(sCURRENT_KEY.getEndTime().substring(0, 2)),
//									sCURRENT_KEY.getSendKeyDate());
					break;

				case ADMIN_LOCK:
					if (CURRENT_KEY != null)
//						if (bleSession.isAdmin())
						sPPLOCK.adminLock(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
//						else
//							sPPLOCK.userLock(sCURRENT_KEY.getUserId(), sCURRENT_KEY.getLockId(), sCURRENT_KEY.getKeyId());
					break;

				case DELETE_LOCK:
					if (CURRENT_KEY != null)
						sPPLOCK.deleteLock(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;

				case INIT_LOCK_DELETE:
					// 添加设备失败后的回滚重连：lockId/keyId/k1 都还没下发，只能走免认证的初始化回滚
					sPPLOCK.resetLockAfterInitFail();
					break;

				case GET_BATTERY_LEVEL:
					sPPLOCK.getBatteryLevel(0);
					break;

				case GET_LOCK_TIME:
					sPPLOCK.getLockTime();
					break;

				case SET_LOCK_TIME:
					if (CURRENT_KEY != null)
						sPPLOCK.setLockTime(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), DateUtil.getCurTimeMillis(), CURRENT_KEY.getK1());
					break;

				case GET_OPERATE_LOG:
					if (CURRENT_KEY != null)
						sPPLOCK.getOperateLog(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1(), CURRENT_KEY.getLockName());
					break;

				case GET_AUTO_LOCK_TIME:
					sPPLOCK.getAutoLockTime();
					break;

				case GET_LOCK_STATUS:
					sPPLOCK.getLockStatus();
					break;

				case SET_AUTO_LOCK_TIME:
					if (CURRENT_KEY != null)
						sPPLOCK.setAutoLocking(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getAutoLockTime(), CURRENT_KEY.getK1());
					break;
				case MODIFY_KEYBOARD_PWD:
					if (CURRENT_KEY != null)
						sPPLOCK.modifyUserKeyboardPwd(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()),
								pplBleSession.getKeyboardPwdOriginal(),
								pplBleSession.getKeyboardPwdNew(), pplBleSession.getKeyboardPwdType(), pplBleSession.getStartDate(),
								pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;
				case MODIFY_KEYBOARD_PWD_VALID:
					if (CURRENT_KEY != null)
						sPPLOCK.modifyUserKeyboardPwdPeriod(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()),
								pplBleSession.getKeyboardPwdOriginal(), pplBleSession.getKeyboardPwdType(), pplBleSession.getStartDate(),
								pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;

				case SET_ADMIN_KEYBOARD_PWD:
					if (CURRENT_KEY != null)
						sPPLOCK.setAdminKeyboardPwd(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getNoKeyPwd(), pplBleSession.getPassword(), CURRENT_KEY.getK1());
					break;

				case DELETE_KEYBOARD_PWD:
					if (CURRENT_KEY != null)
						sPPLOCK.deleteKeyboardPwd(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getKeyboardPwdType(), pplBleSession.getKeyboardPwdOriginal(), CURRENT_KEY.getK1());
					break;

				case RESET_EKEY:
					if (CURRENT_KEY != null)
						sPPLOCK.resetEKey(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;

				case RESET_KEYBOARD_PWD:
					if (CURRENT_KEY != null)
						sPPLOCK.resetKeyboardPwd(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;

				case ADD_KEYBOARD_PWD:
					if (CURRENT_KEY != null)
						sPPLOCK.addKeyboardPwd(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getPassword(), pplBleSession.getStartDate(), pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;

				case FIND_MY_DEVICE:
					if (CURRENT_KEY != null)
						sPPLOCK.findMyDevice(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;

				case ADD_FINGERPRINT:
					if (CURRENT_KEY != null)
						sPPLOCK.addFingerprint(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getFingerType(), pplBleSession.getFingerPriority(),
								pplBleSession.getStartDate(), pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;
				case DEL_FINGERPRINT:
					if (CURRENT_KEY != null)
						sPPLOCK.delFingerprint(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getFingerId(), CURRENT_KEY.getK1());
					break;
				case ADD_CARD:
					if (CURRENT_KEY != null)
						sPPLOCK.addCard(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()), String.valueOf(CURRENT_KEY.getKeyId()),
								pplBleSession.getCardType(), pplBleSession.getCardPriority(), pplBleSession.getStartDate(),
								pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;
				case DEL_CARD:
					if (CURRENT_KEY != null)
						sPPLOCK.delCard(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getCardId(), CURRENT_KEY.getK1());
					break;
				case CLEAR_FINGERS:
					if (CURRENT_KEY != null)
						sPPLOCK.clearFingers(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;
				case CLEAR_CARDS:
					if (CURRENT_KEY != null)
						sPPLOCK.clearCards(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), CURRENT_KEY.getK1());
					break;
				case MODIFY_FINGERPRINT_VALID:
					if (CURRENT_KEY != null)
						sPPLOCK.modifyFingerprintPeriod(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getFingerId(), pplBleSession.getFingerType(),
								pplBleSession.getStartDate(), pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;
				case MODIFY_CARD_VALID:
					if (CURRENT_KEY != null)
						sPPLOCK.modifyCardPeriod(CURRENT_KEY.getUserId(), String.valueOf(CURRENT_KEY.getLockId()),
								String.valueOf(CURRENT_KEY.getKeyId()), pplBleSession.getCardId(), pplBleSession.getCardType(),
								pplBleSession.getStartDate(), pplBleSession.getEndDate(), CURRENT_KEY.getK1());
					break;

				default:
					break;
			}
		}

		@Override
		public void onDeviceDisconnected(BleDevice device) {
			stopLoading();
			Log.e("", "Disconnected");
			switch (pplBleSession.getOperation()) {
				case ADMIN_UNLOCK:
					pplBleSession.getmILockUnlock().onUnlockFinish();
					break;

				case ADMIN_LOCK:
					pplBleSession.getmILockLock().onLockFinish();
					break;

				case DELETE_LOCK:
					if (pplBleSession.getmILockDeleteLock() != null) {
						pplBleSession.getmILockDeleteLock().onFinish();
					}
					break;

				default:
					break;
			}
		}

		@Override
		public void onInitLock(BleDevice device, com.populock.manhattan.sdk.entity.LockData lockData, LockError error) {
			stopLoading();
			if (error == LockError.SUCCESS) {
				/*Intent intent = new Intent(getApplication(), LockNameAddActivity.class);
				intent.putExtra(LockNameAddActivity.KEY_LOCK_INIT_DATA, lockDataJson);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);*/
				//AddDeviceSuccessActivity.actionStart(getApplication(), HomeDeviceInfo.IDeviceModel.MODEL_LOCK_DEADBOLT)
				sPPLOCK.stopScan();
				String lockDataJson = lockData.toJson();
                JSONObject timeInfo = JSON.parseObject(lockDataJson);
                timeInfo.put("bindingTimeVerified", false);
                timeInfo.put("bindingTimeZoneId", java.util.TimeZone.getDefault().getID());
                lockDataJson = timeInfo.toJSONString();
				PeachLogger.e("lockData", lockDataJson);
//				Intent intent = new Intent(getApplication(), LockNameAddActivity.class);
//				intent.putExtra(LockNameAddActivity.KEY_LOCK_INIT_DATA, lockDataJson);
//				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//				startActivity(intent);

				// 保存锁信息
				JSONObject lockInfo = JSON.parseObject(lockDataJson);
				String userId = PeachPreference.readUserId();
				String lockMac =lockInfo.getString("lockMac");
				String lockName = lockInfo.getString("lockName");
				boolean saved = PopulifeDBUtil.getInstance(getApplicationContext())
						.save(new OfflineLock(userId, lockMac, lockName, lockDataJson, -1));
				if (!saved) {
					// 这条记录是"锁已被物理初始化"的唯一凭证：丢了就既进不了未同步列表、也补传不了，
					// 锁却已经不在出厂态。落库失败必须留痕，否则线上完全无从排查。
					PeachLogger.e(TAG, "save OfflineLock FAILED! mac=" + lockMac + " user=" + userId);
				}

				EventBus.getDefault().post(new Event(Event.EventType.LOCK_LOCAL_INITIALIZE_SUCCEED, lockDataJson));

			} else {
				EventBus.getDefault().post(new Event(Event.EventType.LOCK_LOCAL_INITIALIZE_FAIL));
			}
		}

		@Override
		public void onDeleteLock(BleDevice device, LockError error) {
			// 回调可能没被设置（如添加设备失败后的回滚，此前只有详情页删锁流程会 set），
			// 直接调用会 NPE；而删本地缓存那步在它之前，一崩就变成"记录已删、锁没重置"的孤儿设备。
			final MHILockDeleteLock callback = pplBleSession.getmILockDeleteLock();
			if (error == LockError.SUCCESS) {
				// 锁重置成功，需要删除缓存
				if (device != null && !StringUtil.isBlank(device.getAddress())) {
					PopulifeDBUtil.getInstance(BaseApplication.getApplication()).deleteByUserAndMac(PeachPreference.readUserId(), device.getAddress());
					PeachLogger.d("onDeleteLock success, clear local " + device.getAddress());
				} else {
					// 拿不到 mac 就删不掉对应记录，宁可留着让它出现在"未同步设备"里
					PeachLogger.e(TAG, "onDeleteLock success but device mac is null, local record kept");
				}
				if (callback != null) {
					callback.onSuccess();
				}
			} else {
				PeachLogger.d("onDeleteLock fail: " + error);
				if (callback != null) {
					callback.onFail();
				}
			}
		}

		@Override
		public void onUnlock(BleDevice device, int battery, LockError error) {
			if (error == LockError.SUCCESS) { // 开锁成功
				pplBleSession.getmILockUnlock().onUnlockSuccess(battery);
			} else { // 开锁失败
				pplBleSession.getmILockUnlock().onUnlockFail();
			}
		}

		@Override
		public void onLock(BleDevice device, int battery, LockError error) {
			if (error == LockError.SUCCESS) { // 闭锁成功
				pplBleSession.getmILockLock().onLockSuccess(battery);
			} else { // 闭锁失败
				pplBleSession.getmILockLock().onLockFail();
			}
		}

		@Override
		public void onGetBatteryLevel(BleDevice device, int batteryLevel, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockGetBattery().onSuccess(batteryLevel);
			} else {
				pplBleSession.getmILockGetBattery().onFail();
			}
		}

		@Override
		public void onGetLockTime(BleDevice device, long lockTime, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockGetTime().onSuccess(lockTime);
			} else {
				pplBleSession.getmILockGetTime().onFail();
			}
		}

		@Override
		public void onGetAutoLockTime(BleDevice device, int seconds, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockGetAutoLockTime().onSuccess(seconds);
			} else {
				pplBleSession.getmILockGetAutoLockTime().onFail();
			}
		}

		@Override
		public void onGetLockStatus(BleDevice device, boolean isLocked, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockGetLockStatus().onSuccess(isLocked);
			} else {
				pplBleSession.getmILockGetLockStatus().onFail();
			}
		}

		@Override
		public void onSetLockTime(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockSetTime().onSuccess();
			} else {
				pplBleSession.getmILockSetTime().onFail();
			}
		}

		@Override
		public void onGetLockOperateLog(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockGetOperateLog().onSuccess();
			} else {
				pplBleSession.getmILockGetOperateLog().onFail();
			}
		}

		@Override
		public void onSetAutoLockTime(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockModifyAutoLockTime().onSuccess();
			} else {
				pplBleSession.getmILockModifyAutoLockTime().onFail();
			}
		}

		@Override
		public void onSetAdminKeyboardPwd(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockSetAdminKeyboardPwd().onSuccess();
			} else {
				pplBleSession.getmILockSetAdminKeyboardPwd().onFail();
			}
		}

		@Override
		public void onDeleteKeyboardPwd(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockDeletePasscode().onSuccess();
			} else {
				pplBleSession.getmILockDeletePasscode().onFail();
			}
		}

		@Override
		public void onResetEKey(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockResetEkey().onSuccess();
			} else {
				pplBleSession.getmILockResetEkey().onFail();
			}
		}

		@Override
		public void onResetKeyboardPwd(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockResetKeyboardPwd().onSuccess();
			} else {
				pplBleSession.getmILockResetKeyboardPwd().onFail();
			}
		}

		@Override
		public void onAddKeyboardPwd(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockAddPasscode().onSuccess();
			} else {
				pplBleSession.getmILockAddPasscode().onFail();
			}
		}

		@Override
		public void onFindMyDevice(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getILockFindMyDevice().onSuccess();
			} else {
				pplBleSession.getILockFindMyDevice().onFail();
			}
		}

		@Override
		public void onEnterAddFingerprint(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockEnterAddFingerprint().onSuccess();
			} else {
				pplBleSession.getmILockEnterAddFingerprint().onFail();
			}
		}

		@Override
		public void onAddFingerprint(int step, String fingerId, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockAddFingerprint().onSuccess(step, fingerId);
			} else {
				pplBleSession.getmILockAddFingerprint().onFail();
			}

		}

		@Override
		public void onAddCard(String cardId, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockAddCard().onSuccess(cardId);
			} else {
				pplBleSession.getmILockAddCard().onFail();
			}
		}


		@Override
		public void onDeleteFingerprint(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockDeleteFingerprint().onSuccess();
			} else {
				pplBleSession.getmILockDeleteFingerprint().onFail();
			}

		}

		@Override
		public void onDeleteCard(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockDeleteCard().onSuccess();
			} else {
				pplBleSession.getmILockDeleteCard().onFail();
			}
		}

		@Override
		public void onClearFingers(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockClearFingers().onSuccess();
			} else {
				pplBleSession.getmILockClearFingers().onFail();
			}
		}

		@Override
		public void onClearCards(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockClearCards().onSuccess();
			} else {
				pplBleSession.getmILockClearCards().onFail();
			}

		}

		@Override
		public void onModifyKeyboardPwd(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockModifyPasscode().onSuccess();
			} else {
				pplBleSession.getmILockModifyPasscode().onFail();
			}
		}

		@Override
		public void onModifyKeyboardPwdPeriod(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockModifyPasscodePeriod().onSuccess();
			} else {
				pplBleSession.getmILockModifyPasscodePeriod().onFail();
			}
		}

		@Override
		public void onModifyFingerprintPeriod(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockModifyFingerPeriod().onSuccess();
			} else {
				pplBleSession.getmILockModifyFingerPeriod().onFail();
			}
		}

		@Override
		public void onModifyCardPeriod(BleDevice device, LockError error) {
			if (error == LockError.SUCCESS) {
				pplBleSession.getmILockModifyCardPeriod().onSuccess();
			} else {
				pplBleSession.getmILockModifyCardPeriod().onFail();
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		MultiDex.install(this);
		WheelTime.dateFormat = new TimePickerDateFormat();
		init();
		initBleLock();
		registerNetworkCallback();
		registerForegroundWatcher();
	}

	/**
	 * 监听 App 回到前台:此时尝试补传未同步设备。
	 * (冷启动首个 Activity 也会触发一次,与 MainActivity 的触发由上传器内的并发锁去重)
	 */
	private void registerForegroundWatcher() {
		registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
			private int mStartedCount = 0;

			@Override
			public void onActivityStarted(Activity activity) {
				if (mStartedCount++ == 0) {
					OfflineLockUploader.getInstance().tryUploadAll("foreground");
					LockRecordUploader.getInstance().retry();
				}
			}

			@Override
			public void onActivityStopped(Activity activity) {
				if (--mStartedCount < 0) {
					mStartedCount = 0;
				}
			}

			@Override
			public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
			}

			@Override
			public void onActivityResumed(Activity activity) {
			}

			@Override
			public void onActivityPaused(Activity activity) {
			}

			@Override
			public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
			}

			@Override
			public void onActivityDestroyed(Activity activity) {
			}
		});
	}

	/**
	 * 注册全局网络监听:网络恢复可用时,自动补传未上传到云端的设备。
	 */
	private void registerNetworkCallback() {
		try {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) {
				return;
			}
			NetworkRequest request = new NetworkRequest.Builder()
					.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
					.build();
			cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
				@Override
				public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
					if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
						LockRecordUploader.getInstance().retry();
					}
				}

				@Override
				public void onAvailable(Network network) {
					super.onAvailable(network);
					OfflineLockUploader.getInstance().tryUploadAll("network");
					LockRecordUploader.getInstance().retry();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void init() {
		initLock();
		initVideoPlayerConfig();
	}

	private void initVideoPlayerConfig() {
		PlayerManager.loadConfig(
				new Config.Builder(this)
						.cache(false)
						.buildPlayerFactory(new MediaPlayerFactory())
						.build()
		);
	}

	/**
	 * Init TTLockAPI Object
	 */
	private void initLock() {
//		mTTLockAPI = new TTLockAPI(getApplication(), mTTLockCallback);
	}
//	/**
//	 * 全局当前正在使用的 key（蓝牙钥匙）
//	 */
//	public static KeyBean sCURRENT_KEY = new KeyBean();

	//TODO:
	private <K, V extends Parcelable> void broadcastUpdate(String action, K key, V value) {
		final Intent intent = new Intent(action);
		if (key != null) {
			Bundle bundle = new Bundle();
			bundle.putParcelable((String) key, value);
			intent.putExtras(bundle);
		}
		sendBroadcast(intent);
	}

	/**
	 * 初始化蓝牙锁相关功能
	 */
	private void initBleLock() {
		// get PPLock instance of SDK
		sPPLOCK = PPLock.getInstance(getApplication(), mPPLockCallback);
	}

	/**
	 * 请求服务器，初始化锁
	 */
	private void initLock(final com.populock.manhattan.sdk.entity.LockData lockData) {
//		RestClient.builder()
//				.url(Urls.LOCK_BLE_BIND)
//				.params("name", lockData.getLockName())
//				.params("mac", lockData.getLockMac())
//				.params("aesKey", lockData.getAesKey())
//				.params("electricQuantity", String.valueOf(lockData.getBatteryLevel()))
//				.params("timezoneRawOffset", String.valueOf(lockData.getTimezoneRawOffset()))
//				.params("protocolVersion", lockData.getProtocolVersion())
//				.params("modelId", lockData.getModelNum())
//				.params("hardwareVersion", lockData.getHardwareVersion())
//				.params("firwareVersion", lockData.getFirmwareVersion())
//				.params("timestamp", String.valueOf(lockData.getTimestamp()))
//				.params("token", PeachPreference.getAccountToken())
//				.success(new ISuccess() {
//					@Override
//					public void onSuccess(String response) {
//						stopLoading();
//						PeachLogger.d("LOCK_BLE_BIND", response);
//
//						JSONObject result = JSON.parseObject(response);
//						int code = result.getInteger("code");
//						if (code == 200) {
//							JSONObject data = result.getJSONObject("data");
//							String mLockId = data.getString("lockId");
//							String k1 = data.getString("k1");
////							mBattery = (int) requestParams.get("electricQuantity");
//							DeviceAddNameBindHomeActivity.actionStart(ActivityCollector.getTopActivity(),
//									Constant.VAL_LOCK_TYPE_BLUETOOTH, mLockId, lockData.getLockName());
//						}
//					}
//				})
//				.failure(new IFailure() {
//					@Override
//					public void onFailure() {
//						PeachLogger.e("fail", "fail");
//						stopLoading();
//						toast(R.string.note_lock_init_fail);
//					}
//				})
//				.error(new IError() {
//					@Override
//					public void onError(int code, String msg) {
//						PeachLogger.e("error", "error");
//						stopLoading();
//						toast(R.string.note_lock_init_fail);
//					}
//				})
//				.build()
//				.post();
	}

}
