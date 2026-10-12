package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
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
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

public class SubmitNewFeedbackActivity extends BaseActivity {

	private EditText mEtInput, mEtFeedbackContactInfo;
	private TextView mTvWordsNum, mTvSubmit;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_submit_new_feedback);

		initView();
		initListener();
	}

	@SuppressLint("SetTextI18n")
	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.new_feedback);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mEtInput = findViewById(R.id.et_feedback_input);
		mEtFeedbackContactInfo = findViewById(R.id.et_feedback_contact_info);
		mTvWordsNum = findViewById(R.id.tv_feedback_submit_words_num);
		mTvWordsNum.setText(getString(R.string.num_zero) + getString(R.string.words_num_limit_200));
		mTvSubmit = findViewById(R.id.tv_feedback_submit);
	}

	private void initListener() {
		mEtInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@SuppressLint("SetTextI18n")
			@Override
			public void afterTextChanged(Editable editable) {
				mTvWordsNum.setText(String.valueOf(editable.length()) + getString(R.string.words_num_limit_200));
			}
		});

		mTvSubmit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String content = mEtInput.getText().toString();
				if (StringUtil.isBlank(content)) {
					toast(R.string.input_something);
				} else {
					submitFeedback();
				}
			}
		});
	}

	private void submitFeedback() {
		RestClient.builder()
				.url(Urls.USER_FEEDBACK_ADD)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("content", mEtInput.getText().toString())
				.params("contact", mEtFeedbackContactInfo.getText().toString())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("USER_FEEDBACK_ADD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							Intent intent = new Intent();
							setResult(RESULT_OK, intent);
							finish();
						} else {
							toastBackendError(code, R.string.note_submit_feedback_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_submit_feedback_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_submit_feedback_fail);
					}
				})
				.build()
				.post();
	}
}
