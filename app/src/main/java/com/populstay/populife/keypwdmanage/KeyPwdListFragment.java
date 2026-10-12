package com.populstay.populife.keypwdmanage;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.reflect.TypeToken;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.activity.EkeyDetailActivity;
import com.populstay.populife.activity.EkeyRecordActivity;
import com.populstay.populife.activity.EkeyShareModifyActivity;
import com.populstay.populife.activity.LockAddGuideActivity;
import com.populstay.populife.activity.LockSettingsActivity;
import com.populstay.populife.activity.ModifyAdminPasscodeActivity;
import com.populstay.populife.activity.PasscodeDetailActivity;
import com.populstay.populife.activity.PasscodeRecordActivity;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseFragment;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.adapter.KeyPwdListAdapter;
import com.populstay.populife.keypwdmanage.entity.CreatePwdKeyActionInfo;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockDeletePasscode;
import com.populstay.populife.lock.ILockFingerprintDelete;
import com.populstay.populife.lock.ILockIcCardDelete;
import com.populstay.populife.manhattanlock.MHILockDeleteCard;
import com.populstay.populife.manhattanlock.MHILockDeleteFingerprint;
import com.populstay.populife.manhattanlock.MHILockDeletePasscode;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.ui.recycler.CommonRecyclerView;
import com.populstay.populife.ui.recycler.SpacesItemDecoration;
import com.populstay.populife.ui.widget.HelpPopupWindow;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.DeleteFingerprintCallback;
import com.ttlock.bl.sdk.callback.DeleteICCardCallback;
import com.ttlock.bl.sdk.callback.DeletePasscodeCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
//import cn.sharesdk.framework.Platform;
//import cn.sharesdk.framework.PlatformActionListener;
//import cn.sharesdk.onekeyshare.OnekeyShare;

import static android.app.Activity.RESULT_OK;
import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;


public class KeyPwdListFragment extends BaseFragment {
	public static final String CATEGORY_KEY = "CATEGORY_KEY";
	public static final String TYPE_KEY_PWD_FP_CARD = "TYPE_KEY_PWD_FP_CARD";
	public static final String DATA_KEY = "DATA_KEY";
	public static final int REQUEST_CODE_PASSCODE = 001;
	private static final int REQUEST_CODE_MODIFY_EKEY_SHARE = 002;
	private View mRootView;
	private TextView mTvDesc, mTvEmptyHint, tv_create_btn;
	private ImageView iv_empty_hint;
	private LinearLayout ll_empty_hint;
	private SwipeRefreshLayout mSwipeRefreshLayout;
	private CommonRecyclerView mRvKeyPwdList;
	private View mFooterView;
	private KeyPwdListAdapter mKeyPwdListAdapter;
	private ImageView iv_add_more_btn;
	private HelpPopupWindow mHelpPopupWindow;
	private int mCurrentCategory;
	private Key mKey = CURRENT_KEY;
	private String mLockType;
	private int mAccessType = KeyPwdConstant.IType.TYPE_KEY; // 1：钥匙，2：密码，3：指纹，4：门卡
	private int mCurrentPageNo = 1;
	private KeyPwd mAdminPwd, mSelectedPwdFpCardToDelete;
	private boolean mIsLockOperationSuccess;
	private boolean mIsLoadingData; // 正在加载数据（处理上拉加载/下拉刷新 数据重复冲突）

	public static KeyPwdListFragment newInstance(int category, Key key, int type) {
		Bundle args = new Bundle();
		args.putInt(CATEGORY_KEY, category);
		args.putInt(TYPE_KEY_PWD_FP_CARD, type);
		args.putParcelable(DATA_KEY, key);
		KeyPwdListFragment fragment = new KeyPwdListFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		mRootView = inflater.inflate(R.layout.fragment_key_pwd_list, null);
		getArgumentsData();
		initView();
		initListAdapter();
		initDescAndEmptyHint();
		initAdminPwd();
		refreshData();
		return mRootView;
	}

	private void getArgumentsData() {
		Bundle args = getArguments();
		mCurrentCategory = args.getInt(CATEGORY_KEY, KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_NOT_ACTIVATED);
		mKey = args.getParcelable(DATA_KEY);
		mLockType = mKey.getLockName();
		mAccessType = args.getInt(TYPE_KEY_PWD_FP_CARD, KeyPwdConstant.IType.TYPE_KEY);
	}

	private void initAdminPwd() {
		createAdminPwd();
		setData(new ArrayList<KeyPwd>(), isRefreshData());
	}

	private boolean isShowAdminPwd() {
		// 非管理员不显示
		if (!mKey.isAdmin()) {
			return false;
		}

		// 密码列表才需要显示
		if (KeyPwdConstant.IType.TYPE_PWD != mAccessType) {
			return false;
		}

		// 只在可用列表显示
		if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE != mCurrentCategory) {
			return false;
		}

