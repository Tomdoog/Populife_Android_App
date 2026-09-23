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
import com.populstay.populife.adapter.LockOperateRecordAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.LockOperateRecord;
import com.populstay.populife.enumtype.Operation;
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

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockOperateRecordActivity extends BaseActivity implements View.OnClickListener,
		ExpandableListView.OnChildClickListener {

	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_KEY = "KEY_KEY";

	private TextView mTvClear, mTvSearch, mIvSync;
	private LinearLayout mLlNoData;
	private ExpandableListView mExpandableListView;
	private LockOperateRecordAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;

	private Key mKey;
	private List<String> mGroupList = new ArrayList<>(); // 组元素数据列表（日期）
	private Map<String, List<LockOperateRecord>> mChildList = new LinkedHashMap<>(); // 子元素数据列表（操作记录）

	private int mLockId;

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁id
	 */
	public static void actionStart(Context context, int lockId, Key key) {
		Intent intent = new Intent(context, LockOperateRecordActivity.class);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_KEY, key);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_operate_record);
		if (!getIntentData()) return;
		initView();
		initListener();
		requestLockOperateRecords();
	}

	private void initListener() {
		mTvClear.setOnClickListener(this);
		mTvSearch.setOnClickListener(this);
		mIvSync.setOnClickListener(this);
		mExpandableListView.setOnChildClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.page_action_2) {
            DialogUtil.showCommonDialog(
                    LockOperateRecordActivity.this,
                    null,
                    getString(R.string.note_clear_records),
                    getString(R.string.clear),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            clearAllRecords();
                        }
                    },
                    null
            );
        } else if (id == R.id.page_action) {
            DialogUtil.showCommonDialog(
                    LockOperateRecordActivity.this,
                    getString(R.string.sync_operate_records),
                    getString(R.string.note_sync_operate_records),
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (isBleNetEnableWithToast())
                                readLockOperateLog();
                        }
                    },
                    null
            );
        } else if (id == R.id.tv_lock_operate_records_search) {
            LockOperateRecordSearchActivity.actionStart(LockOperateRecordActivity.this, mLockId);
        }
    }


    @Override
	public boolean onChildClick(ExpandableListView parent, View v, final int groupPosition, final int childPosition, long id) {
//		DialogUtil.showCommonDialog(LockOperateRecordActivity.this, null,
//				getString(R.string.note_delete_record),
//				getString(R.string.delete), getString(R.string.cancel), new DialogInterface.OnClickListener() {
//					@Override
//					public void onClick(DialogInterface dialogInterface, int i) {
//						deleteRecord(groupPosition, childPosition);
//					}
//				}, null);
		return true;
	}

	private boolean getIntentData() {
		// 查询、上传、搜索与清空统一使用当前钥匙的 lockId，不能混用旧页面 ID。
		mKey = getIntent().getParcelableExtra(KEY_KEY);
		if (mKey == null || mKey.getLockId() == 0) {
			toast(R.string.record_device_unavailable);
			finish();
			return false;
		}
		mLockId = mKey.getLockId();
		int requestedId = getIntent().getIntExtra(KEY_LOCK_ID, mLockId);
		if (requestedId != mLockId) {
			PeachLogger.w("RECORD_SYNC", "lockId mismatch: page=" + requestedId + " key=" + mLockId);
		}
		MyApplication.CURRENT_KEY = mKey;
		return true;
	}

	private void initView() {
		TextView tvTitle = findViewById(R.id.page_title);
		tvTitle.setText(R.string.lock_action_history);

		mTvClear = findViewById(R.id.page_action_2);
		mTvClear.setText(R.string.clear);
		setClearVisible(false);

		mTvSearch = findViewById(R.id.tv_lock_operate_records_search);
		mIvSync = findViewById(R.id.page_action);
		mIvSync.setText("");
		mIvSync.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.refresh_icon), null, null, null);
		setSyncVisible();

		mLlNoData = findViewById(R.id.layout_no_data);
		mExpandableListView = findViewById(R.id.eplv_lock_operate_records);
		mAdapter = new LockOperateRecordAdapter(LockOperateRecordActivity.this, mGroupList, mChildList);
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
						requestLockOperateRecords();
					}
				});
			}
		});
	}

	/**
	 * 权限处理：普通用户，不显示【同步】按钮
	 */
	private void setSyncVisible() {
		mIvSync.setVisibility(!mKey.isAdmin() && mKey.getKeyRight() != 1 ? View.GONE : View.VISIBLE);
	}

	/**
	 * 权限处理：非管理员不显示【清空】按钮
	 *
	 * @param hasData 列表里是否有数据，无数据则不显示【清空】按钮
	 */
	private void setClearVisible(boolean hasData) {
		if (hasData && mKey.isAdmin()) {
			mTvClear.setText(R.string.clear);
			mTvClear.setVisibility(View.VISIBLE);
		} else {
			mTvClear.setText("");
			mTvClear.setVisibility(View.GONE);
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
				sPPLOCK.getOperateLog(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),mKey.getK1(),mKey.getLockName());
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
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
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.GET_OPERATE_LOG);
			MyApplication.pplBleSession.setmILockGetOperateLog(new MHILockGetOperateLog() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestLockOperateRecords();
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
			requestLockOperateRecords();
		});
	}

	/**
	 * 获取锁的操作记录
	 */
	private void requestLockOperateRecords() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.LOCK_OPERATE_RECORDS_GET)
				.loader(LockOperateRecordActivity.this)
				.params("lockId", mLockId)
				.params("userId", PeachPreference.readUserId())
				.params("start", 0)
				.params("limit", 300)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("LOCK_OPERATE_RECORDS_GET", response);
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
								setClearVisible(true);
								int groupSize = dataArray.size();
								for (int i = 0; i < groupSize; i++) {
									JSONObject recordData = dataArray.getJSONObject(i);
									//循环并得到key列表
									for (String key : recordData.keySet()) {
										// 获得 key
										// TODO: 2019-07-04 移到下边的 if 判断里，防止 records 为空
										mGroupList.add(key);
										// 获得 key 对应的 value
										JSONArray records = recordData.getJSONArray(key);
										List<LockOperateRecord> recordList = new ArrayList<>();
										if (records != null && !records.isEmpty()) {
											int childSize = records.size();
											for (int j = 0; j < childSize; j++) {
												JSONObject recordItem = records.getJSONObject(j);
												LockOperateRecord record = new LockOperateRecord();
												record.setId(recordItem.getString("id"));

												int event = recordItem.getIntValue("event");
												String name = recordItem.getString("nickname");
												if (event == 3) { // 密码开锁
													if (StringUtil.isBlank(name) && recordItem.containsKey("password")) {
														name = recordItem.getString("password");
													}
												} else if (event == 7 || event == 8) { // 指纹/门卡开锁
													if (StringUtil.isBlank(name)) {
														name = getString(R.string.text_no_content_default);
													}
												}
												record.setNickname(name);
												record.setEvent(String.valueOf(event));

												record.setAvatar(recordItem.getString("avatar"));
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
								setClearVisible(false);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.record_load_failed);
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.record_load_failed);
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.build()
				.get();
	}

	/**
	 * 删除单条操作记录
	 *
	 * @param groupPosition item 所在组元素数据列表（日期）的 index
	 * @param childPosition 子元素数据列表（操作记录）中，item 的 index
	 */
	private void deleteRecord(final int groupPosition, final int childPosition) {
		final String key = mGroupList.get(groupPosition);
		List<LockOperateRecord> recordList = mChildList.get(key);
		LockOperateRecord record = recordList.get(childPosition);
		String recordId = record.getId();
		RestClient.builder()
				.url(Urls.LOCK_OPERATE_RECORDS_DELETE)
				.loader(LockOperateRecordActivity.this)
				.params("id", recordId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_OPERATE_RECORDS_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mChildList.get(key).remove(childPosition);
							if (mChildList.get(key).isEmpty()) {
								mGroupList.remove(groupPosition);
							}
							mAdapter.notifyDataSetChanged();
							if (mGroupList.isEmpty()) {
								setClearVisible(false);
								mLlNoData.setVisibility(View.VISIBLE);
							}
						} else {
							toast(R.string.note_delete_record_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_delete_record_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 清空所有操作记录
	 */
	private void clearAllRecords() {
		RestClient.builder()
				.url(Urls.LOCK_OPERATE_RECORDS_CLEAR)
				.loader(LockOperateRecordActivity.this)
				.params("lockId", mLockId)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_OPERATE_RECORDS_CLEAR", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mGroupList.clear();
							mChildList.clear();
							mAdapter.notifyDataSetChanged();
							setClearVisible(false);
							mLlNoData.setVisibility(View.VISIBLE);
						} else {
							toast(R.string.note_clear_records_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_clear_records_fail);
					}
				})
				.build()
				.post();
	}
}
