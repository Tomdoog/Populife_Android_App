package com.populstay.populife.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.Home;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.log.PeachLogger;

import org.greenrobot.eventbus.EventBus;

public class HomeDetailsActivity extends BaseActivity implements View.OnClickListener {

    private RelativeLayout mLlSpaceName;
    private TextView mTvSpaceName, mTvSpaceDeviceNumName;
    private TextView mTvDeleteSpaceBtn;
    private Home mHome;
    public static final String KEY_TRANSFER_DATA = "key_transfer_data";
    private TextView mPageTitle;
    private AlertDialog DIALOG;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_details);
        getIntentData();
        initView();
        setListener();

    }

    private void getIntentData() {
        mHome = getIntent().getParcelableExtra(KEY_TRANSFER_DATA);
    }

    private void initView() {
        findViewById(R.id.page_action).setVisibility(View.GONE);
        mPageTitle = findViewById(R.id.page_title);
        mPageTitle.setText(R.string.manage_space);
        mLlSpaceName = findViewById(R.id.ll_space_name);
        mTvSpaceName = findViewById(R.id.tv_space_name);
        mTvSpaceDeviceNumName = findViewById(R.id.tv_home_device_num_name);
        mTvDeleteSpaceBtn = findViewById(R.id.tv_delete_space_btn);

        if (null != mHome){
            mTvSpaceDeviceNumName.setText(String.format(getResources().getString(R.string.device_num_match),mHome.getDeviceCount()));
        }
        setSpaceName();
    }

    public void setSpaceName(){
        if (null != mHome){
            mTvSpaceName.setText(mHome.getName());
        }
    }

    private void setListener() {
        mTvDeleteSpaceBtn.setOnClickListener(this);
        mLlSpaceName.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.ll_space_name) {
            changeSpaceName();
        } else if (id == R.id.tv_delete_space_btn) {
            deleteSpace(mHome != null && mHome.getDeviceCount() > 0);
        }
    }


    private void changeSpaceName(){
        HomeCreateActivity.actionStart(this, HomeCreateActivity.VAL_HOME_CREATE_ACTION_TYPE_RENAME_HOME, mHome);
    }

    private void deleteSpace(boolean hasIncludeDevice){
        DIALOG = new AlertDialog.Builder(this).create();
        DIALOG.setCanceledOnTouchOutside(false);
        DIALOG.show();
        final Window window = DIALOG.getWindow();
        if (window != null) {
            window.setContentView(R.layout.dialog_input);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            //设置属性
            final WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            window.setAttributes(params);

            ((TextView) window.findViewById(R.id.tv_dialog_input_title)).setText(hasIncludeDevice ? R.string.delete_space_hint : R.string.delete_space_hint2);
            window.findViewById(R.id.et_dialog_input_content).setVisibility(View.GONE);
            Button leftBtn = window.findViewById(R.id.btn_dialog_input_cancel);
            leftBtn.setText(hasIncludeDevice ? R.string.confirm : R.string.cancel);
            leftBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DIALOG.cancel();
                }
            });
            Button rightBtn =  window.findViewById(R.id.btn_dialog_input_ok);
            rightBtn.setVisibility(hasIncludeDevice ? View.GONE : View.VISIBLE);
            rightBtn.setText(R.string.delete);
            rightBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteGroup();
                    DIALOG.cancel();
                }
            });
        }

    }

    private void deleteGroup() {
        RestClient.builder()
                .url(Urls.LOCK_GROUP_DELETE)
                .loader(HomeDetailsActivity.this)
                .params("id", mHome.getId())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_GROUP_DELETE", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 500){
                            toast(R.string.delete_space_hint3);
                        } else if (code != 200) {
                            toastBackendError(code, R.string.note_delete_group_fail);
                        } else {
                            EventBus.getDefault().post(new Event(Event.EventType.DELETE_SPACE));
                            finish();
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.note_delete_group_fail);
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        toast(R.string.note_delete_group_fail);
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        toast(R.string.note_delete_group_fail);
                    }
                })
                .build()
                .post();
    }

    public static void actionStart(Context context, Home home) {
        Intent intent = new Intent(context, HomeDetailsActivity.class);
        intent.putExtra(KEY_TRANSFER_DATA, home);
        context.startActivity(intent);
    }

    @Override
    public void onEventSub(Event event) {
        super.onEventSub(event);
        if (Event.EventType.RENAME_SPACE == event.type) {
            if (event.obj instanceof String){
                String spaceName = (String) event.obj;
                mHome.setName(spaceName);
                setSpaceName();
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != DIALOG){
            DIALOG.dismiss();
        }
    }
}
