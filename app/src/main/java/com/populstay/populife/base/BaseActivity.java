package com.populstay.populife.base;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.net.BackendError;
import com.populstay.populife.activity.LoginVerifyActivity;
import com.populstay.populife.activity.SignActivity;
import com.populstay.populife.activity.SplashActivity;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.constant.Constant;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.permission.PermissionListener;
import com.populstay.populife.push.EventPushService;
import com.populstay.populife.sign.ISignListener;
import com.populstay.populife.sign.SignHandler;
import com.populstay.populife.ui.SmartToast;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.activity.ActivityCollector;
import com.populstay.populife.util.bluetooth.BluetoothUtil;
import com.populstay.populife.util.device.DeviceUtil;
import com.populstay.populife.util.device.HideIMEUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.email.EmailUtil;
import com.populstay.populife.util.locale.LocalManageUtils;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.net.NetworkUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.api.TTLockClient;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


/**
 * Created by Jerry
 */
public class BaseActivity extends AppCompatActivity{

	private static final int REQUEST_CODE_PERMISSION = 30;
	@RequiresApi(api = 31)
	public static String[] PERMISSION_BLE_SCAN_CONNECT = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};

	/**
	 * 图片选择权限数组
	 * - Android 13+：不需要权限（使用 Photo Picker API）
	 * - Android 12及以下：需要 READ_EXTERNAL_STORAGE 权限
	 */
	public static String[] PERMISSION_IMAGES = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
			? new String[]{} 
			: new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (EventPushService.ACTION_NEW_DEVICE_LOGIN.equals(action)) { // 当前账号已经异地登录
				showNewDeviceLoginDialog();
			}
		}
	};
	public NetStateChangeReceiver mNetStateChangeReceiver;
	private PermissionListener mPermissionListener = null;
	//	private MessageReceiver mMessageReceiver;
	private boolean mRemoteLoginChecking;
	private LocationManager mLocationManager;
	private OnBackInvokedCallback mSystemBackCallback;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			// Android 16 不再派发旧返回事件，将系统手势转交给各页面已有的保存/退出逻辑。
			mSystemBackCallback = this::onBackPressed;
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
					OnBackInvokedDispatcher.PRIORITY_DEFAULT, mSystemBackCallback);
		}
        TTLockClient.getDefault().prepareBTService(getApplicationContext());
		registerNetStateChangeReceiver();
		PeachLogger.d("onCreate current activity= " + this.getClass().getSimpleName());
		// 将当前 activity 加入到活动收集器中
		ActivityCollector.addActivity(this);
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
			registerReceiver(mReceiver, getIntentFilter(),RECEIVER_EXPORTED);
		}else {
			registerReceiver(mReceiver, getIntentFilter());
		}
		
		// 状态栏适配
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			// Android 30及以上使用新的WindowInsets API
			getWindow().setDecorFitsSystemWindows(false);
		} else {
			// Android 30以下使用传统方法
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		}
		
//		// 注册美洽
//		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, intentFilter);

//		//  设置本地化语言
//		setLocale();
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setNavigationBarColor(getResources().getColor(R.color.white));
		}
		
		// 设置状态栏和导航栏样式
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			// Android 30及以上使用新的API
			if (getWindow().getInsetsController() != null) {
				getWindow().getInsetsController().setSystemBarsAppearance(
						WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
						WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
				getWindow().getInsetsController().setSystemBarsAppearance(
						WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
						WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
			}
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Android 23-29使用传统方法
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | 
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}
		
		// 确保内容不被状态栏遮挡
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			getWindow().setDecorFitsSystemWindows(false);
		}
	}

	@Override
	public void setContentView(View view) {
		if (view instanceof ViewGroup) {
			ViewGroup rootView = (ViewGroup) view;
			rootView.setFitsSystemWindows(true);
		}
		super.setContentView(view);
		HideIMEUtil.wrap(this);
	}

	@Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);
		View rootView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
		if (rootView instanceof ViewGroup) {
			((ViewGroup) rootView).setFitsSystemWindows(true);
		}
		HideIMEUtil.wrap(this);
	}

	@Override
	public void setContentView(View view, ViewGroup.LayoutParams params) {
		if (view instanceof ViewGroup) {
			ViewGroup rootView = (ViewGroup) view;
			rootView.setFitsSystemWindows(true);
		}
		super.setContentView(view, params);
		HideIMEUtil.wrap(this);
	}

	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocalManageUtils.setLocale(newBase));
	}

//	/**
//	 * 设置Locale
//	 */
//	private void setLocale() {
//		if (!LanguageUtil.isSameLanguage(this)) {
//			LanguageUtil.setLocale(this);
//			//LanguageUtil.toRestartMainActvity(this);
//		}
//	}

//	@Override
//	protected void attachBaseContext(Context newBase) {
//		Context context = languageWork(newBase);
//		super.attachBaseContext(context);
//	}

