package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

public class ModifyNicknameActivity extends BaseActivity {

	public static final String KEY_USER_NICKNAME = "key_user_nickname";

	private ExEditText mEtNickname;
	private TextView mTvSave;

	private String mNickname = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_nickname);
		getIntentData();
		initView();
		initListener();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.modify_nickname);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_sign_action_btn);
		mTvSave.setText(R.string.save);
		mEtNickname = findViewById(R.id.et_modify_nickname);
		if (!StringUtil.isBlank(mNickname)) {
			mEtNickname.setText(mNickname);
			mEtNickname.setSelection(mEtNickname.getText().toString().trim().length());
		}
		setEnableSaveBtn();
	}

	private void getIntentData() {
		mNickname = getIntent().getStringExtra(KEY_USER_NICKNAME);
	}

	private void initListener() {
		mTvSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String input = mEtNickname.getText().toString();
				if (!StringUtil.isBlank(input)) {
					if (!input.equals(mNickname))
						// 修改用户昵称
						saveNickname(input);
					else
						toast(R.string.note_nothing_changed);
				} else
					toast(R.string.enter_nickname);
			}
		});
		mEtNickname.addTextChangedListener(new TextWatcher() {
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

	private void setEnableSaveBtn(){
		if (null == mEtNickname){
			return;
		}
		mTvSave.setEnabled(!TextUtils.isEmpty(mEtNickname.getTextStr()));
	}

	/**
	 * 保存修改后的用户昵称
	 *
	 * @param nickName 修改以后的用户昵称
	 */
	private void saveNickname(final String nickName) {
		RestClient.builder()
				.url(Urls.NICKNAME_MODIFY)
				.loader(this)
				.params("nickname", nickName)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("CHANGE_NICKNAME", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							Intent intent = new Intent();
							intent.putExtra(KEY_USER_NICKNAME, nickName);
							setResult(RESULT_OK, intent);
							finish();
						} else {
							toastBackendError(code, R.string.note_modify_nickname_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_nickname_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_nickname_fail);
					}
				})
				.build()
				.post();
	}
}
