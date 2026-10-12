package com.populstay.populife.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.constant.Constant;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.sign.ISignListener;
import com.populstay.populife.sign.SignHandler;
import com.populstay.populife.ui.widget.SwitchLanguagePopupWindow;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.ui.widget.language.SwitchLanguageBottomSheet;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.activity.ActivityCollector;
import com.populstay.populife.util.device.DeviceUtil;
import com.populstay.populife.util.locale.LanguageUtil;
import com.populstay.populife.util.locale.LocalManageUtils;
import com.populstay.populife.util.locale.SPUtils;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.populstay.populife.util.timer.BaseCountDownTimer;
import com.populstay.populife.util.timer.ITimerListener;
import com.rilixtech.widget.countrycodepicker.CountryCodePicker;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import androidx.annotation.Nullable;

/**
 * 登录、注册、找回密码
 * Created by Jerry
 */
public class SignActivity extends BaseActivity implements View.OnClickListener, ISignListener, ITimerListener,SwitchLanguagePopupWindow.SelectLanguageListener {

    private Context mContext;
	public static final String TAG = SignActivity.class.getSimpleName();

	public static final String VAL_ACCOUNT_SIGN_IN = "val_account_sign_in";
	public static final String VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE = "val_account_sign_in_by_verify_code";
	public static final String VAL_ACCOUNT_SIGN_UP = "val_account_sign_up";
	public static final String VAL_ACCOUNT_RESET_PWD = "val_account_reset_pwd";
	public static final String VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE = "val_account_reset_pwd_get_verify_code";
	public static final String VAL_ACCOUNT_SIGN_IN_BY_PHONE = "val_account_sign_in_by_phone";
	public static final String KEY_ACCOUNT_SIGN_ACTION_TYPE = "key_account_sign_action_type";
	public static final String KEY_CODE = "key_code";
	public static final String KEY_USERNAME = "key_username";
	public static final String KEY_SIGN_TYPE = "key_sign_type";
	public static final int RESET_PWD_REQUEST_CODE = 0;

	private LinearLayout ll_sign_up_action, ll_sign_in_action, ll_switch_language;
	private ImageView iv_logo;
	private RelativeLayout mRlBack, mRlUserTerms, top_bar;
	private TextView mTvPageTitle, mTvActionBtn, mTvForgetPwd, mTvSwitchSignType, mTvSwitchLanguage, tv_sign_up_action_btn, tv_goto_sign_in;
	//private ExTextView mTvUserTerms;
	private TextView mServicesTV, mPrivacyTV,tvAgree2;
	private CheckBox mCbUserTerms;
	private CountryCodePicker mCountryCodePicker;
	private ExEditText mEtUserName, mEtPwd, mEtConfirmPwd, mEtCode;

	private BaseCountDownTimer mTimer = null;
	private ISignListener mISignListener = this;
	private String mAccountActionType = VAL_ACCOUNT_SIGN_IN;
	private int mSignType = Constant.ACCOUNT_TYPE_PHONE;
	private String code;
	private String username;
	private SwitchLanguagePopupWindow mSwitchLanguagePopupWindow;
	private Locale mLocale;
	private SwitchLanguageBottomSheet dialog;

	/**
	 * 启动当前 activity
	 *
	 * @param context           上下文
	 * @param accountActionType 账号操作类型
	 */
	public static void actionStart(Context context, String accountActionType) {
		Intent intent = new Intent(context, SignActivity.class);
		intent.putExtra(KEY_ACCOUNT_SIGN_ACTION_TYPE, accountActionType);
		context.startActivity(intent);
	}

