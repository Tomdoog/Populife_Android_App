package com.populstay.populife.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.BleDevice;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.adapter.FoundDeviceAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.constant.BleConstant;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.api.ExtendedBluetoothDevice;
import com.ttlock.bl.sdk.callback.InitLockCallback;
import com.ttlock.bl.sdk.callback.ScanLockCallback;
import com.ttlock.bl.sdk.entity.LockData;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class FoundDeviceActivity extends BaseActivity implements AdapterView.OnItemClickListener, View.OnClickListener {

	public static final String TAG = FoundDeviceActivity.class.getSimpleName();
	public static final int SCAN_TIME_OUT_SECONDS = 10 * 1000;
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	TextView tvPageTitle;
	private List<ExtendedBluetoothDevice> mKJXLockList = new ArrayList<>();
	private List<BleDevice> mMHTLockList = new ArrayList<>();
	private List<Object> mAllLockList = new ArrayList<>();
	private ListView mListView;
	private LinearLayout ll_found_device;
	private ImageView mIvNewMsg;
	private LinearLayout mLlFoundDeviceView;
	private SeekBar mSeekbarScanDevice;
	private TextView mTvScanDevice, mTv_tap_help;
	private TextView mKeepTheKeypadLight;
	private FoundDeviceAdapter mAdapter;
	private AlertDialog DIALOG;
	private int mCurrentScanProgress = 0;
	private String mLockType;
	private boolean isMHTLock = false;
	private Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			Log.e("mhs", "mRunnable--mCurrentScanProgress=" + mCurrentScanProgress);
			if (mCurrentScanProgress >= SCAN_TIME_OUT_SECONDS) {
				showNoResultDialog();
			} else {
				upDateSeekbarScanDevice();
			}
		}
	};
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BleConstant.ACTION_BLE_DEVICE.equals(action)) {
				setTitleText(false);
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					Object obj = bundle.getParcelable(BleConstant.DEVICE);
					if (obj instanceof BleDevice) {
						//mAdapter = new FoundDeviceAdapter(FoundDeviceActivity.this,mMHLockList,mLockList,true);
						BleDevice device = (BleDevice) obj;
						PeachLogger.d("device=" + device.toString());
						mAdapter.setMHTState(true);
						mAdapter.UpdateMHTDevice(device);
					} else {
                        return;
                    }

					mListView.setAdapter(mAdapter);
//					if (isMHLock) {
//						BleDevice device = bundle.getParcelable(BleConstant.DEVICE);
//						PeachLogger.d("device=" + device.toString());
//						mAdapter.mhUpdateDevice(device);
//					}else {
//						ExtendedBluetoothDevice device = bundle.getParcelable(BleConstant.DEVICE);
//						PeachLogger.d("device=" + device.toString());
//						mAdapter.updateDevice(device);
//					}
					BaseApplication.getHandler().removeCallbacks(mRunnable);
					ll_found_device.setVisibility(View.VISIBLE);
					mLlFoundDeviceView.setVisibility(View.GONE);
					if (DIALOG != null) {
						DIALOG.cancel();
					}
				}
			}
		}
	};

	public static void actionStart(Context context, String lockType) {
		Intent intent = new Intent(context, FoundDeviceActivity.class);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);

	}

	private void showNoResultDialog() {
		if (isFinishing()) {
			return;
		}
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_input);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			((TextView) window.findViewById(R.id.tv_dialog_input_title)).setText(R.string.search_time_out);
			window.findViewById(R.id.et_dialog_input_content).setVisibility(View.GONE);
			TextView tvDialogContent = window.findViewById(R.id.tv_dialog_content);
			tvDialogContent.setVisibility(View.VISIBLE);
			if (HomeDeviceInfo.isPadlock(mLockType)) {
				tvDialogContent.setText(R.string.padlock_not_found);
			} else if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
				tvDialogContent.setText(R.string.deadbolt_not_found_try_again);
			} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
				tvDialogContent.setText(R.string.keybox_not_found_try_again);
			} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
				tvDialogContent.setText(R.string.keybox_not_found_try_again_k4);
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				tvDialogContent.setText(R.string.moonlock_not_found_try_again);
			} else {
				tvDialogContent.setText(R.string.door_lock_not_found_try_again);
			}
			window.findViewById(R.id.btn_dialog_input_cancel).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					LockAddGuideActivity.actionStartByTaskTop(FoundDeviceActivity.this, mLockType);
				}
			});
			TextView okBtn = window.findViewById(R.id.btn_dialog_input_ok);
			okBtn.setText(R.string.scan_again);
			okBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					DIALOG.cancel();
					initSeekbarScanDevice();
				}
			});
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_found_device);
		getIntentData();
		initView();
	}

	private void initView() {
		ll_found_device = findViewById(R.id.ll_found_device);
		mLlFoundDeviceView = findViewById(R.id.ll_found_device_view);
		mListView = findViewById(R.id.lv_found_device);
		mSeekbarScanDevice = findViewById(R.id.seekbar_scan_device);
		mTvScanDevice = findViewById(R.id.tv_scan_device);
		mTv_tap_help = findViewById(R.id.tv_tap_help);
		mTv_tap_help.setOnClickListener(this);
		mKeepTheKeypadLight = findViewById(R.id.keep_the_keypad_light);
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			mTvScanDevice.setText(R.string.padlock_scan_wait);
		} else if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
			mTvScanDevice.setText(getResources().getString(R.string.scanning_nearby_deadbolt_please_wait));
		} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
			mTvScanDevice.setText(getResources().getString(R.string.scanning_nearby_keybox_please_wait));
		} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
			mTvScanDevice.setText(getResources().getString(R.string.scanning_nearby_keybox_please_wait_k4));
		} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			mTvScanDevice.setText(getResources().getString(R.string.scanning_nearby_moonlock_please_wait));
		} else {
			mTvScanDevice.setText(getResources().getString(R.string.scanning_nearby_door_lock_please_wait));
		}
		mSeekbarScanDevice.setOnTouchListener((v, event) -> {
			// 返回true，禁止手动拖动进度值
			return true;
		});
