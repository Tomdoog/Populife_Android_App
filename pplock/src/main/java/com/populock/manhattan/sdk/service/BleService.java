package com.populock.manhattan.sdk.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
//import android.support.annotation.Nullable;
//import android.support.annotation.RequiresPermission;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import android.text.TextUtils;
import android.util.Log;

import com.orhanobut.logger.Logger;
import com.populock.manhattan.sdk.BleDevice;
import com.populock.manhattan.sdk.api.PPLock;
import com.populock.manhattan.sdk.callback.BleWriteCallback;
import com.populock.manhattan.sdk.callback.OnScanFailedListener;
import com.populock.manhattan.sdk.callback.PPLockCallback;
import com.populock.manhattan.sdk.callback.SetLockTimeCallback;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populock.manhattan.sdk.constant.LockUrls;
import com.populock.manhattan.sdk.entity.LockBleSession;
import com.populock.manhattan.sdk.entity.LockData;
import com.populock.manhattan.sdk.entity.LockError;
import com.populock.manhattan.sdk.net.RestClient;
import com.populock.manhattan.sdk.net.callback.IError;
import com.populock.manhattan.sdk.net.callback.IFailure;
import com.populock.manhattan.sdk.net.callback.ISuccess;
import com.populock.manhattan.sdk.scanner.IScanCallback;
import com.populock.manhattan.sdk.scanner.ScannerCompat;
import com.populock.manhattan.sdk.util.Clibrary;
import com.populock.manhattan.sdk.util.ConfigUtil;
import com.populock.manhattan.sdk.util.DateUtil;
import com.populock.manhattan.sdk.util.HexUtil;
import com.populock.manhattan.sdk.util.LogUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service to handle all the operations of BluetoothLE
 * <p>
 * Created by Jerry
 */
public class BleService extends Service {

	public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
	public static final String UUID_WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
	public static final String UUID_READ = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
	private static final String TAG = "BleService";
	// Service connection state
	private static final int STATE_DISCONNECTED = 0; // The service is in disconnected state
	private static final int STATE_CONNECTING = 1; // The service is in connecting state
	private static final int STATE_CONNECTED = 2; // The service is in connected state
	/**
	 * 连接超时时间（10s）
	 */
	private static final long CONNECT_TIME_OUT = 10000L;
	public static int mConnectionState = STATE_DISCONNECTED; // STATE_DISCONNECTED, STATE_CONNECTING, STATE_CONNECTED
	/**
	 * bluetooth operation
	 */
	public static LockBleSession sLockBleSession = LockBleSession.getInstance();
	public static boolean isCanSendCommandAgain = true;
	public static boolean isCheckedLockPermission;
	public static String aesKeyStr;
	public static String lockName;
	private static BleService sBleService;

	private static PPLockCallback sPPLockCallback;

	private static int failCount = 0;

//	static {
//		System.loadLibrary("aes");
//	}

	private final ReentrantLock conLock = new ReentrantLock();
	public BleDevice mBleDevice;
	Timer timer;
	TimerTask disTimerTask;
	private Context mContext;
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothDevice mBluetoothDevice;
	private BluetoothGatt mBluetoothGatt;
	Runnable disconnectRunable = new Runnable() {
		public void run() {
			if (mConnectionState == STATE_CONNECTED) {
				LogUtil.d("disconnecting...");
				disconnect();
			} else if (mConnectionState == STATE_CONNECTING) {
				LogUtil.d("disconnecting...");
				disconnect();
				close();
				if (sPPLockCallback != null) {
					mBleDevice.disconnectStatus = 1;
					sPPLockCallback.onDeviceDisconnected(mBleDevice);
				} else {
					LogUtil.w("mPPLockCallback is null");
				}
			}
		}
	};
	private ScannerCompat mScanner;
	private boolean mIsScan; // whether have satrted scanning or not
	private boolean mIsScanning; // whether is scaning or not
	private ScanCallback scanCallback;
	private OnScanFailedListener mOnScanFailedListener;
	Runnable stateOnScanRunable = new Runnable() {
		@Override
		public void run() {
			startScan();
		}
	};
	private boolean isNeedReCon = true; // whether need reconnect or not
	private boolean isWaitCommand;
	private long connectTime; // the latest timestamp to connect
	private int connectCnt = 0; // connect count
	private int commandSendCount;
	private Handler mHandler;
	BroadcastReceiver bluttoothState = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String stateExtra = BluetoothAdapter.EXTRA_STATE;
			int state = intent.getIntExtra(stateExtra, BluetoothAdapter.STATE_OFF);
			switch (state) {
				case BluetoothAdapter.STATE_TURNING_ON:
					LogUtil.d("BluetoothAdapter.STATE_TURNING_ON");
					break;
				case BluetoothAdapter.STATE_ON:
					LogUtil.d("BluetoothAdapter.STATE_ON");
					if (mIsScan) {
						if (mHandler != null) {
							mHandler.postDelayed(stateOnScanRunable, 1000L);
						}
					} else {
						LogUtil.d("do not start scan");
					}
					break;

				case BluetoothAdapter.STATE_TURNING_OFF:
					LogUtil.d("BluetoothAdapter.STATE_TURNING_OFF");
					break;

				case BluetoothAdapter.STATE_OFF:
					LogUtil.d("BluetoothAdapter.STATE_OFF");
					mConnectionState = STATE_DISCONNECTED;
					break;
				default:
					break;
			}
		}
	};
	private BluetoothGattCharacteristic mNotifyCharacteristic;
	/**
	 * 传输数据
	 */
	private LinkedList<byte[]> dataQueue;
	private LinkedList<byte[]> cloneDataQueue;
	private int currentAPICommand;
	private int mReceivedBufferCount;
	/**
	 * 协议类型
	 */
	private String protocolType;
	/**
	 * 协议版本
	 */
	private String protocolVersion;
	/**
	 * 场景
	 */
	private String scene;
	/**
	 * 公司代号
	 */
	private String group;
	/**
	 * 供应商代号
	 */
	private String vendor;
	/**
	 * 产品型号
	 */
	private String modelNum;
	/**
	 * 硬件版本号
	 */
	private String hardwareVersion;
	/**
	 * 固件版本号
	 */
	private String firmwareVersion;
	private BleWriteCallback mBleWriteCallback;
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		@RequiresPermission(Manifest.permission.BLUETOOTH)
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (mBluetoothGatt != gatt) {
				LogUtil.w("gatt = " + gatt + ", status = " + status + ", newState = " + newState);
				gatt.disconnect();
				gatt.close();
			} else {
//				LogUtil.d( Thread.currentThread().toString());
				LogUtil.i("gatt = " + gatt + ", status = " + status + ", newState = " + newState);
				if (newState == BluetoothProfile.STATE_CONNECTED) { // The profile is in connected state
					Log.i(TAG, "Connected to GATT server.");
					mHandler.removeCallbacks(disconnectRunable);
					try {
						Thread.sleep(600L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					connectTime = System.currentTimeMillis();
					if (mBluetoothGatt != null) {
						Log.i(TAG, "Attempting to start service discovery: " + mBluetoothGatt.discoverServices());
					} else {
						mConnectionState = STATE_DISCONNECTED;
						PPLock.getPPLockCallback().onDeviceDisconnected(mBleDevice);
					}
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) { // The profile is in disconnected state
					isWaitCommand = false;
					isCanSendCommandAgain = true;
					isCheckedLockPermission = false;
					mHandler.removeCallbacks(disconnectRunable);
					if (isNeedReCon && connectCnt < 3 && System.currentTimeMillis() - connectTime < 2000L) {
						LogUtil.w("connect again: " + connectCnt);

						try {
							conLock.lock();
							connect(mBleDevice);
						} finally {
							conLock.unlock();
						}
					} else {
						mConnectionState = STATE_DISCONNECTED;
						Log.i(TAG, "Disconnected from GATT server.");
						close();
						readCacheLog();
						PPLock.getPPLockCallback().onDeviceDisconnected(mBleDevice);
					}
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (mBluetoothGatt == gatt) {
				LogUtil.d("gatt = " + gatt + ", status = " + status);
//				LogUtil.d(Thread.currentThread().toString());
				if (status == BluetoothGatt.GATT_SUCCESS) { // A GATT operation completed successfully
					if (mBluetoothGatt == null) {
						LogUtil.w("mBluetoothGatt is null");
						return;
					}

					BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(UUID_SERVICE));
					List gattCharacteristics;
					Iterator iterator;
					BluetoothGattCharacteristic gattCharacteristic;
					if (service != null) {
						BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(UUID_READ));
						gattCharacteristics = service.getCharacteristics();
						if (gattCharacteristics != null && gattCharacteristics.size() > 0) {
							iterator = gattCharacteristics.iterator();
							while (iterator.hasNext()) {
								gattCharacteristic = (BluetoothGattCharacteristic) iterator.next();
								LogUtil.d(gattCharacteristic.getUuid().toString());
								if (gattCharacteristic.getUuid().toString().equals(UUID_WRITE)) {
									mNotifyCharacteristic = gattCharacteristic;
									LogUtil.d("mNotifyCharacteristic: " + mNotifyCharacteristic);
								} else if (gattCharacteristic.getUuid().toString().equals(UUID_READ)) {
									gatt.setCharacteristicNotification(gattCharacteristic, true);
									BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_HEART_RATE_MEASUREMENT);
									descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
									if (gatt.writeDescriptor(descriptor)) {
										LogUtil.d("writeDescriptor succeeded");
									} else {
										LogUtil.d("writeDescriptor failed");
									}
								}
							}
						}
					} else {
						LogUtil.w("service is null");
						mConnectionState = STATE_DISCONNECTED;
						LogUtil.d("mBluetoothGatt.getServices().size():" + mBluetoothGatt.getServices().size());
//						if (mBluetoothGatt.getServices().size() > 0) {
//							mExtendedBluetoothDevice.setNoLockService(true);
//						}

						close();
						mBleDevice.disconnectStatus = 2;
						PPLock.getPPLockCallback().onDeviceDisconnected(mBleDevice);
					}
				} else {
					LogUtil.w("onServicesDiscovered received: " + status);
				}
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (mBluetoothGatt == gatt) {
//				LogUtil.d(Thread.currentThread().toString());
				super.onDescriptorWrite(gatt, descriptor, status);
				LogUtil.d("gatt = " + gatt + ", descriptor = " + descriptor + ", status = " + status);
				LogUtil.d(descriptor.getCharacteristic().getUuid().toString());
				isNeedReCon = false;
				mConnectionState = STATE_CONNECTED;
				PPLock.getPPLockCallback().onDeviceConnected(mBleDevice);
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (mBluetoothGatt != gatt) {
				LogUtil.e("onCharacteristicWrite fail: gatt=" + gatt + " characteristic=" + characteristic + " status=" + status);
			} else {
//				LogUtil.d(Thread.currentThread().toString());
				LogUtil.d("onCharacteristicWrite success: gatt = " + gatt + ", characteristic = " + HexUtil.encodeHexStr(characteristic.getValue()) + ", status = " + status);
				if (status == BluetoothGatt.GATT_SUCCESS) { // A GATT operation completed successfully
					if (dataQueue.size() > 0) {
						characteristic.setValue(dataQueue.poll());
						boolean success = gatt.writeCharacteristic(characteristic);
						LogUtil.e("write data again: " + success);
					} else {
						mHandler.removeCallbacks(disconnectRunable);
						// TODO: 2019-05-05 发送指令
						disTimerTask = new TimerTask() {
							public void run() {
								disconnect();
							}
						};
						long delay = 2500L;
						if (currentAPICommand == 19) { // 重置锁
							delay = 5500L;
						}
						if (timer != null) {
							timer.schedule(disTimerTask, delay);
						}
					}
				} else {
					LogUtil.w("onCharacteristicWrite failed");
				}
				super.onCharacteristicWrite(gatt, characteristic, status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicRead(gatt, characteristic, status);
			LogUtil.d(Thread.currentThread() + " " + new String(characteristic.getValue()));
			LogUtil.d(Thread.currentThread() + " " + characteristic.getUuid());
			if (status == BluetoothGatt.GATT_SUCCESS) {

			} else if (status == BluetoothGatt.GATT_FAILURE) {

			}
		}

		@Override
		@RequiresPermission(Manifest.permission.BLUETOOTH)
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			if (mBluetoothGatt == gatt) {
				super.onCharacteristicChanged(gatt, characteristic);
//				LogUtil.d(Thread.currentThread().toString());
				try {
					LogUtil.d("gatt = " + gatt + " characteristic = " + characteristic);
					byte[] data = characteristic.getValue();
					int dataLen = data.length;
					String responseData = HexUtil.encodeHexStr(data);
//					int responseLen = responseData.length();
//					if (responseLen>6){
//						String fingerResStart = responseData.substring(0,6);
//						if (fingerResStart.equalsIgnoreCase("01001a")||
//								fingerResStart.equalsIgnoreCase("010019") ||
//								fingerResStart.equalsIgnoreCase("01001b")){
//							    saveAck(responseData);
//							    if (responseLen >= 19){
//							    	String comId = responseData.substring(4,2);
//									String stateId = responseData.substring(10,2);
//									String fingerId = responseData.substring(14,2);
//									if (comId.equals("19")) {
//										replyAck();
//										if (stateId.equals("00")) {
//											sLockBleSession.getmAddFingerprintCallback().onSuccess(1,null);
//											LogUtil.d("请再按一次手指" + "1");
//										} else if (stateId.equalsIgnoreCase("ff")) {
//											LockError error = LockError.FAIL;
//											sLockBleSession.getmAddFingerprintCallback().onFail(error);
//										} else if (stateId.equalsIgnoreCase("fe")){
//											if (failCount == 3){
//												LockError error = LockError.FAIL;
//												sLockBleSession.getmAddFingerprintCallback().onFail(error);
//											}
//											failCount++;
//										}
//									}else if (comId.equalsIgnoreCase("1a")){
//										replyAck();
//										failCount=0;
//										if (stateId.equals("00")) {
//											sLockBleSession.getmAddFingerprintCallback().onSuccess(2,null);
//											LogUtil.d("请再按一次手指" + "2");
//										} else if (stateId.equalsIgnoreCase("ff")) {
//											LockError error = LockError.FAIL;
//											sLockBleSession.getmAddFingerprintCallback().onFail(error);
//										} else if (stateId.equalsIgnoreCase("fe")){
//											if (failCount == 3){
//												LockError error = LockError.FAIL;
//												sLockBleSession.getmAddFingerprintCallback().onFail(error);
//											}
//											failCount++;
//										}
//									}else if(comId.equalsIgnoreCase("1b")){
//										replyAck();
//										failCount=0;
//										if (stateId.equals("00")) {
//											sLockBleSession.getmAddFingerprintCallback().onSuccess(3,fingerId);
//											LogUtil.d("录入成功" + "3");
//										} else if (stateId.equalsIgnoreCase("ff")) {
//											LockError error = LockError.FAIL;
//											sLockBleSession.getmAddFingerprintCallback().onFail(error);
//										} else if (stateId.equalsIgnoreCase("fe")){
//											if (failCount == 3){
//												LockError error = LockError.FAIL;
//												sLockBleSession.getmAddFingerprintCallback().onFail(error);
//											}
//											failCount++;
//										}
//
//									}
//
//								}
//
//						}else if (fingerResStart.equalsIgnoreCase("010020")) {//添加卡片
//							saveAck(responseData);
//							if (responseLen >= 19) {
//								String comId = responseData.substring(4, 2);
//								String stateId = responseData.substring(10, 2);
//								String cardId = responseData.substring(14, 2);
//								if (comId.equals("20")) {
//									replyAck();
//									if (stateId.equals("00")) {//添加卡片成功
//										sLockBleSession.getmAddCardCallback().onSuccess(cardId);
//									} else {
//										LockError error = LockError.FAIL;
//										sLockBleSession.getmAddCardCallback().onFail(error);
//									}
//								}
//
//							}
//
//						}
//					}

					mBleWriteCallback.onResponseSuccess(HexUtil.encodeHexStr(data));
//					if (mReceivedBufferCount + dataLen <= maxBufferCount) {
//						System.arraycopy(data, 0, mReceivedDataBuffer, mReceivedBufferCount, dataLen);
//						mReceivedBufferCount = mReceivedBufferCount + dataLen;
//					}
//						0300060040 5447983d1e152b6c4c72d0e3f40cee895baca0d27d119b579a493916978599af5b9b592d54c86675aa6d3e5fd5646360cdf719a2553d58a971950c78c3ee5aa4
				} catch (Exception e) {
					e.printStackTrace();
					mReceivedBufferCount = 0;
				}
			} else {
				Log.e(TAG, "fail..... ");
			}
		}
	};
	private LockData mLockData;
	private int maxBufferCount = 256;
	private byte[] mReceivedDataBuffer;

	public static BleService getBleService() {
		return sBleService;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		sBleService = this;
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
			registerReceiver(bluttoothState, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),RECEIVER_EXPORTED);
		}else {
			registerReceiver(bluttoothState, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		}
		LogUtil.d("BleService is created");
		mContext = getApplicationContext();
		mHandler = new Handler();
		sPPLockCallback = PPLock.getPPLockCallback();
		mReceivedDataBuffer = new byte[this.maxBufferCount];
		initialize();
	}

	@SuppressLint("MissingPermission")
	private void initialize() {

		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				LogUtil.e("Unable to initialize BluetoothManager.");
				return;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			LogUtil.e("Unable to obtain a BluetoothAdapter.");
		} else {
			scanCallback = new ScanCallback();
			mScanner = ScannerCompat.getScanner();
			if (PPLock.sIsScan) {
				mIsScan = true;
				startScan();
			} else {
				mIsScan = false;
			}
		}
	}

	public boolean isConnected(String address) {
		if (address == null) {
			return false;
		} else {
			return address.equals(mBluetoothDeviceAddress) && mConnectionState == STATE_CONNECTED;
		}
	}

	public boolean isScanning() {
		return mIsScanning;
	}

	public synchronized void setScan(boolean isScan) {
		this.mIsScan = isScan;
	}

	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public void startScan() {
		mIsScan = true;
		if (!mIsScan) {
			LogUtil.w("Already stop scan");
		} else {
			LogUtil.d("Start scan");
			if (!mBluetoothAdapter.isEnabled()) {
				LogUtil.w("BluetoothAdapter is disabled");
			} else {
				if (mScanner == null) {
					mScanner = ScannerCompat.getScanner();
				}

				if (scanCallback == null) {
					scanCallback = new ScanCallback();
				}

				stopScan();
				mScanner.setOnScanFailedListener(mOnScanFailedListener);
				LogUtil.d("start ---");
				mScanner.startScan(scanCallback);
				mIsScanning = true;
				if (mConnectionState != STATE_DISCONNECTED) { // 考虑没有断开的情况
					LogUtil.w("Ble not disconnected");
				}
			}
		}
	}

	public void stopScan() {
		mIsScan = false;
		LogUtil.d("mScanner:" + mScanner);
		LogUtil.d("mIsScanning:" + mIsScanning);
		if (mScanner != null && mIsScanning) {
			mIsScanning = false;
			try {
				mScanner.stopScan();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public boolean connect(String address) {
		boolean result;
		try {
			conLock.lock();
			connectCnt++;
			connectTime = System.currentTimeMillis();
			if (mBluetoothAdapter != null && address != null) {
				if (mBluetoothGatt != null) {
					LogUtil.d("mBluetoothGatt not null");
					disconnect();
					close();
				}
				LogUtil.d("mac connect - mac: " + address);
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				LogUtil.d("connect...");
				mHandler.removeCallbacks(disconnectRunable);
				mHandler.postDelayed(disconnectRunable, CONNECT_TIME_OUT);
				mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
				mConnectionState = STATE_CONNECTING;
				LogUtil.d("mBluetoothGatt: " + mBluetoothGatt);
				LogUtil.i("Trying to create a new connection.");
				LogUtil.i("connected mBluetoothGatt: " + mBluetoothGatt);
				mBluetoothDeviceAddress = address;
				mBluetoothDevice = device;
				mBleDevice = new BleDevice(device);
				result = true;
			} else {
				LogUtil.w("BluetoothAdapter not initialized or unspecified address.");
				LogUtil.w("mBluetoothAdapter: " + mBluetoothAdapter);
				LogUtil.w("address: " + address);
				result = false;
			}
		} finally {
			conLock.unlock();
		}
		return result;
	}

//	/* connect to the device with specified address */
//	public boolean connect(final String deviceAddress) {
//		if (mBluetoothAdapter == null || deviceAddress == null) {
//			return false;
//		}
//		mBluetoothDeviceAddress = deviceAddress;
//
//		// check if we need to connect from scratch or just reconnect to previous device
//		if (mBluetoothGatt != null && mBluetoothGatt.getDevice().getAddress().equals(deviceAddress)) {
//			// just reconnect
//			return mBluetoothGatt.connect();
//		} else {
//			// connect from scratch
//			// get BluetoothDevice object for specified address
//			mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
//			if (mBluetoothDevice == null) {
//				// we got wrong address - that device is not available!
//				return false;
//			}
//			// connect with remote device
//			mBluetoothGatt = mBluetoothDevice.connectGatt(this, false, mGattCallback);
//		}
//		return true;
//	}

	@RequiresPermission(Manifest.permission.BLUETOOTH)
	public boolean connect(BleDevice bleDevice) {
		connectCnt++;
		connectTime = System.currentTimeMillis();
		LogUtil.i("BleDevice:" + bleDevice);
		String address = bleDevice.getDevice().getAddress();

		if (mBluetoothAdapter != null && address != null) {

			if (mBluetoothGatt != null) {
				LogUtil.d("mBluetoothGatt not null");
				disconnect();
				close();
			}
			LogUtil.d("Device connect - mac: " + address);
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
			LogUtil.d("connect...");
			mHandler.removeCallbacks(disconnectRunable);
			mHandler.postDelayed(disconnectRunable, CONNECT_TIME_OUT);
			bleDevice.disconnectStatus = 0;
			mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
			mConnectionState = STATE_CONNECTING;
			LogUtil.i("Trying to create a new connection.");
			LogUtil.i("connected mBluetoothGatt: " + mBluetoothGatt);
			mBluetoothDeviceAddress = address;
			mBluetoothDevice = device;
			mBleDevice = bleDevice;
			return true;
		} else {
			LogUtil.w("BluetoothAdapter not initialized or unspecified address.");
			LogUtil.w("mBluetoothAdapter: " + mBluetoothAdapter);
			LogUtil.w("address: " + address);
			return false;
		}
	}

	public void clearTask() {
		LogUtil.w("clear task");
		if (this.mHandler != null) {
			this.mHandler.removeCallbacks(this.disconnectRunable);
		}

		if (this.disTimerTask != null) {
			this.disTimerTask.cancel();
		}

		if (this.timer != null) {
			this.timer.purge();
		}
	}

	/**
	 * 初始化锁请求
	 */
	public void initLockRequest(final String userId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "03";
		String keyNum = "05";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 030005
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 030005
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {

				LogUtil.e("初始化锁请求发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("初始化锁请求发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("初始化锁请求返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						stringBuilder.append(response.substring(10));
					} else {
						stringBuilder.append(response);
					}
					if (receiveCount[0] == 6) {
						replyAck();
						// 将解析后的数据回调给该方法的调用者进行处理
						// 64 字节 AES 密文 C0
						aesKeyStr = stringBuilder.toString();
						LogUtil.d("aesC0: " + aesKeyStr);

//						String K0 = "0123456789ABCDEF";
//						String aesK2 = aesC0.substring(0, 32); // GuestAESKey
//						String aesK1 = aesC0.substring(32, 64);
//						String aesLockKey = aesC0.substring(64, 96);
//						String aesAdminPW = aesC0.substring(96, 128);

//						byte[] K1 = AESUtil.aesDecrypt(DigitUtil.strToByteArray(aesK1), DigitUtil.strToByteArray(K0));
//						byte[] c1 = AESUtil.aesEncrypt(DigitUtil.strToByteArray("01020304050607080102030405060708"), K1);
//						String C1 = DigitUtil.byteArrayToStr(c1);
//						LogUtil.d(C1);
                        //.params("userId", userId)
						if (aesKeyStr.length() == 128) { // 数据正常
							RestClient.builder()
									.url(LockUrls.INIT_LOCK_C1_GET)
									.params("aesKey", aesKeyStr)
									.params("userId", userId)
									.success(new ISuccess() {
										@Override
										public void onSuccess(String response) {
											LogUtil.d("GET_C1: " + response);

											try {
												JSONObject result = new JSONObject(response);
												int code = result.getInt("code");
												if (code == 200) {
													final String C1 = result.getString("data");
													sLockBleSession.getInitLockRequestCallback().onSuccess(C1);
													LogUtil.d("C1: " + C1);
												} else {
													sLockBleSession.getInitLockRequestCallback().onFail(LockError.LOCK_NO_PERMISSION);
												}
											} catch (JSONException e) {
												e.printStackTrace();
												sLockBleSession.getInitLockRequestCallback().onFail(LockError.LOCK_NO_PERMISSION);
											}
										}
									})
									.failure(new IFailure() {
										@Override
										public void onFailure() {
											LogUtil.e("fail");
											sLockBleSession.getInitLockRequestCallback().onFail(LockError.LOCK_NO_PERMISSION);
										}
									})
									.error(new IError() {
										@Override
										public void onError(int code, String msg) {
											LogUtil.e("error");
											sLockBleSession.getInitLockRequestCallback().onFail(LockError.LOCK_NO_PERMISSION);
										}
									})
									.build()
									.get();
//							String C1 = getC1(aesKeyStr);
//							Log.e("aaaaaaaaa", C1);
//							sLockBleSession.getInitLockRequestCallback().onSuccess(C1);
						} else {
							sLockBleSession.getInitLockRequestCallback().onFail(LockError.LOCK_NO_PERMISSION);
						}
					}
				}
			}
		});
	}


//	private String getC1(String aesKey) {
//		String text = aesKey.substring(32, 64);
//		byte[] byteArr = HexUtil.decodeHex(text);
//		byte[] arr = new byte[16];
//		Clibrary.INSTANCE.AESDecrypt(byteArr, byteArr.length, Clibrary.PASSWORD, Clibrary.PASSWORD.length, 0, byteArr, arr);
//		return HexUtil.encodeHexStr(arr);
//	}

	private String getC1(String k1, String random) {
		byte[] byteArr = HexUtil.decodeHex(random);
		byte[] k1Arr = HexUtil.decodeHex(k1);
		byte[] arr = new byte[16];
		Clibrary.INSTANCE.AESDecrypt(byteArr, 16, k1Arr, 16, 0, byteArr, arr);
		return HexUtil.encodeHexStr(arr);
	}

	/**
	 * 初始化锁认证
	 */
	public void initLockVerify(String C1) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);
		String comandNum = "03";
		String keyNum = "07";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 030007
		int specialLength = comandString.length() / 2; // 3
