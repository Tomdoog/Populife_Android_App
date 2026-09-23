package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.bigkoo.pickerview.builder.OptionsPickerBuilder;
import com.bigkoo.pickerview.listener.CustomListener;
import com.bigkoo.pickerview.listener.OnOptionsSelectListener;
import com.bigkoo.pickerview.view.OptionsPickerView;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.entity.Key;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.lock.ILockModifyAutoLockTime;
import com.populstay.populife.manhattanlock.MHILockModifyAutoLockTime;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.SetAutoLockingPeriodCallback;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.List;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockAutoLockingActivity extends BaseActivity implements View.OnClickListener {

	private static final String KEY_AUTO_LOCK_TIME = "key_auto_lock_time";
	private static final String KEY_KEY = "KEY_KEY";

	private TextView mTvSave, mTvSeconds, mTvSetTime;
	private Switch mSwitch;
	private LinearLayout mLlTime;

	private Key mKey = MyApplication.CURRENT_KEY;
	private OptionsPickerView mOptionsPicker;
	private int mSeconds;

	/**
	 * @param autoLockTime 自动闭锁时间
	 */
	public static void actionStart(Activity context, int autoLockTime, int requestCode,Key key) {
		if (key == null || HomeDeviceInfo.isPadlock(key.getLockName())) return;
		Intent intent = new Intent(context, LockAutoLockingActivity.class);
		intent.putExtra(KEY_AUTO_LOCK_TIME, autoLockTime);
		intent.putExtra(KEY_KEY, key);
		context.startActivityForResult(intent, requestCode);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getIntentData();
		// 同时保护直接 Intent 启动与系统恢复页面的入口。
		if (mKey == null || HomeDeviceInfo.isPadlock(mKey.getLockName())) {
			finish();
			return;
		}
		setContentView(R.layout.activity_lock_auto_locking);
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mSeconds = data.getIntExtra(KEY_AUTO_LOCK_TIME, 0);
		if (data.hasExtra(KEY_KEY)){
			mKey = data.getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.automatic_locking);
		mTvSave = findViewById(R.id.page_action);
		mTvSave.setText(R.string.save);

		mTvSeconds = findViewById(R.id.tv_lock_auto_lock_time);
		mTvSetTime = findViewById(R.id.tv_lock_auto_lock_set_time);
		mSwitch = findViewById(R.id.switch_lock_auto_lock);
		mLlTime = findViewById(R.id.ll_lock_auto_lock_time);
		mLlTime.setVisibility(View.GONE);
		initUI();
		initCustomOptionPicker();
	}

	@SuppressLint("SetTextI18n")
	private void initUI() {
		if (mSeconds == 0) {
			mSeconds = 5;
			mSwitch.setChecked(false);
			mLlTime.setVisibility(View.GONE);
		} else {
			mSwitch.setChecked(true);
			mLlTime.setVisibility(View.VISIBLE);
		}
		mTvSeconds.setText(mSeconds + getString(R.string.seconds));
	}

	private void initCustomOptionPicker() {
		final List<String> timeList = new ArrayList<>();
		int i = 5;
		while (i <= 120) {
			timeList.add(i + "s");
			i++;
		}
		/**
		 * @description
		 *
		 * 注意事项：
		 * 自定义布局中，id为 optionspicker 或者 timepicker 的布局以及其子控件必须要有，否则会报空指针。
		 * 具体可参考demo 里面的两个自定义layout布局。
		 */
		mOptionsPicker = new OptionsPickerBuilder(this, new OnOptionsSelectListener() {
			@SuppressLint("SetTextI18n")
			@Override
			public void onOptionsSelect(int options1, int option2, int options3, View v) {
				//返回的分别是三个级别的选中位置
				String time = timeList.get(options1);
				mSeconds = Integer.valueOf(time.substring(0, time.length() - 1));
				mTvSeconds.setText(mSeconds + getString(R.string.seconds));
			}
		}).setLayoutRes(R.layout.pickerview_custom_auto_lock, new CustomListener() {
					@Override
					public void customLayout(View v) {
						final TextView tvSubmit = v.findViewById(R.id.tv_finish);
						TextView tvCancel = v.findViewById(R.id.iv_cancel);
						tvSubmit.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								mOptionsPicker.returnData();
								mOptionsPicker.dismiss();
							}
						});

						tvCancel.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								mOptionsPicker.dismiss();
							}
						});
					}
				}).isDialog(true).setLineSpacingMultiplier(2.5F)
				.build();

		mOptionsPicker.setPicker(timeList);//添加数据
	}

	private void initListener() {
		mSwitch.setOnClickListener(this);
		mTvSave.setOnClickListener(this);
		mTvSetTime.setOnClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.switch_lock_auto_lock) {
            mLlTime.setVisibility(mSwitch.isChecked() ? View.VISIBLE : View.GONE);
        } else if (id == R.id.tv_lock_auto_lock_set_time) {
            mOptionsPicker.show();
        } else if (id == R.id.page_action) {
            if (isBleEnableWithToast()) {
                modifyAutoLockTime();
            }
        }
    }

    private void modifyAutoLockTime() {
		if (mKey == null || HomeDeviceInfo.isPadlock(mKey.getLockName())) return;
		showLoading();
        if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setModifyAutoLockTimeCallback();
				sPPLOCK.setAutoLocking(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()), mSwitch.isChecked() ? mSeconds : 0,mKey.getK1());
			} else {
				setModifyAutoLockTimeCallback();
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
			setModifyAutoLockTimeCallback();
            mTTLockAPI.setAutomaticLockingPeriod(mSwitch.isChecked() ? mSeconds : 0, mKey.getLockData(), mKey.getLockMac(), new SetAutoLockingPeriodCallback() {
                @Override
                public void onSetAutoLockingPeriodSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            toast(R.string.modify_auto_lock_success);
                            setResultToBack();
                            finish();
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            toast(R.string.modify_auto_lock_fail);
                        }
                    });
                }
            });
		}

	}

	private void setModifyAutoLockTimeCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.SET_AUTO_LOCK_TIME);
			MyApplication.pplBleSession.setAutoLockTime(mSwitch.isChecked() ? mSeconds : 0);
			MyApplication.pplBleSession.setmILockModifyAutoLockTime(new MHILockModifyAutoLockTime() {

				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.modify_auto_lock_success);
							setResultToBack();
							finish();
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.modify_auto_lock_fail);
						}
					});
				}
			});
		}
	}

	private void setResultToBack() {
		if (mSwitch == null || mKey == null || HomeDeviceInfo.isPadlock(mKey.getLockName())) return;
		Intent data = new Intent();
		data.putExtra(LockSettingsActivity.KEY_RESULT_DATA,String.valueOf(mSwitch.isChecked()));
		setResult(RESULT_OK, data);
	}

	@Override
	public void finishCurrentActivity(View view) {
		setResultToBack();
		super.finishCurrentActivity(view);
	}

	@Override
	public void onBackPressed() {
		setResultToBack();
		finish();
	}
}