//		if (isMHLock) {
//			mAdapter = new FoundDeviceAdapter(this,mMHLockList,mLockList,true);
//		}else {
//			mAdapter = new FoundDeviceAdapter(this,mMHLockList, mLockList,false);
//		}

		mAdapter = new FoundDeviceAdapter(FoundDeviceActivity.this, mMHTLockList, mKJXLockList);
		mListView.setOnItemClickListener(this);
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
			registerReceiver(mReceiver, getIntentFilter(),RECEIVER_EXPORTED);
		}else {
			registerReceiver(mReceiver, getIntentFilter());
		}
		//It needs location permission to start bluetooth scan,or it can not scan device
		requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
						: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
				new PermissionListener() {
					@Override
					public void onGranted() {
//						if (isMHLock) {
//							PeachPreference.putStr(PeachPreference.KEY_LOCK_ACTION_TYPE,
//									PeachPreference.VAL_LOCK_ACTION_TYPE_INIT);
//							pplStartScan();
//						}else {
//							//开启蓝牙扫描
//							startScan();
//						}

						PeachPreference.putStr(PeachPreference.KEY_LOCK_ACTION_TYPE,
								PeachPreference.VAL_LOCK_ACTION_TYPE_INIT);
						pplStartScan();

						//开启蓝牙扫描
						startScan();
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {
						toast(isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
					}
				});
		tvPageTitle = findViewById(R.id.page_left_title);
		tvPageTitle.setVisibility(View.VISIBLE);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		setTitleText(true);
		initProgressStatus();
		initTitleBarRightBtn();
	}

	private void setTitleText(boolean isInit) {
		if (isInit) {
			mKeepTheKeypadLight.setVisibility(View.GONE);
			if (HomeDeviceInfo.isPadlock(mLockType)) {
				tvPageTitle.setText(R.string.padlock_scan);
			} else if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
				tvPageTitle.setText(getResources().getString(R.string.scanning_nearby_deadbolt));
			} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
				tvPageTitle.setText(getResources().getString(R.string.scanning_nearby_keybox));
			} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
				tvPageTitle.setText(getResources().getString(R.string.scanning_nearby_keybox_k4));
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				tvPageTitle.setText(getResources().getString(R.string.scanning_nearby_moon_lock));
			} else {
				tvPageTitle.setText(getResources().getString(R.string.scanning_nearby_door_lock));
			}
		} else {
			mKeepTheKeypadLight.setVisibility(View.VISIBLE);
			if (HomeDeviceInfo.isPadlock(mLockType)) {
				mKeepTheKeypadLight.setText(R.string.padlock_wake_hint);
			}
			tvPageTitle.setText(getResources().getString(R.string.select_a_device));
		}
	}

	private void initProgressStatus() {
		ImageView ivStatus1 = findViewById(R.id.iv_status_1);
		ImageView ivStatus2 = findViewById(R.id.iv_status_2);
		ImageView ivStatus3 = findViewById(R.id.iv_status_3);
		View line1 = findViewById(R.id.line_1);
		View line2 = findViewById(R.id.line_2);

		ivStatus1.setImageResource(R.drawable.status_pre_check_red);
		ivStatus2.setImageResource(R.drawable.status_pre_check_red);
		ivStatus3.setImageResource(R.drawable.status_pair_red);
		line1.setBackgroundColor(getResources().getColor(R.color.edit_focus_line));
		line2.setBackgroundColor(getResources().getColor(R.color.edit_focus_line));
	}

	private void initTitleBarRightBtn() {
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(FoundDeviceActivity.this);
			}
		});
	}

	private void upDateSeekbarScanDevice() {
		mCurrentScanProgress += 500;
		mSeekbarScanDevice.setProgress(mCurrentScanProgress);
		BaseApplication.getHandler().postDelayed(mRunnable, 500);
	}

	private void initSeekbarScanDevice() {
		mSeekbarScanDevice.setMax(SCAN_TIME_OUT_SECONDS);
		mCurrentScanProgress = 0;
		upDateSeekbarScanDevice();
	}

	private IntentFilter getIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BleConstant.ACTION_BLE_DEVICE);
		intentFilter.addAction(BleConstant.ACTION_BLE_DISCONNECTED);
		return intentFilter;
	}

	@Override
	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (!mAdapter.canAdd(position)) {
			// 设备已被初始化：绑在别人账号下，或上一次添加中断后没能回滚。
			// 以前这里是靠 Adapter.isEnabled() 屏蔽点击的，用户点了一点反馈都没有。
			PeachLogger.d(TAG + " 点击了已初始化的设备，不可添加");
			toast(R.string.device_already_bound);
			return;
		}
		if (isBleNetEnableWithToast()) {
			final Object obj = mAdapter.getItem(position);
			PeachLogger.d(TAG + " 点击锁头，开始初始化");
			if (isAndroid12()) {
				requestRuntimePermissions(PERMISSION_BLE_SCAN_CONNECT, new PermissionListener() {
					@Override
					public void onGranted() {
						startConnect(obj);
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {
						toast(R.string.note_permission_ble_scan_connect);
					}
				});
			} else {
				startConnect(obj);
			}
		}
	}

	private void startConnect(Object obj) {
        final long bindingTime = System.currentTimeMillis();
        final java.util.TimeZone bindingZone = java.util.TimeZone.getDefault();
        if (obj instanceof BleDevice) {
            try { com.populock.manhattan.sdk.util.LockTimeZone.encode(bindingZone.getOffset(bindingTime)); }
            catch (IllegalArgumentException e) { toast(R.string.binding_timezone_unsupported); return; }
        }
        showLoading();
		if (obj instanceof BleDevice) {//曼哈顿锁走曼哈顿SDK连接方法
			MyApplication.pplBleSession.setOperation(LockOperation.ADD_ADMIN);
			sPPLOCK.connect((BleDevice) obj);
			isMHTLock = true;
			// 添加锁时，在 LockAddSelectTypeActivity 引导页里，锁盒类型统一被初始化为 NAME_LOCK_KEY_BOX（无法区分 KJX or MHT）
			if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX)) {
				// 此时可以判断，该锁盒为 MHT 锁盒，将锁类型赋值为 MHT 锁盒
				mLockType = HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3;
			}
		} else {
            isMHTLock = false;
            ExtendedBluetoothDevice device = (ExtendedBluetoothDevice) obj;
			mTTLockAPI.initLock(device, new InitLockCallback() {
                @Override
                public void onInitLockSuccess(String s) {
                    LockData lockData = com.ttlock.bl.sdk.util.EncryptionUtil.parseLockData(s);
                    if (lockData == null) {
                        EventBus.getDefault().post(new Event(Event.EventType.LOCK_LOCAL_INITIALIZE_FAIL));
                        return;
                    }
                    lockData.setTimezoneRawOffset(bindingZone.getOffset(bindingTime));
                    mTTLockAPI.stopScanLock();
                    // 保存锁信息
                    String userId = PeachPreference.readUserId();
                    String lockMac = device.getAddress();
                    String lockName = device.getName();
                    lockData.setLockMac(lockMac);
                    lockData.setLockName(lockName);
                    String defaultLockDataJson = lockData.toJson();
                    JSONObject lockInfo = JSON.parseObject(defaultLockDataJson);
                    lockInfo.put("lockData", lockData.encodeLockData());
                    lockInfo.put("bindingTimeVerified", false);
                    lockInfo.put("bindingTimeZoneId", bindingZone.getID());
                    lockInfo.put("lockVersion", JSON.parseObject(device.getLockVersionJson()));
                    String lockDataJson = lockInfo.toJSONString();
                    boolean saved = PopulifeDBUtil.getInstance(getApplicationContext())
                            .save(new OfflineLock(userId, lockMac, lockName, lockDataJson, 0));
                    if (!saved) {
                        // 这条记录是"锁已被物理初始化"的唯一凭证：丢了就既进不了未同步列表、也补传不了，
                        // 锁却已经不在出厂态。落库失败必须留痕，否则线上完全无从排查。
                        PeachLogger.e(TAG, "save OfflineLock FAILED! mac=" + lockMac + " user=" + userId);
                    }
                    EventBus.getDefault().post(new Event(Event.EventType.LOCK_LOCAL_INITIALIZE_SUCCEED, lockDataJson));
                }

                @Override
                public void onFail(LockError lockError) {
                    EventBus.getDefault().post(new Event(Event.EventType.LOCK_LOCAL_INITIALIZE_FAIL));
                }
            });
		}
	}

	private void startScan() {
		mTTLockAPI.startScanLock(new ScanLockCallback() {
            @Override
            public void onScanLockSuccess(ExtendedBluetoothDevice extendedBluetoothDevice) {

                ExtendedBluetoothDevice device = extendedBluetoothDevice;
                PeachLogger.d("device=" + device.toString());
                mAdapter.setMHTState(false);
                mAdapter.updateKJXDevice(device);
                mListView.setAdapter(mAdapter);
                BaseApplication.getHandler().removeCallbacks(mRunnable);
                ll_found_device.setVisibility(View.VISIBLE);
                mLlFoundDeviceView.setVisibility(View.GONE);
                if (DIALOG != null) {
                    DIALOG.cancel();
                }
            }

            @Override
            public void onFail(LockError lockError) {

            }
        });
		initSeekbarScanDevice();
	}

	private void stopScan() {
		mTTLockAPI.stopScanLock();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
//		if (isMHLock) {
//			pplStopScan();
//		}else {
//			stopScan();
//		}
		pplStopScan();
		stopScan();
		unregisterReceiver(mReceiver);
		if (null != DIALOG) {
			DIALOG.dismiss();
		}
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type) {
			case Event.EventType.LOCK_LOCAL_INITIALIZE_SUCCEED:
				PeachLogger.d(TAG + " physical initialization complete; verifying time and server");
				if (null == event.obj) {
					//toast(R.string.note_lock_init_fail);
					AddDeviceFailActivity.actionStart(this, mLockType);
					return;
				}
				// 模拟初始化设备失败
				/*if (PeachPreference.getTestDeviceInitFail()){
					// 模拟初始化设备失败
					AddDeviceFailActivity.actionStart(this, mLockType);
					return;
				}*/
				if (isMHTLock) {
					initializeMHTLock((String) event.obj);
				} else {
					initializeKJXLock((String) event.obj);
				}
				break;
			case Event.EventType.LOCK_LOCAL_INITIALIZE_FAIL:
				PeachLogger.d(TAG + " onEventSub LOCK_LOCAL_INITIALIZE_FAIL");
				AddDeviceFailActivity.actionStart(this, mLockType);
				break;

		}

	}

    private void initializeKJXLock(String json) { initializeWithTime(json, false); }
    private void initializeMHTLock(String json) { initializeWithTime(json, true); }

    private void initializeWithTime(String json, boolean mht) {
        JSONObject info = JSON.parseObject(json);
        OfflineLock lock = new OfflineLock(PeachPreference.readUserId(), info.getString("lockMac"),
                info.getString("lockName"), json, mht ? -1 : 0);
        showLoading();
        com.populstay.populife.helper.OfflineLockUploader.getInstance().syncOne(lock,
                new com.populstay.populife.helper.OfflineLockUploader.SyncCallback() {
            @Override public void onSuccess(OfflineLock saved, JSONObject data) {
                stopLoading();
                toast(R.string.note_lock_init_success);
                com.populstay.populife.helper.UnsyncedDeviceDialog.openSuccessPage(
                        FoundDeviceActivity.this, saved, data);
                finish();
            }
            @Override public void onFail(OfflineLock saved, int failType) {
                stopLoading();
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(FoundDeviceActivity.this)
                        .setMessage(com.populstay.populife.helper.OfflineLockUploader.failureMessage(failType))
                        .setPositiveButton(R.string.try_again, (dialog, which) ->
                                initializeWithTime(saved.getLockDataJson(), mht))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                        .setCancelable(false).show();
            }
        });
    }

	private void getIntentData() {
		mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
		if ((mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3))) {
			isMHTLock = true;
		}
	}

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_tap_help) {
            Intent intent = new Intent(this, HelpActivity.class);
            intent.putExtra(HelpActivity.KEY_LOCK_TYPE, mLockType);
            startActivity(intent);
        }
    }


    /**
	 * 开始扫描
	 */
	private void pplStartScan() {
		sPPLOCK.startScan();
		initSeekbarScanDevice();
	}

	/**
	 * 停止扫描
	 */
	private void pplStopScan() {
		sPPLOCK.stopScan();
	}

}
