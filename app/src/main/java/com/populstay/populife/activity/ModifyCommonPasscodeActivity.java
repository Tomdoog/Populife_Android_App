package com.populstay.populife.activity;

import android.content.Context;
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
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockModifyPasscode;
import com.populstay.populife.lock.ILockSetAdminKeyboardPwd;
import com.populstay.populife.manhattanlock.MHILockModifyPasscode;
import com.populstay.populife.manhattanlock.MHILockSetAdminKeyboardPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.date.LockTime;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.ModifyAdminPasscodeCallback;
import com.ttlock.bl.sdk.callback.ModifyPasscodeCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import androidx.appcompat.widget.AppCompatTextView;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

/**
 * 普通密码、管理员密码修改
 */
public class ModifyCommonPasscodeActivity extends BaseActivity {

	public static final String KEY_PASSCODE = "key_content";
	public static final String KEY = "key";

	private ExEditText mEtInput;
	private TextView mTvSave, tv_original_pin_code;

	private Key mKey = MyApplication.CURRENT_KEY;
	private KeyPwd mPasscode;
	private boolean mIsLockOperationSuccess;

	public static void actionStart(Context context, KeyPwd passcode,Key key) {
		Intent intent = new Intent(context, ModifyCommonPasscodeActivity.class);
		intent.putExtra(KEY_PASSCODE, passcode);
		intent.putExtra(KEY, key);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_common_passcode);