	public static void actionStartBySingleTop(Context context, String accountActionType) {
		Intent intent = new Intent(context, SignActivity.class);
		intent.setFlags(/*Intent.FLAG_ACTIVITY_NEW_TASK | */Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.putExtra(SignActivity.KEY_ACCOUNT_SIGN_ACTION_TYPE, accountActionType);
		context.startActivity(intent);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		getIntentData(intent);
		initUIStatus();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mContext = this;
		setContentView(R.layout.activity_sign);
		getIntentData(getIntent());

		initView();
		initUIStatus();
		initListener();
	}

	private void getIntentData(Intent intent) {
		mLocale = LanguageUtil.getLocaleByType(LanguageUtil.getLanguageType(this));
		mAccountActionType = intent.getStringExtra(KEY_ACCOUNT_SIGN_ACTION_TYPE);
		code = intent.getStringExtra(KEY_CODE);
		username = intent.getStringExtra(KEY_USERNAME);
		mSignType = intent.getIntExtra(KEY_SIGN_TYPE, Constant.ACCOUNT_TYPE_PHONE);
		PeachLogger.d(TAG, "mAccountActionType=" + mAccountActionType);
	}

	private void initUIStatus() {

		initUI(mAccountActionType);
//		setCountryInfo();
	}

	private void initView() {
		findViewById(R.id.page_action).setVisibility(View.GONE);
		ll_sign_up_action = findViewById(R.id.ll_sign_up_action);
		ll_switch_language = findViewById(R.id.ll_switch_language);
		iv_logo = findViewById(R.id.iv_logo);
		ll_sign_in_action = findViewById(R.id.ll_sign_in_action);
		mRlBack = findViewById(R.id.page_back);
		mRlUserTerms = findViewById(R.id.rl_user_terms);
		mTvPageTitle = findViewById(R.id.page_title);
		tv_sign_up_action_btn = findViewById(R.id.tv_sign_up_action_btn);
		tv_goto_sign_in = findViewById(R.id.tv_goto_sign_in);
		mTvActionBtn = findViewById(R.id.tv_sign_action_btn);
		mTvForgetPwd = findViewById(R.id.tv_forget_pwd);
		mTvSwitchSignType = findViewById(R.id.tv_switch_sign_type);
		mTvSwitchLanguage = findViewById(R.id.tv_switch_language);
		//mTvUserTerms = findViewById(R.id.tv_sign_user_terms);
		mServicesTV = findViewById(R.id.tvAgree);
//		mPrivacyTV = findViewById(R.id.tvPolicy);
		tvAgree2 = findViewById(R.id.tvAgree2);
		tvAgree2.setText(getUserTermsPrivacyPolicy());
		tvAgree2.setHighlightColor(Color.TRANSPARENT);//去掉点击效果
		tvAgree2.setMovementMethod(LinkMovementMethod.getInstance());//这句话必须有，
		mCbUserTerms = findViewById(R.id.cb_sign_user_terms);
		mEtUserName = findViewById(R.id.et_sign_user_name);
		mCountryCodePicker = mEtUserName.findViewById(R.id.cc_picker);
		if (isChineseLanguage()) {
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("CN");
		}else{
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("US");
		}
		mEtPwd = findViewById(R.id.et_sign_pwd);
		mEtConfirmPwd = findViewById(R.id.et_confirm_pwd);
		mEtCode = findViewById(R.id.et_sign_verification_code);
		top_bar = findViewById(R.id.top_bar);
	}

	private SpannableString getUserTermsPrivacyPolicy(){
		String readAndAgree = getString(R.string.read_and_agree);
		String userTermsPrivacyPolicy = getString(R.string.user_terms_privacy_policy);
		SpannableString spannableInfo= new SpannableString(readAndAgree + userTermsPrivacyPolicy);
		int start = readAndAgree.length();
		int end= readAndAgree.length()+userTermsPrivacyPolicy.length();
		spannableInfo.setSpan(new ClickableSpan() {
			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setUnderlineText(false);
			}
			@Override
			public void onClick(View widget) {
				goToNewActivity(PrivacyPolicyActivity.class);
			}
		}, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannableInfo.setSpan(new ForegroundColorSpan(Color.parseColor("#ee2737")), start, end,  Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return spannableInfo;
	}

	/**
	 * 设置国家信息（国家简称 + 国家码）
	 */
	private void setCountryInfo() {

		requestRuntimePermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION
				, Manifest.permission.READ_EXTERNAL_STORAGE
				, Manifest.permission.WRITE_EXTERNAL_STORAGE}, new PermissionListener() {
			@Override
			public void onGranted() {
				// 获取当前国家的国家码
				String countryNameCode = LanguageUtil.getCountryNameCode(SignActivity.this);
				mCountryCodePicker.setCountryForNameCode(countryNameCode);
			}

			@Override
			public void onDenied(List<String> deniedPermissions) {
				toast(R.string.note_permission);
			}
		});
	}