//	private Context languageWork(Context context) {
//		// 8.0及以上使用createConfigurationContext设置configuration
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//			return updateResources(context);
//		} else {
//			return context;
//		}
//	}

//	@RequiresApi(api = Build.VERSION_CODES.O)
//	private Context updateResources(Context context) {
//		Resources resources = context.getResources();
//		int type = LanguageUtil.getLanguageType(context);
//		Locale locale = LanguageUtil.getLocaleByType(type);
//		if (locale == null) {
//			return context;
//		}
//		Configuration configuration = resources.getConfiguration();
//		configuration.setLocale(locale);
//		configuration.setLocales(new LocaleList(locale));
//		return context.createConfigurationContext(configuration);
//	}

	private IntentFilter getIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(EventPushService.ACTION_NEW_DEVICE_LOGIN);
		return intentFilter;
	}

	public void showLoading() {
		PeachLoader.showLoading(this);
	}

	public void stopLoading() {
		PeachLoader.stopLoading();
	}

	/**
	 * 显示 toast 内容
	 *
	 * @param content 需要显示的内容
	 */
	public void toast(String content) {
		//ToastUtil.showToast(content);
		SmartToast.show(this, content);
	}

	/**
	 * 显示 toast 内容
	 *
	 * @param resId 字符串内容的资源 id
	 */
	public void toast(int resId) {
		//ToastUtil.showToast(resId);
		SmartToast.show(this, resId);
	}

	public void toastSuccess() {
		toast(R.string.operation_success);
	}

	public void toastFail() {
		toast(R.string.operation_fail);
	}

    protected void toastBackendError(JSONObject response, int fallbackRes) {
        toastBackendError(response, fallbackRes, BackendError.Credential.UNKNOWN);
    }

    protected void toastBackendError(JSONObject response, int fallbackRes,
            BackendError.Credential credential) {
        toast(BackendError.messageRes(response, fallbackRes, credential));
    }

	/**
	 * 是否为 Android12 系统
	 */
	public boolean isAndroid12() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
	}

	public void kjxRequestBleConnectPermissionStartConnect(final String address) {
		if (isAndroid12()) {
			requestRuntimePermissions(PERMISSION_BLE_SCAN_CONNECT, new PermissionListener() {
				@Override
				public void onGranted() {
//					mTTLockAPI.connect(address);
				}

				@Override
				public void onDenied(List<String> deniedPermissions) {
					toast(isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
				}
			});
		} else {
//			mTTLockAPI.connect(address);
		}
	}

	/**
	 * 后台操作，不前台提示权限申请
	 */
	public void kjxRequestBleConnectPermissionNoToastStartConnect(final String address) {
		if (isAndroid12()) {
			requestRuntimePermissions(PERMISSION_BLE_SCAN_CONNECT, new PermissionListener() {
				@Override
				public void onGranted() {
//					mTTLockAPI.connect(address);
				}

				@Override
				public void onDenied(List<String> deniedPermissions) {

				}
			});
		} else {
//			mTTLockAPI.connect(address);
		}
	}

	/**
	 * 检测蓝牙是否开启，未开启则 toast 提示
	 */
	public boolean isBleEnableWithToast() {
		boolean isEnable = true;

		if (!BluetoothUtil.isBleEnable()) {
			isEnable = false;
			toast(R.string.enable_bluetooth);
		}

		return isEnable;
	}

	/**
	 * 静默检测蓝牙是否开启
	 */
	public boolean isBleEnableWithoutToast() {
		boolean isEnable = true;

		if (!BluetoothUtil.isBleEnable()) {
			isEnable = false;
		}
		return isEnable;
	}

	/**
	 * 定位服务是否可用，不可用则 toast 提示
	 */
	public boolean isLbsEnableWithToast() {
		if (null == mLocationManager) {
			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		}
		return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	/**
	 * 静默检测定位服务是否可用
	 */
	public boolean isLbsEnableWithoutToast() {
//		return isLocationProviderEnabled();
		if (null == mLocationManager) {
			mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		}
		if (!checkEnableGPS()) {
			return false;
		}
		return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	/**
	 * 静默检测GPS是否可用
	 */
	public boolean checkEnableGPS() {
		String provider = Settings.Secure.getString(getContentResolver(),
				Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
		if (!"".equals(provider)) {
			// GPS Enabled
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 判断是否开启了GPS或网络定位开关
	 */
	public boolean isLocationProviderEnabled() {
		boolean result = false;
		LocationManager locationManager = (LocationManager) MyApplication.getApplication().getSystemService(LOCATION_SERVICE);
		if (locationManager == null) {
			return false;
		}
		if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager
				.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			result = true;
		}
		return result;
	}

	/**
	 * 检测网络是否开启，未开启则 toast 提示
	 */
	public boolean isNetEnableWithToast() {
		boolean isEnable = true;

		if (!NetworkUtil.isNetConnected()) {
			isEnable = false;
			toast(R.string.note_network_error);
		}

		return isEnable;
	}

	/**
	 * 静默检测网络是否开启
	 */
	public boolean isNetEnableWithoutToast() {
		boolean isEnable = true;

		if (!NetworkUtil.isNetConnected()) {
			isEnable = false;
		}

		return isEnable;
	}

	/**
	 * 检测 蓝牙 和 网络 是否同时开启，未开启则 toast 提示
	 */
	public boolean isBleNetEnableWithToast() {
		boolean isEnable = true;

		if (!isBleEnableWithToast()) {
			isEnable = false;
		} else if (!isNetEnableWithToast()) {
			isEnable = false;
		}

		return isEnable;
	}

	/**
	 * 跳转至新的 activity 页面（无需在 activity 之间传递数据）
	 *
	 * @param clazz 要启动的 activity 类名
	 */
	public void goToNewActivity(Class clazz) {
		Intent intent = new Intent(this, clazz);
		startActivity(intent);
	}

	/**
	 * 退出当前 activity
	 *
	 * @param view view 视图
	 */
	public void finishCurrentActivity(View view) {
		this.finish();
	}

	@Override
	protected void onDestroy() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mSystemBackCallback != null) {
			getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mSystemBackCallback);
		}
		super.onDestroy();
		// 页面退出:与下一条 [PAGE] show 组合即可看出用户是"返回"还是"前进"
		PeachLogger.d("[PAGE] close " + this.getClass().getSimpleName());
        TTLockClient.getDefault().stopBTService();
		stopLoading();

		EventBus.getDefault().unregister(this);
		unregisterReceiver(mReceiver);
		unregisterNetStateChangeReceiver();

		// 当前 activity 销毁时,将其从活动收集器中移除
		ActivityCollector.removeActivity(this);
//		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	/**
	 * 运行时权限处理
	 */
	public void requestRuntimePermissions(String[] permissions, PermissionListener permissionListener) {
		Activity topActivity = ActivityCollector.getTopActivity();
		if (null == topActivity) {
			return;
		}
		mPermissionListener = permissionListener;
		List<String> permissionList = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			for (String permission : permissions) {
				if (ContextCompat.checkSelfPermission(topActivity, permission) != PackageManager.PERMISSION_GRANTED) {
					permissionList.add(permission);
				}
			}
			if (!permissionList.isEmpty()) {
				ActivityCompat.requestPermissions(topActivity, permissionList.toArray(new String[permissionList.size()]), REQUEST_CODE_PERMISSION);
			} else {
				mPermissionListener.onGranted();
			}
		} else {
			mPermissionListener.onGranted();
		}
	}

	/**
	 * 用户对运行时权限授权/拒绝后，进行后续的回调操作
	 *
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSION) {
			if (grantResults.length > 0) {
				List<String> deniedPermissions = new ArrayList<>();
				for (int i = 0; i < grantResults.length; i++) {
					int grantResult = grantResults[i];
					String permission = permissions[i];
					if (grantResult != PackageManager.PERMISSION_GRANTED) {
						deniedPermissions.add(permission);
					}
				}
				if (null == mPermissionListener) {
					return;
				}
				if (deniedPermissions.isEmpty()) {
					mPermissionListener.onGranted();
				} else {
					mPermissionListener.onDenied(deniedPermissions);
				}
			}

		}else{
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// 页面轨迹:onCreate 只在首次创建时记录,用户「返回」到已存在的页面不会触发,
		// 这里补上,配合 [PAGE] 前缀即可从日志里还原出用户到过哪些页面、停在哪里
		PeachLogger.d("[PAGE] show " + this.getClass().getSimpleName());
		queryLatestDeviceId();
	}

	private boolean isNeedCheckRemoteLogin() {
		if (Constant.DEBUG) {
			return false;
		}

		// 测试账号，不检测异地登录
		String userName = PeachPreference.getStr(PeachPreference.ACCOUNT);
		if (DeviceUtil.isIgnoreAccountRemoteLoginVerify(userName)){
			return false;
		}

		if (this instanceof SignActivity || this instanceof LoginVerifyActivity || this instanceof SplashActivity) {
			return false;
		}

		long currentTime = System.currentTimeMillis();
		long lastCheckRemoteLoginTime = PeachPreference.getLastCheckRemoteLoginTime();
		if (currentTime - lastCheckRemoteLoginTime < 1000 * 30) {
			return false;
		}

		return true;
	}

	/**
	 * 查询最新的设备id, 判断是否已经在异地登录
	 */
	protected void queryLatestDeviceId() {
		if (!isNeedCheckRemoteLogin()) {
			return;
		}

		// 检查中
		if (mRemoteLoginChecking) {
			return;
		}
		mRemoteLoginChecking = true;

		RestClient.builder()
				.url(Urls.QUERY_LATEST_DEVICE_ID)
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						mRemoteLoginChecking = false;
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							String latestDeviceId = result.getString("data");// 最新登录的设备 id
							String localDeviceId = DeviceUtil.getDeviceId(BaseActivity.this);
							if (latestDeviceId != null && !latestDeviceId.equals(localDeviceId)) {
								showNewDeviceLoginDialog();
							}
						}
					}
				}).failure(new IFailure() {
			@Override
			public void onFailure() {
				mRemoteLoginChecking = false;
			}
		}).build().get();
	}

	/**
	 * 弹窗提醒用户，账号异地登录，请重新登录
	 */
	private void showNewDeviceLoginDialog() {
		DialogUtil.showCommonDialog(BaseActivity.this, null,
				getString(R.string.note_login_new_device),
				getString(R.string.ok), null, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						SignHandler.onSignOut(new ISignListener() {
							@Override
							public void onSignInSuccess() {
							}

							@Override
							public void onSignUpSuccess() {
							}

							@Override
							public void onSignOutSuccess() {
								SignActivity.actionStart(BaseActivity.this, SignActivity.VAL_ACCOUNT_SIGN_IN);
								ActivityCollector.finishAll();
							}
						});
					}
				}, null);
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onEvent(Event event) {
		if (null == event || event.type <= 0) {
			PeachLogger.d("onEvent--无效事件");
			return;
		}
		onEventSub(event);
	}

	public void onEventSub(Event event) {
		// 检查异地登录
		if (Event.EventType.FRAGMENT_RESUME_CHECK_REMOTE_LOGIN == event.type) {
			queryLatestDeviceId();
		}
	}

	public void onNetStateChange(boolean isNetEnable) {

	}

	private void registerNetStateChangeReceiver() {
		if (mNetStateChangeReceiver == null) {
			mNetStateChangeReceiver = new NetStateChangeReceiver(this);
		}
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
			registerReceiver(mNetStateChangeReceiver, getIntentFilter(),RECEIVER_EXPORTED);
		}else {
			registerReceiver(mNetStateChangeReceiver, getIntentFilter());
		}
	}

	private void unregisterNetStateChangeReceiver() {
		if (mNetStateChangeReceiver != null) {
			unregisterReceiver(mNetStateChangeReceiver);
			mNetStateChangeReceiver = null;
		}
	}

	/**
	 * 开始锁操作（设置操作类型 & 开始扫描）
	 */
	public void startLockActionScan() {
		PeachPreference.putStr(PeachPreference.KEY_LOCK_ACTION_TYPE, PeachPreference.VAL_LOCK_ACTION_TYPE_COMMON);

		requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
						: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
				new PermissionListener() {
					@Override
					public void onGranted() {
						MyApplication.sPPLOCK.startScan();
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {
						toast(isAndroid12() ? R.string.note_permission_ble_scan_connect : R.string.note_permission_lbs);
					}
				});
	}

	/**
	 * 开始锁操作（设置操作类型 & 开始扫描）
	 * 后台操作，不前台提示权限申请
	 */
	public void startLockActionScanNoPermissionNoToast() {
		PeachPreference.putStr(PeachPreference.KEY_LOCK_ACTION_TYPE, PeachPreference.VAL_LOCK_ACTION_TYPE_COMMON);

		requestRuntimePermissions(isAndroid12() ? PERMISSION_BLE_SCAN_CONNECT
						: new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
				new PermissionListener() {
					@Override
					public void onGranted() {
						MyApplication.sPPLOCK.startScan();
					}

					@Override
					public void onDenied(List<String> deniedPermissions) {

					}
				});
	}

	static class NetStateChangeReceiver extends BroadcastReceiver {

		SoftReference<BaseActivity> softReference;

		public NetStateChangeReceiver(BaseActivity baseActivity) {
			softReference = new SoftReference(baseActivity);
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if (null == softReference) {
				return;
			}
			if (intent.getAction().matches("android.net.conn.CONNECTIVITY_CHANGE")) {
				boolean isNetEnable = softReference.get().isNetEnableWithToast();
				PeachLogger.d("BroadcastReceiver-->onNetStateChange-->isNetEnable=" + isNetEnable);
				softReference.get().onNetStateChange(isNetEnable);
			}
		}
	}

	public void startImServiceActivity(Context context){
		EmailUtil.sendEmail(this);
	}

	public boolean isChineseLanguage() {
//		Locale locale = LanguageUtil.getLocaleByType(LanguageUtil.getLanguageType(this));

		Locale locale = LocalManageUtils.getSelectLanguageLocal(BaseActivity.this);
		return "ZH".equals(locale.getLanguage().toUpperCase());
	}
}
