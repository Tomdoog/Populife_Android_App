package com.populstay.populife.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.WeakHashMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;

public class EkeyDetailActivity extends BaseActivity implements View.OnClickListener {

	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_KEY_RIGHT = "key_key_right";
	private static final String KEY_KEY_ALIAS = "key_key_alias";
	private static final String KEY_KEY_TYPE = "key_key_type";
	private static final String KEY_START_TIME = "key_start_time";
	private static final String KEY_END_TIME = "key_end_time";
	private static final String KEY_RECEIVER = "key_receiver";
	private static final String KEY_SENDER = "key_sender";
	private static final String KEY_SENDING_TIME = "key_sending_time";
	private static final String KEY_KEY_STATUS = "key_key_status";
	private static final String KEY_ITEM = "key_item";
	private static final int REQUEST_CODE_MODIFY_EKEY_PERIOD = 1;
	private static final int REQUEST_CODE_MODIFY_EKEY_PERMISSION = 2;
	private static final int REQUEST_CODE_MODIFY_EKEY_SHARE = 3;

	private AlertDialog DIALOG;
	private TextView mTvMenu, mTvName, mTvValidPeriod, mTvStartTime,tv_permission_types,
			mTvEndTime, mTvReceiver, mTvSender, mTvSendingTime, mTvDelete,tv_ekey_status,tv_ekey_share;
	private LinearLayout mLlName, mLlValidPeriod, mLlRecord,ll_permission_types;
	private ImageView mIvNameMore, mIvValidPeriodMore,iv_permission_types_more;
	private CheckBox mCbDeleteKeys;

	private String mName, mReceiver, mSender, mKeyStatus;
	private int mKeyId, mKeyRight, mKeyType;
	private long mStartTime, mEndTime, mSendingTime;
	private KeyPwd mKeyPwd;
	private Key mKey = MyApplication.CURRENT_KEY;//当前用户的钥匙
	private EditText mEtDialogInput;
	private int shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.ACCOUNT;//分享类型(1账号,2短信链接)

