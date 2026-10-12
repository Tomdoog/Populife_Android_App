package com.populstay.populife.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
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
import com.populstay.populife.databinding.ActivityLockSendEkeyBinding;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyCreateSuccessActivity;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.CreatePwdKeyActionInfo;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.ui.widget.exedittext.MultiLineHintEditText;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.device.KeyboardUtil;
import com.populstay.populife.util.locale.LanguageUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.rilixtech.widget.countrycodepicker.CountryCodePicker;
import com.ttlock.bl.sdk.api.TTLockClient;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.WeakHashMap;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;

public class LockSendEkeyActivity extends BaseActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

	private static final String KEY_KEY = "key_key";
	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_IS_ADMIN = "key_is_admin";
	private final int REQUEST_CONTACT = 3;

    private ActivityLockSendEkeyBinding binding;

	private AlertDialog DIALOG;
	private LinearLayout mLlTime, ll_receiver;
	private TextView mTvStartTime, mTvEndTime, mTvOneTimeNote, mTvSend;
	private CountryCodePicker mCountryCodePicker;
	private ImageView mIvContact;
	private EditText mEtReceiver,mEtKeyName;
	//时间选择器
	private TimePickerView mTimePicker;

	private Key mKey;
	private int mLockId;
	private boolean mIsAdmin;
	private int mKeyType = KeyPwdConstant.IBTKeyType.PERMANENT;//钥匙类型（1限时，2永久，3单次）
	private boolean isAuAdmin;// 授权类型
	private int shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.ACCOUNT;//分享类型(1账号,2短信链接)
	private Date mStartTime;
	private Date mEndTime;

	private CreatePwdKeyActionInfo mCreatePwdKeyActionInfo = new CreatePwdKeyActionInfo();

	private RadioGroup rg_valid_period, rg_permission_types, rg_share_the_key_through;
	private TextView tv_show_current_date, tv_share_the_key_through_hint;

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁 id
	 * @param isAdmin 是否为管理员
	 */
	public static void actionStart(Context context, int lockId, boolean isAdmin) {
		Intent intent = new Intent(context, LockSendEkeyActivity.class);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_IS_ADMIN, isAdmin);
		context.startActivity(intent);
	}

	public static void actionStart(Context context, int lockId, boolean isAdmin, Key key) {
		Intent intent = new Intent(context, LockSendEkeyActivity.class);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_IS_ADMIN, isAdmin);
		intent.putExtra(KEY_KEY, key);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        binding = ActivityLockSendEkeyBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mKey = data.getParcelableExtra(KEY_KEY);
		mLockId = data.getIntExtra(KEY_LOCK_ID, 0);
		// 没有授权管理员
		mIsAdmin = data.getBooleanExtra(KEY_IS_ADMIN, false);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.create_bluetooth_key);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mLlTime = findViewById(R.id.ll_lock_send_ekey_time);
		ll_receiver = findViewById(R.id.ll_receiver);
		mTvStartTime = findViewById(R.id.tv_lock_send_ekey_start_time);
		mTvEndTime = findViewById(R.id.tv_lock_send_ekey_end_time);
		mTvOneTimeNote = findViewById(R.id.tv_lock_send_ekey_note);
		mCountryCodePicker = findViewById(R.id.cpp_lock_send_ekey);
		if (isChineseLanguage()) {
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("CN");
		}else{
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("US");
		}
		mIvContact = findViewById(R.id.iv_lock_send_ekey_receiver);
		mEtReceiver = findViewById(R.id.et_lock_send_ekey_receiver);
		mEtKeyName = findViewById(R.id.et_lock_send_ekey_name);
		mTvSend = findViewById(R.id.tv_lock_send_ekey_send);

		rg_valid_period = findViewById(R.id.rg_valid_period);
		rg_permission_types = findViewById(R.id.rg_permission_types);
		tv_share_the_key_through_hint = findViewById(R.id.tv_share_the_key_through_hint);
		tv_show_current_date = findViewById(R.id.tv_device_current_time);
		rg_share_the_key_through = findViewById(R.id.rg_share_the_key_through);

		if (mIsAdmin) {
            setPermissionTypes(R.id.rb_authorized_user);
		} else {
			// 非管理员，不能选择授权用户类型
            rg_permission_types.getChildAt(0).setVisibility(View.GONE);
//            rg_permission_types.getChildAt(0).setEnabled(false);
            setPermissionTypes(R.id.rb_general_user);
			if (mKey.getKeyRight() == 1 && mKey.getKeyType() == 1) { // 授权用户，限时钥匙：只允许发送限时钥匙（有效期必须在自己钥匙有效期内）
				rg_valid_period.getChildAt(0).setVisibility(View.GONE);
				selectValidPeriod(R.id.rb_valid_period_time_limited);
			}
		}

