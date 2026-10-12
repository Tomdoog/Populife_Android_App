package com.populstay.populife.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Gateway;
import com.populstay.populife.entity.GatewayInfo;
import com.populstay.populife.entity.GatewayInfoResp;
import com.populstay.populife.entity.Key;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.lock.ILockModifyRemoteUnlockState;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.widget.CustomSwitch;
import com.populstay.populife.util.GsonUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.pkg.ThirdAppUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.SetRemoteUnlockSwitchCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;

import java.util.List;

public class LockRemoteUnlockConfigActivity extends BaseActivity {

	public static final String KEY_LOCK_SPECIAL_VALUE = "key_support_remote_unlock";

	private TextView mTvCurrentMode;
	private CustomSwitch mTvSwitch;
	private LinearLayout no_gateway_layout;
	private RelativeLayout gateway_layout,buy_layout;
	private TextView gateway_image_title;

	private Key mKey = MyApplication.CURRENT_KEY;
	private int mSpecialValue;
	private Gateway mGateway;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_remote_unlock_config);

		mSpecialValue = getIntent().getIntExtra(KEY_LOCK_SPECIAL_VALUE, 0);
		initView();
		initListener();
		initData();
	}

	private void initData() {
		getGatewayInfo();
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.gateway_remote_control);
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mTvCurrentMode = findViewById(R.id.tv_lock_remote_unlock_config_mode);
		mTvSwitch = findViewById(R.id.tv_lock_remote_unlock_config_switch);

		initRemoteStatus();
		no_gateway_layout = findViewById(R.id.no_gateway_layout);
		gateway_layout = findViewById(R.id.gateway_layout);
		gateway_image_title = findViewById(R.id.gateway_image_title);
		buy_layout = findViewById(R.id.buy_layout);
	}

	private void initRemoteStatus() {
		if (FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
			mTvCurrentMode.setText(R.string.on);
			//mTvSwitch.setText(R.string.turn_off);
			mTvSwitch.setChecked(true);
		} else {
			mTvCurrentMode.setText(R.string.off);
			//mTvSwitch.setText(R.string.turn_on);
			mTvSwitch.setChecked(false);
		}
	}

	private void initListener() {
		mTvSwitch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (isBleEnableWithToast()) {
					switchRemoteUnlock();
				}else{
					initRemoteStatus();
				}
			}
		});

		findViewById(R.id.tv_buy_btn).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// 否则用手机浏览器打开
				ThirdAppUtil.startPhoneBrowser(LockRemoteUnlockConfigActivity.this, "https://www.populife.co/products/gateway");

