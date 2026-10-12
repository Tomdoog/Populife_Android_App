package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

public class BackupEkeyPermissionModifyActivity extends BaseActivity implements View.OnClickListener , RadioGroup.OnCheckedChangeListener{

    public static final String KEY_KEY_ID = "key_key_id";
    public static final String KEY_AUTH_TYPE = "key_auth_type";

    private TextView mTvSave, tv_permission_types_hint;

    private RadioGroup rg_permission_types;
    private int mKeyId;
    private int mKeyRight;// 授权类型

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ekey_permission_modify_backup);

        getIntentData();
        initView();
        initListener();
    }

    private void getIntentData() {
        Intent data = getIntent();
        mKeyId = data.getIntExtra(KEY_KEY_ID, 1);
        mKeyRight = data.getIntExtra(KEY_AUTH_TYPE, 0);
    }

    private boolean isAuth(){
        return mKeyRight == KeyPwdConstant.IBTKeyPermissionType.AUTH;
    }

    private void initView() {
        ((TextView) findViewById(R.id.page_title)).setText(R.string.modify_permission_types);
        findViewById(R.id.page_action).setVisibility(View.GONE);
        mTvSave = findViewById(R.id.tv_save_btn);

        rg_permission_types = findViewById(R.id.rg_permission_types);
        tv_permission_types_hint = findViewById(R.id.tv_permission_types_hint);
        tv_permission_types_hint.setText(DateUtil.getCurDate(DateUtil.DATE_TIME_PATTERN_3));

        RadioButton rb_permission;
        if (!isAuth()) {
            rb_permission = (RadioButton) rg_permission_types.getChildAt(0);
        }else {
            rb_permission = (RadioButton) rg_permission_types.getChildAt(1);
        }
        rb_permission.setChecked(true);
    }

    private void initListener() {
        mTvSave.setOnClickListener(this);
        rg_permission_types.setOnCheckedChangeListener(this);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_save_btn) {
            if (isAuth()) {
                unauthEkey();
            } else {
                authEkey();
            }
        }
    }


    private void returnData(){
        Intent intent = new Intent();
        intent.putExtra(KEY_AUTH_TYPE,mKeyRight);
        setResult(RESULT_OK,intent);
    }

    /**
     * 授权用户钥匙
     */
    private void authEkey() {
        RestClient.builder()
                .url(Urls.LOCK_EKEY_AUTHORIZE)
                .loader(BackupEkeyPermissionModifyActivity.this)
                .params("keyId", mKeyId)
                .params("userId", PeachPreference.readUserId())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_EKEY_AUTHORIZE", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            mKeyRight = 1;
                            toast(R.string.ekey_authorize_success);
                            returnData();
                            finish();
                        } else {
                            toastBackendError(code, R.string.ekey_authorize_fail);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.ekey_authorize_fail);
                    }
                })
                .build()
                .post();
    }

    /**
     * 反授权用户钥匙
     */
    private void unauthEkey() {
        RestClient.builder()
                .url(Urls.LOCK_EKEY_UN_AUTHORIZE)
                .loader(BackupEkeyPermissionModifyActivity.this)
                .params("keyId", mKeyId)
                .params("userId", PeachPreference.readUserId())
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_EKEY_UN_AUTHORIZE", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            toast(R.string.ekey_un_authorize_success);
                            mKeyRight = 0;
                            returnData();
                            finish();
                        } else {
                            toastBackendError(code, R.string.ekey_un_authorize_fail);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.ekey_un_authorize_fail);
                    }
                })
                .build()
                .post();
    }


    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (group.getId() == R.id.rg_permission_types) {
            setPermissionTypes(checkedId);
        }
    }

    private void setPermissionTypes(int checkedId) {
        int hint;

        if (checkedId == R.id.rb_general_user) {
            hint = R.string.general_user_hint;
        } else if (checkedId == R.id.rb_authorized_user) {
            hint = R.string.authorized_user_hint;
        } else {
            hint = R.string.general_user_hint; // 默认值，可根据需求调整
        }

        tv_permission_types_hint.setText(hint);
    }

}
