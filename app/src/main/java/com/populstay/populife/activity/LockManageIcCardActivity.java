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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.adapter.IcCardListAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.IcCard;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.lock.ILockIcCardClear;
import com.populstay.populife.lock.ILockIcCardDelete;
import com.populstay.populife.lock.ILockIcCardSearch;
import com.populstay.populife.manhattanlock.MHILockClearCards;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.ClearAllICCardCallback;
import com.ttlock.bl.sdk.callback.DeleteICCardCallback;
import com.ttlock.bl.sdk.callback.GetAllValidICCardCallback;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockManageIcCardActivity extends BaseActivity implements View.OnClickListener, AdapterView.OnItemLongClickListener {

	private AlertDialog DIALOG;
	private TextView mTvMenu;
	private LinearLayout mLlNoData;
	private SwipeRefreshLayout mRefreshLayout;
	private ListView mListView;
	private IcCardListAdapter mAdapter;
	private List<IcCard> mIcCardList = new ArrayList<>();

	private Key mKey = MyApplication.CURRENT_KEY;
	private int mSelectedItemIndex;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_manage_ic_card);

		initView();
		initListener();
	}

	@Override
	protected void onResume() {
		super.onResume();
		getIcCardData();
	}

	/**
	 * 获取 IC 卡数据
	 */
	private void getIcCardData() {
		if (isBleEnableWithToast()) {
			searchLockIcCards();
		}
		requestIcCardList();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.ic_card_management);
		mTvMenu = findViewById(R.id.page_action);
		mTvMenu.setText("");
		mTvMenu.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.ic_menu_more), null, null, null);

		mLlNoData = findViewById(R.id.layout_no_data);
		mListView = findViewById(R.id.lv_ic_card_list);
		mAdapter = new IcCardListAdapter(this, mIcCardList);
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
						getIcCardData();
					}
				});
			}
		});
	}

	private void initListener() {
		mTvMenu.setOnClickListener(this);
		mListView.setOnItemLongClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.page_action) {
            showActionDialog();
        } else if (id == R.id.btn_dialog_ic_card_manage_blutooth) {
            goToNewActivity(FingerprintIcCardAddConfigActivity.class);
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_ic_card_manage_keyboard_add) {
            IcCardKeyboardOperateActivity.actionStart(
                    LockManageIcCardActivity.this,
                    IcCardKeyboardOperateActivity.VAL_IC_CARD_ADD
            );
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_ic_card_manage_upload) {
            goToNewActivity(IcCardUploadActivity.class);
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_ic_card_manage_clear) {
            Resources res = getResources();
            DialogUtil.showCommonDialog(
                    LockManageIcCardActivity.this,
                    null,
                    res.getString(R.string.note_ic_card_delete),
                    res.getString(R.string.clear),
                    res.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            lockClearIcCards();
                        }
                    },
                    null
            );
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_ic_card_manage_keyboard_clear) {
            IcCardKeyboardOperateActivity.actionStart(
                    LockManageIcCardActivity.this,
                    IcCardKeyboardOperateActivity.VAL_IC_CARD_CLEAR
            );
            DIALOG.cancel();
        } else if (id == R.id.btn_dialog_ic_card_manage_cancel) {
            DIALOG.cancel();
        }
    }


    @Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int position, long l) {
		Resources res = getResources();
		DialogUtil.showCommonDialog(LockManageIcCardActivity.this, null,
				res.getString(R.string.note_confirm_delete), res.getString(R.string.ok),
				res.getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						mSelectedItemIndex = position;
						lockDeleteIcCard(Long.valueOf(mIcCardList.get(position).getCardNumber()));
					}
				}, null);
		return true;
	}

	/**
	 * 通过 SDK 读取锁的 IC 卡信息
	 */
	private void searchLockIcCards() {
        mTTLockAPI.getAllValidICCards(mKey.getLockData(), mKey.getLockMac(), new GetAllValidICCardCallback() {
            @Override
            public void onGetAllValidICCardSuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        requestUploadIcCard(s);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {

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
							BaseApplication.getHandler().postDelayed(new Runnable() {
								@Override
								public void run() {
									requestIcCardList();
								}
							}, 2000);
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 通过 SDK 删除某张 IC 卡
	 */
	private void lockDeleteIcCard(long cardNumber) {
		PeachLoader.showLoading(this);
        mTTLockAPI.deleteICCard(String.valueOf(cardNumber), mKey.getLockKey(), mKey.getLockMac(), new DeleteICCardCallback() {
            @Override
            public void onDeleteICCardSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PeachLoader.stopLoading();
                        requestDeleteIcCard(String.valueOf(cardNumber));
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
	 * 请求服务器，删除某张 IC 卡
	 */
	private void requestDeleteIcCard(String cardNumber) {
		RestClient.builder()
				.url(Urls.IC_CARD_DELETE)
				.params("lockId", mKey.getLockId())
				.params("cardNumber", cardNumber)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_DELETE", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.operation_success);
							mIcCardList.remove(mSelectedItemIndex);
							mAdapter.notifyDataSetChanged();
							if (mIcCardList.isEmpty()) {
								mLlNoData.setVisibility(View.VISIBLE);
							}
							requestIcCardList();
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 通过 SDK 清除锁中所有的 IC 卡信息
	 */
	private void lockClearIcCards() {
		PeachLoader.showLoading(this);
		if(mKey.getLockId()<0){
            setClearIcCardCallback();
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.clearFingers(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKey.getK1());
			} else {
				startLockActionScan();
			}
		}else {
            mTTLockAPI.clearAllICCard(mKey.getLockData(), mKey.getLockMac(), new ClearAllICCardCallback() {
                @Override
                public void onClearAllICCardSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PeachLoader.stopLoading();
                            requestClearIcCard();
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

	}

	private void setClearIcCardCallback() {
		if (mKey.getLockId()<0) {
			MyApplication.pplBleSession.setOperation(LockOperation.CLEAR_CARDS);
			MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
			MyApplication.pplBleSession.setmILockClearCards(new MHILockClearCards() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							PeachLoader.stopLoading();
							requestClearIcCard();
						}
					});
				}

				@Override
				public void onFail() {
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
	}

	/**
	 * 请求服务器，清空 IC 卡
	 */
	private void requestClearIcCard() {
		RestClient.builder()
				.url(Urls.IC_CARD_CLEAR)
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_CLEAR", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.operation_success);
							mIcCardList.clear();
							mAdapter.notifyDataSetChanged();
							mLlNoData.setVisibility(View.VISIBLE);
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}

	private void showActionDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_ic_card_manage);
			window.setGravity(Gravity.BOTTOM);
			window.setWindowAnimations(R.style.anim_panel_up_from_bottom);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			// 判断该用户是否为管理员
			if (!mKey.isAdmin()) {
				window.findViewById(R.id.btn_dialog_ic_card_manage_keyboard_add).setVisibility(View.GONE);
				window.findViewById(R.id.btn_dialog_ic_card_manage_keyboard_clear).setVisibility(View.GONE);
				window.findViewById(R.id.line_dialog_ic_card_manage_keyboard_add).setVisibility(View.GONE);
				window.findViewById(R.id.line_dialog_ic_card_manage_keyboard_clear).setVisibility(View.GONE);
			}
			window.findViewById(R.id.btn_dialog_ic_card_manage_blutooth).setOnClickListener(this);
			window.findViewById(R.id.btn_dialog_ic_card_manage_keyboard_add).setOnClickListener(this);
			window.findViewById(R.id.btn_dialog_ic_card_manage_keyboard_clear).setOnClickListener(this);
			window.findViewById(R.id.btn_dialog_ic_card_manage_upload).setOnClickListener(this);
			window.findViewById(R.id.btn_dialog_ic_card_manage_clear).setOnClickListener(this);

			window.findViewById(R.id.btn_dialog_ic_card_manage_cancel).setOnClickListener(this);
		}
	}

	/**
	 * 向服务器请求 IC 卡列表数据
	 */
	private void requestIcCardList() {
		//todo 数据分页
		RestClient.builder()
				.url(Urls.IC_CARD_LIST)
				.params("lockId", mKey.getLockId())
				.params("start", 0)
				.params("limit", 50)
				.params("keyword", "")
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}

						PeachLogger.d("IC_CARD_LIST", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONArray dataArray = result.getJSONArray("data");
							mIcCardList.clear();
							if (dataArray != null && !dataArray.isEmpty()) {
								mLlNoData.setVisibility(View.GONE);
								int size = dataArray.size();
								for (int i = 0; i < size; i++) {
									JSONObject dataObj = dataArray.getJSONObject(i);
									IcCard icCard = new IcCard();

									icCard.setLockId(dataObj.getInteger("lockId"));
									icCard.setCardNumber(dataObj.getString("cardNumber"));
									icCard.setRemark(dataObj.getString("remark"));
									Integer cardType = dataObj.getInteger("type");
									icCard.setType(cardType);
									if (Integer.valueOf(2).equals(cardType)) {//限时 IC 卡
										icCard.setStartDate(dataObj.getLong("startDate"));
										icCard.setEndDate(dataObj.getLong("endDate"));
									}
									icCard.setCardId(dataObj.getInteger("cardId"));
									icCard.setCreateDate(dataObj.getLong("createDate"));
									icCard.setExpire(dataObj.getString("expire"));

									mIcCardList.add(icCard);
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
}
