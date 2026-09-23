package com.populstay.populife.activity;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.Nullable;

public class HelpActivity extends BaseActivity {

    public static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";

    private TextView mTvPageTitle;
    private ImageView mIvNewMsg;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_layout);
        initView();

    }

    private void initView() {
        mTvPageTitle = findViewById(R.id.page_left_title);
        mTvPageTitle.setVisibility(View.VISIBLE);
        mTvPageTitle.setText(R.string.lock_paired_goto_help);
        findViewById(R.id.page_title).setVisibility(View.GONE);
        if (HomeDeviceInfo.isPadlock(getIntent().getStringExtra(KEY_LOCK_TYPE))) {
            ((TextView) findViewById(R.id.lock_help_title_1)).setText(R.string.padlock_help_pairing_title);
            ((TextView) findViewById(R.id.lock_help_content_1)).setText(R.string.padlock_help_pairing);
            ((TextView) findViewById(R.id.lock_help_title_2)).setText(R.string.padlock_help_bound_title);
            ((TextView) findViewById(R.id.lock_help_content_2)).setText(R.string.padlock_help_bound);
            ((TextView) findViewById(R.id.lock_help_title_3)).setText(R.string.padlock_help_wake_title);
            ((TextView) findViewById(R.id.lock_help_content_3)).setText(R.string.padlock_wake_help);
        }
        initTitleBarRightBtn();
    }

    private void initTitleBarRightBtn() {
        findViewById(R.id.page_action).setVisibility(View.GONE);

        mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
        View tvSupport = findViewById(R.id.rl_main_lock_online_service);
        tvSupport.setVisibility(View.VISIBLE);
        tvSupport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startImServiceActivity(HelpActivity.this);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
