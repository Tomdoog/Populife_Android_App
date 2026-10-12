package com.populstay.populife.fragment;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.builder.OptionsPickerBuilder;
import com.bigkoo.pickerview.builder.TimePickerBuilder;
import com.bigkoo.pickerview.listener.CustomListener;
import com.bigkoo.pickerview.listener.OnOptionsSelectListener;
import com.bigkoo.pickerview.listener.OnTimeSelectListener;
import com.bigkoo.pickerview.view.OptionsPickerView;
import com.bigkoo.pickerview.view.TimePickerView;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.net.BackendError;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseFragment;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.entity.CreatePwdKeyActionInfo;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockAddPasscode;
import com.populstay.populife.manhattanlock.MHILockAddPasscode;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.date.LockTime;
import com.populstay.populife.util.device.KeyboardUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.CreateCustomPasscodeCallback;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.WeakHashMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

/**
 * 发送“永久密码” Fragment
 * Created by Jerry
 */

public class LockSendPasscodeFragment extends BaseFragment implements View.OnClickListener {

	public static final String VAL_TAB_TYPE_CUSTOMIZE = " VAL_TAB_TYPE_CUSTOMIZE";
	public static final String VAL_TAB_TYPE_PERMANENT = " VAL_TAB_TYPE_PERMANENT";
	public static final String VAL_TAB_TYPE_PERIOD = " VAL_TAB_TYPE_PERIOD";
	public static final String VAL_TAB_TYPE_ONE_TIME = " VAL_TAB_TYPE_ONE_TIME";
	public static final String VAL_TAB_TYPE_CLEAR = " VAL_TAB_TYPE_CLEAR";
	public static final String VAL_TAB_TYPE_CYCLIC = " VAL_TAB_TYPE_CYCLIC";

	private static final String KEY_KEY = "key_key";
	private static final String KEY_TAB_TYPE = "KEY_TAB_TYPE";
	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_LOCK_NAME = "key_lock_name";
	private static final String KEY_LOCK_MAC = "key_lock_mac";
	private static final String KEY_PASSWORD_LIST = "key_password_list";

	private LinearLayout mLlCyclicMode, mLlTime, mLlCustomPwd;
	private TextView mTvCyclicMode, mTvStartTime, mTvEndTime, mTvNote, mTvGenerate, tv_current_device_time, tv_create_custom_pwd_tips_2;
	private EditText mEtName, mEtCustomPwd;
	private TimePickerView mTimePicker;
	private OptionsPickerView mPickerCyclic;

	private Key mKey = MyApplication.CURRENT_KEY;
	/**
	 * passcodeType		Value(int)
	 * One-time				1
	 * Permanent			2
	 * Period				3
	 * Clear				4
	 * Weekend Cyclic		5
	 * Daily Cyclic			6
	 * Workday Cyclic		7
	 * Monday Cyclic		8
	 * Tuesday Cyclic		9
	 * Wednesday Cyclic		10
	 * Thursday Cyclic		11
	 * Friday Cyclic		12
	 * Saturday Cyclic		13
	 * Sunday Cyclic		14
	 */
	private int mPasscodeType;
	private String mCurTabType = VAL_TAB_TYPE_ONE_TIME; // 默认单次密码
	private int mLockId;
	private int mKeyId;
	private String mLockMac;
	private String mLockName;
	private Date mCreateTime, mStartTime, mEndTime;
	private List<String> mCyclicModeList;
	private AlertDialog DIALOG;
	private EditText mEtDialogInput;
	private String mInputPwd;
	private ArrayList<String> mPasswordList = new ArrayList<>();
	private int pwdCreatehint = -1;
	private String currentNewPwd = "";
	private CreatePwdKeyActionInfo mCreatePwdKeyActionInfo = new CreatePwdKeyActionInfo();

