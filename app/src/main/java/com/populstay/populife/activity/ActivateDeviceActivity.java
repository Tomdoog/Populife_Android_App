package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.entity.Key;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.manhattanlock.MHILockEnterAddFingerprint;
import com.populstay.populife.util.locale.LanguageUtil;
import com.populstay.populife.util.log.PeachLogger;

import java.util.List;

import androidx.annotation.Nullable;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;

/**
 * 确认设备已经亮起来了
 */
public class ActivateDeviceActivity extends BaseActivity implements View.OnClickListener {

	public static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	public static final String KEY_FROM = "KEY_FROM";
	public static final String KEY_FINGERPRINT_CARD_TYPE = "KEY_FINGERPRINT_CARD_TYPE";
	public static final String KEY_KEY = "KEY_KEY";
	public static final String KEY_FINGERPRINT_CARD_NAME = "KEY_FINGERPRINT_CARD_NAME";
	public static final String KEY_FINGERPRINT_CARD_REMARK = "KEY_FINGERPRINT_CARD_REMARK";
	public static final String KEY_VALIDATED_TYPE = "KEY_VALIDATED_TYPE";
	public static final String KEY_START_TIME = "KEY_START_TIME";
	public static final String KEY_END_TIME = "KEY_END_TIME";
	private ImageView mIvAddDevicePic, mIvNewMsg;
	private TextView mTvPageTitle, mTvNext, add_device_activate_hint1, add_device_activate_hint1_1, add_device_activate_hint2;
	private Key mKey = CURRENT_KEY;
	private String mLockType;
	private int mFrom;
	private int mFingerprintCardType = KeyPwdConstant.IType.TYPE_FINGERPRINT; // 添加类型：指纹 or 门卡（默认为指纹）
	private String mFingerCardName;
	private String mFingerCardRemark;
	private int mFingerCardValidType;
	private String mStartTime;
	private String mEndTime;

	// 添加新锁
	public static void actionStart(Context context, String lockType) {
		Intent intent = new Intent(context, ActivateDeviceActivity.class);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	//	mFingerprintCardType, name, remarks, mValidPeriodType,
//			mTvStartTime.getText().toString(), mTvEndTime.getText().toString()
	// 添加指纹
	public static void actionStart(Context context, int from, int fingerprintCardType, Key key, String lockType, String name,
								   String remark, int validPeriodType, String startTime, String endTime) {
		Intent intent = new Intent(context, ActivateDeviceActivity.class);
		intent.putExtra(KEY_FROM, from);
		intent.putExtra(KEY_FINGERPRINT_CARD_TYPE, fingerprintCardType);
		intent.putExtra(KEY_KEY, key);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		intent.putExtra(KEY_FINGERPRINT_CARD_NAME, name);
		intent.putExtra(KEY_FINGERPRINT_CARD_REMARK, remark);
		intent.putExtra(KEY_VALIDATED_TYPE, validPeriodType);
		intent.putExtra(KEY_START_TIME, startTime);
		intent.putExtra(KEY_END_TIME, endTime);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_activate_device);
		getIntentData();
		initView();
		setListener();
		initStatus();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mLockType = data.getStringExtra(KEY_LOCK_TYPE);
		if (data.hasExtra(KEY_FROM)) {
			mFrom = data.getIntExtra(KEY_FROM, 0);
		}
		if (data.hasExtra(KEY_FINGERPRINT_CARD_TYPE)) {
			mFingerprintCardType = data.getIntExtra(KEY_FINGERPRINT_CARD_TYPE, 0);
		}
		if (data.hasExtra(KEY_KEY)) {
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
			if (mLockType == null) {
				mLockType = mKey.getLockName();
			}
		}
		if (data.hasExtra(KEY_FINGERPRINT_CARD_NAME)) {
			mFingerCardName = data.getStringExtra(KEY_FINGERPRINT_CARD_NAME);
		}
		if (data.hasExtra(KEY_FINGERPRINT_CARD_REMARK)) {
			mFingerCardRemark = data.getStringExtra(KEY_FINGERPRINT_CARD_REMARK);
		}
		if (data.hasExtra(KEY_VALIDATED_TYPE)) {
			mFingerCardValidType = data.getIntExtra(KEY_VALIDATED_TYPE, 0);
		}
		if (data.hasExtra(KEY_START_TIME)) {
			mStartTime = data.getStringExtra(KEY_START_TIME);
		}
		if (data.hasExtra(KEY_END_TIME)) {
			mEndTime = data.getStringExtra(KEY_END_TIME);
		}

//		private String mFingerCardName;
//		private String mFingerCardRemark;
//		private int mFingerCardValidType;
//		private String mStartTime;
//		private String mEndTime;
//		intent.putExtra(KEY_FINGERPRINT_CARD_NAME,name);
//		intent.putExtra(KEY_FINGERPRINT_CARD_REMARK,remark);
//		intent.putExtra(KEY_VALIDATED_TYPE,validPeriodType);
//		intent.putExtra(KEY_START_TIME,startTime);
//		intent.putExtra(KEY_END_TIME,endTime);
	}

