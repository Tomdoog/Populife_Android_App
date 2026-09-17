package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.builder.OptionsPickerBuilder;
import com.bigkoo.pickerview.listener.CustomListener;
import com.bigkoo.pickerview.listener.OnOptionsSelectListener;
import com.bigkoo.pickerview.view.OptionsPickerView;
import com.google.gson.reflect.TypeToken;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.helper.DeviceDeleteState;
import com.populstay.populife.databinding.ActivityLockSettingsBinding;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.Home;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.manhattanlock.MHILockDeleteLock;
import com.populstay.populife.manhattanlock.MHILockGetAutoLockTime;
import com.populstay.populife.manhattanlock.MHILockGetBattery;
import com.populstay.populife.manhattanlock.MHILockGetTime;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.widget.HelpPopupWindow;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.device.DeviceUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.GetAutoLockingPeriodCallback;
import com.ttlock.bl.sdk.callback.GetBatteryLevelCallback;
import com.ttlock.bl.sdk.callback.GetLockSoundWithSoundVolumeCallback;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.callback.ResetLockCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.entity.SoundVolume;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockSettingsActivity extends BaseActivity implements View.OnClickListener {

	public static final String KEY_RESULT_DATA = "key_result_data";
	private static final String KEY_LOCK_SETTINGS_LOCK_MAC = "key_lock_settings_lock_mac";
	private static final String KEY_LOCK_SETTINGS_KEY_TYPE = "key_lock_settings_key_type";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final int REQUEST_CODE_NAME = 1;
	private static final int REQUEST_CODE_PASSCODE = 2;
	private static final int REQUEST_CODE_GROUP = 3;
	private static final int REQUEST_CODE_SPECIAL_VALUE = 4;
	private static final int REQUEST_CODE_ADJUST_LOCK_TIME = 5;
	private static final int REQUEST_CODE_AUTO_LOCKING = 6;
	private static final int REQUEST_CODE_KEYPAD_VOLUME = 7;

    private ActivityLockSettingsBinding binding;

	private TextView mTvSerialNum, mTvMacId, mTvBattery, mTvValidity, mTvStartTime, mTvEndTime,
			mTvLockName, mTvAdminPasscode, mTvDelete, mTvRemoteUnlockState, tv_lock_settings_space;
	private ImageView mIvSyncBattery, mIvBattery, mIvMacDisplay, mIvSetLockTimeHelp, tv_lock_settings_space_more, tv_lock_settings_lock_name_more;
	private LinearLayout mLlMacId, mLlValidity, mLlStartEndTime, mLlLockName, ll_serial_number, mLlKeyStatus,
			mLlAdminPasscode, mLlLockTime, mLlAutoLocking, mLlLockUpgrade, mLlRemoteUnlock, mLlKeypadVolume, mLlNotification;
	private AlertDialog DIALOG;
	private EditText mEtDialogInput;
	private CheckBox mCbDeleteKeys;

	private Key mKey = CURRENT_KEY;
	private int mKeyType;//钥匙类型（1限时，2永久，3单次，4循环）
	private String mInputPwd;

	private int mPwdType;//0删除锁时输入密码，1显示 Mac/Id 时输入密码
	private boolean mIsDeleteCallbackCalled; // 删除锁时，onSuccess/onFail 是否被回调过

	/** 删除超时保护:曼哈顿锁"从未连上"时可能无任何回调(连不上 → 连 onFinish 都不触发),用超时兜底为"结果未知" */
	private static final long DELETE_TIMEOUT_MS = 30000;
	private final android.os.Handler mDeleteTimeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	private final Runnable mDeleteTimeoutRunnable = new Runnable() {
		@Override
		public void run() {
			if (!mIsDeleteCallbackCalled) {
				mIsDeleteCallbackCalled = true;
				stopLoading();
				onPhysicalDeleteUnknown();
			}
		}
	};
	private int mPwdWrongCount; // 输入账号密码错误次数

	private HelpPopupWindow mHelpPopupWindow;

	private TextView tv_read_lock_time, tv_auto_locking, tv_lock_settings_keypad_volume, tv_key_status;
	private boolean isClickReadTime, isClickAutoLocking, isClickKeypadVolume;
	private OptionsPickerView mPickerHome;
	private List<Home> mHomeList;
	private boolean mIsLockOperationSuccess;
	private String mLockType;
	private boolean isMHLock = false;

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockMac 锁的 Mac 地址
	 */
	public static void actionStart(Context context, String lockMac, int keyType, String lockType) {
		Intent intent = new Intent(context, LockSettingsActivity.class);
		intent.putExtra(KEY_LOCK_SETTINGS_LOCK_MAC, lockMac);
		intent.putExtra(KEY_LOCK_SETTINGS_KEY_TYPE, keyType);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        binding = ActivityLockSettingsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		getIntentData();
		initView();
		initPicker();
		initListener();
		initLockStatusInfo();
	}

	private void initLockStatusInfo() {
		if (isBleEnableWithoutToast()) { // 蓝牙开启
			// 和锁通信，读取锁时间（后台静默操作，不前台提示权限申请）
			readLockTime(true);
		} else {
            getTimeByNet();
		}
	}
    private void getTimeByNet() {
        tv_auto_locking.setText(R.string.unknown);
        tv_lock_settings_keypad_volume.setText(R.string.unknown);

        if (isNetEnableWithoutToast()) { // 网络开启
            // 通过网关读取锁时间
            if (mKey.getLockId() > 0) {
                readLockTimeViaGateway();
            }
        } else {
            tv_read_lock_time.setText(R.string.unknown);
        }
    }

	private void getIntentData() {
		Intent data = getIntent();
		String lockMac = data.getStringExtra(KEY_LOCK_SETTINGS_LOCK_MAC);
		mKeyType = data.getIntExtra(KEY_LOCK_SETTINGS_KEY_TYPE, 0);
//		mKey = DbService.getKeyByLockmac(lockMac);
//		Log.d("ttttttttttttt1111111111", "" + System.currentTimeMillis());
//		//todo 数据库读取
//		if (mKey == null) {
//			finish();
//		}
//		PeachLogger.d("mac", lockMac);
//		PeachLogger.d("key", mKey);
		mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
		if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3)) {
			isMHLock = true;
		}
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.settings);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSerialNum = findViewById(R.id.tv_lock_settings_serial_number);
		mTvMacId = findViewById(R.id.tv_lock_settings_mac_id);
		mIvSyncBattery = findViewById(R.id.iv_lock_settings_battery_sync);
		mIvBattery = findViewById(R.id.iv_lock_settings_battery);
		mIvMacDisplay = findViewById(R.id.iv_lock_settings_mac_display);
		mTvBattery = findViewById(R.id.tv_lock_settings_battery);
		mLlMacId = findViewById(R.id.ll_lock_settings_mac_id);
		mLlValidity = findViewById(R.id.ll_lock_settings_validity_period);
		mTvValidity = findViewById(R.id.tv_lock_settings_validity_period);
		mLlStartEndTime = findViewById(R.id.ll_lock_settings_time);
		mTvStartTime = findViewById(R.id.tv_lock_settings_start_time);
		mTvEndTime = findViewById(R.id.tv_lock_settings_end_time);
		ll_serial_number = findViewById(R.id.ll_serial_number);
		mLlKeyStatus = findViewById(R.id.ll_lock_settings_status);
		mLlLockName = findViewById(R.id.ll_lock_settings_lock_name);
		mTvLockName = findViewById(R.id.tv_lock_settings_lock_name);
		mLlAdminPasscode = findViewById(R.id.ll_lock_settings_admin_passcode);
		mTvAdminPasscode = findViewById(R.id.tv_lock_settings_admin_passcode);
		tv_lock_settings_space = findViewById(R.id.tv_lock_settings_space);
		tv_lock_settings_space_more = findViewById(R.id.tv_lock_settings_space_more);
		tv_lock_settings_lock_name_more = findViewById(R.id.tv_lock_settings_lock_name_more);
		mLlLockTime = findViewById(R.id.ll_lock_settings_lock_time);
		mLlAutoLocking = findViewById(R.id.ll_lock_settings_auto_locking);
		mLlLockUpgrade = findViewById(R.id.ll_lock_settings_lock_update);
		mLlRemoteUnlock = findViewById(R.id.ll_lock_settings_remote_unlock);
		mTvRemoteUnlockState = findViewById(R.id.tv_lock_settings_remote_unlock_state);
		mLlKeypadVolume = findViewById(R.id.ll_lock_settings_keypad_volume);
		mLlNotification = findViewById(R.id.ll_lock_settings_notification);
		mTvDelete = findViewById(R.id.tv_lock_settings_delete);
		mIvSetLockTimeHelp = findViewById(R.id.iv_lock_settings_lock_time_help);
		tv_read_lock_time = findViewById(R.id.tv_read_lock_time);
		tv_auto_locking = findViewById(R.id.tv_auto_locking);
		tv_lock_settings_keypad_volume = findViewById(R.id.tv_lock_settings_keypad_volume);
		tv_key_status = findViewById(R.id.tv_key_status);
		initUI();
