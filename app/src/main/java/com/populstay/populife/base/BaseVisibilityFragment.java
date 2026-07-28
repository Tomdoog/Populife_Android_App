package com.populstay.populife.base;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;

import com.populstay.populife.eventbus.Event;
import com.populstay.populife.util.email.EmailUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by Jerry
 */
public class BaseVisibilityFragment extends BaseFragment implements OnAttachStateChangeListener, OnFragmentVisibilityChangedListener {

	/**
	 * ParentActivity是否可见
	 */
	private boolean mParentActivityVisible = false;
	/**
	 * 是否可见（Activity处于前台、Tab被选中、Fragment被添加、Fragment没有隐藏、Fragment.View已经Attach）
	 */
	private boolean mVisible = false;

	private BaseVisibilityFragment mParentFragment;
	private OnFragmentVisibilityChangedListener mListener;

	public void setOnVisibilityChangedListener(OnFragmentVisibilityChangedListener listener) {
		mListener = listener;
	}

	@Override
	public void onAttach(Context context) {
		info("onAttach");
		super.onAttach(context);
		final Fragment parentFragment = getParentFragment();
		if (parentFragment != null && parentFragment instanceof BaseVisibilityFragment) {
			mParentFragment = ((BaseVisibilityFragment) parentFragment);
			mParentFragment.setOnVisibilityChangedListener(this);
		}
		checkVisibility(true);
	}

	@Override
	public void onDetach() {
		info("onDetach");
		if (mParentFragment != null) {
			mParentFragment.setOnVisibilityChangedListener(null);
		}
		super.onDetach();
		checkVisibility(false);
		mParentFragment = null;
	}

	@Override
	public void onStart() {
		info("onStart");
		super.onStart();
		onActivityVisibilityChanged(true);
	}

	@Override
	public void onStop() {
		info("onStop");
		super.onStop();
		onActivityVisibilityChanged(false);
	}

	/**
	 * ParentActivity可见性改变
	 */
	protected void onActivityVisibilityChanged(boolean visible) {
		mParentActivityVisible = visible;
		checkVisibility(visible);
	}

	/**
	 * ParentFragment可见性改变
	 */
	@Override
	public void onFragmentVisibilityChanged(boolean visible) {
		checkVisibility(visible);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		info("onCreate");
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		view.addOnAttachStateChangeListener(this);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		checkVisibility(hidden);
	}

	/**
	 * Tab切换时会回调此方法。对于没有Tab的页面，{@link Fragment#getUserVisibleHint()}默认为true。
	 */
	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		info("setUserVisibleHint = " + isVisibleToUser);
		super.setUserVisibleHint(isVisibleToUser);
		checkVisibility(isVisibleToUser);
	}

	@Override
	public void onViewAttachedToWindow(View v) {
		info("onViewAttachedToWindow");
		checkVisibility(true);
	}

	@Override
	public void onViewDetachedFromWindow(View v) {
		info("onViewDetachedFromWindow");
		v.removeOnAttachStateChangeListener(this);
		checkVisibility(false);
	}

	/**
	 * 检查可见性是否变化
	 *
	 * @param expected 可见性期望的值。只有当前值和expected不同，才需要做判断
	 */
	private void checkVisibility(boolean expected) {
		if (expected == mVisible) return;
		final boolean parentVisible = mParentFragment == null ? mParentActivityVisible : mParentFragment.isFragmentVisible();
		final boolean superVisible = super.isVisible();
		final boolean hintVisible = getUserVisibleHint();
		final boolean visible = parentVisible && superVisible && hintVisible;
		info(String.format("==> checkVisibility = %s  ( parent = %s, super = %s, hint = %s )",
				visible, parentVisible, superVisible, hintVisible));
		if (visible != mVisible) {
			mVisible = visible;
			onVisibilityChanged(mVisible);
		}
	}

	/**
	 * 可见性改变
	 */
	protected void onVisibilityChanged(boolean visible) {
		info("==> onFragmentVisibilityChanged = " + visible);
		// 页面轨迹:首页几个 tab 是 Fragment,切换时 Activity 不变,只有这里能反映用户切到了哪一屏
		if (visible) {
			PeachLogger.d("[PAGE] show " + this.getClass().getSimpleName());
		}
		if (mListener != null) {
			mListener.onFragmentVisibilityChanged(visible);
		}

		if (visible){
			EventBus.getDefault().post(new Event(Event.EventType.FRAGMENT_RESUME_CHECK_REMOTE_LOGIN));
		}
	}

	/**
	 * 是否可见（Activity处于前台、Tab被选中、Fragment被添加、Fragment没有隐藏、Fragment.View已经Attach）
	 */
	public boolean isFragmentVisible() {
		return mVisible;
	}

	private void info(String s) {
//		PeachLogger.d(getClass().getSimpleName() + " (" + hashCode() + ")", s);
	}

	public void startImServiceActivity(Context context){
		EmailUtil.sendEmail(context);
	}
}