	private void setUIFeaturesByLanguage() {
		Resources resources = getResources();
//		Locale locale = LanguageUtil.getLocaleByType(LanguageUtil.getLanguageType(this));
//		PeachLogger.d("setUIFeaturesByLanguage locale = " + locale.getLanguage());

		// 获取当前应用的语言
		PeachLogger.d("setUIFeaturesByLanguage locale = " + LocalManageUtils.getSelectLanguage(this));

		if (isChineseLanguage()) {
			if (VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType)) {
				mTvSwitchSignType.setVisibility(View.VISIBLE);
				mTvSwitchSignType.setText(getResources().getString(R.string.sign_in_by_verify_code));
				mEtUserName.setType(ExEditText.TYPE_ACCOUNT);
				mEtUserName.setLabel("");
				mEtUserName.setHint(resources.getString(R.string.please_enter_your_cell_phone_number));
			} else if (VAL_ACCOUNT_SIGN_UP.equals(mAccountActionType)) {
				mEtUserName.setType(ExEditText.TYPE_ACCOUNT);
				mEtUserName.setLabel("");
				mEtUserName.setHint(resources.getString(R.string.please_enter_your_cell_phone_number));
			}
		} else {
			if (VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType)) {
				mTvSwitchSignType.setVisibility(View.VISIBLE);
				mTvSwitchSignType.setText(getResources().getString(R.string.sign_in_by_phone_no));
				mEtUserName.setType(ExEditText.TYPE_EMAIL);
				mEtUserName.setLabel(getResources().getString(R.string.email));
				mEtUserName.setHint(resources.getString(R.string.enter_your_email_address));
			} else if (VAL_ACCOUNT_SIGN_UP.equals(mAccountActionType)) {
				mEtUserName.setType(ExEditText.TYPE_EMAIL);
				mEtUserName.setLabel(getResources().getString(R.string.email));
				mEtUserName.setHint(resources.getString(R.string.enter_your_email_address));
			}
		}
	}

	/**
	 * 初始化界面 UI
	 *
	 * @param accountActionType 账号操作类型
	 *                          VAL_ACCOUNT_SIGN_IN		登录
	 *                          VAL_ACCOUNT_SIGN_UP		注册
	 *                          VAL_ACCOUNT_RESET_PWD	重置密码
	 */
	private void initUI(String accountActionType) {
		mRlBack.setVisibility(View.VISIBLE);
		mTvSwitchSignType.setVisibility(View.GONE);
		mTvForgetPwd.setVisibility(View.GONE);
		mEtConfirmPwd.setVisibility(View.GONE);
		ll_switch_language.setVisibility(View.GONE);
		ll_sign_up_action.setVisibility(View.GONE);
		ll_sign_in_action.setVisibility(View.GONE);
		mTvPageTitle.setVisibility(View.GONE);
		top_bar.setVisibility(View.GONE);
		iv_logo.setVisibility(View.GONE);
		switch (accountActionType) {
			case VAL_ACCOUNT_SIGN_IN:
				iv_logo.setVisibility(View.VISIBLE);
				ll_sign_up_action.setVisibility(View.VISIBLE);
				mTvPageTitle.setText(R.string.sign_in);
				mTvActionBtn.setText(R.string.sign_in);
				mEtCode.setVisibility(View.GONE);
				mEtPwd.setVisibility(View.VISIBLE);
				mRlUserTerms.setVisibility(View.VISIBLE);
				mTvSwitchSignType.setVisibility(View.VISIBLE);
				mTvForgetPwd.setVisibility(View.VISIBLE);
				mTvSwitchSignType.setText(getResources().getString(R.string.sign_in_by_verify_code));
				ll_switch_language.setVisibility(View.VISIBLE);
				break;

			case VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE:
				top_bar.setVisibility(View.VISIBLE);
				mTvPageTitle.setVisibility(View.VISIBLE);
				mTvPageTitle.setText(R.string.sign_in_by_verify_code);
				mTvActionBtn.setText(R.string.sign_in);
				mEtCode.setVisibility(View.VISIBLE);
				mEtPwd.setVisibility(View.GONE);
				mRlUserTerms.setVisibility(View.VISIBLE);
				mTvSwitchSignType.setText(getResources().getString(R.string.sign_in));
				break;

			case VAL_ACCOUNT_SIGN_IN_BY_PHONE:
				top_bar.setVisibility(View.VISIBLE);
				iv_logo.setVisibility(View.GONE);
				mTvSwitchSignType.setVisibility(View.GONE);
				ll_sign_up_action.setVisibility(View.GONE);
				mTvPageTitle.setVisibility(View.VISIBLE);
				mTvPageTitle.setText(R.string.sign_in_by_phone_number);
				mTvActionBtn.setText(R.string.sign_in);
				mEtCode.setVisibility(View.GONE);
				mEtPwd.setVisibility(View.VISIBLE);
				mRlUserTerms.setVisibility(View.VISIBLE);
				mTvForgetPwd.setVisibility(View.VISIBLE);
				ll_switch_language.setVisibility(View.GONE);
				mEtUserName.setHint(getResources().getString(R.string.please_enter_your_cell_phone_number));
				break;

			case VAL_ACCOUNT_SIGN_UP:
				iv_logo.setVisibility(View.VISIBLE);
				ll_sign_in_action.setVisibility(View.VISIBLE);
				mRlBack.setVisibility(View.GONE);
				mTvPageTitle.setText(R.string.sign_up);
				mTvActionBtn.setText(R.string.sign_up);
				mTvSwitchSignType.setVisibility(View.GONE);
				mRlUserTerms.setVisibility(View.VISIBLE);
				mEtConfirmPwd.setVisibility(View.VISIBLE);
				mEtCode.setVisibility(View.VISIBLE);
				ll_switch_language.setVisibility(View.VISIBLE);
				break;

			case VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE:
				top_bar.setVisibility(View.VISIBLE);
				mTvPageTitle.setVisibility(View.VISIBLE);
				mEtPwd.setVisibility(View.GONE);
				mEtConfirmPwd.setVisibility(View.GONE);
				mTvPageTitle.setText(R.string.reset_pwd);
				mTvActionBtn.setText(R.string.next_step);
				mRlUserTerms.setVisibility(View.GONE);
				break;

			case VAL_ACCOUNT_RESET_PWD:
				top_bar.setVisibility(View.VISIBLE);
				mTvPageTitle.setVisibility(View.VISIBLE);
				mEtPwd.setVisibility(View.VISIBLE);
				mEtConfirmPwd.setVisibility(View.VISIBLE);
				mTvPageTitle.setText(R.string.reset_pwd);
				mTvActionBtn.setText(R.string.reset_pwd);
				mRlUserTerms.setVisibility(View.GONE);
				mEtCode.setVisibility(View.GONE);
				mEtUserName.setVisibility(View.GONE);
				break;

			default:
				break;
		}
		setUIFeaturesByLanguage();
		setCurrentLanLabel();
	}

	private void initListener() {
		tv_sign_up_action_btn.setOnClickListener(this);
		tv_goto_sign_in.setOnClickListener(this);
		mTvActionBtn.setOnClickListener(this);
		mTvForgetPwd.setOnClickListener(this);
		mTvSwitchSignType.setOnClickListener(this);
		ll_switch_language.setOnClickListener(this);
		mEtUserName.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				// 正在发送验证码过程中，账号发生变化，恢复验证码初始状态
				resetVerifictionCodeView();
				setEnableGetCodeBtn();
				setEnableActionBtn();
				checkUserNameType();
			}
		});
		mEtPwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableActionBtn();
				//checkPwdValidity();
			}
		});
		mEtConfirmPwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableActionBtn();
				//checkPwdValidity();
			}
		});
		mEtCode.getVerifictionCodeView().setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (checkCodeForm()) {
					// 获取验证码
					getVerificationCode();
				}
			}
		});
		mEtCode.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableActionBtn();
			}
		});

		mCbUserTerms.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				setEnableActionBtn();
			}
		});