	public static LockSendPasscodeFragment newInstance(Key key, String tabType, int lockId, int keyId, String lockName, String lockMac, ArrayList<String> passwordList) {

		Bundle args = new Bundle();
		args.putParcelable(KEY_KEY, key);
		args.putString(KEY_TAB_TYPE, tabType);
		args.putInt(KEY_LOCK_ID, lockId);
		args.putInt(KEY_KEY_ID, keyId);
		args.putString(KEY_LOCK_NAME, lockName);
		args.putString(KEY_LOCK_MAC, lockMac);
		args.putStringArrayList(KEY_PASSWORD_LIST, passwordList);

		LockSendPasscodeFragment fragment = new LockSendPasscodeFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_lock_send_passcode, null);

		getIntentData();
		initView(view);
		initListener();
		return view;
	}

	private void getIntentData() {
		Bundle bundle = getArguments();
		if (bundle != null) {
			Key key = bundle.getParcelable(KEY_KEY);
			if (null != key) {
				mKey = bundle.getParcelable(KEY_KEY);
			}
			mCurTabType = bundle.getString(KEY_TAB_TYPE);
			mLockId = bundle.getInt(KEY_LOCK_ID, 0);
			mKeyId = bundle.getInt(KEY_KEY_ID, 0);
			mLockName = bundle.getString(KEY_LOCK_NAME);
			mLockMac = bundle.getString(KEY_LOCK_MAC);
			mPasswordList = bundle.getStringArrayList(KEY_PASSWORD_LIST);
		}

//		mKey = DbService.getKeyByLockmac(mLockMac);
	}

	private void initView(View view) {
		mLlCyclicMode = view.findViewById(R.id.ll_lock_send_passcode_cyclic_mode);
		mLlTime = view.findViewById(R.id.ll_lock_send_passcode_time);
		mLlCustomPwd = view.findViewById(R.id.ll_custom_pwd);
		mTvCyclicMode = view.findViewById(R.id.tv_lock_send_passcode_cyclic_mode);
		mTvStartTime = view.findViewById(R.id.tv_lock_send_passcode_start_time);
		mTvEndTime = view.findViewById(R.id.tv_lock_send_passcode_end_time);
		mTvGenerate = view.findViewById(R.id.tv_lock_send_passcode_generate);
		tv_create_custom_pwd_tips_2 = view.findViewById(R.id.tv_create_custom_pwd_tips_2);
		tv_current_device_time = view.findViewById(R.id.tv_current_device_time);
		mEtName = view.findViewById(R.id.et_lock_send_passcode_name);
		mEtCustomPwd = view.findViewById(R.id.et_lock_send_passcode_password);
		if (mKey.getLockId()<0){
			mEtCustomPwd.setHint(R.string.create_mh_custom_pwd_tips_3);
			mEtCustomPwd.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
		}
		initTimePicker();
		refreshUI();
		showDeviceTime(-1);
		PeachLogger.d("LockCurrentTime = " + mKey.getLockCurrentTime());
		if (mKey.getLockCurrentTime() > 0) {
			showDeviceTime(mKey.getLockCurrentTime());
		}
	}

	private void showDeviceTime(long time) {
		if (null != tv_current_device_time) {
			String timeStr = "";
			if (time <= 0) {
				timeStr = String.format(getResources().getString(R.string.device_current_date), getResources().getString(R.string.unknown));
			} else {
				timeStr = String.format(getResources().getString(R.string.device_current_date), LockTime.format(time, DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()));
			}
			tv_current_device_time.setText(timeStr);
		}
	}

	private void initTimePicker() {
		// 获取当前时间
		long now = DateUtil.getStringToDate(LockTime.format(System.currentTimeMillis(),
                DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()), DateUtil.DATE_TIME_PATTERN_1);
		mStartTime = new Date(now);
		mEndTime = new Date(now + 3600 * 1000);

		Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(now);
		selectedDate.set(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
				selectedDate.get(Calendar.DAY_OF_MONTH), selectedDate.get(Calendar.HOUR_OF_DAY),
				selectedDate.get(Calendar.MINUTE));
		switch (mCurTabType) {
			case VAL_TAB_TYPE_PERIOD://限时密码
			case VAL_TAB_TYPE_CUSTOMIZE://自定义密码
				mTvStartTime.setText(DateUtil.getDateToString(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_00));
				mTvEndTime.setText(DateUtil.getDateToString(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_00));
				mTimePicker = new TimePickerBuilder(getActivity(), new OnTimeSelectListener() {
                    @Override
                    public void onTimeSelect(Date date, View v) {
                        ((TextView) v).setText(DateUtil.getDateToString(date, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_00));
                        int id = v.getId();

                        if (id == R.id.tv_lock_send_passcode_start_time) {
                            mStartTime = date;
                        } else if (id == R.id.tv_lock_send_passcode_end_time) {
                            mEndTime = date;
                        }
                    }
                })
						.setType(new boolean[]{true, true, true, true, false, false})
						.setLabel(getString(R.string.unit_year), getString(R.string.unit_month), getString(R.string.unit_day),
								getString(R.string.unit_hour), getString(R.string.unit_minute), getString(R.string.unit_second))
						.setSubmitText(getResources().getString(R.string.ok))
						.setCancelText(getResources().getString(R.string.cancel))
						.setDate(selectedDate)
						.setRangDate(selectedDate, null)
						.build();
				break;

			case VAL_TAB_TYPE_CYCLIC://循环密码（默认周末）
				initCustomOptionPicker();

				mTvStartTime.setText(DateUtil.getDateToString(mStartTime, DateUtil.DATE_FORMAT_HH_00));
				mTvEndTime.setText(DateUtil.getDateToString(mEndTime, DateUtil.DATE_FORMAT_HH_00));

				mTimePicker = new TimePickerBuilder(getActivity(), new OnTimeSelectListener() {
                    @Override
                    public void onTimeSelect(Date date, View v) {
                        ((TextView) v).setText(DateUtil.getDateToString(date, DateUtil.DATE_FORMAT_HH_00));
                        int id = v.getId();

                        if (id == R.id.tv_lock_send_passcode_start_time) {
                            mStartTime = date;
                        } else if (id == R.id.tv_lock_send_passcode_end_time) {
                            mEndTime = date;
                        }
                    }

                })
						.setType(new boolean[]{false, false, false, true, false, false})
						.setLabel(getString(R.string.unit_year), getString(R.string.unit_month), getString(R.string.unit_day),
								getString(R.string.unit_hour), getString(R.string.unit_minute), getString(R.string.unit_second))
						.setSubmitText(getResources().getString(R.string.ok))
						.setCancelText(getResources().getString(R.string.cancel))
						.setDate(selectedDate)
						.setRangDate(selectedDate, null)
						.build();
				break;

			default:
				break;
		}
	}

	private void initCustomOptionPicker() {//条件选择器初始化，自定义布局
		mCyclicModeList = new ArrayList<>();
		mCyclicModeList.add(getString(R.string.weekend));
		mCyclicModeList.add(getString(R.string.daily));
		mCyclicModeList.add(getString(R.string.workday));
		mCyclicModeList.add(getString(R.string.monday));
		mCyclicModeList.add(getString(R.string.tuesday));
		mCyclicModeList.add(getString(R.string.wednesday));
		mCyclicModeList.add(getString(R.string.thursday));
		mCyclicModeList.add(getString(R.string.friday));
		mCyclicModeList.add(getString(R.string.saturday));
		mCyclicModeList.add(getString(R.string.sunday));
		/**
		 * @description
		 *
		 * 注意事项：
		 * 自定义布局中，id为 optionspicker 或者 timepicker 的布局以及其子控件必须要有，否则会报空指针。
		 * 具体可参考demo 里面的两个自定义layout布局。
		 */
		mPickerCyclic = new OptionsPickerBuilder(getActivity(), new OnOptionsSelectListener() {
			@Override
			public void onOptionsSelect(int options1, int option2, int options3, View v) {
				//返回的分别是三个级别的选中位置
				String tx = mCyclicModeList.get(options1);
				mTvCyclicMode.setText(tx);
				mPasscodeType = options1 + 5;
			}
		})
				.setLayoutRes(R.layout.pickerview_custom_cyclic_mode, new CustomListener() {
					@Override
					public void customLayout(View v) {
						final TextView tvSubmit = v.findViewById(R.id.tv_finish);
						TextView tvCancel = v.findViewById(R.id.iv_cancel);
						tvSubmit.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								mPickerCyclic.returnData();
								mPickerCyclic.dismiss();
							}
						});

						tvCancel.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								mPickerCyclic.dismiss();
							}
						});
					}
				})
				.isDialog(true)
				.build();

		mPickerCyclic.setPicker(mCyclicModeList);//添加数据
	}

	private void refreshUI() {
		mLlCustomPwd.setVisibility(View.GONE);
		tv_create_custom_pwd_tips_2.setVisibility(View.GONE);
		switch (mCurTabType) {
			case VAL_TAB_TYPE_PERMANENT://永久密码
				mPasscodeType = 2;
				mLlCyclicMode.setVisibility(View.GONE);
				mLlTime.setVisibility(View.GONE);
				pwdCreatehint = R.string.create_dialog_permanent_pwd_tips;
				break;

			case VAL_TAB_TYPE_PERIOD://限时密码
				mPasscodeType = 3;
				mLlCyclicMode.setVisibility(View.GONE);
				pwdCreatehint = R.string.create_dialog_time_limit_pwd_tips;
				break;

			case VAL_TAB_TYPE_ONE_TIME://单次密码
				mPasscodeType = 1;
				mLlCyclicMode.setVisibility(View.GONE);
				mLlTime.setVisibility(View.GONE);
				pwdCreatehint = R.string.create_dialog_one_time_pwd_tips;
				break;

			case VAL_TAB_TYPE_CLEAR://清空密码
				mPasscodeType = 4;
				mLlCyclicMode.setVisibility(View.GONE);
				mLlTime.setVisibility(View.GONE);
				break;

			case VAL_TAB_TYPE_CUSTOMIZE://自定义密码
				mPasscodeType = 15;
				mLlCyclicMode.setVisibility(View.GONE);
				mLlCustomPwd.setVisibility(View.VISIBLE);
				tv_create_custom_pwd_tips_2.setVisibility(View.VISIBLE);
				break;

			case VAL_TAB_TYPE_CYCLIC://循环密码
				mPasscodeType = 5;
				break;

			default:
				break;
		}
	}

	private void initListener() {
		mTvCyclicMode.setOnClickListener(this);
		mTvStartTime.setOnClickListener(this);
		mTvEndTime.setOnClickListener(this);
		mTvGenerate.setOnClickListener(this);
		mEtName.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				setSendBtnEnable();
			}
		});
	}

	private void setSendBtnEnable() {
		mTvGenerate.setEnabled(isSendBtnEnable());
	}

	private boolean isSendBtnEnable() {
		if (StringUtil.isBlank(mEtName.getText().toString().trim())) {
			return false;
		}

		return true;
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_lock_send_passcode_cyclic_mode) {
            mPickerCyclic.show();
        } else if (id == R.id.tv_lock_send_passcode_start_time) {
            setPickerSelectedTime(true, view);
        } else if (id == R.id.tv_lock_send_passcode_end_time) {
            setPickerSelectedTime(false, view);
        } else if (id == R.id.tv_lock_send_passcode_generate) {
            if (checkForm()) {
                if (VAL_TAB_TYPE_CUSTOMIZE.equals(mCurTabType)) { // 自定义密码
                    mInputPwd = mEtCustomPwd.getText().toString();
                    if (!StringUtil.isBlank(mInputPwd) && StringUtil.isNum(mInputPwd)
                            && mInputPwd.length() >= 6 && mInputPwd.length() <= 9) {

                        if (isNetEnableWithToast()) {
                            if (isBleEnableWithoutToast()) {
                                checkPasswordExist(mInputPwd);
                            } else {
                                if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestAddPasscode(mInputPwd, "2");
                                } else {
                                    toast(R.string.enable_bluetooth);
                                }
                            }
                        }
                    } else {
                        toast(R.string.note_passcode_invalid);
                    }
                } else { // 直接和后台获取密码
                    generatePasscode();
                }
            }
        } else if (id == R.id.btn_dialog_input_cancel) {
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_input_ok) {
            mInputPwd = mEtDialogInput.getText().toString();
            if (!StringUtil.isBlank(mInputPwd) && StringUtil.isNum(mInputPwd)
                    && mInputPwd.length() >= 6 && mInputPwd.length() <= 9) {
                if (isBleNetEnable()) {
                    checkPasswordExist(mInputPwd);
                }
            } else {
                toast(R.string.note_passcode_invalid);
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

	/**
	 * 检查密码是否已存在
	 */
	public void checkPasswordExist(String pwd) {
		boolean isExist = false;
		for (String password : mPasswordList) {
			if (pwd.equals(password)) {
				isExist = true;
				break;
			}
		}
		if (!isExist) {
			addKeyboardPasscode(mInputPwd);
			//DIALOG.cancel();
		} else {
			toast(R.string.note_password_exist);
		}
	}

	private void showInputDialog() {
		DIALOG = new AlertDialog.Builder(getActivity()).create();
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

			title.setText(R.string.enter_customized_passcode);
			if(mKey.getLockId()<0){
				mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
				mEtDialogInput.setHint(R.string.passcode_format_6_8_digits);
			}else {
				mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
				mEtDialogInput.setHint(R.string.passcode_format_6_9_digits);
			}
			mEtDialogInput.setInputType(InputType.TYPE_CLASS_NUMBER);
			//mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
			mEtDialogInput.setMaxLines(1);
			DialogUtil.autoPopSoftInput(window, mEtDialogInput);
			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	/**
	 * 添加自定义键盘密码
	 */
	private void addKeyboardPasscode(String pwd) {
		PeachLoader.showLoading(getActivity());
		long startDate = LockTime.parse(mTvStartTime.getText().toString(), "yyyy-MM-dd HH:mm", mKey.getTimezoneRawOffset());
		long endDate = LockTime.parse(mTvEndTime.getText().toString(), "yyyy-MM-dd HH:mm", mKey.getTimezoneRawOffset());
		if (mKey.getLockId()<0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setAddPwdLockCallback(startDate, endDate, pwd);
				sPPLOCK.addKeyboardPwd(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),pwd,startDate,endDate,mKey.getK1());
			} else {
				setAddPwdLockCallback(startDate, endDate, pwd);
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.createCustomPasscode(pwd, startDate, endDate, mKey.getLockData(), mKey.getLockMac(), new CreateCustomPasscodeCallback() {
                @Override
                public void onCreateCustomPasscodeSuccess(String s) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                PeachLoader.stopLoading();
                                requestAddPasscode(pwd, "1");
                            }
                        });
                    }
                }

                @Override
                public void onFail(LockError lockError) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                PeachLoader.stopLoading();
                                // 连接超时说明不在锁附近，用网关设置自定义密码
                                if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestAddPasscode(mInputPwd, "2");
                                } else {
                                    toastFail();
                                }
                            }
                        });
                    }
                }
            });
		}

	}

	private void setAddPwdLockCallback(long startDate, long endDate, final String pwd) {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.ADD_KEYBOARD_PWD);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setPassword(pwd);
			MyApplication.pplBleSession.setStartDate(startDate);
			MyApplication.pplBleSession.setEndDate(endDate);
			MyApplication.pplBleSession.setmILockAddPasscode(new MHILockAddPasscode() {
				@Override
				public void onSuccess() {
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								PeachLoader.stopLoading();
								requestAddPasscode(pwd, "1");
							}
						});
					}
				}

				@Override
				public void onFail() {
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								PeachLoader.stopLoading();
								toastFail();
							}
						});
					}
				}
			});
		}

	}

	/**
	 * 请求服务器，添加键盘密码
	 *
	 * @param keyboardPwd 键盘密码
	 * @param mediumType  通讯介质（1：蓝牙，2：网关，默认是1）
	 */
	private void requestAddPasscode(final String keyboardPwd, final String mediumType) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_ADD)
				.loader(getActivity())
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("keyboardPwd", keyboardPwd)
				.params("startDate", mTvStartTime.getText().toString())
				.params("endDate", mTvEndTime.getText().toString())
				.params("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()))
				.params("keyId", mKey.getUserKeyId())
				.params("alias", mEtName.getText().toString().trim())
				.params("mediumType", mediumType)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_ADD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							showShareKeyDialog(keyboardPwd);

						} else if (code == 951) {
							//toast(R.string.note_password_exist);
							showShareKeyDialog(keyboardPwd);
						} else {
							toastBackendError(result, R.string.note_passcode_customize_fail, BackendError.Credential.PASSCODE);
						}
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				PeachLoader.stopLoading();
				toast(R.string.operation_fail);
			}
		})
				.build()
				.post();

	}


	public boolean isPasscodeGenerated() {
		return !StringUtil.isBlank(currentNewPwd);
	}

	public boolean checkForm() {
		boolean isPass = true;
		if (VAL_TAB_TYPE_PERIOD.equals(mCurTabType) || VAL_TAB_TYPE_CUSTOMIZE.equals(mCurTabType)
				|| VAL_TAB_TYPE_CYCLIC.equals(mCurTabType)) {//密码类型：限时、自定义、循环
			if (!mStartTime.before(mEndTime)) {
				isPass = false;
				toast(R.string.note_time_start_greater_than_end);
			}
		}
		return isPass;
	}

	/**
	 * OnekeyShare 自带分享弹窗 UI
	 */
	public void showShare() {
		try {
			Intent vIt = new Intent(Intent.ACTION_SEND);
//							vIt.setPackage("com.facebook.orca");
			vIt.setType("text/plain");
			vIt.putExtra(Intent.EXTRA_TEXT, getShareContent());
			startActivity(vIt);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		OnekeyShare oks = new OnekeyShare();
//
//		// 自定义分享平台
//		oks.setCustomerLogo(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share_zalo),
//				"Zalo", new View.OnClickListener() {
//					@Override
//					public void onClick(View view) {
//						try {
//							Intent vIt = new Intent(Intent.ACTION_SEND);
////							vIt.setPackage("com.facebook.orca");
//							vIt.setType("text/plain");
//							vIt.putExtra(Intent.EXTRA_TEXT, getShareContent());
//							startActivity(vIt);
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		//关闭sso授权
//		oks.disableSSOWhenAuthorize();
//		oks.setCallback(new PlatformActionListener() {
//			@Override
//			public void onComplete(Platform platform, int i, HashMap<String, Object> hashMap) {
//
//			}
//
//			@Override
//			public void onError(Platform platform, int i, Throwable throwable) {
//
//			}
//
//			@Override
//			public void onCancel(Platform platform, int i) {
//
//			}
//		});
//
//		// title标题，微信、QQ和QQ空间等平台使用
//		oks.setTitle(getString(R.string.app_name));
//		// titleUrl QQ和QQ空间跳转链接
////		oks.setTitleUrl("http://sharesdk.cn");
////		oks.setAddress("13201812820");
//		// text是分享文本，所有平台都需要这个字段
//		oks.setText(getShareContent());
//		// imagePath是图片的本地路径，Linked-In以外的平台都支持此参数
////		oks.setImagePath("/sdcard/test.jpg");//确保SDcard下面存在此张图片
//		// url在微信、微博，Facebook等平台中使用
////		oks.setUrl("http://sharesdk.cn");
//		// comment是我对这条分享的评论，仅在人人网使用
////		oks.setComment("我是测试评论文本");
//		// 启动分享GUI
//		oks.show(getActivity());
	}

	private String getShareContent() {
		long createTime = mCreateTime != null ? mCreateTime.getTime() : 0;
		long startTime = mStartTime != null ? LockTime.parse(DateUtil.getDateToString(mStartTime, DateUtil.DATE_TIME_PATTERN_1), DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()) : 0;
		long endTime = mEndTime != null ? LockTime.parse(DateUtil.getDateToString(mEndTime, DateUtil.DATE_TIME_PATTERN_1), DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()) : 0;
		return Utils.getShareContent(getActivity(), mPasscodeType, currentNewPwd, createTime, startTime, endTime, mLockName);
	}

	/**
	 * 获取键盘密码
	 */
	public void generatePasscode() {
		mCreateTime = new Date(); //获取当前时间

		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_GENERATE)
				.loader(getActivity())
				.params(getParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_GENERATE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONObject passcodeInfo = result.getJSONObject("data");
							showShareKeyDialog(passcodeInfo.getString("keyboardPwd"));

						} else {
							toastBackendError(result, R.string.create_password_fail, BackendError.Credential.PASSCODE);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.create_password_fail);
					}
				})
				.build()
				.post();
	}

	private WeakHashMap<String, Object> getParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mLockId);
		params.put("keyboardPwdVersion", 4);//键盘密码版本, 三代锁的密码版本为4
		params.put("keyboardPwdType", mPasscodeType);
		params.put("keyId", mKey.getUserKeyId());
		params.put("alias", mEtName.getText().toString().trim());

		if (VAL_TAB_TYPE_PERIOD.equals(mCurTabType) || VAL_TAB_TYPE_CUSTOMIZE.equals(mCurTabType)
				|| VAL_TAB_TYPE_CYCLIC.equals(mCurTabType)) {//密码类型：限时、自定义、循环
			params.put("startDate", mTvStartTime.getText().toString());
			params.put("endDate", mTvEndTime.getText().toString());
		}

		params.put("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()));

		return params;
	}


	private void showShareKeyDialog(final String code) {
		currentNewPwd = code;
		DIALOG = new AlertDialog.Builder(mActivity).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_share_pwd);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.WRAP_CONTENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			TextView tipTextView = window.findViewById(R.id.dialog_content);
			if (-1 != pwdCreatehint) {
				tipTextView.setText(pwdCreatehint);
			} else {
				tipTextView.setVisibility(View.GONE);
			}

			TextView tvCode = window.findViewById(R.id.tv_code);
			tvCode.setText(code);

			window.findViewById(R.id.tv_share_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					KeyPwd keyPwd = new KeyPwd();
					keyPwd.setKeyboardPwd(currentNewPwd);
					keyPwd.setKeyboardPwdType(mPasscodeType);
					if (null != mStartTime) {
						keyPwd.setStartDate(LockTime.parse(DateUtil.getDateToString(mStartTime, DateUtil.DATE_TIME_PATTERN_1), DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()));
					}

					if (null != mEndTime) {
						keyPwd.setEndDate(LockTime.parse(DateUtil.getDateToString(mEndTime, DateUtil.DATE_TIME_PATTERN_1), DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()));
					}

					if (null != mCreateTime) {
						keyPwd.setCreateDate(mCreateTime.getTime());
					}

					mCreatePwdKeyActionInfo.setKeyPwd(keyPwd);
					mCreatePwdKeyActionInfo.setShare(true);
					setCreatePwdKeyActionInfoConfig();
					EventBus.getDefault().post(new Event(Event.EventType.CREATE_PWD_SUCCESS, mCreatePwdKeyActionInfo));
					mActivity.finish();
				}
			});
			window.findViewById(R.id.tv_skip_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mCreatePwdKeyActionInfo.setShare(false);
					setCreatePwdKeyActionInfoConfig();
					EventBus.getDefault().post(new Event(Event.EventType.CREATE_PWD_SUCCESS, mCreatePwdKeyActionInfo));
					mActivity.finish();
				}
			});
		}
	}

	private void setCreatePwdKeyActionInfoConfig() {
		if (2 == mPasscodeType || 1 == mPasscodeType || -1 == mPasscodeType) {
			mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
		} else if (3 == mPasscodeType || 15 == mPasscodeType) {
			if (LockTime.parse(DateUtil.getDateToString(mStartTime, DateUtil.DATE_TIME_PATTERN_1), DateUtil.DATE_TIME_PATTERN_1, mKey.getTimezoneRawOffset()) > System.currentTimeMillis()) {
				mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
			} else {
				mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
			}
		} else {
			mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
		}
	}
}
