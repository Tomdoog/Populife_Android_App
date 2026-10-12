package com.populstay.populife.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;
import java.util.TimeZone;
import com.populstay.populife.helper.BindingTimeEvidence;
import com.populstay.populife.helper.LockTimezoneCalibration;
import com.populstay.populife.push.EventPushService;
import com.populstay.populife.util.date.LockTime;
import com.ttlock.bl.sdk.callback.ConnectLockCallback;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.util.EncryptionUtil;
import android.view.View;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.lock.ILockSetTime;
import com.populstay.populife.manhattanlock.MHILockSetTime;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.SetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockTimeActivity extends BaseActivity {

	private static final String KEY_LOCK_TIME = "key_lock_time";
	public static final String KEY_KEY = "KEY_KEY";

	private TextView mTvTime, mTvCalibrate, mTvTimezone, mTvCalibrateTimezone, mTvTimezoneHint;
    private final Handler mTimezoneHandler = new Handler(Looper.getMainLooper());
    private LockTimezoneCalibration mTimezoneTask;
    private final Runnable mTimezoneTimeout = () -> {
        if (mTimezoneTask != null) mTimezoneTask.fail();
    };

	private Key mKey = MyApplication.CURRENT_KEY;
	private long mLockTime;
	private boolean mIsLockOperationSuccess;

	/**
	 * @param lockTime 锁时间
	 */
	public static void actionStart(Activity context, long lockTime, int requestCode, Key key) {
		Intent intent = new Intent(context, LockTimeActivity.class);
		intent.putExtra(KEY_LOCK_TIME, lockTime);
		intent.putExtra(KEY_KEY, key);
		context.startActivityForResult(intent, requestCode);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_time);

		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mLockTime = data.getLongExtra(KEY_LOCK_TIME, DateUtil.getCurTimeMillis());
		if (data.hasExtra(KEY_KEY)){
			mKey = data.getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.calibrate_time);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mTvTime = findViewById(R.id.tv_lock_time);
		mTvCalibrate = findViewById(R.id.tv_lock_time_calibrate);
        showLockTime();
        mTvTimezone = findViewById(R.id.tv_lock_timezone);
        mTvCalibrateTimezone = findViewById(R.id.tv_lock_timezone_calibrate);
        mTvTimezoneHint = findViewById(R.id.tv_lock_timezone_hint);
        findViewById(R.id.lock_timezone_group).setVisibility(canCalibrateTimezone() ? View.VISIBLE : View.GONE);
        showLockTimezone();
        mTvCalibrateTimezone.setOnClickListener(v -> calibrateTimezone());
    }

    private void initListener() {
		mTvCalibrate.setOnClickListener(new View.OnClickListener() {
			@Override
            public void onClick(View view) {
                if (isTimezoneBusy()) return;
                if (isBleEnableWithoutToast()) { // 蓝牙开启
					// 和锁通信，校准锁时间
					calibrateLockTime();
				} else if (isNetEnableWithoutToast()) { // 网络开启
					if (mKey.getLockId()>0){
						// 通过网关校准锁时间
						calibrateLockTimeViaGateway();
					}
				} else {
					toastFail();
				}
			}
		});
	}

    private boolean canCalibrateTimezone() {
        return mKey != null && mKey.getLockId() > 0 && mKey.isAdmin();
    }

    private boolean isTimezoneBusy() {
        return mTimezoneTask != null && mTimezoneTask.isRunning();
    }

    private void showLockTime() {
        if (mLockTime <= 0) { mTvTime.setText(R.string.unknown); return; }
        mTvTime.setText(mKey.getLockId() > 0
                ? LockTime.format(mLockTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS, mKey.getTimezoneRawOffset())
                : DateUtil.getDateToString(mLockTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
    }

    private void showLockTimezone() {
        int offset = mKey.getTimezoneRawOffset();
        long minutes = Math.abs((long) offset) / 60000;
        mTvTimezone.setText(String.format(Locale.ROOT, "UTC%s%02d:%02d",
                offset < 0 ? "-" : "+", minutes / 60, minutes % 60));
    }

    private void calibrateTimezone() {
        if (!canCalibrateTimezone() || isTimezoneBusy()) return;
        if (!isNetEnableWithToast()) return;
        final boolean gateway = !isBleEnableWithoutToast();
        if (gateway && !mKey.isHasGateway()) { toast(R.string.enable_bluetooth); return; }
        final int targetOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis());
        mTvCalibrate.setEnabled(false);
        mTvCalibrateTimezone.setEnabled(false);
        mTvTimezoneHint.setText(R.string.calibrate_timezone_in_progress);
        showLoading();
        mTimezoneTask = new LockTimezoneCalibration(mKey.getLockId(), targetOffset,
                new LockTimezoneCalibration.Actions() {
            @Override public void updateServer(LockTimezoneCalibration.Callback<Void> cb) {
                RestClient.builder().url(Urls.LOCK_TIMEZONE_CALIBRATE)
                        .params("userId", PeachPreference.readUserId())
                        .params("lockId", mKey.getLockId())
                        .params("timezoneRawOffset", targetOffset)
                        .params("type", gateway ? 2 : 1)
                        .success(response -> {
                            try {
                                JSONObject result = JSON.parseObject(response);
                                if (result != null && result.getIntValue("code") == 200) cb.success(null);
                                else cb.failure();
                            } catch (RuntimeException e) { cb.failure(); }
                        }).failure(cb::failure).error((code, msg) -> cb.failure()).build().post();
            }
            @Override public void loadServer(LockTimezoneCalibration.Callback<LockTimezoneCalibration.State> cb) {
                RestClient.builder().url(Urls.LOCK_GET_BASEINFO)
                        .params("userId", PeachPreference.readUserId()).params("lockId", mKey.getLockId())
                        .success(response -> {
                            try {
                                JSONObject result = JSON.parseObject(response);
                                if (result == null || result.getIntValue("code") != 200) { cb.failure(); return; }
                                JSONObject data = result.getJSONObject("data");
                                if (data == null) { cb.failure(); return; }
                                cb.success(new LockTimezoneCalibration.State(data.getIntValue("lockId"),
                                        data.getInteger("timezoneRawOffSet"), data.getString("lockData")));
                            } catch (RuntimeException e) { cb.failure(); }
                        }).failure(cb::failure).error((code, msg) -> cb.failure()).build().get();
            }
            @Override public void syncAndVerifyClock(LockTimezoneCalibration.State state,
                                                     LockTimezoneCalibration.Callback<Long> cb) {
                // 服务器字段和 SDK 凭据必须属于同一把锁且携带相同偏移。
                try {
                    com.ttlock.bl.sdk.entity.LockData data = EncryptionUtil.parseLockData(state.lockData);
                    if (data == null || data.getTimezoneRawOffset() != targetOffset
                            || !mKey.getLockMac().equalsIgnoreCase(data.getLockMac())) {
                        cb.failure(); return;
                    }
                } catch (RuntimeException e) { cb.failure(); return; }
                if (gateway) readTimezoneClockViaGateway(cb);
                else writeTimezoneClock(state, cb);
            }
        }, new LockTimezoneCalibration.Result() {
            @Override public void success(LockTimezoneCalibration.State state, long time) {
                mKey.setTimezoneRawOffset(state.offset);
                mKey.setLockData(state.lockData);
                mKey.setLockCurrentTime(time);
                Key current = MyApplication.CURRENT_KEY;
                if (current != null && current.getLockId() == state.lockId) {
                    current.setTimezoneRawOffset(state.offset);
                    current.setLockData(state.lockData);
                    current.setLockCurrentTime(time);
                }
                mLockTime = time;
                finishTimezoneCalibration(true);
                showLockTime();
                showLockTimezone();
                setResultToBack();
                // 详情页重新读取服务端时区，刷新差异提示和后续密码参数。
                sendBroadcast(new Intent(EventPushService.ACTION_KEY_STATUS_CHANGE).setPackage(getPackageName()));
                PeachLogger.d("LockTimezoneSync success lockId=" + state.lockId + " offsetMillis=" + state.offset);
            }
            @Override public void failure() {
                finishTimezoneCalibration(false);
                PeachLogger.d("LockTimezoneSync incomplete lockId=" + mKey.getLockId());
            }
        });
        mTimezoneHandler.postDelayed(mTimezoneTimeout, 60000);
        mTimezoneTask.start();
    }

    private void writeTimezoneClock(LockTimezoneCalibration.State state, LockTimezoneCalibration.Callback<Long> cb) {
        final LockTimezoneCalibration task = mTimezoneTask;
        mTTLockAPI.connectLock(state.lockData, new ConnectLockCallback() {
            @Override public void onConnectSuccess() {
                mTimezoneHandler.post(() -> {
                    if (!isTimezoneBusy() || mTimezoneTask != task) return;
                    // SDK 已连接时仍保留连接命令，释放后再校时（与绑定校时一致）。
                    mTTLockAPI.clearAllCallback();
                    long writtenAt = System.currentTimeMillis();
                    long elapsedAt = SystemClock.elapsedRealtime();
                    mTTLockAPI.setLockTime(writtenAt, state.lockData, mKey.getLockMac(), new SetLockTimeCallback() {
                        @Override public void onSetTimeSuccess() {
                            mTimezoneHandler.post(() -> {
                                if (!isTimezoneBusy() || mTimezoneTask != task) return;
                                mTTLockAPI.getLockTime(state.lockData, mKey.getLockMac(), new GetLockTimeCallback() {
                                    @Override public void onGetLockTimeSuccess(long time) {
                                        mTimezoneHandler.post(() -> {
                                            if (BindingTimeEvidence.clockMatches(writtenAt,
                                                    SystemClock.elapsedRealtime() - elapsedAt, time)) cb.success(time);
                                            else cb.failure();
                                        });
                                    }
                                    @Override public void onFail(LockError error) { mTimezoneHandler.post(cb::failure); }
                                });
                            });
                        }
                        @Override public void onFail(LockError error) { mTimezoneHandler.post(cb::failure); }
                    });
                });
            }
            @Override public void onFail(LockError error) { mTimezoneHandler.post(cb::failure); }
        });
    }

    private void readTimezoneClockViaGateway(LockTimezoneCalibration.Callback<Long> cb) {
        RestClient.builder().url(Urls.GATEWAY_LOCK_TIME_READ).params("lockId", mKey.getLockId())
                .success(response -> {
                    try {
                        JSONObject result = JSON.parseObject(response);
                        JSONObject data = result == null ? null : result.getJSONObject("data");
                        if (result == null || result.getIntValue("code") != 200 || data == null) { cb.failure(); return; }
                        long time = data.getLongValue("date");
                        if (time > 0 && Math.abs(time - System.currentTimeMillis()) <= 10000) cb.success(time);
                        else cb.failure();
                    } catch (RuntimeException e) { cb.failure(); }
                }).failure(cb::failure).error((code, msg) -> cb.failure()).build().post();
    }

    private void finishTimezoneCalibration(boolean success) {
        mTimezoneHandler.removeCallbacks(mTimezoneTimeout);
        if (!success) mTTLockAPI.clearAllCallback();
        stopLoading();
        mTvCalibrate.setEnabled(true);
        mTvCalibrateTimezone.setEnabled(true);
        mTvTimezoneHint.setText(success ? R.string.calibrate_timezone_hint : R.string.calibrate_timezone_incomplete);
        toast(success ? R.string.calibrate_timezone_success : R.string.calibrate_timezone_incomplete);
    }

    @Override protected void onDestroy() {
        mTimezoneHandler.removeCallbacks(mTimezoneTimeout);
        if (isTimezoneBusy()) {
            mTimezoneTask.cancel();
            mTTLockAPI.clearAllCallback();
        }
        super.onDestroy();
    }

	private void calibrateLockTime() {
		showLoading();
		mLockTime = DateUtil.getCurTimeMillis();
		setSetTimeCallback();
        if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.setLockTime(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mLockTime,mKey.getK1());
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.setLockTime(mLockTime, mKey.getLockData(), mKey.getLockMac(), new SetLockTimeCallback() {
                @Override
                public void onSetTimeSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            toast(R.string.calibrate_time_success);
                            showLockTime();
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
                            if (isNetEnableWithoutToast()) { // 网络开启
                                // 通过网关校准锁时间
                                calibrateLockTimeViaGateway();
                            } else {
                                toastFail();
                            }
                        }
                    });
                }
            });
		}
	}

	private void setSetTimeCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.SET_LOCK_TIME);
			MyApplication.pplBleSession.setmILockSetTime(new MHILockSetTime() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							toast(R.string.calibrate_time_success);
							showLockTime();
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
							toastFail();
						}
					});
				}
			});
		}
	}

	/**
	 * 通过网关校准锁时间
	 */
	private void calibrateLockTimeViaGateway() {
		RestClient.builder()
				.url(Urls.GATEWAY_LOCK_TIME_CALIBRATE)
				.loader(this)
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GATEWAY_LOCK_TIME_CALIBRATE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.calibrate_time_success);
							showLockTime();
						} else {
							toastBackendError(result, R.string.operation_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toastFail();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toastFail();
					}
				})
				.build()
				.post();
	}

	@Override
    public void finishCurrentActivity(View view) {
        if (isTimezoneBusy()) { toast(R.string.calibrate_timezone_in_progress); return; }
        setResultToBack();
		super.finishCurrentActivity(view);
	}

	@Override
    public void onBackPressed() {
        if (isTimezoneBusy()) { toast(R.string.calibrate_timezone_in_progress); return; }
        setResultToBack();
		finish();
	}

	private void setResultToBack() {
		Intent data = new Intent();
		data.putExtra(LockSettingsActivity.KEY_RESULT_DATA, String.valueOf(mLockTime));
        data.putExtra(KEY_KEY, mKey);
		setResult(RESULT_OK, data);
	}
}