		return PeachPreference.isShowLockAdminCode(mKey.getLockId());
	}

	private void createAdminPwd() {
		mAdminPwd = new KeyPwd();
		mAdminPwd.setKeyboardPwd(mKey.getNoKeyPwd());
		mAdminPwd.setKeyType(2);
		mAdminPwd.setKeyboardPwdType(-1);
		mAdminPwd.setAlias(PeachPreference.getAdminCodeName(mKey.getLockId()));
	}

	private void initListAdapter() {
		mKeyPwdListAdapter = new KeyPwdListAdapter(mActivity, mCurrentCategory, new ArrayList<KeyPwd>(), mAccessType);
		mKeyPwdListAdapter.enableFooterView();
		mFooterView = LayoutInflater.from(mActivity).inflate(R.layout.footer_layout, mRvKeyPwdList, false);
		mKeyPwdListAdapter.addFooterView(mFooterView);
		mRvKeyPwdList.setAdapter(mKeyPwdListAdapter);

		mKeyPwdListAdapter.setmDeleteBtnClickListener(new KeyPwdListAdapter.ListViewActionBtnClickListener() {

			@Override
			public void onClick(View view, final KeyPwd item) {
				mSelectedPwdFpCardToDelete = item;
				if (item.isBTKey()) { // 蓝牙钥匙
					DialogUtil.showCommonDialog(mActivity, null,
							mActivity.getResources().getString(R.string.note_delete_ekey), mActivity.getResources().getString(R.string.delete),
							mActivity.getResources().getString(R.string.cancel),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									deleteEkey(item);
								}
							}, null);
				} else {
					DialogUtil.showCommonDialog(mActivity, null,
							getDialogDeleteHint(), getString(R.string.note_pwd_delete_confirm_ok_btn),
							getString(R.string.cancel), new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									if (isNetEnableWithToast()) {
										if (mAccessType == KeyPwdConstant.IType.TYPE_PWD) { // 密码
											// 和锁通信，删除密码
											lockDeletePasscode(item);
										} else if (mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) { // 门卡
											if (isBleEnableWithoutToast()) {
												// 和锁通信
												lockDeleteIcCard(item.getCardNumber());
											} else {
												if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
													requestDeletePwdFpCard(2);
												} else {
													toast(R.string.enable_bluetooth);
												}
											}
										} else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT) { // 指纹
											if (isBleEnableWithoutToast()) {
												// 和锁通信
												lockDeleteFingerprint(Long.parseLong(item.getFingerprintNumber()),item.getFingerprintId());
											} else {
												if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
													requestDeletePwdFpCard(2);
												} else {
													toast(R.string.enable_bluetooth);
												}
											}
										}
									}
								}
							}, null);
				}
			}
		});

		mKeyPwdListAdapter.setmRecordsBtnClickListener(new KeyPwdListAdapter.ListViewActionBtnClickListener() {
			@Override
			public void onClick(View view, final KeyPwd item) {
				if (item.isBTKey()) { // 蓝牙钥匙
					EkeyRecordActivity.actionStart(mActivity, item.getId(), mKey.getLockAlias());
				} else {
					switch (mAccessType) {
//						case KeyPwdConstant.IType.TYPE_KEY: // 蓝牙钥匙
//							EkeyRecordActivity.actionStart(mActivity, item.getId(), mKey.getLockAlias());
//							break;

						case KeyPwdConstant.IType.TYPE_PWD: // 数字密码
						case KeyPwdConstant.IType.TYPE_IC_CARD: // 门卡
						case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 指纹
							PasscodeRecordActivity.actionStart(mActivity, mAccessType, item.getCardNumber(),
									item.getFingerprintNumber(), item.getKeyboardPwd(), mKey.getLockId());
							break;

						default:
							break;
					}
				}
			}
		});
		mKeyPwdListAdapter.setmEditBtnClickListener(new KeyPwdListAdapter.ListViewActionBtnClickListener() {
			@Override
			public void onClick(View view, KeyPwd item) {
				if (item.isBTKey()) {
					EkeyDetailActivity.actionStart(mActivity, item.getId(),
							item.getKeyRight(), item.getAlias(), item.getType(), item.getStartDate(),
							item.getEndDate(), item.getRecUser(), item.getSendUser(), item.getSendDate(), item.getStatus(), item);
				} else {
                    if (item.isAdminPwd()){
                        Intent intent = new Intent();
                        intent.setClass(mActivity, ModifyAdminPasscodeActivity.class);
                        intent.putExtra(ModifyAdminPasscodeActivity.KEY_PASSCODE, item.getKeyboardPwd());
						intent.putExtra(ModifyAdminPasscodeActivity.KEY, mKey);
                        startActivityForResult(intent, REQUEST_CODE_PASSCODE);
                    }else {
						switch (mAccessType) {
//						case KeyPwdConstant.IType.TYPE_KEY: // 蓝牙钥匙
//							EkeyRecordActivity.actionStart(mActivity, item.getId(), mKey.getLockAlias());
//							break;
							case KeyPwdConstant.IType.TYPE_PWD: // 数字密码
							case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 指纹
							case KeyPwdConstant.IType.TYPE_IC_CARD: // 门卡
								PasscodeDetailActivity.actionStart(mActivity, mAccessType, item, mCurrentCategory,mKey);
								break;

							default:
								break;
						}
					}
				}
			}
		});

		mKeyPwdListAdapter.setmSendBtnClickListener(new KeyPwdListAdapter.ListViewActionBtnClickListener() {
			@Override
			public void onClick(View view, KeyPwd item) {
				if (item.isBTKey()) {
					//showShareBTKey(item.getShareKeyUrl());
					Intent intentNickname = new Intent(mActivity, EkeyShareModifyActivity.class);
					intentNickname.putExtra(EkeyShareModifyActivity.KEY_KEY_ID, mKey.getUserKeyId());
					intentNickname.putExtra(EkeyShareModifyActivity.KEY_ITEM, item);
					startActivityForResult(intentNickname, REQUEST_CODE_MODIFY_EKEY_SHARE);

				} else {
					showShare(item);
				}

			}
		});

		mKeyPwdListAdapter.setmWarningBtnClickListener(new KeyPwdListAdapter.ListViewActionBtnClickListener() {
			@Override
			public void onClick(View view, KeyPwd item) {
				switch (item.getKeyboardPwdType()) {
					// 一次性
					case 1:
						showHelpPopupWindow(view, mActivity.getResources().getString(R.string.one_time_pwd_warning));
						break;
					// 永久
					case 2:
						showHelpPopupWindow(view, mActivity.getResources().getString(R.string.permanent_pwd_warning));
						break;
					// 限时
					case 3:
						// 循环
					case 5:
					case 6:
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
						showHelpPopupWindow(view, mActivity.getResources().getString(R.string.time_limited_pwd_warning));
						break;
				}

			}
		});
	}

	private String getDialogDeleteHint() {
		int resId = 0;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				resId = R.string.note_pwd_delete_confirm_dialog_hint;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				resId = R.string.note_ic_card_delete_confirm_dialog_hint;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				resId = R.string.note_fingerprint_delete_confirm_dialog_hint;
				break;

			default:
				break;
		}

		return getString(resId);
	}

	private void showHelpPopupWindow(View anchor, String msg) {
		if (null == mHelpPopupWindow) {
			mHelpPopupWindow = new HelpPopupWindow(mActivity, R.layout.help_popup_window_layout4, R.dimen.help_win_width_2, R.dimen.help_win_height_2);
		}
		mHelpPopupWindow.show(anchor, Gravity.BOTTOM, msg);
	}

	private void initView() {
		ll_empty_hint = mRootView.findViewById(R.id.ll_empty_hint);
		mTvDesc = mRootView.findViewById(R.id.tv_desc);
		mTvEmptyHint = mRootView.findViewById(R.id.tv_empty_hint);
		tv_create_btn = mRootView.findViewById(R.id.tv_create_btn);
		iv_empty_hint = mRootView.findViewById(R.id.iv_empty_hint);
		iv_add_more_btn = mRootView.findViewById(R.id.iv_add_more_btn);

		mSwipeRefreshLayout = mRootView.findViewById(R.id.refresh_layout);
		mRvKeyPwdList = mRootView.findViewById(R.id.rv_key_pwd_list);
		mRvKeyPwdList.addItemDecoration(new SpacesItemDecoration(getResources().getDimensionPixelSize(R.dimen.dimens_dp_10)));
		mRvKeyPwdList.setLayoutManager(new LinearLayoutManager(getActivity()));
		mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				showRefreshView();
				refreshData();
			}
		});
		mRvKeyPwdList.setOnLoadMoreListener(new CommonRecyclerView.LoadMoreListener() {
			@Override
			public void onLoadMore() {
				loadMoreData();
			}
		});

		tv_create_btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createNewPwdOrKey();
			}
		});
		iv_add_more_btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createNewPwdOrKey();
			}
		});
	}

	private void createNewPwdOrKey() {
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_KEY: // 创建蓝牙钥匙
				if (mActivity instanceof KeyPwdManageActivity) {
					KeyPwdManageActivity keyPwdManageActivity = (KeyPwdManageActivity) mActivity;
					keyPwdManageActivity.createKeyPwd(KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_KEY_BT_KEY);
				}
				break;

			case KeyPwdConstant.IType.TYPE_PWD: // 创建数字密码
				// 去选择密码类型
                if (mActivity instanceof KeyPwdManageActivity) {
                    KeyPwdManageActivity keyPwdManageActivity = (KeyPwdManageActivity) mActivity;
                    keyPwdManageActivity.createKeyPwd(KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_PERIOD);
                }
//				KeyPwdTypeSelectActivity.actionStartForResult(mActivity,mKey);
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 添加指纹
				LockAddGuideActivity.actionStart(getActivity(), KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD, KeyPwdConstant.IType.TYPE_FINGERPRINT,mKey,mLockType);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 添加门卡
				LockAddGuideActivity.actionStart(getActivity(), KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD, KeyPwdConstant.IType.TYPE_IC_CARD,mKey,mLockType);
				break;

			default:
				break;
		}
	}

	private void showAddMoreBtn(boolean isShow) {
		if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_INVALID == mCurrentCategory) {
			iv_add_more_btn.setVisibility(View.GONE);
			return;
		}
		iv_add_more_btn.setVisibility(isShow ? View.VISIBLE : View.GONE);
	}

	private void initDescAndEmptyHint() {

		tv_create_btn.setVisibility(View.GONE);

		int none_tips = R.string.no_data;
		int tv_create_text = R.string.create_a_key;

		int empty_hint_icon = R.drawable.ic_no_data;
		if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE == mCurrentCategory) {
			tv_create_btn.setVisibility(View.VISIBLE);
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					none_tips = R.string.none_key_tips;
					tv_create_text = R.string.create_a_key;
					empty_hint_icon = R.drawable.create_a_key;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					none_tips = R.string.none_pwd_tips;
					tv_create_text = R.string.create_a_pwd;
					empty_hint_icon = R.drawable.create_a_pin_code;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					none_tips = R.string.none_fingerprint_tips;
					tv_create_text = R.string.add_a_fingerprint;
					empty_hint_icon = R.drawable.create_a_fingerprint;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					none_tips = R.string.none_ic_card_tips;
					tv_create_text = R.string.add_an_ic_card;
					empty_hint_icon = R.drawable.create_a_ic_card;
					break;

				default:
					break;
			}
		}
		mTvDesc.setText(String.format(getResources().getString(getCountDesc()), 0));
		mTvEmptyHint.setText(none_tips);
		tv_create_btn.setText(tv_create_text);
		iv_empty_hint.setImageResource(empty_hint_icon);
	}

	private @StringRes
	int getCountDesc() {
		int desc = R.string.key_count_tips_valid;
		if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_NOT_ACTIVATED == mCurrentCategory) {
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					desc = R.string.key_count_tips_pending;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					desc = R.string.pwd_count_tips_pending;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					desc = R.string.fingerprint_count_tips_pending;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					desc = R.string.ic_card_count_tips_pending;
					break;

				default:
					break;
			}
		} else if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE == mCurrentCategory) {
			tv_create_btn.setVisibility(View.VISIBLE);
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					desc = R.string.key_count_tips_valid;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					desc = R.string.pwd_count_tips_valid;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					desc = R.string.fingerprint_count_tips_valid;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					desc = R.string.ic_card_count_tips_valid;
					break;

				default:
					break;
			}
		} else if (KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_INVALID == mCurrentCategory) {
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					desc = R.string.key_count_tips_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					desc = R.string.pwd_count_tips_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					desc = R.string.fingerprint_count_tips_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					desc = R.string.ic_card_count_tips_invalid;
					break;

				default:
					break;
			}
		}
		return desc;
	}

	private void setData(List<KeyPwd> data, boolean isResetData) {
		if (isResetData) {
			if (isShowAdminPwd()) {
				data.add(0, mAdminPwd);
			}
			mKeyPwdListAdapter.reset(data);
		} else {
			mKeyPwdListAdapter.addAll(data);
		}
	}

	private void showContentView() {
		if (null != mKeyPwdListAdapter) {
			mKeyPwdListAdapter.notifyDataSetChanged();
		}
		if (null != ll_empty_hint) {
			ll_empty_hint.setVisibility(View.GONE);
		}
		if (null != mRvKeyPwdList) {
			mRvKeyPwdList.setVisibility(View.VISIBLE);
		}
		hideLoadMoreView();
		showAddMoreBtn(true);
	}

	private void showEmptyView() {
		if (!isRefreshData()) {
			toast(R.string.no_more_data);
			return;
		} else {
			setData(new ArrayList<KeyPwd>(), isRefreshData());
		}
			// 有管理员密码需要显示，不需要显示无数据页面
		if (isShowAdminPwd()) {
			mTvDesc.setText(String.format(getResources().getString(getCountDesc()), mKeyPwdListAdapter.getDataCount()));
			showContentView();
			return;
		}

		showAddMoreBtn(false);
		if (null != ll_empty_hint) {
			ll_empty_hint.setVisibility(View.VISIBLE);
		}
		if (null != mRvKeyPwdList) {
			mRvKeyPwdList.setVisibility(View.GONE);
		}
	}

	private void hideRefreshView() {
		if (mSwipeRefreshLayout != null) {
			mSwipeRefreshLayout.setRefreshing(false);
		}
	}

	private void showRefreshView() {
		if (mSwipeRefreshLayout != null) {
			mSwipeRefreshLayout.setRefreshing(true);
		}
	}

	private void hideLoadMoreView() {
		if (null == mFooterView) {
			return;
		}
		if (mFooterView.getVisibility() == View.VISIBLE) {
			mFooterView.setVisibility(View.GONE);
		}
	}

	private void showLoadMoreView() {
		if (null == mFooterView) {
			return;
		}
		if (mFooterView.getVisibility() == View.GONE) {
			mFooterView.setVisibility(View.VISIBLE);
		}
	}

	private boolean isRefreshData() {
		return mCurrentPageNo == 1;
	}

	public void refreshData() {
		if (!mIsLoadingData) {
			mCurrentPageNo = 1;
			requestKeyPwdFpCardList();
		}
	}

	public void loadMoreData() {
		if (!mIsLoadingData) {
			showLoadMoreView();
			requestKeyPwdFpCardList();
		}
	}

	private void requestKeyPwdFpCardList() {
		mIsLoadingData = true;
		RestClient.builder()
				.url(getUrlDataList())
				.params(getParamsDataList())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						mIsLoadingData = false;

						if (!isAdded()) {
							return;
						}
						hideRefreshView();
						hideLoadMoreView();

						JSONObject result = JSON.parseObject(response);
						if (null == result || !result.getBoolean("success")) {
							showEmptyView();
						} else {
							if (mAccessType == KeyPwdConstant.IType.TYPE_KEY || mAccessType == KeyPwdConstant.IType.TYPE_PWD) {
								JSONObject data = result.getJSONObject("data");

								int totalNum = data.getInteger("totalNum");
								if (isShowAdminPwd()) {
									++totalNum;
								}
								mTvDesc.setText(String.format(getResources().getString(getCountDesc()), totalNum));
								List<KeyPwd> keyPwdList = GsonUtil.fromJson(data.getJSONArray("dataList").toJSONString(), new TypeToken<List<KeyPwd>>() {
								});
								if (CollectionUtil.isEmpty(keyPwdList)) {
									showEmptyView();
								} else {
									setData(keyPwdList, isRefreshData());
									showContentView();
									mCurrentPageNo++;
								}
							} else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT || mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) {
								JSONArray dataList = result.getJSONArray("data");

								List<KeyPwd> fpCardList = new ArrayList<>();
								if (mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) {
									fpCardList = GsonUtil.fromJson(dataList.toJSONString(), new TypeToken<List<KeyPwd>>() {
									});
								} else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT) {
									// 因为 KeyPwd 类的 id 为 int，而指纹请求返回数据的 id 为 String，所以无法自动解析，改为手动解析
									for (int i = 0; i < dataList.size(); i++) {
										JSONObject dataObj = dataList.getJSONObject(i);
										KeyPwd fp = new KeyPwd();

										fp.setFpStringId(dataObj.getString("id"));
										fp.setFingerprintId(dataObj.getString("fingerprintId"));
										fp.setFingerprintNumber(dataObj.getString("fingerprintNumber"));
										fp.setRemark(dataObj.getString("remark"));
										Integer fpType = dataObj.getInteger("type");
										fp.setType(fpType);
										if (Integer.valueOf(1).equals(fpType)) { // 限时指纹
											fp.setStartDate(dataObj.getLong("startDate"));
											fp.setEndDate(dataObj.getLong("endDate"));
										}
										fp.setCreateDate(dataObj.getLong("createDate"));

										String alias = dataObj.getString("alias");
										fp.setAlias(StringUtil.isBlank(alias) ? getString(R.string.text_no_content_default) : alias);

										fpCardList.add(fp);
									}
								}

								if (CollectionUtil.isEmpty(fpCardList)) {
									showEmptyView();
								} else {
									setData(fpCardList, isRefreshData());
									showContentView();
									mCurrentPageNo++;
								}

								mTvDesc.setText(String.format(getResources().getString(getCountDesc()), mKeyPwdListAdapter.getDataCount()));
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						mIsLoadingData = false;

						if (!isAdded()) {
							return;
						}
						showEmptyView();
						hideLoadMoreView();
						hideRefreshView();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						mIsLoadingData = false;

						if (!isAdded()) {
							return;
						}
						showEmptyView();
						hideLoadMoreView();
						hideRefreshView();
					}
				})
				.build()
				.post();
	}

	private String getUrlDataList() {
		String url = "";
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_KEY: // 获取蓝牙钥匙列表
				url = Urls.LOCK_KEY_LIST_V2;
				break;

			case KeyPwdConstant.IType.TYPE_PWD: // 获取数字密码列表
				url = Urls.LOCK_KEYBOARD_LIST_V2;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 获取指纹列表
				url = Urls.FINGERPRINT_LIST;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 获取门卡列表
				url = Urls.IC_CARD_LIST;
				break;

			default:
				break;
		}
		return url;
	}

	private WeakHashMap<String, Object> getParamsDataList() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mKey.getLockId());
		params.put("category", mCurrentCategory);

		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_KEY: // 获取蓝牙钥匙列表
			case KeyPwdConstant.IType.TYPE_PWD: // 获取数字密码列表
			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 获取指纹列表
				params.put("pageNo", mCurrentPageNo);
				params.put("pageSize", 20);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 获取门卡列表
				params.put("start", mCurrentPageNo - 1);
				params.put("limit", 20);
				params.put("keyword", "");
				break;

			default:
				break;
		}

		return params;
	}

	private WeakHashMap<String, Object> getParamsDeleteEkey(KeyPwd item) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("keyId", item.getId());

		// Y同时删除他所发送的钥匙，N则不。（注：只适用于授权用户，普通用户可传空）
		if (item.getKeyRight() == 1) {
			params.put("delType", "Y");
		}

		return params;
	}


	/**
	 * 删除钥匙
	 */
	private void deleteEkey(KeyPwd item) {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_DELETE)
				.loader(mActivity)
				.params(getParamsDeleteEkey(item))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.ekey_delete_success);
							refreshData();
						} else {
							toastBackendError(code, R.string.ekey_delete_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.ekey_delete_fail);
					}
				})
				.build()
				.post();
	}

	private void lockDeletePasscode(KeyPwd item) {
		showLoading();
		setDeletePasscodeCallback(item);
		if (mKey.getLockId()<0){
			int pwdType = covertMHpwdType(item.getKeyboardPwdType());
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.deleteKeyboardPwd(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),pwdType,item.getKeyboardPwd(),mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.deletePasscode(item.getKeyboardPwd(), mKey.getLockData(), mKey.getLockMac(), new DeletePasscodeCallback() {
                @Override
                public void onDeletePasscodeSuccess() {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;

                            if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                requestDeletePwdFpCard(2);
                            } else {
                                makeToast(false);
                            }
                        }
                    });
                }
            });
		}
	}

	private int covertMHpwdType(int keyPwdType) {
			int type;
			type = keyPwdType;
			if (keyPwdType == 3 || keyPwdType == 15) {
				type = 4;//限时密码
			}
			if (keyPwdType == 4) {
				type = 6;//清空密码
			}
			if (keyPwdType >= 5 && keyPwdType < 15) {
				type = 3;//循环密码
			}
			return type;
	}

	private void setDeletePasscodeCallback(final KeyPwd item) {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.DELETE_KEYBOARD_PWD);
			MyApplication.pplBleSession.setKeyboardPwdType(item.getKeyboardPwdType());
			MyApplication.pplBleSession.setKeyboardPwdOriginal(item.getKeyboardPwd());
			MyApplication.pplBleSession.setmILockDeletePasscode(new MHILockDeletePasscode() {
				@Override
				public void onSuccess() {
					mActivity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							requestDeletePwdFpCard(1);
						}
					});
				}

				@Override
				public void onFail() {
					mActivity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							makeToast(false);
						}
					});
				}
			});
		}else {

		}

	}

	/**
	 * 通过 SDK 删除某张 IC 卡
	 */
	private void lockDeleteIcCard(String cardNumber) {
		showLoading();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setMH_DeleteICCardCallback(cardNumber);
				sPPLOCK.delCard(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId())
						,cardNumber,mKey.getK1());
			} else {
				//toast("删除卡片通过扫描后进行连接" + cardNumber);
				setMH_DeleteICCardCallback(cardNumber);
				startLockActionScan();
			}
		}else {
            mTTLockAPI.deleteICCard(cardNumber, mKey.getLockData(), mKey.getLockMac(), new DeleteICCardCallback() {
                @Override
                public void onDeleteICCardSuccess() {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (lockError == LockError.IC_CARD_NOT_EXIST) {
                                // 锁里不存在该卡片，直接删除服务器卡片数据
                                requestDeletePwdFpCard(1);
                            } else {
                                if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestDeletePwdFpCard(2);
                                } else {
                                    toast(R.string.operation_fail);
                                }
                            }
                        }
                    });
                }
            });
		}

	}


	/**
	 * 通过 SDK 删除某个指纹
	 */
	private void lockDeleteFingerprint(long fingerprintNumber, String fingerId) {
		showLoading();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setMH_DeleteFingerprintCallback(fingerId);
				sPPLOCK.delFingerprint(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId())
						,fingerId,mKey.getK1());
			} else {
				setMH_DeleteFingerprintCallback(fingerId);
				startLockActionScan();
			}
		}else {
            mTTLockAPI.deleteFingerprint(String.valueOf(fingerprintNumber), mKey.getLockData(), mKey.getLockMac(), new DeleteFingerprintCallback() {
                @Override
                public void onDeleteFingerprintSuccess() {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (lockError == LockError.FINGER_PRINT_NOT_EXIST) {
                                // 锁里不存在该指纹，直接删除服务器指纹数据
                                requestDeletePwdFpCard(1);
                            } else {
                                if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestDeletePwdFpCard(2);
                                } else {
                                    toast(R.string.operation_fail);
                                }
                            }
                        }
                    });
                }
            });
		}

	}


	private void setMH_DeleteFingerprintCallback(String fingerprintId) {
		MyApplication.pplBleSession.setOperation(LockOperation.DEL_FINGERPRINT);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setFingerId(fingerprintId);
		MyApplication.pplBleSession.setmILockDeleteFingerprint(new MHILockDeleteFingerprint() {
			@Override
			public void onSuccess() {
				mActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						requestDeletePwdFpCard(1);
					}
				});
			}
			@Override
			public void onFail() {
				mActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						toast(R.string.operation_fail);
					}
				});

			}
		});
	}

	private void setMH_DeleteICCardCallback(String cardId) {
		MyApplication.pplBleSession.setOperation(LockOperation.DEL_CARD);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setCardId(cardId);
		MyApplication.pplBleSession.setmILockDeleteCard(new MHILockDeleteCard() {
			@Override
			public void onSuccess() {
				mActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						requestDeletePwdFpCard(1);
					}
				});
			}

			@Override
			public void onFail() {
				mActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						toast(R.string.operation_fail);
					}
				});
			}
		});
	}


	/**
	 * 请求服务器，删除键盘密码/指纹/门卡
	 * mediumType可选	Integer
	 * 通讯介质（1：蓝牙，2：网关，默认是1）
	 */
	private void requestDeletePwdFpCard(int mediumType) {
		RestClient.builder()
				.url(getUrlDeletePwdFpCard())
				.loader(mActivity)
				.params(getParamsDeletePwdFpCard(mediumType))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("DELETE_PWD_FINGERPRINT_CARD", response);
						PeachLoader.stopLoading();

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_PWD));
							makeToast(true);
						} else {
							makeToast(false);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						stopLoading();
						makeToast(false);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						stopLoading();
						makeToast(false);
					}
				})
				.build()
				.post();
	}

	private void makeToast(boolean isSuccess) {
		if (KeyPwdConstant.IType.TYPE_PWD == mAccessType) {
			toast(isSuccess ? R.string.passcode_delete_success : R.string.passcode_delete_fail);
		} else {
			if (isSuccess) {
				toastSuccess();
			} else {
				toastFail();
			}
		}
	}

	private String getUrlDeletePwdFpCard() {
		String url = Urls.LOCK_PASSCODE_DELETE;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				url = Urls.LOCK_PASSCODE_DELETE;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				url = Urls.IC_CARD_DELETE;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				url = Urls.FINGERPRINT_DELETE;
				break;

			default:
				break;
		}

		return url;
	}

	private WeakHashMap<String, Object> getParamsDeletePwdFpCard(int mediumType) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				params.put("lockId", mKey.getLockId());
				params.put("userId", PeachPreference.readUserId());
				params.put("keyboardPwdId", mSelectedPwdFpCardToDelete.getId());
				params.put("mediumType", mediumType);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				params.put("lockId", mKey.getLockId());
				params.put("cardNumber", mSelectedPwdFpCardToDelete.getCardNumber());
				params.put("deleteType", mediumType);
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				params.put("id", String.valueOf(mSelectedPwdFpCardToDelete.getFpStringId()));
				params.put("lockId", mKey.getLockId());
				params.put("userId", PeachPreference.readUserId());
				params.put("deleteType", mediumType);
				break;

			default:
				break;
		}

		return params;
	}

	/**
	 * 创建成功事件是否由本 Fragment 处理分享
	 * <p>
	 * KeyPwdManageActivity 下挂着「生效中」「待生效」两个 KeyPwdListFragment，
	 * 且 EventBus 注册在 BaseFragment，两个实例都会收到同一条创建成功事件。
	 * 不加区分的话分享面板会被拉起两次，故只由事件指定的目标 tab 处理。
	 *
	 * @param tabCategory 事件携带的目标 tab 位置（见 CreatePwdKeyActionInfo）
	 */
	private boolean isTargetTab(int tabCategory) {
		int targetCategory = CreatePwdKeyActionInfo.TAB_CATEGORY_0 == tabCategory
				? KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE
				: KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_NOT_ACTIVATED;
		return mCurrentCategory == targetCategory;
	}

	public void showShare(final KeyPwd item) {
		try {
			Intent vIt = new Intent(Intent.ACTION_SEND);
//							vIt.setPackage("com.facebook.orca");
			vIt.setType("text/plain");
			vIt.putExtra(Intent.EXTRA_TEXT, getShareContent(item));
			startActivity(vIt);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		OnekeyShare oks = new OnekeyShare();
//
//		// 自定义分享平台
//		oks.setCustomerLogo(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share_zalo),
//				"Zalo", new View.OnClickListener() {
//					@Override
//					public void onClick(View view) {
//						try {
//							Intent vIt = new Intent(Intent.ACTION_SEND);
////							vIt.setPackage("com.facebook.orca");
//							vIt.setType("text/plain");
//							vIt.putExtra(Intent.EXTRA_TEXT, getShareContent(item));
//							startActivity(vIt);
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		//关闭sso授权
//		oks.disableSSOWhenAuthorize();
//		oks.setCallback(new PlatformActionListener() {
//			@Override
//			public void onComplete(Platform platform, int i, HashMap<String, Object> hashMap) {
//
//			}
//
//			@Override
//			public void onError(Platform platform, int i, Throwable throwable) {
//
//			}
//
//			@Override
//			public void onCancel(Platform platform, int i) {
//
//			}
//		});
//
//		// title标题，微信、QQ和QQ空间等平台使用
//		oks.setTitle(getString(R.string.app_name));
//		// titleUrl QQ和QQ空间跳转链接
////		oks.setTitleUrl("http://sharesdk.cn");
////		oks.setAddress("13201812820");
//		// text是分享文本，所有平台都需要这个字段
//		oks.setText(getShareContent(item));
//		// imagePath是图片的本地路径，Linked-In以外的平台都支持此参数
////		oks.setImagePath("/sdcard/test.jpg");//确保SDcard下面存在此张图片
//		// url在微信、微博，Facebook等平台中使用
////		oks.setUrl("http://sharesdk.cn");
//		// comment是我对这条分享的评论，仅在人人网使用
////		oks.setComment("我是测试评论文本");
//		// 启动分享GUI
//		oks.show(mActivity);
	}

	private String getShareContent(KeyPwd mPasscode) {
		return Utils.getShareContent(getActivity(), mPasscode.getKeyboardPwdType(), mPasscode.getKeyboardPwd(),
				mPasscode.getCreateDate(), mPasscode.getStartDate(), mPasscode.getEndDate(), mKey.getLockName());
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		if (Event.EventType.CREATE_BT_KEY_SUCCESS == event.type) {

			refreshData();
			CreatePwdKeyActionInfo createPwdKeyActionInfo = (CreatePwdKeyActionInfo) event.obj;
			if (null != createPwdKeyActionInfo) {
				if (mActivity instanceof KeyPwdManageActivity) {
					KeyPwdManageActivity keyPwdManageActivity = (KeyPwdManageActivity) mActivity;
					keyPwdManageActivity.setCurrentTab(createPwdKeyActionInfo.getTabCategory());
				}
				if (createPwdKeyActionInfo.isShare() && isTargetTab(createPwdKeyActionInfo.getTabCategory())) {
					showShareBTKey(createPwdKeyActionInfo.getShareUrl());
				}
			}
		} else if (Event.EventType.CREATE_PWD_SUCCESS == event.type) {
			refreshData();
			if (null != event.obj) {
				CreatePwdKeyActionInfo createPwdKeyActionInfo = (CreatePwdKeyActionInfo) event.obj;
				if (createPwdKeyActionInfo.isShare() && isTargetTab(createPwdKeyActionInfo.getTabCategory())) {
					showShare(createPwdKeyActionInfo.getKeyPwd());
				}
				if (mActivity instanceof KeyPwdManageActivity) {
					KeyPwdManageActivity keyPwdManageActivity = (KeyPwdManageActivity) mActivity;
					keyPwdManageActivity.setCurrentTab(createPwdKeyActionInfo.getTabCategory());
				}
			}
		} else if (Event.EventType.SYN_PWD_INFO_SUCCESS == event.type) {
			refreshData();
		} else if (Event.EventType.SHOW_LOCK_ADMIN_CODE_CONFIG_CHANGE == event.type) {
			ArrayList<KeyPwd> datas = (ArrayList<KeyPwd>) mKeyPwdListAdapter.getDatas();
			if (isShowAdminPwd()) {
				if (datas.contains(mAdminPwd)) {
					return;
				} else {
					datas.add(0, mAdminPwd);
				}
			} else {
				if (datas.contains(mAdminPwd)) {
					datas.remove(mAdminPwd);
				} else {
					return;
				}
			}
			if (CollectionUtil.isEmpty(datas)) {
				showEmptyView();
			} else {
				showContentView();
			}
			mKeyPwdListAdapter.notifyDataSetChanged();
			mTvDesc.setText(String.format(getResources().getString(getCountDesc()), mKeyPwdListAdapter.getDataCount()));
		} else if (Event.EventType.CLEAR_PWDS == event.type || Event.EventType.CLEAR_KEYS == event.type) {
			refreshData();
		} else if (Event.EventType.INVALIDATE_KEY == event.type || Event.EventType.RESTORE_KEY == event.type) {
			refreshData();
		} else if (Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE == event.type) {
			String keyboardPwd = (String) event.obj;
			if (!TextUtils.isEmpty(keyboardPwd)) {
				mKey.setNoKeyPwd(keyboardPwd);
				mAdminPwd.setKeyboardPwd(keyboardPwd);
				mKeyPwdListAdapter.notifyDataSetChanged();
			}
		} else if (Event.EventType.MODIFY_LOCK_PASSCODE == event.type || Event.EventType.MODIFY_LOCK_PASSCODE_NAME == event.type) {
			refreshData();
		} else if (Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE_NAME == event.type) {
			String pwdName = (String) event.obj;
			if (!TextUtils.isEmpty(pwdName)) {
				mAdminPwd.setAlias(pwdName);
				mKeyPwdListAdapter.notifyDataSetChanged();
			}
		} else if (
			// 删除密码
				Event.EventType.DELETE_PWD == event.type
						// 修改有效期
						|| Event.EventType.MODIFY_KEY_PERIOD == event.type
						|| Event.EventType.MODIFY_PWD_PERIOD == event.type) {
			refreshData();
		} else if (Event.EventType.ADD_IC_CARD_SUCCESS == event.type) { // 添加门卡成功
			refreshData();
			if (null != event.obj) {
				CreatePwdKeyActionInfo createPwdKeyActionInfo = (CreatePwdKeyActionInfo) event.obj;
				if (mActivity instanceof KeyPwdManageActivity) {
					KeyPwdManageActivity keyPwdManageActivity = (KeyPwdManageActivity) mActivity;
					keyPwdManageActivity.setCurrentTab(createPwdKeyActionInfo.getTabCategory());
				}
			}
		}
	}

	public void showShareBTKey(String data) {
		try {
			final String msg = String.format(mActivity.getResources().getString(R.string.share_bt_key_text), data);
			Intent vIt = new Intent(Intent.ACTION_SEND);
//							vIt.setPackage("com.facebook.orca");
			vIt.setType("text/plain");
			vIt.putExtra(Intent.EXTRA_TEXT, msg);
			startActivity(vIt);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		OnekeyShare oks = new OnekeyShare();
//		final String msg = String.format(mActivity.getResources().getString(R.string.share_bt_key_text), data);
//
//		// 自定义分享平台
//		oks.setCustomerLogo(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share_zalo),
//				"Zalo", new View.OnClickListener() {
//					@Override
//					public void onClick(View view) {
//						try {
//							Intent vIt = new Intent(Intent.ACTION_SEND);
////							vIt.setPackage("com.facebook.orca");
//							vIt.setType("text/plain");
//							vIt.putExtra(Intent.EXTRA_TEXT, msg);
//							startActivity(vIt);
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		//关闭sso授权
//		oks.disableSSOWhenAuthorize();
//		oks.setCallback(new PlatformActionListener() {
//			@Override
//			public void onComplete(Platform platform, int i, HashMap<String, Object> hashMap) {
//
//			}
//
//			@Override
//			public void onError(Platform platform, int i, Throwable throwable) {
//
//			}
//
//			@Override
//			public void onCancel(Platform platform, int i) {
//
//			}
//		});
//
//		// title标题，微信、QQ和QQ空间等平台使用
//		oks.setTitle(getString(R.string.app_name));
//		oks.setText(msg);
//		oks.show(mActivity);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (RESULT_OK != resultCode) {
			return;
		}
		if (REQUEST_CODE_PASSCODE == requestCode) {
			String keyboardPwd = data.getStringExtra(LockSettingsActivity.KEY_RESULT_DATA);
			if (!TextUtils.isEmpty(keyboardPwd)) {
				mKey.setNoKeyPwd(keyboardPwd);
				mAdminPwd.setKeyboardPwd(keyboardPwd);
				mKeyPwdListAdapter.notifyDataSetChanged();
			}
		} else if (REQUEST_CODE_MODIFY_EKEY_SHARE == requestCode) {
			mKeyPwdListAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (null != mHelpPopupWindow) {
			mHelpPopupWindow.dismiss();
		}
	}
}
