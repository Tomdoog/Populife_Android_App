package com.populstay.populife.keypwdmanage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
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
import com.populstay.populife.lock.ILockIcCardClear;
import com.populstay.populife.lock.ILockResetKeyboardPwd;
import com.populstay.populife.manhattanlock.MHILockClearCards;
import com.populstay.populife.manhattanlock.MHILockClearFingers;
import com.populstay.populife.manhattanlock.MHILockResetKeyboardPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.ClearAllFingerprintCallback;
import com.ttlock.bl.sdk.callback.ClearAllICCardCallback;
import com.ttlock.bl.sdk.callback.ResetPasscodeCallback;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class KeyPwdMoreActivity extends BaseActivity implements View.OnClickListener {

	private int mAccessType = KeyPwdConstant.IType.TYPE_KEY;
	private Key mKey = MyApplication.CURRENT_KEY;

	private LinearLayout mLlInvalidCode, mLlAdminCode;
	private Switch mSwitchShowAdminCode;
	private TextView tvTitle;
	private TextView tv_browse_invalid, tv_clear_btn;
	private String mLockType;
	private AlertDialog DIALOG;
	private EditText mEtDialogInput;

	public static void actionStart(Context context, Key key, int type) {
		Intent intent = new Intent(context, KeyPwdMoreActivity.class);
		intent.putExtra("key", key);
		intent.putExtra("type", type);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_key_pwd_more);
		getIntentData();
		initView();
		initListener();
		initStatus();
	}

	private void initView() {
		tvTitle = findViewById(R.id.page_title);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		tv_browse_invalid = findViewById(R.id.tv_browse_invalid);
		mLlInvalidCode = findViewById(R.id.ll_invalid_code);
		mLlAdminCode = findViewById(R.id.ll_admin_code);
		mSwitchShowAdminCode = findViewById(R.id.switch_show_admin_code);
		tv_clear_btn = findViewById(R.id.tv_clear_btn);

	}

	private void initListener() {
		mLlInvalidCode.setOnClickListener(this);
		tv_clear_btn.setOnClickListener(this);
		mSwitchShowAdminCode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				PeachPreference.showLockAdminCode(mKey.getLockId(), isChecked);
				EventBus.getDefault().post(new Event(Event.EventType.SHOW_LOCK_ADMIN_CODE_CONFIG_CHANGE));
			}
		});
	}

	private void initStatus() {
		if (mKey.isAdmin() && KeyPwdConstant.IType.TYPE_PWD == mAccessType) {
			mLlAdminCode.setVisibility(View.VISIBLE);
		} else {
			mLlAdminCode.setVisibility(View.GONE);
		}

		if (mKey.isAdmin() || (KeyPwdConstant.IType.TYPE_KEY == mAccessType && mKey.getKeyRight() == 1)) {
			tv_clear_btn.setVisibility(View.VISIBLE);
		} else {
			tv_clear_btn.setVisibility(View.GONE);
		}

		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_KEY:
				tvTitle.setText(R.string.management_setting_title_key);
				tv_browse_invalid.setText(R.string.management_title_key_invalid);
				tv_clear_btn.setText(R.string.clear_all_keys);
				break;

			case KeyPwdConstant.IType.TYPE_PWD:
				tvTitle.setText(R.string.management_setting_title_pwd);
				tv_browse_invalid.setText(R.string.management_title_pwd_invalid);
				tv_clear_btn.setText(R.string.clear_all_codes);
				mSwitchShowAdminCode.setChecked(PeachPreference.isShowLockAdminCode(mKey.getLockId()));
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				tvTitle.setText(R.string.management_setting_title_fingerprint);
				tv_browse_invalid.setText(R.string.management_title_fingerprint_invalid);
				tv_clear_btn.setText(R.string.clear_all_fingerprint);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				tvTitle.setText(R.string.management_setting_title_ic_card);
				tv_browse_invalid.setText(R.string.management_title_ic_card_invalid);
				tv_clear_btn.setText(R.string.clear_all_ic_card);
				break;

			default:
				break;
		}
	}

	private void getIntentData() {
		mKey = getIntent().getParcelableExtra("key");
		if (mKey.getLockName()!=null){
			mLockType = mKey.getLockName();
		}
		mAccessType = getIntent().getIntExtra("type", KeyPwdConstant.IType.TYPE_KEY);
	}

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.ll_invalid_code) {
            KeyPwdManageActivity.actionStart(this, mKey, mAccessType, KeyPwdConstant.IFrom.FROM_MORE, mLockType);

        } else if (id == R.id.tv_clear_btn) {
            showInputDialog();
        }
    }

    private void showInputDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
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

			title.setText(R.string.enter_account_passwprd);
			mEtDialogInput.setHint(R.string.enter_pwd);
			mEtDialogInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
			DialogUtil.autoPopSoftInput(window, mEtDialogInput);

			cancel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					DIALOG.dismiss();
				}
			});
			ok.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String inputPwd = mEtDialogInput.getText().toString();
					if (!StringUtil.isBlank(inputPwd)) {
						verifyAccountPwd(inputPwd);
						DIALOG.cancel();
					} else {
						toast(R.string.enter_account_passwprd);
					}
				}
			});
		}
	}

	private void verifyAccountPwd(String pwd) {
		RestClient.builder()
				.url(Urls.LOCK_USER_CHECK)
				.loader(KeyPwdMoreActivity.this)
				.params("password", pwd)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_VERIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							boolean isPwdValid = result.getBoolean("success");
							if (isPwdValid) {
								DialogUtil.showCommonDialog(KeyPwdMoreActivity.this, null,
										getDialogDeleteHint(), getString(R.string.clear),
										getString(R.string.cancel), new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialogInterface, int i) {
												if (isNetEnableWithToast()) {
													switch (mAccessType) {
														case KeyPwdConstant.IType.TYPE_KEY:
															requestClearAllEkeys();
															break;

														case KeyPwdConstant.IType.TYPE_PWD:
															if (isBleEnableWithToast()) {
																resetPasscode();
															}
															break;

														case KeyPwdConstant.IType.TYPE_IC_CARD:
															lockClearIcCards();
															break;

														case KeyPwdConstant.IType.TYPE_FINGERPRINT:
															lockClearFingerprints();
															break;

														default:
															break;
													}
												}
											}
										}, null);
							} else {
								toastBackendError(code, R.string.note_pwd_invalid);
							}
						} else {
							toastBackendError(code, R.string.note_pwd_invalid);
						}
					}
				})
				.build()
				.post();
	}

	private String getDialogDeleteHint() {
		int resId = 0;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_KEY:
				resId = R.string.confirm_clear_all_keys;
				break;

			case KeyPwdConstant.IType.TYPE_PWD:
				resId = R.string.confirm_clear_all_codes;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				resId = R.string.confirm_clear_all_ic_cards;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				resId = R.string.confirm_clear_all_fingerprints;
				break;

			default:
				break;
		}

		return getString(resId);
	}

	/**
	 * 通过 SDK 清除锁中所有的 IC 卡信息
	 */
	private void lockClearIcCards() {
		showLoading();
		if (mKey.getLockId()<0){
            setClearIcCardCallback();
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.clearCards(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKey.getK1());
			} else {
				startLockActionScan();
			}
		}else {
            PeachLogger.d("[ACTION] 清空所有IC卡 lockId=" + mKey.getLockId());
            mTTLockAPI.clearAllICCard(mKey.getLockData(), mKey.getLockMac(), new ClearAllICCardCallback() {
                @Override
                public void onClearAllICCardSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            requestClearIcCard();
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            toast(R.string.operation_fail);
                        }
                    });
                }
            });

		}

	}

	private void setClearIcCardCallback() {
		if (mKey.getLockId()<0) {
			MyApplication.pplBleSession.setOperation(LockOperation.CLEAR_CARDS);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockClearCards(new MHILockClearCards() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestClearIcCard();
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.operation_fail);
						}
					});
				}
			});
		}
	}

	/**
	 * 通过 SDK 清除锁中所有的指纹信息
	 */
	private void lockClearFingerprints() {
		showLoading();
		if(mKey.getLockId()<0) {
            setCleaFingerprintCallback();
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.clearFingers(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKey.getK1());
			} else {
				startLockActionScan();
			}
		}else {
            PeachLogger.d("[ACTION] 清空所有指纹 lockId=" + mKey.getLockId());
            mTTLockAPI.clearAllFingerprints(mKey.getLockData(), mKey.getLockMac(), new ClearAllFingerprintCallback() {
                @Override
                public void onClearAllFingerprintSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            requestClearFingerprint();
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            toast(R.string.operation_fail);
                        }
                    });
                }
            });
		}
	}

	private void setCleaFingerprintCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.CLEAR_FINGERS);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockClearFingers(new MHILockClearFingers() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestClearFingerprint();
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.operation_fail);
						}
					});
				}
			});
		}
	}

	/**
	 * 请求服务器，清空 IC 卡
	 */
	private void requestClearIcCard() {
		RestClient.builder()
				.url(Urls.IC_CARD_CLEAR)
				.loader(KeyPwdMoreActivity.this)
				.params("lockId", mKey.getLockId())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_CLEAR", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toastSuccess();
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_PWD));
							finish();
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toastFail();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toastFail();
					}
				})
				.build()
				.post();
	}

	/**
	 * 请求服务器，清空指纹
	 */
	private void requestClearFingerprint() {
		RestClient.builder()
				.url(Urls.FINGERPRINT_CLEAR)
				.loader(KeyPwdMoreActivity.this)
				.params("lockId", mKey.getLockId())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("FINGERPRINT_CLEAR", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toastSuccess();
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_PWD));
							finish();
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toastFail();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toastFail();
					}
				})
				.build()
				.post();
	}

	private void resetPasscode() {
		showLoading();
		setLockOperateCallback();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.resetKeyboardPwd(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.resetPasscode(mKey.getLockData(), mKey.getLockMac(), new ResetPasscodeCallback() {
                @Override
                public void onResetPasscodeSuccess(String s) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            requestResetPasscode(s, System.currentTimeMillis());
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            toast(R.string.note_passcode_reset_fail);
                        }
                    });
                }
            });
		}

	}

	private void setLockOperateCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.RESET_KEYBOARD_PWD);
			MyApplication.pplBleSession.setmILockResetKeyboardPwd(new MHILockResetKeyboardPwd() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
//							EventBus.getDefault().post(new Event(Event.EventType.CLEAR_PWDS));
//							toastSuccess();
//							finish();
							mKey.setPwdInfo("");
							requestResetPasscode(mKey.getPwdInfo(),mKey.getTimestamp());
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.note_clear_lock_pwd_fail);
						}
					});

				}
			});
		}
	}

	/**
	 * 请求服务器，重置键盘密码
	 *
	 * @param pwdInfo
	 * @param timestamp
	 */
	private void requestResetPasscode(String pwdInfo, long timestamp) {
		if (mKey.getLockId()<0){
			RestClient.builder()
					.url(Urls.LOCK_PASSCODE_RESET)
					.loader(this)
					.params("userId", PeachPreference.readUserId())
					.params("lockId", mKey.getLockId())
					.params("pwdInfo", "")
					.success(new ISuccess() {
						@Override
						public void onSuccess(String response) {
							PeachLogger.d("LOCK_PASSCODE_RESET", response);

							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
							if (code == 200) {
								EventBus.getDefault().post(new Event(Event.EventType.CLEAR_PWDS));
								toastSuccess();
								finish();
							} else {
								toastBackendError(code, R.string.operation_fail);
							}
						}
					})
					.failure(new IFailure() {
						@Override
						public void onFailure() {
							toastFail();
						}
					})
					.error(new IError() {
						@Override
						public void onError(int code, String msg) {
							toastFail();
						}
					})
					.build()
					.post();

		}else {
			RestClient.builder()
					.url(Urls.LOCK_PASSCODE_RESET)
					.loader(this)
					.params("userId", PeachPreference.readUserId())
					.params("lockId", mKey.getLockId())
					.params("pwdInfo", pwdInfo)
					.params("timestamp", timestamp)
					.success(new ISuccess() {
						@Override
						public void onSuccess(String response) {
							PeachLogger.d("LOCK_PASSCODE_RESET", response);

							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
							if (code == 200) {
								EventBus.getDefault().post(new Event(Event.EventType.CLEAR_PWDS));
								toastSuccess();
								finish();
							} else {
								toastBackendError(code, R.string.operation_fail);
							}
						}
					})
					.failure(new IFailure() {
						@Override
						public void onFailure() {
							toastFail();
						}
					})
					.error(new IError() {
						@Override
						public void onError(int code, String msg) {
							toastFail();
						}
					})
					.build()
					.post();

		}

	}

	private void requestClearAllEkeys() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_CLEAR)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							EventBus.getDefault().post(new Event(Event.EventType.CLEAR_KEYS));
							toast(R.string.note_bluetooth_keys_clear_success);
						} else {
							toastBackendError(code, R.string.note_bluetooth_keys_clear_fail);
							finish();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_bluetooth_keys_clear_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_bluetooth_keys_clear_fail);
					}
				})
				.build()
				.post();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (null != DIALOG) {
			DIALOG.dismiss();
		}
	}
}