//		getLockBattery();
	}

	private void setKeyStatusUI() {
		switch (mKey.getKeyStatus()) {
			case "110401"://正常使用
				tv_key_status.setText(R.string.key_pwd_status_available);
				break;
			case "110400": // 还未到生效时间
			case "110402"://待接收
				//tv_key_status.setText(R.string.key_pwd_status_not_activated);
			case "110405"://已冻结
			case "110408"://已删除
			case "110410"://已重置
			case "110500"://已过期
				tv_key_status.setText(R.string.key_pwd_status_invalid);
				break;
		}
	}

	@SuppressLint("SetTextI18n")
	private void initUI() {
		refreshBattery();
		setKeyStatusUI();
		mTvSerialNum.setText(mKey.getLockName());
		mTvLockName.setText(mKey.getLockAlias());
		ll_serial_number.setVisibility(View.GONE);
		mLlLockTime.setVisibility(View.GONE);
		mIvSyncBattery.setVisibility(View.GONE);
		mLlKeypadVolume.setVisibility(View.GONE);
		mTvDelete.setVisibility(View.GONE);
		mLlRemoteUnlock.setVisibility(View.GONE);
		mLlAutoLocking.setVisibility(View.GONE);
		Home home = mKey.getHome();
		if (null != home) {
			tv_lock_settings_space.setText(home.getName());
		}

		if (mKey.isAdmin()) { // 管理员
			ll_serial_number.setVisibility(View.VISIBLE);
			mLlLockTime.setVisibility(View.VISIBLE);
			mIvSyncBattery.setVisibility(View.VISIBLE);
			mLlKeypadVolume.setVisibility(View.VISIBLE);
			if (isMHLock) {
				mLlKeypadVolume.setVisibility(View.GONE);
			}
			if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)) {

				mLlAdminPasscode.setVisibility(View.GONE);
			}
			mTvDelete.setVisibility(View.VISIBLE);
			mLlValidity.setVisibility(View.GONE);
			mLlKeyStatus.setVisibility(View.GONE);
			// 远程开锁开关入口
			mLlRemoteUnlock.setVisibility(View.VISIBLE);
			if (isMHLock) {
				mLlRemoteUnlock.setVisibility(View.GONE);
			}
			boolean isSupportRemoteUnlock = FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK);
			mTvRemoteUnlockState.setText(isSupportRemoteUnlock ? R.string.on : R.string.off);
			if (isMHLock) {
				mTvRemoteUnlockState.setVisibility(View.GONE);
			}
			// 管理员、同时支持 APP 闭锁，则显示“自动闭锁”，否则隐藏
			mLlAutoLocking.setVisibility(DigitUtil.isSupportManualLock(mKey.getSpecialValue()) ? View.VISIBLE : View.GONE);
			if (mKey.getLockId() < 0) {
				if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3)) {
					mLlAutoLocking.setVisibility(View.GONE);
				} else {
					mLlAutoLocking.setVisibility(View.VISIBLE);
				}
