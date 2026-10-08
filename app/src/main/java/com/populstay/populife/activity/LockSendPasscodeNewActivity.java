package com.populstay.populife.activity;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BasePagerAdapter;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.fragment.LockSendPasscodeContainerFragment;
import com.populstay.populife.fragment.LockSendPasscodeFragment;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.manhattanlock.MHILockGetTime;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LockSendPasscodeNewActivity extends BaseActivity {

	private static final String KEY_LOCK_KEY = "key_lock_key";
	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_LOCK_NAME = "key_lock_name";
	private static final String KEY_LOCK_MAC = "key_lock_mac";
	private static final String KEY_PASSWORD_LIST = "key_password_list";
	public static final String KEY_PWD_TYPE = "key_pwd_type";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
    private TabLayout mTabLayout;
    private TextView mPwdTypeHint;

	private TextView mTvSend,mPageTitle;
	private ViewPager mViewPager;
	private List<Fragment> mFragmentList = new ArrayList<>();
	private BasePagerAdapter mAdapter;

	private int mLockId;
	private int mKeyId;
	private Key mKey;
	private String mLockName;
	private String mLockMac;
	private ArrayList<String> mPasswordList = new ArrayList<>();

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁 id
	 */
	public static void actionStart(Context context,Key key, int lockId, int keyId, String lockName, String lockMac, ArrayList<String> passwordList,String keyType) {
		Intent intent = new Intent(context, LockSendPasscodeNewActivity.class);
		intent.putExtra(KEY_LOCK_KEY, key);
		intent.putExtra(KEY_LOCK_ID, lockId);
		intent.putExtra(KEY_KEY_ID, keyId);
		intent.putExtra(KEY_LOCK_NAME, lockName);
		intent.putExtra(KEY_LOCK_MAC, lockMac);
		intent.putStringArrayListExtra(KEY_PASSWORD_LIST, passwordList);
		intent.putExtra(KEY_PWD_TYPE, keyType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_send_passcode_new);
		mKey = getIntent().getParcelableExtra(KEY_LOCK_KEY);
        if (mKey != null && !com.populstay.populife.db.PopulifeDBUtil.getInstance(getApplicationContext())
                .queryByUserAndMac(com.populstay.populife.util.storage.PeachPreference.readUserId(), mKey.getLockMac()).isEmpty()) {
            toast(R.string.binding_incomplete_password);
            finish();
            return;
        }
		mLockId = getIntent().getIntExtra(KEY_LOCK_ID, 0);
		mKeyId = getIntent().getIntExtra(KEY_KEY_ID, 0);
		mLockName = getIntent().getStringExtra(KEY_LOCK_NAME);
		mLockMac = getIntent().getStringExtra(KEY_LOCK_MAC);
		mPasswordList = getIntent().getStringArrayListExtra(KEY_PASSWORD_LIST);
//		initCurrentAccessTypeIndex(getIntent().getStringExtra(KEY_PWD_TYPE));
		initView();
		initListener();
		initTab();
		readLockTime();
	}

	private void readLockTime() {
		//showLoading();
		setGetTimeCallback();
		if (mKey.getLockId()<0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getLockTime();
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.getLockTime(mKey.getLockData(), mKey.getLockMac(), new GetLockTimeCallback() {
                @Override
                public void onGetLockTimeSuccess(long l) {
                    mKey.setLockCurrentTime(l);
                }

                @Override
                public void onFail(LockError lockError) {

                }
            });
		}

	}

	private void setGetTimeCallback() {
		if (mKey.getLockId()<0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_LOCK_TIME);
			MyApplication.pplBleSession.setmILockGetTime(new MHILockGetTime() {
				@Override
				public void onSuccess(final long time) {
					mKey.setLockCurrentTime(time);
				}

				@Override
				public void onFail() {
				}
			});
		}
	}

	private void initView() {
        mTabLayout = findViewById(R.id.tl_lock_passcode_type);
        mPwdTypeHint = findViewById(R.id.tv_pwd_type_hint);
		mPageTitle = findViewById(R.id.page_title);
		mPageTitle.setText(R.string.create_password);
		mTvSend = findViewById(R.id.page_action);
		mTvSend.setText(R.string.share);
		mTvSend.setVisibility(View.GONE);
		mViewPager = findViewById(R.id.vp_lock_send_passcode_container);
        mTabLayout.setupWithViewPager(mViewPager);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()){
                    case 0:
                        mPwdTypeHint.setText(getString(R.string.create_random_pwd_hint));
                        break;
                    case 1:
                        mPwdTypeHint.setText(getString(R.string.create_custom_pwd_hint));
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
	}
	private void initListener() {
		mTvSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				LockSendPasscodeFragment fragment = (LockSendPasscodeFragment) mFragmentList.get(mViewPager.getCurrentItem());
				if (fragment != null) {
					if (fragment.isPasscodeGenerated()) {
						// 分享
						fragment.showShare();
					} else {
						toast(R.string.note_create_password_first);
					}
				}
			}
		});
	}
	protected void initTab() {
		Resources res = getResources();
        mTitleArr = new String[]{getString(R.string.key_pwd_type_random), getString(R.string.key_pwd_type_custom)};

		mFragmentList.add(LockSendPasscodeContainerFragment.newInstance(mKey,LockSendPasscodeContainerFragment.Type.RANDOM,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//永久密码
		mFragmentList.add(LockSendPasscodeContainerFragment.newInstance(mKey,LockSendPasscodeContainerFragment.Type.CUSTOM,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//限时密码

		mAdapter = new BasePagerAdapter(getSupportFragmentManager(), mFragmentList, mTitleArr);
		mViewPager.setAdapter(mAdapter);
	}

	private String[] mTitleArr;
}