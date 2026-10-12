package com.populstay.populife.fragment;

import static android.content.Context.RECEIVER_EXPORTED;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.logger.Logger;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.activity.GatewayBindedLockListActivity;
import com.populstay.populife.activity.LockAddSelectTypeActivity;
import com.populstay.populife.activity.LockDetailActivity;
import com.populstay.populife.activity.LockOperateRecordActivity;
import com.populstay.populife.activity.LockSettingsActivity;
import com.populstay.populife.adapter.DeviceListAdapter;
import com.populstay.populife.adapter.LockActionAdapter;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.base.BaseFragment;
import com.populstay.populife.common.Urls;
import com.populstay.populife.helper.DeviceDeleteRecoveryDialog;
import com.populstay.populife.helper.DeviceDeleteState;
import com.populstay.populife.databinding.FragmentLockDetailBinding;
import com.populstay.populife.entity.Gateway;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.LockAction;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.Home;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.KeyPwdManageActivity;
import com.populstay.populife.lock.ILockGetBattery;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.lock.ILockLock;
import com.populstay.populife.lock.ILockUnlock;
import com.populstay.populife.manhattanlock.MHILockGetBattery;
import com.populstay.populife.manhattanlock.MHILockGetTime;
import com.populstay.populife.manhattanlock.MHILockLock;
import com.populstay.populife.manhattanlock.MHILockUnlock;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.push.EventPushService;
import com.populstay.populife.ui.MyGridView;
import com.populstay.populife.ui.loader.LoaderStyle;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.ui.widget.extextview.ExTextView;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.JSONSafe;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.device.DeviceUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.api.TTLockClient;
import com.ttlock.bl.sdk.callback.ControlLockCallback;
import com.ttlock.bl.sdk.callback.GetAccessoryBatteryLevelCallback;
import com.ttlock.bl.sdk.callback.GetBatteryLevelCallback;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.constant.ControlAction;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.AccessoryInfo;
import com.ttlock.bl.sdk.entity.AccessoryType;
import com.ttlock.bl.sdk.entity.ControlLockResult;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

/**
 * 锁详情操作界面（开闭锁、key、passcode、records、settings）
 * Created by Jerry
 */
public class LockDetailFragment extends BaseFragment implements View.OnClickListener, AdapterView.OnItemClickListener {