//				mLlLockUpgrade.setVisibility(View.VISIBLE);
			}
			mTvValidity.setText(getString(R.string.permanent));
			mLlStartEndTime.setVisibility(View.GONE);
			mTvAdminPasscode.setText(mKey.getNoKeyPwd());
		} else {
            if (mKey.isAllowSyncBattery()) {
                mIvSyncBattery.setVisibility(View.VISIBLE);
            }
            if (mKey.isAllowCalibrateTime()) {
                mLlLockTime.setVisibility(View.VISIBLE);
            }
            binding.llLockSettingsTransfer.setVisibility(View.GONE);
            binding.llLockSettingsNotification.setVisibility(View.GONE);
			if (mKey.getKeyRight() == 1) { // 授权用户
				mLlAdminPasscode.setVisibility(View.GONE);
				tv_lock_settings_space.setEnabled(false);
				tv_lock_settings_space_more.setVisibility(View.GONE);
				tv_lock_settings_lock_name_more.setVisibility(View.GONE);
				mLlLockName.setEnabled(false);
				switch (mKeyType) { // 钥匙类型（1限时，2永久，3单次，4循环）
					case 1:
						mLlValidity.setVisibility(View.GONE);
						mTvStartTime.setText(DateUtil.getDateToString(mKey.getStartDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKey.getEndDate(), "yyyy-MM-dd HH:mm"));
						break;

					case 2:
						mLlValidity.setVisibility(View.VISIBLE);
						mTvValidity.setText(getString(R.string.permanent));
						mLlStartEndTime.setVisibility(View.GONE);
						break;

					case 3:
						mLlValidity.setVisibility(View.VISIBLE);
						mTvValidity.setText(getString(R.string.one_time));
						mLlStartEndTime.setVisibility(View.GONE);
						break;
				}
			} else { // 普通用户
				tv_lock_settings_space.setEnabled(false);
				mLlLockName.setEnabled(false);
				tv_lock_settings_space_more.setVisibility(View.GONE);
				tv_lock_settings_lock_name_more.setVisibility(View.GONE);
				mLlLockName.setVisibility(View.VISIBLE);
				mLlAdminPasscode.setVisibility(View.GONE);
				mLlMacId.setVisibility(View.GONE);
				mLlValidity.setVisibility(View.GONE);
				mLlAdminPasscode.setVisibility(View.GONE);
				mLlAutoLocking.setVisibility(View.GONE);
				mLlLockUpgrade.setVisibility(View.GONE);
				mLlRemoteUnlock.setVisibility(View.GONE);
				mLlKeypadVolume.setVisibility(View.GONE);
				switch (mKeyType) { // 钥匙类型（1限时，2永久，3单次，4循环）
					case 1:
						mLlStartEndTime.setVisibility(View.VISIBLE);
						mLlValidity.setVisibility(View.GONE);
						mTvStartTime.setText(DateUtil.getDateToString(mKey.getStartDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKey.getEndDate(), "yyyy-MM-dd HH:mm"));
						break;

					case 2:
						mLlValidity.setVisibility(View.VISIBLE);
						mTvValidity.setText(getString(R.string.permanent));
						mLlStartEndTime.setVisibility(View.GONE);
						break;

					case 3:
						mLlValidity.setVisibility(View.VISIBLE);
						mTvValidity.setText(getString(R.string.one_time));
						mLlStartEndTime.setVisibility(View.GONE);
						break;
				}
			}
		}
		if (HomeDeviceInfo.isPadlock(mKey.getLockName())) {
			mLlAutoLocking.setVisibility(View.GONE);
			mLlAdminPasscode.setVisibility(View.GONE);
			mLlKeypadVolume.setVisibility(View.GONE);
		}
	}

	/**
	 * 刷新锁电量显示
	 */
	@SuppressLint("SetTextI18n")
	private void refreshBattery() {
		Resources res = getResources();
		int battery = CURRENT_KEY.getElectricQuantity();
		mTvBattery.setText(battery + res.getString(R.string.unit_percent));
		int batteryLevel = (battery - 1) / 20;
		int imgResInt = R.drawable.ic_battery_100;
		int txtColor = res.getColor(R.color.battery_high_green);
		switch (batteryLevel) {
			case 0:
				imgResInt = R.drawable.ic_battery_20;
				txtColor = res.getColor(R.color.battery_low_red);
				break;

			case 1:
				imgResInt = R.drawable.ic_battery_40;
				txtColor = res.getColor(R.color.battery_middle_orange);
				break;

			case 2:
				imgResInt = R.drawable.ic_battery_60;
				txtColor = res.getColor(R.color.battery_high_green);
				break;

			case 3:
				imgResInt = R.drawable.ic_battery_80;
				txtColor = res.getColor(R.color.battery_high_green);
				break;

			case 4:
				imgResInt = R.drawable.ic_battery_100;
				txtColor = res.getColor(R.color.battery_high_green);
				break;

			default:
				break;
		}
		mIvBattery.setImageResource(imgResInt);
		mTvBattery.setTextColor(txtColor);
	}

	private void showHelpPopupWindow(View anchor) {
		if (null == mHelpPopupWindow) {
			mHelpPopupWindow = new HelpPopupWindow(this, R.layout.help_popup_window_layout2, R.dimen.help_win_width, R.dimen.help_win_height_92dp);
		}
		mHelpPopupWindow.show(anchor, Gravity.LEFT);
	}

	private void hideHelpPopupWindow() {
		if (null != mHelpPopupWindow) {
			mHelpPopupWindow.dismiss();
		}
	}

	private void initListener() {
		mLlMacId.setOnClickListener(this);
		mLlLockName.setOnClickListener(this);
		mLlAdminPasscode.setOnClickListener(this);
		mLlLockTime.setOnClickListener(this);
		mLlAutoLocking.setOnClickListener(this);
		mLlLockUpgrade.setOnClickListener(this);
		mTvDelete.setOnClickListener(this);
		mLlRemoteUnlock.setOnClickListener(this);
		mIvSyncBattery.setOnClickListener(this);
		mLlKeypadVolume.setOnClickListener(this);
		mLlNotification.setOnClickListener(this);
		mIvSetLockTimeHelp.setOnClickListener(this);
		tv_lock_settings_space.setOnClickListener(this);
        binding.llLockSettingsNotification.setOnClickListener(this);
        binding.llLockSettingsTransfer.setOnClickListener(this);
	}
    @Override
    public void onClick(View view) {
        Intent intent = new Intent();
        int id = view.getId();

        if (id == R.id.ll_lock_settings_mac_id) {
            showInputDialog();
            mPwdType = 1;

        } else if (id == R.id.ll_lock_settings_lock_name) {
            intent.setClass(LockSettingsActivity.this, ModifyLockNameActivity.class);
            intent.putExtra(ModifyLockNameActivity.KEY_LOCK_NAME, mTvLockName.getText().toString());
            intent.putExtra(ModifyLockNameActivity.KEY_LOCK_ID, mKey.getLockId());
            startActivityForResult(intent, REQUEST_CODE_NAME);

        } else if (id == R.id.ll_lock_settings_admin_passcode) {
            intent.setClass(LockSettingsActivity.this, ModifyAdminPasscodeActivity.class);
            intent.putExtra(ModifyAdminPasscodeActivity.KEY_PASSCODE, mTvAdminPasscode.getText().toString());
            intent.putExtra(ModifyAdminPasscodeActivity.KEY, mKey);
            startActivityForResult(intent, REQUEST_CODE_PASSCODE);

        } else if (id == R.id.ll_lock_settings_lock_time) {
            if (isBleEnableWithoutToast()) {
                isClickReadTime = true;
                mIsLockOperationSuccess = false;
                showLoading();
                readLockTime(false);
            } else if (isNetEnableWithoutToast()) {
                isClickReadTime = true;
                if (mKey.getLockId() > 0) readLockTimeViaGateway();
            } else {
                toastFail();
            }

        } else if (id == R.id.ll_lock_settings_auto_locking) {
            if (HomeDeviceInfo.isPadlock(mKey.getLockName())) return;
            if (isBleEnableWithToast()) {
                isClickAutoLocking = true;
                searchAutoLockTime();
            }

        } else if (id == R.id.ll_lock_settings_lock_update) {
            goToNewActivity(LockUpdateActivity.class);

        } else if (id == R.id.tv_lock_settings_delete) {
            if (mKey.isAdmin()) {
                showInputDialog();
                mPwdType = 0;
            } else if (mKey.getKeyRight() == 1) {
                showChooseDialog();
            } else {
                Resources res = getResources();
                DialogUtil.showCommonDialog(LockSettingsActivity.this, null,
                        res.getString(R.string.note_confirm_delete), res.getString(R.string.ok),
                        res.getString(R.string.cancel), (dialogInterface, i) -> deleteEkey("N"), null);
            }

        } else if (id == R.id.btn_dialog_input_cancel || id == R.id.btn_dialog_choose_cancel) {
            DIALOG.cancel();

        } else if (id == R.id.btn_dialog_input_ok) {
            mInputPwd = mEtDialogInput.getText().toString();
            if (!StringUtil.isBlank(mInputPwd)) {
                verifyAccountPwd(mInputPwd);
                DIALOG.cancel();
            } else {
                toast(R.string.enter_account_passwprd);
            }

        } else if (id == R.id.btn_dialog_choose_ok) {
            String delType = mCbDeleteKeys.isChecked() ? "Y" : "N";
            deleteEkey(delType);
            DIALOG.cancel();

        } else if (id == R.id.ll_lock_settings_remote_unlock) {
            intent.setClass(LockSettingsActivity.this, LockRemoteUnlockConfigActivity.class);
            intent.putExtra(LockRemoteUnlockConfigActivity.KEY_LOCK_SPECIAL_VALUE, mKey.getSpecialValue());
            startActivityForResult(intent, REQUEST_CODE_SPECIAL_VALUE);

        } else if (id == R.id.iv_lock_settings_battery_sync) {
            getLockBattery();
        } else if (id == R.id.ll_lock_settings_keypad_volume) {
            if (isBleNetEnableWithToast()) {
                isClickKeypadVolume = true;
                if (mKey.getLockId() > 0) queryKeypadVolume();
            }

        } else if (id == R.id.iv_lock_settings_lock_time_help) {
            showHelpPopupWindow(view);

        } else if (id == R.id.tv_lock_settings_space) {
            if (CollectionUtil.isEmpty(mHomeList)) {
                requestLockGroup();
            } else {
                mPickerHome.show();
            }
        } else if (id == R.id.ll_lock_settings_notification) {
            LockNotificationActivity.actionStart(this);
        } else if (id == R.id.ll_lock_settings_transfer) {
            LockTransferActivity.actionStart(this);
        }
    }


	/**
	 * 绑定锁家庭
	 */
	private void bindLockHome(final Home currentHome) {
		RestClient.builder()
				.url(Urls.LOCK_BIND_HOME)
				.loader(this)
				.params("lockId", mKey.getLockId())
				.params("homeId", currentHome.getId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							tv_lock_settings_space.setText(currentHome.getName());
							mKey.setHome(currentHome);
						} else {
							toast(R.string.change_space_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.change_space_fail);
					}
				})
				.build()
				.post();
	}

	private void queryKeypadVolume() {
		if (HomeDeviceInfo.isPadlock(mKey.getLockName())) return;
		if (isClickKeypadVolume) {
			showLoading();
		}
        mTTLockAPI.getLockSoundWithSoundVolume(mKey.getLockData(), new GetLockSoundWithSoundVolumeCallback() {
            @Override
            public void onGetLockSoundSuccess(boolean b, SoundVolume keypadVolume) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        if (isClickKeypadVolume) {
                            isClickKeypadVolume = false;
                            LockSoundActivity.actionStart(LockSettingsActivity.this, b ? 1 : 0, REQUEST_CODE_KEYPAD_VOLUME);
                        } else {
                            if (b) {
                                tv_lock_settings_keypad_volume.setText(R.string.on);
                            } else {
                                tv_lock_settings_keypad_volume.setText(R.string.off);
                            }
                        }
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                stopLoading();
                if (isClickKeypadVolume) {
                    toastFail();
                }
                tv_lock_settings_keypad_volume.setText(R.string.unknown);
            }
        });
	}


	/**
	 * 读取锁时间
	 *
	 * @param isBackground 是否为后台静默操作
	 *                     - true：不前台提示权限申请
	 *                     - false：前台提示权限申请
	 */
	private void readLockTime(boolean isBackground) {
		setGetTimeCallback();
		if (mKey.getLockId() < 0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getLockTime();
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				if (!isBackground) {
					startLockActionScan();
				} else {
					startLockActionScanNoPermissionNoToast();
				}
			}
		} else {
            mTTLockAPI.getLockTime(mKey.getLockData(), mKey.getLockMac(), new GetLockTimeCallback() {
                @Override
                public void onGetLockTimeSuccess(long time) {
                    CURRENT_KEY.setLockCurrentTime(time);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (isClickReadTime) {
                                LockTimeActivity.actionStart(LockSettingsActivity.this, time, REQUEST_CODE_ADJUST_LOCK_TIME, mKey);
                            } else {
                                tv_read_lock_time.setText(DateUtil.getDateToString(time, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
                                searchAutoLockTime();
                            }
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (isClickReadTime) {
                                if (isNetEnableWithoutToast()) { // 网络开启
                                    // 通过网关读取锁时间
                                    if (mKey.getLockId() > 0) {
                                        readLockTimeViaGateway();
                                    }
                                } else {
                                    toastFail();
                                }
                            } else {
                                searchAutoLockTime();
                                if (isNetEnableWithoutToast()) { // 网络开启
                                    // 通过网关读取锁时间
                                    if (mKey.getLockId() > 0) {
                                        readLockTimeViaGateway();
                                    }
                                } else {
                                    tv_read_lock_time.setText(R.string.unknown);
                                }
                            }
                        }
                    });
                }
            });
		}
	}

	private void setGetTimeCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_LOCK_TIME);
			MyApplication.pplBleSession.setmILockGetTime(new MHILockGetTime() {
				@Override
				public void onSuccess(final long time) {
					CURRENT_KEY.setLockCurrentTime(time);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							if (isClickReadTime) {
								LockTimeActivity.actionStart(LockSettingsActivity.this, time, REQUEST_CODE_ADJUST_LOCK_TIME, mKey);
							} else {
								tv_read_lock_time.setText(DateUtil.getDateToString(time, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
								searchAutoLockTime();
							}
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							if (isClickReadTime) {
								if (isNetEnableWithoutToast()) { // 网络开启
									// 通过网关读取锁时间
									if (mKey.getLockId() > 0) {
										readLockTimeViaGateway();
									}
								} else {
									toastFail();
								}
							} else {
								searchAutoLockTime();
								if (isNetEnableWithoutToast()) { // 网络开启
									// 通过网关读取锁时间
									if (mKey.getLockId() > 0) {
										readLockTimeViaGateway();
									}
								} else {
									tv_read_lock_time.setText(R.string.unknown);
								}
							}
						}
					});
				}

			});

		}
	}

	/**
	 * 通过网关读取锁时间
	 */
	private void readLockTimeViaGateway() {
		RestClient.builder()
				.url(Urls.GATEWAY_LOCK_TIME_READ)
				.loader(isClickReadTime ? this : null)
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GATEWAY_LOCK_TIME_READ", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONObject dateInfo = result.getJSONObject("data");
							if (null != dateInfo) {
								long time = dateInfo.getLong("date");
								if (isClickReadTime) {
									LockTimeActivity.actionStart(LockSettingsActivity.this, time, REQUEST_CODE_ADJUST_LOCK_TIME, mKey);
								} else {
									tv_read_lock_time.setText(DateUtil.getDateToString(time, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
								}
							}
						} else {
							if (isClickReadTime) {
								toastFail();
							} else {
								tv_read_lock_time.setText(R.string.unknown);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						if (isClickReadTime) {
							toastFail();
						} else {
							tv_read_lock_time.setText(R.string.unknown);
						}
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						if (isClickReadTime) {
							toastFail();
						} else {
							tv_read_lock_time.setText(R.string.unknown);
						}
					}
				})
				.build()
				.post();
	}

	private void searchAutoLockTime() {
		// P3 不支持自动闭锁，也不查询随后串联的键盘声音设置。
		if (HomeDeviceInfo.isPadlock(mKey.getLockName())) return;
		if (isClickAutoLocking) {
			showLoading();
		}
		setSearchAutoLockTimeCallback();
		if (mKey.getLockId() < 0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getAutoLockTime();
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}

		} else {
            mTTLockAPI.getAutomaticLockingPeriod(mKey.getLockData(), new GetAutoLockingPeriodCallback() {
                @Override
                public void onGetAutoLockingPeriodSuccess(int second, int i1, int i2) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            if (isClickAutoLocking) {
                                LockAutoLockingActivity.actionStart(LockSettingsActivity.this, second, REQUEST_CODE_AUTO_LOCKING, mKey);
                            } else {
                                if (mKey.getLockId() > 0) {
                                    queryKeypadVolume();
                                }
                            }
                            if (second > 0) {
                                tv_auto_locking.setText(R.string.on);
                            } else {
                                tv_auto_locking.setText(R.string.off);
                            }
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            stopLoading();
                            if (isClickAutoLocking) {
                                toastFail();
                            } else {
                                queryKeypadVolume();
                            }
                            tv_auto_locking.setText(R.string.unknown);
                        }
                    });
                }
            });
		}
	}

	private void setSearchAutoLockTimeCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_AUTO_LOCK_TIME);
			MyApplication.pplBleSession.setmILockGetAutoLockTime(new MHILockGetAutoLockTime() {
				@Override
				public void onSuccess(final int second) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							if (isClickAutoLocking) {
								LockAutoLockingActivity.actionStart(LockSettingsActivity.this, second, REQUEST_CODE_AUTO_LOCKING, mKey);
							} else {
								if (mKey.getLockId() > 0) {
									queryKeypadVolume();
								}
							}
							if (second > 0) {
								tv_auto_locking.setText(R.string.on);
							} else {
								tv_auto_locking.setText(R.string.off);
							}
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@SuppressLint("SetTextI18n")
						@Override
						public void run() {
							stopLoading();
							if (isClickAutoLocking) {
								toastFail();
							} else {
								if (mKey.getLockId() > 0) {
									queryKeypadVolume();
								}
							}
							tv_auto_locking.setText(R.string.unknown);
						}
					});
				}
			});

		}
	}

	private WeakHashMap<String, Object> getLockParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("keyId", mKey.isAdmin() ? mKey.getKeyId() : mKey.getUserKeyId());

		// Y同时删除他所发送的钥匙，N则不。（注：只适用于授权用户，普通用户可传空）
		if (mKey.getKeyRight() == 1 || mKey.isAdmin()) {
			params.put("delType", "Y");
		}

		return params;
	}

	/**
	 * （授权/普通）用户删除自己的钥匙（锁设置页面）
	 */
	private void deleteEkey(String delType) {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_DELETE)
				.params(getLockParams())
				.loader(LockSettingsActivity.this)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							PeachPreference.setLastUnlockTimeAndType(mKey.getLockId(), 0, 0);
							toast(R.string.ekey_delete_success);
