package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.builder.TimePickerBuilder;
import com.bigkoo.pickerview.listener.OnTimeSelectListener;
import com.bigkoo.pickerview.view.TimePickerView;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar;
import java.util.Date;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;

public class EkeyPeriodModifyActivity extends BaseActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

	public static final String KEY_KEY = "key_key";
	public static final String KEY_KEY_ID = "key_key_id";
	public static final String KEY_KEY_TYPE = "key_key_type";
	public static final String KEY_START_TIME = "key_start_time";
	public static final String KEY_END_TIME = "key_end_time";

	private TextView mTvSave, mTvStartTime, mTvEndTime, tv_show_current_date, tv_save_btn;
	private LinearLayout ll_time_info, ll_end_time, ll_start_time;

	private RadioGroup rg_valid_period;
	private TimePickerView mTimePicker;
	private Key mKey;
	private int mKeyId;
	private int mKeyType;//钥匙类型（1限时，2永久，3单次，4循环）
	private long mStartTime, mEndTime; // 单位：毫秒

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ekey_period_modify);

		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mKey = data.getParcelableExtra(KEY_KEY);
		mKeyId = data.getIntExtra(KEY_KEY_ID, 1);
		mKeyType = data.getIntExtra(KEY_KEY_TYPE, 1);
		mStartTime = data.getLongExtra(KEY_START_TIME, 0) * 1000; // 传参单位：秒，需要 *1000 变成毫秒
		mEndTime = data.getLongExtra(KEY_END_TIME, 0) * 1000; // 传参单位：秒，需要 *1000 变成毫秒
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_period);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_save_btn);

		mTvStartTime = findViewById(R.id.tv_ekey_period_modify_start_time);
		mTvEndTime = findViewById(R.id.tv_ekey_period_modify_end_time);
		rg_valid_period = findViewById(R.id.rg_valid_period);
		ll_time_info = findViewById(R.id.ll_time_info);
		ll_end_time = findViewById(R.id.ll_end_time);
		ll_start_time = findViewById(R.id.ll_start_time);
		tv_show_current_date = findViewById(R.id.tv_device_current_time);

		if (mKeyType == 2) { // 永久钥匙
			long now = System.currentTimeMillis();
			mStartTime = now;
			mEndTime = now + 3600 * 1000;
		}

		ll_time_info.setVisibility(View.VISIBLE);
		mTvStartTime.setText(DateUtil.getDateToString(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
		mTvEndTime.setText(DateUtil.getDateToString(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
		initTimePicker();

		showDeviceTime(-1);
		if (mKey.getLockCurrentTime() > 0) {
			showDeviceTime(mKey.getLockCurrentTime());
		} else {
			readLockTime();
		}
	}

	private void showDeviceTime(long time) {
		if (null != tv_show_current_date) {
			String timeStr = "";
			if (time <= 0) {
				timeStr = String.format(getResources().getString(R.string.device_current_date), getResources().getString(R.string.unknown));
			} else {
				timeStr = String.format(getResources().getString(R.string.device_current_date), DateUtil.getDateToString(time, DateUtil.DATE_TIME_PATTERN_1));
			}
			tv_show_current_date.setText(timeStr);
		}
	}

	private void readLockTime() {
		//showLoading();
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


    private void initTimePicker() {
        Calendar selectedDate = Calendar.getInstance();
        selectedDate.set(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
                selectedDate.get(Calendar.HOUR_OF_DAY),
                selectedDate.get(Calendar.MINUTE)
        );

        mTimePicker = new TimePickerBuilder(this, new OnTimeSelectListener() {
            @Override
            public void onTimeSelect(Date date, View v) {
                String time = DateUtil.getDateToString(date, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM);
                int id = v.getId();

                if (id == R.id.tv_ekey_period_modify_start_time || id == R.id.ll_start_time) {
                    ((TextView) findViewById(R.id.tv_ekey_period_modify_start_time)).setText(time);
                    mStartTime = DateUtil.getStringToDate(time, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM); // 毫秒
                } else if (id == R.id.tv_ekey_period_modify_end_time || id == R.id.ll_end_time) {
                    ((TextView) findViewById(R.id.tv_ekey_period_modify_end_time)).setText(time);
                    mEndTime = DateUtil.getStringToDate(time, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM); // 毫秒
                }
            }
        })
                .setType(new boolean[]{true, true, true, true, true, false})
                .setLabel(
                        getString(R.string.unit_year),
                        getString(R.string.unit_month),
                        getString(R.string.unit_day),
                        getString(R.string.unit_hour),
                        getString(R.string.unit_minute),
                        getString(R.string.unit_second)
                )
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
		ll_start_time.setOnClickListener(this);
		ll_end_time.setOnClickListener(this);
		rg_valid_period.setOnCheckedChangeListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_save_btn) {
            if (mStartTime < mEndTime) {
                modifyEkeyPeriod();
            } else {
                toast(R.string.note_time_start_greater_than_end);
            }
        } else if (id == R.id.tv_ekey_period_modify_start_time || id == R.id.ll_start_time) {
            setPickerSelectedTime(true, view);
        } else if (id == R.id.tv_ekey_period_modify_end_time || id == R.id.ll_end_time) {
            setPickerSelectedTime(false, view);
        }
    }


    private void setPickerSelectedTime(boolean isStart, View view) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(isStart ? mStartTime : mEndTime);
		mTimePicker.setDate(cal);
		mTimePicker.show(view);
	}

	/**
	 * 修改钥匙有效期
	 */
	private void modifyEkeyPeriod() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_MODIFY_PERIOD)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("keyId", mKeyId)
				.params("type", 1)
				.params("startDate", mTvStartTime.getText().toString())
				.params("endDate", mTvEndTime.getText().toString())
				.params("timeZone", DateUtil.getTimeZone())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_MODIFY_PERIOD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");

						if (code == 200) {
							Intent intent = new Intent();
							mKeyType = 1;
							intent.putExtra(KEY_KEY_TYPE, mKeyType);
							intent.putExtra(KEY_START_TIME, mStartTime / 1000);
							intent.putExtra(KEY_END_TIME, mEndTime / 1000);

							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_KEY_PERIOD));

							setResult(RESULT_OK, intent);
							toast(R.string.note_modify_period_success);
							finish();
						} else {
							toastBackendError(code, R.string.note_modify_period_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_period_fail);
					}
				})
				.build()
				.post();
	}

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (group.getId() == R.id.rg_valid_period) {
            selectValidPeriod(checkedId);
        }
    }

    private void selectValidPeriod(int checkedId) {
        if (checkedId == R.id.rb_valid_period_permanent) {
            ll_time_info.setVisibility(View.GONE);
            mKeyType = KeyPwdConstant.IBTKeyType.PERMANENT;
        } else if (checkedId == R.id.rb_valid_period_time_limited) {
            ll_time_info.setVisibility(View.VISIBLE);
            mKeyType = KeyPwdConstant.IBTKeyType.TIME_LIMITED;
            tv_show_current_date.setText(DateUtil.getCurDate(DateUtil.DATE_TIME_PATTERN_3));

            if (mStartTime == 0) {
                mStartTime = new Date().getTime();
            }
            if (mEndTime == 0) {
                mEndTime = new Date().getTime();
            }

            mTvStartTime.setText(DateUtil.getDateToString(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
            mTvEndTime.setText(DateUtil.getDateToString(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
        }
    }

}
