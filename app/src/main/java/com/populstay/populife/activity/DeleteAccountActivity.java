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
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.sign.ISignListener;
import com.populstay.populife.sign.SignHandler;
import com.populstay.populife.util.activity.ActivityCollector;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.populstay.populife.util.timer.BaseCountDownTimer;
import com.populstay.populife.util.timer.ITimerListener;

import java.text.MessageFormat;

public class DeleteAccountActivity extends BaseActivity implements View.OnClickListener, ITimerListener {

	private static final String KEY_ACCOUNT = "key_account";

	private EditText mEtCode;
	private TextView mTvGetCode, mTvNote, mTvVerify;

	private BaseCountDownTimer mTimer = null;
	private String mAccount;

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param account 账号
	 */
	public static void actionStart(Context context, String account) {
		Intent intent = new Intent(context, DeleteAccountActivity.class);
		intent.putExtra(KEY_ACCOUNT, account);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_delete_account);

		mAccount = getIntent().getStringExtra(KEY_ACCOUNT);

		initView();
		initListener();
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
		mTvGetCode.setOnClickListener(this);
		mTvVerify.setOnClickListener(this);
	}

	private void validateVerificationCode() {
		RestClient.builder()
				.url(Urls.VERIFICATION_CODE_VALIDATE)
				.loader(this)
				.params("phone", mAccount)
				.params("code", mEtCode.getText().toString().trim())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("VERIFICATION_CODE_VALIDATE", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							boolean isValid = result.getBoolean("data");
							if (isValid) {
								deleteAccount();
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
	 * 删除账号时，获取验证码
	 */
	private void getVerificationCode() {
		RestClient.builder()
				.url(Urls.VERIFICATION_CODE_RESETPWD_DELETEACCOUNT_NEWDEVICELOGIN)
				.loader(this)
				.params("username", mAccount)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GET_VIRIFICATION_CODE_DELETE_ACCOUNT", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							// 获取验证码成功，开始倒计时
							mTimer = new BaseCountDownTimer(60, DeleteAccountActivity.this);
							mTimer.start();
							toast(R.string.note_get_verification_code_success);
						} else if (code==955) {
							toast(R.string.note_get_verification_code_time_limit);
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

	@SuppressLint("SetTextI18n")
	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.safety_verification);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mEtCode = findViewById(R.id.et_delete_account_code);
		mTvGetCode = findViewById(R.id.tv_delete_account_get_code);
		mTvNote = findViewById(R.id.tv_delete_account_note);
		mTvVerify = findViewById(R.id.tv_delete_account_verify);

		mTvNote.setText(getResources().getString(R.string.note_safety_verifiction_send_code) + " " + mAccount);
	}

	/**
	 * 删除账号
	 */
	private void deleteAccount() {
		RestClient.builder()
				.url(Urls.DELETE_ACCOUNT)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("DELETE_ACCOUNT", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_delete_account_success);
							SignHandler.onSignOut(new ISignListener() {
								@Override
								public void onSignInSuccess() {
								}

								@Override
								public void onSignUpSuccess() {
								}

								@Override
								public void onSignOutSuccess() {
									SignActivity.actionStart(DeleteAccountActivity.this, SignActivity.VAL_ACCOUNT_SIGN_IN);
									ActivityCollector.finishAll();
								}
							});
						} else {
							toastBackendError(result, R.string.note_delete_account_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_delete_account_fail);
					}
				})
				.build()
				.post();
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_delete_account_get_code) {
            // 判断用户名下是否有锁/钥匙，如果有则提示先删除锁/钥匙，没有则可以获取验证码，删除账号
            int accountLockNum = PeachPreference.getAccountLockNum(PeachPreference.readUserId());
            if (accountLockNum > 0) {
                toast(R.string.note_delete_account_remove_lock);
            } else {
                getVerificationCode();
            }
        } else if (id == R.id.tv_delete_account_verify) {
            validateVerificationCode();
        }
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
		mTvGetCode.setTextColor(getResources().getColor(R.color.white));
		mTvGetCode.setText(MessageFormat.format("{0} s", secondsLeft));
	}

	@Override
	public void onTimerFinish() {
		mTvGetCode.setEnabled(true);
		mTvGetCode.setTextColor(getResources().getColor(R.color.text_main));
		mTvGetCode.setText(getString(R.string.get_code));
	}
}
