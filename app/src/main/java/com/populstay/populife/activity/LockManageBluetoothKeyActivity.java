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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.BluetoothKeyListAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.BluetoothKey;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.lock.ILockResetEkey;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.ResetKeyCallback;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;

public class LockManageBluetoothKeyActivity extends BaseActivity implements View.OnClickListener,
		AdapterView.OnItemClickListener {

	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_IS_ADMIN = "key_is_admin";

	private AlertDialog DIALOG;
	private TextView mTvMenu;
	private LinearLayout mLlNoData;
	private ListView mListView;
	private BluetoothKeyListAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;
	private EditText mEtDialogInput;

	private Key mKey = MyApplication.CURRENT_KEY;
	private List<BluetoothKey> mBluetoothKeyList = new ArrayList<>();
	private int mLockId;
	private boolean mIsAdmin;
	private String mInputPwd;
	private int mActionType;//0 清空钥匙，1 重置钥匙
	private int mPwdWrongCount; // 输入账号密码错误次数

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁 id
	 * @param isAdmin 是否为管理员
	 */
	public static void actionStart(Context context, int lockId, boolean isAdmin) {
		Intent intent = new Intent(context, LockManageBluetoothKeyActivity.class);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_IS_ADMIN, isAdmin);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listview_refresh);

		getIntentData();
		initView();
		initListener();
	}

	@Override
	protected void onResume() {
		super.onResume();
		requestEkeyList();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mLockId = data.getIntExtra(KEY_LOCK_ID, 0);
		mIsAdmin = data.getBooleanExtra(KEY_IS_ADMIN, false);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.bluetooth_keys);
		mTvMenu = findViewById(R.id.page_action);
		mTvMenu.setText("");
		mTvMenu.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.ic_menu_more), null, null, null);

		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.list_view);
		mAdapter = new BluetoothKeyListAdapter(this, mBluetoothKeyList);
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
						requestEkeyList();
					}
				});
			}
		});
	}

	private void initListener() {
		mTvMenu.setOnClickListener(this);
		mListView.setOnItemClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.page_action) {
            showActionDialog();
        } else if (id == R.id.btn_dialog_send_ekey_one_time) { // 清空所有钥匙
            DIALOG.cancel();
            if (mBluetoothKeyList == null || mBluetoothKeyList.isEmpty()) {
                toast(R.string.note_no_ekey_in_list);
            } else {
                showInputDialog();
                mActionType = 0;
            }
        } else if (id == R.id.btn_dialog_send_ekey_permanent) { // 重置所有钥匙
            DIALOG.cancel();
            if (mBluetoothKeyList == null || mBluetoothKeyList.isEmpty()) {
                toast(R.string.note_no_ekey_in_list);
            } else {
                showInputDialog();
                mActionType = 1;
            }
        } else if (id == R.id.btn_dialog_send_ekey_period) { // 发送钥匙
            LockSendEkeyActivity.actionStart(LockManageBluetoothKeyActivity.this, mLockId, mIsAdmin);
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
								DialogUtil.showCommonDialog(LockManageBluetoothKeyActivity.this, null,
										res.getString(R.string.note_ekey_reset_clear_confirm), res.getString(R.string.clear),
										res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialogInterface, int i) {
												if (mActionType == 0) {//清空钥匙
													requestClearAllEkeys();
												} else if (mActionType == 1) {//重置钥匙
													lockResetAllEkeys();
												}
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
//			DialogUtil.showCommonDialog(LockManageBluetoothKeyActivity.this, null,
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

	private void requestClearAllEkeys() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_CLEAR)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mLockId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_CLEAR", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mBluetoothKeyList.clear();
							mAdapter.notifyDataSetChanged();
							mLlNoData.setVisibility(View.VISIBLE);
							toast(R.string.note_bluetooth_keys_clear_success);
						} else {
							toastBackendError(result, R.string.note_bluetooth_keys_clear_fail);
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

	private void lockResetAllEkeys() {
		PeachLoader.showLoading(this);
        mTTLockAPI.resetEkey(mKey.getLockData(), mKey.getLockMac(), new ResetKeyCallback() {
            @Override
            public void onResetKeySuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        requestResetEkey();
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        toast(R.string.note_ekey_reset_fail);
                    }
                });
            }
        });
	}

	/**
	 * 请求服务器，重置钥匙
	 */
	private void requestResetEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_RESET)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mLockId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_RESET", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mBluetoothKeyList.clear();
							mAdapter.notifyDataSetChanged();
							mLlNoData.setVisibility(View.VISIBLE);
							toast(R.string.note_ekey_reset_success);
						} else {
							toastBackendError(result, R.string.note_ekey_reset_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_ekey_reset_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_ekey_reset_fail);
					}
				})
				.build()
				.post();
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

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		BluetoothKey bluetoothKey = mBluetoothKeyList.get(i);
		EkeyDetailActivity.actionStart(LockManageBluetoothKeyActivity.this, bluetoothKey.getKeyId(),
				bluetoothKey.getKeyRight(), bluetoothKey.getAlias(), bluetoothKey.getType(), bluetoothKey.getStartDate(),
				bluetoothKey.getEndDate(), bluetoothKey.getRecUser(), bluetoothKey.getSendUser(), bluetoothKey.getSendDate(), bluetoothKey.getKeyStatus(),null);
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

			AppCompatButton sendKey = window.findViewById(R.id.btn_dialog_send_ekey_period);
			AppCompatButton resetKeys = window.findViewById(R.id.btn_dialog_send_ekey_permanent);
			AppCompatButton clearKeys = window.findViewById(R.id.btn_dialog_send_ekey_one_time);
			window.findViewById(R.id.btn_dialog_send_ekey_cancel).setOnClickListener(this);
			clearKeys.setText(R.string.clear_all_bluetooth_keys);
			resetKeys.setVisibility(View.GONE);
			sendKey.setText(R.string.send_bluetooth_key);
			clearKeys.setOnClickListener(this);
			sendKey.setOnClickListener(this);
		}
	}

	/**
	 * 获取用户钥匙列表数据
	 */
	private void requestEkeyList() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.LOCK_EKEY_LIST_MANAGEMENT)
