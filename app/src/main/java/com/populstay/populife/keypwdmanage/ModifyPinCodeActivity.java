package com.populstay.populife.keypwdmanage;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockModifyPasscode;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.date.LockTime;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.ModifyPasscodeCallback;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;

public class ModifyPinCodeActivity extends BaseActivity {

	private TextView mTvSave;
	private ExEditText mEtOldPwd, mEtNewPwd, mEtRePwd;

	private Key mKey;
	private KeyPwd mPasscode;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_pin_code);

		initView();
		initListener();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_pin_code);
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
					modifyPasscode(mEtNewPwd.getText().toString().trim());
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
							toast(R.string.note_modify_pwd_fail);
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
				|| StringUtil.isBlank(newPwd) || newPwd.length() < 6) {
			toast(R.string.note_passcode_invalid);
			isPass = false;
		} else if (newPwd.equals(oldPwd)) {
			//toast(R.string.note_confirm_pwd);
			isPass = false;
		}else if (!isBleNetEnableWithToast()){
			isPass = false;
		}

		return isPass;
	}


	private void modifyPasscode(String newPwd) {
		PeachLoader.showLoading(this);
        mTTLockAPI.modifyPasscode(mPasscode.getKeyboardPwd(), newPwd, 0, 0, mKey.getLockData(), mKey.getLockMac(), new ModifyPasscodeCallback() {
            @Override
            public void onModifyPasscodeSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        requestModifyPasscode(newPwd);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        if (lockError == LockError.LOCK_PASSWORD_NOT_EXIST) {
                            toast(R.string.note_unused_passcode_cannot_be_modified);
                        } else {
                            toast(R.string.operation_fail);
                        }
                    }
                });
            }
        });
	}

	/**
	 * 请求服务器，修改键盘密码
	 */
	private void requestModifyPasscode(final String newPwd) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_MODIFY)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("keyboardPwdId", mPasscode.getId())
				.params("changeType", 1)
				.params("newKeyboardPwd", newPwd)
				.params("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mPasscode.setKeyboardPwd(newPwd);
							toast(R.string.operation_success);
						} else {
							toast(R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}
}