//		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
//		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString);
		String dataStr = String.format("AB0000150000%s0300070010%s", sequenceID, C1);
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("初始化锁认证发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("初始化锁认证发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if ("030008000100".equals(response)) { // 认证成功
						sLockBleSession.getInitLockVerifyCallback().onSuccess();
					} else if ("030008000101".equals(response)) { // 认证失败
						sLockBleSession.getInitLockVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
					}
				}
			}
		});
	}

	/**
	 * （管理员）删除锁
	 */
	public void adminDeleteLock() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "03";
		String keyNum = "03";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 030003
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString);
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("删除锁发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("删除锁发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if ("030004000100".equals(response)) { // 删除锁成功
						sLockBleSession.getDeleteLockCallback().onSuccess(mBleDevice);
//						PPLock.getPPLockCallback().onDeleteLock(mBleDevice, LockError.SUCCESS);
					} else if ("030004000101".equals(response)) { // 删除锁失败
						sLockBleSession.getDeleteLockCallback().onFail(LockError.FAIL);
//						PPLock.getPPLockCallback().onDeleteLock(mBleDevice, LockError.DELETE_LOCK_FAIL);
					}
				}
			}
		});
	}

	/**
	 * 查询锁版本
	 */
	public void getLockVersion() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "02";
		String keyNum = "01";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 020001
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020001
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁版本发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁版本发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁版本返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetLockVersionCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else { // 02000400050101010101
//							String str = response.substring(10);
//							String protocolType = str.substring(0, 2);
//							String protocolVersion = str.substring(2, 4);
//							String scene = str.substring(4, 6);
//							String group = str.substring(6, 8);
//							String vendor = str.substring(8, 10);

							JSONObject object = new JSONObject();
							try {
								// 固定值
								object.put("protocolType", "1");
								object.put("protocolVersion", "1");
								object.put("scene", "1");
								object.put("group", "Populstay");
								object.put("vendor", "Populstay");
							} catch (JSONException e) {
								e.printStackTrace();
							}
							protocolType = "1";
							protocolVersion = "1";
							scene = "1";
							group = "Populstay";
							vendor = "Populstay";
							sLockBleSession.getGetLockVersionCallback().onSuccess(object.toString());
						}
					}
				}
			}
		});
	}

	/**
	 * 查询锁信息
	 */
	public void getLockInfo() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "02";
		String keyNum = "05";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 020005
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020005
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁信息发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁信息发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁信息返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						stringBuilder.append(response.substring(10));
					} else {
						stringBuilder.append(response);
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetLockInfoCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else {
							/*
							 * 产品型号 - 硬件版本 - 固件版本 - 生产日期 - 蓝牙地址
							 * 534e3133385f50504c2d4442 - 312e30 - 312e302e302e3131303800 - 1201 - 4846a1f113e2
							 * SN138_PPL-DB - 1.0 - 1.0.0.11080 -  （20）18 年 1 月 - 48:46:a1:f1:13:e2
							 * */
							// 解析锁信息数据
							JSONObject object = parseLockInfo(stringBuilder.toString());
							mLockData = getLockDataObj();
							// 初始化锁成功，校准锁时间
							// 时间同步由应用持久化初始化凭据后执行，回读成功前不得提交绑定。
//							sLockBleSession.getGetLockInfoCallback().onSuccess(object.toString());
							PPLock.getPPLockCallback().onInitLock(mBleDevice, mLockData, LockError.SUCCESS);
							sLockBleSession.setLockOperation(LockOperation.INIT_LOCK_DELETE);
						}
					}
				}
			}
		});
	}

	/**
	 * 查询锁电量
	 */
	public void getBatteryLevel() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "02";
		String keyNum = "07";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 020007
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020007
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁电量发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁电量发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁电量返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetBatteryLevelCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else { // 020008000125
							String str = response.substring(10);
							int battery = HexUtil.hexStr2Int(str);
							mBleDevice.setBatteryLevel(battery);
							sLockBleSession.getGetBatteryLevelCallback().onSuccess(battery);
						}
					}
				}
			}
		});
	}

	/**
	 * 查询锁时间
	 */
	public void getLockTime() {
        getLockTime(sLockBleSession.getTimezoneRawOffset());
    }

    public void getLockTime(final Integer expectedOffset) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "02";
		String keyNum = "09";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 020009
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020009
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁时间发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁时间发送失败");
                if (sLockBleSession.getGetLockTimeCallback() != null)
                    sLockBleSession.getGetLockTimeCallback().onFail(LockError.FAIL);
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁时间返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetLockTimeCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else {
							String str = response.substring(10);

							int year = 2000 + HexUtil.hexStr2Int(str.substring(0, 2));
							int month = HexUtil.hexStr2Int(str.substring(2, 4));
							int day = HexUtil.hexStr2Int(str.substring(4, 6));
							int hour = HexUtil.hexStr2Int(str.substring(6, 8));
							int minute = HexUtil.hexStr2Int(str.substring(8, 10));
							int second = HexUtil.hexStr2Int(str.substring(10, 12));
							int weekDay = HexUtil.hexStr2Int(str.substring(12, 14));
							int timeZone = HexUtil.hexStr2Int(str.substring(14, 16));

							JSONObject object = new JSONObject();
							try {
								object.put("year", year);
								object.put("month", month);
								object.put("day", day);
								object.put("hour", hour);
								object.put("minute", minute);
								object.put("second", second);
								object.put("weekDay", weekDay);
								object.put("timeZone", timeZone);
							} catch (JSONException e) {
								e.printStackTrace();
							}
							if (expectedOffset != null && timeZone != com.populock.manhattan.sdk.util.LockTimeZone.encode(expectedOffset)) {
                                sLockBleSession.getGetLockTimeCallback().onFail(LockError.FAIL);
                                return;
                            }
                            java.util.TimeZone zone = expectedOffset == null ? java.util.TimeZone.getDefault()
                                    : new java.util.SimpleTimeZone(expectedOffset, "Lock");
                            Calendar value = Calendar.getInstance(zone);
                            value.clear();
                            value.set(year, month - 1, day, hour, minute, second);
                            long lockTime = value.getTimeInMillis();
							sLockBleSession.getGetLockTimeCallback().onSuccess(lockTime);
						}
					}
				}
			}
		});
	}

	/**
	 * 查询锁操作记录
	 */
	public void getLockOperateLog(final String lockId, final String keyId, final String userId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);
		String comandNum = "02";
		String keyNum = "0B";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 02000B
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 02000B
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		final int[] logLength = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();
		final List<JSONObject> logList = new ArrayList<>();
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁操作记录发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁操作记录发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁操作记录返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						if (TextUtils.isEmpty(response) || "02000c0000".equalsIgnoreCase(response)) { // 无最新的操作记录
							replyAck();
							LogUtil.e("no new logs");
							sLockBleSession.getGetLockOperateLogCallback().onSuccess();
							return;
						} else { // 有最新的操作记录
							// 截取字符串，获取操作记录返回数据总长度
							String logLen = response.substring(6, 10);
							// 操作记录的字节长度
							logLength[0] = HexUtil.hexStr2Int(logLen);
							stringBuilder.append(response.substring(10));
						}
					} else {
						stringBuilder.append(response);
					}

					// 接收数据完毕，开始解析操作记录
					String operateLog = stringBuilder.toString(); // 整个操作记录数据
					if (operateLog.length() == logLength[0] * 2) {
						replyAck();
						if (lockName.contains("_ml_") || lockName.contains("_ML_")) {
							for (int i = 0; i < logLength[0] / 20; i++) {
								// 一条操作记录
								String oneLog = operateLog.substring(i * 40, i * 40 + 40);
								JSONObject object = parseOperateLog(lockId, keyId, oneLog);
								if (object != null) logList.add(object);
							}

						}else {
							for (int i = 0; i < logLength[0] / 16; i++) {
								// 一条操作记录
								String oneLog = operateLog.substring(i * 32, i * 32 + 32);
								JSONObject object = parseOperateLog(lockId, keyId, oneLog);
								if (object != null) logList.add(object);
							}
						}
						if (logList.isEmpty()) { // 无记录数据
							LogUtil.e("no new logs");
							sLockBleSession.getGetLockOperateLogCallback().onSuccess();
						} else { // 有记录数据
							LogUtil.e("log: " + logList.toString());
							uploadOperateLog(lockId, keyId, logList.toString(),userId);
						}
					}
				}
			}
		});
	}

	/**
	 * 上传锁操作记录
	 */
	//.raw(logJson)
	private void uploadOperateLog(final String lockId, final String keyId, String logJson,final String userId) {
		if (logJson==null){
			logJson = "";
		}
		RestClient.builder()
				.url(LockUrls.OPERATE_LOG_UPLOAD)
				.params("userId", userId)
				.params("lockId", lockId)
				.params("records", logJson)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("OPERATE_LOG_UPLOAD: " + response);
						// 读取并上传成功，再次读取
						getLockOperateLog(lockId, keyId,userId);
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getGetLockOperateLogCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getGetLockOperateLogCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}

	/**
	 * 查询锁固件版本
	 */
	public void getFirmwareVersion() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandNum = "02";
		String keyNum = "0F";
		String comandString = String.format("%s00%s", comandNum, keyNum); // 02000F
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 02000F
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询锁固件版本发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询锁固件版本发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询锁固件版本返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetFirmwareVersionCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else { // 020010000B312E302E302E31313038（1.0.0.1108，ASCII 值）
							// TODO: 2019-07-16 测试（协议 11 字节？还是 10 字节？）
							firmwareVersion = HexUtil.hexStr2Ascii(response);
							sLockBleSession.getGetFirmwareVersionCallback().onSuccess(firmwareVersion);
						}
					}
				}
			}
		});
	}

	/**
	 * 查询自动上锁时间
	 */
	public void getAutoLockTime() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandString = "020011";
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020011
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询自动上锁时间发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询自动上锁时间发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询自动上锁时间返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetAutoLockTimeCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else {
							String str = response.substring(10);
							int time = HexUtil.hexStr2Int(str);
							sLockBleSession.getGetAutoLockTimeCallback().onSuccess(time);
						}
					}
				}
			}
		});
	}

	/**
	 * 查询上锁状态（上锁/打开）
	 */
	public void getLockStatus() {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandString = "020013";
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0003 0000 0001 020013
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("查询查询上锁状态发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("查询查询上锁状态发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("查询查询上锁状态返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						replyAck();

						if ("03000d000102".equalsIgnoreCase(response)) {
							// 权限不足
							LogUtil.e("权限不足");
							sLockBleSession.getGetLockStatusCallback().onFail(LockError.LOCK_NO_PERMISSION);
						} else {
							String str = response.substring(10);
							int status = HexUtil.hexStr2Int(str);
							sLockBleSession.getGetLockStatusCallback().onSuccess(status == 0); // 0 上锁，1 打开
						}
					}
				}
			}
		});
	}

	/**
	 * 解析锁信息数据
	 */
	private JSONObject parseLockInfo(String lockInfo) {
		JSONObject object = new JSONObject();

		modelNum = HexUtil.hexStr2Ascii(lockInfo.substring(0, 24));
		hardwareVersion = HexUtil.hexStr2Ascii(lockInfo.substring(24, 30));
		firmwareVersion = HexUtil.hexStr2Ascii(lockInfo.substring(30, 50));

		try {
			object.put("modelNum", modelNum);
			object.put("hardwareVersion", hardwareVersion);
			object.put("firmwareVersion", firmwareVersion);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return object;
	}

	/**
	 * 解析锁的操作记录（单条）
	 *
	 * @param oneLog 单条操作记录数据
	 */
	private JSONObject parseOperateLog(final String lockId, final String keyId, String oneLog) {
		JSONObject object = null;

		// 日志类型（0-密码开锁，1-APP 开锁，2-APP 闭锁）
		int logType = HexUtil.hexStr2Int(oneLog.substring(0, 2));

		if ((logType == 0) || (logType == 7) || (logType == 8)) { // 筛选键盘密码日志类型（0-密码开锁,7-指纹开锁，8-密码开锁）
			int year = 2000 + HexUtil.hexStr2Int(oneLog.substring(2, 4));
			int month = HexUtil.hexStr2Int(oneLog.substring(4, 6));
			int day = HexUtil.hexStr2Int(oneLog.substring(6, 8));
			int hour = HexUtil.hexStr2Int(oneLog.substring(8, 10));
			int minute = HexUtil.hexStr2Int(oneLog.substring(10, 12));
			int second = HexUtil.hexStr2Int(oneLog.substring(12, 14));
			String uid = oneLog.substring(14);
			StringBuilder stringBuilder = new StringBuilder();
//			if (logType == 7 || logType == 8) {
//				if (logType == 8) {//卡片卡号增加到10个字节
//					for (int i = 0; i < 3; i += 2) {
//						String str = uid.substring(i, i + 2);
//						// 返回结果填充 ff，则过滤掉
//						if ("ff".equalsIgnoreCase(str)) {
//							continue;
//						}
//						String pwdStr = String.valueOf(HexUtil.hexStr2Int(str));
//						stringBuilder.append(pwdStr);
//					}
//				}else {
//					for (int i = 0; i < 3; i += 2) {
//						String str = uid.substring(i, i + 2);
//						// 返回结果填充 ff，则过滤掉
//						if ("ff".equalsIgnoreCase(str)) {
//							continue;
//						}
//						String pwdStr = String.valueOf(HexUtil.hexStr2Int(str));
//						stringBuilder.append(pwdStr);
//					}
//
//				}
//
//
//			}else {
				for (int i = 0; i < uid.length(); i += 2) {
					String str = uid.substring(i, i + 2);
					// 返回结果填充 ff，则过滤掉
					if ("ff".equalsIgnoreCase(str)) {
						continue;
					}

					String pwdStr;

					if (logType == 8) {//卡片

						pwdStr = str;

					}else{

						pwdStr = String.valueOf(HexUtil.hexStr2Int(str));
					}


					stringBuilder.append(pwdStr);
				}
//			}
			String currentTime = String.format("%s-%s-%s %s:%s:%s", year, month, day, hour, minute, second);
			SimpleDateFormat dateFormat = new SimpleDateFormat(DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS);
			Date date = new Date();
			try {
				date = dateFormat.parse(currentTime);
			} catch (ParseException e) {
				e.printStackTrace();
			}

			try {
				object = new JSONObject();
				object.put("lockId", lockId);
				object.put("keyId", keyId);
				object.put("operateDate", date.getTime() / 1000);
				object.put("uid", stringBuilder.toString());
				object.put("recordType", logType);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		return object;
	}

	/**
	 * 身份认证请求
	 */
	public void authVerifyRequest(final boolean isAdmin, final String userId, final String lockId, final String keyId, final String k1) {
		Logger.t("PPLock").e("开始authVerifyRequest");
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);
		String comandStr = "030009";
		int specialLength = comandStr.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandStr); // AB00 0003 0000 0001 030009
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("身份认证请求发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("身份认证请求发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("身份认证请求返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						stringBuilder.append(response.substring(10));
					} else {
						replyAck();
						stringBuilder.append(response);

						// 16 字节 random
						String randomStr = stringBuilder.toString();
						LogUtil.d("randomStr: " + randomStr);
						if (randomStr.length() == 32) { // 数据正常
//							String C1 = k1AESDecryptRandom(k1,randomStr);
//							LogUtil.d("C1: " + C1);
//							authVerify(isAdmin, C1);
//							.params("lockId", lockId)
//									.params("keyId", keyId)
							RestClient.builder()
									.url(isAdmin ? LockUrls.POPULIFE_AUTH_VERIFY_ADMIN_C1_GET : LockUrls.POPULIFE_AUTH_VERIFY_ADMIN_C1_GET)
									.params("userId", userId)
									.params("randomStr", randomStr)
									.params("lockId", lockId)
									.success(new ISuccess() {
										@Override
										public void onSuccess(String response) {
											LogUtil.d("LOCK_BLE_VERIFY_C1_GET: " + response);
											try {
												JSONObject result = new JSONObject(response);
												int code = result.getInt("code");
												if (code == 200) {
													String C1 = result.getString("data");
													LogUtil.d("C1: " + C1);
													authVerify(isAdmin, C1);
												} else {
													sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
												}
											} catch (JSONException e) {
												e.printStackTrace();
												sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
											}
										}
									})
									.failure(new IFailure() {
										@Override
										public void onFailure() {
											LogUtil.e("fail");
											sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
										}
									})
									.error(new IError() {
										@Override
										public void onError(int code, String msg) {
											LogUtil.e("error");
											sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
										}
									})
									.build()
									.get();
						} else { // 数据异常
							sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
						}
					}
				}
			}
		});
	}

	/**
	 * 身份认证
	 *
	 * @param C1 16 字节密文
	 */
	public void authVerify(final boolean isAdmin, String C1) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = (isAdmin ? "01" : "02") + C1;

		String comandString = "03000B" + "0011" + paramStr;
		int specialLength = comandString.length() / 2; // 22
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0016
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0016 0000 0001 03000B 0011 + 17 字节 value
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};
		LogUtil.e("data: " + dataStr);
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("身份认证发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("身份认证发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("身份认证返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if (response.length() == 12) {
						String flagStr = response.substring(10);
						if ("00".equalsIgnoreCase(flagStr)) {
							sLockBleSession.getAuthVerifyCallback().onSuccess();
						} else {
							sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
						}
					} else {
						sLockBleSession.getAuthVerifyCallback().onFail(LockError.LOCK_NO_PERMISSION);
					}
				}
			}
		});
	}

	/**
	 * 管理员开锁
	 */
	public void adminUnlock(String userId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = HexUtil.ascii2HexStr(userId); // 用户 id，长度 12

		String comandString = "040001" + "0006" + paramStr;
		int specialLength = comandString.length() / 2; // 11
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 000B
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 000B 0000 0001 040001 0006 + 6 字节 uid 对应的 ASCII 码
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("管理员开锁发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("管理员开锁发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("管理员开锁返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if (response.length() == 12) { // 040002000100
						String flagStr = response.substring(10);
						if ("00".equalsIgnoreCase(flagStr)) {
							sLockBleSession.getAdminUnlockCallback().onSuccess();
						} else {
							sLockBleSession.getAdminUnlockCallback().onFail(LockError.FAIL);
						}
					} else {
						sLockBleSession.getAdminUnlockCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 管理员闭锁
	 */
	public void adminLock(String userId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = HexUtil.ascii2HexStr(userId); // 用户 id，长度 12
		LogUtil.e("lock: " + paramStr);
		String comandString = "040007" + "0006" + paramStr;
		int specialLength = comandString.length() / 2; // 11
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 000B
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 000B 0000 0001 040007 0006 + 6 字节 uid 对应的 ASCII 码
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("管理员闭锁发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("管理员闭锁发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("管理员闭锁返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if (response.length() == 12) { // 040008000100
						String flagStr = response.substring(10);
						if ("00".equalsIgnoreCase(flagStr)) {
							sLockBleSession.getAdminLockCallback().onSuccess();
						} else {
							sLockBleSession.getAdminLockCallback().onFail(LockError.FAIL);
						}
					} else {
						sLockBleSession.getAdminLockCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 普通用户开锁请求
	 *
	 * @param keyType 电子钥匙类型
	 *                限时：1
	 *                永久：2
	 *                单次：3
	 *                循环：4周一循环，5周二循环，6周三循环，7周四循环，8周五循环，9周六循环，10周日循环，11每日循环，12工作日循环，13周末循环
	 */
	public void userUnlockRequest(final String userId, final String lockId, final String keyId, final int keyType,
								  final long startDate, final long endDate, final int cyclicStartHour,
								  final int cyclicEndHour, final long createDate) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = HexUtil.ascii2HexStr(userId); // 用户 id，长度 12
		String comandString = "040003" + "0006" + paramStr;
		int specialLength = comandString.length() / 2; // 11
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 000B
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 000B 0000 0001 040003 0006 + 6 字节 uid 对应的 ASCII 码
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("普通用户开锁请求发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("普通用户开锁请求发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("普通用户开锁请求返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					// 8 字节随机数 R1
					String R1 = response.substring(10);

					// 将电子钥匙的时限参数按照规则填充成 8 字节的 T1
					String T1 = getEkeyTimeParam(keyType, startDate, endDate, cyclicStartHour, cyclicEndHour, createDate);

					// 将 R1 和 T1 拼接成 16 字节，再用 K1 加密获得密文 C1
					String paramStr = R1 + T1;

					// 用 K1 对密码数据进行加密
					RestClient.builder()
							.url(LockUrls.AUTH_VERIFY_USERUNLOCK_C1_GET)
							.params("userId", userId)
							.params("lockId", lockId)
							.params("keyId", keyId)
							.params("randomStr", paramStr)
							.success(new ISuccess() {
								@Override
								public void onSuccess(String response) {
									LogUtil.d("USER_UNLOCK_ENCRYPT: " + response);

									try {
										JSONObject result = new JSONObject(response);
										int code = result.getInt("code");
										if (code == 200) {
											// 密文 C1
											String C1 = result.getString("data");
											userUnlockVerify(C1);
										} else {
											sLockBleSession.getUserUnlockCallback().onFail(LockError.FAIL);
										}
									} catch (JSONException e) {
										e.printStackTrace();
										sLockBleSession.getUserUnlockCallback().onFail(LockError.FAIL);
									}
								}
							})
							.failure(new IFailure() {
								@Override
								public void onFailure() {
									sLockBleSession.getUserUnlockCallback().onFail(LockError.FAIL);
								}
							})
							.error(new IError() {
								@Override
								public void onError(int code, String msg) {
									sLockBleSession.getUserUnlockCallback().onFail(LockError.FAIL);
								}
							})
							.build()
							.post();
				}
			}
		});
	}

	/**
	 * 将电子钥匙的时限参数按照规则填充成 8 字节的 T1
	 *
	 * @param keyType 电子钥匙类型
	 *                限时：1
	 *                永久：2
	 *                单次：3
	 *                循环：4周一循环，5周二循环，6周三循环，7周四循环，8周五循环，9周六循环，10周日循环，11每日循环，12工作日循环，13周末循环
	 */
	private String getEkeyTimeParam(int keyType, long startDate, long endDate, int cyclicStartHour, int cyclicEndHour, long createDate) {
		String timeParam;
		switch (keyType) {
			case 1: // 限时钥匙
				// 开始时间
				Calendar startCalendar = Calendar.getInstance();
				startCalendar.setTimeInMillis(startDate);
				int startYear = startCalendar.get(Calendar.YEAR) - 2000; // 开始年的后两位数字（以 2000 年为基准）
				int startMonth = startCalendar.get(Calendar.MONTH) + 1;
				int startDay = startCalendar.get(Calendar.DAY_OF_MONTH);
				int startHour = startCalendar.get(Calendar.HOUR_OF_DAY);
				int startMinute = startCalendar.get(Calendar.MINUTE);

				// 结束时间
				Calendar endCalendar = Calendar.getInstance();
				endCalendar.setTimeInMillis(endDate);
//				int endYear = endCalendar.get(Calendar.YEAR) - 2000;
				int endMonth = endCalendar.get(Calendar.MONTH) + 1;
				int endDay = endCalendar.get(Calendar.DAY_OF_MONTH);
				int endHour = endCalendar.get(Calendar.HOUR_OF_DAY);
				int endMinute = endCalendar.get(Calendar.MINUTE);

				String startYearHex = HexUtil.int2HexStr(startYear, 1);
//				String endYearHex = HexUtil.int2HexStr(endYear, 1);
				String startMonthHex = String.format("%01X", startMonth);
				String endMonthHex = String.format("%01X", endMonth);
				String startDayHex = HexUtil.int2HexStr(startDay, 1);
				String endDayHex = HexUtil.int2HexStr(endDay, 1);
				String startHourHex = HexUtil.int2HexStr(startHour, 1);
				String endHourHex = HexUtil.int2HexStr(endHour, 1);
				String startMinuteHex = HexUtil.int2HexStr(startMinute, 1);
				String endMinuteHex = HexUtil.int2HexStr(endMinute, 1);

				timeParam = startYearHex + startMonthHex + endMonthHex + startDayHex + endDayHex
						+ startHourHex + endHourHex + startMinuteHex + endMinuteHex;
				break;

			case 2: // 永久钥匙
				timeParam = "0000000000000000";
				break;

			case 3: // 单次钥匙
				// 生成钥匙的时间
				Calendar createCalendar = Calendar.getInstance();
				createCalendar.setTimeInMillis(createDate);
				int createYear = createCalendar.get(Calendar.YEAR) - 2000; // 开始年的后两位数字（以 2000 年为基准）
				int createMonth = createCalendar.get(Calendar.MONTH) + 1;
				int createDay = createCalendar.get(Calendar.DAY_OF_MONTH);
				int createHour = createCalendar.get(Calendar.HOUR_OF_DAY);
				int createMinute = createCalendar.get(Calendar.MINUTE);

				String createYearHex = HexUtil.int2HexStr(createYear, 1);
				String createMonthHex = HexUtil.int2HexStr(createMonth, 1);
				String createDayHex = HexUtil.int2HexStr(createDay, 1);
				String createHourHex = HexUtil.int2HexStr(createHour, 1);
				String createMinuteHex = HexUtil.int2HexStr(createMinute, 1);

				timeParam = "010000" + createYearHex + createMonthHex + createDayHex + createHourHex + createMinuteHex;
				break;

			default: // 循环钥匙
				// 循环类型标志
				int cyclicType = keyType - 3;

				// 开始日期
				Calendar cyclicStartCalendar = Calendar.getInstance();
				cyclicStartCalendar.setTimeInMillis(startDate);
				int cyclicStartYear = cyclicStartCalendar.get(Calendar.YEAR) - 2000; // 开始年的后两位数字（以 2000 年为基准）
				int cyclicStartMonth = cyclicStartCalendar.get(Calendar.MONTH) + 1;
				int cyclicStartDay = cyclicStartCalendar.get(Calendar.DAY_OF_MONTH);

				// 结束日期
				Calendar cyclicEndCalendar = Calendar.getInstance();
				cyclicEndCalendar.setTimeInMillis(endDate);
//				int cyclicEndYear = cyclicEndCalendar.get(Calendar.YEAR) - 2000;
				int cyclicEndMonth = cyclicEndCalendar.get(Calendar.MONTH) + 1;
				int cyclicEndDay = cyclicEndCalendar.get(Calendar.DAY_OF_MONTH);

				String cyclicTypeHex = String.format("F%01X", cyclicType);
				String cyclicStartYearHex = HexUtil.int2HexStr(cyclicStartYear, 1);
//				String cyclicEndYearHex = HexUtil.int2HexStr(cyclicEndYear, 1);
				String cyclicStartMonthHex = HexUtil.int2HexStr(cyclicStartMonth, 1);
				String cyclicEndMonthHex = HexUtil.int2HexStr(cyclicEndMonth, 1);
				String cyclicStartDayHex = HexUtil.int2HexStr(cyclicStartDay, 1);
				String cyclicEndDayHex = HexUtil.int2HexStr(cyclicEndDay, 1);
				String cyclicStartHourHex = HexUtil.int2HexStr(cyclicStartHour, 1);
				String cyclicEndHourHex = HexUtil.int2HexStr(cyclicEndHour, 1);

				timeParam = cyclicTypeHex + cyclicStartYearHex + cyclicStartHourHex + cyclicEndHourHex +
						cyclicStartMonthHex + cyclicEndMonthHex + cyclicStartDayHex + cyclicEndDayHex;
				break;
		}

		return timeParam;
	}

	/**
	 * 普通用户开锁验证
	 *
	 * @param ciphertext 16 字节密文 C1
	 */
	public void userUnlockVerify(String ciphertext) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandString = "040005" + "0010" + ciphertext;
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 040005 0010 + 16 字节密文 C1
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};
		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("普通用户开锁验证发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("普通用户开锁验证发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("普通用户开锁验证返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if ("040006000100".equalsIgnoreCase(response)) {
						sLockBleSession.getUserUnlockCallback().onSuccess();
					} else {
						sLockBleSession.getUserUnlockCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 普通用户闭锁
	 */
	public void userLock(String userId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = HexUtil.ascii2HexStr(userId); // 用户 id，长度 12

		String comandString = "040009" + "0006" + paramStr;
		int specialLength = comandString.length() / 2; // 11
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 000B
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 000B 0000 0001 040009 0006 + 6 字节 uid 对应的 ASCII 码
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("普通用户闭锁发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("普通用户闭锁发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("普通用户闭锁返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if (response.length() == 12) { // 04000A000100
						String flagStr = response.substring(10);
						if ("00".equalsIgnoreCase(flagStr)) {
							sLockBleSession.getUserLockCallback().onSuccess();
						} else {
							sLockBleSession.getUserLockCallback().onFail(LockError.FAIL);
						}
					} else {
						sLockBleSession.getUserLockCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * （管理员）设置锁时间
	 *
	 * @param time 时间毫秒值
	 */
    private java.util.TimeZone lockTimeZone() {
        Integer offset = sLockBleSession.getTimezoneRawOffset();
        return offset == null ? java.util.TimeZone.getDefault() : new java.util.SimpleTimeZone(offset, "Lock");
    }

    private String formatLockDate(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss", java.util.Locale.US);
        format.setTimeZone(lockTimeZone());
        return format.format(new Date(time));
    }

	public void setLockTime(long time) {
        Integer offset = sLockBleSession.getTimezoneRawOffset();
        setLockTime(time, offset == null ? java.util.TimeZone.getDefault().getOffset(time) : offset);
    }

    public void setLockTime(long time, int offsetMillis) {
        final int zone;
        try { zone = com.populock.manhattan.sdk.util.LockTimeZone.encode(offsetMillis); }
        catch (IllegalArgumentException e) {
            if (sLockBleSession.getSetLockTimeCallback() != null)
                sLockBleSession.getSetLockTimeCallback().onFail(LockError.FAIL);
            return;
        }
        sLockBleSession.setTimezoneRawOffset(offsetMillis);
		// 按照协议格式，解析时间数据
		SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss", java.util.Locale.US);
        format.setTimeZone(new java.util.SimpleTimeZone(offsetMillis, "Lock"));
        String dateStr = format.format(new Date(time));
		LogUtil.e("dateStr: " + dateStr);
		String[] dateArr = dateStr.split(":");
		int year = Integer.valueOf(dateArr[0]) - 2000;
		if (year < 0) year = 0;
		int month = Integer.valueOf(dateArr[1]);
		int day = Integer.valueOf(dateArr[2]);
		int hour = Integer.valueOf(dateArr[3]);
		int minute = Integer.valueOf(dateArr[4]);
		int second = Integer.valueOf(dateArr[5]);

		Calendar calendar = Calendar.getInstance(new java.util.SimpleTimeZone(offsetMillis, "Lock"));
        calendar.setTimeInMillis(time);
        int weekday = calendar.get(Calendar.DAY_OF_WEEK) - 1;


		String yearStr = HexUtil.int2HexStr(year, 1);
		String monthStr = HexUtil.int2HexStr(month, 1);
		String dayStr = HexUtil.int2HexStr(day, 1);
		String hourStr = HexUtil.int2HexStr(hour, 1);
		String minuteStr = HexUtil.int2HexStr(minute, 1);
		String secondStr = HexUtil.int2HexStr(second, 1);
		String weekdayStr = HexUtil.int2HexStr(weekday, 1);
		String zoneStr = HexUtil.int2HexStr(zone, 1);

		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = yearStr + monthStr + dayStr + hourStr + minuteStr + secondStr + weekdayStr + zoneStr;
		LogUtil.e("paramStr: " + paramStr);
		String comandString = "010001" + "0008" + paramStr;
		int specialLength = comandString.length() / 2; // 13
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 000D
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 000D 0000 0001 010001 0008 + 8 字节 时间数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("设置锁时间发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("设置锁时间发送失败");
                if (sLockBleSession.getSetLockTimeCallback() != null)
                    sLockBleSession.getSetLockTimeCallback().onFail(LockError.FAIL);
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("设置锁时间返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					SetLockTimeCallback callback = sLockBleSession.getSetLockTimeCallback();
					if (callback != null) {
						if ("010002000100".equalsIgnoreCase(response)) {
							callback.onSuccess();
						} else {
							callback.onFail(LockError.FAIL);
						}
					}
				}
			}
		});
	}

	/**
	 * 设置自动闭锁
	 *
	 * @param time 时间（秒）
	 */
	public void setAutoLocking(int time) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String paramStr = HexUtil.int2HexStr(time, 1);
		String comandString = "010003" + "0001" + paramStr;
		int specialLength = comandString.length() / 2; // 6
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0006
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0006 0000 0001 010003 0001 + 1 字节 时间数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("设置自动闭锁发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("设置自动闭锁发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("设置自动闭锁返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();

					if ("010004000100".equalsIgnoreCase(response)) {
						sLockBleSession.getSetAutoLockTimeCallback().onSuccess();
					} else if ("010004000101".equalsIgnoreCase(response)) {
						sLockBleSession.getSetAutoLockTimeCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 修改管理员键盘密码
	 */
	public void setAdminKeyboardPwd(String lockId, final String originalPwd, String newPwd) {
		// 对密码数据进行处理
		// 新密码
		String newPwdHex = HexUtil.intStr2HexStr(newPwd);
		StringBuilder newSb = new StringBuilder();
		// 不足 16 字节，后边补 00
		for (int i = 0; i < 14 - newPwd.length(); i++) {
			newSb.append("00");
		}
		String d1 = String.format("010%s%s%s", newPwd.length(), newPwdHex, newSb.toString());

		// 旧密码
		String originalPwdHex = HexUtil.intStr2HexStr(originalPwd);
		StringBuilder originalSb = new StringBuilder();
		// 不足 16 字节，后边补 00
		for (int i = 0; i < 15 - originalPwd.length(); i++) {
			originalSb.append("00");
		}
		String d2 = String.format("0%s%s%s", originalPwd.length(), originalPwdHex, originalSb.toString());

		// 用 K1 对密码数据进行加密
		RestClient.builder()
				.url(LockUrls.GET_CIPHERTEXT_MODIFY_PWD)
				.params("lockId", lockId)
				.params("d1", d1)
				.params("d2", d2)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("GET_CIPHERTEXT_MODIFY_PWD: " + response);

						try {
							JSONObject result = new JSONObject(response);
							int code = result.getInt("code");
							if (code == 200) {
								// 加密后的密码数据
								String paramStr = result.getString("data");

								int commandID = ConfigUtil.getCommandId(BleService.this);
								commandID++;
								ConfigUtil.setCommandId(BleService.this, commandID);

								String comandString = "010005" + "0020" + paramStr;
								int specialLength = comandString.length() / 2; // 37
								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
								String sequenceID = HexUtil.int2HexStr(commandID, 2);
								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

								final int[] receiveCount = new int[]{0};

								sendCommand(messageBytes, new BleWriteCallback() {
									@Override
									public void onWriteSuccess() {
										LogUtil.e("修改管理员键盘密码发送成功");
									}

									@Override
									public void onFail() {
										LogUtil.e("修改管理员键盘密码发送失败");
									}

									@Override
									public void onResponseSuccess(String response) {
										LogUtil.e("修改管理员键盘密码返回: " + response);
										saveAck(response);
										receiveCount[0]++;

										// 解析蓝牙返回数据
										if (receiveCount[0] == 3) {
											replyAck();

											if ("010006000100".equalsIgnoreCase(response)) {
												sLockBleSession.getSetAdminKeyboardPwdCallback().onSuccess();
											} else {
												sLockBleSession.getSetAdminKeyboardPwdCallback().onFail(LockError.FAIL);
											}
										}
									}
								});
							}
						} catch (JSONException e) {
							e.printStackTrace();
							sLockBleSession.getSetAdminKeyboardPwdCallback().onFail(LockError.FAIL);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getSetAdminKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getSetAdminKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}

	/**
	 * 修改普通键盘密码
	 *
	 * @param pwdType 修改后的密码类型（1 管理员密码；2 永久密码；3 限时密码）
	 */
	public void modifyKeyboardPwd(String lockId, final String originalPwd, String newPwd, int pwdType, long startTimeMillis, long endTimeMillis) {

		String originParam;

		if (startTimeMillis > 0) {
			// 按照协议格式，解析时间数据
			String startDateStr = formatLockDate(startTimeMillis);
			LogUtil.e("dateStr: " + startDateStr);
			String[] startDateArr = startDateStr.split(":");
			int startYear = Integer.valueOf(startDateArr[0]) - 2018;
			if (startYear < 0) startYear = 0;
			int startMonth = Integer.valueOf(startDateArr[1]);
			int startDay = Integer.valueOf(startDateArr[2]);
			int startHour = Integer.valueOf(startDateArr[3]);

			String startYearStr = HexUtil.intLetterHexStr(startYear, 1);
			String startMonthStr = HexUtil.intLetterHexStr(startMonth, 1);
			String startDayStr = HexUtil.int2HexStr(startDay, 1);
			String startHourStr = HexUtil.int2HexStr(startHour, 1);

			// 按照协议格式，解析时间数据
			String endDateStr = formatLockDate(endTimeMillis);
			LogUtil.e("dateStr: " + startDateStr);
			String[] endDateArr = endDateStr.split(":");
			int endYear = Integer.valueOf(endDateArr[0]) - 2018;
			if (endYear < 0) endYear = 0;
			int endMonth = Integer.valueOf(endDateArr[1]);
			int endDay = Integer.valueOf(endDateArr[2]);
			int endHour = Integer.valueOf(endDateArr[3]);

			String endYearStr = HexUtil.intLetterHexStr(endYear, 1);
			String endMonthStr = HexUtil.intLetterHexStr(endMonth, 1);
			String endDayStr = HexUtil.int2HexStr(endDay, 1);
			String endHourStr = HexUtil.int2HexStr(endHour, 1);

//			String timeStr = startYearStr + startMonthStr + startDayStr + startHourStr;
//			LogUtil.e("timeStr: " + timeStr);

			//新密码每个前面加一个0
			String  newPwdHex = HexUtil.intStr2HexStr(newPwd);
//			StringBuilder pwdHex = new StringBuilder();
			int pwdLength = newPwd.length();
//			for (int i = 0; i < pwdLength; i++) {
//				String hex = originalPwd.substring(i,i+1);
//				String zeroHex = String.format("0%s",hex);
//				pwdHex.append(zeroHex);
//			}

			//对密码长度不足8位的补0
			StringBuilder pwdParam = new StringBuilder();
			// 不足 16 字节，后边补 00
			for (int i = 0; i < 8 - pwdLength; i++) {
				pwdParam.append("00");
			}

			String timeStr = endYearStr + startYearStr + endMonthStr + startMonthStr
					+ startDayStr + endDayStr  + startHourStr + endHourStr;

			String pwdHexStr = newPwdHex;

			String pwdParamStr =  pwdParam.toString();

			if (pwdParamStr.length()>0){

				originParam = String.format("0%s0%s%s%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr,pwdParamStr,timeStr);

			}else {

				originParam = String.format("0%s0%s%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr,timeStr);

			}

		}else {

			//新密码每个前面加一个0
			String  newPwdHex = HexUtil.intStr2HexStr(newPwd);
			int pwdLength = newPwd.length();
			//对密码长度不足8位的补0
			StringBuilder pwdParam = new StringBuilder();
			int count = 16 - pwdLength - 2;
			// 不足 16 字节，后边补 00
			for (int i = 0; i < count; i++) {
				pwdParam.append("00");
			}

			String pwdHexStr = newPwdHex;

			String pwdParamStr =  pwdParam.toString();

			if (pwdParamStr.length()>0){

				originParam = String.format("0%s0%s%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr,pwdParamStr);

			}else {

				originParam = String.format("0%s0%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr);

			}


		}

		String d1 = originParam;

//		// 对密码数据进行处理
//		// 新密码
//		String newPwdHex = HexUtil.intStr2HexStr(newPwd);
//		StringBuilder newSb = new StringBuilder();
//		// 不足 16 字节，后边补 00
//		for (int i = 0; i < 14 - newPwd.length(); i++) {
//			newSb.append("00");
//		}
//		String d1 = String.format("010%s%s%s", newPwd.length(), newPwdHex, newSb.toString());

		// 旧密码
		String originalPwdHex = HexUtil.intStr2HexStr(originalPwd);
		StringBuilder originalSb = new StringBuilder();
		// 不足 16 字节，后边补 00
		for (int i = 0; i < 15 - originalPwd.length(); i++) {
			originalSb.append("00");
		}
		String d2 = String.format("0%s%s%s", originalPwd.length(), originalPwdHex, originalSb.toString());

//		if (pwdType == 1) {
//
//		} else if (pwdType == 2) {
//
//		} else if (pwdType == 3) {
//
//		}

		// 用 K1 对密码数据进行加密
		RestClient.builder()
				.url(LockUrls.GET_CIPHERTEXT_MODIFY_PWD)
				.params("lockId", lockId)
				.params("d1", d1)
				.params("d2", d2)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("GET_CIPHERTEXT_MODIFY_PWD: " + response);

						try {
							JSONObject result = new JSONObject(response);
							int code = result.getInt("code");
							if (code == 200) {
								// 加密后的密码数据
								String paramStr = result.getString("data");
								int commandID = ConfigUtil.getCommandId(BleService.this);
								commandID++;
								ConfigUtil.setCommandId(BleService.this, commandID);
								String comandString = "010005" + "0020" + paramStr;
								int specialLength = comandString.length() / 2; // 37
								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
								String sequenceID = HexUtil.int2HexStr(commandID, 2);
								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

								final int[] receiveCount = new int[]{0};

								sendCommand(messageBytes, new BleWriteCallback() {
									@Override
									public void onWriteSuccess() {
										LogUtil.e("修改键盘密码发送成功");
									}

									@Override
									public void onFail() {
										LogUtil.e("修改键盘密码发送失败");
									}

									@Override
									public void onResponseSuccess(String response) {
										LogUtil.e("修改键盘密码返回: " + response);
										saveAck(response);
										receiveCount[0]++;

										// 解析蓝牙返回数据
										if (receiveCount[0] == 3) {
											replyAck();

											if ("010006000100".equalsIgnoreCase(response)) {
												sLockBleSession.getmModifyUserKeyboardPwdCallback().onSuccess();
											} else {
												sLockBleSession.getmModifyUserKeyboardPwdCallback().onFail(LockError.FAIL);
											}
										}
									}
								});
							}
						} catch (JSONException e) {
							e.printStackTrace();
							sLockBleSession.getmModifyUserKeyboardPwdCallback().onFail(LockError.FAIL);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getmModifyUserKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getmModifyUserKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}

	/**
	 * 修改普通键盘密码
	 *
	 * @param pwdType 修改后的密码类型（1 管理员密码；2 永久密码；3 限时密码）
	 */
	public void modifyKeyboardPwdValid(String lockId, final String originalPwd, String newPwd, int pwdType, long startTimeMillis, long endTimeMillis) {

		String originParam = "";//旧密码和有效期组成d1

		String validTimeStr = "";//新有效期time组成d2

		if (startTimeMillis > 0) {
			// 按照协议格式，解析时间数据
			String startDateStr = formatLockDate(startTimeMillis);
			LogUtil.e("dateStr: " + startDateStr);
			String[] startDateArr = startDateStr.split(":");
			int startYear = Integer.valueOf(startDateArr[0]) - 2018;
			if (startYear < 0) startYear = 0;
			int startMonth = Integer.valueOf(startDateArr[1]);
			int startDay = Integer.valueOf(startDateArr[2]);
			int startHour = Integer.valueOf(startDateArr[3]);

			String startYearStr = HexUtil.intLetterHexStr(startYear, 1);
			String startMonthStr = HexUtil.intLetterHexStr(startMonth, 1);
			String startDayStr = HexUtil.int2HexStr(startDay, 1);
			String startHourStr = HexUtil.int2HexStr(startHour, 1);

			// 按照协议格式，解析时间数据
			String endDateStr = formatLockDate(endTimeMillis);
			LogUtil.e("dateStr: " + endDateStr);
			String[] endDateArr = endDateStr.split(":");
			int endYear = Integer.valueOf(endDateArr[0]) - 2018;
			if (endYear < 0) endYear = 0;
			int endMonth = Integer.valueOf(endDateArr[1]);
			int endDay = Integer.valueOf(endDateArr[2]);
			int endHour = Integer.valueOf(endDateArr[3]);

			String endYearStr = HexUtil.intLetterHexStr(endYear, 1);
			String endMonthStr = HexUtil.intLetterHexStr(endMonth, 1);
			String endDayStr = HexUtil.int2HexStr(endDay, 1);
			String endHourStr = HexUtil.int2HexStr(endHour, 1);

//			String timeStr = startYearStr + startMonthStr + startDayStr + startHourStr;
//			LogUtil.e("timeStr: " + timeStr);

			//新密码每个前面加一个0
			String  pwdHex = HexUtil.intStr2HexStr(originalPwd);
//			StringBuilder pwdHex = new StringBuilder();
			int pwdLength = originalPwd.length();
//			for (int i = 0; i < pwdLength; i++) {
//				String hex = originalPwd.substring(i,i+1);
//				String zeroHex = String.format("0%s",hex);
//				pwdHex.append(zeroHex);
//			}

			//对密码长度不足9位的补0
			StringBuilder pwdParam = new StringBuilder();
			// 不足 16 字节，后边补 00
			for (int i = 0; i < 8 - pwdLength; i++) {
				pwdParam.append("00");
			}

			String timeStr = endYearStr + startYearStr + endMonthStr + startMonthStr
					+ startDayStr + endDayStr  + startHourStr + endHourStr;

			validTimeStr = "00000000000000000000" + timeStr;

			String pwdHexStr = pwdHex;

			String pwdParamStr =  pwdParam.toString();

			if (pwdParamStr.length()>0){

				originParam = String.format("0%s%s%s%s",String.valueOf(pwdLength),pwdHexStr,pwdParamStr,timeStr);

			}else {

				originParam = String.format("0%s%s%s",String.valueOf(pwdLength),pwdHexStr,timeStr);

			}

		}else {

			//密码每个前面加一个0
			String  newPwdHex = HexUtil.intStr2HexStr(originalPwd);
			int pwdLength = originalPwd.length();
			//对密码长度不足8位的补0
			StringBuilder pwdParam = new StringBuilder();
			int count = 9 - pwdLength;
			// 不足 16 字节，后边补 00
			for (int i = 0; i < count; i++) {
				pwdParam.append("00");
			}

			String pwdHexStr = newPwdHex;

			String pwdParamStr =  pwdParam.toString();

			if (pwdParamStr.length()>0){

				originParam = String.format("0%s0%s%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr,pwdParamStr);

			}else {

				originParam = String.format("0%s0%s%s",String.valueOf(pwdType),String.valueOf(pwdLength),pwdHexStr);

			}


		}

		String d1 = originParam;

//		// 对密码数据进行处理
//		// 新密码
//		String newPwdHex = HexUtil.intStr2HexStr(newPwd);
//		StringBuilder newSb = new StringBuilder();
//		// 不足 16 字节，后边补 00
//		for (int i = 0; i < 14 - newPwd.length(); i++) {
//			newSb.append("00");
//		}
//		String d1 = String.format("010%s%s%s", newPwd.length(), newPwdHex, newSb.toString());

		// 旧密码
//		String originalPwdHex = HexUtil.intStr2HexStr(originalPwd);
//		StringBuilder originalSb = new StringBuilder();
//		// 不足 16 字节，后边补 00
//		for (int i = 0; i < 15 - originalPwd.length(); i++) {
//			originalSb.append("00");
//		}
//		String d2 = String.format("0%s%s%s", originalPwd.length(), originalPwdHex, originalSb.toString());

		String d2 = validTimeStr;

		// 用 K1 对密码数据进行加密
		RestClient.builder()
				.url(LockUrls.GET_CIPHERTEXT_MODIFY_PWD)
				.params("lockId", lockId)
				.params("d1", d1)
				.params("d2", d2)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("GET_CIPHERTEXT_MODIFY_PWD: " + response);

						try {
							JSONObject result = new JSONObject(response);
							int code = result.getInt("code");
							if (code == 200) {
								// 加密后的密码数据
								String paramStr = result.getString("data");

								int commandID = ConfigUtil.getCommandId(BleService.this);
								commandID++;
								ConfigUtil.setCommandId(BleService.this, commandID);

								String comandString = "010005" + "0020" + paramStr;
								int specialLength = comandString.length() / 2; // 37
								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
								String sequenceID = HexUtil.int2HexStr(commandID, 2);
								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

								final int[] receiveCount = new int[]{0};

								sendCommand(messageBytes, new BleWriteCallback() {
									@Override
									public void onWriteSuccess() {
										LogUtil.e("修改键盘密码有效期发送成功");
									}

									@Override
									public void onFail() {
										LogUtil.e("修改键盘密码有效期发送失败");
									}

									@Override
									public void onResponseSuccess(String response) {
										LogUtil.e("修改键盘密码有效期返回: " + response);
										saveAck(response);
										receiveCount[0]++;

										// 解析蓝牙返回数据
										if (receiveCount[0] == 3) {
											replyAck();
											if ("010006000100".equalsIgnoreCase(response)) {
												sLockBleSession.getmModifyUserKeyboardPwdPeriodCallback().onSuccess();
											} else {
												sLockBleSession.getmModifyUserKeyboardPwdPeriodCallback().onFail(LockError.FAIL);
											}
										}
									}
								});
							}
						} catch (JSONException e) {
							e.printStackTrace();
							sLockBleSession.getmModifyUserKeyboardPwdPeriodCallback().onFail(LockError.FAIL);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getmModifyUserKeyboardPwdPeriodCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getmModifyUserKeyboardPwdPeriodCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}

	/**
	 * 修改指纹有效期
	 *
	 * @param fingerType 修改指纹有效期（0 永久指纹；1 限时指纹；）
	 */
	public void modifyFingerValid(String lockId, final String fingerId, String fingerType, long startTimeMillis, long endTimeMillis) {

		String originParam = "";//旧密码和有效期组成d1

		if (startTimeMillis > 0) {
			// 按照协议格式，解析时间数据
			String startDateStr = formatLockDate(startTimeMillis);
			LogUtil.e("dateStr: " + startDateStr);
			String[] startDateArr = startDateStr.split(":");
			int startYear = Integer.valueOf(startDateArr[0]) - 2021;
			if (startYear < 0) startYear = 0;
			int startMonth = Integer.valueOf(startDateArr[1]);
			int startDay = Integer.valueOf(startDateArr[2]);
			int startHour = Integer.valueOf(startDateArr[3]);
			int startMintune = Integer.valueOf(startDateArr[4]);

			String startYearStr = HexUtil.intLetterHexStr(startYear, 1);
			String startMonthStr = HexUtil.intLetterHexStr(startMonth, 1);
			String startDayStr = HexUtil.int2HexStr(startDay, 1);
			String startHourStr = HexUtil.int2HexStr(startHour, 1);
			String startMintuneStr = HexUtil.int2HexStr(startMintune, 1);

			// 按照协议格式，解析时间数据
			String endDateStr = formatLockDate(endTimeMillis);
			LogUtil.e("dateStr: " + endDateStr);
			String[] endDateArr = endDateStr.split(":");
			int endYear = Integer.valueOf(endDateArr[0]) - 2021;
			if (endYear < 0) endYear = 0;
			int endMonth = Integer.valueOf(endDateArr[1]);
			int endDay = Integer.valueOf(endDateArr[2]);
			int endHour = Integer.valueOf(endDateArr[3]);
			int endMintune = Integer.valueOf(endDateArr[4]);

			String endYearStr = HexUtil.intLetterHexStr(endYear, 1);
			String endMonthStr = HexUtil.intLetterHexStr(endMonth, 1);
			String endDayStr = HexUtil.int2HexStr(endDay, 1);
			String endHourStr = HexUtil.int2HexStr(endHour, 1);
			String endMintuneStr = HexUtil.int2HexStr(endMintune, 1);


			String timeStr = endYearStr + startYearStr + endMonthStr + startMonthStr
					+ startDayStr + endDayStr  + startHourStr + endHourStr
					+ startMintuneStr + endMintuneStr + "0000000000";

			String fingerHexStr = fingerId;

			originParam = String.format("%s%s%s",fingerType,fingerHexStr,timeStr);


		}

		String d1 = originParam;

		int commandID = ConfigUtil.getCommandId(BleService.this);

		commandID++;

		ConfigUtil.setCommandId(BleService.this, commandID);

		String comandString = "010027" + "0010" + d1;//paramStr;
		int specialLength = comandString.length() / 2; // 37
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
					@Override
					public void onWriteSuccess() {
						LogUtil.e("修改指纹有效期发送成功");
					}

					@Override
					public void onFail() {
						LogUtil.e("修改指纹有效期发送失败");
					}

					@Override
					public void onResponseSuccess(String response) {
						LogUtil.e("修改指纹有效期返回: " + response);
						saveAck(response);
						receiveCount[0]++;
						// 解析蓝牙返回数据
						if (receiveCount[0] == 3) {
							replyAck();
							if ("010028000100".equalsIgnoreCase(response)) {
								sLockBleSession.getmModifyFingerprintPeriodCallback().onSuccess();
							} else {
								sLockBleSession.getmModifyFingerprintPeriodCallback().onFail(LockError.FAIL);
							}
						}
					}

		});

//		// 用 K1 对密码数据进行加密
//		RestClient.builder()
//				.url(LockUrls.GET_CIPHERTEXT_ADDPWD_DELPWD)
//				.params("lockId", lockId)
//				.params("text", d1)
//				.success(new ISuccess() {
//					@Override
//					public void onSuccess(String response) {
//						LogUtil.d("DELETE_KEYBOADR_PWD_ENCRYPT: " + response);
//
//						try {
//							JSONObject result = new JSONObject(response);
//							int code = result.getInt("code");
//							if (code == 200) {
//								// 加密后的数据
//								String paramStr = result.getString("data");
//
//								int commandID = ConfigUtil.getCommandId(BleService.this);
//								commandID++;
//								ConfigUtil.setCommandId(BleService.this, commandID);
//
//								String comandString = "010027" + "0010" + d1;//paramStr;
//								int specialLength = comandString.length() / 2; // 37
//								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
//								String sequenceID = HexUtil.int2HexStr(commandID, 2);
//								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
//								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
//
//								final int[] receiveCount = new int[]{0};
//
//								sendCommand(messageBytes, new BleWriteCallback() {
//									@Override
//									public void onWriteSuccess() {
//										LogUtil.e("修改指纹有效期发送成功");
//									}
//
//									@Override
//									public void onFail() {
//										LogUtil.e("修改指纹有效期发送失败");
//									}
//
//									@Override
//									public void onResponseSuccess(String response) {
//										LogUtil.e("修改指纹有效期返回: " + response);
//										saveAck(response);
//										receiveCount[0]++;
//
//										// 解析蓝牙返回数据
//										if (receiveCount[0] == 3) {
//											replyAck();
//											if ("010028000100".equalsIgnoreCase(response)) {
//												sLockBleSession.getmModifyFingerprintPeriodCallback().onSuccess();
//											} else {
//												sLockBleSession.getmModifyFingerprintPeriodCallback().onFail(LockError.FAIL);
//											}
//										}
//									}
//								});
//							}
//						} catch (JSONException e) {
//							e.printStackTrace();
//							sLockBleSession.getmModifyFingerprintPeriodCallback().onFail(LockError.FAIL);
//						}
//					}
//				})
//				.failure(new IFailure() {
//					@Override
//					public void onFailure() {
//						sLockBleSession.getmModifyFingerprintPeriodCallback().onFail(LockError.FAIL);
//					}
//				})
//				.error(new IError() {
//					@Override
//					public void onError(int code, String msg) {
//						sLockBleSession.getmModifyFingerprintPeriodCallback().onFail(LockError.FAIL);
//					}
//				})
//				.build()
//				.post();
	}

	/**
	 * 修改卡片有效期
	 *
	 * @param cardType 修改卡片有效期（0 永久卡片；1 限时卡片；）
	 */
	public void modifyCardValid(String lockId, final String cardId, String cardType, long startTimeMillis, long endTimeMillis) {

		String originParam = "";//旧密码和有效期组成d1

		if (startTimeMillis > 0) {
			// 按照协议格式，解析时间数据
			String startDateStr = formatLockDate(startTimeMillis);
			LogUtil.e("dateStr: " + startDateStr);
			String[] startDateArr = startDateStr.split(":");
			int startYear = Integer.valueOf(startDateArr[0]) - 2021;
			if (startYear < 0) startYear = 0;
			int startMonth = Integer.valueOf(startDateArr[1]);
			int startDay = Integer.valueOf(startDateArr[2]);
			int startHour = Integer.valueOf(startDateArr[3]);
			int startMintune = Integer.valueOf(startDateArr[4]);

			String startYearStr = HexUtil.intLetterHexStr(startYear, 1);
			String startMonthStr = HexUtil.intLetterHexStr(startMonth, 1);
			String startDayStr = HexUtil.int2HexStr(startDay, 1);
			String startHourStr = HexUtil.int2HexStr(startHour, 1);
			String startMintuneStr = HexUtil.int2HexStr(startMintune, 1);

			// 按照协议格式，解析时间数据
			String endDateStr = formatLockDate(endTimeMillis);
			LogUtil.e("dateStr: " + endDateStr);
			String[] endDateArr = endDateStr.split(":");
			int endYear = Integer.valueOf(endDateArr[0]) - 2021;
			if (endYear < 0) endYear = 0;
			int endMonth = Integer.valueOf(endDateArr[1]);
			int endDay = Integer.valueOf(endDateArr[2]);
			int endHour = Integer.valueOf(endDateArr[3]);
			int endMintune = Integer.valueOf(endDateArr[4]);

			String endYearStr = HexUtil.intLetterHexStr(endYear, 1);
			String endMonthStr = HexUtil.intLetterHexStr(endMonth, 1);
			String endDayStr = HexUtil.int2HexStr(endDay, 1);
			String endHourStr = HexUtil.int2HexStr(endHour, 1);
			String endMintuneStr = HexUtil.int2HexStr(endMintune, 1);


			String timeStr = endYearStr + startYearStr + endMonthStr + startMonthStr
					+ startDayStr + endDayStr  + startHourStr + endHourStr
					+ startMintuneStr + endMintuneStr + "00000000000000000000000000";

			String  cardHexStr = cardId;

			originParam = String.format("%s%s%s",cardType,cardHexStr,timeStr);


		}

		String d1 = originParam;

		int commandID = ConfigUtil.getCommandId(BleService.this);
		commandID++;
		ConfigUtil.setCommandId(BleService.this, commandID);

		String comandString = "010029" + "0020" + d1;
		int specialLength = comandString.length() / 2; // 37
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("修改卡片有效期发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("修改卡片有效期发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("修改卡片有效期返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("010030000100".equalsIgnoreCase(response)) {
						sLockBleSession.getmModifyCardPeriodCallback().onSuccess();
					} else {
						sLockBleSession.getmModifyCardPeriodCallback().onFail(LockError.FAIL);
					}
				}
			}
		});

//		// 用 K1 对密码数据进行加密
//		RestClient.builder()
//				.url(LockUrls.GET_CIPHERTEXT_ADDPWD_DELPWD)
//				.params("lockId", lockId)
//				.params("text", d1)
//				.success(new ISuccess() {
//					@Override
//					public void onSuccess(String response) {
//						LogUtil.d("DELETE_KEYBOADR_PWD_ENCRYPT: " + response);
//
//						try {
//							JSONObject result = new JSONObject(response);
//							int code = result.getInt("code");
//							if (code == 200) {
//								// 加密后的数据
//								String paramStr = result.getString("data");
//
//								int commandID = ConfigUtil.getCommandId(BleService.this);
//								commandID++;
//								ConfigUtil.setCommandId(BleService.this, commandID);
//
//								String comandString = "010029" + "0020" + paramStr;
//								int specialLength = comandString.length() / 2; // 37
//								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0025
//								String sequenceID = HexUtil.int2HexStr(commandID, 2);
//								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0025 0000 0001 010005 0020 + 32 字节 密码数据
//								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());
//
//								final int[] receiveCount = new int[]{0};
//
//								sendCommand(messageBytes, new BleWriteCallback() {
//									@Override
//									public void onWriteSuccess() {
//										LogUtil.e("修改卡片有效期发送成功");
//									}
//
//									@Override
//									public void onFail() {
//										LogUtil.e("修改卡片有效期发送失败");
//									}
//
//									@Override
//									public void onResponseSuccess(String response) {
//										LogUtil.e("修改卡片有效期返回: " + response);
//										saveAck(response);
//										receiveCount[0]++;
//
//										// 解析蓝牙返回数据
//										if (receiveCount[0] == 3) {
//											replyAck();
//											if ("010030000100".equalsIgnoreCase(response)) {
//												sLockBleSession.getmModifyCardPeriodCallback().onSuccess();
//											} else {
//												sLockBleSession.getmModifyCardPeriodCallback().onFail(LockError.FAIL);
//											}
//										}
//									}
//								});
//							}
//						} catch (JSONException e) {
//							e.printStackTrace();
//							sLockBleSession.getmModifyCardPeriodCallback().onFail(LockError.FAIL);
//						}
//					}
//				})
//				.failure(new IFailure() {
//					@Override
//					public void onFailure() {
//						sLockBleSession.getmModifyCardPeriodCallback().onFail(LockError.FAIL);
//					}
//				})
//				.error(new IError() {
//					@Override
//					public void onError(int code, String msg) {
//						sLockBleSession.getmModifyCardPeriodCallback().onFail(LockError.FAIL);
//					}
//				})
//				.build()
//				.post();
	}

	/**
	 * 删除键盘密码
	 *
	 * @param pwdType 密码类型，1:单次密码，2: 永久密码 3: 循环密码 4: 限时密码 5:管理员密码 6:清空密码
	 * @param pwd     密码
	 */
	public void deleteKeyboardPwd(String lockId, int pwdType, final String pwd) {
		// 对密码数据进行处理
		String pwdHex = HexUtil.intStr2HexStr(pwd);
		StringBuilder sb = new StringBuilder();
		// 不足 16 字节，后边补 01
		for (int i = 0; i < 14 - pwd.length(); i++) {
			sb.append("01");
		}
		String pwdParam = String.format("0%s0%s%s%s", pwdType, pwd.length(), pwdHex, sb.toString());

		// 用 K1 对密码数据进行加密
		RestClient.builder()
				.url(LockUrls.GET_CIPHERTEXT_ADDPWD_DELPWD)
				.params("lockId", lockId)
				.params("text", pwdParam)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("DELETE_KEYBOADR_PWD_ENCRYPT: " + response);

						try {
							JSONObject result = new JSONObject(response);
							int code = result.getInt("code");
							if (code == 200) {
								// 加密后的密码数据
								String paramStr = result.getString("data");

								int commandID = ConfigUtil.getCommandId(BleService.this);
								commandID++;
								ConfigUtil.setCommandId(BleService.this, commandID);

								String comandString = "010007" + "0010" + paramStr;
								int specialLength = comandString.length() / 2; // 21
								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
								String sequenceID = HexUtil.int2HexStr(commandID, 2);
								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010007 0010 + 16 字节 密码数据
								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

								final int[] receiveCount = new int[]{0};

								sendCommand(messageBytes, new BleWriteCallback() {
									@Override
									public void onWriteSuccess() {
										LogUtil.e(
												"删除键盘密码发送成功");
									}

									@Override
									public void onFail() {
										LogUtil.e("删除键盘密码发送失败");
									}

									@Override
									public void onResponseSuccess(String response) {
										LogUtil.e("删除键盘密码返回: " + response);
										saveAck(response);
										receiveCount[0]++;
										// 解析蓝牙返回数据
										if (receiveCount[0] == 3) {
											replyAck();
											if ("010008000100".equalsIgnoreCase(response)) {
												sLockBleSession.getDeleteKeyboardPwdCallback().onSuccess();
											} else if ("010008000101".equalsIgnoreCase(response)) {
												sLockBleSession.getDeleteKeyboardPwdCallback().onFail(LockError.FAIL);
											}
										}
									}
								});
							}
						} catch (JSONException e) {
							e.printStackTrace();
							sLockBleSession.getDeleteKeyboardPwdCallback().onFail(LockError.FAIL);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getDeleteKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getDeleteKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}

	/**
	 * 重置电子钥匙
	 */
	public void resetEKey(final String userId, final String lockId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandString = "010009";
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0006 0000 0001 010009
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("重置电子钥匙发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("重置电子钥匙发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("重置电子钥匙返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						stringBuilder.append(response.substring(10));
					} else {
						replyAck();
						stringBuilder.append(response);

						// 16 字节密文
						String ciphertext = stringBuilder.toString();
						LogUtil.d("K2 ciphertext: " + ciphertext);

						RestClient.builder()
								.url(LockUrls.RESET_EKEY_UPLOAD_K2)
								.params("userId", userId)
								.params("lockId", lockId)
								.params("ciphertext", ciphertext)
								.success(new ISuccess() {
									@Override
									public void onSuccess(String response) {
										LogUtil.d("RESET_EKEY_UPLOAD_K2: " + response);

										try {
											JSONObject result = new JSONObject(response);
											int code = result.getInt("code");
											if (code == 200) {
												sLockBleSession.getResetEkeyCallback().onSuccess();
											} else {
												sLockBleSession.getResetEkeyCallback().onFail(LockError.FAIL);
											}
										} catch (JSONException e) {
											e.printStackTrace();
											sLockBleSession.getResetEkeyCallback().onFail(LockError.FAIL);
										}
									}
								})
								.failure(new IFailure() {
									@Override
									public void onFailure() {
										sLockBleSession.getResetEkeyCallback().onFail(LockError.FAIL);
									}
								})
								.error(new IError() {
									@Override
									public void onError(int code, String msg) {
										sLockBleSession.getResetEkeyCallback().onFail(LockError.FAIL);
									}
								})
								.build()
								.post();
					}
				}
			}
		});
	}

	/**
	 * 重置键盘密码
	 */
	public void resetKeyboardPwd(final String userId, final String lockId) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		String comandString = "01000B";
		int specialLength = comandString.length() / 2; // 3
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0003
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0006 0000 0001 01000B
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};
		final StringBuilder stringBuilder = new StringBuilder();

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("重置键盘密码发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("重置键盘密码发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("重置键盘密码返回: " + response);
				saveAck(response);
				receiveCount[0]++;

				// 解析蓝牙返回数据
				if (receiveCount[0] >= 3) {
					if (receiveCount[0] == 3) {
						stringBuilder.append(response.substring(10));
					} else {
						replyAck();
						stringBuilder.append(response);
						// 16 字节密文
						String ciphertext = stringBuilder.toString();
						LogUtil.d("lockKey ciphertext: " + ciphertext);
						RestClient.builder()
								.url(LockUrls.RESET_PWD_UPLOAD_LOCK_KEY)
								.params("userId", userId)
								.params("lockId", lockId)
								.params("text", ciphertext)
								.success(new ISuccess() {
									@Override
									public void onSuccess(String response) {
										LogUtil.d("RESET_PWD_UPLOAD_LOCK_KEY: " + response);
										try {
											JSONObject result = new JSONObject(response);
											int code = result.getInt("code");
											if (code == 200) {
												sLockBleSession.getResetKeyboardPwdCallback().onSuccess();
											} else {
												sLockBleSession.getResetKeyboardPwdCallback().onFail(LockError.FAIL);
											}
										} catch (JSONException e) {
											e.printStackTrace();
											sLockBleSession.getResetKeyboardPwdCallback().onFail(LockError.FAIL);
										}
									}
								})
								.failure(new IFailure() {
									@Override
									public void onFailure() {
										sLockBleSession.getResetKeyboardPwdCallback().onFail(LockError.FAIL);
									}
								})
								.error(new IError() {
									@Override
									public void onError(int code, String msg) {
										sLockBleSession.getResetKeyboardPwdCallback().onFail(LockError.FAIL);
									}
								})
								.build()
								.post();
					}
				}
			}
		});
	}

	/**
	 * 添加自定义键盘密码（限时密码）
	 */
	public void addKeyboardPwd(String lockId, String pwd, long startDate, long endDate) {

		LogUtil.d("mhs---addKeyboardPwd 原始参数: lockId = " + lockId + ",pwd = " + pwd + ",startDate="+ startDate + ",endDate="+endDate);
		// 对密码数据进行处理
		String pwdHex = HexUtil.intStr2HexStr(pwd);
		String pwdLength = HexUtil.int2HexStr(pwd.length(), 1);
		LogUtil.d("mhs---addKeyboardPwd 密码转16进制1: pwdHex = " + pwdHex + ",pwdLength = " + pwdLength);

		StringBuilder sb = new StringBuilder();
		// 不足 8 字节，后边补 00
		for (int i = 0; i < 8 - pwd.length(); i++) {
			sb.append("00");
		}

		String pwdStr = String.format("03%s%s%s", pwdLength, pwdHex, sb.toString());

		LogUtil.d("mhs---addKeyboardPwd 密码转16进制2: pwdStr = " + pwdStr);

		// 开始时间
		Calendar startCalendar = Calendar.getInstance(lockTimeZone());
		startCalendar.setTimeInMillis(startDate);
		int startYear = startCalendar.get(Calendar.YEAR) - 2018; // 年相对值（以 2018 年为基准）
		int startMonth = startCalendar.get(Calendar.MONTH) + 1;
		int startDay = startCalendar.get(Calendar.DAY_OF_MONTH);
		int startHour = startCalendar.get(Calendar.HOUR_OF_DAY);

		LogUtil.d("mhs---addKeyboardPwd 开始时间处理: startYear = " + startYear + ",startMonth = " + startMonth + ",startDay="+ startDay + ",startHour="+startHour);

		// 结束时间
		Calendar endCalendar = Calendar.getInstance(lockTimeZone());
		endCalendar.setTimeInMillis(endDate);
		int endYear = endCalendar.get(Calendar.YEAR) - 2018; // 年相对值（以 2018 年为基准）
		int endMonth = endCalendar.get(Calendar.MONTH) + 1;
		int endDay = endCalendar.get(Calendar.DAY_OF_MONTH);
		int endHour = endCalendar.get(Calendar.HOUR_OF_DAY);

		LogUtil.d("mhs---addKeyboardPwd 结束时间处理: endYear = " + endYear + ",endMonth = " + endMonth + ",endDay="+ endDay + ",endHour="+endHour);

//		// 对失效时间进行处理（往前调整 1h）
//		if (endHour == 0) {
//			endHour = 23;
//			if (endDay == 1) {
//				if (endMonth == 5 || endMonth == 7 || endMonth == 10 || endMonth == 12) { // 当月 31 天
//					endDay = 30;
//					endMonth = endMonth - 1;
//				} else if (endMonth == 3) {
//					if ((endYear % 4 == 0 && endYear % 100 != 0) || endYear % 400 == 0) {
//						endDay = 29;
//					} else {
//						endDay = 28;
//					}
//					endMonth = 2;
//				} else if (endMonth == 1) {
//					endYear = endYear - 1;
//					endMonth = 12;
//					endDay = 31;
//				} else {
//					endMonth = endMonth - 1;
//					endDay = 31;
//				}
//			} else {
//				endDay = endDay - 1;
//			}
//		} else {
//			endHour = endHour - 1;
//		}

		String startYearHex = String.format("%01X", startYear);
		String endYearHex = String.format("%01X", endYear);
		String startMonthHex = String.format("%01X", startMonth);
		String endMonthHex = String.format("%01X", endMonth);
		String startDayHex = HexUtil.int2HexStr(startDay, 1);
		String endDayHex = HexUtil.int2HexStr(endDay, 1);
		String startHourHex = HexUtil.int2HexStr(startHour, 1);
		String endHourHex = HexUtil.int2HexStr(endHour, 1);


		LogUtil.d("mhs---addKeyboardPwd 开始时间处理转16进制: startYearHex = " + startYearHex + ",startMonthHex = " + startMonthHex + ",startDayHex="+ startDayHex + ",startHourHex="+startHourHex);
		LogUtil.d("mhs---addKeyboardPwd 结束时间处理转16进制: endYearHex = " + endYear + ",endMonthHex = " + endMonthHex + ",endDayHex="+ endDayHex + ",endHourHex="+endHourHex);

		String timeStr = endYearHex + startYearHex + endMonthHex + startMonthHex + startDayHex + endDayHex + startHourHex + endHourHex;
		String pwdParam = pwdStr + timeStr;
		LogUtil.d("mhs---addKeyboardPwd 密码和时间拼接之后的参数text: pwdParam = " + pwdParam);

		// 用 K1 对密码数据进行加密，得到 16 字节密文 C1
		RestClient.builder()
				.url(LockUrls.GET_CIPHERTEXT_ADDPWD_DELPWD)
				.params("lockId", lockId)
				.params("text", pwdParam)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						LogUtil.d("ADD_KEYBOADR_PWD_ENCRYPT: " + response);
						try {
							JSONObject result = new JSONObject(response);
							int code = result.getInt("code");
							if (code == 200) {
								// 加密后的密码数据（16 字节密文 C1）
								String paramStr = result.getString("data");

								int commandID = ConfigUtil.getCommandId(BleService.this);
								commandID++;
								ConfigUtil.setCommandId(BleService.this, commandID);

								String comandString = "01000D" + "0010" + paramStr;
								int specialLength = comandString.length() / 2; // 21
								String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
								String sequenceID = HexUtil.int2HexStr(commandID, 2);
								String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 01000D 0010 + 16 字节 密码数据
								byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

								final int[] receiveCount = new int[]{0};

								sendCommand(messageBytes, new BleWriteCallback() {
									@Override
									public void onWriteSuccess() {
										LogUtil.e("添加自定义键盘密码发送成功");
									}

									@Override
									public void onFail() {
										LogUtil.e("添加自定义键盘密码发送失败");
									}

									@Override
									public void onResponseSuccess(String response) {
										LogUtil.e("添加自定义键盘密码返回: " + response);
										saveAck(response);
										receiveCount[0]++;

										// 解析蓝牙返回数据
										if (receiveCount[0] == 3) {
											replyAck();

											if ("01000e000100".equalsIgnoreCase(response)) {
												sLockBleSession.getAddKeyboardPwdCallback().onSuccess();
											} else if ("01000e000101".equalsIgnoreCase(response)) {
												sLockBleSession.getAddKeyboardPwdCallback().onFail(LockError.FAIL);
											}
										}
									}
								});
							} else {
								sLockBleSession.getAddKeyboardPwdCallback().onFail(LockError.FAIL);
							}
						} catch (JSONException e) {
							e.printStackTrace();
							sLockBleSession.getAddKeyboardPwdCallback().onFail(LockError.FAIL);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						sLockBleSession.getAddKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						sLockBleSession.getAddKeyboardPwdCallback().onFail(LockError.FAIL);
					}
				})
				.build()
				.post();
	}



	/**
	 * 添加指纹
	 */
	public void addFingerprint(String lockId, String type, String priority, long startDate, long endDate) {

		String fingerprintStr = type + priority;
		// 开始时间
		Calendar startCalendar = Calendar.getInstance();
		startCalendar.setTimeInMillis(startDate);
		int startYear = startCalendar.get(Calendar.YEAR) - 2021; // 年相对值（以 2021 年为基准）
		int startMonth = startCalendar.get(Calendar.MONTH) + 1;
		int startDay = startCalendar.get(Calendar.DAY_OF_MONTH);
		int startHour = startCalendar.get(Calendar.HOUR_OF_DAY);
		int startMinute = startCalendar.get(Calendar.MINUTE);

		// 结束时间
		Calendar endCalendar = Calendar.getInstance();
		endCalendar.setTimeInMillis(endDate);
		int endYear = endCalendar.get(Calendar.YEAR) - 2021; // 年相对值（以 2021 年为基准）
		int endMonth = endCalendar.get(Calendar.MONTH) + 1;
		int endDay = endCalendar.get(Calendar.DAY_OF_MONTH);
		int endHour = endCalendar.get(Calendar.HOUR_OF_DAY);
		int endMinute = endCalendar.get(Calendar.MINUTE);

		String startYearHex = String.format("%01X", startYear);
		String endYearHex = String.format("%01X", endYear);
		String startMonthHex = String.format("%01X", startMonth);
		String endMonthHex = String.format("%01X", endMonth);
		String startDayHex = HexUtil.int2HexStr(startDay, 1);
		String endDayHex = HexUtil.int2HexStr(endDay, 1);
		String startHourHex = HexUtil.int2HexStr(startHour, 1);
		String endHourHex = HexUtil.int2HexStr(endHour, 1);
		String startMinuteHex = HexUtil.int2HexStr(startMinute, 1);
		String endMinuteHex = HexUtil.int2HexStr(endMinute, 1);
		String timeStr = "";
		if ("00".equals(type)) { // 永久指纹
			timeStr = "0000000000000000";
		} else if ("01".equals(type)) { // 限时指纹
			timeStr = endYearHex + startYearHex + endMonthHex + startMonthHex + startDayHex + endDayHex +
					startHourHex + endHourHex + startMinuteHex + endMinuteHex;
		}

		String paramStr = fingerprintStr + timeStr + "000000000000";//0000

		int commandID = ConfigUtil.getCommandId(BleService.this);

		commandID++;

		ConfigUtil.setCommandId(BleService.this, commandID);

		String comandString = "010017" + "0010" + paramStr;
//		String comandString = "01001C" + "0010" + "0001"+ "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010017 0010 + 16 字节 指纹信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};
		final int[] step1FailCount = new int[]{0};
		final int[] step2FailCount = new int[]{0};
		final int[] step3FailCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("进入添加指纹模式发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("进入添加指纹模式发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("进入添加指纹模式返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) { // 进入添加指纹模式返回
					replyAck();
					if ("010018000100".equalsIgnoreCase(response)) { // 成功
						LogUtil.e("进入添加指纹模式返回：成功");
                       //sLockBleSession.getAddKeyboardPwdCallback().onSuccess();
                       //sLockBleSession.getmEnterAddFingerprintCallback().onSuccess();
						sLockBleSession.getmAddFingerprintCallback().onSuccess(0,null);
					} else if ("010018000102".equalsIgnoreCase(response)) { // 参数错误
						LogUtil.e("进入添加指纹模式返回：参数错误");
						sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
					} else if ("010018000103".equalsIgnoreCase(response)) { // 达到指纹最大个数
						LogUtil.e("进入添加指纹模式返回：达到指纹最大个数");
						sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
					}
				}

				else if (receiveCount[0] > 3) {
//					if (receiveCount[0] % 2 == 1) { // 收到数据时，回 Ack
//						replyAck();
//					}

					if (response.contains("010019")) { // 添加指纹返回 step1
						replyAck(); // 收到数据时，回 Ack
						if ("010019000400".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加指纹返回 step1：成功");
							sLockBleSession.getmAddFingerprintCallback().onSuccess(1,null);
//						sLockBleSession.getAddKeyboardPwdCallback().onSuccess();
						} else { // 失败（录入失败，可继续录入）
							if (++step1FailCount[0] <= 2) {
								LogUtil.e("添加指纹返回 step1：" + step1FailCount[0] + "次失败（录入失败，可继续录入）");
							} else { // 录入错误 3 次，退出录指纹模式，断开连接
								LogUtil.e("添加指纹返回 step1：3 次失败，退出录指纹模式，断开连接");
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
							}
						}
					}

					if (response.contains("01001A") || response.contains("01001a")) { // 添加指纹返回 step2
						replyAck(); // 收到数据时，回 Ack
						if ("01001A000400".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加指纹返回 step2：成功");
							sLockBleSession.getmAddFingerprintCallback().onSuccess(2,null);
//						sLockBleSession.getAddKeyboardPwdCallback().onSuccess();
						} else if ("01001A0004FF".equalsIgnoreCase(response.substring(0, 12))) { // 致命失败
							LogUtil.e("添加指纹返回 step2：致命失败");
							sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
						} else { // 其他失败（录入失败，可继续录入）
							LogUtil.e("添加指纹返回 step2：其他失败（录入失败，可继续录入）");
							if (++step2FailCount[0] <= 2) {
								LogUtil.e("添加指纹返回 step2：" + step2FailCount[0] + "次失败（录入失败，可继续录入）");
							} else { // 录入错误 3 次，退出录指纹模式，断开连接
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
								LogUtil.e("添加指纹返回 step2：3 次失败，退出录指纹模式，断开连接");
							}
						}
					}

					if (response.contains("01001B") || response.contains("01001b")) { // 添加指纹返回 step3
						replyAck(); // 收到数据时，回 Ack
						if ("01001B000400".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加指纹返回 step3：成功");
//							if (response.length() == 18){
//								String fingerId = response.substring(14);
//								sLockBleSession.getmAddFingerprintCallback().onSuccess(3,fingerId);
//							}else{
//								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
//							}
							sLockBleSession.getmAddFingerprintCallback().onSuccess(3,null);

						} else if ("01001B0004FF".equalsIgnoreCase(response.substring(0, 12))) { // 致命失败
							LogUtil.e("添加指纹返回 step3：致命失败");
							sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
						} else if ("01001B0004FE".equalsIgnoreCase(response.substring(0, 12))) { // 一般失败（可以继续录入/验证）
							LogUtil.e("添加指纹返回 step2：一般失败（可以继续录入/验证）");
							if (++step3FailCount[0] <= 2) {
								LogUtil.e("添加指纹返回 step3：" + step3FailCount[0] + "次失败（录入失败，可继续录入）");
							} else { // 录入错误 3 次，退出录指纹模式，断开连接
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
								LogUtil.e("添加指纹返回 step3：3 次失败，退出录指纹模式，断开连接");
							}
						}
					}

					if (response.contains("01001C") || response.contains("01001c")) { // 添加指纹返回 step4
						replyAck(); // 收到数据时，回 Ack
						if ("01001C000400".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加指纹返回 step3：成功");
//							if (response.length() == 18){
//								String fingerId = response.substring(14);
//								sLockBleSession.getmAddFingerprintCallback().onSuccess(3,fingerId);
//							}else{
//								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
//							}
							sLockBleSession.getmAddFingerprintCallback().onSuccess(4,null);

						} else if ("01001C0004FF".equalsIgnoreCase(response.substring(0, 12))) { // 致命失败
							LogUtil.e("添加指纹返回 step3：致命失败");
							sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
						} else if ("01001C0004FE".equalsIgnoreCase(response.substring(0, 12))) { // 一般失败（可以继续录入/验证）
							LogUtil.e("添加指纹返回 step2：一般失败（可以继续录入/验证）");
							if (++step3FailCount[0] <= 2) {
								LogUtil.e("添加指纹返回 step3：" + step3FailCount[0] + "次失败（录入失败，可继续录入）");
							} else { // 录入错误 3 次，退出录指纹模式，断开连接
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
								LogUtil.e("添加指纹返回 step3：3 次失败，退出录指纹模式，断开连接");
							}
						}
					}

					if (response.contains("01001D") || response.contains("01001d")) { // 添加指纹返回 step5
						replyAck(); // 收到数据时，回 Ack
						if ("01001D000400".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加指纹返回 step3：成功");
							if (response.length() == 18){
								String fingerId = response.substring(14);
								sLockBleSession.getmAddFingerprintCallback().onSuccess(5,fingerId);
							}else{
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
							}

						} else if ("01001D0004FF".equalsIgnoreCase(response.substring(0, 12))) { // 致命失败
							LogUtil.e("添加指纹返回 step3：致命失败");
							sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
						} else if ("01001D0004FE".equalsIgnoreCase(response.substring(0, 12))) { // 一般失败（可以继续录入/验证）
							LogUtil.e("添加指纹返回 step2：一般失败（可以继续录入/验证）");
							if (++step3FailCount[0] <= 2) {
								LogUtil.e("添加指纹返回 step3：" + step3FailCount[0] + "次失败（录入失败，可继续录入）");
							} else { // 录入错误 3 次，退出录指纹模式，断开连接
								sLockBleSession.getmAddFingerprintCallback().onFail(LockError.FAIL);
								LogUtil.e("添加指纹返回 step3：3 次失败，退出录指纹模式，断开连接");
							}
						}
					}
				}
			}
		});
	}

	/**
	 * 添加卡片
	 */
	public void addICCard(String lockId, String type, String priority, long startDate, long endDate) {

		String fingerprintStr = type + priority;
		// 开始时间
		Calendar startCalendar = Calendar.getInstance();
		startCalendar.setTimeInMillis(startDate);
		int startYear = startCalendar.get(Calendar.YEAR) - 2021; // 年相对值（以 2021 年为基准）
		int startMonth = startCalendar.get(Calendar.MONTH) + 1;
		int startDay = startCalendar.get(Calendar.DAY_OF_MONTH);
		int startHour = startCalendar.get(Calendar.HOUR_OF_DAY);
		int startMinute = startCalendar.get(Calendar.MINUTE);

		// 结束时间
		Calendar endCalendar = Calendar.getInstance();
		endCalendar.setTimeInMillis(endDate);
		int endYear = endCalendar.get(Calendar.YEAR) - 2021; // 年相对值（以 2021 年为基准）
		int endMonth = endCalendar.get(Calendar.MONTH) + 1;
		int endDay = endCalendar.get(Calendar.DAY_OF_MONTH);
		int endHour = endCalendar.get(Calendar.HOUR_OF_DAY);
		int endMinute = endCalendar.get(Calendar.MINUTE);

		String startYearHex = String.format("%01X", startYear);
		String endYearHex = String.format("%01X", endYear);
		String startMonthHex = String.format("%01X", startMonth);
		String endMonthHex = String.format("%01X", endMonth);
		String startDayHex = HexUtil.int2HexStr(startDay, 1);
		String endDayHex = HexUtil.int2HexStr(endDay, 1);
		String startHourHex = HexUtil.int2HexStr(startHour, 1);
		String endHourHex = HexUtil.int2HexStr(endHour, 1);
		String startMinuteHex = HexUtil.int2HexStr(startMinute, 1);
		String endMinuteHex = HexUtil.int2HexStr(endMinute, 1);

		String timeStr = "";
		if ("00".equals(type)) { // 永久卡片
			timeStr = "0000000000000000";
		} else if ("01".equals(type)) { // 限时卡片
			timeStr = endYearHex + startYearHex + endMonthHex + startMonthHex + startDayHex + endDayHex + startHourHex + endHourHex
					+ startMinuteHex + endMinuteHex;
		}

		String paramStr = fingerprintStr + timeStr + "000000000000";

		int commandID = ConfigUtil.getCommandId(BleService.this);

		commandID++;

		ConfigUtil.setCommandId(BleService.this, commandID);

		String comandString = "01001E" + "0010" + paramStr;
//		String comandString = "01001C" + "0010" + "0001"+ "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 01001E 0010 + 16 字节 卡片信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};
		final int[] step1FailCount = new int[]{0};
		final int[] step2FailCount = new int[]{0};
		final int[] step3FailCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("进入添加卡片模式发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("进入添加卡片模式发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("进入添加卡片模式返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) { // 进入添加卡片模式返回
					replyAck();
					if ("01001F000100".equalsIgnoreCase(response)) { // 成功
						LogUtil.e("进入添加卡片模式返回：成功");
						//sLockBleSession.getAddKeyboardPwdCallback().onSuccess();
						//sLockBleSession.getmEnterAddFingerprintCallback().onSuccess();
						sLockBleSession.getmAddCardCallback().onSuccess(null);
					} else if ("01001F000102".equalsIgnoreCase(response)) { // 参数错误
						LogUtil.e("进入添加卡片模式返回：参数错误");
						sLockBleSession.getmAddCardCallback().onFail(LockError.FAIL);
					} else if ("010018000103".equalsIgnoreCase(response)) { // 达到指纹最大个数
						LogUtil.e("进入添加卡片模式返回：达到卡片最大个数");
						sLockBleSession.getmAddCardCallback().onFail(LockError.FAIL);
					}else {
						sLockBleSession.getmAddCardCallback().onFail(LockError.FAIL);
					}
				}

				else if (receiveCount[0] > 3) {
//					if (receiveCount[0] % 2 == 1) { // 收到数据时，回 Ack
//						replyAck();
//					}
					if (response.contains("010020")) { // 添加卡片返回
						replyAck(); // 收到数据时，回 Ack
						if ("010020000c00".equalsIgnoreCase(response.substring(0, 12))) { // 成功
							LogUtil.e("添加卡片返回：成功");
							if (response.length() >= 18){
								String cardId = response.substring(14);
								sLockBleSession.getmAddCardCallback().onSuccess(cardId);
							}else {
								sLockBleSession.getmAddCardCallback().onFail(LockError.FAIL);
							}
						} else { // 失败（添加失败，可继续添加）
							if (++step1FailCount[0] <= 2) {
								LogUtil.e("添加卡片返回：" + step1FailCount[0] + "次失败（添加失败，可继续添加）");
							} else { // 添加错误 3 次，退出添加模式，断开连接
								LogUtil.e("添加卡片返回：3 次失败，退出添加模式，断开连接");
								sLockBleSession.getmAddCardCallback().onFail(LockError.FAIL);
							}
						}
					}
				}
			}
		});
	}

	/**
	 * 删除指纹
	 */
	public void delFingerprint(String lockId, final String fingerprintId) {
		int commandID = ConfigUtil.getCommandId(BleService.this);
		commandID++;
		ConfigUtil.setCommandId(BleService.this, commandID);

		String comandString = "01001E" + "0010" + fingerprintId + "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010017 0010 + 16 字节 指纹信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("删除单个指纹发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("删除单个指纹发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("删除单个指纹返回: " + response);
				saveAck(response);
				receiveCount[0]++;

//				// 解析蓝牙返回数据
//
//				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("01001f000100".equalsIgnoreCase(response)) {
						LogUtil.e("删除单个指纹成功: " + fingerprintId);
						sLockBleSession.getmDeleteFingerprintCallback().onSuccess();
					} else if ("01001f000101".equalsIgnoreCase(response)) {
						LogUtil.e("删除单个指纹失败: " + fingerprintId);
						sLockBleSession.getmDeleteFingerprintCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 删除卡片
	 */
	public void delICCard(String lockId, final String cardId) {
		int commandID = ConfigUtil.getCommandId(BleService.this);
		commandID++;
		ConfigUtil.setCommandId(BleService.this, commandID);
		String comandString = "010021" + "0010" + cardId + "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010021 0010 + 16 字节 卡片信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("删除单张卡片发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("删除单张卡片发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("删除单张卡片返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("010022000100".equalsIgnoreCase(response)) {
						LogUtil.e("删除单张卡片成功: " + cardId);
						sLockBleSession.getmDeleteCardCallback().onSuccess();
					} else if ("010022000101".equalsIgnoreCase(response)) {
						LogUtil.e("删除单张卡片失败: " + cardId);
						sLockBleSession.getmDeleteCardCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 清空指纹
	 */
	public void clearFingers(String lockId) {
		int commandID = ConfigUtil.getCommandId(BleService.this);
		commandID++;
		ConfigUtil.setCommandId(BleService.this, commandID);
		String comandString = "010023" + "0010" + "0000" + "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010021 0010 + 16 字节 卡片信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("清空所有指纹发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("清空所有指纹发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("清空所有指纹返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("010024000100".equalsIgnoreCase(response)) {
						LogUtil.e("清空所有指纹成功: ");
						sLockBleSession.getmClearFingersCallback().onSuccess();
					} else if ("010024000101".equalsIgnoreCase(response)) {
						LogUtil.e("清空所有指纹失败: ");
						sLockBleSession.getmClearFingersCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}


	/**
	 * 清空卡片
	 */
	public void clearICCard(String lockId) {
		int commandID = ConfigUtil.getCommandId(BleService.this);
		commandID++;
		ConfigUtil.setCommandId(BleService.this, commandID);
		String comandString = "010025" + "0010" + "0000" + "0000000000000000000000000000";
		int specialLength = comandString.length() / 2; // 21
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0015
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0015 0000 0001 010021 0010 + 16 字节 卡片信息数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("清空所有卡片发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("清空所有卡片发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("清空所有卡片返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("010026000100".equalsIgnoreCase(response)) {
						LogUtil.e("清空所有卡片成功: ");
						sLockBleSession.getmClearCardsCallback().onSuccess();
					} else if ("010026000101".equalsIgnoreCase(response)) {
						LogUtil.e("清空所有卡片失败: ");
						sLockBleSession.getmClearCardsCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 查找我的设备
	 *
	 * @param alarmDuration 提示音时长
	 */
	public void findMyDevice(int alarmDuration) {
		int commandID = ConfigUtil.getCommandId(this);
		commandID++;
		ConfigUtil.setCommandId(this, commandID);

		//String paramStr = HexUtil.int2HexStr(alarmDuration, 1);
		String comandString = "030001";//"01000f" + "0001" + paramStr;
		int specialLength = comandString.length() / 2; // 6
		String comandLength = HexUtil.int2HexStr(specialLength, 2); // 0006
		String sequenceID = HexUtil.int2HexStr(commandID, 2);
		String dataStr = String.format("AB00%s0000%s%s", comandLength, sequenceID, comandString); // AB00 0006 0000 0001 01000f 0001 + 1 字节 时间数据
		byte[] messageBytes = HexUtil.decodeHex(dataStr.toCharArray());

		final int[] receiveCount = new int[]{0};

		sendCommand(messageBytes, new BleWriteCallback() {
			@Override
			public void onWriteSuccess() {
				LogUtil.e("进入设备升级模式发送成功");
			}

			@Override
			public void onFail() {
				LogUtil.e("进入设备升级模式发送失败");
			}

			@Override
			public void onResponseSuccess(String response) {
				LogUtil.e("进入设备升级模式返回: " + response);
				saveAck(response);
				receiveCount[0]++;
				// 解析蓝牙返回数据
				if (receiveCount[0] == 3) {
					replyAck();
					if ("010010000100".equalsIgnoreCase(response)) {
						sLockBleSession.getFindMyDeviceCallback().onSuccess();
					} else if ("010010000101".equalsIgnoreCase(response)) {
						sLockBleSession.getFindMyDeviceCallback().onFail(LockError.FAIL);
					}
				}
			}
		});
	}

	/**
	 * 发送蓝牙指令
	 *
	 * @param commandSrc 蓝牙数据
	 * @param callback   蓝牙写数据的回调
	 */
	public void sendCommand(byte[] commandSrc, BleWriteCallback callback) {
		mBleWriteCallback = callback;
		int length = commandSrc.length;
		if (dataQueue == null) {
			dataQueue = new LinkedList();
		}

		dataQueue.clear();

		for (int startPos = 0; length > 0; startPos += 20) {
			int ln = Math.min(length, 20);
			byte[] data = new byte[ln];
			System.arraycopy(commandSrc, startPos, data, 0, ln);
			this.dataQueue.add(data);
			length -= 20;
		}

		cloneDataQueue = (LinkedList<byte[]>) dataQueue.clone();

		if (mNotifyCharacteristic != null && mBluetoothGatt != null) {
			try {
				commandSendCount = 0;

				mNotifyCharacteristic.setValue(dataQueue.poll());
				boolean writeSuccess = mBluetoothGatt.writeCharacteristic(mNotifyCharacteristic);
//				LogUtil.e("write data: " + writeSuccess);
				if (writeSuccess) {
					mBleWriteCallback.onWriteSuccess();
				} else {
					mBleWriteCallback.onFail();
					commandSendCount++;
					if (commandSendCount == 1 && cloneDataQueue != null) {
						if (mNotifyCharacteristic != null && mBluetoothGatt != null) {
							dataQueue = cloneDataQueue;
							sendCommandAgain();
						}
					}
				}
			} catch (Exception e) {
				mConnectionState = STATE_DISCONNECTED;
				mBleWriteCallback.onFail();
				if (sPPLockCallback != null) {
					sPPLockCallback.onDeviceDisconnected(mBleDevice);
				}
			}
		} else {
			LogUtil.d("mNotifyCharacteristic:" + mNotifyCharacteristic);
			LogUtil.d("mBluetoothGatt:" + mBluetoothGatt);
			LogUtil.d("mNotifyCharacteristic or mBluetoothGatt is null");
			mConnectionState = STATE_DISCONNECTED;
			mBleWriteCallback.onFail();
			PPLock.getPPLockCallback().onDeviceDisconnected(mBleDevice);
		}
	}

	private void sendCommandAgain() {
		try {
			LogUtil.d("sendCommandAgain");
//			mNotifyCharacteristic.setValue(dataQueue.poll());
			boolean writeSuccess = mBluetoothGatt.writeCharacteristic(mNotifyCharacteristic);
			if (writeSuccess) {
				mBleWriteCallback.onWriteSuccess();
			} else {
				mBleWriteCallback.onFail();
			}
		} catch (Exception e) {
			mConnectionState = STATE_DISCONNECTED;
			if (sPPLockCallback != null) {
				sPPLockCallback.onDeviceDisconnected(mBleDevice);
			}
		}
	}

	/**
	 * 保存 ACK
	 *
	 * @param response 蓝牙返回数据
	 */
	private void saveAck(String response) {
		int len = response.length();
		if (len == 16) {
			String pre = response.substring(0, 4);
			if ("ab00".equalsIgnoreCase(pre)) {
				String ackId = response.substring(len - 4);
				ConfigUtil.setAckId(this, ackId);
				LogUtil.e("save Ack: " + " ackId: " + ackId);
			}
		}
	}

	/**
	 * 接收数据成功后，给固件端返回 ACK
	 */
	private void replyAck() {

		if (disTimerTask != null) {
			disTimerTask.cancel();
		}
		if (timer != null) {
			LogUtil.d("num:" + timer.purge());
		}

		String ackID = ConfigUtil.getAckId(this);
		String ackString = String.format("AB1000000000%s", ackID);
		byte[] messageBytes = HexUtil.decodeHex(ackString.toCharArray());

		if (mNotifyCharacteristic != null && mBluetoothGatt != null) {
			try {
				mNotifyCharacteristic.setValue(messageBytes);
				boolean success = mBluetoothGatt.writeCharacteristic(mNotifyCharacteristic);
				LogUtil.e("reply Ack: " + success + " data: " + ackString);
			} catch (Exception e) {
				mConnectionState = STATE_DISCONNECTED;
				if (sPPLockCallback != null) {
					sPPLockCallback.onDeviceDisconnected(mBleDevice);
				}
			}
		} else {
			LogUtil.d("mNotifyCharacteristic:" + mNotifyCharacteristic);
			LogUtil.d("mBluetoothGatt:" + mBluetoothGatt);
			LogUtil.d("mNotifyCharacteristic or mBluetoothGatt is null");
			mConnectionState = STATE_DISCONNECTED;
			PPLock.getPPLockCallback().onDeviceDisconnected(mBleDevice);
		}
	}

	public void disconnect() {
		ConfigUtil.setCommandId(this, 0);
		readCacheLog();
		mConnectionState = STATE_DISCONNECTED;
		LogUtil.d("disconnect ble");
		if (mBluetoothAdapter != null && mBluetoothGatt != null) {
			try {
				mBluetoothGatt.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			LogUtil.w("BluetoothAdapter not initialized");
		}
	}

	private long initializationTimestamp;
    private int initializationOffset;

    public void setInitializationClock(long timestamp, int offsetMillis) {
        initializationTimestamp = timestamp;
        initializationOffset = offsetMillis;
    }

    private LockData getLockDataObj() {
		LockData lockData = new LockData();
		lockData.setTimestamp(initializationTimestamp);
        lockData.setTimezoneRawOffset(initializationOffset);
        lockData.setLockName(mBleDevice.getName());
		lockData.setLockMac(mBleDevice.getAddress());
		lockData.setAesKey(aesKeyStr);
		lockData.setBatteryLevel(mBleDevice.getBatteryLevel());
		lockData.setProtocolType(protocolType);
		lockData.setProtocolVersion(protocolVersion);
		lockData.setScene(scene);
		lockData.setGroup(group);
		lockData.setVendor(vendor);
		lockData.setModelNum(modelNum);
		lockData.setHardwareVersion(hardwareVersion);
		lockData.setFirmwareVersion(firmwareVersion);

		return lockData;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LogUtil.w("----------onDestroy----------");
		unregisterReceiver(bluttoothState);
		if (mHandler != null) {
			mHandler.removeCallbacksAndMessages(null);
		}
		if (disTimerTask != null) {
			disTimerTask.cancel();
		}

		isCanSendCommandAgain = true;
		disTimerTask = null;
		if (timer != null) {
			timer.cancel();
			timer.purge();
		}

		timer = null;
		PPLock.sIsScan = false;
		LogUtil.d("PPLock.scan:" + PPLock.sIsScan);
		stopScan();
		disconnect();
		close();
	}

	// TODO: 2019-05-05
	private void readCacheLog() {
//		if (this.currentAPICommand == 26 && this.logOperates != null && this.logOperates.size() > 0 && this.transferData != null && this.transferData.getOperateLogType() == OperateLogType.NEW) {
//			this.returnCacheLog();
//		}
	}

	public synchronized void close() {
		if (mBluetoothGatt != null) {
			mBluetoothGatt.disconnect();
			mBluetoothGatt.close();
			mBluetoothGatt = null;
		}
	}

	public boolean isNeedReCon() {
		return isNeedReCon;
	}

	public void setNeedReCon(boolean needReCon) {
		isNeedReCon = needReCon;
	}

	public int getConnectCnt() {
		return connectCnt;
	}

	public void setConnectCnt(int connectCnt) {
		this.connectCnt = connectCnt;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	class ScanCallback implements IScanCallback {

		@Override
		public void onScan(final BleDevice bleDevice) {
			Logger.t("PPLock").e("ScanCallback1");
			ThreadPool.getThreadPool().execute(new Runnable() {
				public void run() {
					Logger.t("PPLock").e("ScanCallback2  mIsScanning = " + mIsScanning +",mConnectionState = " + mConnectionState);
					if (mIsScanning && mConnectionState == STATE_DISCONNECTED) {
						PPLock.getPPLockCallback().onFoundDevice(bleDevice);
					}
				}
			});
		}
	}

	public static void setLockName(String lockName) {
		BleService.lockName = lockName;
	}

	public static String getLockName() {
		return lockName;
	}
}
