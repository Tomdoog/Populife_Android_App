package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.constant.Constant;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.sign.ISignListener;
import com.populstay.populife.sign.SignHandler;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.activity.ActivityCollector;
import com.populstay.populife.util.device.DeviceUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.populstay.populife.util.timer.BaseCountDownTimer;
import com.populstay.populife.util.timer.ITimerListener;

import java.text.MessageFormat;
import java.util.WeakHashMap;

public class LoginVerifyActivity extends BaseActivity implements View.OnClickListener, ITimerListener,
		ISignListener {

	private static final String KEY_ACCOUNT_TYPE = "key_account_type";
	private static final String KEY_COUNTRY_CODE = "key_country_code";
	private static final String KEY_LOGIN_ACCOUNT = "key_login_account";
	private static final String KEY_LOGIN_RESPONSE = "key_login_response";
	private static final String KEY_LOGIN_PWD = "key_login_pwd";

    private Context mContext;
    private ExEditText mEtCode;
	private TextView mTvGetCode, mTvNote, mTvVerify;

	private BaseCountDownTimer mTimer = null;
	private ISignListener mISignListener = this;
	private String mCountryCode, mLoginAccount, mLoginResponse, mLoginPwd;
	private int mAccountType = Constant.ACCOUNT_TYPE_PHONE;

	/**
	 * 启动当前 activity
	 *
	 * @param context       上下文
	 * @param signType      登录类型（手机号：1，邮箱：2）
	 * @param countryCode   国家编码（如：+86）
	 * @param account       账号（手机号(含国家编码+86)/邮箱）
	 * @param loginResponse 登录请求时的返回数据
	 * @param loginPwd      登录密码
	 */
	public static void actionStart(Context context, int signType, String countryCode, String account,
								   String loginResponse, String loginPwd) {
		Intent intent = new Intent(context, LoginVerifyActivity.class);
		intent.putExtra(KEY_ACCOUNT_TYPE, signType);
		intent.putExtra(KEY_COUNTRY_CODE, countryCode);
		intent.putExtra(KEY_LOGIN_ACCOUNT, account);
		intent.putExtra(KEY_LOGIN_RESPONSE, loginResponse);
		intent.putExtra(KEY_LOGIN_PWD, loginPwd);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mContext = this;
		setContentView(R.layout.activity_login_verify);
		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		mAccountType = getIntent().getIntExtra(KEY_ACCOUNT_TYPE, Constant.ACCOUNT_TYPE_PHONE);
		mCountryCode = getIntent().getStringExtra(KEY_COUNTRY_CODE);
		mLoginAccount = getIntent().getStringExtra(KEY_LOGIN_ACCOUNT);
		mLoginResponse = getIntent().getStringExtra(KEY_LOGIN_RESPONSE);
		mLoginPwd = getIntent().getStringExtra(KEY_LOGIN_PWD);
	}

	@SuppressLint("SetTextI18n")
	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.safety_verification);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mEtCode = findViewById(R.id.et_login_verify_code);
		mTvGetCode = mEtCode.getVerifictionCodeView();
		mTvGetCode.setEnabled(true);
		mTvNote = findViewById(R.id.tv_login_verify_note);
		mTvVerify = findViewById(R.id.tv_login_verify);

		mTvNote.setText(getResources().getString(R.string.note_safety_verifiction_send_code) + " " + mLoginAccount);
	}


	private void initListener() {
		mEtCode.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				mTvVerify.setEnabled(!StringUtil.isBlank(editable.toString().trim()));
			}
		});
		mTvGetCode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// 获取验证码
				getVerificationCode();
			}
		});
		mTvVerify.setOnClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tv_login_verify) {
            validateVerificationCode();
        }
    }

    private void getVerificationCode() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("username", mLoginAccount);
		if (mAccountType == Constant.ACCOUNT_TYPE_PHONE) { // 使用手机找回密码时，需传入国家编码（如：+86）
			params.put("country", mCountryCode);
		}
        mTimer = new BaseCountDownTimer(60, LoginVerifyActivity.this);
        mTimer.start();
		RestClient.builder()
				.url(Urls.USER_LOGIN_BYCODE_SEND_CODE)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("USER_LOGIN_BYCODE_SEND_CODE", response);
						JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        String msg = result.getString("msg");
						if (code == 200) {
							toast(R.string.note_get_verification_code_success);
						} else if (code == 955) {
                            toast(Utils.get955CodeLimit(mContext, msg));
                        } else {
							toastBackendError(result, R.string.note_get_verification_code_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_get_verification_code_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 验证验证码是否正确
	 */
	private void validateVerificationCode() {
		RestClient.builder()
				.url(Urls.USER_LOGIN_BYCODE)
				.loader(this)
				//.params("phone", mLoginAccount)
				.params("username", mLoginAccount)
				.params("code", mEtCode.getText().toString().trim())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("USER_LOGIN_BYCODE", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							boolean isValid = result.getBoolean("success");
							if (isValid) {
								//验证成功，执行登录成功后的逻辑
								PeachPreference.putStr(PeachPreference.ACCOUNT_PWD, mLoginPwd);
								SignHandler.onSignIn(mLoginResponse, mISignListener);
								PeachPreference.updateLastCheckRemoteLoginTime();
								//新设备登录，推送异地登录
								signInNewDevicePush();
							} else {
								toast(R.string.note_verifiction_code_invalid);
							}
						} else {
							toastBackendError(result, R.string.operation_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.operation_fail);
					}
				})
				.build()
				.get();
	}

	/**
	 * 重登录推送通知（用户在另一设备登录时，向前一设备推送下线通知）
	 */
	private void signInNewDevicePush() {
		final JSONObject result = JSON.parseObject(mLoginResponse);
		String userId = result.getJSONObject("data").getString("userId");
		RestClient.builder()
				.url(Urls.SIGN_IN_NEW_DEVICE_PUSH)
				.params("userId", userId)
				.params("deviceId", DeviceUtil.getDeviceId(this))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("SIGN_IN_NEW_DEVICE_PUSH", response);
					}
				})
				.build()
				.post();
	}

	@Override
	public void onSignInSuccess() {
		goToNewActivity(MainActivity.class);
		ActivityCollector.finishAll();
	}

	@Override
	public void onSignUpSuccess() {
		goToNewActivity(MainActivity.class);
		ActivityCollector.finishAll();
	}

	@Override
	public void onSignOutSuccess() {
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	@Override
	public void onTimerTick(final long secondsLeft) {
		mTvGetCode.setEnabled(false);
		mTvGetCode.setText(MessageFormat.format("{0} s", secondsLeft));
	}

	@Override
	public void onTimerFinish() {
		mTvGetCode.setEnabled(true);
		mTvGetCode.setText(getString(R.string.get_code));
	}

	@Override
	protected void queryLatestDeviceId() {

	}
}
