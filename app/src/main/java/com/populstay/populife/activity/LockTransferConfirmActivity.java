package com.populstay.populife.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.android.material.tabs.TabLayout;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.constant.Constant;
import com.populstay.populife.databinding.ActivityLockTransferConfirmBinding;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.helper.ClickableTextHelper;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.RestClientBuilder;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

public class LockTransferConfirmActivity extends BaseActivity {
    private final Key mKey = MyApplication.CURRENT_KEY;
    private ActivityLockTransferConfirmBinding binding;
    private List<InputTypeTab> inputTypeTabs;



    public static void actionStart(Context context) {
        Intent intent = new Intent(context, LockTransferConfirmActivity.class);
        context.startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockTransferConfirmBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
    }



    private void initView(){
        binding.titleBar.pageTitle.setText(getString(R.string.setting_transfer_title));
        binding.titleBar.pageAction.setVisibility(View.GONE);
//        binding.titleBar
        setEnableBtn();
        setupTabLayout();
        if (isChineseLanguage()) {
            binding.etUserName.cCPicker.setDefaultCountryUsingNameCodeEx("CN");
        } else {
            binding.etUserName.cCPicker.setDefaultCountryUsingNameCodeEx("US");
        }
        binding.rlUserTerms.setOnClickListener(v -> {
            binding.cbAgree.toggle();
        });
        binding.etUserName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                setEnableBtn();
            }
        });
        binding.cbAgree.setOnCheckedChangeListener((view, v)->{
            setEnableBtn();
        });
        binding.tvContine.setOnClickListener(v -> {
            requestLockTransfer();
        });
        String desc = String.format(getString(R.string.setting_transfer_confirm_desc),
                "<b>" + mKey.getLockAlias() + "</b>",
                mKey.getLockName());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            binding.tvDesc.setText(Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY));
        } else {
            binding.tvDesc.setText(Html.fromHtml(desc));
        }
    }
    private void setupTabLayout() {
        initTabs();
        // 1. 添加 Tab
        for (InputTypeTab tab : inputTypeTabs) {
            binding.tlInputTabs.addTab(
                    binding.tlInputTabs.newTab().setText(tab.getTitle())
            );
        }

        // 2. 设置默认选中第一个
        binding.tlInputTabs.getTabAt(0).select();
        binding.etUserName.setType(inputTypeTabs.get(0).inputType);
        binding.etUserName.setHint(inputTypeTabs.get(0).title);
        binding.etUserName.setLabel(inputTypeTabs.get(0).title);

        // 3. 添加 Tab 选择监听
        binding.tlInputTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position < inputTypeTabs.size()) {
                    // 切换输入类型
                    InputTypeTab selectedTab = inputTypeTabs.get(position);
                    // 清空输入内容（可选）
                    binding.etUserName.setText("");
                    // 根据输入类型设置 hint
                    binding.etUserName.setHint(selectedTab.title);
                    binding.etUserName.setLabel(selectedTab.title);
                    binding.etUserName.setType(selectedTab.getInputType());
                    // 重新获取焦点
                    binding.etUserName.requestFocus();
                    // 显示合适的键盘
//                    showKeyboardForInputType(selectedTab.getInputType());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Tab 未选中时的处理
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Tab 重新选中时的处理
            }
        });
    }
    private void initTabs() {
        inputTypeTabs = new ArrayList<>();

        // 根据你的需求添加 Tab
        inputTypeTabs.add(new InputTypeTab(getString(R.string.email),
                ExEditText.TYPE_EMAIL));

        inputTypeTabs.add(new InputTypeTab(getString(R.string.phone),
                ExEditText.TYPE_PHONE));
    }

    private void requestLockTransfer(){
        String recUser = getUserName();
        if (recUser == null) {
            toast(getString(R.string.note_receiver_format));
        }
        PeachLogger.d("[ACTION] 转移锁 receiver=" + recUser);
        RestClient.builder()
                .url(Urls.LOCK_TRANSFER)
                .loader(this)
                .params("userId", PeachPreference.readUserId())
                .params("lockId", mKey.getLockId())
                .params("recUser", recUser)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_TRANSFER", response);
                        PeachLoader.stopLoading();
                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            EventBus.getDefault().post(new Event(Event.EventType.REFRESH_HOME_DATA));
                            toast(R.string.setting_transfer_request_success);
                            Intent intent = new Intent(LockTransferConfirmActivity.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                        } else if (code == 951) {
                            toast(R.string.setting_transfer_request_951);
                        } else if (code == 952) {
                            toast(R.string.setting_transfer_request_952);
                        } else if (code == 920) {
                            toast(R.string.setting_transfer_request_920);
                        } else {
                            toastBackendError(code, R.string.operation_fail);
                        }
                    }
                }).failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        PeachLoader.stopLoading();
                        toast(R.string.operation_fail);
                    }
                })
                .build()
                .post();
    }

    private String getUserName() {
        String recUser = binding.etUserName.getTextStr();
        if (binding.etUserName.getType() == ExEditText.TYPE_PHONE) {
            String code = binding.etUserName.cCPicker.getSelectedCountryCodeWithPlus();
            recUser = code + recUser;
            if (!StringUtil.isPhoneNumberValid(recUser, code)) {
                return null;
            }
        } else if (!StringUtil.isEmail(recUser)) {
            return null;
        }
        return recUser;
    }
    private void setEnableBtn() {
        binding.tvContine.setEnabled(binding.cbAgree.isChecked() && !binding.etUserName.getTextStr().isEmpty());
    }
    private static class InputTypeTab {
        private final String title;
        private final int inputType;

        public InputTypeTab(String title, int inputType) {
            this.title = title;
            this.inputType = inputType;
        }

        public String getTitle() { return title; }
        public int getInputType() { return inputType; }
    }
}