//							Key key = DbService.getKeyByLockmac(mKey.getLockMac());
//							if (key != null) {
//								DbService.deleteKey(key);
//							}
							goToNewActivity(MainActivity.class);
						} else {
							toast(R.string.ekey_delete_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_delete_fail);
					}
				})
				.build()
				.post();
	}

	private void showChooseDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_choose);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			TextView title = window.findViewById(R.id.tv_dialog_choose_title);
			mCbDeleteKeys = window.findViewById(R.id.cb_dialog_choose_content);
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_choose_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_choose_ok);
			ok.setText(R.string.delete);

			title.setText(R.string.note_delete_autu_key);
			mCbDeleteKeys.setText(R.string.note_delete_sent_key);

			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	private void verifyAccountPwd(String pwd) {
		RestClient.builder()
				.url(Urls.LOCK_USER_CHECK)
				.loader(LockSettingsActivity.this)
				.params("password", pwd)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_VERIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							boolean isPwdValid = result.getBoolean("success");
							if (isPwdValid) {
								mPwdWrongCount = 0;
								if (mPwdType == 0) {
									Resources res = getResources();
									DialogUtil.showCommonDialog(LockSettingsActivity.this, null,
											res.getString(R.string.note_confirm_delete), res.getString(R.string.ok),
											res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialogInterface, int i) {
													adminResetLock();
												}
											}, null);
								} else {
									mIvMacDisplay.setVisibility(View.GONE);
									mTvMacId.setText(mKey.getLockMac() + "/" + mKey.getKeyId());
									mLlMacId.setEnabled(false);
								}
							} else {
								showPwdWrongDialog();
							}
						} else {
							showPwdWrongDialog();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						// 删除准备(user/check)断网/失败:明确提示,不静默 —— 停在准备前,物理与账号均不变
						toast(R.string.unsync_fail_network);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.unsync_fail_network);
					}
				})
				.build()
				.post();
	}

	private void showPwdWrongDialog() {
//		if (++mPwdWrongCount >= 3) {
//			DialogUtil.showCommonDialog(LockSettingsActivity.this, null,
//					getString(R.string.note_pwd_wrong_times), getString(R.string.reset_pwd),
//					getString(R.string.cancel), new DialogInterface.OnClickListener() {
//						@Override
//						public void onClick(DialogInterface dialog, int which) {
//							goToNewActivity(ResetPwdActivity.class);
//						}
//					}, null);
//		} else {
		toast(R.string.note_pwd_invalid);
//		}
	}

	private void showInputDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_input);
			window.setGravity(Gravity.CENTER);
