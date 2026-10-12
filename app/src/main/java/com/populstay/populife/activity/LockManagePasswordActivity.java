package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.adapter.PasscodeListAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.Passcode;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.lock.ILockGetOperateLog;
import com.populstay.populife.lock.ILockResetKeyboardPwd;
import com.populstay.populife.manhattanlock.MHILockGetOperateLog;
import com.populstay.populife.manhattanlock.MHILockResetKeyboardPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.GetOperationLogCallback;
import com.ttlock.bl.sdk.callback.ResetPasscodeCallback;
import com.ttlock.bl.sdk.constant.LogType;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockManagePasswordActivity extends BaseActivity implements View.OnClickListener,
		AdapterView.OnItemClickListener {

	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_LOCK_NAME = "key_lock_name";
	private static final String KEY_LOCK_MAC = "key_lock_mac";
	private static final String KEY_KEY = "KEY_KEY";

	public List<Passcode> mPasscodeList = new ArrayList<>();
	private AlertDialog DIALOG;
	private ImageView mIvMenu, mIvSync;
	private LinearLayout mLlNoData;
	private ListView mListView;
	private PasscodeListAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;
	private EditText mEtDialogInput;
	private Key mKey = MyApplication.CURRENT_KEY;
	private int mLockId;
	private int mKeyId;
	private String mLockName;
	private String mLockMac;
	private String mInputPwd;
	private String mLockType;
	private boolean isMHLock = false;
	private int mPwdWrongCount; // 输入账号密码错误次数

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁 id
	 */
	public static void actionStart(Context context, int lockId, int keyId, String lockName, String lockMac, Key key) {
		Intent intent = new Intent(context, LockManagePasswordActivity.class);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_KEY_ID, keyId);
		intent.putExtra(KEY_LOCK_NAME, lockName);
		intent.putExtra(KEY_LOCK_MAC, lockMac);
		intent.putExtra(KEY_KEY, key);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_manage_password);
		getIntentData();
		initView();
		initListener();
	}

	@Override
	protected void onResume() {
		super.onResume();
		requestPasscodeList();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mLockId = data.getIntExtra(KEY_LOCK_ID, 0);
		mKeyId = getIntent().getIntExtra(KEY_KEY_ID, 0);
		mLockName = getIntent().getStringExtra(KEY_LOCK_NAME);
		mLockMac = getIntent().getStringExtra(KEY_LOCK_MAC);
		mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
		if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			isMHLock = true;
		}
		if (data.hasExtra(KEY_KEY)) {
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		mIvMenu = findViewById(R.id.iv_manage_passcode_menu);
		mIvSync = findViewById(R.id.iv_manage_passcode_sync);
		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.list_view);
		mAdapter = new PasscodeListAdapter(LockManagePasswordActivity.this, mPasscodeList);
		mListView.setAdapter(mAdapter);

		mRefreshLayout = findViewById(R.id.refresh_layout);
		mRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
		mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				mRefreshLayout.post(new Runnable() {
					@Override
					public void run() {
						mRefreshLayout.setRefreshing(true);
						requestPasscodeList();
					}
				});
			}
		});
	}

	private void initListener() {
		mIvMenu.setOnClickListener(this);
		mIvSync.setOnClickListener(this);
		mListView.setOnItemClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.iv_manage_passcode_menu) {
            showActionDialog();
        } else if (id == R.id.iv_manage_passcode_sync) {
            DialogUtil.showCommonDialog(
                    LockManagePasswordActivity.this,
                    getString(R.string.sync_password_status),
                    getString(R.string.note_sync_password_status),
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (isBleNetEnableWithToast())
                                // 读取锁密码操作记录
                                readLockOperateLog();
                        }
                    },
                    null
            );
        } else if (id == R.id.btn_dialog_send_ekey_one_time) { // 清空密码
            DIALOG.cancel();
            if (mPasscodeList == null || mPasscodeList.isEmpty()) {
                toast(R.string.note_no_passcode_in_list);
            } else {
                showInputDialog();
            }
        } else if (id == R.id.btn_dialog_send_ekey_period) { // 创建密码
            ArrayList<String> passwordList = new ArrayList<>();
            passwordList.add(mKey.getNoKeyPwd());
            for (Passcode passcode : mPasscodeList) {
                passwordList.add(passcode.getKeyboardPwd());
            }
            LockSendPasscodeActivity.actionStart(
                    LockManagePasswordActivity.this,
                    mKey,
                    mLockId,
                    mKeyId,
                    mLockName,
                    mLockMac,
                    passwordList,
                    ""
            );
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_send_ekey_cancel || id == R.id.btn_dialog_input_cancel) {
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_input_ok) {
            mInputPwd = mEtDialogInput.getText().toString();
            if (!StringUtil.isBlank(mInputPwd)) {
                verifyAccountPwd(mInputPwd);
                DIALOG.cancel();
            } else {
                toast(R.string.enter_account_passwprd);
            }
        }
    }


    /**
	 * 读取锁操作记录
	 */
	private void readLockOperateLog() {
		showLoading();
		setReadOperateLogCallback();
		if (mKey.getLockId() < 0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getOperateLog(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()), mKey.getK1(), mKey.getLockName());
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		} else {
            mTTLockAPI.getOperationLog(LogType.ALL, mKey.getLockData(), mKey.getLockMac(), new GetOperationLogCallback() {
                @Override
                public void onGetLogSuccess(String s) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            PeachLogger.d(s);
                            uploadLockOperateLog(s);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    stopLoading();
                    toastFail();
                }
            });
		}

	}

	private void setReadOperateLogCallback() {
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_OPERATE_LOG);
			MyApplication.pplBleSession.setmILockGetOperateLog(new MHILockGetOperateLog() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestPasscodeList();
						}
					});
				}

				@Override
				public void onFail() {
					stopLoading();
					toastFail();
				}
			});
		}
	}

	/**
	 * 上传锁密码操作记录
	 */
	private void uploadLockOperateLog(String operateLog) {
		showLoading();
		LockRecordUploader.getInstance().uploadRecords(mKey.getLockId(), operateLog, (uploaded, saved) -> {
			stopLoading();
			if (isFinishing() || isDestroyed()) return;
			if (!uploaded) {
				toast(saved ? R.string.record_sync_queued : R.string.record_storage_failed);
				return;
			}
			mRefreshLayout.setRefreshing(true);
			requestPasscodeList();
		});
	}

	private void verifyAccountPwd(String pwd) {
		RestClient.builder()
				.url(Urls.ACCOUNT_PWD_VERIFY)
				.loader(this)
				.params("password", pwd)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_VERIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							boolean isPwdValid = result.getBoolean("data");
							if (isPwdValid) {
								mPwdWrongCount = 0;
								Resources res = getResources();
								DialogUtil.showCommonDialog(LockManagePasswordActivity.this, null,
										res.getString(R.string.note_passcode_reset_confirm), res.getString(R.string.clear),
										res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialogInterface, int i) {
												if (isBleNetEnableWithToast())
													resetPasscode();
											}
										}, null);
							} else {
								showPwdWrongDialog();
							}
						} else {
							showPwdWrongDialog();
						}
					}
				})
				.build()
				.post();
	}

	private void showPwdWrongDialog() {
//		if (++mPwdWrongCount >= 3) {
//			DialogUtil.showCommonDialog(LockManagePasswordActivity.this, null,
//					getString(R.string.note_pwd_wrong_times), getString(R.string.reset_pwd),
//					getString(R.string.cancel), new DialogInterface.OnClickListener() {
//						@Override
//						public void onClick(DialogInterface dialog, int which) {
//							goToNewActivity(ResetPwdActivity.class);
//						}
//					}, null);
//		} else {
		toast(R.string.note_pwd_invalid);
//		}
	}

	private void resetPasscode() {
		showLoading();
		setLockOperateCallback();
		if (mKey.getLockId() < 0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.resetKeyboardPwd(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()), mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		} else {
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
		if (mKey.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.RESET_KEYBOARD_PWD);
			MyApplication.pplBleSession.setmILockResetKeyboardPwd(new MHILockResetKeyboardPwd() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
						}
					});
				}

				@Override
				public void onFail() {
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

			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	/**
	 * 请求服务器，重置键盘密码
	 *
	 * @param pwdInfo
	 * @param timestamp
	 */
	private void requestResetPasscode(String pwdInfo, long timestamp) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_RESET)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mLockId)
				.params("pwdInfo", pwdInfo)
				.params("timestamp", timestamp)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_RESET", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mPasscodeList.clear();
							mLlNoData.setVisibility(View.VISIBLE);
							mIvSync.setVisibility(View.GONE);
							toast(R.string.note_passcode_reset_success);
						} else {
							toastBackendError(result, R.string.note_passcode_reset_fail);
						}
					}
				})
				.build()
				.post();
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		PasscodeDetailActivity.actionStart(LockManagePasswordActivity.this, mPasscodeList.get(i));
	}

	private void showActionDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_send_ekey_type);
			window.setGravity(Gravity.BOTTOM);
			window.setWindowAnimations(R.style.anim_panel_up_from_bottom);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			AppCompatButton createPasscode = window.findViewById(R.id.btn_dialog_send_ekey_period);
			window.findViewById(R.id.ll_dialog_send_ekey_permanent).setVisibility(View.GONE);
			AppCompatButton clearPasscodes = window.findViewById(R.id.btn_dialog_send_ekey_one_time);
			window.findViewById(R.id.btn_dialog_send_ekey_cancel).setOnClickListener(this);
			if (!mKey.isAdmin() && mKey.getKeyRight() == 1) {
				window.findViewById(R.id.layout_line).setVisibility(View.INVISIBLE);
				clearPasscodes.setVisibility(View.GONE);
				createPasscode.setBackgroundResource(R.drawable.border_round_all);
			}
			clearPasscodes.setText(R.string.clear_all_passwords);
			createPasscode.setText(R.string.create_password);
			clearPasscodes.setOnClickListener(this);
			createPasscode.setOnClickListener(this);
		}
	}

	/**
	 * 获取锁键盘密码列表数据
	 */
	private void requestPasscodeList() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_LIST)
