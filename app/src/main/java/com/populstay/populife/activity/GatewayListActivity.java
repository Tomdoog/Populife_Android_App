package com.populstay.populife.activity;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.GatewayListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Gateway;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.net.NetworkUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class GatewayListActivity extends BaseActivity implements AdapterView.OnItemClickListener,
		AdapterView.OnItemLongClickListener, View.OnClickListener {

	private TextView mTvAdd;
	private LinearLayout mLlNoData;
	private SwipeRefreshLayout mRefreshLayout;
	private ListView mListView;
	private GatewayListAdapter mAdapter;
	private List<Gateway> mGatewayList = new ArrayList<>();
	private AlertDialog DIALOG;
	private EditText mEtDialogInput;
	private int mSelectedItemIndex;//长按网关列表时，选中的 item 下标（重命名、删除）
	private DialogInterface.OnClickListener mGatewayDialogListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(final DialogInterface dialog, int which) {
			switch (which) {
				case 0:
					//重命名网关
					showInputDialog();
					break;

				case 1:
					//删除网关
					Resources res = getResources();
					DialogUtil.showCommonDialog(GatewayListActivity.this, null,
							res.getString(R.string.note_delete_gateway), res.getString(R.string.delete),
							res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									deleteGateway();
								}
							}, null);
					break;

				default:
					break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_listview_refresh);

		initView();
		initListener();
	}

	@Override
	protected void onResume() {
		super.onResume();
		requestGatewayList();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.gateway);
		mTvAdd = findViewById(R.id.page_action);
		mTvAdd.setText("");
		mTvAdd.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.ic_add), null, null, null);
		mTvAdd.setVisibility(View.INVISIBLE);

		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.list_view);
		mAdapter = new GatewayListAdapter(this, mGatewayList);
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
						requestGatewayList();
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

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.page_action) {
            if (NetworkUtil.isNetConnected()) {
                goToNewActivity(GatewayAddGuideActivity.class);
            } else {
                toast(R.string.note_add_gateway_wifi_connected);
            }
        } else if (id == R.id.btn_dialog_input_cancel) {
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_input_ok) {
            String input = mEtDialogInput.getText().toString();
            if (!StringUtil.isBlank(input)) {
                renameGateway(mGatewayList.get(mSelectedItemIndex).getGatewayId(), input);
                DIALOG.cancel();
            } else {
                toast(R.string.enter_gateway_name);
            }
        }
    }

    @Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		Gateway gateway = mGatewayList.get(i);
		GatewayBindedLockListActivity.actionStart(GatewayListActivity.this, gateway);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
		mSelectedItemIndex = position;
		Resources res = getResources();
		DialogUtil.showListDialog(GatewayListActivity.this, null,
				new String[]{res.getString(R.string.rename), res.getString(R.string.delete)}, mGatewayDialogListener);
		return true;
	}

	/**
	 * 获取网关列表
	 */
	private void requestGatewayList() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.GATEWAY_LIST)
				.params("userId", PeachPreference.readUserId())
				.params("pageNo", 1)
				.params("pageSize", 50)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
						PeachLogger.d("GATEWAY_LIST", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray feedbackList = result.getJSONArray("data");
							mGatewayList.clear();
							if (feedbackList != null && !feedbackList.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = feedbackList.size();
								for (int i = 0; i < size; i++) {
									JSONObject feedbackItem = feedbackList.getJSONObject(i);
									Gateway gateway = new Gateway();

									gateway.setGatewayId(feedbackItem.getInteger("gatewayId"));
									gateway.setGatewayMac(feedbackItem.getString("gatewayMac"));
									//网关地址（添加网关时 SDK 返回的地址，后期调用接口时的 gatewayMac 也使用这个值）
									gateway.setGatewayName(feedbackItem.getString("gatewayName"));
									gateway.setLockNum(feedbackItem.getInteger("lockNum"));
									gateway.setIsOnline(feedbackItem.getInteger("isOnline"));
									gateway.setName(feedbackItem.getString("name"));
									gateway.setAlias(feedbackItem.getString("alias"));//网关别名

									mGatewayList.add(gateway);
								}
								mAdapter.notifyDataSetChanged();
							} else {
								mLlNoData.setVisibility(View.VISIBLE);
							}
						} else {
							mLlNoData.setVisibility(View.VISIBLE);
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

			TextView title = window.findViewById(R.id.tv_dialog_input_title);
			mEtDialogInput = window.findViewById(R.id.et_dialog_input_content);
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_input_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_input_ok);
			title.setText(R.string.modify_name);
			mEtDialogInput.setHint(R.string.enter_gateway_name);
			mEtDialogInput.setText(mGatewayList.get(mSelectedItemIndex).getAlias());
			mEtDialogInput.setSelection(mGatewayList.get(mSelectedItemIndex).getAlias().length());
			DialogUtil.autoPopSoftInput(window, mEtDialogInput);
			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	/**
	 * 重命名网关
	 */
	private void renameGateway(int gatewayId, final String gatewayName) {
		RestClient.builder()
				.url(Urls.GATEWAY_MODIFY_NAME)
				.loader(this)
				.params("gatewayId", gatewayId)
				.params("name", gatewayName)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.operation_success);
							mGatewayList.get(mSelectedItemIndex).setAlias(gatewayName);
							mAdapter.notifyDataSetChanged();
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 根据网关id删除网关
	 */
	private void deleteGateway() {
		RestClient.builder()
				.url(Urls.GATEWAY_DELETE)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("gatewayId", mGatewayList.get(mSelectedItemIndex).getGatewayId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
						PeachLogger.d("GATEWAY_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mGatewayList.remove(mSelectedItemIndex);
							toast(R.string.operation_success);
							mAdapter.notifyDataSetChanged();
							if (mGatewayList.isEmpty()) {
								mLlNoData.setVisibility(View.VISIBLE);
							}
						} else if (code == 951) {
							toast(R.string.note_gateway_donot_exists);
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}
}
