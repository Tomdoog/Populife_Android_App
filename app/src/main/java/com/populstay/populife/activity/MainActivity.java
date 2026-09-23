package com.populstay.populife.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.fragment.MainGeneralFragment;
import com.populstay.populife.fragment.MainLockFragment;
import com.populstay.populife.fragment.MainMeFragment;
import com.populstay.populife.helper.OfflineLockUploader;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.push.EventPushService;
import com.populstay.populife.ui.NoScrollViewPager;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import cn.ittiger.player.PlayerManager;

public class MainActivity extends BaseActivity {

	private static final int TAB_LOCK = 0, TAB_GENERAL = 1, TAB_ME = 2;
	private static boolean mIsExit = false; // 退出 APP 判断标志
	@SuppressLint("HandlerLeak")
	private static Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			mIsExit = false;
		}
	};
	private int mCurrentTab = TAB_LOCK;
	private NoScrollViewPager mViewPager;
	private RadioGroup navigation;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestRuntimePermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, new PermissionListener() {
                @Override
                public void onGranted() {
                    initView();
                    initListener();
                    init();
                }

                @Override
                public void onDenied(List<String> deniedPermissions) {
                    initView();
                    initListener();
                    init();
                }
            });
        } else {
            initView();
            initListener();
            init();
        }
	}

	private void showAppUserManual() {
		boolean hasShownBefore = PeachPreference.getBoolean(PeachPreference.SHOW_APP_USER_MAUAL);
		if (!hasShownBefore) {
			BaseApplication.getHandler().postDelayed(new Runnable() {
				@Override
				public void run() {
					PDFActivity.actionStart(MainActivity.this, getString(R.string.user_manual_app),
							"user_manual_app.pdf", false);
					PeachPreference.setBoolean(PeachPreference.SHOW_APP_USER_MAUAL, true);
				}
			}, 1000);
		}
	}

	private void initView() {
		mViewPager = findViewById(R.id.nsv_main);
		mViewPager.setOffscreenPageLimit(3);
		setupViewPager(mViewPager);
		navigation = findViewById(R.id.navigation);
		setCurrentTab(mCurrentTab);
	}

	/**
	 * 切换底部导航栏 tab 状态
	 *
	 * @param clickedTab 被点击的 tab
	 */
	private void setCurrentTab(int clickedTab) {
		mCurrentTab = clickedTab;
		mViewPager.setCurrentItem(clickedTab, false);
	}

	private void setupViewPager(ViewPager viewPager) {
		ViewPagerAdapter localViewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
		localViewPagerAdapter.addFragment(new MainLockFragment());
		localViewPagerAdapter.addFragment(new MainGeneralFragment());
		localViewPagerAdapter.addFragment(new MainMeFragment());
		viewPager.setAdapter(localViewPagerAdapter);
	}

	private void initListener() {
		navigation.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.nav_main_lock) {
                    setCurrentTab(TAB_LOCK);
                } else if (checkedId == R.id.nav_main_general) {
                    setCurrentTab(TAB_GENERAL);
                } else if (checkedId == R.id.nav_main_me) {
                    setCurrentTab(TAB_ME);
                }
            }
        });
	}

	/**
	 * Initialization
	 */
	private void init() {
		//turn on bluetooth
		MyApplication.sPPLOCK.startBleService(this);

		Intent pushServiceIntent = new Intent(this, EventPushService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(pushServiceIntent);
        } else {
            startService(pushServiceIntent);
        }

		// 进入主界面时,尝试补传未上传到云端的设备(防止绑定中断导致设备变砖)
		OfflineLockUploader.getInstance().tryUploadAll("MainActivity");
		LockRecordUploader.getInstance().retry();
    }

	/**
	 * 退出 APP
	 */
	private void exit() {
		if (!mIsExit) {
			mIsExit = true;
			toast(R.string.click_again_to_exit);
			mHandler.sendEmptyMessageDelayed(-1, 2000);
		} else {
			finish();
			System.exit(0);
		}
	}

	@Override
	public void onBackPressed() {
		if (PlayerManager.getInstance().onBackPressed()){
			return;
		}
		exit();
	}

	class ViewPagerAdapter extends FragmentPagerAdapter {
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

}
