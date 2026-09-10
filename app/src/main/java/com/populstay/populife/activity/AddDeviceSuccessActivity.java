package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigkoo.pickerview.builder.OptionsPickerBuilder;
import com.bigkoo.pickerview.listener.CustomListener;
import com.bigkoo.pickerview.listener.OnOptionsSelectListener;
import com.bigkoo.pickerview.view.OptionsPickerView;
import com.google.gson.reflect.TypeToken;
import com.populstay.populife.R;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.Gateway;
import com.populstay.populife.entity.Key;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.helper.OfflineLockUploader;
import com.populstay.populife.home.entity.Home;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.device.KeyboardUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public class AddDeviceSuccessActivity extends BaseActivity implements View.OnClickListener {

	public static final String KEY_DEVICE_TYPE = "key_device_type";
	public static final String KEY_DEVICE_DATA = "key_device_data";
	public static final String KEY_LOCK_KEY = "key_lock_key";
	public static final int REQUEST_CODE_PASSCODE = 001;
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";

	private OptionsPickerView mPickerHome;
	private TextView mTvHomeName, mTvFinish, mTvAdminPwd;
	private List<Home> mHomeList;
	private ExEditText mEtDeviceName;
	private LinearLayout mAdminPwdLl;
	private Home mHome;
	private String mDeviceType = HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX;
	private HomeDevice mHomeDevice;
	private Key mKey;

	public static void actionStart(Context context, String deviceType, HomeDevice device, Key key) {
		Intent intent = new Intent(context, AddDeviceSuccessActivity.class);
		intent.putExtra(KEY_DEVICE_TYPE, deviceType);
		intent.putExtra(KEY_DEVICE_DATA, device);
		intent.putExtra(KEY_LOCK_KEY, key);
		context.startActivity(intent);
	}

	public static void actionStart(Context context, String deviceType, HomeDevice device) {
		Intent intent = new Intent(context, AddDeviceSuccessActivity.class);
		intent.putExtra(KEY_DEVICE_TYPE, deviceType);
		intent.putExtra(KEY_DEVICE_DATA, device);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_device_success);
		getIntentData();
		initView();
		setListener();
		initPicker();
		requestLockGroup();
	}

	private void getIntentData() {
		Intent intent = getIntent();
		mDeviceType = intent.getStringExtra(KEY_DEVICE_TYPE);
		mHomeDevice = intent.getParcelableExtra(KEY_DEVICE_DATA);
		mKey = intent.getParcelableExtra(KEY_LOCK_KEY);
	}

	private void initView() {
		findViewById(R.id.page_action).setVisibility(View.GONE);
		TextView tvPageTitle = findViewById(R.id.page_title);
		tvPageTitle.setText(R.string.add_success);
		mTvHomeName = findViewById(R.id.tv_home_name);
		mTvAdminPwd = findViewById(R.id.tv_admin_pwd);
		mEtDeviceName = findViewById(R.id.et_device_name);
		mTvFinish = findViewById(R.id.tv_finish);
		mAdminPwdLl = findViewById(R.id.admin_pwd_ll);
		String deviceName = mHomeDevice.getName();
		if (!StringUtil.isBlank(deviceName)) {
			mEtDeviceName.setText(deviceName);
			mEtDeviceName.setSelection(deviceName.length());
		}

		if (mDeviceType.startsWith(HomeDeviceInfo.IDeviceName.NAME_GATEWAY)) {
			mAdminPwdLl.setVisibility(View.GONE);
		}
		if (mDeviceType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
				|| mDeviceType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)) {
			mAdminPwdLl.setVisibility(View.GONE);
		}
		if (null != mKey) {
			mTvAdminPwd.setText(mKey.getNoKeyPwd());
		}
		if (HomeDeviceInfo.isPadlock(deviceName)) {
			mAdminPwdLl.setVisibility(View.GONE);
		}
	}

	private void setListener() {
		mTvHomeName.setOnClickListener(this);
		mTvAdminPwd.setOnClickListener(this);
		mTvFinish.setOnClickListener(this);
		mEtDeviceName.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				setEnableTvFinishBtn();
			}
		});
	}

	private void setEnableTvFinishBtn() {
		mTvFinish.setEnabled(!TextUtils.isEmpty(mEtDeviceName.getTextStr()) && !TextUtils.isEmpty(mTvHomeName.getText().toString().trim()));
	}

	private void initPicker() {
		mPickerHome = new OptionsPickerBuilder(this, new OnOptionsSelectListener() {
			@Override
			public void onOptionsSelect(int options1, int option2, int options3, View v) {
				if (CollectionUtil.isEmpty(mHomeList)) {
					return;
				}
				mHome = mHomeList.get(options1);
				mTvHomeName.setText(mHome.getName());
				setEnableTvFinishBtn();
			}
		}).setLayoutRes(R.layout.pickerview_select_home_group, new CustomListener() {
			@Override
			public void customLayout(View v) {
				final TextView tvSubmit = v.findViewById(R.id.tv_finish);
				TextView tvCancel = v.findViewById(R.id.iv_cancel);
				tvSubmit.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mPickerHome.returnData();
						mPickerHome.dismiss();
					}
				});

				tvCancel.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mPickerHome.dismiss();
					}
				});
			}
		}).setLineSpacingMultiplier(2.5F)
				.isDialog(false)
				.build();
	}

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.tv_home_name) {
            mPickerHome.show();
            KeyboardUtil.hideSoftInput(this);
        } else if (id == R.id.tv_finish) {
            // 锁头
            if (!mDeviceType.startsWith(HomeDeviceInfo.IDeviceName.NAME_GATEWAY)) {
                modifyLockName();
                bindLockHome();
            } else {
                modifyGatewayName();
                bindGatewayHome();
            }
        } else if (id == R.id.tv_admin_pwd) {
            modifyLockAdminPwd();
        }
    }


    private void requestLockGroup() {
		RestClient.builder()
				.url(Urls.GET_HOME_MY_OWN)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mHomeList = GsonUtil.fromJson(result.getJSONArray("data").toJSONString(), new TypeToken<List<Home>>() {
							});
							if (CollectionUtil.isEmpty(mHomeList)) {
								return;
							}
							String currentHomeId = PeachPreference.getLastSelectHomeId();
							List<String> mHomeNames = new ArrayList<>();
							mHome = mHomeList.get(0);
							int selectPosition = 0;
							for (int i = 0, len = mHomeList.size(); i < len; i++) {
								Home home = mHomeList.get(i);
								mHomeNames.add(home.getName());
								if (home.getId().equals(currentHomeId)) {
									mHome = home;
									selectPosition = i;
								}
							}

							mPickerHome.setSelectOptions(selectPosition);
							mTvHomeName.setText(mHome.getName());
							mPickerHome.setPicker(mHomeNames);
							setEnableTvFinishBtn();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
					}
				})
				.build()
				.get();
	}

	/**
	 * 修改锁名称
	 */
	private void modifyLockName() {
		RestClient.builder()
				.url(Urls.LOCK_NAME_MODIFY)
				.loader(this)
				.params("lockId", mHomeDevice.getDeviceId())
				.params("lockAlias", mEtDeviceName.getTextStr())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_NAME_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
						} else {
							toast(R.string.note_lock_name_add_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_lock_name_add_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 修改锁管理员密码
	 */
	private void modifyLockAdminPwd() {
		Intent intent = new Intent();
		intent.setClass(AddDeviceSuccessActivity.this, ModifyAdminPasscodeActivity.class);
		intent.putExtra(ModifyAdminPasscodeActivity.KEY_PASSCODE, mTvAdminPwd.getText().toString());
		intent.putExtra(ModifyAdminPasscodeActivity.KEY, mKey);
		//mDeviceType
		intent.putExtra(KEY_LOCK_TYPE, mDeviceType);
		intent.putExtra(ModifyAdminPasscodeActivity.KEY, mKey);
		startActivityForResult(intent, REQUEST_CODE_PASSCODE);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (RESULT_OK != resultCode) {
			return;
		}
		if (REQUEST_CODE_PASSCODE == requestCode) {
			String keyboardPwd = data.getStringExtra(LockSettingsActivity.KEY_RESULT_DATA);
			if (!TextUtils.isEmpty(keyboardPwd)) {
				mKey.setNoKeyPwd(keyboardPwd);
				mTvAdminPwd.setText(keyboardPwd);
			}
		}
	}

	/**
	 * 绑定锁家庭
	 */
	private void bindLockHome() {
		RestClient.builder()
				.url(Urls.LOCK_BIND_HOME)
				.loader(this)
				.params("lockId", mHomeDevice.getDeviceId())
				.params("homeId", mHome.getId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							completeLockSync();
							changeSelectHome();
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							//PeachPreference.getAccountLockNum(PeachPreference.readUserId());
							goToNewActivity(MainActivity.class);
							//LockDetailActivity.actionStart(getActivity(), homeDevice.getDeviceId(),homeDevice.getName());
							LockDetailActivity.actionStart(AddDeviceSuccessActivity.this, String.valueOf(mKey.getLockId()), mHomeDevice.getName());
						} else {
							toast(R.string.note_lock_name_add_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_lock_name_add_fail);
					}
				})
				.build()
				.post();
	}

	/** 设备完成云端初始化且已归属空间后，才可清除本地待同步记录。 */
	private void completeLockSync() {
		if (mKey == null || TextUtils.isEmpty(mKey.getLockMac())) {
			return;
		}
		PopulifeDBUtil.getInstance(getApplicationContext())
				.deleteByUserAndMac(mKey.getUserId(), mKey.getLockMac());
		OfflineLockUploader.clearSyncFailed(mKey.getUserId(), mKey.getLockMac());
		PeachLogger.d("LOCK_BIND_HOME complete, clear local " + mKey.getLockMac());
	}

	/**
	 * 修改网关名称
	 */
	private void modifyGatewayName() {
		RestClient.builder()
				.url(Urls.GATEWAY_MODIFY_NAME)
				.loader(this)
				.params("gatewayId", mHomeDevice.getDeviceId())
				.params("name", mEtDeviceName.getTextStr())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_NAME_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
						} else {
							toast(R.string.note_lock_name_add_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_lock_name_add_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 绑定网关家庭
	 */
	private void bindGatewayHome() {
		RestClient.builder()
				.url(Urls.GATEWAY_SET_HOME)
				.loader(this)
				.params("gatewayId", Integer.valueOf(mHomeDevice.getDeviceId()))
				.params("homeId", mHome.getId())
				.success(new ISuccess() {
					@SuppressLint("SetTextI18n")
					@Override
					public void onSuccess(String response) {
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							changeSelectHome();
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							// 返回主页
							goToNewActivity(MainActivity.class);
							// 跳转锁列表
							Gateway gateway = new Gateway();
							gateway.setGatewayId(Integer.valueOf(mHomeDevice.getDeviceId()));
							gateway.setName(mEtDeviceName.getTextStr());
							GatewayBindedLockListActivity.actionStart(AddDeviceSuccessActivity.this, gateway);
						} else {
							toast(R.string.note_lock_name_add_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_lock_name_add_fail);
					}
				})
				.build()
				.post();
	}

	private void changeSelectHome() {
		String lastSelectHomeId = PeachPreference.getLastSelectHomeId();
		if (!lastSelectHomeId.equals(mHome.getId())) {
			PeachPreference.setLastSelectHomeId(mHome.getId());
			PeachPreference.setLastSelectHomeName(mHome.getName());
			EventBus.getDefault().post(new Event(Event.EventType.CHANGE_HOME, mHome));
		}
	}
}
