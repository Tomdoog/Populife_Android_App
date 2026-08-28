package com.populstay.populife.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.populock.manhattan.sdk.BleDevice;
import com.populstay.populife.R;
import com.populstay.populife.ui.ViewHolder;
import com.ttlock.bl.sdk.api.ExtendedBluetoothDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2016/9/6 0006.
 */
public class FoundDeviceAdapter extends BaseAdapter {

	private Context mContext;
	private List<ExtendedBluetoothDevice> mKJXDevices;
	private List<BleDevice> mMHTDevices;
	private List<Object> mAllDevices = new ArrayList<>();
	private Boolean isMHTLock = false;

	public FoundDeviceAdapter(Context context, List<BleDevice> MHTDevices, List<ExtendedBluetoothDevice> KJXDevices) {
		mContext = context;
//		if (isMHLock) {
//			this.mDeviceList = mDeviceList;
//		}else {
//			this.mLeDevices = mLeDevices;
//		}
		this.mMHTDevices = MHTDevices;
		this.mKJXDevices = KJXDevices;
	}

	public void setMHTState(Boolean state) {
//		if (state) {
//			this.mDeviceList = mDeviceList;
//		}else {
//			this.mLeDevices = mLeDevices;
//		}

		this.isMHTLock = state;
	}

	public boolean isMHTLock() {
		return isMHTLock;
	}

	/**
	 * update scan device
	 *
	 * @param extendedBluetoothDevice
	 */
	public void updateKJXDevice(ExtendedBluetoothDevice extendedBluetoothDevice) {
		isMHTLock = false;
		boolean contain = false;
		boolean update = false;
		if (mKJXDevices != null) {
			for (ExtendedBluetoothDevice device : mKJXDevices) {
				if (device.equals(extendedBluetoothDevice)) {
					contain = true;
					if (device.isSettingMode() != extendedBluetoothDevice.isSettingMode()) {
						device.setSettingMode(extendedBluetoothDevice.isSettingMode());
						update = true;
					}
					if (extendedBluetoothDevice.isTouch()) {
						device.setTouch(extendedBluetoothDevice.isTouch());
						update = true;
					}
					break;
				}
			}
		}
		if (!contain) {
			if (extendedBluetoothDevice != null) {
				mKJXDevices.add(extendedBluetoothDevice);
				mAllDevices.add(extendedBluetoothDevice);
				update = true;
			}
		}
		if (update)
			notifyDataSetChanged();
	}

	/**
	 * update scan device
	 */
	public void UpdateMHTDevice(BleDevice bleDevice) {
		isMHTLock = true;
		boolean contain = false;
		boolean update = false;
		if (mMHTDevices != null) {
			for (BleDevice device : mMHTDevices) {
				if (device.equals(bleDevice)) {
					contain = true;
					if (device.isSettingMode() != bleDevice.isSettingMode()) {
						device.setSettingMode(bleDevice.isSettingMode());
						update = true;
					}
					break;
				}
			}
		}
		if (!contain) {
			if (bleDevice != null) {
				mMHTDevices.add(bleDevice);
				mAllDevices.add(bleDevice);
				update = true;
			}
		}
		if (update)
			notifyDataSetChanged();
	}

//	@Override
//	public int getCount() {
//		if (isMHTLock) {
//			return mMHTDevices.size();
//		}
//		return mKJXDevices.size();
//	}
//
//	@Override
//	public Object getItem(int position) {
//		if (isMHTLock) {
//			return mMHTDevices.get(position);
//		}
//		return mKJXDevices.get(position);
//	}

	@Override
	public int getCount() {
		return mAllDevices.size();
	}

	@Override
	public Object getItem(int position) {
		return mAllDevices.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	/**
	 * 恒定可点。已初始化的设备点下去要能得到一句解释，而在这里返回 false 的话
	 * ListView 压根不派发点击，用户点一个灰条目毫无反应，只会理解成"设备坏了"。
	 * 能不能添加改由 {@link #canAdd(int)} 判断，在点击回调里拦截并提示。
	 */
	@Override
	public boolean isEnabled(int position) {
		return true;
	}

	/** 该设备是否还处于可添加（未被初始化）状态 */
	public boolean canAdd(int position) {
		Object device = getItem(position);
		if (device instanceof BleDevice) {
			return ((BleDevice) device).isSettingMode();
		}
		if (device instanceof ExtendedBluetoothDevice) {
			return ((ExtendedBluetoothDevice) device).isSettingMode();
		}
		return false;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder viewHolder = ViewHolder.get(mContext, convertView, R.layout.item_device);
		TextView deviceName = viewHolder.getView(R.id.tv_item_device_name);
//        TextView macAddress = viewHolder.getView(R.id.device_address);
		ImageView addIcon = viewHolder.getView(R.id.iv_item_device_add_mark);
		TextView devicePaired = viewHolder.getView(R.id.tv_item_device_paired);
		devicePaired.setVisibility(View.GONE);

		Object obj = getItem(position);
		if (obj instanceof BleDevice) {
			BleDevice device = (BleDevice) obj;
			deviceName.setText(device.getName());
			if (device.isSettingMode()) {
				addIcon.setVisibility(View.VISIBLE);
				deviceName.setTextColor(mContext.getResources().getColor(R.color.text_gray_dark));
			} else {
				addIcon.setVisibility(View.GONE);
				deviceName.setTextColor(mContext.getResources().getColor(R.color.text_gray_light));
				// 已经配对
//				if (device.isTouch()){
				devicePaired.setVisibility(View.VISIBLE);
//				}
			}

		} else {
			ExtendedBluetoothDevice device = (ExtendedBluetoothDevice) obj;
			deviceName.setText(device.getName());
			if (device.isSettingMode()) {
				addIcon.setVisibility(View.VISIBLE);
				deviceName.setTextColor(mContext.getResources().getColor(R.color.text_gray_dark));
			} else {
				addIcon.setVisibility(View.GONE);
				deviceName.setTextColor(mContext.getResources().getColor(R.color.text_gray_light));
				// 已经配对
				if (device.isTouch()) {
					devicePaired.setVisibility(View.VISIBLE);
				}
			}
		}
//        macAddress.setText(mLeDevices.get(position).getAddress());
		return viewHolder.getConvertView();
	}
}
