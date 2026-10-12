package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.lock.ILockSetAdminKeyboardPwd;
import com.populstay.populife.manhattanlock.MHILockSetAdminKeyboardPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.ModifyAdminPasscodeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class ModifyAdminPasscodeActivity extends BaseActivity {

	public static final String KEY_PASSCODE = "key_content";
	public static final String KEY = "key";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";

	private ExEditText mEtInput;
	private TextView mTvSave;

	private String mContent;
	private Key mKey = MyApplication.CURRENT_KEY;
	private String mLockType;
	private boolean isMHLock = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_admin_passcode);
		getIntentData();
		initView();
	}

	private void getIntentData() {
		Intent intent = getIntent();
		mContent = intent.getStringExtra(KEY_PASSCODE);
		if (intent.hasExtra(KEY)) {
			mKey = intent.getParcelableExtra(KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
		if (intent.hasExtra(KEY_LOCK_TYPE)) {
			mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
			if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK) ||
					mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
					|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3)
			) {
				isMHLock = true;
			}
		}
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.admin_modify_passcode);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_save);
		mEtInput = findViewById(R.id.et_modify_passcode);
		TextView lenLimitTV = findViewById(R.id.tv_len_limit);
		if (isMHLock || mKey.getLockId() < 0) {//如果是曼哈顿
			lenLimitTV.setText(R.string.note_mh_passcode_format);
			mEtInput.setHint(getResources().getString(R.string.passcode_format_6_8_digits));
			mEtInput.setMaxLength(8);
		}else {
			lenLimitTV.setText(R.string.note_passcode_format);
			mEtInput.setHint(getResources().getString(R.string.passcode_format_6_9_digits));
			mEtInput.setMaxLength(9);
		}
		mEtInput.setText(mContent);
		mEtInput.setSelection(mContent.length());
		setEnableSave();
		mEtInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableSave();
			}
		});

		mTvSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String input = mEtInput.getText().toString();
				if (StringUtil.isBlank(input)) {
					toast(R.string.input_something);
				} else if (input.equals(mContent)) {
					toast(R.string.note_nothing_changed);
				} else if (!StringUtil.isNum(input) || input.length() < 6 || input.length() > 9) {
					toast(R.string.note_passcode_invalid);
				} else {
					if (isBleNetEnableWithToast()) {
						PeachLogger.d("[ACTION] 修改管理员密码 lockId=" + mKey.getLockId()
								+ " mac=" + mKey.getLockMac() + " hasLockData=" + !StringUtil.isBlank(mKey.getLockData()));
						showLoading();
						setCallback(input);
						if (isMHLock || (mKey.getLockId() < 0)) {
							if (sPPLOCK.isConnected(mKey.getLockMac())) {
								sPPLOCK.setAdminKeyboardPwd(mKey.getUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()),
										mKey.getNoKeyPwd(), input, mKey.getK1());
							} else {
//							    sPPLOCK.connect(mKey.getLockMac());
								startLockActionScan();
							}
						} else {
                            mTTLockAPI.modifyAdminPasscode(input, mKey.getLockData(), mKey.getLockMac(), new ModifyAdminPasscodeCallback() {
                                @Override
                                public void onModifyAdminPasscodeSuccess(String s) {
                                    stopLoading();
                                    setAdminKeyboardPwd(input);
                                }

                                @Override
                                public void onFail(LockError lockError) {
                                    stopLoading();
                                    toast(R.string.note_modify_admin_passcode_fail);
                                }
                            });
						}

					}
				}
			}
		});
	}

	public void setEnableSave() {
		mTvSave.setEnabled(null != mEtInput && !TextUtils.isEmpty(mEtInput.getTextStr()));
	}

	private void setCallback(final String input) {
		if (isMHLock || (mKey.getLockId() < 0)) {
			MyApplication.pplBleSession.setOperation(LockOperation.SET_ADMIN_KEYBOARD_PWD);
			MyApplication.pplBleSession.setPassword(input);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockSetAdminKeyboardPwd(new MHILockSetAdminKeyboardPwd() {
				@Override
				public void onSuccess() {
					stopLoading();
					setAdminKeyboardPwd(input);
				}

				@Override
				public void onFail() {
					stopLoading();
					toast(R.string.note_modify_admin_passcode_fail);
				}
//				@Override
//				public void onSetPwdSuccess() {
//					stopLoading();
//					setAdminKeyboardPwd(input);
//				}
//
//				@Override
//				public void onSetPwdFail() {
//					stopLoading();
//					toast(R.string.note_modify_admin_passcode_fail);
//				}
			});
		}
	}


	/**
	 * 设置锁的管理员密码
	 *
	 * @param keyboardPwd
	 */
	private void setAdminKeyboardPwd(final String keyboardPwd) {
		RestClient.builder()
				.url(Urls.LOCK_ADMIN_KEYBOARD_PWD_MODIFY)
				.params("password", keyboardPwd)
				.params("lockId", mKey.getLockId())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_ADMIN_KEYBOARD_PWD_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							Intent intent = new Intent();
							mKey.setNoKeyPwd(keyboardPwd);
							intent.putExtra(LockSettingsActivity.KEY_RESULT_DATA, keyboardPwd);
							setResult(RESULT_OK, intent);
							finish();
						} else {
							toastBackendError(code, R.string.note_modify_admin_passcode_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_admin_passcode_fail);
					}
				})
				.build()
				.post();
	}
}
