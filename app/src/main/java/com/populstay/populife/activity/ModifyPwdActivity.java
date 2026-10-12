package com.populstay.populife.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
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
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

public class ModifyPwdActivity extends BaseActivity {

	private TextView mTvSave;

	private ExEditText mEtOldPwd, mEtNewPwd, mEtRePwd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_pwd);

		initView();
		initListener();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_pwd);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_save_btn);
		mTvSave.setText(R.string.save);

		mEtOldPwd = findViewById(R.id.et_modify_pwd_old);
		mEtNewPwd = findViewById(R.id.et_modify_pwd_new);
		mEtRePwd = findViewById(R.id.et_modify_pwd_confirm);
		setEnableSaveBtn();
	}

	private void initListener() {
		mTvSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (checkForm()) {
					changePwd();
				}
			}
		});

		mEtOldPwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableSaveBtn();
			}
		});

		mEtNewPwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableSaveBtn();
			}
		});

		mEtRePwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableSaveBtn();
			}
		});
	}

	private void setEnableSaveBtn() {
		boolean isNotEmptyOldPwd = !TextUtils.isEmpty(mEtOldPwd.getTextStr());
		boolean isNotEmptyNewPwd = !TextUtils.isEmpty(mEtNewPwd.getTextStr());
		boolean isNotEmptyRePwd = !TextUtils.isEmpty(mEtRePwd.getTextStr());
		boolean isEnable = isNotEmptyOldPwd && isNotEmptyNewPwd && isNotEmptyRePwd;
		mTvSave.setEnabled(isEnable);
	}

	private void changePwd() {
		RestClient.builder()
				.url(Urls.ACCOUNT_PWD_MODIFY)
				.loader(this)
				.params("password", mEtOldPwd.getText().toString())
				.params("newPassword", mEtNewPwd.getText().toString())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_modify_pwd_success);
							PeachPreference.putStr(PeachPreference.ACCOUNT_PWD, mEtNewPwd.getText().toString());
							finish();
						} else if (code == 910) {
							toast(R.string.note_current_pwd_incorrect);
						} else {
							toastBackendError(result, R.string.note_modify_pwd_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_pwd_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_pwd_fail);
					}
				})
				.build()
				.post();
	}

	private boolean checkForm() {
		String oldPwd = mEtOldPwd.getText().toString();
		String newPwd = mEtNewPwd.getText().toString();
		String rePwd = mEtRePwd.getText().toString();

		boolean isPass = true;

		if (StringUtil.isBlank(oldPwd) || oldPwd.length() < 6
				|| StringUtil.isBlank(newPwd) || newPwd.length() < 6
				|| StringUtil.isBlank(rePwd) || rePwd.length() < 6) {
			toast(R.string.note_pwd_format);
			isPass = false;
		} else if (!newPwd.equals(rePwd)) {
			toast(R.string.note_confirm_pwd);
			isPass = false;
		}

		return isPass;
	}
}
