package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.home.entity.HomeDeviceInfo;

import androidx.annotation.Nullable;

public class AddDeviceFailActivity extends BaseActivity implements View.OnClickListener {

	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
	private static final String KEY_ROLLBACK_STATE = "KEY_ROLLBACK_STATE";

	/** 没有发生过物理回滚：蓝牙都没初始化成功，或属于网络失败、锁留着等自动补传 */
	public static final int ROLLBACK_NONE = 0;
	/** 锁已重置回出厂态，可以直接重新添加 */
	public static final int ROLLBACK_DONE = 1;
	/** 锁仍是已初始化状态、没能自动恢复，本地记录已保留在"未同步设备"里 */
	public static final int ROLLBACK_FAILED = 2;

	private TextView mTvTryPairingAgain, mTvBackToSmart;
	private String mLockType;
	private int mRollbackState = ROLLBACK_NONE;
	private TextView tvTheFollowing, tvTheFollowingDetail;

	public static void actionStart(Context context, String lockType) {
		actionStart(context, lockType, ROLLBACK_NONE);
	}

	/**
	 * @param rollbackState 物理锁的回滚结果，决定这一屏告诉用户"锁现在是什么状态、下一步该做什么"。
	 *                      不带这个信息的话，锁已回出厂态和锁还锁着两种情况看到的是同一句"添加失败"。
	 */
	public static void actionStart(Context context, String lockType, int rollbackState) {
		Intent intent = new Intent(context, AddDeviceFailActivity.class);
		intent.putExtra(KEY_LOCK_TYPE, lockType);
		intent.putExtra(KEY_ROLLBACK_STATE, rollbackState);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_device_fail);

		getIntentData();
		initView();
		setListener();
	}

	private void getIntentData() {
		mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
		mRollbackState = getIntent().getIntExtra(KEY_ROLLBACK_STATE, ROLLBACK_NONE);
	}

	private void initView() {
		initTitleBar();
		mTvTryPairingAgain = findViewById(R.id.tv_try_pairing_again);
		mTvBackToSmart = findViewById(R.id.tv_back_to_smart);
		tvTheFollowing = findViewById(R.id.tv_the_following);
		tvTheFollowingDetail = findViewById(R.id.tv_the_following_detail);

		// 发生过回滚时，锁的实际状态比"重启蓝牙、重启 App"那套通用建议重要得多，优先讲清楚
		if (mRollbackState == ROLLBACK_DONE) {
			tvTheFollowing.setText(R.string.add_fail_rollback_done);
			tvTheFollowingDetail.setText(HomeDeviceInfo.isPadlock(mLockType)
					? R.string.padlock_scan_retry : R.string.add_fail_rollback_done_detail);
			return;
		}
		if (mRollbackState == ROLLBACK_FAILED) {
			tvTheFollowing.setText(R.string.add_fail_rollback_failed);
			tvTheFollowingDetail.setText(R.string.add_fail_rollback_failed_detail);
			return;
		}

		if (HomeDeviceInfo.isPadlock(mLockType)) {
			tvTheFollowingDetail.setText(R.string.padlock_scan_retry);
		} else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_GATEWAY)) {
			tvTheFollowing.setText(R.string.failed_to_add_gateway_hint);
			tvTheFollowingDetail.setVisibility(View.GONE);
		} else if (HomeDeviceInfo.isDeadboltLack(mLockType)) {
			tvTheFollowingDetail.setText(R.string.the_following_detail_deadbolt);
		} else if (HomeDeviceInfo.isKeyBox(mLockType)) {
			tvTheFollowingDetail.setText(R.string.the_following_detail_keybox);
		} else if (HomeDeviceInfo.isKeyBoxK4(mLockType)) {
			tvTheFollowingDetail.setText(R.string.the_following_detail_keybox_k4);
		}  else if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			tvTheFollowingDetail.setText(R.string.the_following_detail_deadbolt);
		} else {
			tvTheFollowingDetail.setText(R.string.the_following_detail_door_lock);
		}
	}

	private void initTitleBar() {
		TextView titleTv = findViewById(R.id.page_left_title);
		titleTv.setVisibility(View.VISIBLE);
		titleTv.setText(R.string.failed_to_add);
		findViewById(R.id.page_title).setVisibility(View.GONE);
		findViewById(R.id.page_action).setVisibility(View.GONE);
	}

	private void setListener() {
		mTvTryPairingAgain.setOnClickListener(this);
		mTvBackToSmart.setOnClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_try_pairing_again) {
            if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_GATEWAY)) {
                GatewayAddGuideActivity.actionStartByTaskTop(this, mLockType);
            } else {
                LockAddGuideActivity.actionStartByTaskTop(this, mLockType);
            }
        } else if (id == R.id.tv_back_to_smart) {
            goToNewActivity(MainActivity.class);
        }
    }


    @Override
	public void finishCurrentActivity(View view) {
		goToNewActivity(MainActivity.class);
	}
}