//		mTvUserTerms.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				mCbUserTerms.setChecked(!mCbUserTerms.isChecked());
//			}
//		});
//		String agreeUserTerms = getResources().getString(R.string.note_sign_up_agree_user_terms);
//		mTvUserTerms.setText(agreeUserTerms, agreeUserTerms.indexOf("["), agreeUserTerms.lastIndexOf("]") + 1, new View.OnClickListener() {
//			@Override
//			public void onClick(View view) {
//				goToNewActivity(PrivacyPolicyActivity.class);
//			}
//		});
//
		mServicesTV.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				goToNewActivity(PrivacyPolicyActivity.class);
			}
		});

	}

//	private void pushPrivacyActivity (int flag) {
//		String filePath = LanguageUtil.isChinese(this) ? "file:///android_asset/privacy_policy_cn.html"
//				: "file:///android_asset/privacy_policy_en.html";
//		String urlPath = flag == 0:filePath:
//		Intent intent = new Intent(this, PrivacyPolicyActivity.class);
//		intent.putExtra(KEY_DEVICE_TYPE, deviceType);
//		startActivity(intent);
//	}

	private void checkUserNameType() {
		if (StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + mEtUserName.getTextStr(), mCountryCodePicker.getSelectedCountryCodeWithPlus())) {
			mSignType = Constant.ACCOUNT_TYPE_PHONE;
		} else {
			mSignType = Constant.ACCOUNT_TYPE_EMAIL;
		}
	}

	private void checkPwdValidity() {
		String pwd = mEtPwd.getTextStr();
		String confirmPwd = mEtConfirmPwd.getTextStr();
		int lenPwd = pwd.length();
		int lenConfirmPwd = confirmPwd.length();
		if (lenPwd > 0 || lenConfirmPwd > 0) {
			mEtPwd.showEditCheckHint(lenPwd < 8, "密码至少8位");
		}

		if (lenConfirmPwd > 0) {
			mEtConfirmPwd.showEditCheckHint(!pwd.equals(confirmPwd), "两次录入密码不一致");
		}
	}

	private void setEnableGetCodeBtn() {
		mEtCode.getVerifictionCodeView().setEnabled(!TextUtils.isEmpty(mEtUserName.getTextStr()));
	}

	private void setEnableActionBtn() {
		boolean isNotEmptyUserName = !TextUtils.isEmpty(mEtUserName.getTextStr());
		boolean isNotEmptyPwd = !TextUtils.isEmpty(mEtPwd.getTextStr());
		boolean isNotEmptyConfirmPwd = !TextUtils.isEmpty(mEtConfirmPwd.getTextStr());
		boolean isNotEmptyCode = !TextUtils.isEmpty(mEtCode.getTextStr());
		boolean isSelectedUserTerms = mCbUserTerms.isChecked();

		boolean isEnable = false;

		switch (mAccountActionType) {
			case VAL_ACCOUNT_SIGN_IN:
			case VAL_ACCOUNT_SIGN_IN_BY_PHONE:
				isEnable = isNotEmptyUserName && isNotEmptyPwd && isSelectedUserTerms;
				break;
			case VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE:
				isEnable = isNotEmptyUserName && isNotEmptyCode && isSelectedUserTerms;
				break;
			case VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE:
				isEnable = isNotEmptyUserName && isNotEmptyCode;
				break;
			case VAL_ACCOUNT_SIGN_UP:
				isEnable = isNotEmptyUserName && isNotEmptyPwd && isNotEmptyConfirmPwd && isNotEmptyCode && isSelectedUserTerms;
				break;
			case VAL_ACCOUNT_RESET_PWD:
				isEnable = isNotEmptyPwd && isNotEmptyConfirmPwd;
				break;
		}

		mTvActionBtn.setEnabled(isEnable);
	}
    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_sign_up_action_btn || id == R.id.tv_goto_sign_in) {
            if (VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE.equals(mAccountActionType)
                    || VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType)) {
                actionStartBySingleTop(SignActivity.this, VAL_ACCOUNT_SIGN_UP);
            } else {
                actionStartBySingleTop(SignActivity.this, VAL_ACCOUNT_SIGN_IN);
            }
        } else if (id == R.id.tv_sign_action_btn) {
            if (checkForm()) {
                if (VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE.equals(mAccountActionType)) {
                    signInByCode();
                } else if (VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType)
                        || VAL_ACCOUNT_SIGN_IN_BY_PHONE.equals(mAccountActionType)) {
                    signIn();
                } else if (VAL_ACCOUNT_SIGN_UP.equals(mAccountActionType)) {
                    signUp();
                } else if (VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE.equals(mAccountActionType)) {
                    gotoResetPwd();
                } else if (VAL_ACCOUNT_RESET_PWD.equals(mAccountActionType)) {
                    resetPwd();
                }
            }
        } else if (id == R.id.tv_forget_pwd) {
            actionStart(SignActivity.this, VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE);
        } else if (id == R.id.tv_switch_sign_type) {
            if ("ZH".equals(mLocale.getLanguage().toUpperCase())) {
                actionStart(SignActivity.this,
                        VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType) ? VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE : VAL_ACCOUNT_SIGN_IN);
            } else {
                actionStart(SignActivity.this,
                        VAL_ACCOUNT_SIGN_IN.equals(mAccountActionType) ? VAL_ACCOUNT_SIGN_IN_BY_PHONE : VAL_ACCOUNT_SIGN_IN);
            }
        } else if (id == R.id.ll_switch_language) {
            showSwitchLanguageDialog();
        }
    }

	private void setCurrentLanLabel() {
//		Locale locale = LanguageUtil.getLocaleByType(LanguageUtil.getLanguageType(this));
		Locale locale = LocalManageUtils.getSelectLanguageLocal(SignActivity.this);

		String lan = locale.getLanguage().toUpperCase();
		// 中文都显示成CN，没有中文繁体
		if ("ZH".equals(lan)) {
			mTvSwitchLanguage.setText("CN");
		} else if ("JA".equals(lan)) {
			mTvSwitchLanguage.setText("JP");
		} else {
			mTvSwitchLanguage.setText(lan);
		}
	}

	private void showSwitchLanguageDialog() {
		dialog = new SwitchLanguageBottomSheet();
		dialog.setSelectLanguageListener(this);
		dialog.show(getSupportFragmentManager(), "language_dialog");
	}

	private void showSwitchLanguagePopupWindow(View anchor) {
		if (null == mSwitchLanguagePopupWindow) {
			mSwitchLanguagePopupWindow = new SwitchLanguagePopupWindow(this);
			mSwitchLanguagePopupWindow.setSelectLanguageListener(new SwitchLanguagePopupWindow.SelectLanguageListener() {
				@Override
				public void onSelectLanguage(int languageType) {
					changeLanguage(languageType);
				}
			});
		}
		mSwitchLanguagePopupWindow.show(anchor, Gravity.TOP);
	}