//				.loader(LockManageBluetoothKeyActivity.this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mLockId)
				.params("pageNo", 1)
				.params("pageSize", 50)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("LOCK_EKEY_LIST_MANAGEMENT", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray dataArray = result.getJSONArray("data");
							mBluetoothKeyList.clear();
							if (dataArray != null && !dataArray.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = dataArray.size();
								for (int i = 0; i < size; i++) {
									JSONObject dataObj = dataArray.getJSONObject(i);
									BluetoothKey bluetoothKey = new BluetoothKey();

									bluetoothKey.setKeyId(dataObj.getInteger("keyId"));
									bluetoothKey.setAlias(dataObj.getString("alias"));
									Long start = dataObj.getLong("startDate");
									if (start != null) {
										bluetoothKey.setStartDate(start * 1000);
									} else {
										bluetoothKey.setStartDate(0);
									}
									Long end = dataObj.getLong("endDate");
									if (end != null) {
										bluetoothKey.setEndDate(end * 1000);
									} else {
										bluetoothKey.setEndDate(0);
									}
									bluetoothKey.setRecUser(dataObj.getString("recUser"));
									bluetoothKey.setSendUser(dataObj.getString("sendUser"));
									Long send = dataObj.getLong("sendDate");
									if (end != null) {
										bluetoothKey.setSendDate(send);
									} else {
										bluetoothKey.setSendDate(0);
									}
									bluetoothKey.setKeyStatus(dataObj.getString("keyStatus"));
									bluetoothKey.setType(dataObj.getInteger("type"));
									bluetoothKey.setAvatar(dataObj.getString("avatar"));
									bluetoothKey.setKeyRight(dataObj.getInteger("keyRight"));

									mBluetoothKeyList.add(bluetoothKey);
								}
								mAdapter.notifyDataSetChanged();
							} else {
								mLlNoData.setVisibility(View.VISIBLE);
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
