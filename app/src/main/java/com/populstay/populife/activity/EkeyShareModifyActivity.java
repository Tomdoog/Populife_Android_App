package com.populstay.populife.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyCreateSuccessActivity;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.CreatePwdKeyActionInfo;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.locale.LanguageUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.string.StringUtil;
import com.rilixtech.widget.countrycodepicker.CountryCodePicker;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import androidx.annotation.Nullable;
//import cn.sharesdk.framework.Platform;
//import cn.sharesdk.framework.PlatformActionListener;
//import cn.sharesdk.onekeyshare.OnekeyShare;

public class EkeyShareModifyActivity extends BaseActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

	public static final String KEY_KEY_ID = "key_key_id";
	public static final String KEY_SHARE_TYPE = "shareKeyThrough";
	public static final String KEY_ITEM = "key_item";
	private final int REQUEST_CONTACT = 3;

	private TextView mTvSave;

	private RadioGroup rg_share_the_key_through;
	private int mKeyId;
	private int shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.ACCOUNT;//分享类型(1账号,2短信链接)
	private LinearLayout ll_receiver;
	private EditText mEtReceiver;
	private KeyPwd mKeyPwd;
	private CountryCodePicker mCountryCodePicker;
	private ImageView mIvContact;
	private TextView tv_share_the_key_through_hint;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ekey_share_modify);

		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mKeyId = data.getIntExtra(KEY_KEY_ID, 1);
		shareKeyThrough = data.getIntExtra(KEY_SHARE_TYPE, KeyPwdConstant.IBTKeyShareThrough.ACCOUNT);
		mKeyPwd = data.getParcelableExtra(KEY_ITEM);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.share_digital_key);
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvSave = findViewById(R.id.tv_save_btn);

		tv_share_the_key_through_hint = findViewById(R.id.tv_share_the_key_through_hint);

		ll_receiver = findViewById(R.id.ll_receiver);
		mEtReceiver = findViewById(R.id.et_lock_send_ekey_receiver);
		mCountryCodePicker = findViewById(R.id.cpp_lock_send_ekey);
		if (isChineseLanguage()) {
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("CN");
		}else{
			mCountryCodePicker.setDefaultCountryUsingNameCodeEx("US");
		}
		mIvContact = findViewById(R.id.iv_lock_send_ekey_receiver);