	public static final String VAL_TAG_FRAGMENT = "val_tag_fragment";//显示在 MainLockFragment 中
	public static final String VAL_TAG_ACTIVITY = "val_tag_activity";//显示在 LockDetailActivity 中
	public static final int SHOW_DEVICE_ADD = 0;
	public static final int SHOW_LOCK_INFO = 1;
	public static final int SHOW_DEVICE_LIST = 2;
	public static final int SHOW_OUT_OF_DATE = 3;
	public static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_TAG = "key_tag";
	private static final String HOME_ID = "home_id";
	private Key mCurKEY;
	private Integer mNoticeLockOffset;
	private SwipeRefreshLayout mRefreshLayout;
	private MyGridView mGridView;
	private LockActionAdapter mAdapter;
	private List<LockAction> mActions = new ArrayList<>();
	private TextView mTvLockName;
	private ImageView mIvUnlocking, mIvUnlock, mIvLock, mIvLockImg;
	private LinearLayout mLlLockDetailUnlocking, mLlLockDetailUnlock, mLlLockDetailLock;
	private TextView mIvAddLock;
	private FrameLayout mFlLockInfo, mFlDeviceList;
	private RelativeLayout mRlUnlocking;
	private LinearLayout mLlLockAdd, mLlUnlockLock, mLlOutOfDate;
	private TextView mTvUnLockLastTime, mTvUnLockLastMsg;
	private ExTextView mIvLockBattery;
	private int mOpenid;
	private String mTag;
	private String mKeyId;
	private String mHomeId;
	private String mLockType;
	private int mKeyType;//钥匙类型（1限时，2永久，3单次，4循环）
	private boolean mIsLockCalled;//闭锁时，onLockSuccess/onLockFail是否被调用过
	private boolean mIsUnlockCalled;//开锁时，onUnlockSuccess/onUnlockFail是否被调用过
	private RecyclerView mDeviceListView;
	private DeviceListAdapter mDeviceListAdapter;
	private List<HomeDevice> mDeviceList;
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (EventPushService.ACTION_KEY_STATUS_CHANGE.equals(action)) { // 钥匙状态发生变化
				//requestUserLockInfo(mKeyId);
				requestDeviceData();
			} else if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
				updateTimezoneNotice();
			}
		}
	};
	private boolean isMHLock = false;
	private ImageView mIvAddMoreDeviceBtn;

	public static LockDetailFragment newInstance(String actionType, String keyId, String lockType) {
		Bundle args = new Bundle();
		args.putString(KEY_TAG, actionType);
		args.putString(KEY_KEY_ID, keyId);
		args.putString(KEY_LOCK_TYPE, lockType);
		LockDetailFragment fragment = new LockDetailFragment();
		fragment.setArguments(args);
		return fragment;
	}

	/**
	 * 开启开闭锁时的旋转动画
	 *
	 * @param operateType 操作类型
	 *                    1 大图标开锁（锁只支持 APP 开锁）
	 *                    2 小图标开锁（锁同时支持 APP 开锁、闭锁）
	 *                    3 小图标闭锁（锁同时支持 APP 开锁、闭锁）
	 */
	private void startLockingAnimation(int operateType) {
		enableLockingColorFiltr(false, false, 0);
		Animation anim = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate_locking);
		LinearInterpolator lir = new LinearInterpolator();
		anim.setInterpolator(lir);
		switch (operateType) {
			case 1:
				//mBarUnlocking.setVisibility(View.VISIBLE);
				//mBarUnlocking.startAnimation(anim);

				//startScaleAnimation(mLlLockDetailUnlocking);
//				startScaleAnimation(mBarUnlocking);
				break;

			case 2:
				//mBarUnlock.setVisibility(View.VISIBLE);
				//mBarUnlock.startAnimation(anim);

				//startScaleAnimation(mLlLockDetailUnlock);
//				startScaleAnimation(mBarUnlock);
				break;

			case 3:
				//mBarLock.setVisibility(View.VISIBLE);
				//mBarLock.startAnimation(anim);

				//startScaleAnimation(mLlLockDetailLock);
//				startScaleAnimation(mBarLock);
				break;

			default:
				break;
		}
	}

	/**
	 * 开启锁图标的缩放动画
	 */
	private void startScaleAnimation(View view) {
		ObjectAnimator animatorX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.85f);
		ObjectAnimator animatorY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.85f);
		AnimatorSet set = new AnimatorSet();
		set.setDuration(300);
		set.playTogether(animatorX, animatorY);
		set.start();
	}

	/**
	 * 停止开闭锁时的旋转动画
	 *
	 * @param operateType 操作类型
	 *                    1 大图标开锁（锁只支持 APP 开锁）
	 *                    2 小图标开锁（锁同时支持 APP 开锁、闭锁）
	 *                    3 小图标闭锁（锁同时支持 APP 开锁、闭锁）
	 */
	private void stopLockingAnimation(int operateType) {
		enableLockingColorFiltr(true, false, 0);

		switch (operateType) {
			case 1:
				stopScaleAnimation(mLlLockDetailUnlocking);
				//stopScaleAnimation(mBarUnlocking);
				break;

			case 2:
				stopScaleAnimation(mLlLockDetailUnlock);
				//stopScaleAnimation(mBarUnlock);
				break;

			case 3:
				stopScaleAnimation(mLlLockDetailLock);
				//stopScaleAnimation(mBarLock);
				break;

			default:
				break;
		}

	/*	mBarUnlocking.setVisibility(View.GONE);
		mBarUnlock.setVisibility(View.GONE);
		mBarLock.setVisibility(View.GONE);*/
	}

	/**
	 * 停止锁图标的缩放动画
	 */
	private void stopScaleAnimation(View view) {
		ObjectAnimator animatorX = ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1.0f, 0.9f, 1.0f);
		ObjectAnimator animatorY = ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1.0f, 0.9f, 1.0f);
		AnimatorSet set = new AnimatorSet();
		set.setDuration(500);
		set.playTogether(animatorX, animatorY);
		set.start();
	}

    private FragmentLockDetailBinding binding;
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLockDetailBinding.inflate(inflater, container, false);
        mNoticeLockOffset = null;
        View view = binding.getRoot();
		getIntentData();
		initView(view);
		initListener();
		registerReceiver();
		//requestUserLockInfo(mKeyId);
		initDeviceListUI(view);
		if (VAL_TAG_ACTIVITY.equals(mTag)) {
			PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator);
			requestDeviceData();
		}
		return view;
	}

	private void initDeviceListUI(View view) {

		mIvAddMoreDeviceBtn = view.findViewById(R.id.iv_add_more_device_btn);
		mIvAddMoreDeviceBtn.setOnClickListener(this);
		mDeviceListView = view.findViewById(R.id.home_device_list_recyclerview);
		mDeviceListView.setLayoutManager(new GridLayoutManager(getContext(), 2));
		mDeviceList = new ArrayList<>();
		mDeviceListAdapter = new DeviceListAdapter(mDeviceList, getContext(), DeviceListAdapter.SHOW_TYPE_TWO_CARD, DeviceListAdapter.USE_FROM_DEVICE_LIST);
		mDeviceListView.setAdapter(mDeviceListAdapter);
		mDeviceListAdapter.setOnItemClickListener(new DeviceListAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(View v, int position) {
				mDeviceListAdapter.selectItem(position);
				HomeDevice homeDevice = mDeviceList.get(position);
				// 锁
				if (homeDevice.getType() == 1) {
					LockDetailActivity.actionStart(getActivity(), homeDevice.getDeviceId(), homeDevice.getName());
				}
				// 网关
				else if (homeDevice.getType() == 2) {
					Gateway gateway = new Gateway();
					gateway.setGatewayId(Integer.valueOf(homeDevice.getDeviceId()));
					gateway.setName(homeDevice.getAlias());
					GatewayBindedLockListActivity.actionStart(mActivity, gateway);
				}
			}
		});

	}

	private void registerReceiver() {
		if (getActivity() != null) {
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
				getActivity().registerReceiver(mReceiver, getIntentFilter(),RECEIVER_EXPORTED);
			}else {
				getActivity().registerReceiver(mReceiver, getIntentFilter());
			}
		}
	}

	private IntentFilter getIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(EventPushService.ACTION_KEY_STATUS_CHANGE);
		intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
		return intentFilter;
	}

	@Override
	public void onResume() {
		super.onResume();
		updateTimezoneNotice();
	}

	private void readTimezoneNotice(JSONObject lockInfo) {
		// 缺少服务端时区时不能把默认值 0 当成锁位于 UTC。
		mNoticeLockOffset = null;
		try {
			mNoticeLockOffset = lockInfo.getInteger("timezoneRawOffSet");
		} catch (RuntimeException ignored) {
			// 无有效时区信息时不显示差异提示。
		}
		updateTimezoneNotice();
	}

	private void updateTimezoneNotice() {
		if (binding == null || !isAdded()) return;
		boolean different = mNoticeLockOffset != null
				&& mNoticeLockOffset != TimeZone.getDefault().getOffset(System.currentTimeMillis());
		binding.tvLockTimezoneNotice.setVisibility(different ? View.VISIBLE : View.GONE);
		if (different) {
			long minutes = Math.abs((long) mNoticeLockOffset) / 60000;
			String zone = String.format(Locale.ROOT, "UTC%s%02d:%02d",
					mNoticeLockOffset < 0 ? "-" : "+", minutes / 60, minutes % 60);
			binding.tvLockTimezoneNotice.setText(getString(R.string.lock_timezone_notice, zone));
		}
	}

	private void getIntentData() {
		Bundle bundle = getArguments();
		if (bundle != null) {
			mTag = bundle.getString(KEY_TAG);
			mKeyId = bundle.getString(KEY_KEY_ID);
			mHomeId = bundle.getString(HOME_ID);
			mLockType = bundle.getString(KEY_LOCK_TYPE);
			if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK) ||
					mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				isMHLock = true;
			}
		}
	}

	private void initView(View view) {
		mTvLockName = view.findViewById(R.id.tv_lock_detail_name);
		mTvLockName.setVisibility(View.GONE);
		mIvAddLock = view.findViewById(R.id.tv_lock_detail_add);
		mIvUnlocking = view.findViewById(R.id.iv_lock_detail_unlocking);
		mIvUnlock = view.findViewById(R.id.iv_lock_detail_unlock);
		mIvLock = view.findViewById(R.id.iv_lock_detail_lock);
		mFlLockInfo = view.findViewById(R.id.fl_lock_detail);
		mFlDeviceList = view.findViewById(R.id.fl_device_list);
		mLlLockAdd = view.findViewById(R.id.ll_lock_detail_add);
		mLlOutOfDate = view.findViewById(R.id.ll_out_of_date);
		mRlUnlocking = view.findViewById(R.id.rl_lock_detail_unlocking);
		mLlUnlockLock = view.findViewById(R.id.ll_lock_detail_unlock_lock);
		mTvUnLockLastTime = view.findViewById(R.id.tv_lock_last_time);
		mTvUnLockLastMsg = view.findViewById(R.id.tv_lock_last_msg);
		mIvLockBattery = view.findViewById(R.id.iv_lock_battery);
		mIvLockImg = view.findViewById(R.id.iv_lock_img);

		if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			LinearLayout.LayoutParams linearParams = (LinearLayout.LayoutParams) mIvLockImg.getLayoutParams();
			//取控件当前的布局参数
			linearParams.height = 440;// 控件的高强制设成20
		}

		mLlLockDetailUnlocking = view.findViewById(R.id.ll_lock_detail_unlocking);
		mLlLockDetailUnlock = view.findViewById(R.id.ll_lock_detail_unlock);
		mLlLockDetailLock = view.findViewById(R.id.ll_lock_detail_lock);

		mRefreshLayout = view.findViewById(R.id.refresh_layout);
		mRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
		mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				mRefreshLayout.post(new Runnable() {
					@Override
					public void run() {
						mRefreshLayout.setRefreshing(true);
						doRefresh();
					}
				});
			}
		});

		mGridView = view.findViewById(R.id.gv_lock_detail_action);
		mAdapter = new LockActionAdapter(getActivity(), mActions);
		mGridView.setAdapter(mAdapter);

		mCurKEY = new Key();
		mOpenid = PeachPreference.getOpenid();
	}

	private void initListener() {
		mGridView.setOnItemClickListener(this);
		mIvAddLock.setOnClickListener(this);
		mLlLockDetailUnlocking.setOnClickListener(this);
		mLlLockDetailUnlock.setOnClickListener(this);
		mLlLockDetailLock.setOnClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
		LockAction action = mActions.get(position);
		boolean isEnable = action.isEnable();
		switch (action.getActionType()) {
			case EKEY_MANAGE:
				if (isEnable) {
					//LockManageBluetoothKeyActivity.actionStart(getActivity(), mCurKEY.getLockId(), mCurKEY.isAdmin());
					KeyPwdManageActivity.actionStart(mActivity, mCurKEY, KeyPwdConstant.IType.TYPE_KEY, KeyPwdConstant.IFrom.FROM_LOCK_DETAILS, mLockType);
				}
				break;

			case PASSCODE_MANAGE:
				//todo 密码管理，需要合并到蓝牙钥匙
				if (isEnable) {
					/*LockManagePasswordActivity.actionStart(getActivity(), mCurKEY.getLockId(), mCurKEY.getKeyId(),
							mCurKEY.getLockName(), mCurKEY.getLockMac());*/
					KeyPwdManageActivity.actionStart(mActivity, mCurKEY, KeyPwdConstant.IType.TYPE_PWD, KeyPwdConstant.IFrom.FROM_LOCK_DETAILS, mLockType);
				}
				break;
			case OPERATE_RECORD:
				/*if (isEnable) {
					LockManagePasswordActivity.actionStart(getActivity(), mCurKEY.getLockId(), mCurKEY.getKeyId(),
							mCurKEY.getLockName(), mCurKEY.getLockMac());
				}*/
				LockOperateRecordActivity.actionStart(mActivity, mCurKEY.getLockId(), mCurKEY);
				break;

			case IC_CARDS:
				if (isEnable) {
					KeyPwdManageActivity.actionStart(mActivity, mCurKEY, KeyPwdConstant.IType.TYPE_IC_CARD, KeyPwdConstant.IFrom.FROM_LOCK_DETAILS, mLockType);
				}
				break;

			case FINGRPRINT:
				if (isEnable) {
					KeyPwdManageActivity.actionStart(mActivity, mCurKEY, KeyPwdConstant.IType.TYPE_FINGERPRINT, KeyPwdConstant.IFrom.FROM_LOCK_DETAILS, mLockType);
				}
				break;

			case SETTINGS:
				LockSettingsActivity.actionStart(getActivity(), mCurKEY.getLockMac(), mKeyType, mLockType);
				break;

			default:
				break;
		}
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_lock_detail_add || id == R.id.iv_add_more_device_btn) {
            goToNewActivity(LockAddSelectTypeActivity.class);
        } else if (id == R.id.ll_lock_detail_unlocking) {
            // 开锁
            unlock(1);
        } else if (id == R.id.ll_lock_detail_unlock) {
            // 开锁
            unlock(2);
        } else if (id == R.id.ll_lock_detail_lock) {
            // 闭锁
            lock();
        }
    }

    private boolean isSupportRemoteLock() {
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())) {
			return false;
		}
		boolean isSupportRemoteUnlock = FeatureValueUtil.isSupportFeature(mCurKEY.getLockData(), FeatureValue.GATEWAY_UNLOCK);
		// 远程开锁关闭，闭锁也不允许操作了
		if (!isSupportRemoteUnlock) {
			return false;
		}

		// 同时支持远程开锁/闭锁
		boolean isSupportRemoteLock = DigitUtil.isSupportManualLock(mCurKEY.getSpecialValue()) && mCurKEY.isHasGateway();

		if (!isSupportRemoteLock) {
			return false;
		}

		// 管理员
		if (mCurKEY.isAdmin()) {
			return true;
		}

		/*if (mCurKEY.getKeyRight() == 1 && mCurKEY.isAllowRemoteUnlock()//授权用户，且允许远程开锁
				&& ("110401".equals(mCurKEY.getKeyStatus()) || "110402".equals(mCurKEY.getKeyStatus()))) {//钥匙正常使用或待接收
			return true;
		}*/
		// 永久/限时授权用户、永久普通用户，如果远程开锁打开，默认开锁时先用蓝牙开锁，再用远程开锁。限时普通用户即时远程分开锁的功能开启，也不调用网关
		if (("110401".equals(mCurKEY.getKeyStatus()) || "110402".equals(mCurKEY.getKeyStatus()))) {//钥匙正常使用或待接收
			// 钥匙类型（1限时，2永久，3单次，4循环）
//			if (mKeyType == 2  //永久钥匙，普通、授权用户都可以远程操作
//					|| (mCurKEY.getKeyRight() == 1 && mKeyType == 1)) {//授权用户,限时钥匙也可以远程操作
				return true;
//			}
		}

		return false;
	}

	private boolean isSupportRemoteUnlock() {
		boolean isSupportRemoteUnlock = FeatureValueUtil.isSupportFeature(mCurKEY.getLockData(), FeatureValue.GATEWAY_UNLOCK)  && mCurKEY.isHasGateway();

		// 不支持远程开锁
		if (!isSupportRemoteUnlock) {
			return false;
		}

		// 管理员
		if (mCurKEY.isAdmin()) {
			return true;
		}

		/*if (mCurKEY.getKeyRight() == 1 && mCurKEY.isAllowRemoteUnlock()//授权用户，且允许远程开锁
				&& ("110401".equals(mCurKEY.getKeyStatus()) || "110402".equals(mCurKEY.getKeyStatus()))) {//钥匙正常使用或待接收
			return true;
		}*/
		// 永久/限时授权用户、永久普通用户，如果远程开锁打开，默认开锁时先用蓝牙开锁，再用远程开锁。限时普通用户即时远程分开锁的功能开启，也不调用网关
		if (("110401".equals(mCurKEY.getKeyStatus()) || "110402".equals(mCurKEY.getKeyStatus()))) {//钥匙正常使用或待接收
			// 钥匙类型（1限时，2永久，3单次，4循环）
//			if (mKeyType == 2  //永久钥匙，普通、授权用户都可以远程操作
//					|| (mCurKEY.getKeyRight() == 1 && mKeyType == 1)) {//授权用户,限时钥匙也可以远程操作
				return true;
//			}
		}

		return false;
	}

	private void showUnLocking() {
		PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator.name(), R.string.unlocking);
	}

	private void showLocking() {
		PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator.name(), R.string.locking);
	}

	private void closeUnLockingOrLocking() {
		PeachLoader.stopLoading();
	}

	private void exeRemoteLock() {
		remoteLock();
	}

	private void exeRemoteUnlock(boolean tryBle, final int operateType) {
		closeUnLockingOrLocking();
		// 网关远程开锁
		Resources res = getResources();
		DialogUtil.showCommonDialog(getActivity(), res.getString(R.string.note_unlock_remotely),
				res.getString(R.string.note_unlock_remotely_msg),
				res.getString(R.string.unlock), res.getString(R.string.cancel),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						if (isNetEnableWithToast())
							remoteUnlock(tryBle, operateType);
					}
				}, null);
	}

	/**
	 * 远程闭锁
	 */
	private void remoteLock() {
		showLocking();
		RestClient.builder()
				.url(Urls.GATEWAY_REMOTE_LOCK)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mCurKEY.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						closeUnLockingOrLocking();
						PeachLogger.d("GATEWAY_REMOTE_LOCK", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.operation_success);
						} else if (code == 951) {
							toast(R.string.note_gateway_donot_exists);
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				closeUnLockingOrLocking();
			}
		}).error(new IError() {
			@Override
			public void onError(int code, String msg) {
				closeUnLockingOrLocking();
			}
		})
				.build()
				.post();
	}

	/**
	 * 远程开锁
	 */
	private void remoteUnlock(boolean tryBle, final int operateType) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                kjxRequestBleConnectPermissionStartConnect(new PermissionListener() {
                    @Override
                    public void onGranted() {
                        // 开始带重试机制的开锁
                        unlockWithRetry(operateType, 0, false);
                    }

                    @Override
                    public void onDenied(List<String> deniedPermissions) {
                        // 权限被拒绝处理
                    }
                });
            }
        };
		showUnLocking();
		RestClient.builder()
				.url(Urls.GATEWAY_REMOTE_UNLOCK)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mCurKEY.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						closeUnLockingOrLocking();
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							// 展示最近开锁成功的时间
							long curTimeMillis = DateUtil.getCurTimeMillis();
                            stopLockingAnimation(operateType);
							PeachPreference.setLastUnlockTimeAndType(mCurKEY.getLockId(), curTimeMillis, 2);
							showLastUnLockTime(DateUtil.getDateToString(curTimeMillis, DateUtil.DATE_TIME_PATTERN_1), 2);
							toast(R.string.operation_success);
						} else {
                            if (code == 951) {
                                toast(R.string.note_gateway_donot_exists);
                            } else {
                                toastBackendError(code, R.string.operation_fail);
                            }
                            if (tryBle) {
                                task.run();
                            }
                        }
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				closeUnLockingOrLocking();
                if (tryBle) {
                    task.run();
                }
			}
		}).error(new IError() {
			@Override
			public void onError(int code, String msg) {
				closeUnLockingOrLocking();
                if (tryBle) {
                    task.run();
                }
			}
		})
				.build()
				.post();
	}

	/**
	 * 开锁
	 */
    private void unlock(final int operateType) {
        PeachLogger.d("[ACTION] 开锁 lockId=" + (null != mCurKEY ? mCurKEY.getLockId() : 0) + " type=" + operateType);
        if (isBleEnableWithoutToast()) {
            mIsUnlockCalled = false;
            showUnLocking();
            startLockingAnimation(operateType);
            setUnlockCallback(operateType);
            Logger.t(BaseApplication.TAG).e("操作unlock");

            if (mCurKEY.getLockId() < 0) {
                Logger.t(BaseApplication.TAG).e("操作unlock sPPLOCK");
                if (sPPLOCK.isConnected(mCurKEY.getLockMac())) {
                    Logger.t(BaseApplication.TAG).e("操作unlock sPPLOCK adminUnlock");
                    sPPLOCK.adminUnlock(PeachPreference.readUserId(), String.valueOf(mCurKEY.getLockId()),
                            String.valueOf(mCurKEY.getKeyId()), mCurKEY.getK1());
                } else {
                    Logger.t(BaseApplication.TAG).e("操作unlock sPPLOCK startLockActionScan");
                    startLockActionScan();
                }
            } else {
                if (isSupportRemoteUnlock() && mCurKEY.getUnlockType() == 1) {
                    exeRemoteUnlock(true, operateType);
                } else {
                    kjxRequestBleConnectPermissionStartConnect(new PermissionListener() {
                        @Override
                        public void onGranted() {
                            // 开始带重试机制的开锁
                            unlockWithRetry(operateType, 0, true);
                        }

                        @Override
                        public void onDenied(List<String> deniedPermissions) {
                            // 权限被拒绝处理
                        }
                    });
                }
            }
        } else {
            if (!isSupportRemoteUnlock()) {
                toast(R.string.enable_bluetooth);
                return;
            }
            exeRemoteUnlock(false, operateType);
        }
    }

    /**
     * 带重试机制的开锁
     * @param operateType 操作类型
     * @param retryCount 当前已重试次数
     */
    private void unlockWithRetry(final int operateType, final int retryCount, final boolean tryRemote) {
        final int MAX_RETRY = 4;

        TTLockClient.getDefault().controlLock(ControlAction.UNLOCK, mCurKEY.getLockData(),
                mCurKEY.getLockMac(), new ControlLockCallback() {

                    @Override
                    public void onControlLockSuccess(ControlLockResult controlLockResult) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                closeUnLockingOrLocking();
                                mIsUnlockCalled = true;
                                stopLockingAnimation(operateType);
                                addLockOperateLog(1);
                                if (mKeyType == 3) {
                                    deleteOneTimeEkey();
                                }
                                boolean isRemind = PeachPreference.isShowLockingReminder(PeachPreference.readUserId());
                                if (isRemind) {
                                    toast(R.string.unlocked_successfully);
                                    DeviceUtil.vibrate(getActivity(), 500);
                                }
                                long curTimeMillis = DateUtil.getCurTimeMillis();
                                showLastUnLockTime(DateUtil.getDateToString(curTimeMillis, DateUtil.DATE_TIME_PATTERN_1), 1);
                                PeachPreference.setLastUnlockTimeAndType(mCurKEY.getLockId(), curTimeMillis, 1);
                                clearDeleteUnknownIfNeeded();
                                getLockBattery();
                            }
                        });
                    }

                    @Override
                    public void onFail(LockError error) {
                        if (getActivity() == null) return;

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 如果是需要重试的错误码，且未超过最大重试次数
                                if (Objects.equals(error.getErrorCode(), "0x401") && retryCount < MAX_RETRY) {
                                    Logger.t(BaseApplication.TAG).e("开锁失败，错误码：" + error.getErrorCode() +
                                            "，第" + (retryCount + 1) + "次重试，间隔1.5秒");
                                    toast(getString(R.string.device_init_loading));

                                    // 延迟1秒后重试
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            unlockWithRetry(operateType, retryCount + 1, tryRemote);
                                        }
                                    }, 1500);
                                } else {
                                    // 重试次数用尽或其他错误，执行失败处理
//                                    if (retryCount >= MAX_RETRY) {
//                                        Logger.t(BaseApplication.TAG).e("开锁失败，已达最大重试次数：" + MAX_RETRY);
//                                        toast("开锁失败，请稍后重试");
//                                    }

                                    mIsUnlockCalled = true;
                                    stopLockingAnimation(operateType);

                                    if (isSupportRemoteUnlock() && tryRemote) {
                                        exeRemoteUnlock(false, operateType);
                                    } else {
                                        closeUnLockingOrLocking();
                                        toastFail();
                                    }
                                }
                            }
                        });
                    }
                });
    }

	/**
	 * @param operateType 操作类型
	 *                    1 大图标开锁（锁只支持 APP 开锁）
	 *                    2 小图标开锁（锁同时支持 APP 开锁、闭锁）
	 *                    3 小图标闭锁（锁同时支持 APP 开锁、闭锁）
	 */
	/**
	 * 状态B(删除结果未知)下开锁成功 = 原钥匙仍能开 = 锁未被删除,清除待确认记录、恢复正常管理。
	 */
	private void clearDeleteUnknownIfNeeded() {
		String userId = PeachPreference.readUserId();
		int lockId = mCurKEY.getLockId();
		if (DeviceDeleteState.isResultUnknown(userId, lockId)) {
			DeviceDeleteState.clear(userId, lockId);
			PeachLogger.d("[ACTION] 删除待确认设备原钥匙开锁成功,恢复正常 lockId=" + lockId);
		}
	}

	private void setUnlockCallback(final int operateType) {
		if (mCurKEY.getLockId() < 0) {
			Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback 开锁成功");
			MyApplication.pplBleSession.setOperation(LockOperation.ADMIN_UNLOCK);
			MyApplication.pplBleSession.setLockMac(mCurKEY.getLockMac());
			MyApplication.pplBleSession.setAdmin(mCurKEY.isAdmin());
			MyApplication.pplBleSession.setmILockUnlock(new MHILockUnlock() {
				@Override
				public void onUnlockSuccess(final int battery) {
					Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockSuccess1");
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockSuccess2");
								closeUnLockingOrLocking();
								mIsUnlockCalled = true;
								stopLockingAnimation(operateType);
								addLockOperateLog(1);//添加开锁记录
								if (mKeyType == 3) {//如果是一次性钥匙，开锁成功后手动删除
									deleteOneTimeEkey();
								}
								boolean isRemind = PeachPreference.isShowLockingReminder(PeachPreference.readUserId());
								if (isRemind) {
									// 开锁成功提示
									toast(R.string.unlocked_successfully);
									DeviceUtil.vibrate(getActivity(), 500);
								}

								// 展示最近开锁成功的时间
								long curTimeMillis = DateUtil.getCurTimeMillis();
								showLastUnLockTime(DateUtil.getDateToString(curTimeMillis, DateUtil.DATE_TIME_PATTERN_1), 1);
								PeachPreference.setLastUnlockTimeAndType(mCurKEY.getLockId(), curTimeMillis, 1);
								clearDeleteUnknownIfNeeded();
								requestUploadLockBattery(battery);
							}
						});
					}
				}

				@Override
				public void onUnlockFail() {
					Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockFail1");
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockFail2");
								mIsUnlockCalled = true;
								stopLockingAnimation(operateType);
								closeUnLockingOrLocking();
								toastFail();
							}
						});
					}
				}

				@Override
				public void onUnlockFinish() {
					Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockFinish1");
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Logger.t("PPLock").e("操作unlock sPPLOCK setUnlockCallback onUnlockFinish2  mIsUnlockCalled = " + mIsUnlockCalled);
								if (!mIsUnlockCalled) {
									mIsUnlockCalled = true;
									stopLockingAnimation(operateType);
									closeUnLockingOrLocking();
									toast(R.string.note_make_sure_lock_nearby);
								}
							}
						});
					}
				}
			});
		}
	}

	/**
	 * @param timeStr
	 * @param type    1--蓝牙，2--远程
	 */
	private void showLastUnLockTime(String timeStr, int type) {
		// 展示最近开锁成功的时间
		mTvUnLockLastTime.setText(timeStr);
		mTvUnLockLastTime.setVisibility(View.VISIBLE);
		mTvUnLockLastMsg.setVisibility(View.VISIBLE);
		if (type == 1) {
			mTvUnLockLastMsg.setText(R.string.latest_unlocked_by_you);
		} else if (type == 2) {
			mTvUnLockLastMsg.setText(R.string.latest_unlocked_by_you_remotely);
		} else {
			mTvUnLockLastTime.setVisibility(View.GONE);
			mTvUnLockLastMsg.setVisibility(View.GONE);
		}
	}

	private WeakHashMap<String, Object> getParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();

		params.put("userId", PeachPreference.readUserId());
		params.put("keyId", mCurKEY.getKeyId());

		// Y同时删除他所发送的钥匙，N则不。（注：只适用于授权用户，普通用户可传空）
		if (mCurKEY.getKeyRight() == 1 || mCurKEY.isAdmin()) {
			params.put("delType", "Y");
		}

		return params;
	}

	/**
	 * 删除一次性钥匙
	 */
	private void deleteOneTimeEkey() {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_DELETE)
				.params(getParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_DELETE", response);

						if (VAL_TAG_FRAGMENT.equals(mTag)) {
							//requestUserLockInfo(mKeyId);
							requestDeviceData();
						} else if (VAL_TAG_ACTIVITY.equals(mTag)) {
							if (getActivity() != null) {
								getActivity().finish();
							}
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 闭锁
	 */
	private void lock() {
		PeachLogger.d("[ACTION] 关锁 lockId=" + (null != mCurKEY ? mCurKEY.getLockId() : 0));
		if (isBleEnableWithoutToast()) {
			mIsLockCalled = false;
			showLocking();
			startLockingAnimation(3);
			setLockCallback();
			if (mCurKEY.getLockId() < 0) {
				if (sPPLOCK.isConnected(mCurKEY.getLockMac())) {
					sPPLOCK.adminLock(PeachPreference.readUserId(), String.valueOf(mCurKEY.getLockId()),
							String.valueOf(mCurKEY.getKeyId()), mCurKEY.getK1());
				} else {
					startLockActionScan();
				}

			} else {
                kjxRequestBleConnectPermissionStartConnect(new PermissionListener() {
                    @Override
                    public void onGranted() {
                        TTLockClient.getDefault().controlLock(ControlAction.LOCK, mCurKEY.getLockData(), mCurKEY.getLockMac(),new ControlLockCallback() {
                            @Override
                            public void onControlLockSuccess(ControlLockResult controlLockResult) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            closeUnLockingOrLocking();
                                            mIsLockCalled = true;
                                            stopLockingAnimation(3);
                                            addLockOperateLog(2);//添加闭锁记录
                                            boolean isRemind = PeachPreference.isShowLockingReminder(PeachPreference.readUserId());
                                            if (isRemind) {
                                                // 闭锁成功提示
                                                toast(R.string.locked_successfully);
                                                DeviceUtil.vibrate(getActivity(), 500);
                                            }
                                            getLockBattery();
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onFail(LockError error) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            closeUnLockingOrLocking();
                                            mIsLockCalled = true;
                                            stopLockingAnimation(3);
                                            toastFail();
                                        }
                                    });
                                }
                            }
                        });
                    }

                    @Override
                    public void onDenied(List<String> deniedPermissions) {

                    }
                });
			}
		} else {
			if (!isSupportRemoteLock()) {
				toast(R.string.enable_bluetooth);
				return;
			}
			if (mCurKEY.getLockId() > 0) {
				exeRemoteLock();
			}
		}
	}

	private void setLockCallback() {
		if (mCurKEY.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.ADMIN_LOCK);
			MyApplication.pplBleSession.setLockMac(mCurKEY.getLockMac());
			MyApplication.pplBleSession.setmILockLock(new MHILockLock() {
				@Override
				public void onLockSuccess(final int battery) {
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								closeUnLockingOrLocking();
								mIsLockCalled = true;
								stopLockingAnimation(3);
								addLockOperateLog(2);//添加闭锁记录
								boolean isRemind = PeachPreference.isShowLockingReminder(PeachPreference.readUserId());
								if (isRemind) {
									// 闭锁成功提示
									toast(R.string.locked_successfully);
									DeviceUtil.vibrate(getActivity(), 500);
								}
								requestUploadLockBattery(battery);
							}
						});
					}
				}

				@Override
				public void onLockFail() {
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								closeUnLockingOrLocking();
								mIsLockCalled = true;
								stopLockingAnimation(3);
								toastFail();
							}
						});
					}
				}

				@Override
				public void onLockFinish() {
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (!mIsLockCalled) {
									closeUnLockingOrLocking();
									mIsLockCalled = true;
									stopLockingAnimation(3);
									toast(R.string.note_make_sure_lock_nearby);
								}
							}
						});
					}
				}
			});
		}
	}

	/**
	 * 请求服务器，上传锁电量
	 */
	private void requestUploadLockBattery(final int battery) {
		RestClient.builder()
				.url(Urls.LOCK_UPLOAD_BATTERY)
				.params("lockId", mCurKEY.getLockId())
				.params("electricQuantity", String.valueOf(battery))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_UPLOAD_BATTERY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							CURRENT_KEY.setElectricQuantity(battery);
							mCurKEY.setElectricQuantity(battery);
							refreshBattery();
							if (battery <= 20) {
								DialogUtil.showCommonDialog(getActivity(), null,
										getString(R.string.note_low_battery), getString(R.string.ok), null,
										null, null);
							}
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 上传开闭锁操作记录
	 *
	 * @param eventType 事件
	 *                  1：开锁
	 *                  2：闭锁
	 *                  4：IC 卡开锁
	 */
	private void addLockOperateLog(int eventType) {
		LockRecordUploader.getInstance().uploadAppEvent(mCurKEY.getLockId(), mCurKEY.getUserKeyId(),
				eventType, (uploaded, saved) -> {
					if (!uploaded && isAdded() && getActivity() != null && !getActivity().isFinishing()) {
						toast(saved ? R.string.record_upload_queued_after_operation
								: R.string.record_upload_failed_after_operation);
					}
				});
	}

	/**
	 * 进行数据刷新操作
	 */
	public void doRefresh() {
		//requestUserLockInfo(mKeyId);
		requestDeviceData();
//		getLockBattery();
	}

	/**
	 * 通过 SDK 读取锁电量
	 */
	private void getLockBattery() {
		if (mCurKEY != null) {
			if (mCurKEY.getLockId() < 0) {
				if (sPPLOCK.isConnected(mCurKEY.getLockMac())) {
					setGetBatteryCallback();
					sPPLOCK.getBatteryLevel(0);
				} else {
					setGetBatteryCallback();
					startLockActionScan();
				}

			} else {
                kjxRequestBleConnectPermissionStartConnect(new PermissionListener() {
                    @Override
                    public void onGranted() {
                        TTLockClient.getDefault().getBatteryLevel(mCurKEY.getLockData(), mCurKEY.getLockMac(), new GetBatteryLevelCallback() {
                            @Override
                            public void onGetBatteryLevelSuccess(int i) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        requestUploadLockBattery(i);
                                    }
                                });
                            }

                            @Override
                            public void onFail(LockError lockError) {
                            }
                        });
                    }

                    @Override
                    public void onDenied(List<String> deniedPermissions) {

                    }
                });

			}

		}
	}


	private void setGetBatteryCallback() {
		if (mCurKEY != null) {
			if (mCurKEY.getLockId() < 0) {
				MyApplication.pplBleSession.setOperation(LockOperation.GET_BATTERY_LEVEL);
				MyApplication.pplBleSession.setLockMac(mCurKEY.getLockMac());
				MyApplication.pplBleSession.setmILockGetBattery(new MHILockGetBattery() {
					@Override
					public void onSuccess(final int battery) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								stopLoading();
								requestUploadLockBattery(battery);
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

	}

	private void requestDeviceData() {
		//  独立详情页面
		if (VAL_TAG_ACTIVITY.equals(mTag)) {
			requestUserLockInfo(mKeyId);
		}
		// 主页面中
		else {
			if (!TextUtils.isEmpty(mHomeId)) {
				requestDeviceListForGroup(mHomeId);
			} else {
				// 还没拿到当前空间(如首页数据尚未加载完)时无从刷新。
				// 此处必须关掉调用方已经打开的全局 loading,否则遮罩没人关闭,首页会一直转圈。
				PeachLoader.stopLoading();
			}
		}
	}

	private void requestDeviceListForGroup(String homeId) {
		RestClient.builder()
				.url(Urls.LOCK_GROUP_GET_DEVICE)
				.loader(getActivity())
				.params("userId", PeachPreference.readUserId())
				.params("homeId", homeId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						try {
							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
							if (code == 200) {

								List<HomeDevice> datas = GsonUtil.fromJson(result.getJSONArray("data").toJSONString(), new TypeToken<List<HomeDevice>>() {
								});

								// 过滤一下非法数据
								List<HomeDevice> tempDatas = null;
								for (HomeDevice device : datas) {
									if (null == tempDatas) {
										tempDatas = new ArrayList<>();
									}
									if (!TextUtils.isEmpty(device.getName())) {
										tempDatas.add(device);
									}
								}

								// 没有设备,显示添加锁UI
								if (CollectionUtil.isEmpty(tempDatas)) {
									setLockInfoVisible(SHOW_DEVICE_ADD);
								}
								// 有一个设备，请求详情
							/*else if (datas.size() == 1){
								mKeyId = datas.get(0).getDeviceId();
								requestUserLockInfo(mKeyId);
							}*/
								// 显示设备列表
								else {
									setLockInfoVisible(SHOW_DEVICE_LIST);
                                    if(mDeviceList.isEmpty() && tempDatas.size() == 1){
                                        HomeDevice device = tempDatas.get(0);
                                        LockDetailActivity.actionStart(requireContext(), device.getDeviceId(), device.getName());
                                    }
									mDeviceList.clear();
									mDeviceList.addAll(tempDatas);
									mDeviceListAdapter.notifyDataSetChanged();
								}
							}
						}catch (Exception e){
							e.printStackTrace();
						}finally {
							PeachLoader.stopLoading();
							if (mRefreshLayout != null) {
								mRefreshLayout.setRefreshing(false);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLoader.stopLoading();
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLoader.stopLoading();
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.isCloseLoadDialog(false)
				.build()
				.get();
	}

	/**
	 * 请求锁详情信息
	 */
	public void requestUserLockInfo(String lockId) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("userId", PeachPreference.readUserId());
		if (!StringUtil.isBlank(lockId)) {
			params.put("lockId", lockId);
		}
		RestClient.builder()
				.url(Urls.LOCK_GET_BASEINFO)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLoader.stopLoading();
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
						PeachLogger.d("USER_LOCK_INFO", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 920) {//没有锁信息
							PeachPreference.setAccountLockNum(PeachPreference.readUserId(), 0);
							setLockInfoVisible(SHOW_DEVICE_ADD);
						} else if (code == 200) {
							JSONObject lockInfo = result.getJSONObject("data");
							if (lockInfo != null && !lockInfo.isEmpty()) { //有锁信息，显示对应的锁UI或锁列表UI
								setLockInfoVisible(SHOW_LOCK_INFO);
								int lockId = lockInfo.getInteger("lockId");
								mCurKEY.setLockId(lockId);
								if (mCurKEY.getLockId() < 0) {
									parseMHTLockInfo(lockInfo);
								} else {
									try{
										parseKJXLockInfo(lockInfo);
									}catch (Exception e){
										e.printStackTrace();
									}
								}
								showLastUnLockTime(DateUtil.getDateToString(PeachPreference.getLastUnlockTime(mCurKEY.getLockId()),
										DateUtil.DATE_TIME_PATTERN_1), PeachPreference.getLastUnlockType(mCurKEY.getLockId()));
							} else { //无锁信息，显示添加锁UI
								PeachPreference.setAccountLockNum(PeachPreference.readUserId(), 0);
								setLockInfoVisible(SHOW_DEVICE_ADD);
							}
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLoader.stopLoading();
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLoader.stopLoading();
						if (mRefreshLayout != null) {
							mRefreshLayout.setRefreshing(false);
						}
					}
				})
				.build()
				.get();
	}

    public void requestUnlockType() {
        WeakHashMap<String, Object> params = new WeakHashMap<>();
        params.put("userId", PeachPreference.readUserId());
        if (!StringUtil.isBlank(mKeyId)) {
            params.put("lockId", mKeyId);
        }
        params.put("unlockType", mCurKEY.getUnlockType() == 1 ? 2 : 1);
        RestClient.builder()
                .url(Urls.LOCK_UNLOCK_TYPE_POST)
                .params(params)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLoader.stopLoading();
                        if (mRefreshLayout != null) {
                            mRefreshLayout.setRefreshing(false);
                        }
                        PeachLogger.d("LOCK_UNLOCK_TYPE_POST", response);
                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 920) {//没有锁信息
                            PeachPreference.setAccountLockNum(PeachPreference.readUserId(), 0);
                            setLockInfoVisible(SHOW_DEVICE_ADD);
                        } else if (code == 200) {
                            doRefresh();
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        PeachLoader.stopLoading();
                        if (mRefreshLayout != null) {
                            mRefreshLayout.setRefreshing(false);
                        }
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        PeachLoader.stopLoading();
                        if (mRefreshLayout != null) {
                            mRefreshLayout.setRefreshing(false);
                        }
                    }
                })
                .build()
                .post();
    }

	private void setLockInfoVisible(int showType) {
		mLlLockAdd.setVisibility(View.GONE);
		mFlLockInfo.setVisibility(View.GONE);
		mFlDeviceList.setVisibility(View.GONE);
		mLlOutOfDate.setVisibility(View.GONE);
		switch (showType) {
			case SHOW_DEVICE_ADD:
				mLlLockAdd.setVisibility(View.VISIBLE);
				break;
			case SHOW_LOCK_INFO:
				mFlLockInfo.setVisibility(View.VISIBLE);
				break;
			case SHOW_DEVICE_LIST:
				mFlDeviceList.setVisibility(View.VISIBLE);
				break;
			case SHOW_OUT_OF_DATE:
				mLlOutOfDate.setVisibility(View.VISIBLE);
				break;
		}
	}

    private void parseKJXLockInfo(@NonNull JSONObject lockInfo) {
        readTimezoneNotice(lockInfo);
        // 科技侠的锁id
        int lockId = JSONSafe.getInt(lockInfo, "lockId", 0);

        // lockVersion 特殊处理：可能是 JSONObject 或 String
        String lockVersion = "";
        Object versionObj = JSONSafe.get(lockInfo, "lockVersion");
        if (versionObj instanceof JSONObject) {
            lockVersion = String.valueOf(versionObj);
        } else if (versionObj instanceof String) {
            lockVersion = (String) versionObj;
        }

        // 锁的蓝牙名称
        String lockName = JSONSafe.getString(lockInfo, "name", "");
        // 锁别名
        String lockAlias = JSONSafe.getString(lockInfo, "alias", "");
        // 锁mac地址
        String lockMac = JSONSafe.getString(lockInfo, "mac", "");
        // 锁电量
        int electricQuantity = JSONSafe.getInt(lockInfo, "electricQuantity", 0);
        // 锁开门标志位
        int lockFlagPos = JSONSafe.getInt(lockInfo, "flagPos", 0);

        // 管理员钥匙会有，锁的管理员密码，锁管理相关操作需要携带，校验管理员权限
        String adminPwd = JSONSafe.getString(lockInfo, "adminPwd", "");
        // 锁开门的关键信息，开门用的
        String lockKey = JSONSafe.getString(lockInfo, "key", "");
        // 管理员键盘密码
        String noKeyPwd = JSONSafe.getString(lockInfo, "noKeyPwd", "");
        // 密码数据，用于生成密码，SDK提供
        String pwdInfo = JSONSafe.getString(lockInfo, "pwdInfo", "");
        // 时间戳，用于初始化密码数据
        long timestamp = JSONSafe.getLong(lockInfo, "timestamp", 0);
        // Aes加解密key
        String aesKeyStr = JSONSafe.getString(lockInfo, "aesKey", "");

        long startDate = JSONSafe.getLong(lockInfo, "startDate", 0) * 1000;
        long endDate = JSONSafe.getLong(lockInfo, "endDate", 0) * 1000;

        // 锁特征值，用于表示锁支持的功能
        int specialValue = JSONSafe.getInt(lockInfo, "specialValue", 0);
        // 锁所在时区和UTC时区时间的差数，单位milliseconds
        int timezoneRawOffset = JSONSafe.getInt(lockInfo, "timezoneRawOffSet", 0);
        int keyRight = JSONSafe.getInt(lockInfo, "keyRight", 0);

        boolean isAllowAllPermissions = JSONSafe.getBoolean(lockInfo, "allowAllPermissions", false);
        boolean isAllowRemoteUnlock = JSONSafe.getBoolean(lockInfo, "allowRemoteUnlock", false);
        boolean isAllowSyncBattery = JSONSafe.getBoolean(lockInfo, "allowSyncBattery", false);
        boolean isAllowCalibrateTime = JSONSafe.getBoolean(lockInfo, "allowCalibrateTime", false);

        // 产品型号（用于锁固件升级）
        String modelNum = JSONSafe.getString(lockInfo, "modelNum", "");
        // 硬件版本号（用于锁固件升级）
        String hardwareRevision = JSONSafe.getString(lockInfo, "hardwareRevision", "");
        // 固件版本号（用于锁固件升级）
        String firmwareRevision = JSONSafe.getString(lockInfo, "firmwareRevision", "");

        // 初始化时间
        long initDate = JSONSafe.getLong(lockInfo, "initDate", 0);
        // 管理员钥匙id
        int keyId = JSONSafe.getInt(lockInfo, "keyId", 0);
        // 用户钥匙id，普通用户用于删除钥匙
        int userKeyId = JSONSafe.getInt(lockInfo, "userKeyId", 0);
        // 锁状态（0删除，1正常）
        int status = JSONSafe.getInt(lockInfo, "status", 0);
        // 钥匙的状态（110401：正常使用，110402：待接收，110405：已冻结，110408：已删除，110410：已重置,110500：已过期）
        String keyStatus = JSONSafe.getString(lockInfo, "keyStatus", "");
        // 协议类型
        int protocolType = JSONSafe.getInt(lockInfo, "protocolType", 0);
        // 锁版本信息
        int protocolVersion = JSONSafe.getInt(lockInfo, "protocolVersion", 0);
        // 场景
        int scene = JSONSafe.getInt(lockInfo, "scene", 0);
        // 应用商
        int orgId = JSONSafe.getInt(lockInfo, "orgId", 0);
        // 公司
        int groupId = JSONSafe.getInt(lockInfo, "groupId", 0);
        // true为管理员，false否
        boolean isAdmin = JSONSafe.getBoolean(lockInfo, "isAdmin", false);
        boolean hasGateway = JSONSafe.getBoolean(lockInfo, "hasGateway", false);
        // 场景，默认2
        int unlockType = JSONSafe.getInt(lockInfo, "unlockType", 2);

        // v3
        String lockData = JSONSafe.getString(lockInfo, "lockData", "");
        mCurKEY.setLockData(lockData);
        mCurKEY.setUserId(PeachPreference.readUserId());
        mCurKEY.setLockId(lockId);
        mCurKEY.setKeyId(keyId);
        mCurKEY.setUserKeyId(userKeyId);

        // 封装lockVersion信息，蓝牙开锁/闭锁需要
        // {"lockId":2118210,"protocolType":5,"protocolVersion":3,"scene":2,"groupId":10,"orgId":32,"logoUrl":null,"showAdminKbpwdFlag":null}
        JSONObject lockVersionObj = new JSONObject();
        lockVersionObj.put("lockId", lockId);
        lockVersionObj.put("protocolType", protocolType);
        lockVersionObj.put("protocolVersion", protocolVersion);
        lockVersionObj.put("scene", scene);
        lockVersionObj.put("groupId", groupId);
        lockVersionObj.put("orgId", orgId);
        lockVersionObj.put("logoUrl", null);
        lockVersionObj.put("showAdminKbpwdFlag", null);
        lockVersion = String.valueOf(lockVersionObj);

        JSONObject homeObj = JSONSafe.getJSONObject(lockInfo, "home");
        if (null != homeObj) {
            Home home = GsonUtil.fromJson(homeObj.toJSONString(), Home.class);
            mCurKEY.setHome(home);
        }

        mCurKEY.setStatus(status);
        mCurKEY.setKeyStatus(keyStatus);
        mCurKEY.setLockVersion(lockVersion);
        mCurKEY.setLockName(lockName);
        mCurKEY.setLockAlias(lockAlias);
        mCurKEY.setLockMac(lockMac);
        mCurKEY.setElectricQuantity(electricQuantity);
        mCurKEY.setLockFlagPos(lockFlagPos);
        mCurKEY.setAdminPwd(adminPwd);
        mCurKEY.setLockKey(lockKey);
        mCurKEY.setNoKeyPwd(noKeyPwd);
        mCurKEY.setPwdInfo(pwdInfo);
        mCurKEY.setTimestamp(timestamp);
        mCurKEY.setAesKeyStr(aesKeyStr);
        mCurKEY.setStartDate(startDate);
        mCurKEY.setEndDate(endDate);

        // startDate有效开始时间，0是永久有效 (时间戳)
        // endDate 失效时间，0是永久有效，格式(时间戳)
        // 钥匙类型（1限时，2永久，3单次，4循环）
        // 目前只有1限时、2永久
        int keyType = JSONSafe.getInt(lockInfo, "keyType", 1);
        mCurKEY.setKeyType(keyType);
        mKeyType = keyType;

        mCurKEY.setSpecialValue(specialValue);
        mCurKEY.setTimezoneRawOffset(timezoneRawOffset);
        mCurKEY.setKeyRight(keyRight);
        mCurKEY.setModelNum(modelNum);
        mCurKEY.setHardwareRevision(hardwareRevision);
        mCurKEY.setFirmwareRevision(firmwareRevision);
        mCurKEY.setAllowAllPermissions(isAllowAllPermissions);
        mCurKEY.setAllowRemoteUnlock(isAllowRemoteUnlock);
        mCurKEY.setAllowSyncBattery(isAllowSyncBattery);
        mCurKEY.setAllowCalibrateTime(isAllowCalibrateTime);
        mCurKEY.setHasGateway(hasGateway);
        mCurKEY.setUnlockType(unlockType);
        mCurKEY.isAdmin(isAdmin);
        CURRENT_KEY = mCurKEY;

        if (null != mActivity && !mActivity.isFinishing()) {
            refreshLockActionUI();
        }

        // （后台静默读取，不提示用户）读一下锁时间，备用
        readKJXLockTimeBackground();
    }

	private void parseMHTLockInfo(@NonNull JSONObject lockInfo) {
		readTimezoneNotice(lockInfo);
		int lockId = lockInfo.getInteger("lockId");//科技侠的锁id
		String lockName = lockInfo.getString("name");//锁的蓝牙名称
		String lockAlias = lockInfo.getString("alias");//锁别名
		String lockMac = lockInfo.getString("mac");//锁mac地址
		int userKeyId = lockInfo.getInteger("userKeyId");//用户钥匙id，普通用户用于删除钥匙
		int electricQuantity = lockInfo.getInteger("electricQuantity");//锁电量
		String noKeyPwd = "";
		if (lockInfo.containsKey("noKeyPwd"))
			noKeyPwd = lockInfo.getString("noKeyPwd");//管理员键盘密码
		long timestamp = lockInfo.getLong("timestamp");//时间戳，用于初始化密码数据
		String aesKeyStr = lockInfo.getString("aesKey");//Aes加解密key
		long startDate = lockInfo.getLong("startDate") * 1000;
		long endDate = lockInfo.getLong("endDate") * 1000;
		int specialValue = lockInfo.getInteger("specialValue");//锁特征值，用于表示锁支持的功能
		int timezoneRawOffset = lockInfo.getInteger("timezoneRawOffSet");//锁所在时区和UTC时区时间的差数，单位milliseconds
		int keyRight = lockInfo.containsKey("keyRight") ? lockInfo.getInteger("keyRight") : 0;
		String modelNum = lockInfo.getString("modelNum");//产品型号（用于锁固件升级）
		String hardwareRevision = lockInfo.getString("hardwareRevision");//硬件版本号（用于锁固件升级）
		String firmwareRevision = lockInfo.getString("firmwareRevision");//固件版本号（用于锁固件升级）

		long initDate = lockInfo.getLong("initDate");//初始化时间
		int keyId = lockInfo.getInteger("keyId");//管理员钥匙id
		int status = lockInfo.getInteger("status");//锁状态（0删除，1正常）
		String keyStatus = lockInfo.getString("keyStatus");//钥匙的状态（110401：正常使用，110402：待接收，110405：已冻结，110408：已删除，110410：已重置,110500:已过期）
		int protocolVersion = lockInfo.getInteger("protocolVersion");//锁版本信息
		boolean isAdmin = lockInfo.getBoolean("isAdmin");//true为管理员，

//        v3
        String lockData = lockInfo.getString("lockData");
        mCurKEY.setLockData(lockData);
		mCurKEY.setUserId(PeachPreference.readUserId());
		mCurKEY.setLockId(lockId);
		mCurKEY.setKeyId(keyId);
		mCurKEY.setUserKeyId(userKeyId);

		// 封装lockVersion信息，蓝牙开锁/闭锁需要
		//{"lockId":2118210,"protocolType":5,"protocolVersion":3,"scene":2,"groupId":10,"orgId":32,"logoUrl":null,"showAdminKbpwdFlag":null}
		JSONObject homeObj = lockInfo.containsKey("home") ? lockInfo.getJSONObject("home") : null;
		if (null != homeObj) {
			Home home = GsonUtil.fromJson(homeObj.toJSONString(), Home.class);
			mCurKEY.setHome(home);
		}

		mCurKEY.setStatus(status);
		mCurKEY.setKeyStatus(keyStatus);
		mCurKEY.setLockName(lockName);
		mCurKEY.setLockAlias(lockAlias);
		mCurKEY.setLockMac(lockMac);
		mCurKEY.setElectricQuantity(electricQuantity);
		mCurKEY.setNoKeyPwd(noKeyPwd);
		mCurKEY.setTimestamp(timestamp);
		mCurKEY.setAesKeyStr(aesKeyStr);
		mCurKEY.setStartDate(startDate);
		mCurKEY.setEndDate(endDate);
		/*// startDate有效开始时间，0是永久有效 (时间戳)
		// endDate 失效时间，0是永久有效，格式(时间戳)
		// 钥匙类型（1限时，2永久，3单次，4循环）
		int keyType = 1;
		if (startDate > 0 && endDate > 0){
			keyType = 1;
		}else {
			keyType = 2;
		}
		// 目前只有1限时、2永久
		mCurKEY.setKeyType(keyType);
		mKeyType = keyType;*/


		int keyType = lockInfo.getInteger("keyType");
		mCurKEY.setKeyType(keyType);
		mKeyType = keyType;
		mCurKEY.setSpecialValue(specialValue);
		mCurKEY.setTimezoneRawOffset(timezoneRawOffset);
		mCurKEY.setKeyRight(keyRight);
		mCurKEY.setModelNum(modelNum);
		mCurKEY.setHardwareRevision(hardwareRevision);
		mCurKEY.setFirmwareRevision(firmwareRevision);
		mCurKEY.isAdmin(isAdmin);
		CURRENT_KEY = mCurKEY;
		if (null != mActivity && !mActivity.isFinishing()) {
			refreshLockActionUI();
		}

		// （后台静默读取，不提示用户）读一下锁时间，备用
		readMHTLockTimeBackground();
	}

	/**
	 * 读一下锁时间，备用
	 * （后台静默读取，不提示用户）
	 */
	private void readKJXLockTimeBackground() {
		//showLoading();
        kjxRequestBleConnectPermissionStartConnect(new PermissionListener() {
            @Override
            public void onGranted() {
                TTLockClient.getDefault().getLockTime(CURRENT_KEY.getLockData(),CURRENT_KEY.getLockMac(), new GetLockTimeCallback() {
                    @Override
                    public void onGetLockTimeSuccess(long lockTimestamp) {
                        CURRENT_KEY.setLockCurrentTime(lockTimestamp);
                    }

                    @Override
                    public void onFail(LockError error) {
                        CURRENT_KEY.setLockCurrentTime(-1);
                    }
                });
            }

            @Override
            public void onDenied(List<String> deniedPermissions) {

            }
        });
	}

	/**
	 * 读一下锁时间，备用
	 * （后台静默读取，不提示用户）
	 */
	private void readMHTLockTimeBackground() {
		setGetTimeCallback();
		if (sPPLOCK.isConnected(CURRENT_KEY.getLockMac())) {
			sPPLOCK.getLockTime();
		} else {
			startLockActionScanNoPermissionToast();
		}
	}

	private void setGetTimeCallback() {
		if (mCurKEY.getLockId() < 0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_LOCK_TIME);
			MyApplication.pplBleSession.setmILockGetTime(new MHILockGetTime() {
				@Override
				public void onSuccess(final long time) {
					CURRENT_KEY.setLockCurrentTime(time);
				}

				@Override
				public void onFail() {
					CURRENT_KEY.setLockCurrentTime(-1);
				}
			});
		}
	}

	/**
	 * 初始化锁的操作按钮界面
	 */
	private void refreshLockActionUI() {
		refreshBattery();
		binding.spacePadlockActions.setVisibility(
				HomeDeviceInfo.isPadlock(mCurKEY.getLockName()) ? View.VISIBLE : View.GONE);
		binding.spacePadlockImage.setVisibility(binding.spacePadlockActions.getVisibility());
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())) {
			mIvLockImg.setImageResource(R.drawable.product_padlock);
			mIvLockImg.setAdjustViewBounds(true);
			mIvLockImg.setMaxHeight((int) (220 * getResources().getDisplayMetrics().density));
		} else if (HomeDeviceInfo.isDeadboltLack(mCurKEY.getLockName())) {
			mIvLockImg.setImageResource(R.drawable.product_deadbolt);
		} else if (HomeDeviceInfo.isKeyBox(mCurKEY.getLockName())) {
			mIvLockImg.setImageResource(R.drawable.product_keybox);
		} else if (HomeDeviceInfo.isKeyBoxK4(mCurKEY.getLockName())) {
			mIvLockImg.setImageResource(R.drawable.product_k4);
		} else if (mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			mIvLockImg.setImageResource(R.drawable.product_moonlock);
			isMHLock = true;
		} else {
			mIvLockImg.setImageResource(R.drawable.product_door_lock);
		}
		mTvLockName.setText(HomeDeviceInfo.getTypeNameByName(mCurKEY.getLockName()));
		if (mActivity instanceof LockDetailActivity) {
			((LockDetailActivity) mActivity).setTitleName(mCurKEY.getLockAlias());
		}