//		setCountryInfo();

		initTimePicker();
		showDeviceTime(-1);
		if (mKey.getLockCurrentTime() > 0) {
			showDeviceTime(mKey.getLockCurrentTime());
		} else {
			readLockTime();
		}
	}

	/**
	 * 设置国家信息（国家简称 + 国家码）
	 */
	private void setCountryInfo() {

		requestRuntimePermissions(new String[]{Manifest.permission.READ_PHONE_STATE,
				Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, new PermissionListener() {
			@Override
			public void onGranted() {
				// 获取当前国家的国家码
				String countryNameCode = LanguageUtil.getCountryNameCode(LockSendEkeyActivity.this);
				mCountryCodePicker.setCountryForNameCode(countryNameCode);
			}

			@Override
			public void onDenied(List<String> deniedPermissions) {
				toast(R.string.note_permission);
			}
		});
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
        mTTLockAPI.getLockTime(mKey.getLockKey(), mKey.getLockMac(), new GetLockTimeCallback() {
            @Override
            public void onGetLockTimeSuccess(long l) {
                CURRENT_KEY.setLockCurrentTime(l);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showDeviceTime(l);
                        }
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
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
                ((TextView) v).setText(DateUtil.getDateToString(date, "yyyy-MM-dd HH:mm"));

                int id = v.getId();
                if (id == R.id.tv_lock_send_ekey_start_time) {
                    mStartTime = date;
                } else if (id == R.id.tv_lock_send_ekey_end_time) {
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
				.setCancelColor(0Xff212322)
				.setSubmitColor(0xff212322)
				.setRangDate(selectedDate, null)
				.build();
	}

	private void initListener() {
		mIvContact.setOnClickListener(this);
		mTvStartTime.setOnClickListener(this);
		mTvEndTime.setOnClickListener(this);
		mTvSend.setOnClickListener(this);

		mEtReceiver.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				setSendBtnEnable();
				mCountryCodePicker.setVisibility(editable.length() == 0 ||
						StringUtil.isNum(editable.toString()) ? View.VISIBLE : View.GONE);
			}
		});
		mEtKeyName.addTextChangedListener(new TextWatcher() {
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

		rg_valid_period.setOnCheckedChangeListener(this);
		rg_permission_types.setOnCheckedChangeListener(this);
		rg_share_the_key_through.setOnCheckedChangeListener(this);
	}

	private void setSendBtnEnable() {
		mTvSend.setEnabled(isSendBtnEnable());
	}

	private boolean isSendBtnEnable() {
		if (StringUtil.isBlank(mEtKeyName.getText().toString().trim())) {
			return false;
		}

		if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT && StringUtil.isBlank(mEtReceiver.getText().toString().trim())) {
			return false;
		}

		return true;
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.iv_lock_send_ekey_receiver) {
        /*requestRuntimePermissions(new String[]{Manifest.permission.READ_CONTACTS}, new PermissionListener() {
            @Override
            public void onGranted() {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_PICK);
                intent.setData(ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(intent, REQUEST_CONTACT);
            }

            @Override
            public void onDenied(List<String> deniedPermissions) {
                toast(getString(R.string.note_permission_contact));
            }
        });*/

        } else if (id == R.id.tv_lock_send_ekey_start_time) {
            setPickerSelectedTime(true, view);

        } else if (id == R.id.tv_lock_send_ekey_end_time) {
            setPickerSelectedTime(false, view);

        } else if (id == R.id.tv_lock_send_ekey_send) {
            if (checkForm()) {
                sendEkey();
            }

        } else if (id == R.id.btn_dialog_send_ekey_cancel) {
            DIALOG.cancel();
        }
    }

	private void setPickerSelectedTime(boolean isStart, View view) {
		KeyboardUtil.hideSoftInput(view);

		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(isStart ? mStartTime.getTime() : mEndTime.getTime());
		mTimePicker.setDate(cal);
		mTimePicker.show(view);
	}

	private boolean checkForm() {
		final String receiver = mEtReceiver.getText().toString().trim();

		boolean isPass = true;
		if (mKeyType != KeyPwdConstant.IBTKeyType.TIME_LIMITED) {//非限时钥匙
			// 账号发送类型，需要检测接受者账号是否合法
			if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT && !StringUtil.isNum(receiver) && !StringUtil.isEmail(receiver)) {
				isPass = false;
				toast(R.string.note_receiver_format);
			}
		} else {//限时钥匙
			if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT && !StringUtil.isNum(receiver) && !StringUtil.isEmail(receiver)) {
				isPass = false;
				toast(R.string.note_receiver_format);
			} else if (!mStartTime.before(mEndTime)) {
				isPass = false;
				toast(R.string.note_time_start_greater_than_end);
			} else if (mKey.getKeyRight() == 1 && mKey.getKeyType() == 1 && mEndTime.after(new Date(mKey.getEndDate()))) { // 授权用户，限时钥匙，不能超期创建钥匙
				isPass = false;
				toast(getString(R.string.note_key_cant_beyond_validity_period));
			}
		}

		if (mKeyType != KeyPwdConstant.IBTKeyType.TIME_LIMITED) {
			if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT) {
				mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
			} else {
				mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
			}
		} else {
			if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT) {

				if (mStartTime.getTime() > System.currentTimeMillis()) {
					mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
				} else {
					mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
				}
			} else {
				mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_1);
			}
		}

		return isPass;
	}

	private void showShareKeyDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_share_key);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.WRAP_CONTENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			window.findViewById(R.id.tv_share_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (null != mCreatePwdKeyActionInfo) {
						mCreatePwdKeyActionInfo.setShare(true);
					}
					EventBus.getDefault().post(new Event(Event.EventType.CREATE_BT_KEY_SUCCESS, mCreatePwdKeyActionInfo));
					finish();
				}
			});
			window.findViewById(R.id.tv_skip_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (null != mCreatePwdKeyActionInfo) {
						mCreatePwdKeyActionInfo.setShare(false);
					}
					EventBus.getDefault().post(new Event(Event.EventType.CREATE_BT_KEY_SUCCESS, mCreatePwdKeyActionInfo));
					finish();
				}
			});
		}
	}

	/**
	 * 发送 ekey
	 */
	private void sendEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_V2_SEND)
				.loader(LockSendEkeyActivity.this)
				.params(getRequestParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						switch (code) {
							case 200:
								if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.SMS_LINK) {
									String data = result.getString("data");
									mCreatePwdKeyActionInfo.setShareUrl(data);
									showShareKeyDialog();
								} else {
									EventBus.getDefault().post(new Event(Event.EventType.CREATE_BT_KEY_SUCCESS, mCreatePwdKeyActionInfo));
									KeyCreateSuccessActivity.actionStart(LockSendEkeyActivity.this, KeyCreateSuccessActivity.FROM_KEY_CREATE);
									finish();
								}

								//LockManageBluetoothKeyActivity.actionStart(LockSendEkeyActivity.this, mLockId, mIsAdmin);
								break;

							case 920:
								toast(R.string.note_receive_user_not_found);
								break;

							case 951:
								toast(R.string.note_send_ekey_to_registered_user);
								break;

							case 952:
								toast(R.string.note_cannot_send_ekey_to_yourself);
								break;

							case 953:
								toast(R.string.note_no_auth_send_ekey);
								break;

							case 954:
								toast(R.string.note_cannot_exceed_expiration_send_ekey);
								break;

							default:
								toastBackendError(result, R.string.send_ekey_fail);
								break;
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.send_ekey_fail);
					}
				})
				.build()
				.post();
	}

	private WeakHashMap<String, Object> getRequestParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		String receiver = mEtReceiver.getText().toString();
		if (!TextUtils.isEmpty(receiver)) {
			params.put("recUser", StringUtil.isNum(receiver) ? mCountryCodePicker.getSelectedCountryCodeWithPlus() + receiver : receiver);
		}
		params.put("lockId", mLockId);
		params.put("type", mKeyType);
		params.put("keyAlias", mEtKeyName.getText().toString());

		if (mKeyType == KeyPwdConstant.IBTKeyType.TIME_LIMITED) { // 限时钥匙
			params.put("startDate", mTvStartTime.getText().toString());
			params.put("endDate", mTvEndTime.getText().toString());
			params.put("timeZone", DateUtil.getTimeZone());
		}

        params.put("arUnlock", binding.vppPermission.stv2.isChecked());
        params.put("auAdmin", isAuAdmin);
		if (mIsAdmin) {
            params.put("allowAllPermissions", binding.vppPermission.stv1.isChecked());
            params.put("allowSyncBattery", binding.vppPermission.stv3.isChecked());
            params.put("allowCalibrateTime", binding.vppPermission.stv4.isChecked());
		} else {
            params.put("allowAllPermissions", false);
            params.put("allowSyncBattery", false);
            params.put("allowCalibrateTime", false);
        }
		return params;
	}

	@SuppressLint("Range")
	private String[] getPhoneContacts(Uri uri) {
		String[] contact = new String[2];
		Cursor cursor = null;
		try {
			// 得到 ContentResolver 对象
			ContentResolver cr = getContentResolver();
			// 取得电话本中开始一项的光标
			cursor = cr.query(uri, null, null, null, null);

			if (cursor != null) {
				cursor.moveToFirst();
				// 取得联系人名字
				contact[0] = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));

				// 取得电话号码
				contact[1] = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}


		return contact;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK && requestCode == REQUEST_CONTACT) {
			if (data == null) {
				return;
			}
			Uri result = data.getData();
			String[] contact = getPhoneContacts(result);

			String phoneNum = contact[1].replaceAll(" ", "")
					.replaceFirst("^0*", "");

			mEtReceiver.setText(phoneNum);
			mEtKeyName.setText(contact[0]);
			mEtKeyName.setSelection(contact[0].length());
		}
	}
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        int id = group.getId();

        if (id == R.id.rg_valid_period) {
            selectValidPeriod(checkedId);
        } else if (id == R.id.rg_permission_types) {
            setPermissionTypes(checkedId);
        } else if (id == R.id.rg_share_the_key_through) {
            setShareTheKeyThrough(checkedId);
        }
    }

    private void selectValidPeriod(int checkedId) {
        mTvOneTimeNote.setVisibility(View.GONE);

        if (checkedId == R.id.rb_valid_period_permanent) {
            binding.rbValidPeriodPermanent.setChecked(true);
            mLlTime.setVisibility(View.GONE);
            mKeyType = KeyPwdConstant.IBTKeyType.PERMANENT;

        } else if (checkedId == R.id.rb_valid_period_time_limited) {
            binding.rbValidPeriodTimeLimited.setChecked(true);
            mLlTime.setVisibility(View.VISIBLE);
            mKeyType = KeyPwdConstant.IBTKeyType.TIME_LIMITED;
        }
    }

    private void setPermissionTypes(int checkedId) {

        if (checkedId == R.id.rb_general_user) {
            binding.rbGeneralUser.setChecked(true);
            binding.vppPermission.stv1.setVisibility(View.GONE);
            binding.vppPermission.isiv3.setChecked(false);
            binding.vppPermission.stv3.setVisibility(View.GONE);
            binding.vppPermission.stv4.setVisibility(View.GONE);
            isAuAdmin = false;
        } else if (checkedId == R.id.rb_authorized_user) {
            binding.rbAuthorizedUser.setChecked(true);
            binding.vppPermission.stv1.setVisibility(View.VISIBLE);
            binding.vppPermission.isiv3.setChecked(true);
            binding.vppPermission.stv3.setVisibility(View.VISIBLE);
            binding.vppPermission.stv4.setVisibility(View.VISIBLE);
            isAuAdmin = true;
        }
    }

    private void setShareTheKeyThrough(int checkedId) {
        if (checkedId == R.id.rb_share_key_through_account) {
            tv_share_the_key_through_hint.setText(R.string.share_type_populife_account);
            shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.ACCOUNT;
            ll_receiver.setVisibility(View.VISIBLE);
        } else if (checkedId == R.id.rb_share_key_through_sms_link) {
            tv_share_the_key_through_hint.setText(R.string.share_type_sms_link);
            shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.SMS_LINK;
            ll_receiver.setVisibility(View.GONE);
        }

        setSendBtnEnable();
    }


	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type) {
			case Event.EventType.CREATE_BT_KEY_SUCCESS:
				finish();
				break;
		}
	}
}
