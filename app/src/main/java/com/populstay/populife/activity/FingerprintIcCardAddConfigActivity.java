package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.bigkoo.pickerview.builder.TimePickerBuilder;
import com.bigkoo.pickerview.listener.OnTimeSelectListener;
import com.bigkoo.pickerview.view.TimePickerView;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.manhattanlock.MHILockGetTime;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.device.KeyboardUtil;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.Calendar;
import java.util.Date;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class FingerprintIcCardAddConfigActivity extends BaseActivity implements View.OnClickListener {

	private EditText mEtName, mEtRemarks;
	private TextView mTvTitle, mTvTips, mTvName, mTvStartTime, mTvEndTime, mTvNext, mTvDeviceCurrentTime;
	private LinearLayout mLlRemarks, mLlTime;
	//时间选择器
	private TimePickerView mTimePicker;
	private RadioGroup mRgValidPeriod;

	public static final String KEY_KEY = "KEY_KEY";
	public static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	public static final String KEY_FROM = "KEY_FROM";
	public static final String KEY_FINGERPRINT_CARD_TYPE = "KEY_FINGERPRINT_CARD_TYPE";
	private int mFrom;
	private Date mStartTime;
	private Date mEndTime;
	private Key mKey = CURRENT_KEY;
	private String mLockType;
	private boolean isMHLock = false;
	private int mFingerprintCardType = KeyPwdConstant.IType.TYPE_FINGERPRINT; // 添加类型：指纹 or 门卡（默认为指纹）
	private int mValidPeriodType = KeyPwdConstant.IFingerprintCardValidType.PERMANENT; // 指纹/门卡有效类型（1 限时，2 永久）

	public static void actionStart(Context context, int from, int fingerprintCardType,Key key, String lockType) {
		Intent intent = new Intent(context, FingerprintIcCardAddConfigActivity.class);
		intent.putExtra(KEY_FINGERPRINT_CARD_TYPE, fingerprintCardType);
		intent.putExtra(KEY_FROM, from);
		intent.putExtra(KEY_KEY,key);
		intent.putExtra(KEY_LOCK_TYPE,lockType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_fingerprint_ic_card_add_config);
		getIntentData();
		initView();
		initStatus();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		if (data.hasExtra(KEY_FROM)){
			mFrom = data.getIntExtra(KEY_FROM, 0);
		}
		if (data.hasExtra(KEY_FINGERPRINT_CARD_TYPE)){
			mFingerprintCardType = data.getIntExtra(KEY_FINGERPRINT_CARD_TYPE, 0);
		}
		mFingerprintCardType = data.getIntExtra(KEY_FINGERPRINT_CARD_TYPE, KeyPwdConstant.IType.TYPE_FINGERPRINT);
		mLockType = data.getStringExtra(KEY_LOCK_TYPE);
		if (data.hasExtra(KEY_KEY)){
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
		if (mLockType == null){
			mLockType = mKey.getLockName();
		}
		if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK) ||
				mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			isMHLock = true;
		}
	}

	private void initView() {
		mEtName = findViewById(R.id.et_ic_card_bluetooth_config_name);
		mEtRemarks = findViewById(R.id.et_ic_card_bluetooth_config_remarks);
		mTvTitle = findViewById(R.id.page_left_title);
		mTvTips = findViewById(R.id.tv_fingerprint_ic_card_add_config_tips);
		mTvName = findViewById(R.id.tv_ic_card_bluetooth_config_name);
		mTvStartTime = findViewById(R.id.tv_ic_card_bluetooth_config_start_time);
		mTvEndTime = findViewById(R.id.tv_ic_card_bluetooth_config_end_time);
		mTvNext = findViewById(R.id.tv_ic_card_bluetooth_config_next);
		mTvDeviceCurrentTime = findViewById(R.id.tv_device_current_time);
		mLlRemarks = findViewById(R.id.ll_ic_card_bluetooth_config_remarks);
		mLlTime = findViewById(R.id.ll_ic_card_bluetooth_config_time);
		mRgValidPeriod = findViewById(R.id.rg_valid_period);

		initTimePicker();
		showDeviceTime(-1);
		if (mKey.getLockCurrentTime() > 0) {
			showDeviceTime(mKey.getLockCurrentTime());
		} else {
			readLockTime();
		}

		if (mKey.getKeyRight() == 1 && mKey.getKeyType() == 1) { // 授权用户，限时钥匙：只允许添加限时指纹/门卡（有效期必须在自己钥匙有效期内）
			mRgValidPeriod.getChildAt(0).setVisibility(View.GONE);
			((RadioButton) mRgValidPeriod.getChildAt(1)).setChecked(true);
			selectValidPeriod(R.id.rb_valid_period_time_limited);
		}
	}

	private void initTimePicker() {
		// 获取当前时间
		long now = DateUtil.getCurTimeMillis();
		mStartTime = new Date(now);
		mEndTime = new Date(now + 3600 * 1000);

		mTvStartTime.setText(DateUtil.getDateToString(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
		mTvEndTime.setText(DateUtil.getDateToString(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));

		Calendar selectedDate = Calendar.getInstance();
		selectedDate.set(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
				selectedDate.get(Calendar.DAY_OF_MONTH), selectedDate.get(Calendar.HOUR_OF_DAY),
				selectedDate.get(Calendar.MINUTE));

		mTimePicker = new TimePickerBuilder(this, new OnTimeSelectListener() {
            @Override
            public void onTimeSelect(Date date, View v) {
                ((TextView) v).setText(DateUtil.getDateToString(date, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
                int id = v.getId();

                if (id == R.id.tv_ic_card_bluetooth_config_start_time) {
                    mStartTime = date;
                } else if (id == R.id.tv_ic_card_bluetooth_config_end_time) {
                    mEndTime = date;
                }
            }
        })
				.setType(new boolean[]{true, true, true, true, true, false})
				.setLabel(getString(R.string.unit_year), getString(R.string.unit_month), getString(R.string.unit_day),
						getString(R.string.unit_hour), getString(R.string.unit_minute), getString(R.string.unit_second))
				.setSubmitText(getResources().getString(R.string.ok))
				.setCancelText(getResources().getString(R.string.cancel))
				.setDate(selectedDate)
				.setRangDate(selectedDate, null)
				.build();
	}

	private void showDeviceTime(long time) {
		if (null != mTvDeviceCurrentTime) {
			String timeStr = "";
			if (time <= 0) {
				timeStr = String.format(getResources().getString(R.string.device_current_date), getResources().getString(R.string.unknown));
			} else {
				timeStr = String.format(getResources().getString(R.string.device_current_date), DateUtil.getDateToString(time, DateUtil.DATE_TIME_PATTERN_1));
			}
			mTvDeviceCurrentTime.setText(timeStr);
		}
	}

	private void readLockTime() {
		//showLoading();
		setGetTimeCallback();
        if(mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getLockTime();
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else{
            mTTLockAPI.getLockTime(mKey.getLockData(),mKey.getLockMac(), new GetLockTimeCallback() {
                @Override
                public void onGetLockTimeSuccess(long time) {
                    CURRENT_KEY.setLockCurrentTime(time);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                showDeviceTime(time);
                            }
                        }
                    });
                }

                @Override
                public void onFail(LockError error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                showDeviceTime(-1);
                            }
                        }
                    });
                }
            });
		}
	}

	private void setGetTimeCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.GET_LOCK_TIME);
			MyApplication.pplBleSession.setmILockGetTime(new MHILockGetTime() {

				@Override
				public void onSuccess(final long time) {
					CURRENT_KEY.setLockCurrentTime(time);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (!isFinishing()) {
								showDeviceTime(time);
							}
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (!isFinishing()) {
								showDeviceTime(-1);
							}
						}
					});
				}
			});
		}
	}

	private void initStatus() {
		if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
			mTvTitle.setText(R.string.ic_card_config);
			mTvTips.setText(R.string.add_ic_card_tips);
			mTvName.setText(R.string.ic_card_name);
			mEtName.setHint(R.string.ic_card_name_hint);
			mLlRemarks.setVisibility(View.GONE);
		} else { // 添加指纹
			mTvTitle.setText(R.string.fingerprint_config);
			mTvTips.setText(R.string.add_fingerprint_tips);
			if (HomeDeviceInfo.isPadlock(mLockType)) {
				mTvTips.setText(R.string.padlock_fingerprint_info);
			}
			mTvName.setText(R.string.fingerprint_user_name);
			mEtRemarks.setHint(R.string.fingerprint_remarks_hint);
			mLlRemarks.setVisibility(View.VISIBLE);
		}
	}

	private void initListener() {
		mTvStartTime.setOnClickListener(this);
		mTvEndTime.setOnClickListener(this);
		mTvNext.setOnClickListener(this);
		mEtName.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				mTvNext.setEnabled(!StringUtil.isBlank(editable.toString().trim()));
			}
		});
		mRgValidPeriod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				selectValidPeriod(checkedId);
			}
		});
	}

    private void selectValidPeriod(int checkedId) {
        if (checkedId == R.id.rb_valid_period_permanent) {
            mLlTime.setVisibility(View.GONE);
            mValidPeriodType = KeyPwdConstant.IFingerprintCardValidType.PERMANENT;
        } else if (checkedId == R.id.rb_valid_period_time_limited) {
            mLlTime.setVisibility(View.VISIBLE);
            mValidPeriodType = KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_ic_card_bluetooth_config_start_time) {
            setPickerSelectedTime(true, view);
        } else if (id == R.id.tv_ic_card_bluetooth_config_end_time) {
            setPickerSelectedTime(false, view);
        } else if (id == R.id.tv_ic_card_bluetooth_config_next) {
            String name = mEtName.getText().toString().trim();
            String remarks = mEtRemarks.getText().toString().trim();

            if (mValidPeriodType == KeyPwdConstant.IFingerprintCardValidType.TIME_LIMITED
                    && !mStartTime.before(mEndTime)) {
                toast(R.string.note_time_start_greater_than_end);
            } else if (mKey.getKeyRight() == 1 && mKey.getKeyType() == 1
                    && mEndTime.after(new Date(mKey.getEndDate()))) { // 授权用户，限时钥匙，不能超期添加指纹/门卡
                if (mFingerprintCardType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 添加门卡
                    toast(getString(R.string.note_ic_card_cant_beyond_validity_period));
                } else { // 添加指纹
                    toast(getString(R.string.note_fingerprint_cant_beyond_validity_period));
                }
            } else {
                if (mKey.getLockId() < 0) { // KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD, KeyPwdConstant.IType.TYPE_IC_CARD
                    ActivateDeviceActivity.actionStart(
                            FingerprintIcCardAddConfigActivity.this,
                            mFrom,
                            mFingerprintCardType,
                            mKey,
                            mLockType,
                            name,
                            remarks,
                            mValidPeriodType,
                            mTvStartTime.getText().toString(),
                            mTvEndTime.getText().toString()
                    );
                } else {
                    IcCardBluetoothAddActivity.actionStart(
                            FingerprintIcCardAddConfigActivity.this,
                            mFingerprintCardType,
                            name,
                            remarks,
                            mValidPeriodType,
                            mTvStartTime.getText().toString(),
                            mTvEndTime.getText().toString(),
                            mKey,
                            mLockType
                    );
                }
            }
        }
    }

    private void setPickerSelectedTime(boolean isStart, View view) {
		KeyboardUtil.hideSoftInput(view);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(isStart ? mStartTime.getTime() : mEndTime.getTime());
		mTimePicker.setDate(cal);
		mTimePicker.show(view);
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		if (Event.EventType.ADD_IC_CARD_SUCCESS == event.type) { // 添加门卡成功
			finish();
		}
	}
}
