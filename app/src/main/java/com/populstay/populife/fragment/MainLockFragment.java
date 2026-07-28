package com.populstay.populife.fragment;

import android.Manifest;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.reflect.TypeToken;
import com.populstay.populife.R;
import com.populstay.populife.activity.LockAddSelectTypeActivity;
import com.populstay.populife.activity.PDFActivity;
import com.populstay.populife.base.BaseVisibilityFragment;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.helper.OfflineLockUploader;
import com.populstay.populife.helper.UnsyncedDeviceDialog;
import com.populstay.populife.home.HomeListActivity;
import com.populstay.populife.home.entity.Home;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.ui.NoScrollViewPager;
import com.populstay.populife.ui.loader.LoaderStyle;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * 底部导航栏“Lock”Fragment
 * Created by Jerry
 */

public class MainLockFragment extends BaseVisibilityFragment {

	private static final int TAB_LOCK_DETAIL = 0;
	private static final int TAB_LOCK_LIST = 1;

	//当前用户名下锁拥有的 锁/钥匙 数量
	public static int mAccountLockNum = PeachPreference.getAccountLockNum(PeachPreference.readUserId());
	public static int mCurrentFragmentIndex = TAB_LOCK_DETAIL;
	private NoScrollViewPager mViewPager;
	private ImageView mIvAddLock, mIvNewMsg;
	private RelativeLayout mRlOnlineService;
	private TextView mTvSwitchHome;
	private ImageView mIvBrowseUserManual;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_main_lock, null);

		initView(view);
		initListener();
		requestLockGroup();
		return view;
	}

	private void receiveKey(String preId) {
		RestClient.builder()
				.url(Urls.KEY_RECEIVE)
				.loader(getActivity())
				.params("userId", PeachPreference.readUserId())
				.params("preId", preId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/


						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							fromShareHomeId = result.getString("data");

						}
						// 不能领取自己分享的钥匙
						else if (code == 952){
							toast(R.string.cannot_receive_oneself_shared_keys);
						}
						//钥匙已经被领取
						else if (code == 951){
							toast(R.string.the_key_has_been_receive);
						}
						requestLockGroup();
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						requestLockGroup();
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						requestLockGroup();
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/
					}
				})
				.build()
				.post();
	}



	private String fromShareHomeId;
	private Home currentHome;
	private void   requestLockGroup() {
		PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator);
		RestClient.builder()
				.url(Urls.LOCK_GROUP_LIST)
				.loader(getActivity())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/

						PeachLogger.d("LOCK_GROUP_LIST", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							List<Home> datas = GsonUtil.fromJson(result.getJSONArray("data").toJSONString(),new TypeToken<List<Home>>(){});
							if (!CollectionUtil.isEmpty(datas)){
								currentHome = datas.get(0);
								String lastSelectHomeId = PeachPreference.getLastSelectHomeId();

								if (!TextUtils.isEmpty(fromShareHomeId)){
									lastSelectHomeId = fromShareHomeId;
									PeachPreference.setLastSelectHomeId("");
									PeachPreference.setLastSelectHomeName("");
								}

								for (Home home : datas){
									if (home.getId().equals(lastSelectHomeId)){
										currentHome = home;
										break;
									}
								}

								if (TextUtils.isEmpty(lastSelectHomeId)){
									PeachPreference.setLastSelectHomeId(currentHome.getId());
									PeachPreference.setLastSelectHomeName(currentHome.getName());
								}
								EventBus.getDefault().post(new Event(Event.EventType.GET_HOME_DATA_COMPLETE,currentHome));
							}else {
								HomeListActivity.actionStart(getActivity(), HomeListActivity.VAL_ACTION_TYPE_SWITCH_HOME);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLoader.stopLoading();
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLoader.stopLoading();
						/*if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}*/
					}
				}).isCloseLoadDialog(false)
				.build()
				.get();
	}

	@Override
	protected void onVisibilityChanged(boolean visible) {
		super.onVisibilityChanged(visible);
		if (visible) {
			// 首页重新可见时先尝试补传;补传结束后若仍有同步失败的设备,再弹窗兜底提醒
			OfflineLockUploader.getInstance().tryUploadAll("lockHome", new Runnable() {
				@Override
				public void run() {
					checkUnsyncedDeviceDialog();
				}
			});

			final String sharePreId = PeachPreference.getShareKeyPreId();
			if (!TextUtils.isEmpty(sharePreId)){
				DialogUtil.showCommonDialog(mActivity, null,
						getString(R.string.receive_share_key),
						getString(R.string.ok), getString(R.string.cancel), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								receiveKey(sharePreId);
								PeachPreference.setShareKeyPreId("");
							}
						}, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								PeachPreference.setShareKeyPreId("");
							}
						});
			}

			// 刷新子 fragment 页面
			refreshChildFragment();
		}
	}

	/**
	 * 兜底提醒:自动补传结束后仍存在同步失败的设备时,在首页弹窗提示用户手动同步。
	 * 自动同步失败时不当场打扰用户(只记录状态),留到回到首页时再提醒。
	 */
	private void checkUnsyncedDeviceDialog() {
		if (getActivity() == null || getActivity().isFinishing()) {
			return;
		}
		if (UnsyncedDeviceDialog.isShowing()) {
			return;
		}
		List<OfflineLock> list = OfflineLockUploader.getInstance().queryLocalList(PeachPreference.readUserId());
		if (list == null || list.isEmpty()) {
			return;
		}
		for (OfflineLock lock : list) {
			if (UnsyncedDeviceDialog.shouldShow(lock)) {
				UnsyncedDeviceDialog.show(getActivity(), lock);
				return;
			}
		}
	}

	private void refreshChildFragment() {
		int curTabIndex = mViewPager.getCurrentItem();
		ViewPagerAdapter adapter = (ViewPagerAdapter) mViewPager.getAdapter();
		if (adapter != null) {
			if (curTabIndex == 0) {
				LockDetailFragment fragment = (LockDetailFragment) adapter.getItem(0);
				if (fragment != null) {
					//fragment.doRefresh();
				}
			} else if (curTabIndex == 1) {
				/*LockListFragment fragment = (LockListFragment) adapter.getItem(1);
				if (fragment != null) {
					fragment.doRefresh();
				}*/
			}
		}
	}

	private void initView(View view) {
		mTvSwitchHome = view.findViewById(R.id.tv_switch_home);
		mIvBrowseUserManual = view.findViewById(R.id.iv_browse_user_manual);
		mIvAddLock = view.findViewById(R.id.iv_main_lock_add);
		mIvNewMsg = view.findViewById(R.id.iv_main_lock_msg_new);
		mRlOnlineService = view.findViewById(R.id.rl_main_lock_online_service);

		mViewPager = view.findViewById(R.id.nsv_main_lock);
		setupViewPager(mViewPager);

		if (mAccountLockNum <= 1) {//没有锁或只有1把锁，显示锁详情页面
			setCurrentTab(TAB_LOCK_DETAIL);
		} else {//拥有多把锁（>=2），显示锁列表页面
			setCurrentTab(TAB_LOCK_LIST);
		}

		// 如果有锁，则显示 + 图标；如果没有，则隐藏 + 图标（会在 LockDetailFragment 页面显示“添加设备”的按钮）
		showAddLockBtn(mAccountLockNum > 0);
	}

	private void showAddLockBtn(boolean isShow){
		if (null == mIvAddLock){
			return;
		}
		mIvAddLock.setVisibility(isShow ? View.VISIBLE : View.GONE);
	}

	private void initListener() {
		mIvAddLock.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				goToNewActivity(LockAddSelectTypeActivity.class);
			}
		});
		mRlOnlineService.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(getActivity());
			}
		});
		mTvSwitchHome.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				HomeListActivity.actionStart(getActivity(), HomeListActivity.VAL_ACTION_TYPE_SWITCH_HOME);
			}
		});
		mIvBrowseUserManual.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PDFActivity.actionStart(getActivity(), getString(R.string.user_manual_app),
						"user_manual_app.pdf", true);
			}
		});
	}

	/**
	 * 切换 LockDetailFragment 和 LockListFragment 页面
	 *
	 * @param index 被切换的 index
	 */
	private void setCurrentTab(int index) {
		mCurrentFragmentIndex = index;
		mViewPager.setCurrentItem(index, false);
		refreshChildFragment();
	}

	private void setupViewPager(ViewPager viewPager) {
		ViewPagerAdapter localViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
		localViewPagerAdapter.addFragment(LockDetailFragment.newInstance(LockDetailFragment.VAL_TAG_FRAGMENT, "",""));
		//localViewPagerAdapter.addFragment(new LockListFragment());
		viewPager.setAdapter(localViewPagerAdapter);
	}

	public void showLockDetailFragment() {
		setCurrentTab(TAB_LOCK_DETAIL);
	}

	public void showLockListFragment() {
		setCurrentTab(TAB_LOCK_LIST);
	}

	public class ViewPagerAdapter extends FragmentPagerAdapter {
		private List<Fragment> mListFragment = new ArrayList<>();

		private ViewPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		private void addFragment(Fragment paramFragment) {
			if (mListFragment == null)
				mListFragment = new ArrayList();
			mListFragment.add(paramFragment);
		}

		public int getCount() {
			if (mListFragment == null)
				return 0;
			return mListFragment.size();
		}

		public Fragment getItem(int paramInt) {
			if (mListFragment == null)
				return null;
			return mListFragment.get(paramInt);
		}
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type){
            case Event.EventType.REFRESH_HOME_DATA:
                requestLockGroup();
                break;
			case Event.EventType.GET_HOME_DATA_COMPLETE:
			case Event.EventType.CHANGE_HOME:
				String userAccount = PeachPreference.getStr(PeachPreference.ACCOUNT);
				String lastSelectHomeName = PeachPreference.getLastSelectHomeName();
				if (TextUtils.isEmpty(lastSelectHomeName)){
					if (TextUtils.isEmpty(userAccount)){
						mTvSwitchHome.setText(R.string.my_space);
					}else {
						mTvSwitchHome.setText(String.format(getString(R.string.so_space), userAccount));
					}
				}else {
					mTvSwitchHome.setText(lastSelectHomeName);
				}
				break;
		}

	}
}
