package com.populstay.populife.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.helper.LockRecordResult;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.adapter.PasscodeRecordAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.LockOperateRecord;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.lock.ILockGetOperateLog;
import com.populstay.populife.manhattanlock.MHILockGetOperateLog;
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
import com.ttlock.bl.sdk.constant.LogType;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;
import static com.populstay.populife.keypwdmanage.KeyPwdListFragment.TYPE_KEY_PWD_FP_CARD;

public class PasscodeRecordActivity extends BaseActivity implements View.OnClickListener {

	private static final String KEY_PASSCODE_ALIAS = "key_alias";
	private static final String KEY_PASSCPDE = "key_id";
	private static final String LOCK_ID = "lock_id";
	private static final String CARD_NUMBER = "CARD_NUMBER";
	private static final String FINGERPRINT_NUMBER = "FINGERPRINT_ID";

	private TextView mTvTitle, mIvSync;
	private LinearLayout mLlNoData;
	private ExpandableListView mExpandableListView;
	private PasscodeRecordAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;

	private List<String> mGroupList = new ArrayList<>(); // 组元素数据列表（日期）
	private Map<String, List<LockOperateRecord>> mChildList = new LinkedHashMap<>(); // 子元素数据列表（操作记录）
	private Key mKey = MyApplication.CURRENT_KEY;
	private String mPasscode, mCardNumber, mFingerprintNumber;
	private int lockId;
	private int mType;

	public static void actionStart(Context context, int type, String cardNumber, String fingerprintNumber, String passcode, int lockId) {
		Intent intent = new Intent(context, PasscodeRecordActivity.class);
		intent.putExtra(KEY_PASSCPDE, passcode);
		intent.putExtra(LOCK_ID, lockId);
		intent.putExtra(TYPE_KEY_PWD_FP_CARD, type);
		intent.putExtra(CARD_NUMBER, cardNumber);
		intent.putExtra(FINGERPRINT_NUMBER, fingerprintNumber);
		context.startActivity(intent);
	}