	private boolean isAuth(){
		return mKeyRight == KeyPwdConstant.IBTKeyPermissionType.AUTH;
	}

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param keyId   钥匙 id
	 */
	public static void actionStart(Context context, int keyId, int keyRight, String keyAlias, int keyType,
								   long startTime, long endTime, String receiver, String sender,
								   long sendingTime, String keyStatus, KeyPwd item) {
		Intent intent = new Intent(context, EkeyDetailActivity.class);
		intent.putExtra(KEY_KEY_ID, keyId);
		intent.putExtra(KEY_KEY_RIGHT, keyRight);
		intent.putExtra(KEY_KEY_ALIAS, keyAlias);
		intent.putExtra(KEY_KEY_TYPE, keyType);
		intent.putExtra(KEY_START_TIME, startTime);
		intent.putExtra(KEY_END_TIME, endTime);
		intent.putExtra(KEY_RECEIVER, receiver);
		intent.putExtra(KEY_SENDER, sender);
		intent.putExtra(KEY_SENDING_TIME, sendingTime);
		intent.putExtra(KEY_KEY_STATUS, keyStatus);
		intent.putExtra(KEY_ITEM, item);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ekey_detail);

		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mKeyId = data.getIntExtra(KEY_KEY_ID, 0);
		mKeyRight = data.getIntExtra(KEY_KEY_RIGHT, 0);
		mKeyType = data.getIntExtra(KEY_KEY_TYPE, KeyPwdConstant.IBTKeyType.TIME_LIMITED);
		mName = data.getStringExtra(KEY_KEY_ALIAS);
		mReceiver = data.getStringExtra(KEY_RECEIVER);
		mSender = data.getStringExtra(KEY_SENDER);
		mKeyStatus = data.getStringExtra(KEY_KEY_STATUS);
		mStartTime = data.getLongExtra(KEY_START_TIME, 0); // 秒
		mEndTime = data.getLongExtra(KEY_END_TIME, 0); // 秒
		mSendingTime = data.getLongExtra(KEY_SENDING_TIME, 0); // 毫秒
		mKeyPwd = data.getParcelableExtra(KEY_ITEM);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.ekey_detail);
		mTvMenu = findViewById(R.id.page_action);
		mTvMenu.setText("");
		mTvMenu.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.ic_menu_more), null, null, null);

		mTvName = findViewById(R.id.tv_ekey_detail_name);
		mTvValidPeriod = findViewById(R.id.tv_ekey_detail_valid_period);
		mTvStartTime = findViewById(R.id.tv_ekey_detail_start_time);
		tv_permission_types = findViewById(R.id.tv_permission_types);
		mTvEndTime = findViewById(R.id.tv_ekey_detail_end_time);
		mTvReceiver = findViewById(R.id.tv_ekey_detail_receiver);
		mTvSender = findViewById(R.id.tv_ekey_detail_sender);
		mTvSendingTime = findViewById(R.id.tv_ekey_detail_sending_time);
		tv_ekey_status = findViewById(R.id.tv_ekey_status);
		mTvDelete = findViewById(R.id.tv_ekey_detail_delete);
		tv_ekey_share = findViewById(R.id.tv_ekey_share);
		mLlName = findViewById(R.id.ll_ekey_detail_name);
		mLlValidPeriod = findViewById(R.id.ll_ekey_detail_valid_period);
		mLlRecord = findViewById(R.id.ll_ekey_detail_records);
		ll_permission_types = findViewById(R.id.ll_permission_types);
		mIvNameMore = findViewById(R.id.iv_ekey_detail_name_more);
		mIvValidPeriodMore = findViewById(R.id.iv_ekey_detail_valid_period_more);
		iv_permission_types_more = findViewById(R.id.iv_permission_types_more);

		refreshUI();
	}

	private void setKeyStatusUI(){
		mTvDelete.setVisibility(View.GONE);
		switch (mKeyStatus) {
			case "110400": // 还未到生效时间
			case "110501": // 还未到生效时间
			case "110402"://待接收
				tv_ekey_status.setText(R.string.key_pwd_status_not_activated);
				break;

			case "110401"://正常使用
				tv_ekey_status.setText(R.string.key_pwd_status_available);
				// 限制失效按钮
				mTvDelete.setVisibility(View.VISIBLE);
				mTvDelete.setText(R.string.invalidate_this_key);
				break;

			case "110405"://已冻结
				tv_ekey_status.setText(R.string.key_pwd_status_invalid);
				// 限制恢复按钮
				mTvDelete.setVisibility(View.VISIBLE);
				mTvDelete.setText(R.string.restore_this_key);
				break;
			case "110408"://已删除
			case "110410"://已重置
			case "110500"://已过期
				tv_ekey_status.setText(R.string.key_pwd_status_invalid);
		}
	}

	private void setKeyAuthUI(){
		if (isAuth()) {
			tv_permission_types.setText(R.string.authorized_user);
		}else {
			tv_permission_types.setText(R.string.general_user);
		}
	}


	private void refreshUI() {
		setShareStatusUI();
		setKeyStatusUI();
		setKeyAuthUI();

		mTvName.setText(mName);
		mTvReceiver.setText(mReceiver);
		mTvSender.setText(mSender);
		mTvSendingTime.setText(DateUtil.getDateToString(mSendingTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
		switch (mKeyType) {//钥匙类型（1限时，2永久，3单次，4循环）
			case 1:
				mLlName.setEnabled(true);
				mLlValidPeriod.setEnabled(true);
				mIvNameMore.setVisibility(View.VISIBLE);
				mIvValidPeriodMore.setVisibility(View.VISIBLE);
				iv_permission_types_more.setVisibility(View.VISIBLE);
				mTvValidPeriod.setVisibility(View.GONE);
				mTvStartTime.setVisibility(View.VISIBLE);
				mTvEndTime.setVisibility(View.VISIBLE);
				mTvStartTime.setText(DateUtil.getDateToStringConvert(mStartTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
				mTvEndTime.setText(DateUtil.getDateToStringConvert(mEndTime, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
				break;

			case 2:
				mLlName.setEnabled(true);
				mLlValidPeriod.setEnabled(true);
				mIvNameMore.setVisibility(View.VISIBLE);
				mIvValidPeriodMore.setVisibility(View.VISIBLE);
				iv_permission_types_more.setVisibility(View.VISIBLE);
				mTvValidPeriod.setVisibility(View.VISIBLE);
				mTvStartTime.setVisibility(View.GONE);
				mTvEndTime.setVisibility(View.GONE);
				mTvValidPeriod.setText(R.string.permanent);
				break;

			case 3:
				mLlName.setEnabled(false);
				mLlValidPeriod.setEnabled(false);
				mTvMenu.setVisibility(View.GONE);
				mIvNameMore.setVisibility(View.GONE);
				mIvValidPeriodMore.setVisibility(View.GONE);
				iv_permission_types_more.setVisibility(View.GONE);
				mTvStartTime.setVisibility(View.GONE);
				mTvEndTime.setVisibility(View.GONE);
				mTvValidPeriod.setText(R.string.one_time);
				break;
		}


	}

	private void setShareStatusUI() {

		// 分享链接不为空可以继续分享
		if (null != mKeyPwd && !TextUtils.isEmpty(mKeyPwd.getShareKeyUrl())){
			tv_ekey_share.setVisibility(View.VISIBLE);
		}else {
			tv_ekey_share.setVisibility(View.GONE);
		}
	}

	private void initListener() {
		mTvMenu.setOnClickListener(this);
		mLlName.setOnClickListener(this);
		mLlValidPeriod.setOnClickListener(this);
		mLlRecord.setOnClickListener(this);
		ll_permission_types.setOnClickListener(this);
		mTvDelete.setOnClickListener(this);
		tv_ekey_share.setOnClickListener(this);
	}
    @Override
    public void onClick(View view) {
        int id = view.getId();
        Intent intent;

        if (id == R.id.page_action) {
            showActionDialog();
        } else if (id == R.id.ll_ekey_detail_name) {
            showInputDialog();
        } else if (id == R.id.ll_ekey_detail_valid_period) { // 修改有效期
            intent = new Intent(this, EkeyPeriodModifyActivity.class);
            intent.putExtra(EkeyPeriodModifyActivity.KEY_KEY, mKey);
            intent.putExtra(EkeyPeriodModifyActivity.KEY_KEY_ID, mKeyId);
            intent.putExtra(EkeyPeriodModifyActivity.KEY_KEY_TYPE, mKeyType);
            if (mKeyType != KeyPwdConstant.IBTKeyType.PERMANENT) {
                intent.putExtra(EkeyPeriodModifyActivity.KEY_START_TIME, mStartTime); // 秒
                intent.putExtra(EkeyPeriodModifyActivity.KEY_END_TIME, mEndTime); // 秒
            }
            startActivityForResult(intent, REQUEST_CODE_MODIFY_EKEY_PERIOD);
        } else if (id == R.id.ll_ekey_detail_records) {
            EkeyRecordActivity.actionStart(EkeyDetailActivity.this, mKeyId, mName);
        } else if (id == R.id.ll_permission_types) {
            EkeyPermissionModifyActivity.actionStart(this, mKeyId, mKeyRight);
//            intent = new Intent(this, EkeyPermissionModifyActivity.class);
//            intent.putExtra(EkeyPermissionModifyActivity.KEY_KEY_ID, mKeyId);
//            intent.putExtra(EkeyPermissionModifyActivity.KEY_AUTH_TYPE, mKeyRight);
//            startActivityForResult(intent, REQUEST_CODE_MODIFY_EKEY_PERMISSION);
        } else if (id == R.id.tv_ekey_detail_delete) {
            if ("110405".equals(mKeyStatus)) { // 已冻结
                DialogUtil.showCommonDialog(EkeyDetailActivity.this, getString(R.string.confirm_to_restore),
                        getString(R.string.key_to_restore_dialog_content),
                        getString(R.string.confirm_restore), getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                unfreezeEkey();
                            }
                        }, null);
            } else {
                DialogUtil.showCommonDialog(EkeyDetailActivity.this, getString(R.string.confirm_to_invalidate),
                        getString(R.string.key_to_invalidate_dialog_content),
                        getString(R.string.confirm_invalidate), getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                freezeEkey();
                            }
                        }, null);
            }
        } else if (id == R.id.tv_ekey_share) {
            intent = new Intent(this, EkeyShareModifyActivity.class);
            intent.putExtra(EkeyShareModifyActivity.KEY_KEY_ID, mKeyId);
            intent.putExtra(EkeyShareModifyActivity.KEY_SHARE_TYPE, shareKeyThrough);
            intent.putExtra(EkeyShareModifyActivity.KEY_ITEM, mKeyPwd);
            startActivityForResult(intent, REQUEST_CODE_MODIFY_EKEY_SHARE);
        } else if (id == R.id.btn_dialog_send_ekey_period) { // 冻结、解冻
            if ("110405".equals(mKeyStatus)) { // 已冻结
                unfreezeEkey();
            } else {
                freezeEkey();
            }
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_send_ekey_one_time) { // 授权、反授权
            if (isAuth()) { // 已授权
                unauthEkey();
            } else {
                authEkey();
            }
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_send_ekey_cancel
                || id == R.id.btn_dialog_choose_cancel
                || id == R.id.btn_dialog_input_cancel) {
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_choose_ok) {
            String delType = mCbDeleteKeys.isChecked() ? "Y" : "N";
            deleteEkey(delType);
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_input_ok) {
            String ekeyAlias = mEtDialogInput.getText().toString();
            if (!StringUtil.isBlank(ekeyAlias)) {
                modifyEkeyAlias(ekeyAlias);
                DIALOG.cancel();
            } else {
                toast(R.string.enter_account_passwprd);
            }
        }
    }

	/**
	 * 修改钥匙别名
	 */
	private void modifyEkeyAlias(final String ekeyAlias) {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_MODIFY_ALIAS)
				.loader(this)
				.params("alias", ekeyAlias)
				.params("keyId", mKeyId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_MODIFY_ALIAS", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_modify_name_success);
							mName = ekeyAlias;
							refreshUI();
						} else {
							toast(R.string.note_modify_name_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_name_fail);
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
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_input_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_input_ok);
			try{
				TextView title = window.findViewById(R.id.tv_dialog_input_title);
				mEtDialogInput = window.findViewById(R.id.et_dialog_input_content);
				mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
				title.setText(R.string.modify_name);
				mEtDialogInput.setText(mName);
				mEtDialogInput.setSelection(mName.length());
				DialogUtil.autoPopSoftInput(window, mEtDialogInput);

			}catch (Exception e){
				e.printStackTrace();
			}


			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	private void showChooseDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_choose);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			TextView title = window.findViewById(R.id.tv_dialog_choose_title);
			mCbDeleteKeys = window.findViewById(R.id.cb_dialog_choose_content);
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_choose_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_choose_ok);
			ok.setText(R.string.delete);

			title.setText(R.string.note_delete_autu_key);
			mCbDeleteKeys.setText(R.string.note_delete_sent_key);

			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
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

			AppCompatButton clearKeys = window.findViewById(R.id.btn_dialog_send_ekey_period);
			window.findViewById(R.id.btn_dialog_send_ekey_permanent).setVisibility(View.GONE);
			AppCompatButton sendKey = window.findViewById(R.id.btn_dialog_send_ekey_one_time);
			window.findViewById(R.id.btn_dialog_send_ekey_cancel).setOnClickListener(this);
			if ("110405".equals(mKeyStatus)) {//已冻结
				clearKeys.setText(R.string.unfreeze);
			} else {
				clearKeys.setText(R.string.freeze);
			}
			if (isAuth()) {
				sendKey.setText(R.string.deauthorize);
			} else {
				sendKey.setText(R.string.authorize);
			}
			if (!mKey.isAdmin()) {
				clearKeys.setBackgroundResource(R.drawable.border_round_all);
				sendKey.setVisibility(View.GONE);
			}
			clearKeys.setOnClickListener(this);
			sendKey.setOnClickListener(this);
		}
	}

	/**
	 * 冻结用户钥匙
	 */
	private void freezeEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_FREEZE)
				.loader(EkeyDetailActivity.this)
				.params("keyId", mKeyId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_FREEZE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_freeze_success);
							mKeyStatus = "110405";
							setKeyStatusUI();
							EventBus.getDefault().post(new Event(Event.EventType.INVALIDATE_KEY));
							finish();
						} else if (code == 951) {
							toast(R.string.note_pending_ekey_cannot_freeze);
						} else {
							toast(R.string.ekey_freeze_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_freeze_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 解冻用户钥匙
	 */
	private void unfreezeEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_UN_FREEZE)
				.loader(EkeyDetailActivity.this)
				.params("keyId", mKeyId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_UN_FREEZE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_un_freeze_success);
							 mKeyStatus = "110401";
							setKeyStatusUI();
							EventBus.getDefault().post(new Event(Event.EventType.RESTORE_KEY));
							finish();
						} else {
							toast(R.string.ekey_un_freeze_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_un_freeze_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 授权用户钥匙
	 */
	private void authEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_AUTHORIZE)
				.loader(EkeyDetailActivity.this)
				.params("keyId", mKeyId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_AUTHORIZE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_authorize_success);
							finish();
						} else {
							toast(R.string.ekey_authorize_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_authorize_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 反授权用户钥匙
	 */
	private void unauthEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_UN_AUTHORIZE)
				.loader(EkeyDetailActivity.this)
				.params("keyId", mKeyId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_UN_AUTHORIZE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_un_authorize_success);
							finish();
						} else {
							toast(R.string.ekey_un_authorize_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_un_authorize_fail);
					}
				})
				.build()
				.post();
	}

	private WeakHashMap<String, Object> getRequestParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("keyId", mKeyId);

		// Y同时删除他所发送的钥匙，N则不。（注：只适用于授权用户，普通用户可传空）
		if (isAuth() || mKey.isAdmin()){
			params.put("delType", "Y");
		}

		return params;
	}

	/**
	 * 删除钥匙（钥匙详情页面）
	 */
	private void deleteEkey(String delType) {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_DELETE)
				.loader(EkeyDetailActivity.this)
				.params(getRequestParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_delete_success);
							finish();
						} else {
							toast(R.string.ekey_delete_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_delete_fail);
					}
				})
				.build()
				.post();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK) {
			return;
		}
		if (requestCode == REQUEST_CODE_MODIFY_EKEY_PERIOD) {

			mKeyType = data.getIntExtra(EkeyPeriodModifyActivity.KEY_KEY_TYPE, KeyPwdConstant.IBTKeyType.TIME_LIMITED);
			long startTime = data.getLongExtra(EkeyPeriodModifyActivity.KEY_START_TIME, mStartTime); // 秒
			long endTime = data.getLongExtra(EkeyPeriodModifyActivity.KEY_END_TIME, mEndTime); // 秒
			mStartTime = startTime; // 秒
			mEndTime = endTime; // 秒
			refreshUI();
		} else if (requestCode == REQUEST_CODE_MODIFY_EKEY_PERMISSION) {
			mKeyRight = data.getIntExtra(EkeyPermissionModifyActivity.KEY_AUTH_TYPE, 0);
			refreshUI();
		} else if (requestCode == REQUEST_CODE_MODIFY_EKEY_SHARE) {
			mKeyRight = data.getIntExtra(EkeyShareModifyActivity.KEY_SHARE_TYPE, 0);
			refreshUI();
		}
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type) {
			case Event.EventType.CREATE_BT_KEY_SUCCESS:
				finish();
				break;
		}
	}
}