//		setCountryInfo();

		rg_share_the_key_through = findViewById(R.id.rg_share_the_key_through);

		RadioButton rb_share;
		if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT) {
			rb_share = (RadioButton) rg_share_the_key_through.getChildAt(0);
			ll_receiver.setVisibility(View.VISIBLE);
		} else {
			rb_share = (RadioButton) rg_share_the_key_through.getChildAt(1);
			ll_receiver.setVisibility(View.GONE);
		}
		rb_share.setChecked(true);
	}

	/**
	 * 设置国家信息（国家简称 + 国家码）
	 */
	private void setCountryInfo() {

		requestRuntimePermissions(new String[]{Manifest.permission.READ_PHONE_STATE,
				Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, new PermissionListener() {
			@Override
			public void onGranted() {
				// 获取当前国家的国家码
				String countryNameCode = LanguageUtil.getCountryNameCode(EkeyShareModifyActivity.this);
				mCountryCodePicker.setCountryForNameCode(countryNameCode);
			}

			@Override
			public void onDenied(List<String> deniedPermissions) {
				toast(R.string.note_permission);
			}
		});
	}

	private void initListener() {
		mTvSave.setOnClickListener(this);
		mIvContact.setOnClickListener(this);
		rg_share_the_key_through.setOnCheckedChangeListener(this);
		mEtReceiver.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				setSendBtnEnable();
				mCountryCodePicker.setVisibility(editable.length() == 0 ||
						StringUtil.isNum(editable.toString()) ? View.VISIBLE : View.GONE);
			}
		});
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.tv_save_btn) {
            if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT) {
                sendKeyByAccount(mEtReceiver.getText().toString());
            } else {
                showShareBTKey(mKeyPwd.getShareKeyUrl());
            }
        } else if (id == R.id.iv_lock_send_ekey_receiver) {
        /*
        requestRuntimePermissions(new String[]{Manifest.permission.READ_CONTACTS}, new PermissionListener() {
            @Override
            public void onGranted() {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_PICK);
                intent.setData(ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(intent, REQUEST_CONTACT);
            }

            @Override
            public void onDenied(List<String> deniedPermissions) {
                toast(getString(R.string.note_permission_contact));
            }
        });
        */
        }
    }

    public void showShareBTKey(String data) {
		 //oks = new OnekeyShare();
		final String msg = String.format(getResources().getString(R.string.share_bt_key_text), data);
		try {
			Intent vIt = new Intent(Intent.ACTION_SEND);
//							vIt.setPackage("com.facebook.orca");
			vIt.setType("text/plain");
			vIt.putExtra(Intent.EXTRA_TEXT, msg);
			startActivity(vIt);
		} catch (Exception e) {
			e.printStackTrace();
		}

//		// 自定义分享平台
//		oks.setCustomerLogo(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share_zalo),
//				"Zalo", new View.OnClickListener() {
//					@Override
//					public void onClick(View view) {
//						try {
//							Intent vIt = new Intent(Intent.ACTION_SEND);
////							vIt.setPackage("com.facebook.orca");
//							vIt.setType("text/plain");
//							vIt.putExtra(Intent.EXTRA_TEXT, msg);
//							startActivity(vIt);
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		//关闭sso授权
//		oks.disableSSOWhenAuthorize();
//		oks.setCallback(new PlatformActionListener() {
//			@Override
//			public void onComplete(Platform platform, int i, HashMap<String, Object> hashMap) {
//
//			}
//
//			@Override
//			public void onError(Platform platform, int i, Throwable throwable) {
//
//			}
//
//			@Override
//			public void onCancel(Platform platform, int i) {
//
//			}
//		});
//
//		// title标题，微信、QQ和QQ空间等平台使用
//		oks.setTitle(getString(R.string.app_name));
//		oks.setText(msg);
//		oks.show(this);
	}

	private WeakHashMap<String, Object> getRequestParams() {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		//params.put("userId", PeachPreference.readUserId());
		String receiver = mEtReceiver.getText().toString();
		if (!TextUtils.isEmpty(receiver)) {
			params.put("recUser", StringUtil.isNum(receiver) ? mCountryCodePicker.getSelectedCountryCodeWithPlus() + receiver : receiver);
		}
		params.put("preId", mKeyPwd.getId());
		params.put("timeZone", DateUtil.getTimeZone());

		return params;
	}

	private void sendKeyByAccount(String recUser) {
		RestClient.builder()
				.url(Urls.LOCK_EKEY_SHARE_RECEIVE)
				.loader(this)
				.params(getRequestParams())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_EKEY_MODIFY_PERIOD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");

						switch (code) {
							case 200: {
									/*Intent intent = new Intent();
									intent.putExtra(KEY_SHARE_TYPE, shareKeyThrough);
									setResult(RESULT_OK, intent);
									toast(R.string.send_ekey_success);
									finish();*/
								CreatePwdKeyActionInfo mCreatePwdKeyActionInfo = new CreatePwdKeyActionInfo();
								mCreatePwdKeyActionInfo.setTabCategory(CreatePwdKeyActionInfo.TAB_CATEGORY_0);
								EventBus.getDefault().post(new Event(Event.EventType.CREATE_BT_KEY_SUCCESS, mCreatePwdKeyActionInfo));
								KeyCreateSuccessActivity.actionStart(EkeyShareModifyActivity.this, KeyCreateSuccessActivity.FROM_SHARE_EDIT);
								finish();

							}
							break;

							case 920:
								toast(R.string.note_receive_user_not_found);
								break;

							case 951:
								toast(R.string.note_send_ekey_to_registered_user);
								break;

							case 952:
								toast(R.string.note_cannot_send_ekey_to_yourself);
								break;

							case 953:
								toast(R.string.note_no_auth_send_ekey);
								break;

							case 954:
								toast(R.string.note_cannot_exceed_expiration_send_ekey);
								break;

							default:
								toastBackendError(code, R.string.send_ekey_fail);
								break;
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.send_ekey_fail);
					}
				})
				.build()
				.post();
	}

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (group.getId() == R.id.rg_share_the_key_through) {
            setShareTheKeyThrough(checkedId);
        }
    }

    private void setShareTheKeyThrough(int checkedId) {
        if (checkedId == R.id.rb_share_key_through_account) {
            tv_share_the_key_through_hint.setText(R.string.share_type_populife_account);
            shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.ACCOUNT;
            ll_receiver.setVisibility(View.VISIBLE);
        } else if (checkedId == R.id.rb_share_key_through_sms_link) {
            tv_share_the_key_through_hint.setText(R.string.share_type_sms_link);
            shareKeyThrough = KeyPwdConstant.IBTKeyShareThrough.SMS_LINK;
            ll_receiver.setVisibility(View.GONE);
        }

        setSendBtnEnable();
    }


    private void setSendBtnEnable() {
		mTvSave.setEnabled(isSendBtnEnable());
	}

	private boolean isSendBtnEnable() {

		if (shareKeyThrough == KeyPwdConstant.IBTKeyShareThrough.ACCOUNT && StringUtil.isBlank(mEtReceiver.getText().toString().trim())) {
			return false;
		}

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK && requestCode == REQUEST_CONTACT) {
			if (data == null) {
				return;
			}
			Uri result = data.getData();
			String[] contact = getPhoneContacts(result);

			String phoneNum = contact[1].replaceAll(" ", "")
					.replaceFirst("^0*", "");

			mEtReceiver.setText(phoneNum);
		}
	}

    private String[] getPhoneContacts(Uri uri) {
        String[] contact = new String[2];
        if (uri == null) return contact;

        ContentResolver cr = getContentResolver();
        Cursor cursor = null;
        Cursor phoneCursor = null;

        try {
            cursor = cr.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                // 取得联系人名字
                int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                contact[0] = nameIndex != -1 ? cursor.getString(nameIndex) : "";

                // 取得联系人ID
                int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                if (idIndex != -1) {
                    String contactId = cursor.getString(idIndex);
                    phoneCursor = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                            new String[]{contactId},
                            null
                    );

                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        int numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        contact[1] = numberIndex != -1 ? phoneCursor.getString(numberIndex) : "";
                    }
                }
            }
        } finally {
            if (cursor != null) cursor.close();
            if (phoneCursor != null) phoneCursor.close();
        }

        return contact;
    }

}
