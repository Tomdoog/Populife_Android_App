package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
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
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.populstay.populife.util.timer.BaseCountDownTimer;
import com.populstay.populife.util.timer.ITimerListener;

import java.text.MessageFormat;
import java.util.WeakHashMap;

public class ResetPwdActivity extends BaseActivity implements TextWatcher, View.OnClickListener, ITimerListener {
    private Context mContext;

	private EditText mEtCode, mEtPwd;
	private TextView mTvGetCode, mTvNote, mTvReset;

	private BaseCountDownTimer mTimer = null;
	private String mCountryCode, mLoginAccount;
	private int mAccountType = Constant.ACCOUNT_TYPE_PHONE;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mContext = this;
		setContentView(R.layout.activity_reset_pwd);

		loadCachePersonalInfo();
		initView();
		initListener();
	}

	private void loadCachePersonalInfo() {
		mAccountType = PeachPreference.getAccountRegisterType();
		String phone = PeachPreference.getStr(PeachPreference.ACCOUNT_PHONE);
		String email = PeachPreference.getStr(PeachPreference.ACCOUNT_EMAIL);
		mLoginAccount = mAccountType == Constant.ACCOUNT_TYPE_PHONE ? phone : email;

	}

	@SuppressLint("SetTextI18n")
	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.reset_pwd);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mEtCode = findViewById(R.id.et_reset_code);
		mEtPwd = findViewById(R.id.et_reset_pwd);
		mTvGetCode = findViewById(R.id.tv_reset_get_code);
		mTvNote = findViewById(R.id.tv_reset_pwd_note);
		mTvReset = findViewById(R.id.tv_reset_pwd);

		mTvNote.setText(getResources().getString(R.string.note_safety_verifiction_send_code) + " " + mLoginAccount);
	}

	private void initListener() {
		mEtCode.addTextChangedListener(this);
		mEtPwd.addTextChangedListener(this);
		mTvGetCode.setOnClickListener(this);
		mTvReset.setOnClickListener(this);
	}

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.tv_reset_get_code) {
            // 获取验证码
            getVerificationCode();
        } else if (id == R.id.tv_reset_pwd) {
            resetPwd();
        }
    }

    /**
	 * （忘记密码后）重置密码时，获取验证码
	 */
	private void getVerificationCode() {
		PeachLogger.d("account: "+mLoginAccount+", type: "+mAccountType);
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("username", mLoginAccount);
//		if (mAccountType == Constant.ACCOUNT_TYPE_PHONE) { // 使用手机找回密码时，需传入国家编码（如：+86）
//			params.put("country", mCountryCode);
//		}
        startTimer();
		RestClient.builder()
				.url(Urls.VERIFICATION_CODE_RESETPWD_DELETEACCOUNT_NEWDEVICELOGIN)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GET_VIRIFICATION_CODE_RETRIEVE_PWD", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
                        String msg = result.getString("msg");
						if (code == 200) {
							// 获取验证码成功，开始倒计时
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
	 * 开始倒计时
	 */
	private void startTimer() {
		mTimer = new BaseCountDownTimer(60, ResetPwdActivity.this);
		mTimer.start();
	}

	/**
	 * 停止倒计时
	 */
	private void stopTimer() {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	/**
	 * 重置密码
	 */
	private void resetPwd() {
		RestClient.builder()
				.url(Urls.ACCOUNT_PWD_RESET)
				.loader(this)
				.params("username", mLoginAccount)
				.params("password", mEtPwd.getText().toString())
				.params("code", mEtCode.getText().toString())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_RESET", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_reset_pwd_success);
							PeachPreference.putStr(PeachPreference.ACCOUNT_PWD, mEtPwd.getText().toString());
							finish();
						} else if (code == 954 || code == 953) {
							toast(R.string.note_verifiction_code_invalid);
						} else {
							toastBackendError(result, R.string.note_reset_pwd_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_reset_pwd_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_reset_pwd_fail);
					}
				})
				.build()
				.post();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		mTvReset.setEnabled(checkForm());
	}

	private boolean checkForm() {
		String code = mEtCode.getText().toString().trim();
		String pwd = mEtPwd.getText().toString().trim();
		boolean isPass = true;
		if (StringUtil.isBlank(code) || StringUtil.isBlank(pwd) || pwd.length() < 6) {
			isPass = false;
		}
		return isPass;
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
	public void onDestroy() {
		super.onDestroy();
		stopTimer();
	}
}
