package com.populstay.populife.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.net.BackendError;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.CreatePwdKeyActionInfo;
import com.populstay.populife.lock.ILockFingerprintAdd;
import com.populstay.populife.lock.ILockFingerprintModifyPeriod;
import com.populstay.populife.lock.ILockIcCardAdd;
import com.populstay.populife.lock.ILockIcCardModifyPeriod;
import com.populstay.populife.manhattanlock.MHILockAddCard;
import com.populstay.populife.manhattanlock.MHILockAddFingerprint;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.AddFingerprintCallback;
import com.ttlock.bl.sdk.callback.AddICCardCallback;
import com.ttlock.bl.sdk.callback.ModifyFingerprintPeriodCallback;
import com.ttlock.bl.sdk.callback.ModifyICCardPeriodCallback;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class IcCardBluetoothAddActivity extends BaseActivity {
	private static final String KEY_FINGERPRINT_IC_CARD_NAME = "KEY_FINGERPRINT_IC_CARD_NAME";
	private static final String KEY_FINGERPRINT_REMARKS = "KEY_FINGERPRINT_REMARKS";
	private static final String KEY_FINGERPRINT_IC_CARD_VALID_TYPE = "KEY_FINGERPRINT_IC_CARD_VALID_TYPE";
	private static final String KEY_FINGERPRINT_IC_CARD_START_DATE = "KEY_FINGERPRINT_IC_CARD_START_DATE";
	private static final String KEY_FINGERPRINT_IC_CARD_END_DATE = "KEY_FINGERPRINT_IC_CARD_END_DATE";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final String KEY_KEY = "KEY_KEY";
	private static final String KEY_FINGERPRINT_CARD_TYPE = "KEY_FINGERPRINT_CARD_TYPE";

	private ImageView mIvAddDevicePic, mIvNewMsg, mIvHintSuccess;
	private TextView mTvPageTitle, mTvHintSuccess, mTvHintBack, add_device_activate_hint1, add_device_activate_hint2;
	private LinearLayout mLlHintSuccess;

	private Key mKey = MyApplication.CURRENT_KEY;
	private String mLockType;
	private boolean isMHLock = false;
	private String mFingerprintCardName, mFingerprintRemarks, mStartDate, mEndDate;
	private CreatePwdKeyActionInfo mActionInfo = new CreatePwdKeyActionInfo();
	private int mFingerprintCardType = KeyPwdConstant.IType.TYPE_FINGERPRINT; // 添加类型：指纹 or 门卡（默认为指纹）
	private int mValidPeriodType = KeyPwdConstant.IFingerprintCardValidType.PERMANENT; // 指纹/门卡有效类型（1 限时，2 永久）
	private int mTotalCount;
	private boolean mIsLockOperationSuccessAdd, mIsLockOperationSuccessModify;
	private boolean hasClickToAddFrCard = false;
	private int mCurrentScanProgress = 0;
	private int SCAN_TIME_OUT_SECONDS = 30;

	private Handler mHandler = new Handler();
	private Runnable mTimeOutRunnable = new Runnable() {
		@Override
		public void run() {
			/* 超时处理 */
			addFail();
		}
	};

//	private Runnable mRunnable = new Runnable() {
//		@Override
//		public void run() {
//			lockAddIcCard();
//			BaseApplication.getHandler().postDelayed(mRunnable, 5000);
//		}
//	};

//	private Runnable mRunnable = new Runnable() {
//		@Override
//		public void run() {
//			Log.e("mhs", "mRunnable--mCurrentScanProgress=" + mCurrentScanProgress);
//			if (mCurrentScanProgress >= SCAN_TIME_OUT_SECONDS) {
//				addFail();
//			} else {
//				updateTime();
//			}
//		}
//	};

//	private void updateTime () {
//
//		mCurrentScanProgress ++;
//
//		BaseApplication.getHandler().postDelayed(mRunnable, 500);
//
//	}

	public static void actionStart(Context context, int fingerprintCardType, String fingerprintCardName,
								   String fingerprintRemarks, int validPeriodType, String startDate, String endDate, Key key, String lockType) {
		Intent intent = new Intent(context, IcCardBluetoothAddActivity.class);
		intent.putExtra(KEY_FINGERPRINT_CARD_TYPE, fingerprintCardType);
		intent.putExtra(KEY_FINGERPRINT_IC_CARD_NAME, fingerprintCardName);
		intent.putExtra(KEY_FINGERPRINT_REMARKS, fingerprintRemarks);
		intent.putExtra(KEY_FINGERPRINT_IC_CARD_VALID_TYPE, validPeriodType);
		intent.putExtra(KEY_FINGERPRINT_IC_CARD_START_DATE, startDate);
		intent.putExtra(KEY_FINGERPRINT_IC_CARD_END_DATE, endDate);
		intent.putExtra(KEY_KEY, key);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ic_card_bluetooth_add);
		getIntentData();
		initView();
		setListener();
		refreshStatus(true);

//		BaseApplication.getHandler().postDelayed(mRunnable, 5000);
	}

	private void getIntentData() {
		Intent data = getIntent();
		mFingerprintCardType = data.getIntExtra(KEY_FINGERPRINT_CARD_TYPE, KeyPwdConstant.IType.TYPE_FINGERPRINT);
		mFingerprintCardName = data.getStringExtra(KEY_FINGERPRINT_IC_CARD_NAME);
		mFingerprintRemarks = data.getStringExtra(KEY_FINGERPRINT_REMARKS);
		mValidPeriodType = data.getIntExtra(KEY_FINGERPRINT_IC_CARD_VALID_TYPE, KeyPwdConstant.IFingerprintCardValidType.PERMANENT);
		mStartDate = data.getStringExtra(KEY_FINGERPRINT_IC_CARD_START_DATE);
		mEndDate = data.getStringExtra(KEY_FINGERPRINT_IC_CARD_END_DATE);
		if (data.hasExtra(KEY_LOCK_TYPE)) {
			mLockType = data.getStringExtra(KEY_LOCK_TYPE);
			if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				isMHLock = true;
			}
		}
		if (data.hasExtra(KEY_KEY)) {
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
			if (mLockType == null) {
				mLockType = mKey.getLockName();
				if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
						|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
					isMHLock = true;
				}
			}
		}
	}

	private void initView() {
		mIvAddDevicePic = findViewById(R.id.iv_add_device_pic);
		mLlHintSuccess = findViewById(R.id.ll_hint_success);
		mIvHintSuccess = findViewById(R.id.iv_hint_success);
		mTvHintSuccess = findViewById(R.id.tv_hint_success);
		mTvHintBack = findViewById(R.id.tv_hint_back);
		mTvPageTitle = findViewById(R.id.page_left_title);
		mTvPageTitle.setVisibility(View.VISIBLE);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		add_device_activate_hint1 = findViewById(R.id.add_device_activate_hint1);
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
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(IcCardBluetoothAddActivity.this);
			}
		});
	}

	private void setListener() {
		mLlHintSuccess.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!hasClickToAddFrCard) {
					if (HomeDeviceInfo.isPadlock(mLockType) && !isBleNetEnableWithToast()) return;
					addFpCard();
					hasClickToAddFrCard = true;
					mTvHintBack.setEnabled(false);
				}
			}
		});
	}

	private void refreshStatus(boolean isInit) {
		if (isInit) {
			mTvPageTitle.setText(R.string.activate_door_lock);
			if (HomeDeviceInfo.isKeyBoxK4(mLockType)){
				mIvAddDevicePic.setImageResource(R.drawable.keybox_k4_inactive_2);
			}else {
				mIvAddDevicePic.setImageResource(R.drawable.door_lock_activate);
			}
			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
				add_device_activate_hint1.setText(R.string.add_fingerprint_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.add_fingerprint_device_activate_hint2);
				mTvHintSuccess.setText(R.string.start_registering_fingerprint);
			} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
				add_device_activate_hint1.setText(R.string.add_ic_card_device_activate_hint1);
				add_device_activate_hint2.setText(R.string.add_ic_card_device_activate_hint2);
				mTvHintSuccess.setText(R.string.start_adding_ic_card);
			}
		} else {
			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
				mTvPageTitle.setText(R.string.adding_fingerprint_title);
				if (HomeDeviceInfo.isKeyBoxK4(mLockType)){
					mIvAddDevicePic.setImageResource(R.drawable.keybox_k4_inactive_2);
				}else {
					mIvAddDevicePic.setImageResource(R.drawable.fingerprint_add_guide_img);
				}
				add_device_activate_hint1.setText(Html.fromHtml(String.format(getString(R.string.adding_fingerprint_hint_1), "-")));
				add_device_activate_hint2.setText(R.string.adding_fingerprint_hint_2);
				mTvHintSuccess.setText(R.string.complete_above_operation);
			} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
				mTvPageTitle.setText(R.string.adding_ic_card_title);
				if (HomeDeviceInfo.isKeyBoxK4(mLockType)){
					mIvAddDevicePic.setImageResource(R.drawable.keybox_k4_inactive_2);
				}else {
					mIvAddDevicePic.setImageResource(R.drawable.ic_card_add_guide_img);
				}
				add_device_activate_hint1.setText(R.string.adding_ic_card_hint_1);
				add_device_activate_hint2.setText(R.string.adding_ic_card_hint_2);
				mTvHintSuccess.setText(R.string.complete_above_operation);
			}
			mTvHintBack.setEnabled(false);
		}
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			mIvAddDevicePic.setImageResource(R.drawable.padlock_outline);
			mIvAddDevicePic.setAdjustViewBounds(true);
			mIvAddDevicePic.setMaxHeight((int) (220 * getResources().getDisplayMetrics().density));
			if (isInit) {
				mTvPageTitle.setText(R.string.activate_padlock);
				add_device_activate_hint1.setText(R.string.padlock_fingerprint_keep_open);
				add_device_activate_hint2.setText(R.string.padlock_fingerprint_activation);
				mTvHintSuccess.setText(R.string.padlock_start_fingerprint);
			} else {
				add_device_activate_hint1.setText(R.string.padlock_fingerprint_enrollment);
				add_device_activate_hint2.setText(R.string.padlock_fingerprint_retry);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void addFpCard() {
		if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
			if (mKey.getLockId() < 0) {
				refreshStatus(true);
				mh_updateNoteText(5);
				//refreshDownTime();
				showLoading();
				setAddFingerprintCallback();
				if (mKey.getLockId() < 0) {
					String fingerType = "00";
					if (mValidPeriodType == 1) {
						fingerType = "01";
					}
					String priority = "00";
					if (!mKey.isAdmin()) {
						priority = "01";
					}
					if (sPPLOCK.isConnected(mKey.getLockMac())) {
						sPPLOCK.addFingerprint(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()),
								fingerType, priority, DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"),
								DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"), mKey.getK1());
					} else {
						//sPPLOCK.connect(mKey.getLockMac());
						MyApplication.pplBleSession.setFingerType(fingerType);
						MyApplication.pplBleSession.setFingerPriority(priority);
						MyApplication.pplBleSession.setStartDate(DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"));
						MyApplication.pplBleSession.setEndDate(DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"));
						startLockActionScan();
					}
				}

			} else {
				lockAddFingerprint();
			}
		} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
			if (mKey.getLockId() < 0) {
				mh_addIcCard();
			} else {
				lockAddIcCard();
			}
		}
	}


	private void lockAddFingerprint() {
		showLoading();
		setAddFingerprintCallback();
		if (mKey.getLockId() < 0) {
//			if (sPPLOCK.isConnected(mKey.getLockMac())) {
//				String fingerType = "00";
//				if (mValidPeriodType==1){
//					fingerType = "01";
//				}
//				String priority = "00";
//				if (!mKey.isAdmin()){;
//					priority = "01";
//				}
//				sPPLOCK.addFingerprint(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),
//						fingerType,priority,DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"),
//						DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"),mKey.getK1());
//			} else {
//				//sPPLOCK.connect(mKey.getLockMac());
//				startLockActionScan();
//			}
		} else {
            mTTLockAPI.addFingerprint(0, 0, mKey.getLockData(), mKey.getLockMac(), new AddFingerprintCallback() {
                @Override
                public void onEnterAddMode(int i) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            refreshStatus(false);
                            updateNoteText(0, i);
                        }
                    });
                }

                @Override
                public void onCollectFingerprint(int i) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateNoteText(i, mTotalCount);
                        }
                    });
                }

                @Override
                public void onAddFingerpintFinished(long l) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateNoteText(mTotalCount, mTotalCount);
                            if (mValidPeriodType == KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED) {
                                // 限时指纹，需要再调用一个修改期限的 SDK 方法
                                lockModifyFingerprintPeriod(l);
                            } else {
                                // 永久指纹，直接上传数据到服务器
                                mIsLockOperationSuccessAdd = true;
                                requestAddFingerprint(String.valueOf(l));
                            }
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    if (!mIsLockOperationSuccessAdd) {
                        addFail();
                    }
                }
            });
		}

	}

	private void setAddFingerprintCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.ADD_FINGERPRINT);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockAddFingerprint(new MHILockAddFingerprint() {
				@Override
				public void onSuccess(final int step, final String fingerId) {

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
//					stopLoading();
//					refreshStatus(false);
//					updateNoteText(step, 3);
//					if ((fingerId !=null) && (step == 3)){
//						// 永久指纹，直接上传数据到服务器
//						mIsLockOperationSuccessAdd = true;
//						requestAddFingerprint(String.valueOf(fingerId));
//					}
							if (step > 0) {
								mHandler.removeCallbacks(mTimeOutRunnable);
							}
							//BaseApplication.getHandler().removeCallbacks(mRunnable);
							updateNoteText(step, 5);
							if (step < 5) {
								if (step == 0) {
									stopLoading();
								}
								toast(R.string.add_fingerprint_step_hint);
								//refreshDownTime();
								//refreshStatus(true);
								mTvHintSuccess.setText(R.string.complete_above_operation);
								mHandler.postDelayed(mTimeOutRunnable, 60 * 1000); // 超时检测时间 1s
							} else {
								if ((fingerId != null) && (step == 5)) {
									toast(R.string.add_fingerprint_success_hint);
									// 永久指纹，直接上传数据到服务器
									mIsLockOperationSuccessAdd = true;
									refreshStatus(false);
									toast("五次指纹录入成功，将上传数据到服务器");
									showLoading();
									requestAddFingerprint(fingerId);
								}
							}

						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mIsLockOperationSuccessAdd = true;
							//BaseApplication.getHandler().removeCallbacks(mRunnable);
							addFail();

						}
					});

				}

			});
		} else {

		}
	}

	private void updateNoteText(int currentCount, int totalCount) {
		mTotalCount = totalCount;
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			String instructions = getString(R.string.padlock_fingerprint_enrollment);
			if (totalCount > 0) {
				instructions += "\n\n" + getString(R.string.padlock_fingerprint_progress,
						Math.max(0, Math.min(currentCount, totalCount)), totalCount);
			}
			add_device_activate_hint1.setText(instructions);
			return;
		}
		int leftCount = mTotalCount - currentCount;
		add_device_activate_hint1.setText(Html.fromHtml(String.format(getString(R.string.adding_fingerprint_hint_1), leftCount == 0 ? "-" : "" + leftCount)));
	}

	private void mh_updateNoteText(int totalCount) {
		if (HomeDeviceInfo.isPadlock(mLockType)) {
			updateNoteText(0, 0);
			return;
		}
		int leftCount = 0;
		add_device_activate_hint1.setText(Html.fromHtml(String.format(getString(R.string.adding_fingerprint_hint_1), leftCount == 0 ? "-" : "" + leftCount)));
	}

	private void addFail() {
        addFail(HomeDeviceInfo.isPadlock(mLockType)
                ? R.string.padlock_fingerprint_failed : R.string.operation_fail);
    }

    private void addFail(int messageRes) {
		runOnUiThread(() -> {
			stopLoading();
            toast(messageRes);
			finish();
		});
	}

	private void lockAddIcCard() {
		showLoading();
        mTTLockAPI.addICCard(0, 0, mKey.getLockData(), mKey.getLockMac(), new AddICCardCallback() {
            @Override
            public void onEnterAddMode() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        refreshStatus(false);
                    }
                });
            }

            @Override
            public void onAddICCardSuccess(long l) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//						BaseApplication.getHandler().removeCallbacks(mRunnable);
                        // 限时 IC 卡，需要再调用一个修改期限的 SDK 方法
                        if (mValidPeriodType == KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED) {
                            lockModifyIcCardPeriod(l);
                        } else {
                            requestAddIcCard(String.valueOf(l));
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
                        toast(R.string.adding_ic_card_operation_fail);
                        finish();
                    }
                });
            }
        });
	}

	private void mh_addIcCard() {
		showLoading();
		setMH_addIcCardCallback();
		String fingerType = "00";
		if (mValidPeriodType == 1) {
			fingerType = "01";
		}
		String priority = "00";
		if (!mKey.isAdmin()) {
			priority = "01";
		}
		if (sPPLOCK.isConnected(mKey.getLockMac())) {
			sPPLOCK.addCard(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()),
					fingerType, priority, DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"),
					DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"), mKey.getK1());
		} else {
			MyApplication.pplBleSession.setCardType(fingerType);
			MyApplication.pplBleSession.setCardPriority(priority);
			MyApplication.pplBleSession.setStartDate(DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"));
			MyApplication.pplBleSession.setEndDate(DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"));
			startLockActionScan();
		}
	}

	private void setMH_addIcCardCallback() {
		MyApplication.pplBleSession.setOperation(LockOperation.ADD_CARD);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setmILockAddCard(new MHILockAddCard() {
			@Override
			public void onSuccess(final String cardId) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						refreshStatus(false);
						if (cardId == null) {
							mTvHintSuccess.setText(R.string.complete_above_operation);
							mHandler.postDelayed(mTimeOutRunnable, 60 * 1000); // 超时检测时间 1s
						}
						if (cardId != null) {
							mHandler.removeCallbacks(mTimeOutRunnable);
							requestAddIcCard(cardId);
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
						toast(R.string.adding_ic_card_operation_fail);
						finish();
					}
				});

			}
		});
	}


	private void lockModifyIcCardPeriod(long cardNumber) {
		showLoading();
        mTTLockAPI.modifyICCardValidityPeriod(
                DateUtil.getStringToDate(mStartDate, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM),
                DateUtil.getStringToDate(mEndDate, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM),
                String.valueOf(cardNumber),
                mKey.getLockData(),
                mKey.getLockMac(),
                new ModifyICCardPeriodCallback() {
                    @Override
                    public void onModifyICCardPeriodSuccess() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopLoading();
                                requestAddIcCard(String.valueOf(cardNumber));
                            }
                        });
                    }

                    @Override
                    public void onFail(LockError lockError) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopLoading();
                                toast(R.string.adding_ic_card_operation_fail);
                                finish();
                            }
                        });
                    }
                }
        );
	}

	private void lockModifyFingerprintPeriod(long fingerprintNumber) {
        mTTLockAPI.modifyFingerprintValidityPeriod(
                DateUtil.getStringToDate(mStartDate, "yyyy-MM-dd HH:mm"),
                DateUtil.getStringToDate(mEndDate, "yyyy-MM-dd HH:mm"),
                String.valueOf(fingerprintNumber),
                mKey.getLockData(),
                mKey.getLockMac(),
                new ModifyFingerprintPeriodCallback() {
                    @Override
                    public void onModifyPeriodSuccess() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsLockOperationSuccessModify = true;
                                requestAddFingerprint(String.valueOf(fingerprintNumber));
                            }
                        });
                    }

                    @Override
                    public void onFail(LockError lockError) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsLockOperationSuccessModify = true;
                                addFail();
                            }
                        });
                    }
                }

        );
	}

	/**
	 * 请求服务器，添加指纹
	 */
	private void requestAddFingerprint(String fingerprintNumber) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("lockId", mKey.getLockId());
		params.put("userId", PeachPreference.readUserId());
		params.put("fingerprintId", fingerprintNumber);
		params.put("type", mValidPeriodType);
		params.put("alias", mFingerprintCardName);
		params.put("remark", mFingerprintRemarks);
		params.put("fingerprintNumber", fingerprintNumber);
		if (mValidPeriodType == KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED) {
			params.put("startDate", mStartDate);
			params.put("endDate", mEndDate);
			params.put("timeZone", DateUtil.getTimeZone());
		}
		PeachLogger.d(params);
		RestClient.builder()
				.url(Urls.FINGERPRINT_ADD)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						stopLoading();
						PeachLogger.d("FINGERPRINT_ADD", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							setSuceessStatus();
							setActionInfoConfig();
							EventBus.getDefault().post(new Event(Event.EventType.ADD_IC_CARD_SUCCESS, mActionInfo));

							BaseApplication.getHandler().postDelayed(new Runnable() {
								@Override
								public void run() {
									// 1s 后自动跳转到卡片列表页面
									finish();
								}
							}, 1000);
						} else {
							addFail(BackendError.messageRes(code,
                                    HomeDeviceInfo.isPadlock(mLockType) ? R.string.padlock_fingerprint_failed : R.string.operation_fail,
                                    BackendError.Credential.FINGERPRINT));
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 请求服务器，添加 IC 卡
	 */
	private void requestAddIcCard(final String cardNumber) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("cardNumber", cardNumber);
		params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mKey.getLockId());
		params.put("alias", mFingerprintCardName);
		if (mValidPeriodType == KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED) {
			params.put("timeZone", DateUtil.getTimeZone());
			params.put("startDate", mStartDate);
			params.put("endDate", mEndDate);
		}
		RestClient.builder()
				.url(Urls.IC_CARD_ADD)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_ADD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							// 此卡已添加过，本次添加覆盖之前的信息
							if ("REPEAT".equals(result.getString("data"))) {
								toast(R.string.adding_ic_card_toast_successfully_readd);
							} else {
								toast(R.string.adding_ic_card_toast_successfully);
							}

							setSuceessStatus();
							setActionInfoConfig();
							EventBus.getDefault().post(new Event(Event.EventType.ADD_IC_CARD_SUCCESS, mActionInfo));

							BaseApplication.getHandler().postDelayed(new Runnable() {
								@Override
								public void run() {
									// 1s 后自动跳转到卡片列表页面
									finish();
								}
							}, 1000);
						} else {
							toastBackendError(code, R.string.adding_ic_card_operation_fail, BackendError.Credential.CARD);
							finish();
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 添加成功，设置提示状态
	 */
	private void setSuceessStatus() {
		mTvHintBack.setEnabled(true);
		mIvHintSuccess.setVisibility(View.VISIBLE);
		if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
			mTvHintSuccess.setText(R.string.adding_fingerprint_hint_successfully);
		} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
			mTvHintSuccess.setText(R.string.adding_ic_card_hint_successfully);
		}
	}

	private void setActionInfoConfig() {
		if (mValidPeriodType != KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED) {
			mActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
		} else {
			if (DateUtil.getStringToDate(mStartDate, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM) > System.currentTimeMillis()) {
				mActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
			} else {
				mActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
//		BaseApplication.getHandler().removeCallbacks(mRunnable);
		sPPLOCK.disconnect();
		mTTLockAPI.disconnect();
//		if (!mIsLockOperationSuccess) {
//			if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 添加指纹
//				toastFail();
//			} else if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
//				toast(R.string.adding_ic_card_operation_fail);
//			}
//		}
	}

//	private void refreshDownTime() {
//		mCurrentScanProgress = 0;
//		updateTime();
//	}
}
