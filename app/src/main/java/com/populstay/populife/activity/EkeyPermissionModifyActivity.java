package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.databinding.ActivityEkeyPermissionModifyBinding;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;

import org.greenrobot.eventbus.EventBus;

import java.util.WeakHashMap;

public class EkeyPermissionModifyActivity extends BaseActivity {

    public static final String KEY_KEY_ID = "key_key_id";
    public static final String KEY_AUTH_TYPE = "key_auth_type";

    private ActivityEkeyPermissionModifyBinding binding;

    private Key mKey = MyApplication.CURRENT_KEY;

    private int mKeyId;
    private int mKeyRight;// 授权类型

    private boolean isInit = false;

    public static void actionStart(Context context, int keyId, int keyRight) {
        Intent intent = new Intent(context, EkeyPermissionModifyActivity.class);
        intent.putExtra(KEY_KEY_ID, keyId);
        intent.putExtra(KEY_AUTH_TYPE, keyRight);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEkeyPermissionModifyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getIntentData();
        getEkeyPermission();
        initView();
    }

    private void getIntentData() {
        Intent data = getIntent();
        mKeyId = data.getIntExtra(KEY_KEY_ID, 1);
        mKeyRight = data.getIntExtra(KEY_AUTH_TYPE, 0);
    }

    private boolean isAuth() {
        return mKeyRight == KeyPwdConstant.IBTKeyPermissionType.AUTH;
    }

    private void initView() {
        binding.vppPermission.llContainer.setVisibility(View.GONE);
        binding.tvSaveBtn.setEnabled(false);
        if (isAuth()) {
            binding.tvPermissionTypes.setText(R.string.authorized_user);

            binding.vppPermission.stv1.setVisibility(View.VISIBLE);
            binding.vppPermission.isiv3.setChecked(true);
            binding.vppPermission.stv3.setVisibility(View.VISIBLE);
            binding.vppPermission.stv4.setVisibility(View.VISIBLE);
        } else {
            binding.tvPermissionTypes.setText(R.string.general_user);

            binding.vppPermission.stv1.setVisibility(View.GONE);
            binding.vppPermission.isiv3.setChecked(false);
            binding.vppPermission.stv3.setVisibility(View.GONE);
            binding.vppPermission.stv4.setVisibility(View.GONE);
        }
        binding.tvSaveBtn.setOnClickListener(v -> {
            postEkeyPermission();
        });
    }


    private void returnData() {
        EventBus.getDefault().post(new Event(Event.EventType.PERMISSION_CHANGED));
        Intent intent = new Intent();
        intent.putExtra(KEY_AUTH_TYPE, mKeyRight);
        setResult(RESULT_OK, intent);
    }

    /**
     * 钥匙权限
     */
    private void getEkeyPermission() {
        RestClient.builder()
                .url(Urls.LOCK_EKEY_PERMISSION_GET)
                .loader(EkeyPermissionModifyActivity.this)
                .params("keyId", mKeyId)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_EKEY_PERMISSION_GET", response);

                        JSONObject result = JSON.parseObject(response);
                        JSONObject data = result.getJSONObject("data");
                        int code = result.getInteger("code");
                        if (code == 200) {
                            binding.vppPermission.llContainer.setVisibility(View.VISIBLE);
                            binding.tvSaveBtn.setEnabled(true);
                            if (data.containsKey("allowAllPermissions"))
                                binding.vppPermission.stv1.setChecked(data.getBoolean("allowAllPermissions"));
                            if (data.containsKey("allowRemoteUnlock"))
                                binding.vppPermission.stv2.setChecked(data.getBoolean("allowRemoteUnlock"));
                            if (data.containsKey("allowSyncBattery"))
                                binding.vppPermission.stv3.setChecked(data.getBoolean("allowSyncBattery"));
                            if (data.containsKey("allowCalibrateTime"))
                                binding.vppPermission.stv4.setChecked(data.getBoolean("allowCalibrateTime"));
                        } else {
                            toastBackendError(code, R.string.backend_error_generic);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        finish();
//                        toast(R.string.ekey_un_authorize_fail);
                    }
                })
                .build()
                .get();
    }

    /**
     * 钥匙权限
     */
    private void postEkeyPermission() {
        WeakHashMap<String, Object> params = new WeakHashMap<>();
        params.put("keyId", mKeyId);
        params.put("userId", PeachPreference.readUserId());
        params.put("allowRemoteUnlock", binding.vppPermission.stv2.isChecked());
        if(isAuth()) {
            params.put("allowAllPermissions", binding.vppPermission.stv1.isChecked());
            params.put("allowSyncBattery", binding.vppPermission.stv3.isChecked());
            params.put("allowCalibrateTime", binding.vppPermission.stv4.isChecked());
        }
        RestClient.builder()
                .url(Urls.LOCK_EKEY_PERMISSION_POST)
                .loader(EkeyPermissionModifyActivity.this)
                .params(params)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_EKEY_PERMISSION_POST", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            returnData();
                            finish();
                        } else {
                            toastBackendError(code, R.string.backend_error_generic);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        finish();
//                        toast(R.string.ekey_un_authorize_fail);
                    }
                })
                .build()
                .post();
    }

}
