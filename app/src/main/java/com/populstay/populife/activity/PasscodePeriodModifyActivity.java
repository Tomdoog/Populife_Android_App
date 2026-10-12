package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.builder.TimePickerBuilder;
import com.bigkoo.pickerview.listener.OnTimeSelectListener;
import com.bigkoo.pickerview.view.TimePickerView;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockFingerprintModifyPeriod;
import com.populstay.populife.lock.ILockIcCardModifyPeriod;
import com.populstay.populife.lock.ILockModifyPasscode;
import com.populstay.populife.manhattanlock.MHILockModifyCardPeriod;
import com.populstay.populife.manhattanlock.MHILockModifyFingerprintPeriod;
import com.populstay.populife.manhattanlock.MHILockModifyPasscodePeriod;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.date.LockTime;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.ModifyFingerprintPeriodCallback;
import com.ttlock.bl.sdk.callback.ModifyICCardPeriodCallback;
import com.ttlock.bl.sdk.callback.ModifyPasscodeCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar;
import java.util.Date;
import java.util.WeakHashMap;

import static com.populstay.populife.activity.PasscodeDetailActivity.KEY_PASSCODE;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;
import static com.populstay.populife.keypwdmanage.KeyPwdListFragment.TYPE_KEY_PWD_FP_CARD;

public class PasscodePeriodModifyActivity extends BaseActivity implements View.OnClickListener {

	public static final String KEY_PASSCODE_PWD = "key_passode";
	public static final String KEY_PASSCODE_ID = "key_passode_id";
	public static final String KEY_PASSCODE_TYPE = "key_passcode_type";
	public static final String KEY_PASSCODE_START_TIME = "key_passcode_start_time";
	public static final String KEY_PASSCODE_END_TIME = "key_passcode_end_time";

	private TextView mTvSave, mTvStartTime, mTvEndTime;

	private KeyPwd mKeyPwd;
	private Key mKey = MyApplication.CURRENT_KEY;
	private TimePickerView mTimePicker;
	private String mPasscodePwd;
	// 密码精确到小时，指纹/门卡精确到分钟
	private String mDatePattern;
	private long mStartTime, mEndTime;
	private int mPasscodeId, mPasscodeType;
	private int mAccessType = KeyPwdConstant.IType.TYPE_PWD; // 1：钥匙，2：密码，3：指纹，4：门卡
	private boolean mIsLockOperationSuccess;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_passcode_period_modify);
		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mKeyPwd = data.getParcelableExtra(KEY_PASSCODE);
		mPasscodePwd = data.getStringExtra(KEY_PASSCODE_PWD);
		mPasscodeId = data.getIntExtra(KEY_PASSCODE_ID, 0);
		mPasscodeType = data.getIntExtra(KEY_PASSCODE_TYPE, 0);
		mStartTime = data.getLongExtra(KEY_PASSCODE_START_TIME, 0);
		mEndTime = data.getLongExtra(KEY_PASSCODE_END_TIME, 0);
		mAccessType = data.getIntExtra(TYPE_KEY_PWD_FP_CARD, KeyPwdConstant.IType.TYPE_PWD);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_period);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_save_btn);

		mTvStartTime = findViewById(R.id.tv_passcode_period_modify_start_time);
		mTvEndTime = findViewById(R.id.tv_passcode_period_modify_end_time);

		mDatePattern = mAccessType == KeyPwdConstant.IType.TYPE_PWD ?
				DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_00 : DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM;

		if (mStartTime == 0) { // 永久密码/指纹/门卡
			long now = System.currentTimeMillis();
			mStartTime = now;
			mEndTime = now + 3600 * 1000;
		}
