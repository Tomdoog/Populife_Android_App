package com.populstay.populife.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.adapter.GatewayAddListAdapter;
import com.populstay.populife.adapter.WifiListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.ui.CustomProgress;
import com.populstay.populife.ui.widget.exedittext.ExEditText;
import com.populstay.populife.util.CollectionUtil;
import com.populstay.populife.util.locale.SPUtils;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.net.NetworkUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.api.ExtendedBluetoothDevice;
import com.ttlock.bl.sdk.gateway.api.GatewayClient;
import com.ttlock.bl.sdk.gateway.callback.ConnectCallback;
import com.ttlock.bl.sdk.gateway.callback.InitGatewayCallback;
import com.ttlock.bl.sdk.gateway.callback.ScanGatewayCallback;
import com.ttlock.bl.sdk.gateway.callback.ScanWiFiByGatewayCallback;
import com.ttlock.bl.sdk.gateway.model.ConfigureGatewayInfo;
import com.ttlock.bl.sdk.gateway.model.DeviceInfo;
import com.ttlock.bl.sdk.gateway.model.GatewayError;
import com.ttlock.bl.sdk.gateway.model.WiFi;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class GatewayAddActivity extends BaseActivity implements TextWatcher {

	public static final int SCAN_TIME_OUT_SECONDS = 10 * 1000;
	private LinearLayout mLlConfig, mLlFoundDeviceView;
	private TextView mTvTitle, mTvNext;
	private ExEditText mEtWifiName, mEtWifiPwd;
	private EditText mEtGatewayName;
	private SeekBar mSeekbarScanDevice;
	private ListView mListView;
	private GatewayAddListAdapter mAdapter;
	private List<ExtendedBluetoothDevice> mDeviceList = new ArrayList<>();
	private GatewayClient mGatewayAPI = GatewayClient.getDefault();
	private CustomProgress mCustomProgress;
	private ExtendedBluetoothDevice mSelectedDevice;
	private AlertDialog DIALOG;
	private int mCurrentScanProgress = 0;
	private Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			Log.e("mhs", "mRunnable--mCurrentScanProgress=" + mCurrentScanProgress);
			if (mCurrentScanProgress >= SCAN_TIME_OUT_SECONDS) {
				showNoResultDialog();
			} else {
				upDateSeekbarScanDevice();
			}
		}
	};
	private AlertDialog mWifiListDialog;
	private ListView mWifiListView;
	private TextView mTvSearchState;
	private WifiListAdapter mWifiListAdapter;
	private List<WiFi> mWifiList = new ArrayList<>();
	private List<WiFi> mTempWifiList = new ArrayList<>();
	private MyHandler mMyHandler;
	private long mSearchWifiTime;
	private boolean isCloseWifiListDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gateway_add);

		initView();
		initListener();
		initData();
	}

	private void initView() {
		findViewById(R.id.page_action).setVisibility(View.GONE);
		mTvTitle = findViewById(R.id.page_title);
		mTvTitle.setText(R.string.add_gateway_title);

		mLlConfig = findViewById(R.id.ll_gateway_add);

		mListView = findViewById(R.id.lv_gateway_add);
		mAdapter = new GatewayAddListAdapter(this, mDeviceList);
		mListView.setAdapter(mAdapter);

		mEtWifiName = findViewById(R.id.et_gateway_add_wifi_name);
		mTvNext = findViewById(R.id.tv_gateway_add_next);
		mEtWifiPwd = findViewById(R.id.et_gateway_add_wifi_pwd);
		mEtGatewayName = findViewById(R.id.et_gateway_add_gateway_name);
		//mEtWifiName.setText(NetworkUtil.getWifiSSid());

		mLlFoundDeviceView = findViewById(R.id.ll_found_device_view);
		mSeekbarScanDevice = findViewById(R.id.seekbar_scan_device);
		mSeekbarScanDevice.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// 返回true，禁止手动拖动进度值
				return true;
			}
		});

	}

	private void initListener() {
		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				showLoading();
				mSelectedDevice = mDeviceList.get(position);

				requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
								: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
						new PermissionListener() {
							@Override
							public void onGranted() {
								// 开始蓝牙连接网关

								mGatewayAPI.connectGateway(mSelectedDevice, new ConnectCallback() {
									@Override
                                    public void onConnectSuccess(ExtendedBluetoothDevice device) {
										runOnUiThread(new Runnable() {
											@Override
											public void run() {
												stopLoading();
												mListView.setVisibility(View.GONE);
												mLlConfig.setVisibility(View.VISIBLE);
												mEtGatewayName.setText(mSelectedDevice.getName());
												String lastWifiSSID = SPUtils.getInstance(GatewayAddActivity.this).getWifiSSID();
												String laseWifiPwd = SPUtils.getInstance(GatewayAddActivity.this).getWifiPwd();
												if (TextUtils.isEmpty(lastWifiSSID) || TextUtils.isEmpty(laseWifiPwd)){
													// 展示wifi列表对话框
													showWifiListDialog(MyHandler.SEARCH_WIFI_STATE_START);
													scanWiFiByGateway(mSelectedDevice.getAddress());
												}else {
													mEtWifiName.setText(lastWifiSSID);
													mEtWifiPwd.setText(laseWifiPwd);
												}
											}
										});
									}

									@Override
                                    public void onDisconnected() {
										stopLoading();
									}
								});
							}

							@Override
							public void onDenied(List<String> deniedPermissions) {
								toast(isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
							}
						});
			}
		});

		mEtWifiName.addTextChangedListener(this);
		mEtWifiPwd.addTextChangedListener(this);
		mEtGatewayName.addTextChangedListener(this);

		mTvNext.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				/*mCustomProgress = CustomProgress.show(GatewayAddActivity.this,
						getString(R.string.configuring_gateway), false, null);*/
				SPUtils.getInstance(GatewayAddActivity.this).saveLastWifi(mEtWifiName.getText().toString());
				SPUtils.getInstance(GatewayAddActivity.this).saveLastWifiPwd(mEtWifiPwd.getText().toString());
				showLoading();
				mTvNext.setEnabled(false);
				getUserKeyId();
			}
		});

		mEtWifiName.setOnRightIconClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showWifiListDialog(MyHandler.SEARCH_WIFI_STATE_START);
				scanWiFiByGateway(mSelectedDevice.getAddress());
			}
		});
	}

    private void scanWiFiByGateway(String address) {
        mGatewayAPI.scanWiFiByGateway(mSelectedDevice.getAddress(), new ScanWiFiByGatewayCallback() {
            @Override
            public void onScanWiFiByGateway(List<WiFi> list) {
                mTempWifiList.clear();
                for (WiFi wiFi : list){
                    if (!TextUtils.isEmpty(wiFi.getSsid())) {
                        mTempWifiList.add(wiFi);
                    }
                }
                mMyHandler.sendEmptyMessage(MyHandler.WHAT_SHOW_WIFI_LIST_DIALOG);
            }

            @Override
            public void onScanWiFiByGatewaySuccess() {

            }

            @Override
            public void onFail(GatewayError gatewayError) {

            }
        });
    }

	private void initData() {
		mMyHandler = new MyHandler(this);
		startScanGateway();
		initSeekbarScanDevice();
	}

	private void showWifiListDialog(int state) {
		if (MyHandler.SEARCH_WIFI_STATE_START == state) {
			isCloseWifiListDialog = false;
		}
		if (isCloseWifiListDialog) {
			return;
		}
		if (null == mWifiListDialog) {
			mWifiListDialog = new AlertDialog.Builder(this).create();
			mWifiListDialog.show();
			final Window window = mWifiListDialog.getWindow();
			if (window != null) {
				window.setContentView(R.layout.dialog_wifi_list);
				window.setGravity(Gravity.CENTER);
				window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
				//设置属性
				final WindowManager.LayoutParams params = window.getAttributes();
				params.width = WindowManager.LayoutParams.WRAP_CONTENT;
				params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
				params.dimAmount = 0.5f;
				window.setAttributes(params);

				window.findViewById(R.id.tv_cancel_btn).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mWifiListDialog.dismiss();
					}
				});

				mWifiListView = window.findViewById(R.id.wifiListView);
				mTvSearchState = window.findViewById(R.id.tv_search_state);
				if (!CollectionUtil.isEmpty(mTempWifiList)) {
					mWifiList.clear();
					mWifiList.addAll(mTempWifiList);
				}
				mWifiListAdapter = new WifiListAdapter(mWifiList, this);
				mWifiListView.setAdapter(mWifiListAdapter);
				mWifiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						WiFi wiFi = (WiFi) parent.getItemAtPosition(position);
						mEtWifiName.setText(wiFi.getSsid());
						mWifiListDialog.dismiss();
					}
				});
			}
			mWifiListDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					isCloseWifiListDialog = true;
					mMyHandler.removeMessages(MyHandler.WHAT_SHOW_WIFI_LIST_DIALOG);
					mMyHandler.removeMessages(MyHandler.WHAT_SEARCH_WIFI_LIST);
					mGatewayAPI.stopScanGateway();
				}
			});
		} else {
			if (!mWifiListDialog.isShowing()) {
				mWifiListDialog.show();
			}
			mWifiList.clear();
			if (null != mTempWifiList) {
				mWifiList.addAll(mTempWifiList);
			}
			mWifiListAdapter.notifyDataSetChanged();
		}

		mWifiListView.setVisibility(View.GONE);
		mTvSearchState.setVisibility(View.GONE);
		if (MyHandler.SEARCH_WIFI_STATE_START == state) {
			mTvSearchState.setVisibility(View.VISIBLE);
			mTvSearchState.setText(R.string.wifi_searching);
			mSearchWifiTime = 0;
			mMyHandler.sendEmptyMessageDelayed(MyHandler.WHAT_SEARCH_WIFI_LIST, 500);
		} else if (MyHandler.SEARCH_WIFI_STATE_RUNNING == state) {
			mTvSearchState.setVisibility(View.VISIBLE);
			mMyHandler.sendEmptyMessageDelayed(MyHandler.WHAT_SEARCH_WIFI_LIST, 500);
		} else if (MyHandler.SEARCH_WIFI_STATE_RESULT == state) {
			mWifiListView.setVisibility(View.VISIBLE);
			mMyHandler.removeMessages(MyHandler.WHAT_SEARCH_WIFI_LIST);

		} else if (MyHandler.SEARCH_WIFI_STATE_END == state) {
			mMyHandler.removeMessages(MyHandler.WHAT_SEARCH_WIFI_LIST);
			mTvSearchState.setVisibility(View.VISIBLE);
			mTvSearchState.setText(R.string.wifi_no_found);
		}
	}

	private void startScanGateway() {
		if (null == mGatewayAPI) {
			return;
		}

		requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
						: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
				new PermissionListener() {
					@Override
					public void onGranted() {
						// 开始蓝牙扫描
                        mGatewayAPI.startScanGateway(new ScanGatewayCallback() {
                            @Override
                            public void onScanGatewaySuccess(ExtendedBluetoothDevice extendedBluetoothDevice) {
                                String name = extendedBluetoothDevice.getName();
                                // 网关(G2开头的，在添加设备时，转为 Gateway)
                                if (name.contains("G2")) {
                                    extendedBluetoothDevice.setName(name.replace("G2", HomeDeviceInfo.IDeviceName.NAME_GATEWAY));
                                }
                                PeachLogger.d(extendedBluetoothDevice);
                                if (mAdapter != null) {
                                    mAdapter.updateDevice(extendedBluetoothDevice);
                                }
                                BaseApplication.getHandler().removeCallbacks(mRunnable);
                                mListView.setVisibility(View.VISIBLE);
                                mLlFoundDeviceView.setVisibility(View.GONE);
                                if (DIALOG != null) {
                                    DIALOG.cancel();
                                }
                            }

                            @Override
                            public void onScanFailed(int i) {

                            }
                        });
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {
						toast(isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
					}
				});
	}

	private void upDateSeekbarScanDevice() {
		mCurrentScanProgress += 500;
		mSeekbarScanDevice.setProgress(mCurrentScanProgress);
		BaseApplication.getHandler().postDelayed(mRunnable, 500);
	}

	private void initSeekbarScanDevice() {
		mSeekbarScanDevice.setMax(SCAN_TIME_OUT_SECONDS);
		mCurrentScanProgress = 0;
		upDateSeekbarScanDevice();
	}

	private void showNoResultDialog() {
		if (isFinishing()) {
			return;
		}
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

			((TextView) window.findViewById(R.id.tv_dialog_input_title)).setText(R.string.search_timeout);
			window.findViewById(R.id.et_dialog_input_content).setVisibility(View.GONE);
			TextView tvContent = window.findViewById(R.id.tv_dialog_content);
			tvContent.setVisibility(View.VISIBLE);
			tvContent.setText(R.string.gateway_was_not_founded_try_again);
			Button rightButton = window.findViewById(R.id.btn_dialog_input_ok);
			rightButton.setText(R.string.scan_again);
			rightButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					DIALOG.cancel();
					startScanGateway();
					initSeekbarScanDevice();
				}
			});
			Button leftButton = window.findViewById(R.id.btn_dialog_input_cancel);
			leftButton.setText(R.string.cancel);
			leftButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					finish();
				}
			});
		}
	}

	/**
	 * 通过用户id获取sciener用户主键id
	 */
	private void getUserKeyId() {
		RestClient.builder()
				.url(Urls.GATEWAY_GET_USER_KEY_ID)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GATEWAY_GET_USER_KEY_ID", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							int userKeyId = result.getInteger("data");
							configGateway(userKeyId);
						} else if (code == 951) {
							toast(R.string.note_gateway_donot_exists);
							refreshBtnState();
						} else {
							toastBackendError(result, R.string.note_gateway_init_fail);
							refreshBtnState();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_gateway_init_fail);
						refreshBtnState();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						refreshBtnState();
					}
				})
				.build()
				.get();
	}

	private void configGateway(int userKeyId) {
		String userPwd = PeachPreference.getStr(PeachPreference.ACCOUNT_PWD);
		String md51 = StringUtil.md5(userPwd);
		String md52 = StringUtil.md5(md51);
		String md53 = StringUtil.md5(md52);
		String md54 = StringUtil.md5(md53);

		ConfigureGatewayInfo configureGatewayInfo = new ConfigureGatewayInfo();
		configureGatewayInfo.uid = userKeyId;
		configureGatewayInfo.userPwd = md54;

		configureGatewayInfo.ssid = mEtWifiName.getText().toString().trim();
		configureGatewayInfo.wifiPwd = mEtWifiPwd.getText().toString().trim();
		configureGatewayInfo.plugName = mSelectedDevice.getAddress();
        mGatewayAPI.initGateway(configureGatewayInfo, new InitGatewayCallback() {
            @Override
            public void onInitGatewaySuccess(DeviceInfo deviceInfo) {
                checkInitGatewaySuccess(mSelectedDevice.getAddress(), deviceInfo);
            }

            @Override
            public void onFail(GatewayError gatewayError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(R.string.note_gateway_init_fail);
                        refreshBtnState();
                        initFail();
                    }
                });
            }
        });
	}

	/**
	 * 使用SDK添加网关后调用该接口绑定网关名称
	 *
	 * @param gatewayMac The Mac which you will get when calling the SDK method to add a gateway
	 */
	private void addGateway(final String gatewayMac, final DeviceInfo deviceInfo, final int gatewayId) {

		RestClient.builder()
				.url(Urls.GATEWAY_ADD)
				.params("userId", PeachPreference.readUserId())
				.params("name", mSelectedDevice.getName())
				.params("gatewayMac", gatewayMac)
				.params("gatewayId", gatewayId)
				// gatewayVersion对应ModelNum
				.params("gatewayVersion", deviceInfo.getModelNum())
				.params("networkName", NetworkUtil.getWifiSSid())
				//硬件版本
				.params("hardwareRevision", deviceInfo.getHardwareRevision())
				//固件版本
				.params("firmwareRevision", deviceInfo.getFirmwareRevision())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GATEWAY_ADD", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							HomeDevice device = new HomeDevice();
							device.setName(mEtGatewayName.getText().toString().trim());
							device.setDeviceId(String.valueOf(gatewayId));
							device.setModelNum(deviceInfo.getModelNum());
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							AddDeviceSuccessActivity.actionStart(GatewayAddActivity.this, HomeDeviceInfo.IDeviceName.NAME_GATEWAY, device);
						} else {
							toastBackendError(result, R.string.note_gateway_init_fail);
							initFail();
							refreshBtnState();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_gateway_init_fail);
						initFail();
						refreshBtnState();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						initFail();
						refreshBtnState();
					}
				})
				.build()
				.post();
	}

	/**
	 * 使用SDK添加网关后调用该接口，查询网关是否添加成功
	 *
	 * @param gatewayNetMac The Mac which you will get when calling the SDK method to add a gateway
	 *                      {
	 *                      "success": true,
	 *                      "code": 200,
	 *                      "msg": "",
	 *                      "data": {
	 *                      "errcode": 0,
	 *                      "errmsg": "表示失败或否",
	 *                      "gatewayId": 382323
	 *                      }
	 *                      }
	 */
	private void checkInitGatewaySuccess(String gatewayNetMac, final DeviceInfo deviceInfo) {
		RestClient.builder()
				.url(Urls.GATEWAY_INIT_CHECK_SUCCESS)
				.params("userId", PeachPreference.readUserId())
				.params("gatewayNetMac", gatewayNetMac)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("GATEWAY_INIT_CHECK_SUCCESS", response);

						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							JSONObject dataObj = result.getJSONObject("data");
							int errcode = dataObj.getInteger("errcode");
							int gatewayId = dataObj.getInteger("gatewayId");
							if (0 == errcode) {
								toast(getString(R.string.note_gateway_init_success));
								refreshBtnState();
								/*Intent intent = new Intent(GatewayAddActivity.this, GatewayListActivity.class);
								startActivity(intent);*/
								// todo

								addGateway(mSelectedDevice.getAddress(), deviceInfo, gatewayId);
							} else {
								toastBackendError(result, R.string.note_gateway_init_fail);
								refreshBtnState();
								initFail();
							}
						} else {
							toastBackendError(result, R.string.note_gateway_init_fail);
							initFail();
							refreshBtnState();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_gateway_init_fail);
						initFail();
						refreshBtnState();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						refreshBtnState();
						initFail();
					}
				})
				.build()
				.get();
	}

	private void initFail() {
		AddDeviceFailActivity.actionStart(GatewayAddActivity.this, HomeDeviceInfo.IDeviceName.NAME_GATEWAY);
	}

	private void refreshBtnState() {
		//mCustomProgress.cancel();
		stopLoading();
		mTvNext.setEnabled(true);
		mTvNext.setText(R.string.ok);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		mTvNext.setEnabled(checkForm());
	}

	private boolean checkForm() {
		String wifiName = mEtWifiName.getText().toString().trim();
		String wifiPwd = mEtWifiPwd.getText().toString().trim();
		String gatewayName = mEtGatewayName.getText().toString().trim();
		boolean isPass = true;
		if (StringUtil.isBlank(wifiName) || StringUtil.isBlank(wifiPwd) || StringUtil.isBlank(gatewayName)
				|| wifiPwd.length() < 8) {
			isPass = false;
		}
		return isPass;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mGatewayAPI.stopScanGateway();
		if (null != mMyHandler) {
			mMyHandler.removeCallbacksAndMessages(null);
			mMyHandler = null;
		}
	}

	static class MyHandler extends Handler {

		public static final int WHAT_SHOW_WIFI_LIST_DIALOG = 1;
		public static final int WHAT_SEARCH_WIFI_LIST = 2;
		public static final long WHAT_SEARCH_WIFI_LIST_TIME_OUT = 10 * 1000;
		public static int SEARCH_WIFI_STATE_START = 0;
		public static int SEARCH_WIFI_STATE_END = 1;
		public static int SEARCH_WIFI_STATE_RUNNING = 2;
		public static int SEARCH_WIFI_STATE_RESULT = 3;
		private WeakReference wf;

		public MyHandler(GatewayAddActivity activity) {
			this.wf = new WeakReference(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			GatewayAddActivity activity = (GatewayAddActivity) wf.get();
			if (null == activity || activity.isFinishing()) {
				return;
			}
			if (WHAT_SHOW_WIFI_LIST_DIALOG == msg.what) {
				activity.showWifiListDialog(SEARCH_WIFI_STATE_RESULT);
			} else if (WHAT_SEARCH_WIFI_LIST == msg.what) {
				activity.mSearchWifiTime += 500;
				if (activity.mSearchWifiTime < WHAT_SEARCH_WIFI_LIST_TIME_OUT) {
					activity.showWifiListDialog(SEARCH_WIFI_STATE_RUNNING);
				} else {
					activity.showWifiListDialog(SEARCH_WIFI_STATE_END);
				}
			}
		}
	}
}
