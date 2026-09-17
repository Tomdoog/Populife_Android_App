package com.populstay.populife.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BluetoothBaseActivity;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.ui.widget.HelpPopupWindow;
import com.populstay.populife.util.log.PeachLogger;

import java.util.List;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;

public class LockAddGuideActivity extends BluetoothBaseActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final String KEY_FROM = "KEY_FROM";
	private static final String KEY_FINGERPRINT_CARD_TYPE = "KEY_FINGERPRINT_CARD_TYPE";
	private static final String KEY_KEY = "KEY_KEY";

	private TextView mTvNext, mTvPageTitle, mAddLockPreCheckHint;
	private CheckBox mCbBtOpen, mCbNetOpen, mCkBatteryInstall, mCbConfirmTime, mCbLbsOpen;
	private HelpPopupWindow mHelpPopupWindow;
	private ImageView mIvNewMsg, mIvAddLockGuidePic;
	private Key mKey = CURRENT_KEY;
	private String mLockType;
	private int mFrom;
	private int mFingerprintCardType = KeyPwdConstant.IType.TYPE_FINGERPRINT; // 添加类型：指纹 or 门卡（默认为指纹）

	/**
	 * 从添加 锁 跳转过来
	 */
	public static void actionStartAddLock(Context context, String lockType) {
		Intent intent = new Intent(context, LockAddGuideActivity.class);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	/**
	 * 从添加 指纹/门卡 跳转过来
	 */
	public static void actionStart(Context context, int from, int fingerprintCardType, Key key, String lockType) {
		Intent intent = new Intent(context, LockAddGuideActivity.class);
		intent.putExtra(KEY_FROM, from);
		intent.putExtra(KEY_FINGERPRINT_CARD_TYPE, fingerprintCardType);
		intent.putExtra(KEY_KEY, key);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	public static void actionStartByTaskTop(Context context, String lockType) {
		Intent intent = new Intent(context, LockAddGuideActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_add_guide);
		getIntentData();
		initView();
		initListener();
		initStatus();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mLockType = data.getStringExtra(KEY_LOCK_TYPE);
		mFrom = data.getIntExtra(KEY_FROM, 0);
		mFingerprintCardType = data.getIntExtra(KEY_FINGERPRINT_CARD_TYPE, KeyPwdConstant.IType.TYPE_FINGERPRINT);
		if (data.hasExtra(KEY_KEY)) {
			mKey = data.getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		mTvPageTitle = findViewById(R.id.page_left_title);
		mTvPageTitle.setVisibility(View.VISIBLE);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		mTvNext = findViewById(R.id.tv_lock_add_guide_next);
		mAddLockPreCheckHint = findViewById(R.id.add_lock_pre_check_hint);
		mIvAddLockGuidePic = findViewById(R.id.iv_add_lock_guide_pic);

		mCbBtOpen = findViewById(R.id.cb_bt_open);
		mCbLbsOpen = findViewById(R.id.cb_lbs_open);
		mCbNetOpen = findViewById(R.id.cb_net_open);
		mCkBatteryInstall = findViewById(R.id.ck_battery_install);
		mCbConfirmTime = findViewById(R.id.cb_confirm_time);
		initTitleBarRightBtn();
	}

	private void initTitleBarRightBtn() {
		TextView tvQuestion = findViewById(R.id.page_action);
		tvQuestion.setText("");
		tvQuestion.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.help_icon), null, null, null);

		tvQuestion.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//CommonQuestionDetailActivity.actionStart(LockAddGuideActivity.this, "0", "1");
				showHelpPopupWindow(v);
			}
		});
		tvQuestion.setVisibility(View.GONE);

		mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(LockAddGuideActivity.this);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (HomeDeviceInfo.isPadlock(mLockType)) refreshPadlockEnvironment();
	}

	private void checkBlePermission() {
		requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
						: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
				new PermissionListener() {
					@Override
					public void onGranted() {
						if (HomeDeviceInfo.isPadlock(mLockType)) {
							refreshPadlockEnvironment();
						} else {
							mCbLbsOpen.setChecked(isLbsEnableWithoutToast());
						}
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {
						toast(isAndroid12() && HomeDeviceInfo.isPadlock(mLockType)
								? R.string.padlock_bluetooth_permission
								: isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
						mCbLbsOpen.setChecked(false);
					}
				});
	}

	private void showHelpPopupWindow(View anchor) {
		if (null == mHelpPopupWindow) {
			mHelpPopupWindow = new HelpPopupWindow(this);
		}
		mHelpPopupWindow.show(anchor, Gravity.RIGHT);
	}

	private void initListener() {
		mTvNext.setOnClickListener(this);
		mCbBtOpen.setOnCheckedChangeListener(this);
		mCbLbsOpen.setOnCheckedChangeListener(this);
		mCbNetOpen.setOnCheckedChangeListener(this);
		mCkBatteryInstall.setOnCheckedChangeListener(this);
		mCbConfirmTime.setOnCheckedChangeListener(this);
	}

	private void initStatus() {
		if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) { // 添加指纹/门卡
			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
				mTvPageTitle.setText(R.string.add_fingerprint_pre_check);
				mAddLockPreCheckHint.setText(R.string.add_fingerprint_pre_check_hint);
			} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
				mTvPageTitle.setText(R.string.add_ic_card_pre_check);
				mAddLockPreCheckHint.setText(R.string.add_ic_card_pre_check_hint);
			}
			mCkBatteryInstall.setText(R.string.door_lock_battery_enough_keypad_active_hint);
			mCbConfirmTime.setText(R.string.door_lock_distance_confirm);
			if (HomeDeviceInfo.isKeyBoxK4(mLockType)){
				mIvAddLockGuidePic.setImageResource(R.drawable.keybox_k4_check);
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)) {
				mIvAddLockGuidePic.setImageResource(R.drawable.product_moonlock);
			} else {
				mIvAddLockGuidePic.setImageResource(R.drawable.door_lock_check);
			}
		} else { // 添加门锁
			mTvPageTitle.setText(R.string.add_lock_pre_check);
			if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
				mAddLockPreCheckHint.setText(R.string.add_deadbolt_lock_pre_check_hint);
				mCkBatteryInstall.setText(R.string.deadbolt_battery_install_hint);
				mCbConfirmTime.setText(R.string.deadbolt_distance_confirm);
				mIvAddLockGuidePic.setImageResource(R.drawable.deadbolt_check);
			} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
				mAddLockPreCheckHint.setText(R.string.add_key_box_lock_pre_check_hint);
				mCkBatteryInstall.setText(R.string.key_box_battery_install_hint);
				mCbConfirmTime.setText(R.string.key_box_distance_confirm);
				mIvAddLockGuidePic.setImageResource(R.drawable.keybox_check);
			} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
				mAddLockPreCheckHint.setText(R.string.add_key_box_lock_pre_check_hint_k4);
				mCkBatteryInstall.setText(R.string.key_box_battery_install_hint_k4);
				mCbConfirmTime.setText(R.string.key_box_distance_confirm_k4);
				mIvAddLockGuidePic.setImageResource(R.drawable.keybox_k4_check);
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				mAddLockPreCheckHint.setText(R.string.add_moon_lock_pre_check_hint);
				mCkBatteryInstall.setText(R.string.moonlock_battery_install_hint);
				mCbConfirmTime.setText(R.string.moonlock_distance_confirm);
				mIvAddLockGuidePic.setImageResource(R.drawable.product_moonlock);
			} else {
				mAddLockPreCheckHint.setText(R.string.add_door_lock_pre_check_hint);
				mCkBatteryInstall.setText(R.string.door_lock_battery_install_hint);
				mCbConfirmTime.setText(R.string.door_lock_distance_confirm);
				mIvAddLockGuidePic.setImageResource(R.drawable.door_lock_check);
			}
		}

		if (HomeDeviceInfo.isPadlock(mLockType)) {
			mIvAddLockGuidePic.setImageResource(R.drawable.padlock_outline);
			mIvAddLockGuidePic.setAdjustViewBounds(true);
			mIvAddLockGuidePic.setMaxHeight((int) (220 * getResources().getDisplayMetrics().density));
			boolean fingerprint = mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD;
			mCkBatteryInstall.setText(fingerprint
					? R.string.padlock_fingerprint_battery_hint : R.string.padlock_battery_hint);
			mCbConfirmTime.setText(R.string.padlock_distance_hint);
			mAddLockPreCheckHint.setText(R.string.padlock_wake_hint);
			TextView safetyHint = findViewById(R.id.padlock_safety_hint);
			safetyHint.setText(fingerprint ? R.string.padlock_fingerprint_safety : R.string.padlock_pairing_safety);
			safetyHint.setVisibility(View.VISIBLE);
			if (isAndroid12()) mCbLbsOpen.setText(R.string.padlock_permission_check);
			mCbLbsOpen.setOnClickListener(v -> checkBlePermission());
			refreshPadlockEnvironment();
			return;
		}

		mCbBtOpen.setChecked(isBleEnableWithoutToast());
		mCbLbsOpen.setChecked(isLbsEnableWithoutToast());
		mCbNetOpen.setChecked(isNetEnableWithoutToast());
	}

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_lock_add_guide_next) {
            if (HomeDeviceInfo.isPadlock(mLockType)) {
                refreshPadlockEnvironment();
                if (!mTvNext.isEnabled()) return;
            }
            // 添加指纹/门卡或门锁
            if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) {
                FingerprintIcCardAddConfigActivity.actionStart(
                        LockAddGuideActivity.this,
                        mFrom,
                        mFingerprintCardType,
                        mKey,
                        mLockType
                );
            } else {
                ActivateDeviceActivity.actionStart(
                        LockAddGuideActivity.this,
                        mLockType
                );
            }
        }
    }


    @Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		setNextBtnEnable();
	}

	private void setNextBtnEnable() {
		boolean nextEnable = mCbBtOpen.isChecked()
				&& mCbLbsOpen.isChecked()
				&& mCbNetOpen.isChecked()
				&& mCkBatteryInstall.isChecked()
				&& mCbConfirmTime.isChecked();
		mTvNext.setEnabled(nextEnable);
	}

	private boolean hasPadlockScanPermission() {
		if (isAndroid12()) {
			return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
					&& ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
		}
		return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private void refreshPadlockEnvironment() {
		boolean permissionGranted = hasPadlockScanPermission();
		mCbBtOpen.setChecked((!isAndroid12() || permissionGranted) && isBleEnableWithoutToast());
		mCbLbsOpen.setChecked(permissionGranted && (isAndroid12() || isLbsEnableWithoutToast()));
		mCbLbsOpen.setEnabled(!permissionGranted);
		mCbNetOpen.setChecked(isNetEnableWithoutToast());
		setNextBtnEnable();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (null != mHelpPopupWindow) {
			mHelpPopupWindow.dismiss();
		}
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) { // 添加指纹/门卡
			if (Event.EventType.ADD_IC_CARD_SUCCESS == event.type) { // 添加门卡成功
				finish();
			}
		}
	}

	@Override
	public void onBluetoothStateChanged(boolean isOpen) {
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			refreshPadlockEnvironment();
			return;
		}
		mCbBtOpen.setChecked(isOpen);
	}

	@Override
	public void onLocationStateChanged(boolean isOpen) {
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			refreshPadlockEnvironment();
			return;
		}
		mCbLbsOpen.setChecked(isOpen);
	}

	@Override
	public void onNetStateChange(boolean isNetEnable) {
		mCbNetOpen.setChecked(isNetEnable);
	}
}
