package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BasePagerAdapter;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.fragment.LockSendPasscodeFragment;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.lock.ILockGetTime;
import com.populstay.populife.manhattanlock.MHILockGetTime;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class LockSendPasscodeActivity extends BaseActivity {

	private static final String KEY_LOCK_KEY = "key_lock_key";
	private static final String KEY_LOCK_ID = "key_lock_id";
	private static final String KEY_KEY_ID = "key_key_id";
	private static final String KEY_LOCK_NAME = "key_lock_name";
	private static final String KEY_LOCK_MAC = "key_lock_mac";
	private static final String KEY_PASSWORD_LIST = "key_password_list";
	public static final String KEY_PWD_TYPE = "key_pwd_type";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";

	private TextView mTvSend,mPageTitle,mTvPwdTypeName,tv_create_pwd_top_tips;
	private ViewPager mViewPager;
	private List<Fragment> mFragmentList = new ArrayList<>();
	private BasePagerAdapter mAdapter;

	private int mLockId;
	private int mKeyId;
	private Key mKey;
	private String mLockName;
	private String mLockMac;
	private ArrayList<String> mPasswordList = new ArrayList<>();
	private AlertDialog mPwdTypeDialog;
	private LinearLayout mLlShowPwdDialogBtn;

	/**
	 * 启动当前 activity
	 *
	 * @param context 上下文
	 * @param lockId  锁 id
	 */
	public static void actionStart(Context context,Key key, int lockId, int keyId, String lockName, String lockMac, ArrayList<String> passwordList,String keyType) {
		Intent intent = new Intent(context, LockSendPasscodeActivity.class);
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
		setContentView(R.layout.activity_lock_send_passcode);
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
		initCurrentAccessTypeIndex(getIntent().getStringExtra(KEY_PWD_TYPE));
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
            mTTLockAPI.getLockTime(mKey.getLockKey(), mKey.getLockMac(), new GetLockTimeCallback() {
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

	private void initCurrentAccessTypeIndex(String keyPwdType){
		mKeyPwdType = keyPwdType;
		if (TextUtils.isEmpty(keyPwdType)){
			mCurrentAccessTypeIndex = 0;
			return;
		}
		for (int i = 0; i < mPwdAccessTypeArr.length; i++) {
			if (keyPwdType.equals(mPwdAccessTypeArr[i])){
				mCurrentAccessTypeIndex = i;
				break;
			}
		}
	}

	private void initView() {
		tv_create_pwd_top_tips = findViewById(R.id.tv_create_pwd_top_tips);
		mPageTitle = findViewById(R.id.page_title);
		mPageTitle.setText(R.string.generate_pin_code);
		mTvPwdTypeName = findViewById(R.id.tv_pwd_type_name);
		mTvSend = findViewById(R.id.page_action);
		mTvSend.setText(R.string.share);
		mTvSend.setVisibility(View.GONE);
		mLlShowPwdDialogBtn = findViewById(R.id.ll_show_pwd_dialog_btn);
		mViewPager = findViewById(R.id.vp_lock_send_passcode);
	}

	public void setTopTips(int position){
		tv_create_pwd_top_tips.setText(mPwdAccessTypeTitleArr[position]);
	}
	public void setTopTitle(){
		if (KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_CUSTOM.equals(mKeyPwdType)){
			mPageTitle.setText(R.string.create_custom_code);
		}else {
			mPageTitle.setText(R.string.generate_pin_code);
		}
	}
	public void setPwdTypeName(int position){
		mTvPwdTypeName.setText(mPwdAccessTypeNameArr[position]);
	}

	public void setCurrentPage(int position){
		mKeyPwdType = mPwdAccessTypeArr[position];
		mViewPager.setCurrentItem(position, false);
		setTopTips(position);
		setPwdTypeName(position);
		setTopTitle();
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
		mLlShowPwdDialogBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showPwdTypeSelectDialog();
			}
		});
	}

	protected void initTab() {
		Resources res = getResources();
		String customTip;
		if (mKey.getLockId()<0){
			customTip = res.getString(R.string.key_pwd_mh_custom_create_desc);
		}else {
			customTip = res.getString(R.string.key_pwd_custom_create_desc);
		}
		mPwdAccessTypeTitleArr = new String[]{
				res.getString(R.string.create_permanent_pwd_tips), res.getString(R.string.create_time_limited_pwd_tips),
				res.getString(R.string.create_one_time_pwd_tips), customTip};
		mPwdAccessTypeNameArr = new String[]{
				res.getString(R.string.key_pwd_permanent), res.getString(R.string.key_pwd_period),
				res.getString(R.string.key_pwd_one_time), res.getString(R.string.key_pwd_custom)};

		mFragmentList.add(LockSendPasscodeFragment.newInstance(mKey,LockSendPasscodeFragment.VAL_TAB_TYPE_PERMANENT,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//永久密码
		mFragmentList.add(LockSendPasscodeFragment.newInstance(mKey,LockSendPasscodeFragment.VAL_TAB_TYPE_PERIOD,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//限时密码
		mFragmentList.add(LockSendPasscodeFragment.newInstance(mKey,LockSendPasscodeFragment.VAL_TAB_TYPE_ONE_TIME,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//单次密码
		mFragmentList.add(LockSendPasscodeFragment.newInstance(mKey,LockSendPasscodeFragment.VAL_TAB_TYPE_CUSTOMIZE,
				mLockId, mKeyId, mLockName, mLockMac, mPasswordList));//自定义密码

		mAdapter = new BasePagerAdapter(getSupportFragmentManager(), mFragmentList, mPwdAccessTypeTitleArr);
		mViewPager.setAdapter(mAdapter);
		setCurrentPage(mCurrentAccessTypeIndex);
	}

	private String[] mPwdAccessTypeTitleArr;
	private String[] mPwdAccessTypeNameArr;

	private int[] mPwdAccessTypeItemArr = {R.id.rb_1, R.id.rb_2, R.id.rb_3, R.id.rb_4};
	private String[] mPwdAccessTypeArr = {KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_PERMANENT,
			KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_PERIOD,
			KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_ONE_TIME,
			KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_CUSTOM};
	private int mCurrentAccessTypeIndex = 0;
	private int mSelectAccessTypeIndex = 0;
	private String mKeyPwdType;

	private void showPwdTypeSelectDialog() {
		mPwdTypeDialog = new AlertDialog.Builder(this).create();
		mPwdTypeDialog.show();
		final Window window = mPwdTypeDialog.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_select_pwd_type);
			window.setGravity(Gravity.CENTER);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.WRAP_CONTENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			RadioGroup radioGroup = window.findViewById(R.id.radio_group);
			radioGroup.check(mPwdAccessTypeItemArr[mCurrentAccessTypeIndex]);
			radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(RadioGroup group, int checkedId) {
					if (checkedId == mPwdAccessTypeItemArr[0]){
						//永久密码
						mSelectAccessTypeIndex = 0;
					}else if (checkedId == mPwdAccessTypeItemArr[1]){
						//限时密码
						mSelectAccessTypeIndex = 1;
					}else if (checkedId == mPwdAccessTypeItemArr[2]){
						//单次密码
						mSelectAccessTypeIndex = 2;
					}else if (checkedId == mPwdAccessTypeItemArr[3]){
						//自定义密码
						mSelectAccessTypeIndex = 3;
					}
				}
			});
			window.findViewById(R.id.tv_cancel_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mPwdTypeDialog.dismiss();
				}
			});
			window.findViewById(R.id.tv_save_btn).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mCurrentAccessTypeIndex = mSelectAccessTypeIndex;
					setCurrentPage(mCurrentAccessTypeIndex);
					mPwdTypeDialog.dismiss();
				}
			});
		}
	}
}
