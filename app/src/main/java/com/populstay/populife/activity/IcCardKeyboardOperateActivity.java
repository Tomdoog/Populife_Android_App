package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import com.populstay.populife.lock.ILockIcCardSearch;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.GetAllValidICCardCallback;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;

public class IcCardKeyboardOperateActivity extends BaseActivity {

	public static final String VAL_IC_CARD_ADD = "val_ic_card_add";
	public static final String VAL_IC_CARD_CLEAR = "val_ic_card_clear";
	private static final String KEY_OPERATE_TYPE = "key_operate_type";

	private TextView mTvCommand, mTvPasscode, mTvNote, mTvSynchronize;

	private Key mKey = MyApplication.CURRENT_KEY;
	private String mOperateType;

	/**
	 * 启动当前 activity
	 */
	public static void actionStart(Context context, String operateType) {
		Intent intent = new Intent(context, IcCardKeyboardOperateActivity.class);
		intent.putExtra(KEY_OPERATE_TYPE, operateType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ic_card_keyboard_operate);

		mOperateType = getIntent().getStringExtra(KEY_OPERATE_TYPE);

		initView();
		initListener();
	}

	private void initView() {
		TextView pageTitle = findViewById(R.id.page_title);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mTvCommand = findViewById(R.id.tv_ic_card_keyboard_operate_command);
		mTvPasscode = findViewById(R.id.tv_ic_card_keyboard_operate_passcode);
		mTvNote = findViewById(R.id.tv_ic_card_keyboard_operate_note);
		mTvSynchronize = findViewById(R.id.tv_ic_card_keyboard_operate_synchronize);

		mTvPasscode.setText(mKey.getNoKeyPwd());
		if (VAL_IC_CARD_ADD.equals(mOperateType)) {
			pageTitle.setText(R.string.ic_card_add);
			mTvCommand.setText(R.string.ic_card_keyboard_command_add);
			mTvNote.setText(R.string.note_ic_card_keyboard_add);
			mTvNote.setTextColor(getResources().getColor(R.color.white));
		} else if (VAL_IC_CARD_CLEAR.equals(mOperateType)) {
			pageTitle.setText(R.string.ic_card_clear);
			mTvCommand.setText(R.string.ic_card_keyboard_command_clear);
			mTvNote.setText(R.string.note_ic_card_keyboard_clear);
			mTvNote.setTextColor(getResources().getColor(R.color.battery_low_red));
		}
	}

	private void initListener() {
		mTvSynchronize.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//读取并上传锁中的 IC 卡信息
				searchLockIcCards();
			}
		});
	}

	/**
	 * 读取锁的 IC 卡信息
	 */
	private void searchLockIcCards() {
		PeachLoader.showLoading(this);
        mTTLockAPI.getAllValidICCards(mKey.getLockData(), mKey.getLockMac(), new GetAllValidICCardCallback() {
            @Override
            public void onGetAllValidICCardSuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        requestUploadIcCard(s);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        toast(R.string.operation_fail);
                    }
                });
            }
        });
	}

	/**
	 * 请求服务器，上传 IC 卡
	 */
	private void requestUploadIcCard(String icCardInfo) {
		RestClient.builder()
				.url(Urls.IC_CARD_UPLOAD)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("records", icCardInfo)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_UPLOAD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.operation_success);
						} else {
							toastBackendError(result, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}
}
