package com.populstay.populife.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.FeedbackListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.ContentInfo;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.ArrayList;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class FeedbackListActivity extends BaseActivity implements FeedbackListAdapter.IFeedbackListCallback {

	private static final int REQUEST_CODE_ADD_FEEDBACK = 1;

	private ImageView mTvNew;
	private LinearLayout mLlNoData;
	private ListView mListView;
	private FeedbackListAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;
	private List<ContentInfo> mFeedbackList = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listview_refresh);

		initView();
		initListener();
		requestFeedbackList();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.service_support_feedback);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvNew = findViewById(R.id.tv_new_feedback);
		mTvNew.setVisibility(View.VISIBLE);

		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.list_view);
		mAdapter = new FeedbackListAdapter(FeedbackListActivity.this, mFeedbackList, this);
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
						requestFeedbackList();
					}
				});
			}
		});
	}

	private void initListener() {
		mTvNew.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intentNickname = new Intent(FeedbackListActivity.this, SubmitNewFeedbackActivity.class);
				startActivityForResult(intentNickname, REQUEST_CODE_ADD_FEEDBACK);
			}
		});
	}

	/**
	 * 获取用户反馈信息数据
	 */
	private void requestFeedbackList() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.USER_FEEDBACK_LIST)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("USER_FEEDBACK_LIST", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray feedbackList = result.getJSONArray("data");
							mFeedbackList.clear();
							if (feedbackList != null && !feedbackList.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = feedbackList.size();
								for (int i = 0; i < size; i++) {
									JSONObject feedbackItem = feedbackList.getJSONObject(i);
									ContentInfo feedback = new ContentInfo();

									feedback.setId(feedbackItem.getString("id"));
									feedback.setUserId(feedbackItem.getString("userId"));
									feedback.setContent(feedbackItem.getString("content"));
									//todo 时区转换
									String createTime = DateUtil.getDateToString(
											feedbackItem.getLong("createDate"), "yyyy-MM-dd HH:mm:ss");
									feedback.setCreateTime(createTime);

									mFeedbackList.add(feedback);
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
				.get();
	}

	/**
	 * 删除单条反馈消息
	 *
	 * @param index 消息列表中，消息的 index
	 */
	private void deleteFeedback(final int index) {
		String messageId = mFeedbackList.get(index).getId();
		RestClient.builder()
				.url(Urls.USER_FEEDBACK_ITEM_DELETE)
				.loader(FeedbackListActivity.this)
				.params("id", messageId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("USER_FEEDBACK_ITEM_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mFeedbackList.remove(index);
							mAdapter.notifyDataSetChanged();
							if (mFeedbackList.isEmpty()) {
								mLlNoData.setVisibility(View.VISIBLE);
							}
						} else {
							toastBackendError(code, R.string.note_delete_feedback_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_delete_feedback_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_delete_feedback_fail);
					}
				})
				.build()
				.post();
	}

	@Override
	public void onDeleteClick(final int position) {
		DialogUtil.showCommonDialog(FeedbackListActivity.this, null,
				getString(R.string.note_delete_feedback),
				getString(R.string.delete), getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						deleteFeedback(position);
					}
				}, null);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CODE_ADD_FEEDBACK && resultCode == RESULT_OK) {
			requestFeedbackList();
		}
	}
}