//        网关开关
        binding.llRemoteOpen.setVisibility(mCurKEY.isHasGateway() ? View.VISIBLE : View.GONE);
        binding.scRemoteOpen.setOnCheckedChangeListener(null);
        binding.scRemoteOpen.setChecked(mCurKEY.getUnlockType() == 1);
        binding.scRemoteOpen.setEnabled(true);
        binding.scRemoteOpen.setOnCheckedChangeListener((view, v)-> {
            binding.scRemoteOpen.setEnabled(false);
            requestUnlockType();
        });
		// 删除异常态:进入设备详情时弹恢复弹窗(产品《设备删除一致性与异常恢复》第七节)
		// 弹窗只做引导,不改变页面本身 —— 用户点 Later 后仍要能查旧密码、看开锁记录,
		// 所以入口一律照常展示,不做任何屏蔽。
		final String delUserId = PeachPreference.readUserId();
		final int delLockId = mCurKEY.getLockId();
		if (DeviceDeleteState.isPendingAccount(delUserId, delLockId)) {
			// 状态A:物理删除已确认、等待账号移除 —— 引导用户完成移除
			DeviceDeleteRecoveryDialog.show(getActivity(), true, delUserId, delLockId, null);
		} else if (DeviceDeleteState.isResultUnknown(delUserId, delLockId)) {
			// 状态B:删除结果未知 —— 提示用户靠近设备确认;不拦截操作,允许试开锁,
			// 原钥匙能开(见 setUnlockCallback 里 clearDeleteUnknownIfNeeded)即视为未删、自动恢复正常
			DeviceDeleteRecoveryDialog.show(getActivity(), false, delUserId, delLockId, null);
		}

		mActions.clear();
		if (mCurKEY.isAdmin()) { // 判断是否为管理员
			// 管理员，显示全部按钮
			if (mCurKEY.getLockId() < 0) {
				initMHAdminUI();
			} else {
				initAdminUI();
			}
		} else {
			// 判断钥匙未到生效时间
			if (mCurKEY.getStartDate() > DateUtil.getCurTimeMillis()) {
				mCurKEY.setKeyStatus("110400"); // 还未到生效时间，设置钥匙状态
			}
			if (mCurKEY.getKeyRight() == 1) { // 判断普通用户是否被授权
				// 授权用户，显示全部按钮
				if (mCurKEY.getLockId() < 0) {
					initMHAuthUserUI(mCurKEY.getKeyStatus());
				} else {
					initAuthUserUI(mCurKEY.getKeyStatus());
				}
			} else { // 普通用户，只显示 2 个按钮（操作记录、设置）
				initCommonUserUI(mCurKEY.getKeyStatus());
			}
		}

		// Deadbolt、keybox 用一排 2 个图标；其他门锁用一排 4 个图标（支持指纹、IC卡）
		if (HomeDeviceInfo.isDeadboltLack(mCurKEY.getLockName())
				|| HomeDeviceInfo.isKeyBox(mLockType)
				|| HomeDeviceInfo.isKeyBoxK4(mLockType)
				|| mActions.size() == 2) {
			mGridView.setNumColumns(2);
		} else {
			mGridView.setNumColumns(4);
			mGridView.setBackgroundColor(getResources().getColor(R.color.white));
		}

		mAdapter.notifyDataSetChanged();
	}

	/**
	 * 刷新锁电量显示
	 */
	private void refreshBattery() {
		if (!isAdded()) {
			return;
		}
		int batteryLevel = CURRENT_KEY.getElectricQuantity();

		int imgResInt = R.drawable.bg_battery_green_stroke;
		int icon = R.drawable.battery_green;
		int textColor = R.color.battery_green;
		if (batteryLevel <= 20) {
			imgResInt = R.drawable.bg_battery_red_stroke;
			textColor = R.color.battery_red;
			icon = R.drawable.battery_red;
		} else if (batteryLevel > 20 && batteryLevel <= 50) {
			imgResInt = R.drawable.bg_battery_orange_stroke;
			textColor = R.color.battery_orange;
			icon = R.drawable.battery_orange;
		} else {
			imgResInt = R.drawable.bg_battery_green_stroke;
			textColor = R.color.battery_green;
			icon = R.drawable.battery_green;
		}

		mIvLockBattery.setBackground(getResources().getDrawable(imgResInt));
		mIvLockBattery.setTextColor(getResources().getColor(textColor));
		mIvLockBattery.setText(batteryLevel + "%");
		mIvLockBattery.setIcon(icon, 10);
	}

	private void initMHAdminUI() {
		setUnlockLock();
		enableLockingColorFiltr(true, false, 0);
//		if (DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
//			addFingerprint(true);
//		}

		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
				|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			addFingerprint(true);
			addEKeyManage(true);
			addIcCards(true);
		} else {
			addPasscodeManage(true);
			addEKeyManage(true);
		}