//				.loader(LockManagePasswordActivity.this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mLockId)
				.params("pageNo", 1)
				.params("pageSize", 100)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
						PeachLogger.d("LOCK_PASSCODE_LIST", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray dataArray = result.getJSONArray("data");
							mPasscodeList.clear();
							if (dataArray != null && !dataArray.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = dataArray.size();
								for (int i = 0; i < size; i++) {
									JSONObject dataObj = dataArray.getJSONObject(i);
									Passcode passcode = new Passcode();
									passcode.setKeyboardPwdId(dataObj.getInteger("keyboardPwdId"));
									passcode.setKeyboardPwd(dataObj.getString("keyboardPwd"));
									String alias = dataObj.getString("alias");
									passcode.setAlias(StringUtil.isBlank(alias) ? "" : alias);
									passcode.setSendUser(dataObj.getString("sendUser"));
									passcode.setKeyboardPwdType(dataObj.getInteger("keyboardPwdType"));
									Long start = dataObj.getLong("startDate");
									if (start != null) {
										passcode.setStartDate(start);
									} else {
										passcode.setStartDate(0);
									}
									Long end = dataObj.getLong("endDate");
									if (end != null) {
										passcode.setEndDate(end);
									} else {
										passcode.setEndDate(0);
									}
									Long create = dataObj.getLong("createDate");
									if (create != null) {
										passcode.setCreateDate(create);
									} else {
										passcode.setCreateDate(0);
									}
									passcode.setStatus(dataObj.getInteger("status"));

									mPasscodeList.add(passcode);
								}
								mAdapter.notifyDataSetChanged();
								mIvSync.setVisibility(View.VISIBLE);
							} else {
								mLlNoData.setVisibility(View.VISIBLE);
								mIvSync.setVisibility(View.GONE);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.build()
				.post();
	}
}
