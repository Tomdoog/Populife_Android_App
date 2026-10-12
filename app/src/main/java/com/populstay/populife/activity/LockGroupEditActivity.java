package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import androidx.appcompat.widget.AppCompatEditText;

public class LockGroupEditActivity extends BaseActivity {

	public static final String KEY_GROUP_ACTION_TYPE = "key_group_action_type";
	public static final String VAL_TYPE_ADD = "key_type_add";
	public static final String VAL_TYPE_MODIFY = "key_type_modify";
	public static final String KEY_GROUP_ID = "key_group_id";
	public static final String KEY_GROUP_NAME = "key_group_name";

	private TextView mTvSave;
	private AppCompatEditText mEtInput;

	private String mActionType = VAL_TYPE_ADD;
	private String mGroupId, mGroupName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_group_edit);

		getIntentData();
		initView();
		initListener();
	}

	private void initListener() {
		mTvSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				switch (mActionType) {
					case VAL_TYPE_ADD:
						addGroup();
						break;

					case VAL_TYPE_MODIFY:
						String input = mEtInput.getText().toString();
						if (!StringUtil.isBlank(input)) {
							if (!input.equals(mGroupName)) {
								// 修改分组
								modifyGroup();
							} else {
								toast(R.string.note_nothing_changed);
							}
						} else {
							toast(R.string.enter_group_name);
						}
						break;

					default:
						break;
				}
			}
		});
	}

	private void getIntentData() {
		mActionType = getIntent().getStringExtra(KEY_GROUP_ACTION_TYPE);
		if (mActionType.equals(VAL_TYPE_MODIFY)) {
			mGroupId = getIntent().getStringExtra(KEY_GROUP_ID);
			mGroupName = getIntent().getStringExtra(KEY_GROUP_NAME);
		}
	}

	private void initView() {
		TextView title = findViewById(R.id.page_title);
		if (mActionType.equals(VAL_TYPE_ADD)) {
			title.setText(R.string.lock_group_add);
		} else if (mActionType.equals(VAL_TYPE_MODIFY)) {
			title.setText(R.string.lock_group_modify);
		}

		mTvSave = findViewById(R.id.page_action);
		mTvSave.setText(R.string.save);

		mEtInput = findViewById(R.id.et_edit_lock_gourp_name);
		if (!StringUtil.isBlank(mGroupName)) {
			mEtInput.setText(mGroupName);
			mEtInput.setSelection(mGroupName.length());
		}
	}

	private void addGroup() {
		RestClient.builder()
				.url(Urls.LOCK_GROUP_ADD)
				.loader(LockGroupEditActivity.this)
				.params("userId", PeachPreference.readUserId())
				.params("name", mEtInput.getText().toString())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_GROUP_ADD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							setResult(RESULT_OK, new Intent());
							finish();
						} else if (code == 910) {
							toast(R.string.note_name_already_exists);
						} else {
							toastBackendError(result, R.string.note_add_group_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_add_group_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_add_group_fail);
					}
				})
				.build()
				.post();
	}

	private void modifyGroup() {
		RestClient.builder()
				.url(Urls.LOCK_GROUP_MODIFY)
				.loader(LockGroupEditActivity.this)
				.params("id", mGroupId)
				.params("userId", PeachPreference.readUserId())
				.params("name", mEtInput.getText().toString())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_GROUP_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							setResult(RESULT_OK, new Intent());
							finish();
						} else if (code == 910) {
							toast(R.string.note_name_already_exists);
						} else {
							toastBackendError(result, R.string.note_modify_group_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_group_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_group_fail);
					}
				})
				.build()
				.post();
	}
}
