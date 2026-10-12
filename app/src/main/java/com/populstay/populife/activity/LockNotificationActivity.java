package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.databinding.ActivityLockNotificationBinding;
import com.populstay.populife.entity.Key;
import com.populstay.populife.helper.ClickableTextHelper;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.notification.NotificationUtil;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.Objects;

public class LockNotificationActivity extends BaseActivity {
    private final Key mKey = MyApplication.CURRENT_KEY;
    private ActivityLockNotificationBinding binding;


    public static void actionStart(Context context) {
        Intent intent = new Intent(context, LockNotificationActivity.class);
        context.startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockNotificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getNotification();
        initView();
    }

    private void initView(){
        binding.titleBar.pageTitle.setText(getString(R.string.notification));
        binding.titleBar.pageAction.setVisibility(View.GONE);
        binding.scOpen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setOpen(isChecked);
            }
        });
        ClickableTextHelper.setClickableText(binding.tvCombined,
                getString(R.string.go_setting_notification_link_pre) + " ",
                getString(R.string.go_setting_notification_link),
                getColor(R.color.text_main),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NotificationUtil.openPushChannelSettings(LockNotificationActivity.this);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.cvGoSetting.setVisibility(NotificationUtil.canReceivePushNotification(this) ? View.GONE : View.VISIBLE);
    }



    private void setOpen(Boolean open) {
        binding.scOpen.setChecked(open);
        binding.tvOpen.setText(open ? getString(R.string.on) : getString(R.string.off));
        setNotification(open);
    }
    private void getNotification() {
        RestClient.builder()
                .url(Urls.LOCK_NOTIFICATION_GET)
                .loader(LockNotificationActivity.this)
                .params("lockId", mKey.getLockId())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_NOTIFICATION_GET", response);

                        JSONObject result = JSON.parseObject(response);
                        String data = result.getString("data");
                        int code = result.getInteger("code");
                        if (code == 200) {
                            setOpen(Objects.equals(data, "true"));
                        } else {
                            toastBackendError(code, R.string.backend_error_generic);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                    }
                })
                .build()
                .post();
    }

    private void setNotification(Boolean open) {
        binding.scOpen.setEnabled(false);
        RestClient.builder()
                .url(Urls.LOCK_NOTIFICATION_POST)
                .loader(LockNotificationActivity.this)
                .params("lockId", mKey.getLockId())
                .params("userId", PeachPreference.readUserId())
                .params("notificationEnable", open)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_NOTIFICATION_POST", response);

                        binding.scOpen.setEnabled(true);
                        JSONObject result = JSON.parseObject(response);
                        JSONObject data = result.getJSONObject("data");
                        int code = result.getInteger("code");
                        if (code == 200) {
                        } else {
                            toastBackendError(code, R.string.backend_error_generic);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        binding.scOpen.setEnabled(true);
                    }
                })
                .build()
                .post();
    }

}