		getIntentData();
		initView();
	}

	private void getIntentData() {
		Intent intent = getIntent();
		mPasscode = intent.getParcelableExtra(KEY_PASSCODE);
		if (intent.hasExtra(KEY)) {
			mKey = intent.getParcelableExtra(KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_pin_code);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mTvSave = findViewById(R.id.tv_save);
		tv_original_pin_code = findViewById(R.id.tv_original_pin_code);

		tv_original_pin_code.setText(mPasscode.getKeyboardPwd());
		mEtInput = findViewById(R.id.et_modify_passcode);
		AppCompatTextView mPlaceHoler = findViewById(R.id.tv_placeholder);
		if (mKey.getLockId()<0){
			mEtInput.setHint(getResources().getString(R.string.passcode_format_6_8_digits));
			mEtInput.setMaxLength(8);
			mPlaceHoler.setText(getResources().getString(R.string.passcode_format_6_8_digits));
		}else {
			mEtInput.setHint(getResources().getString(R.string.passcode_format_6_9_digits));
			mEtInput.setMaxLength(9);
			mPlaceHoler.setText(getResources().getString(R.string.passcode_format_6_9_digits));
		}
		//mEtInput.setSelection(mPasscode.getKeyboardPwd().length());
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
				} else if (input.equals(mPasscode.getKeyboardPwd())) {
					toast(R.string.note_nothing_changed);
				} else if (!StringUtil.isNum(input) || input.length() < 6 || input.length() > 9) {
					toast(R.string.note_passcode_invalid);
				} else {
					if (isAdminCode()) {
						if (isBleNetEnableWithToast()) {
							showLoading();
							modifyAdminPasscode(input);
						}
					} else {
						if (isNetEnableWithToast()) {
							if (isBleEnableWithoutToast()) {
								//和锁通信
								modifyPasscode(input);
							} else {
								if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
									showLoading();
									requestModifyPasscode(input, 2);
								} else {
									toast(R.string.enable_bluetooth);
								}
							}
						}
					}
				}
			}
		});
	}

	public void setEnableSave() {
		mTvSave.setEnabled(null != mEtInput && !TextUtils.isEmpty(mEtInput.getTextStr()));
	}

	private boolean isAdminCode() {
		return mPasscode.getKeyboardPwdType() == -1;
	}


	private void modifyPasscode(final String newPwd) {
		PeachLoader.showLoading(this);
		if (mKey.getLockId() < 0) {
			int pwdType = mPasscode.getKeyboardPwdType();
			if (mPasscode.getKeyboardPwdType() == 15) {
				pwdType = 3;
			}
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				MyApplication.pplBleSession.setKeyboardPwdType(pwdType);
				setModifyPasscodeCallback(newPwd);
				sPPLOCK.modifyUserKeyboardPwd(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()), mPasscode.getKeyboardPwd(), newPwd, pwdType, mPasscode.getStartDate(), mPasscode.getEndDate(), mKey.getK1());
			} else {
				MyApplication.pplBleSession.setKeyboardPwdType(pwdType);
				setModifyPasscodeCallback(newPwd);
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				startLockActionScan();
			}

		}else {
            mTTLockAPI.modifyPasscode(mPasscode.getKeyboardPwd(), newPwd, 0, 0, mKey.getLockData(), mKey.getLockMac(), new ModifyPasscodeCallback() {
                @Override
                public void onModifyPasscodeSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestModifyPasscode(newPwd, 1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsLockOperationSuccess = true;

                            if (lockError == LockError.LOCK_PASSWORD_NOT_EXIST) {
                                stopLoading();
                                toast(R.string.note_unused_passcode_cannot_be_modified);
                            } else {
                                if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestModifyPasscode(newPwd, 2);
                                } else {
                                    stopLoading();
                                    toast(R.string.operation_fail);
                                }
                            }
                        }
                    });
                }
            });
		}
	}

	private void setModifyPasscodeCallback(final String newPwd) {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.MODIFY_KEYBOARD_PWD);
			//MyApplication.pplBleSession.setKeyboardPwdType(mPasscode.getKeyboardPwdType());
			MyApplication.pplBleSession.setKeyboardPwdOriginal(mPasscode.getKeyboardPwd());
			MyApplication.pplBleSession.setKeyboardPwdNew(newPwd);
			MyApplication.pplBleSession.setStartDate(mPasscode.getStartDate());
			MyApplication.pplBleSession.setEndDate(mPasscode.getEndDate());
			MyApplication.pplBleSession.setmILockModifyPasscode(new MHILockModifyPasscode() {

				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							requestModifyPasscode(newPwd, 1);
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mIsLockOperationSuccess = true;
							stopLoading();
							toast(R.string.operation_fail);
						}
					});
				}
			});
		}
	}

	/**
	 * mediumType可选	Integer
	 * 通讯介质（1：蓝牙，2：网关，默认是1）
	 * 请求服务器，修改键盘密码
	 */
	private void requestModifyPasscode(final String newPwd, int mediumType) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_MODIFY)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("keyboardPwdId", mPasscode.getId())
				.params("changeType", 1)
				.params("newKeyboardPwd", newPwd)
				.params("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()))
				.params("mediumType", mediumType)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mPasscode.setKeyboardPwd(newPwd);
							toast(R.string.operation_success);
							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_PASSCODE, newPwd));
							finish();
						} else {
							toast(R.string.operation_fail);
						}
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				stopLoading();
				toast(R.string.operation_fail);
			}
		})
				.build()
				.post();
	}

	private void modifyAdminPasscode(String input) {
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setAdminKeyboardPwdCallback(input);
				int pwdType = mPasscode.getKeyboardPwdType();
				if(mPasscode.getKeyboardPwdType()==15){
					pwdType = 3;
				}
				sPPLOCK.setAdminKeyboardPwd(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),mKey.getNoKeyPwd(),input,mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				setAdminKeyboardPwdCallback(input);
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}

		}else{
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


	private void setAdminKeyboardPwdCallback(final String input) {
		if (mKey.getLockId()<0){
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
							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE, keyboardPwd));
							/*intent.putExtra(LockSettingsActivity.KEY_RESULT_DATA, keyboardPwd);
							setResult(RESULT_OK, intent);*/
							finish();
						} else {
							toast(R.string.note_modify_admin_passcode_fail);
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