//	private void changeLanguage(int languageType) {
//		boolean sameLanguage = LanguageUtil.isSameLanguage(languageType);
//		if (!sameLanguage) {
//			LanguageUtil.setLocale(languageType);
//			LanguageUtil.putLanguageType(languageType);
//			sendCurrentLanguage(languageType);
//			setCurrentLanLabel();
//			actionStart(this, mAccountActionType);
//			finish();
//		} else {
//			// 设置完语言后缓存type
//			LanguageUtil.putLanguageType(languageType);
//		}
//	}

	private void changeLanguage(int languageType) {
		int type = SPUtils.getInstance(this).getLanguage();
		boolean sameLanguage = type == languageType;
		if (!sameLanguage) {
			sendCurrentLanguage(languageType);
			setCurrentLanLabel();
			actionStart(this, mAccountActionType);
			finish();
		}

		LocalManageUtils.setSelectLanguage(this, languageType);
	}

	/**
	 * 请求服务器，告知当前语言环境
	 */
	private void sendCurrentLanguage(final int languageType) {
//		Locale locale = LanguageUtil.getLocaleByType(languageType);
		Locale locale = LocalManageUtils.getSelectLanguageLocal(SignActivity.this);
		RestClient.builder()
				.url(Urls.USER_LANGUAGE_SWITCH)
				.loader(SignActivity.this)
				// 语种，取值：en_US（英语），zh_CN（简体中文），ja_JP（日语）
				.params("locale", locale)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
					}
				})
				.build()
				.get();
	}

	/**
	 * 获取验证码
	 */
	private void getVerificationCode() {
		if (mAccountActionType.equals(VAL_ACCOUNT_SIGN_UP)) {
			// 注册时，获取验证码
			WeakHashMap<String, Object> params = new WeakHashMap<>();
			params.put("type", mSignType);
			params.put("username", mSignType == Constant.ACCOUNT_TYPE_PHONE ? mCountryCodePicker.getSelectedCountryCodeWithPlus()
					+ mEtUserName.getText().toString() : mEtUserName.getText().toString());
			if (mSignType == Constant.ACCOUNT_TYPE_PHONE) { // 使用手机注册时，需传入国家编码（如：+86）
				params.put("country", mCountryCodePicker.getSelectedCountryCodeWithPlus());
			}
			// 获取验证码成功，开始倒计时
			startTimer();
			RestClient.builder()
					.url(Urls.VERIFICATION_CODE_REGISTER_OR_BIND)
					.params(params)
					.success(new ISuccess() {
						@Override
						public void onSuccess(String response) {
							PeachLogger.d("GET_VIRIFICATION_CODE_REGISTER", response);
							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
							switch (code) {
								case 200:
									if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
										toast(R.string.note_success_get_verification_code_phone);
									} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
										toast(R.string.note_success_get_verification_code_email);
									}

									break;

								case 951:
									toast(R.string.note_phone_has_been_registered);
									resetVerifictionCodeView();
									break;

								case 952:
									resetVerifictionCodeView();
									toast(R.string.note_email_has_been_registered);
									break;

								default:
									resetVerifictionCodeView();
									toastBackendError(code, R.string.note_get_verification_code_fail);
									break;
							}
						}
					})
					.failure(new IFailure() {
						@Override
						public void onFailure() {
							resetVerifictionCodeView();
							toast(R.string.note_get_verification_code_fail);
						}
					})
					.build()
					.post();
		} else if (mAccountActionType.equals(VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE)) {
			//（忘记密码后）重置密码时，获取验证码
			WeakHashMap<String, Object> params = new WeakHashMap<>();
			params.put("username", mSignType == Constant.ACCOUNT_TYPE_PHONE ? mCountryCodePicker.getSelectedCountryCodeWithPlus()
					+ mEtUserName.getText().toString() : mEtUserName.getText().toString());
			if (mSignType == Constant.ACCOUNT_TYPE_PHONE) { // 使用手机找回密码时，需传入国家编码（如：+86）
				params.put("country", mCountryCodePicker.getSelectedCountryCodeWithPlus());
			}
            startTimer();
			RestClient.builder()
					.url(Urls.VERIFICATION_CODE_RESETPWD_DELETEACCOUNT_NEWDEVICELOGIN)
					.params(params)
					.success(new ISuccess() {
						@Override
						public void onSuccess(String response) {
							PeachLogger.d("GET_VIRIFICATION_CODE_RESET_PWD", response);
							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
                            String msg = result.getString("msg");
							switch (code) {
								case 200:
									// 获取验证码成功，开始倒计时
									if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
										toast(R.string.note_success_get_verification_code_phone);
									} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
										toast(R.string.note_success_get_verification_code_email);
									}

									break;

								case 920:
									if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
										toast(R.string.note_phone_has_not_been_registered);
									} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
										toast(R.string.note_email_has_not_been_registered);
									}
									break;
//                                    频繁获取验证码
                                case 955:
                                    toast(Utils.get955CodeLimit(mContext, msg));
                                    break;
								default:
									toastBackendError(code, R.string.note_get_verification_code_fail);
									break;
							}
						}
					})
					.failure(new IFailure() {
						@Override
						public void onFailure() {
							toast(R.string.note_get_verification_code_fail);
						}
					})
					.build()
					.post();
		} else if (mAccountActionType.equals(VAL_ACCOUNT_SIGN_IN_BY_VERIFY_CODE)) {
			// 验证码登录方式，获取验证码
			WeakHashMap<String, Object> params = new WeakHashMap<>();
			params.put("username", mSignType == Constant.ACCOUNT_TYPE_PHONE ? mCountryCodePicker.getSelectedCountryCodeWithPlus()
					+ mEtUserName.getText().toString() : mEtUserName.getText().toString());
			if (mSignType == Constant.ACCOUNT_TYPE_PHONE) { // 使用手机找回密码时，需传入国家编码（如：+86）
				params.put("country", mCountryCodePicker.getSelectedCountryCodeWithPlus());
			}
            startTimer();
			RestClient.builder()
					.url(Urls.USER_LOGIN_BYCODE_SEND_CODE)
					.params(params)
					.success(new ISuccess() {
						@Override
						public void onSuccess(String response) {
							PeachLogger.d("USER_LOGIN_BYCODE_SEND_CODE", response);
							JSONObject result = JSON.parseObject(response);
							int code = result.getInteger("code");
                            String msg = result.getString("msg");
							switch (code) {
								case 200:
									// 获取验证码成功，开始倒计时

									if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
										toast(R.string.note_success_get_verification_code_phone);
									} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
										toast(R.string.note_success_get_verification_code_email);
									}

									break;

								case 920:
									if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
										toast(R.string.note_phone_has_not_been_registered);
									} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
										toast(R.string.note_email_has_not_been_registered);
									}
									break;
//                                    频繁获取验证码
                                case 955:
                                    toast(Utils.get955CodeLimit(mContext, msg));
                                    break;
								default:
									toastBackendError(code, R.string.note_get_verification_code_fail);
									break;
							}
						}
					})
					.failure(new IFailure() {
						@Override
						public void onFailure() {
							toast(R.string.note_get_verification_code_fail);
						}
					})
					.build()
					.post();
		}
	}

	/**
	 * 开始倒计时
	 */
	private void startTimer() {
		mTimer = new BaseCountDownTimer(60, SignActivity.this);
		mTimer.start();
	}

	/**
	 * 停止倒计时
	 */
	private void stopTimer() {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	/**
	 * 验证码登录
	 */
	private void signInByCode() {
		final String countryCode = mCountryCodePicker.getSelectedCountryCodeWithPlus();
		final String userName = mSignType == Constant.ACCOUNT_TYPE_PHONE ? countryCode + mEtUserName.getText().toString()
				: mEtUserName.getText().toString();
		final String code = mEtCode.getText().toString();
		RestClient.builder()
				.url(Urls.USER_LOGIN_BYCODE)
				.loader(SignActivity.this)
				.params("username", userName)
				.params("code", code)
				.params("deviceId", DeviceUtil.getDeviceId(SignActivity.this))
				.success(new ISuccess() {
					@Override
					public void onSuccess(final String response) {
						PeachLogger.d("SIGN_IN", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						switch (code) {
							case 200:
								JSONObject accountInfo = result.getJSONObject("data");
								//处理异地登录
								if (DeviceUtil.isIgnoreAccountRemoteLoginVerify(userName)){ // 测试账号，不检测异地登录，直接登录进入主页
									PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
									SignHandler.onSignIn(response, mISignListener);
								} else {
									if (!Constant.DEBUG && (accountInfo.containsKey("phone") || accountInfo.containsKey("email"))) {// 新设备登录，跳转到验证码验证页面
										PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
										LoginVerifyActivity.actionStart(SignActivity.this, mSignType, countryCode, userName, response, "");
									} else {//没有异地登录（依旧在同一设备登录）
										PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
										SignHandler.onSignIn(response, mISignListener);
									}
								}
								break;

							case 910:
								if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
									toast(R.string.note_phone_or_pwd_incorrect);
								} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
									toast(R.string.note_email_or_pwd_incorrect);
								}
								break;

							default:
								toastBackendError(code, R.string.note_sign_in_fail);
								break;
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_sign_in_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 登录
	 */
	private void signIn() {
		final String countryCode = mCountryCodePicker.getSelectedCountryCodeWithPlus();
		final String userName = mSignType == Constant.ACCOUNT_TYPE_PHONE ? countryCode + mEtUserName.getText().toString()
				: mEtUserName.getText().toString();
		final String loginPwd = mEtPwd.getText().toString();
		RestClient.builder()
				.url(Urls.SIGN_IN)
				.loader(SignActivity.this)
				.params("username", userName)
				.params("password", loginPwd)
				.params("deviceId", DeviceUtil.getDeviceId(SignActivity.this))
				.success(new ISuccess() {
					@Override
					public void onSuccess(final String response) {
						PeachLogger.d("SIGN_IN", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						switch (code) {
							case 200:
								JSONObject accountInfo = result.getJSONObject("data");
								//处理异地登录
								if (DeviceUtil.isIgnoreAccountRemoteLoginVerify(userName)){ // 测试账号，不检测异地登录，直接登录进入主页
									PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
									SignHandler.onSignIn(response, mISignListener);
								} else if (!Constant.DEBUG && (accountInfo.containsKey("phone") || accountInfo.containsKey("email"))) {// 新设备登录，跳转到验证码验证页面
									PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
									LoginVerifyActivity.actionStart(SignActivity.this, mSignType, countryCode, userName, response, loginPwd);
								} else {//没有异地登录（依旧在同一设备登录）
									PeachPreference.putStr(PeachPreference.ACCOUNT_PWD, loginPwd);
									PeachPreference.putStr(PeachPreference.ACCOUNT, userName);
									SignHandler.onSignIn(response, mISignListener);
								}
								break;

							case 910:
								if (mSignType == Constant.ACCOUNT_TYPE_PHONE) {
									toast(R.string.note_phone_or_pwd_incorrect);
								} else if (mSignType == Constant.ACCOUNT_TYPE_EMAIL) {
									toast(R.string.note_email_or_pwd_incorrect);
								}
								break;

							default:
								toastBackendError(code, R.string.note_sign_in_fail);
								break;
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_sign_in_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 注册
	 */
	private void signUp() {
		RestClient.builder()
				.url(Urls.SIGN_UP)
				.loader(SignActivity.this)
				.params("username", mSignType == Constant.ACCOUNT_TYPE_PHONE ? mCountryCodePicker.getSelectedCountryCodeWithPlus()
						+ mEtUserName.getText().toString() : mEtUserName.getText().toString())
				.params("password", mEtPwd.getText().toString())
				.params("code", mEtCode.getText().toString())
				.params("type", mSignType)
				.params("deviceId", DeviceUtil.getDeviceId(SignActivity.this))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("SIGN_UP", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						switch (code) {
							case 200:
								PeachPreference.putStr(PeachPreference.ACCOUNT_PWD, mEtPwd.getText().toString());
								SignHandler.onSignUp(response, mISignListener);
								break;

							case 951:
								toast(R.string.note_phone_has_been_registered);
								break;

							case 952:
								toast(R.string.note_email_has_been_registered);
								break;
							// 953验证码已过期,954验证码错误
							case 953:
							case 954:
								toast(R.string.note_verifiction_code_invalid);
								break;

							default:
								toastBackendError(code, R.string.note_register_fail);
								break;
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_register_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 跳转重置密码页面
	 */
	private void gotoResetPwd() {
		if (!TextUtils.isEmpty(mEtCode.getText().toString().trim())) {

			Intent intent = new Intent(this, SignActivity.class);
			intent.putExtra(KEY_ACCOUNT_SIGN_ACTION_TYPE, VAL_ACCOUNT_RESET_PWD);
			intent.putExtra(KEY_CODE, mEtCode.getText().toString().trim());
			intent.putExtra(KEY_USERNAME, mEtUserName.getText().toString().trim());
			intent.putExtra(KEY_SIGN_TYPE, mSignType);
			startActivityForResult(intent, RESET_PWD_REQUEST_CODE);
		}
	}

	/**
	 * 重置密码
	 */
	private void resetPwd() {
		RestClient.builder()
				.url(Urls.ACCOUNT_PWD_RESET)
				.loader(SignActivity.this)
				.params("username", mSignType == Constant.ACCOUNT_TYPE_PHONE ? mCountryCodePicker.getSelectedCountryCodeWithPlus()
						+ username : username)
				.params("password", mEtPwd.getText().toString())
				.params("code", code)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("ACCOUNT_PWD_RESET", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_reset_pwd_success);
							setResult(RESULT_OK);
							finish();
						} else if (code == 954 || code == 953) {
							toast(R.string.note_verifiction_code_invalid);
						} else {
							toastBackendError(code, R.string.note_reset_pwd_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_reset_pwd_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_reset_pwd_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 切换登录/注册方式
	 *
	 * @param signType 登录/注册方式
	 *                 Constant.ACCOUNT_TYPE_PHONE 手机登录/注册
	 *                 Constant.ACCOUNT_TYPE_EMAIL 邮箱登录/注册
	 */
	public void changeSignType(int signType) {
		if (mSignType != signType) {
			mSignType = signType;
			switch (signType) {
				case Constant.ACCOUNT_TYPE_PHONE:

					mEtUserName.setHint(getString(R.string.enter_phone_num));
					//mEtUserName.setInputType(InputType.TYPE_CLASS_NUMBER);

					//mLlCountry.setVisibility(View.VISIBLE);
					break;

				case Constant.ACCOUNT_TYPE_EMAIL:

					mEtUserName.setHint(getString(R.string.enter_email));
					//mEtUserName.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

					//mLlCountry.setVisibility(View.GONE);
					break;

				default:
					break;
			}
			mEtUserName.setText("");
			mEtPwd.setText("");
			mEtCode.setText("");

			stopTimer();
			mEtCode.getVerifictionCodeView().setEnabled(true);
			mEtCode.getVerifictionCodeView().setText(getString(R.string.get_code));
		}
	}

	/**
	 * 获取验证码时，检查手机号或邮箱
	 */
	private boolean checkCodeForm() {
		final String userName = mEtUserName.getText().toString().trim();

		boolean isPass = true;

		switch (mSignType) {
			case Constant.ACCOUNT_TYPE_PHONE:
				if (userName.isEmpty()) {
					toast(R.string.note_phone_invalid);
					isPass = false;
				}
				break;

			case Constant.ACCOUNT_TYPE_EMAIL:
				if (userName.isEmpty() || !StringUtil.isEmail(userName)) {
					toast(R.string.note_email_invalid);
					isPass = false;
				}
				break;

			default:
				break;
		}
		return isPass;
	}

	private boolean checkForm() {
		final String userName = mEtUserName.getText().toString().trim();
		final String pwd = mEtPwd.getText().toString().trim();
		final String confirmPwd = mEtConfirmPwd.getText().toString().trim();
		final String code = mEtCode.getText().toString().trim();

		boolean isPass = true;

		if (!userName.isEmpty()) {
			if (StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + userName, mCountryCodePicker.getSelectedCountryCodeWithPlus())) {
				mSignType = Constant.ACCOUNT_TYPE_PHONE;
			} else {
				mSignType = Constant.ACCOUNT_TYPE_EMAIL;
			}
		}

		switch (mAccountActionType) {
			case VAL_ACCOUNT_SIGN_IN_BY_PHONE:
				if (!StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + userName, mCountryCodePicker.getSelectedCountryCodeWithPlus())) {
					toast(R.string.note_phone_invalid);
					isPass = false;
				} else if (pwd.isEmpty() || pwd.length() < 6) {
					toast(R.string.note_pwd_format);
					isPass = false;
				}
				break;
			case VAL_ACCOUNT_SIGN_IN:
				if (isChineseLanguage()) {
					if (!StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + userName, mCountryCodePicker.getSelectedCountryCodeWithPlus()) && !StringUtil.isEmail(userName)) {
						toast(R.string.note_phone_or_email_invalid);
						isPass = false;
						break;
					}
				} else {
					if (!StringUtil.isEmail(userName)) {
						toast(R.string.note_email_invalid);
						isPass = false;
						break;
					}
				}

				if (pwd.isEmpty() || pwd.length() < 6) {
					toast(R.string.note_pwd_format);
					isPass = false;
				}
				break;
			case VAL_ACCOUNT_RESET_PWD_GET_VERIFY_CODE:
				if (!StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + userName, mCountryCodePicker.getSelectedCountryCodeWithPlus()) && !StringUtil.isEmail(userName)) {
					toast(R.string.note_phone_or_email_invalid);
					isPass = false;
				}
				break;

			case VAL_ACCOUNT_SIGN_UP:
				if (isChineseLanguage()) {
					if (!StringUtil.isPhoneNumberValid(mCountryCodePicker.getSelectedCountryCodeWithPlus() + userName, mCountryCodePicker.getSelectedCountryCodeWithPlus())) {
						toast(R.string.note_phone_invalid);
						isPass = false;
						break;
					}
				} else {
					if (!StringUtil.isEmail(userName)) {
						toast(R.string.note_email_invalid);
						isPass = false;
						break;
					}
				}

				if (pwd.isEmpty() || pwd.length() < 6) {
					toast(R.string.note_pwd_format);
					isPass = false;
				} else if (!pwd.equals(confirmPwd)) {
					toast(R.string.note_confirm_pwd_not_format);
					isPass = false;
				} else if (code.isEmpty()) {
					toast(R.string.note_verifiction_code_invalid);
					isPass = false;
				}
				break;
			case VAL_ACCOUNT_RESET_PWD:
				if (pwd.isEmpty() || pwd.length() < 6) {
					toast(R.string.note_pwd_format);
					isPass = false;
				} else if (!pwd.equals(confirmPwd)) {
					toast(R.string.note_confirm_pwd_not_format);
					isPass = false;
				}
				break;

			default:
				break;
		}
		return isPass;
	}

	@Override
	public void onSignInSuccess() {
		goToNewActivity(MainActivity.class);
		ActivityCollector.finishAll();
	}

	@Override
	public void onSignUpSuccess() {
		// 注册成功后，自动登录
		signIn();
	}

	@Override
	public void onSignOutSuccess() {
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (null != mSwitchLanguagePopupWindow) {
			mSwitchLanguagePopupWindow.dismiss();
		}
		if (null != dialog) {
			dialog.dismiss();
		}
		stopTimer();
	}

	@Override
	public void onTimerTick(final long secondsLeft) {
		mEtCode.getVerifictionCodeView().setEnabled(false);
		mEtCode.getVerifictionCodeView().setText(MessageFormat.format("{0} s", secondsLeft));
	}

	@Override
	public void onTimerFinish() {
		resetVerifictionCodeView();
	}

	private void resetVerifictionCodeView() {
		stopTimer();
		mEtCode.setText("");
		mEtCode.getVerifictionCodeView().setEnabled(true);
		mEtCode.getVerifictionCodeView().setText(getString(R.string.get_code));
	}

	@Override
	protected void queryLatestDeviceId() {

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (RESET_PWD_REQUEST_CODE == requestCode && RESULT_OK == resultCode) {
			finish();
		}
	}

	@Override
	public void onSelectLanguage(int languageType) {
		changeLanguage(languageType);
	}
}
