package com.populstay.populife.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.LockGroupListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.LockGroup;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class LockGroupListActivity extends BaseActivity implements View.OnClickListener,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

	private static final int REQUEST_CODE_GROUP_ADD = 1;
	private static final int REQUEST_CODE_GROUP_MODIFY = 2;

	private LinearLayout mLlNoData;
	private TextView mTvAdd;
	private ListView mListView;
	private LockGroupListAdapter mAdapter;
	private SwipeRefreshLayout mRefreshLayout;

	private List<LockGroup> mGroupList = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listview_refresh);

		initView();
		initListener();
		requestLockGroup();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.lock_group);
		mTvAdd = findViewById(R.id.page_action);
		mTvAdd.setText("");
		mTvAdd.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.ic_add), null, null, null);

		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.list_view);
		mAdapter = new LockGroupListAdapter(LockGroupListActivity.this, mGroupList);
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
						requestLockGroup();
					}
				});
			}
		});
	}

	private void initListener() {
		mTvAdd.setOnClickListener(this);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
	}

	private void requestLockGroup() {
		RestClient.builder()
				.url(Urls.LOCK_GROUP_LIST)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("LOCK_GROUP_LIST", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray list = result.getJSONArray("data");
							mGroupList.clear();
							if (list != null && !list.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = list.size();
								for (int i = 0; i < size; i++) {
									JSONObject groupItem = list.getJSONObject(i);
									LockGroup lockGroup = new LockGroup();

									String id = groupItem.getString("id");
									if (!StringUtil.isBlank(id)) {
										lockGroup.setId(id);
										lockGroup.setName(groupItem.getString("name"));
										lockGroup.setCreateTime(groupItem.getLong("createDate"));
									} else {
										lockGroup.setName(getResources().getString(R.string.other_lowercase));
									}
									//lockGroup.setLockCount(groupItem.getInteger("lockCount"));
									mGroupList.add(lockGroup);
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

	@Override
	public void onClick(View view) {
		Intent intent = new Intent(LockGroupListActivity.this, LockGroupEditActivity.class);
		intent.putExtra(LockGroupEditActivity.KEY_GROUP_ACTION_TYPE, LockGroupEditActivity.VAL_TYPE_ADD);
		startActivityForResult(intent, REQUEST_CODE_GROUP_ADD);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
		LockGroup group = mGroupList.get(position);
		Intent intent = new Intent(LockGroupListActivity.this, LockGroupEditActivity.class);
		intent.putExtra(LockGroupEditActivity.KEY_GROUP_ACTION_TYPE, LockGroupEditActivity.VAL_TYPE_MODIFY);
		intent.putExtra(LockGroupEditActivity.KEY_GROUP_ID, group.getId());
		intent.putExtra(LockGroupEditActivity.KEY_GROUP_NAME, group.getName());
		startActivityForResult(intent, REQUEST_CODE_GROUP_MODIFY);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int position, long l) {
		DialogUtil.showCommonDialog(LockGroupListActivity.this, null,
				getString(R.string.note_delete_group),
				getString(R.string.ok), getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						deleteGroup(position);
					}
				}, null);
		return true;
	}

	private void deleteGroup(final int position) {
		RestClient.builder()
				.url(Urls.LOCK_GROUP_DELETE)
				.loader(LockGroupListActivity.this)
				.params("id", mGroupList.get(position).getId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_GROUP_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mGroupList.remove(position);
							mAdapter.notifyDataSetChanged();
						} else {
							toastBackendError(code, R.string.note_delete_group_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_delete_group_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_delete_group_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_delete_group_fail);
					}
				})
				.build()
				.post();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			requestLockGroup();
		}
	}
}