//		if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
//			addIcCards(true);
//		}
		addOperateRecord(true);
		addSettings(true);
	}

	private void initAdminUI() {
		setUnlockLock();
		enableLockingColorFiltr(true, false, 0);
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
				|| DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
			addFingerprint(true);
		}
		addPasscodeManage(true);
		addEKeyManage(true);
		if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
			addIcCards(true);
		}
		addOperateRecord(true);
		addSettings(true);
	}

	private void addPasscodeManage(boolean isEnable) {
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())) {
			return;
		}
		addFunItem(LockAction.LockActionType.PASSCODE_MANAGE,
				R.drawable.lock_action_pwd, R.string.lock_action_pin_code, isEnable);
	}

	private void addEKeyManage(boolean isEnable) {
		addFunItem(LockAction.LockActionType.EKEY_MANAGE,
				R.drawable.lock_action_ekey, R.string.lock_action_digital_key, isEnable);
	}

	private void addOperateRecord(boolean isEnable) {
		addFunItem(LockAction.LockActionType.OPERATE_RECORD,
				R.drawable.lock_action_operate_record, R.string.lock_action_history, isEnable);
	}

	private void addSettings(boolean isEnable) {
		addFunItem(LockAction.LockActionType.SETTINGS,
				R.drawable.lock_action_settings, R.string.lock_action_settings, isEnable);
	}

	private void addIcCards(boolean isEnable) {
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())) {
			return;
		}
		addFunItem(LockAction.LockActionType.IC_CARDS,
				R.drawable.lock_action_ic_card, R.string.lock_action_ic_cards, isEnable);
	}

	private void addFingerprint(boolean isEnable) {
		addFunItem(LockAction.LockActionType.FINGRPRINT,
				R.drawable.lock_action_fingerprint, R.string.lock_action_fingerprint, isEnable);
	}

	private void addFunItem(LockAction.LockActionType actionType, int iconResInt, int titleResInt, boolean isEnable) {
		mActions.add(new LockAction(actionType, iconResInt, titleResInt, isEnable));
	}

	/**
	 * 判断是否支持 开锁 & 闭锁
	 */
	private void setUnlockLock() {
		if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())) {
			mRlUnlocking.setVisibility(View.VISIBLE);
			mLlUnlockLock.setVisibility(View.GONE);
			return;
		}
		if (mCurKEY.getLockId() < 0) {//曼哈顿
			if (mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3)) {
				mRlUnlocking.setVisibility(View.VISIBLE);
				mLlUnlockLock.setVisibility(View.GONE);
			} else if (mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
					|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
				mRlUnlocking.setVisibility(View.VISIBLE);
				mLlUnlockLock.setVisibility(View.GONE);
			}
		} else {
			if (DigitUtil.isSupportManualLock(mCurKEY.getSpecialValue())) { // 同时支持 APP 开锁、闭锁
				mRlUnlocking.setVisibility(View.GONE);
				mLlUnlockLock.setVisibility(View.VISIBLE);
			} else { // 只支持 APP 开锁
				mRlUnlocking.setVisibility(View.VISIBLE);
				mLlUnlockLock.setVisibility(View.GONE);
			}
		}
	}

	/**
	 * 激活/禁用 开闭锁图标，同时设置图标颜色
	 *
	 * @param isEnable       是否激活开闭锁图标
	 * @param setColorFilter 是否设置图标颜色
	 * @param color          图标颜色
	 */
	private void enableLockingColorFiltr(boolean isEnable, boolean setColorFilter, int color) {
		mLlLockDetailUnlocking.setEnabled(isEnable);
		mLlLockDetailUnlock.setEnabled(isEnable);
		mLlLockDetailLock.setEnabled(isEnable);
	}

	/**
	 * @param keyStatus 钥匙状态
	 *                  110401：正常使用
	 *                  110402：待接收
	 *                  110405：已冻结
	 *                  110408：已删除
	 *                  110410：已重置
	 *                  110500：过期
	 */
	private void initAuthUserUI(String keyStatus) {
		Resources res = getResources();
		int colorGray = res.getColor(R.color.text_gray_light);
		int colorGrayParent = res.getColor(R.color.gray_lock_disable);
		switch (keyStatus) {
			case "110401"://正常使用
			case "110402"://待接收
				setUnlockLock();
				enableLockingColorFiltr(true, false, 0);
				if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
						|| DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
					addFingerprint(true);
				}
				addPasscodeManage(true);
				addEKeyManage(true);
				if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
					addIcCards(true);
				}
				addOperateRecord(true);
				addSettings(true);
				break;

			case "110400": // 还未到生效时间
			case "110405": // 已冻结
			case "110500": // 已过期

				setUnlockLock();

				enableLockingColorFiltr(false, true, colorGrayParent);

				if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
						|| DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
					addFingerprint(false);
				}
				addPasscodeManage(false);
				addEKeyManage(false);
				if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
					addIcCards(false);
				}
				addOperateRecord(true);
				addSettings(true);
				break;

			case "110408"://已删除
			case "110410"://已重置
			default:
				setLockInfoVisible(SHOW_DEVICE_ADD);
				break;
		}
	}

	/**
	 * @param keyStatus 钥匙状态
	 *                  110401：正常使用
	 *                  110402：待接收
	 *                  110405：已冻结
	 *                  110408：已删除
	 *                  110410：已重置
	 *                  110500：过期
	 */
	private void initMHAuthUserUI(String keyStatus) {
		Resources res = getResources();
		int colorGray = res.getColor(R.color.text_gray_light);
		int colorGrayParent = res.getColor(R.color.gray_lock_disable);
		switch (keyStatus) {
			case "110401"://正常使用
			case "110402"://待接收
				setUnlockLock();
				enableLockingColorFiltr(true, false, 0);
//				if (DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
//					addFingerprint(true);
//				}
				if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
						|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
						|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
					addFingerprint(true);
					addEKeyManage(true);
					addIcCards(true);
				} else {
					addPasscodeManage(true);
					addEKeyManage(true);
				}
				//addPasscodeManage(true);

//				if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
//					addIcCards(true);
//				}
				addOperateRecord(true);
				addSettings(true);
				break;

			case "110400": // 还未到生效时间
			case "110405": // 已冻结
			case "110500": // 已过期
				setUnlockLock();
				enableLockingColorFiltr(false, true, colorGrayParent);
//				if (DigitUtil.isSupportFingerPrint(mCurKEY.getSpecialValue())) { // 支持指纹
//					addFingerprint(false);
//				}
				if (HomeDeviceInfo.isPadlock(mCurKEY.getLockName())
						|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
						|| mCurKEY.getLockName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
					addFingerprint(false);
					addEKeyManage(false);
					addIcCards(false);
				} else {
					addPasscodeManage(false);
					addEKeyManage(false);
				}

				//addPasscodeManage(false);

//				if (DigitUtil.isSupportIC(mCurKEY.getSpecialValue())) { // 支持 IC 卡
//					addIcCards(false);
//				}
				addOperateRecord(true);
				addSettings(true);
				break;

			case "110408"://已删除
			case "110410"://已重置
			default:
				setLockInfoVisible(SHOW_DEVICE_ADD);
				break;
		}
	}

	/**
	 * @param keyStatus //钥匙状态
	 *                  110401：正常使用
	 *                  110402：待接收
	 *                  110405：已冻结
	 *                  110408：已删除
	 *                  110410：已重置
	 *                  110500：过期
	 */
	private void initCommonUserUI(String keyStatus) {
		addOperateRecord(true);
		addSettings(true);

		Resources res = getResources();
		int colorGrayParent = res.getColor(R.color.gray_lock_disable);
		switch (keyStatus) {
			case "110400": // 还未到生效时间
			case "110500": // 已过期
			case "110405": // 已冻结
				setUnlockLock();
				enableLockingColorFiltr(false, true, colorGrayParent);
				break;

			case "110401": // 正常使用
			case "110402": // 待接收
				setUnlockLock();
				enableLockingColorFiltr(true, false, 0);
				break;

			case "110408": // 已删除
			case "110410": // 已重置
			default:
				setLockInfoVisible(SHOW_DEVICE_ADD);
				break;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (getActivity() != null) {
			getActivity().unregisterReceiver(mReceiver);
		}
	}

	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type) {
			case Event.EventType.GET_HOME_DATA_COMPLETE:
				Home home = (Home) event.obj;
				if (null != home) {
					mHomeId = home.getId();
				}
				requestDeviceData();
				break;
			case Event.EventType.CHANGE_HOME:
				home = (Home) event.obj;
				if (null != home) {
					mHomeId = home.getId();
				}
				PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator);
				requestDeviceData();
				break;
            case Event.EventType.PERMISSION_CHANGED:
                doRefresh();
                break;
			case Event.EventType.ADD_DEVICE_SUCCESS:
			case Event.EventType.DELETE_LOCK_SUCCESS:
				if (VAL_TAG_FRAGMENT.equals(mTag)) {
					PeachLoader.showLoading(mActivity, LoaderStyle.BallSpinFadeLoaderIndicator);
					requestDeviceData();
				}
				break;
		}
	}

}