	public static void actionStart(Context context, String passcode, int lockId) {
		Intent intent = new Intent(context, PasscodeRecordActivity.class);
		intent.putExtra(KEY_PASSCPDE, passcode);
		intent.putExtra(LOCK_ID, lockId);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ekey_passcode_record);
		getIntentData();
		initView();
		requestPasscodeOperateRecords();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mPasscode = data.getStringExtra(KEY_PASSCPDE);
		lockId = data.getIntExtra(LOCK_ID, 0);
		mType = data.getIntExtra(TYPE_KEY_PWD_FP_CARD, KeyPwdConstant.IType.TYPE_PWD);
		mCardNumber = data.getStringExtra(CARD_NUMBER);
		mFingerprintNumber = data.getStringExtra(FINGERPRINT_NUMBER);
	}

	private void initView() {
		mTvTitle = findViewById(R.id.page_title);
		if (KeyPwdConstant.IType.TYPE_PWD == mType) {
			mTvTitle.setText(mPasscode);
		} else {
			mTvTitle.setText(R.string.records);
		}

		mIvSync = findViewById(R.id.page_action);
		mIvSync.setVisibility(View.VISIBLE);
		mIvSync.setText("");
		mIvSync.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.refresh_icon), null, null, null);
		mIvSync.setOnClickListener(this);

		mLlNoData = findViewById(R.id.layout_no_data);
		mExpandableListView = findViewById(R.id.eplv_eky_passcode_records);
		mAdapter = new PasscodeRecordAdapter(this, mType, mGroupList, mChildList);
		mExpandableListView.setAdapter(mAdapter);
		// 设置 expandableListview 默认可折叠
		mExpandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
			@Override
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				return false;
			}
		});

		mRefreshLayout = findViewById(R.id.refresh_layout);
		mRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
		mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				mRefreshLayout.post(new Runnable() {
					@Override
					public void run() {
						mRefreshLayout.setRefreshing(true);
						requestPasscodeOperateRecords();
					}
				});
			}
		});
	}

	/**
	 * 获取键盘密码的操作记录
	 */
	private void requestPasscodeOperateRecords() {

		//todo 数据分页
		RestClient.builder()
				.url(getRequestUrl())
				.loader(this)
				.params(getRequestParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_RECORD", response);
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						LockRecordResult queryResult = LockRecordResult.parse(response);
						if (!queryResult.isSuccess()) {
							PeachLogger.w("RECORD_QUERY", "query failed: code=" + queryResult.getCode());
							toast(R.string.record_load_failed);
							return;
						}
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray dataArray = result.getJSONArray("data");
							mGroupList.clear();
							mChildList.clear();
							if (dataArray != null && !dataArray.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int groupSize = dataArray.size();
								for (int i = 0; i < groupSize; i++) {
									JSONObject recordData = dataArray.getJSONObject(i);
									//循环并得到key列表
									for (String key : recordData.keySet()) {
										// 获得key
										mGroupList.add(key);
										//获得key值对应的value
										JSONArray records = recordData.getJSONArray(key);
										List<LockOperateRecord> recordList = new ArrayList<>();
										if (records != null && !records.isEmpty()) {
											int childSize = records.size();
											for (int j = 0; j < childSize; j++) {
												JSONObject recordItem = records.getJSONObject(j);
												LockOperateRecord record = new LockOperateRecord();
												record.setId(recordItem.getString("id"));
												String alias = recordItem.getString("alias");
												record.setNickname(StringUtil.isBlank(alias) ? getString(R.string.text_no_content_default) : alias);
												record.setContent(recordItem.getString("content"));
												long date = recordItem.getLong("createDate");
												record.setCreateDate(DateUtil.getDateToString(date, DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));
												recordList.add(record);
											}
											mChildList.put(key, recordList);
										}
									}
								}
								for (int k = 0; k < mGroupList.size(); k++) {
									mExpandableListView.expandGroup(k);
								}
								mAdapter.notifyDataSetChanged();
							} else {
								mLlNoData.setVisibility(View.VISIBLE);
							}
						}
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				toast(R.string.record_load_failed);
				if (mRefreshLayout != null) {
					mRefreshLayout.setRefreshing(false);
				}
			}
		}).error(new IError() {
			@Override
			public void onError(int code, String msg) {
				toast(R.string.record_load_failed);
				if (mRefreshLayout != null) {
					mRefreshLayout.setRefreshing(false);
				}
			}
		}).build().get();
	}

	private String getRequestUrl() {
		String url = "";
		switch (mType) {
			case KeyPwdConstant.IType.TYPE_PWD: // 数字密码
				url = Urls.LOCK_PASSCODE_RECORD;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 指纹
				url = Urls.FINGERPRINT_RECORD;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 门卡
				url = Urls.IC_CARD_RECORD;
				break;

			default:
				break;
		}
		return url;
	}

	private WeakHashMap<String, Object> getRequestParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("lockId", mKey.getLockId());
		params.put("start", 0);
		params.put("limit", 300);

		switch (mType) {
			case KeyPwdConstant.IType.TYPE_PWD: // 数字密码
				params.put("password", mPasscode);

				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 指纹
				params.put("id", mFingerprintNumber);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 门卡
				params.put("id", mCardNumber);
				break;

			default:
				break;
		}

		return params;
	}

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.page_action) {
            DialogUtil.showCommonDialog(PasscodeRecordActivity.this,
                    getString(R.string.sync_operate_records),
                    getString(R.string.note_sync_operate_records),
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 读取锁操作记录
                            if (isBleNetEnableWithToast()) {
                                readLockOperateLog();
                            }
                        }
                    },
                    null
            );
        }
    }
    /**
	 * 读取锁操作记录
	 */
	private void readLockOperateLog() {
		showLoading();
		setReadOperateLogCallback();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getOperateLog(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKey.getK1(),mKey.getLockName());
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.getOperationLog(LogType.ALL, mKey.getLockData(), mKey.getLockMac(), new GetOperationLogCallback() {
                @Override
                public void onGetLogSuccess(String operateLog) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            PeachLogger.d(operateLog);
                            uploadLockOperateLog(operateLog);
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
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.GET_OPERATE_LOG);
			MyApplication.pplBleSession.setmILockGetOperateLog(new MHILockGetOperateLog() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestPasscodeOperateRecords();
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
			requestPasscodeOperateRecords();
		});
	}


}
