package com.populstay.populife.activity;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.MessageListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.ContentInfo;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageListActivity extends BaseActivity implements View.OnClickListener,
		AdapterView.OnItemClickListener,ExpandableListView.OnChildClickListener{

	private LinearLayout mLlNoData;
	private TextView mTvClear;
	private SwipeRefreshLayout mRefreshLayout;


	private MessageListAdapter mAdapter;
	private ExpandableListView mExpandableListView;
	private List<String> mGroupList = new ArrayList<>(); // 组元素数据列表（日期）
	private Map<String, List<ContentInfo>> mChildList = new LinkedHashMap<>(); // 子元素数据列表（操作记录）

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listview_msg);

		initView();
		initListener();
		requestMessageList();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.me_list_item_name_auditor);
		mTvClear = findViewById(R.id.page_action);
		mTvClear.setText(R.string.clear);
		mTvClear.setVisibility(View.GONE);

		mLlNoData = findViewById(R.id.layout_no_data);
		mExpandableListView = findViewById(R.id.list_view);
		mAdapter = new MessageListAdapter(this, mGroupList, mChildList);
		mExpandableListView.setAdapter(mAdapter);
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
						requestMessageList();
					}
				});
			}
		});
	}

	private void initListener() {
		mTvClear.setOnClickListener(this);
		mExpandableListView.setOnChildClickListener(this);
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, final int groupPosition, final int childPosition, long id) {
		DialogUtil.showCommonDialog(this, null,
				getString(R.string.note_delete_record),
				getString(R.string.delete), getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						deleteMessage(groupPosition, childPosition);
					}
				}, null);
		return true;
	}

	@Override
	public void onClick(View view) {
		DialogUtil.showCommonDialog(MessageListActivity.this, null,
				getString(R.string.note_clear_notifications),
				getString(R.string.clear), getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						clearAllMessages();
					}
				}, null);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
//		MessageDetailActivity.actionStart(MessageListActivity.this, mMessageList.get(i).getId());
	}


	/**
	 * 获取用户消息列表数据
	 */
	private void requestMessageList() {
		RestClient.builder()
				.url(Urls.USER_MESSAGE_LIST)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("start", 0)
				.params("limit", 1000)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("LOCK_OPERATE_RECORDS_GET", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray dataArray = result.getJSONArray("data");
							mGroupList.clear();
							mChildList.clear();

							if (dataArray != null && !dataArray.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								mTvClear.setVisibility(View.VISIBLE);
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
										List<ContentInfo> recordList = new ArrayList<>();
										if (records != null && !records.isEmpty()) {
											int childSize = records.size();


											for (int j = 0; j < childSize; j++) {
												JSONObject messageItem = records.getJSONObject(j);
												ContentInfo message = new ContentInfo();

												message.setId(messageItem.getString("id"));
												message.setUserId(messageItem.getString("userId"));
												message.setTitle(messageItem.getString("title"));
												message.setContent(messageItem.getString("content"));
												/*String createTime = DateUtil.getDateToString(
														messageItem.getLong("createDate"), "yyyy-MM-dd HH:mm:ss");
												message.setCreateTime(createTime);*/
												message.setHasRead("Y".equals(messageItem.getString("hasRead")));
												recordList.add(message);
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
								mTvClear.setVisibility(View.GONE);
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
				.get();
	}


	/**
	 * 清空用户所有消息
	 */
	private void clearAllMessages() {
		RestClient.builder()
				.url(Urls.USER_MESSAGE_CLEAR_ALL)
				.loader(MessageListActivity.this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("USER_MESSAGE_CLEAR_ALL", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_clear_notifications_success);
							mGroupList.clear();
							mChildList.clear();
							mAdapter.notifyDataSetChanged();
							mTvClear.setVisibility(View.GONE);
							mLlNoData.setVisibility(View.VISIBLE);
						} else {
							toastBackendError(code, R.string.note_clear_notifications_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_clear_notifications_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_clear_notifications_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 删除单条消息
	 * @param groupPosition
	 * @param childPosition
	 */
	private void deleteMessage(final int groupPosition, final int childPosition) {
		final String key = mGroupList.get(groupPosition);
		List<ContentInfo> recordList = mChildList.get(key);
		ContentInfo record = recordList.get(childPosition);
		String recordId = record.getId();
		RestClient.builder()
				.url(Urls.USER_MESSAGE_ITEM_DELETE)
				.loader(this)
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
								mLlNoData.setVisibility(View.VISIBLE);
							}
						} else {
							toastBackendError(code, R.string.note_delete_notification_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_delete_notification_fail);
					}
				})
				.build()
				.post();
	}

}