//		if (mPasscodeType == 2) { // 永久密码
//			long now = System.currentTimeMillis();
//			mStartTime = now;
//			mEndTime = now + 3600 * 1000;
//		}
		mTvStartTime.setText(LockTime.format(mStartTime, mDatePattern, mKey.getTimezoneRawOffset()));
		mTvEndTime.setText(LockTime.format(mEndTime, mDatePattern, mKey.getTimezoneRawOffset()));
		initTimePicker();
	}

	private void initTimePicker() {
		Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(DateUtil.getStringToDate(LockTime.format(System.currentTimeMillis(),
                DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()), DateUtil.DATE_TIME_PATTERN_1));
		selectedDate.set(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
				selectedDate.get(Calendar.DAY_OF_MONTH), selectedDate.get(Calendar.HOUR_OF_DAY),
				selectedDate.get(Calendar.MINUTE));

		mTimePicker = new TimePickerBuilder(this, new OnTimeSelectListener() {
            @Override
            public void onTimeSelect(Date date, View v) {
                String time = DateUtil.getDateToString(date, mDatePattern);
                ((TextView) v).setText(time);

                int id = v.getId();
                if (id == R.id.tv_passcode_period_modify_start_time) {
                    mStartTime = LockTime.parse(time, mDatePattern, mKey.getTimezoneRawOffset());
                } else if (id == R.id.tv_passcode_period_modify_end_time) {
                    mEndTime = LockTime.parse(time, mDatePattern, mKey.getTimezoneRawOffset());
                }
            }
        })
				// 密码精确到小时，指纹/门卡精确到分钟
				.setType(new boolean[]{true, true, true, true, mAccessType != KeyPwdConstant.IType.TYPE_PWD, false})
				.setLabel(getString(R.string.unit_year), getString(R.string.unit_month), getString(R.string.unit_day),
						getString(R.string.unit_hour), getString(R.string.unit_minute), getString(R.string.unit_second))
				.setSubmitText(getResources().getString(R.string.ok))
				.setCancelText(getResources().getString(R.string.cancel))
				.setDate(selectedDate)
				.setRangDate(selectedDate, null)
				.build();
	}

	private void initListener() {
		mTvSave.setOnClickListener(this);
		mTvStartTime.setOnClickListener(this);
		mTvEndTime.setOnClickListener(this);
	}
    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_save_btn) {
            // 曼哈顿SDK暂不支持修改键盘密码
            // if (mKey.getLockId() < 0) return;

            if (mStartTime < mEndTime) {
                if (mAccessType == KeyPwdConstant.IType.TYPE_PWD) { // 密码
                    if (isBleNetEnableWithToast()) {
                        // 和锁通信，修改密码期限
                        modifyPasscodePeriod();
                    }
                } else if (mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 门卡
                    if (isNetEnableWithToast()) {
                        if (isBleEnableWithoutToast()) {
                            // 和锁通信，修改门卡期限
                            modifyIcCardPeriod(Long.parseLong(mKeyPwd.getCardNumber()));
                        } else {
                            if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                updateIcCardInfo(2);
                            } else {
                                toast(R.string.enable_bluetooth);
                            }
                        }
                    }
                } else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 指纹
                    if (isNetEnableWithToast()) {
                        if (isBleEnableWithoutToast()) {
                            // 和锁通信，修改指纹期限
                            if (mKey.getLockId() < 0) {
                                MHModifyFingerprintPeriod();
                            } else {
                                modifyFingerprintPeriod(Long.parseLong(mKeyPwd.getFingerprintNumber()));
                            }
                        } else {
                            if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                updateFingerprintInfo(2);
                            } else {
                                toast(R.string.enable_bluetooth);
                            }
                        }
                    }
                }
            } else {
                toast(R.string.note_time_start_greater_than_end);
            }

        } else if (id == R.id.tv_passcode_period_modify_start_time) {
            setPickerSelectedTime(true, view);

        } else if (id == R.id.tv_passcode_period_modify_end_time) {
            setPickerSelectedTime(false, view);
        }
    }

	private void setPickerSelectedTime(boolean isStart, View view) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(DateUtil.getStringToDate(LockTime.format(isStart ? mStartTime : mEndTime,
                DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()), DateUtil.DATE_TIME_PATTERN_1));
		mTimePicker.setDate(cal);
		mTimePicker.show(view);
	}

	private void modifyPasscodePeriod() {
		showLoading();
		if (mKey.getLockId()<0){
			int pwdType = covertMHpwdType(mKeyPwd.getKeyboardPwdType());
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setModifyPasscodeCallback();
				sPPLOCK.modifyUserKeyboardPwdPeriod(PeachPreference.readUserId(),
						String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),mPasscodePwd,
						pwdType,mStartTime,mEndTime,mKey.getK1());
			} else {
				MyApplication.pplBleSession.setKeyboardPwdType(pwdType);
				setModifyPasscodeCallback();
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.modifyPasscode(mPasscodePwd, "", mStartTime, mEndTime, mKey.getLockData(), mKey.getLockMac(), new ModifyPasscodeCallback() {
                @Override
                public void onModifyPasscodeSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            requestModifyPasscodePeriod();
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            if (lockError == LockError.LOCK_PASSWORD_NOT_EXIST) {
                                toast(R.string.note_unused_passcode_cannot_be_modified);
                            } else {
                                toast(R.string.operation_fail);
                            }
                        }
                    });
                }
            });
		}

	}

	private int covertMHpwdType(int keyPwdType) {
		int type;
		type = keyPwdType;
		if (keyPwdType == 15) {
			type = 3;//限时密码
		}
		return type;
	}

	private void setModifyPasscodeCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.MODIFY_KEYBOARD_PWD_VALID);
			MyApplication.pplBleSession.setKeyboardPwdType(3);//永久密码（2）修改期限后，变成限时密码（3）
			MyApplication.pplBleSession.setKeyboardPwdOriginal(mPasscodePwd);
			MyApplication.pplBleSession.setKeyboardPwdNew("");
			MyApplication.pplBleSession.setStartDate(mStartTime);
			MyApplication.pplBleSession.setEndDate(mEndTime);
			MyApplication.pplBleSession.setmILockModifyPasscodePeriod(new MHILockModifyPasscodePeriod() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestModifyPasscodePeriod();
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.operation_fail);
						}
					});
				}
			});
		}
	}

	private void modifyIcCardPeriod(long cardNumber) {
		showLoading();
		if (mKey.getLockId()<0) {
			String cardType = "00";
			if (mKeyPwd.getType() == 1) {
				cardType = "01";
			}
			if (mStartTime > 0) {
				cardType = "01";
			}
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setModifyIcCardPeriodCallback(cardNumber,cardType);
				sPPLOCK.modifyCardPeriod(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKeyPwd.getCardNumber(),
						cardType,mStartTime,mEndTime,mKey.getK1());
			} else {
				setModifyIcCardPeriodCallback(cardNumber,cardType);
				startLockActionScan();
			}
		}else {
            mTTLockAPI.modifyICCardValidityPeriod(mStartTime, mEndTime, String.valueOf(cardNumber), mKey.getLockData(), mKey.getLockMac(), new ModifyICCardPeriodCallback() {
                @Override
                public void onModifyICCardPeriodSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            updateIcCardInfo(1);
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

                            if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                updateIcCardInfo(2);
                            } else {
                                toast(R.string.operation_fail);
                            }
                        }
                    });
                }
            });
		}

	}

	private void setModifyIcCardPeriodCallback(final long cardNumber,String cardType) {
		if (mKey.getLockId()<0) {
			MyApplication.pplBleSession.setOperation(LockOperation.MODIFY_CARD_VALID);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setStartDate(mStartTime);
			MyApplication.pplBleSession.setEndDate(mEndTime);
			MyApplication.pplBleSession.setCardId(String.valueOf(cardNumber));
			MyApplication.pplBleSession.setCardType(cardType);
			MyApplication.pplBleSession.setmILockModifyCardPeriod(new MHILockModifyCardPeriod() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							updateIcCardInfo(1);
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
							toast(R.string.operation_fail);
						}
					});
				}

			});
		}
	}

	private void MHModifyFingerprintPeriod () {
		showLoading();
		String fingerType = "00";
		if (mKeyPwd.getType() == 1){
			fingerType = "01";
		}
		if (mStartTime > 0) {
			fingerType = "01";
		}
		if (sPPLOCK.isConnected(mKey.getLockMac())) {
			setMHModifyFingerprintPeriodCallback(fingerType);
			sPPLOCK.modifyFingerprintPeriod(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
					String.valueOf(mKey.getKeyId()),
					mKeyPwd.getFingerprintId(),fingerType,mStartTime,mEndTime,mKey.getK1());
		} else {
			setMHModifyFingerprintPeriodCallback(fingerType);
			startLockActionScan();
		}
	}

	private void modifyFingerprintPeriod(long fingerprintNumber) {
		showLoading();
        mTTLockAPI.modifyFingerprintValidityPeriod(mStartTime, mEndTime, String.valueOf(fingerprintNumber), mKey.getLockData(), mKey.getLockMac(), new ModifyFingerprintPeriodCallback() {
            @Override
            public void onModifyPeriodSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        mIsLockOperationSuccess = true;
                        updateFingerprintInfo(1);
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
                        if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                            updateFingerprintInfo(2);
                        } else {
                            toast(R.string.operation_fail);
                        }
                    }
                });
            }
        });
	}

	private void setMHModifyFingerprintPeriodCallback(String fingerType) {
		MyApplication.pplBleSession.setOperation(LockOperation.MODIFY_FINGERPRINT_VALID);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setStartDate(mStartTime);
		MyApplication.pplBleSession.setEndDate(mEndTime);
		MyApplication.pplBleSession.setFingerId(mKeyPwd.getFingerprintId());
		MyApplication.pplBleSession.setFingerType(fingerType);
		MyApplication.pplBleSession.setmILockModifyFingerPeriod(new MHILockModifyFingerprintPeriod() {
			@Override
			public void onSuccess() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						updateFingerprintInfo(1);
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
						toast(R.string.operation_fail);
					}
				});
			}
		});

	}

	/**
	 * 请求服务器，修改密码有效期
	 */
	private void requestModifyPasscodePeriod() {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_MODIFY)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("keyboardPwdId", mPasscodeId)
				.params("changeType", 1)
				.params("startDate", mTvStartTime.getText().toString())
				.params("endDate", mTvEndTime.getText().toString())
				.params("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");

						if (code == 200) {
							setResult();
						} else {
							toastBackendError(result, R.string.note_modify_period_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_period_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_period_fail);
					}
				})
				.build()
				.post();
	}

	private void setResult() {
		Intent intent = new Intent();
		intent.putExtra(KEY_PASSCODE_START_TIME, mStartTime);
		intent.putExtra(KEY_PASSCODE_END_TIME, mEndTime);
		EventBus.getDefault().post(new Event(Event.EventType.MODIFY_PWD_PERIOD));
		setResult(RESULT_OK, intent);
		toast(R.string.note_modify_period_success);
		finish();
	}

	/**
	 * 更新门卡信息
	 */
	private void updateIcCardInfo(int changeType) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("cardId", mKeyPwd.getCardId());
		params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mKey.getLockId());
		params.put("changeType", changeType);
		params.put("startDate", LockTime.format(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM, mKey.getTimezoneRawOffset()));
		params.put("endDate", LockTime.format(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM, mKey.getTimezoneRawOffset()));
		params.put("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()));

		RestClient.builder()
				.url(Urls.IC_CARD_INFO_UPDATE)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_UPDATE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							setResult();
						} else {
							stopLoading();
							toastBackendError(result, R.string.note_modify_period_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						stopLoading();
						toast(R.string.note_modify_period_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						stopLoading();
						toast(R.string.note_modify_period_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 更新指纹信息
	 */
	private void updateFingerprintInfo(int changeType) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("id", mKeyPwd.getFpStringId());
		//params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mKey.getLockId());
		params.put("changeType", changeType);
		params.put("startDate", LockTime.format(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM, mKey.getTimezoneRawOffset()));
		params.put("endDate", LockTime.format(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM, mKey.getTimezoneRawOffset()));
		params.put("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()));

		RestClient.builder()
				.url(Urls.FINGERPRINT_INFO_UPDATE)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("FINGERPRINT_UPDATE", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							setResult();
						} else {
							stopLoading();
							toastBackendError(result, R.string.note_modify_period_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						stopLoading();
						toast(R.string.note_modify_period_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						stopLoading();
						toast(R.string.note_modify_period_fail + msg);
					}
				})
				.build()
				.post();
	}
}
