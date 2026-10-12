package com.populstay.populife.home;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
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
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.storage.PeachPreference;

import org.greenrobot.eventbus.EventBus;

public class HomeCreateActivity extends BaseActivity {

    private String mActionType = VAL_HOME_CREATE_ACTION_TYPE_NEW_HOME;
    public static final String VAL_HOME_CREATE_ACTION_TYPE_NEW_HOME = "val_home_create_action_type_new_home";
    public static final String VAL_HOME_CREATE_ACTION_TYPE_RENAME_HOME = "val_home_create_action_type_rename_home";
    public static final String KEY_HOME_CREATE_ACTION_TYPE = "key_home_create_action_type";
    public static final String KEY_HOME_CREATE_TRANSFER_DATA = "key_home_create_transfer_data";

    private TextView mTvCreateSpaceBtn;
    private ExEditText mEtSpaceName;
    private TextView mPageTitle;
    private Home mHome;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_create);
        getIntentData();
        initView();
        setListener();

    }

    private void getIntentData() {
        mActionType = getIntent().getStringExtra(KEY_HOME_CREATE_ACTION_TYPE);
        mHome = getIntent().getParcelableExtra(KEY_HOME_CREATE_TRANSFER_DATA);
    }

    private void initView() {
        findViewById(R.id.page_action).setVisibility(View.GONE);
        mPageTitle = findViewById(R.id.page_title);
        mTvCreateSpaceBtn = findViewById(R.id.tv_create_space_btn);
        mEtSpaceName = findViewById(R.id.et_space_name);
        mPageTitle.setText(VAL_HOME_CREATE_ACTION_TYPE_NEW_HOME.equals(mActionType) ? R.string.new_create_space :  R.string.rename_space);
        mTvCreateSpaceBtn.setText(VAL_HOME_CREATE_ACTION_TYPE_NEW_HOME.equals(mActionType) ? R.string.create :  R.string.confirm);
        if (null != mHome){
            mEtSpaceName.setText(mHome.getName());
        }
        setCreateSpaceBtnEnable();
    }

    private void setListener() {

        mEtSpaceName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                setCreateSpaceBtnEnable();
            }
        });

        mTvCreateSpaceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VAL_HOME_CREATE_ACTION_TYPE_NEW_HOME.equals(mActionType)){
                    addGroup();
                }else {
                    modifyGroup();
                }
            }
        });
    }

    private void setCreateSpaceBtnEnable(){
        mTvCreateSpaceBtn.setEnabled(!TextUtils.isEmpty(mEtSpaceName.getTextStr()));
    }

    private void addGroup() {
        RestClient.builder()
                .url(Urls.LOCK_GROUP_ADD)
                .loader(HomeCreateActivity.this)
                .params("userId", PeachPreference.readUserId())
                .params("name", mEtSpaceName.getTextStr())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            EventBus.getDefault().post(new Event(Event.EventType.ADD_SPACE));
                            setResult(RESULT_OK, new Intent());
                            finish();
                        } else if (code == 910) {
                            toast(R.string.note_name_already_exists);
                        } else {
                            toastBackendError(result, R.string.note_add_group_fail);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.note_add_group_fail);
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        toast(R.string.note_add_group_fail);
                    }
                })
                .build()
                .post();
    }

    private void modifyGroup() {
        RestClient.builder()
                .url(Urls.LOCK_GROUP_MODIFY)
                .loader(HomeCreateActivity.this)
                .params("id", mHome.getId())
                .params("userId", PeachPreference.readUserId())
                .params("name", mEtSpaceName.getTextStr())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            EventBus.getDefault().post(new Event(Event.EventType.RENAME_SPACE, mEtSpaceName.getTextStr()));
                            setResult(RESULT_OK, new Intent());
                            finish();
                        } else if (code == 910) {
                            toast(R.string.note_name_already_exists);
                        } else if (code == 951) {
                            toast(R.string.space_name_modified_hint);
                        } else {
                            toastBackendError(result, R.string.note_modify_group_fail);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.note_modify_group_fail);
                    }
                })
                .error(new IError() {
                    @Override
                    public void onError(int code, String msg) {
                        toast(R.string.note_modify_group_fail);
                    }
                })
                .build()
                .post();
    }

    public static void actionStart(Context context, String actionType,Home home) {
        Intent intent = new Intent(context, HomeCreateActivity.class);
        intent.putExtra(KEY_HOME_CREATE_ACTION_TYPE, actionType);
        intent.putExtra(KEY_HOME_CREATE_TRANSFER_DATA, home);
        context.startActivity(intent);
    }
}