	private void initView() {
		mIvAddDevicePic = findViewById(R.id.iv_add_device_pic);
		mTvPageTitle = findViewById(R.id.page_left_title);
		mTvPageTitle.setVisibility(View.VISIBLE);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		mTvNext = findViewById(R.id.tv_next);
		if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) { // 添加指纹/门卡
			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
				mTvNext.setText(R.string.start_add_fingerprint_next);
			}
		}
		add_device_activate_hint1 = findViewById(R.id.add_device_activate_hint1);
		add_device_activate_hint1_1 = findViewById(R.id.add_device_activate_hint1_1);
		add_device_activate_hint2 = findViewById(R.id.add_device_activate_hint2);
		initProgressStatus();
		initTitleBarRightBtn();
	}

	private void initProgressStatus() {
		ImageView ivStatus1 = findViewById(R.id.iv_status_1);
		ImageView ivStatus2 = findViewById(R.id.iv_status_2);
		ImageView ivStatus3 = findViewById(R.id.iv_status_3);
		View line1 = findViewById(R.id.line_1);
		View line2 = findViewById(R.id.line_2);

		ivStatus1.setImageResource(R.drawable.status_pre_check_red);
		ivStatus2.setImageResource(R.drawable.status_activate_red);
		ivStatus3.setImageResource(R.drawable.status_pair);
		line1.setBackgroundColor(getResources().getColor(R.color.edit_focus_line));
		line2.setBackgroundColor(getResources().getColor(R.color.gray_medium));
	}

	private void initTitleBarRightBtn() {
		TextView tvQuestion = findViewById(R.id.page_action);
		tvQuestion.setText("");
		tvQuestion.setVisibility(View.GONE);
		tvQuestion.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.help_icon), null, null, null);


		tvQuestion.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String pdfAssetName = "user_manual_gateway_en.pdf";
				if (LanguageUtil.isChinese(ActivateDeviceActivity.this)) {
					pdfAssetName = "user_manual_gateway_cn.pdf";
				}
				PDFActivity.actionStart(ActivateDeviceActivity.this, getString(R.string.user_manual_gateway),
						pdfAssetName, true);
			}
		});

		mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(ActivateDeviceActivity.this);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void setListener() {
		mTvNext.setOnClickListener(this);
	}

	private void initStatus() {
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			mTvPageTitle.setText(R.string.activate_padlock);
			mIvAddDevicePic.setImageResource(R.drawable.padlock_outline);
			mIvAddDevicePic.setAdjustViewBounds(true);
			mIvAddDevicePic.setMaxHeight((int) (220 * getResources().getDisplayMetrics().density));
			add_device_activate_hint1.setText(R.string.padlock_wake_hint);
			add_device_activate_hint2.setVisibility(View.GONE);
			return;
		}
		if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) { // 添加指纹/门卡
			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
				mTvPageTitle.setText(R.string.activate_moon_lock);
				mIvAddDevicePic.setImageResource(R.drawable.product_moonlock);
				add_device_activate_hint1.setText(R.string.moon_lock_add_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.moon_lock_add_device_activate_hint2);
			} else {
				mTvPageTitle.setText(R.string.activate_moon_lock);
				mIvAddDevicePic.setImageResource(R.drawable.product_moonlock);
				add_device_activate_hint1.setText(R.string.moon_lock_add_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.moon_lock_add_device_activate_hint2);
			}
		} else {
			if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
				mTvPageTitle.setText(R.string.activate_the_deadbolt);
				mIvAddDevicePic.setImageResource(R.drawable.deadbolt_activate);
				add_device_activate_hint1.setText(R.string.deadbolt_add_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.deadbolt_add_device_activate_hint2);
			} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
				mTvPageTitle.setText(R.string.activate_the_keybox);
				mIvAddDevicePic.setImageResource(R.drawable.keybox_opened_with_hand);
				add_device_activate_hint1.setText(R.string.key_box_add_device_activate_hint1);
				add_device_activate_hint1_1.setVisibility(View.VISIBLE);
				add_device_activate_hint1_1.setText(R.string.key_box_add_device_activate_hint1_1);
				add_device_activate_hint2.setText(R.string.key_box_add_device_activate_hint2);
			} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
				mTvPageTitle.setText(R.string.activate_the_keybox_k4);
				mIvAddDevicePic.setImageResource(R.drawable.keybox_k4_inactive_2);
				add_device_activate_hint1.setText(R.string.key_box_add_device_activate_hint1_k4);
				add_device_activate_hint1_1.setVisibility(View.VISIBLE);
				add_device_activate_hint1_1.setText(R.string.key_box_add_device_activate_hint1_1_k4);
				add_device_activate_hint2.setVisibility(View.GONE);
				add_device_activate_hint2.setText(R.string.key_box_add_device_activate_hint2_k4);
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KJX_DOOR_LOCK)) {
				mTvPageTitle.setText(R.string.activate_door_lock);
				mIvAddDevicePic.setImageResource(R.drawable.door_lock_activate);
				add_device_activate_hint1.setText(R.string.door_lock_add_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.door_lock_add_device_activate_hint2);
			} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				mTvPageTitle.setText(R.string.activate_moon_lock);
				mIvAddDevicePic.setImageResource(R.drawable.product_moonlock);
				add_device_activate_hint1.setText(R.string.moon_lock_add_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.moon_lock_add_device_activate_hint2);
			} else {
				mTvPageTitle.setText(R.string.activate_door_lock);
			}

		}
	}

	@Override
	public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tv_next) {
            if (mFrom == KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD) { // 添加指纹/门卡
                if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
                    if (isBleNetEnableWithToast() && isLbsEnableWithToast()) {
                        IcCardBluetoothAddActivity.actionStart(ActivateDeviceActivity.this,
                                mFingerprintCardType, mFingerCardName, mFingerCardRemark, mFingerCardValidType,
                                mStartTime, mEndTime, mKey, mLockType);
                    }
                } else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) {
//						if (isBleNetEnableWithToast() && isLbsEnableWithToast()) {
                    //							showLoading();
//						setAddFingerprintCallback();
//							if (mKey.getLockId() < 0) {
//								if (sPPLOCK.isConnected(mKey.getLockMac())) {
//									String fingerType = "00";
//									if (mFingerCardValidType==1){
//										fingerType = "01";
//									}
//									String priority = "00";
//									if (!mKey.isAdmin()){
//										priority = "01";
//									}
//									sPPLOCK.addFingerprint(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),
//											fingerType,priority, DateUtil.getStringToDate(mStartTime, "yyyy-MM-dd HH:mm"),
//											DateUtil.getStringToDate(mEndTime, "yyyy-MM-dd HH:mm"),mKey.getK1());
//								} else {
//									//sPPLOCK.connect(mKey.getLockMac());
//									startLockActionScan();
//								}
//							}
//						}
                    IcCardBluetoothAddActivity.actionStart(ActivateDeviceActivity.this,
                            mFingerprintCardType, mFingerCardName, mFingerCardRemark, mFingerCardValidType,
                            mStartTime, mEndTime, mKey, mLockType);

                }
            } else {
                if (isBleNetEnableWithToast() && isLbsEnableWithToast()) {
                    FoundDeviceActivity.actionStart(this, mLockType);
                }
            }
        }
	}

	private void setAddFingerprintCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.ADD_FINGERPRINT);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockEnterAddFingerprint(new MHILockEnterAddFingerprint() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							IcCardBluetoothAddActivity.actionStart(ActivateDeviceActivity.this,
									mFingerprintCardType, mFingerCardName, mFingerCardRemark, mFingerCardValidType,
									mStartTime, mEndTime, mKey, mLockType);
						}
					});
				}

				@Override
				public void onFail() {
					toastFail();
				}
			});
		}
	}

	private void setAddCardCallback() {
//		if (mKey.getLockId() < 0) {
//			MyApplication.pplBleSession.setOperation(LockOperation.ADD_CARD);
//			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
//			MyApplication.pplBleSession.setmILockAdd(new MHILockEnterAddFingerprint() {
//				@Override
//				public void onSuccess() {
//					runOnUiThread(new Runnable() {
//						@Override
//						public void run() {
//							stopLoading();
//							IcCardBluetoothAddActivity.actionStart(ActivateDeviceActivity.this,
//									mFingerprintCardType, mFingerCardName, mFingerCardRemark, mFingerCardValidType,
//									mStartTime, mEndTime,mKey,mLockType);
//						}
//					});
//				}
//
//				@Override
//				public void onFail() {
//					toastFail();
//				}
//			});
//		}
	}
}