//			window.setWindowAnimations(R.style.anim_panel_up_from_bottom);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			TextView title = window.findViewById(R.id.tv_dialog_input_title);
			mEtDialogInput = window.findViewById(R.id.et_dialog_input_content);
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_input_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_input_ok);

			title.setText(R.string.enter_account_passwprd);
			mEtDialogInput.setHint(R.string.enter_pwd);
			mEtDialogInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
			DialogUtil.autoPopSoftInput(window, mEtDialogInput);

			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDeleteTimeoutHandler.removeCallbacks(mDeleteTimeoutRunnable);
	}

	/**
	 * 物理删除设备。此前 verifyAccountPwd(lock/user/check)已完成"删除准备"(联网+身份+密码校验),
	 * 断网/密码错/非管理员根本进不到这里。本方法只负责发蓝牙删除指令并按三态处理结果。
	 */
	private void adminResetLock() {
		PeachLogger.d("[ACTION] 删除设备 lockId=" + mKey.getLockId() + " mac=" + mKey.getLockMac());
		if (isBleNetEnableWithToast()) {
			showLoading();
			mIsDeleteCallbackCalled = false;
			// 启动超时保护:超时仍无任何回调(如锁始终不在范围连不上)则按"结果未知"处理,不让 loading 挂死
			mDeleteTimeoutHandler.removeCallbacks(mDeleteTimeoutRunnable);
			mDeleteTimeoutHandler.postDelayed(mDeleteTimeoutRunnable, DELETE_TIMEOUT_MS);
			// 发出蓝牙删除指令前先留痕:万一 App 在回调前被杀,下次进设备详情能按"结果未知"对账,不留幽灵设备
			DeviceDeleteState.set(PeachPreference.readUserId(), mKey.getLockId(), DeviceDeleteState.STATE_DELETING, mKey.getLockMac());
			setDeleteLockCallback();
			if (mKey.getLockId() < 0) {
				if (sPPLOCK.isConnected(mKey.getLockMac())) {
					sPPLOCK.deleteLock(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()), mKey.getK1());
				} else {//connect the lock
					//sPPLOCK.connect(mKey.getLockMac());
					startLockActionScan();
				}
			} else {
                mTTLockAPI.resetLock(mKey.getLockData(), mKey.getLockMac(), new ResetLockCallback() {
                    @Override
                    public void onResetLockSuccess() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsDeleteCallbackCalled = true;
                                onPhysicalDeleteSuccess();
                            }
                        });
                    }

                    @Override
                    public void onFail(final LockError lockError) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsDeleteCallbackCalled = true;
                                stopLoading();
                                // 连接超时/断连 → 结果未知(锁可能已删),不能当作明确失败
                                if (isBleConnectError(lockError)) {
                                    onPhysicalDeleteUnknown();
                                } else {
                                    onPhysicalDeleteFail();
                                }
                            }
                        });
                    }
                });
			}
		}
	}

	/** TTLock 的连接/超时类错误 = 物理删除结果未知(而非明确失败) */
	private boolean isBleConnectError(LockError lockError) {
		return lockError == LockError.LOCK_CONNECT_FAIL
				|| lockError == LockError.WIRELESS_KEYBOARD_NO_RESPONSE
				|| lockError == LockError.DEVICE_CONNECT_FAILED;
	}

	private void setDeleteLockCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.DELETE_LOCK);
			MyApplication.pplBleSession.setmILockDeleteLock(new MHILockDeleteLock() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mIsDeleteCallbackCalled = true;
							onPhysicalDeleteSuccess();
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mIsDeleteCallbackCalled = true;
							stopLoading();
							onPhysicalDeleteFail();
						}
					});
				}

				@Override
				public void onFinish() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (!mIsDeleteCallbackCalled) {
								mIsDeleteCallbackCalled = true;
								stopLoading();
								// 连接断开、没等到删除结果 → 结果未知
								onPhysicalDeleteUnknown();
							}
						}
					});
				}
			});
		}
	}

	/** 物理删除明确成功:锁已重置,记为"待账号移除"并立即执行账号移除 */
	private void onPhysicalDeleteSuccess() {
		mDeleteTimeoutHandler.removeCallbacks(mDeleteTimeoutRunnable);
		stopLoading();
		DeviceDeleteState.set(PeachPreference.readUserId(), mKey.getLockId(), DeviceDeleteState.STATE_PENDING_ACCOUNT, mKey.getLockMac());
		requestAccountRemove();
	}

	/** 物理删除明确失败:锁未动,清除记录,设备保留可继续管理 */
	private void onPhysicalDeleteFail() {
		mDeleteTimeoutHandler.removeCallbacks(mDeleteTimeoutRunnable);
		DeviceDeleteState.clear(PeachPreference.readUserId(), mKey.getLockId());
		toastFail();
	}

	/** 物理删除结果未知(超时/断连):保留"结果待确认"记录,回详情页由用户靠近设备确认 */
	private void onPhysicalDeleteUnknown() {
		mDeleteTimeoutHandler.removeCallbacks(mDeleteTimeoutRunnable);
		DeviceDeleteState.set(PeachPreference.readUserId(), mKey.getLockId(), DeviceDeleteState.STATE_UNKNOWN, mKey.getLockMac());
		toast(R.string.delete_result_unknown_tip);
		finish();
	}

	/**
	 * 账号移除:物理删除成功后调 lock/del/v2(不需密码,复用删除准备阶段已验过的授权)。
	 * 成功 → 清记录、刷新首页;失败/超时 → 保留"待账号移除"记录,由详情页引导用户完成同一笔移除。
	 */
	private void requestAccountRemove() {
		RestClient.builder()
				.url(Urls.LOCK_ADMIN_DELETE_V2)
				.loader(LockSettingsActivity.this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_ADMIN_DELETE_V2 " + response);
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
							onAccountRemoveDone();
						} else {
							onAccountRemovePending();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						onAccountRemovePending();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						onAccountRemovePending();
					}
				})
				.build()
				.post();
	}

	/** 账号移除成功:两端都已删除,清记录、刷新首页、退出 */
	private void onAccountRemoveDone() {
		DeviceDeleteState.clear(PeachPreference.readUserId(), mKey.getLockId());
		PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
		int lockNum = PeachPreference.getAccountLockNum(PeachPreference.readUserId());
		PeachPreference.setAccountLockNum(PeachPreference.readUserId(), lockNum - 1);
		PeachPreference.setLastUnlockTimeAndType(mKey.getLockId(), 0, 0);
		EventBus.getDefault().post(new Event(Event.EventType.DELETE_LOCK_SUCCESS));
		finish();
	}

	/** 账号移除未完成:物理锁已重置,记录保留为"待账号移除",提示到设备详情继续 */
	private void onAccountRemovePending() {
		DeviceDeleteState.set(PeachPreference.readUserId(), mKey.getLockId(), DeviceDeleteState.STATE_PENDING_ACCOUNT, mKey.getLockMac());
		toast(R.string.delete_pending_account_tip);
		finish();
	}

	/**
	 * 通过 SDK 读取锁电量
	 */
	private void getLockBattery() {
		showLoading();
		setGetBatteryCallback();

		if (mKey.getLockId() < 0) {
            if (isBleNetEnableWithToast()) {
                DialogUtil.showCommonDialog(LockSettingsActivity.this, getString(R.string.sync_battery),
                        getString(R.string.note_sync_battery), getString(R.string.ok), getString(R.string.cancel),
                        (dialog, which) -> {
                            if (sPPLOCK.isConnected(mKey.getLockMac())) {
                                sPPLOCK.getBatteryLevel(0);
                            } else {
                                startLockActionScan();
                            }
                        }, null);
            }
		} else {
            requestGetLockBattery();
		}
	}

    private void getLockBatteryBle() {
        if (isBleNetEnableWithToast()) {
            DialogUtil.showCommonDialog(LockSettingsActivity.this, getString(R.string.sync_battery),
                    getString(R.string.note_sync_battery), getString(R.string.ok), getString(R.string.cancel),
                    (dialog, which) -> {
                        mTTLockAPI.getBatteryLevel(mKey.getLockData(), mKey.getLockMac(), new GetBatteryLevelCallback() {
                            @Override
                            public void onGetBatteryLevelSuccess(int battery) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        stopLoading();
                                        requestUploadLockBattery(battery);
                                    }
                                });
                            }

                            @Override
                            public void onFail(LockError lockError) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        stopLoading();
                                        toastFail();
                                    }
                                });
                            }
                        });
                    }, null);
        }
    }

	private void setGetBatteryCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_BATTERY_LEVEL);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockGetBattery(new MHILockGetBattery() {
				@Override
				public void onSuccess(final int battery) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestUploadLockBattery(battery);
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toastFail();
						}
					});
				}
			});
		}
	}

	/**
	 * 请求服务器，上传锁电量
	 */
	private void requestUploadLockBattery(final int battery) {
		RestClient.builder()
				.url(Urls.LOCK_UPLOAD_BATTERY)
				.loader(this)
				.params("lockId", mKey.getLockId())
				.params("electricQuantity", String.valueOf(battery))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_UPLOAD_BATTERY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							CURRENT_KEY.setElectricQuantity(battery);
							refreshBattery();
							if (battery <= 20) {
								DialogUtil.showCommonDialog(LockSettingsActivity.this, null,
										getString(R.string.note_low_battery), getString(R.string.ok), null,
										null, null);
								DeviceUtil.vibrate(LockSettingsActivity.this, 500);
							} else
								toastSuccess();
						}
					}
				})
				.build()
				.post();
	}

    /**
     * 请求服务器，查询锁电量
     */
    private void requestGetLockBattery() {
        RestClient.builder()
                .url(Urls.LOCK_GET_BATTERY)
                .loader(this)
                .params("lockId", mKey.getLockId())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_GET_BATTERY", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        int battery = result.getInteger("data");
                        Log.d("TESTTEST", "get battery " + battery);
                        if (code == 200) {
                            CURRENT_KEY.setElectricQuantity(battery);
                            refreshBattery();
                            if (battery <= 20) {
                                DialogUtil.showCommonDialog(LockSettingsActivity.this, null,
                                        getString(R.string.note_low_battery), getString(R.string.ok), null,
                                        null, null);
                                DeviceUtil.vibrate(LockSettingsActivity.this, 500);
                            } else
                                toastSuccess();
                        } else {
                            getLockBatteryBle();
                        }
                    }

                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        getLockBatteryBle();
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        getLockBatteryBle();
                    }
                })
                .build()
                .post();
    }

	@SuppressLint("MissingSuperCall")
	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if (resultCode == RESULT_OK) {
			String dataResult = "";
			if (data != null) {
				dataResult = data.getStringExtra(KEY_RESULT_DATA);
			}
			switch (requestCode) {
				case REQUEST_CODE_NAME:
					mTvLockName.setText(dataResult);
					break;
				case REQUEST_CODE_PASSCODE:
					mTvAdminPasscode.setText(dataResult);
					break;
				case REQUEST_CODE_SPECIAL_VALUE:
//					int specialValue = data.getIntExtra(LockRemoteUnlockConfigActivity.KEY_LOCK_SPECIAL_VALUE, 0);
//					mKey.setSpecialValue(specialValue);
					if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
						mTvRemoteUnlockState.setText(R.string.on);
					} else {
						mTvRemoteUnlockState.setText(R.string.off);
					}
					break;
				case REQUEST_CODE_ADJUST_LOCK_TIME:
					tv_read_lock_time.setText(DateUtil.getDateToString(Long.valueOf(dataResult), DateUtil.DATE_TIME_PATTERN_1));
					break;
				case REQUEST_CODE_AUTO_LOCKING:
					if (Boolean.valueOf(dataResult)) {
						tv_auto_locking.setText(R.string.on);
					} else {
						tv_auto_locking.setText(R.string.off);
					}
					break;
				case REQUEST_CODE_KEYPAD_VOLUME:
					if (Integer.valueOf(dataResult) == 1) {
						tv_lock_settings_keypad_volume.setText(R.string.on);
					} else {
						tv_lock_settings_keypad_volume.setText(R.string.off);
					}
					break;
			}
		}
	}

	private void initPicker() {

		mPickerHome = new OptionsPickerBuilder(this, new OnOptionsSelectListener() {
			@Override
			public void onOptionsSelect(int options1, int option2, int options3, View v) {
				if (CollectionUtil.isEmpty(mHomeList)) {
					return;
				}
				Home selectHome = mHomeList.get(options1);
				Home curHome = mKey.getHome();
				if (null != curHome && selectHome.getId().equals(curHome.getId())) {
					return;
				}
				bindLockHome(selectHome);
			}
		}).setLayoutRes(R.layout.pickerview_select_home_group, new CustomListener() {
			@Override
			public void customLayout(View v) {
				final TextView tvSubmit = v.findViewById(R.id.tv_finish);
				TextView tvCancel = v.findViewById(R.id.iv_cancel);
				tvSubmit.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mPickerHome.returnData();
						mPickerHome.dismiss();
					}
				});

				tvCancel.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mPickerHome.dismiss();
					}
				});
			}
		}).setLineSpacingMultiplier(2.5F)
				.isDialog(false)
				.build();
	}

	private void requestLockGroup() {
		RestClient.builder()
				.url(Urls.GET_HOME_MY_OWN)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mHomeList = GsonUtil.fromJson(result.getJSONArray("data").toJSONString(), new TypeToken<List<Home>>() {
							});
							if (CollectionUtil.isEmpty(mHomeList)) {
								return;
							}

							Home curHome = mKey.getHome();
							String currentHomeId = null != curHome ? curHome.getId() : "";
							List<String> mHomeNames = new ArrayList<>();
							int selectPosition = 0;
							for (int i = 0, len = mHomeList.size(); i < len; i++) {
								Home home = mHomeList.get(i);
								mHomeNames.add(home.getName());
								if (home.getId().equals(currentHomeId)) {
									selectPosition = i;
								}
							}

							mPickerHome.setSelectOptions(selectPosition);
							mPickerHome.setPicker(mHomeNames);
							mPickerHome.show();

						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
					}
				})
				.build()
				.get();
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		hideHelpPopupWindow();
	}
}