//				String productUrl = LanguageUtil.isChinese(LockRemoteUnlockConfigActivity.this) ? "https://item.taobao.com/item.htm?id=626624880701" : "https://www.populife.co/products/gateway";
//				if (LanguageUtil.isChinese(LockRemoteUnlockConfigActivity.this) && ThirdAppUtil.isAppInstalled(LockRemoteUnlockConfigActivity.this, ThirdAppUtil.TAO_BAO)) {
//					// App 为中文语言，同时手机已安装淘宝 App，则用淘宝 App 打开商品详情页
//					ThirdAppUtil.startTaobaoAppGoodsDetail(LockRemoteUnlockConfigActivity.this, productUrl);
//				} else {
//					// 否则用手机浏览器打开
//					ThirdAppUtil.startPhoneBrowser(LockRemoteUnlockConfigActivity.this, productUrl);
//				}
			}
		});

		findViewById(R.id.gateway_layout).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				mGateway.setName(mGateway.getAlias());
				GatewayBindedLockListActivity.actionStart(LockRemoteUnlockConfigActivity.this, mGateway);
			}
		});
	}

	/**
	 * 开启或关闭远程开锁功能
	 */
	private void switchRemoteUnlock() {
		showLoading();
        mTTLockAPI.setRemoteUnlockSwitchState(mTvSwitch.isChecked(), mKey.getLockData(), mKey.getLockMac(), new SetRemoteUnlockSwitchCallback() {
            @Override
            public void onSetRemoteUnlockSwitchSuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        toastSuccess();
                        updateLockData(s);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        toastFail();
                    }
                });
            }
        });
	}

	private void getGatewayInfo() {
		RestClient.builder()
				.url(Urls.GATEWAY_V5_LOCK_LIST)
				.loader(this)
				.params("userId", mKey.getUserId())
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_SPECIAL_VALUE_MODIFY", response);
						GatewayInfoResp gatewayInfoResp = GsonUtil.fromJson(response, GatewayInfoResp.class);
						showGatewayInfo(gatewayInfoResp);
					}
				}).error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLogger.d("GATEWAY_V5_LOCK_LIST", "onError msg = " + msg + ",code= " + code);
						showGatewayInfo(null);
					}
				}).failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLogger.d("GATEWAY_V5_LOCK_LIST", "onFailure");
						showGatewayInfo(null);
					}
				}).build().get();
	}

	private void showGatewayInfo(final GatewayInfoResp gatewayInfoResp) {
		if (gatewayInfoResp.isSuccess()) {
			GatewayInfo gatewayInfo = gatewayInfoResp.getData();
			if (gatewayInfo == null || gatewayInfo.getDataList() == null || gatewayInfo.getDataList().isEmpty() || !gatewayInfo.isHasGateway()){
				showNoGatewayLayout();
				return;
			}

			List<Gateway> dataList = gatewayInfo.getDataList();
			Gateway gateway = dataList.get(0);
			showGatewayLayout(gateway);
		}
	}

	private void  showNoGatewayLayout() {
		no_gateway_layout.setVisibility(View.VISIBLE);
		gateway_layout.setVisibility(View.GONE);
		buy_layout.setVisibility(View.VISIBLE);
	}

	private void showGatewayLayout(Gateway gateway) {
		no_gateway_layout.setVisibility(View.GONE);
		gateway_layout.setVisibility(View.VISIBLE);
		buy_layout.setVisibility(View.GONE);
		gateway_image_title.setText(gateway.getAlias());
		mGateway = gateway;
	}

	/**
	 * 修改锁的特征值
	 *
	 * @param remoteUnlockState 远程开锁开关状态（1 on 打开、0 off关闭）
	 */
	private void modifyLockSpcialValue(final int remoteUnlockState, final int specialValue) {
		RestClient.builder()
				.url(Urls.LOCK_SPECIAL_VALUE_MODIFY)
				.loader(this)
				.params("specialValue", specialValue)
				.params("lockId", mKey.getLockId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_SPECIAL_VALUE_MODIFY", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mSpecialValue = specialValue;
							toast(R.string.operation_success);
							if (remoteUnlockState == 0) {//远程开锁已关闭
								mTvCurrentMode.setText(R.string.off);
								//mTvSwitch.setText(R.string.turn_on);
								mTvSwitch.setChecked(false);
							} else if (remoteUnlockState == 1) {//远程开锁已打开
								mTvCurrentMode.setText(R.string.on);
								//mTvSwitch.setText(R.string.turn_off);
								mTvSwitch.setChecked(true);
							}
						} else {
							toastBackendError(result, R.string.operation_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.operation_fail);
					}
				})
				.build()
				.post();
	}

    /**
     * 修改锁的特征值
     *
     * @param lockData lockData
     */
    private void updateLockData(final String lockData) {
        RestClient.builder()
                .url(Urls.LOCK_DATA_UPDATE_POST)
                .loader(this)
                .params("lockData", lockData)
                .params("lockId", mKey.getLockId())
                .params("type", 1)
                .success(new ISuccess() {
                    @Override
                    public void onSuccess(String response) {
                        PeachLogger.d("LOCK_SPECIAL_VALUE_MODIFY", response);

                        JSONObject result = JSON.parseObject(response);
                        int code = result.getInteger("code");
                        if (code == 200) {
                            mKey.setLockData(lockData);
                            toast(R.string.operation_success);
                            if (!mTvSwitch.isChecked()) {//远程开锁已关闭
                                mTvCurrentMode.setText(R.string.off);
                                //mTvSwitch.setText(R.string.turn_on);
                                mTvSwitch.setChecked(false);
                            } else {//远程开锁已打开
                                mTvCurrentMode.setText(R.string.on);
                                //mTvSwitch.setText(R.string.turn_off);
                                mTvSwitch.setChecked(true);
                            }
                        } else {
                            toastBackendError(result, R.string.operation_fail);
                        }
                    }
                })
                .failure(new IFailure() {
                    @Override
                    public void onFailure() {
                        toast(R.string.operation_fail);
                    }
                })
                .build()
                .post();
    }

	private void setResult() {
		Intent intent = new Intent();
		intent.putExtra(KEY_LOCK_SPECIAL_VALUE, mSpecialValue);
		setResult(RESULT_OK, intent);
	}

	@Override
	public void finishCurrentActivity(View view) {
		setResult();
		super.finishCurrentActivity(view);
	}

	@Override
	public void onBackPressed() {
		setResult();
		super.onBackPressed();
	}
}
