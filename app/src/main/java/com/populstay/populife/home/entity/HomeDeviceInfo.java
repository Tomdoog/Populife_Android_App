package com.populstay.populife.home.entity;

import android.text.TextUtils;

import com.populstay.populife.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class HomeDeviceInfo {

	public static @StringRes
	int getTypeNameByName(String deviceName) {
		@StringRes
		int name = R.string.door_lock;

		if (TextUtils.isEmpty(deviceName)) {
			return name;
		}

		if (deviceName.startsWith(IDeviceName.NAME_GATEWAY)) {
			name = R.string.device_name_gateway;
		} else if (isPadlock(deviceName)) {
			name = R.string.lock_type_padlock;
		} else if (HomeDeviceInfo.isDeadboltLack(deviceName)) {
			name = R.string.lock_type_deadbolt;
		} else if (HomeDeviceInfo.isKeyBox(deviceName)) {
			name = R.string.lock_type_keybox;
		}  else if (HomeDeviceInfo.isKeyBoxK4(deviceName)) {
			name = R.string.lock_type_keybox_k4;
		} else if (deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			name = R.string.lock_type_moonlock;
		} else {
			// 除了 Gateway、Keybox、Deadbolt，其他统一判断为 kjx 门锁 Door lock
			name = R.string.door_lock;
		}
		return name;
	}

	public static @DrawableRes
	int getIconByName(String deviceName) {
		@DrawableRes
		int iconActive = R.drawable.device_card_single_icon_door_lock_selector;

		if (TextUtils.isEmpty(deviceName)) {
			return iconActive;
		}

		if (deviceName.startsWith(IDeviceName.NAME_GATEWAY)) {
			iconActive = R.drawable.device_card_single_icon_gateway_selector;
		} else if (isPadlock(deviceName)) {
			iconActive = R.drawable.padlock_outline;
		} else if (HomeDeviceInfo.isDeadboltLack(deviceName)) {
			iconActive = R.drawable.device_card_single_icon_deadbolt_selector;
		} else if (HomeDeviceInfo.isKeyBox(deviceName)) {
			iconActive = R.drawable.device_card_single_icon_key_box_selector;
		} else if (HomeDeviceInfo.isKeyBoxK4(deviceName)) {
			iconActive = R.drawable.device_card_single_icon_key_box_k4_selector;
		} else if (deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			iconActive = R.drawable.device_card_single_icon_moon_lock_selector;
		} else {
			// 除了 Gateway、Keybox、Deadbolt，其他统一判断为 kjx 门锁 Door lock
			iconActive = R.drawable.device_card_single_icon_door_lock_selector;
		}
		return iconActive;
	}


	public static @DrawableRes
	int getProductPictureByName(String deviceName) {
		@DrawableRes
		int productPicture = R.drawable.product_door_lock;

		if (TextUtils.isEmpty(deviceName)) {
			return productPicture;
		}

		if (deviceName.startsWith(IDeviceName.NAME_GATEWAY)) {
			productPicture = R.drawable.product_gateway;
		} else if (isPadlock(deviceName)) {
			productPicture = R.drawable.product_padlock;
		} else if (HomeDeviceInfo.isDeadboltLack(deviceName)) {
			productPicture = R.drawable.product_deadbolt;
		} else if (HomeDeviceInfo.isKeyBox(deviceName)) {
			productPicture = R.drawable.product_keybox;
		} else if (HomeDeviceInfo.isKeyBoxK4(deviceName)) {
			productPicture = R.drawable.product_k4;
		} else if (deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
				|| deviceName.startsWith(IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)) {
			productPicture = R.drawable.product_moonlock;
		} else {
			// 除了 Gateway、Keybox、Deadbolt，其他统一判断为 kjx 门锁 Door lock
			productPicture = R.drawable.product_door_lock;
		}
		return productPicture;
	}

	public static boolean isPadlock(String lockType) {
		return lockType != null && lockType.regionMatches(true, 0,
				IDeviceName.NAME_LOCK_PADLOCK, 0, IDeviceName.NAME_LOCK_PADLOCK.length());
	}

	public static  boolean isDeadboltLack(String lockType) {
		if (TextUtils.isEmpty(lockType)){
			return false;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_DEADBOLT)) {
			return true;
		}
		return false;
	}

	public static  boolean isKeyBox(String lockType) {
		if (TextUtils.isEmpty(lockType)){
			return false;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_KEY_BOX)) {
			return true;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_KEY_BOX_2)) {
			return true;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_KEY_BOX_3)) {
			return true;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_JP_PPL)) {
			return true;
		}
		return false;
	}

	// K4设备
	public static  boolean isKeyBoxK4(String lockType) {
		if (TextUtils.isEmpty(lockType)){
			return false;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_PPL_KB4)) {
			return true;
		}
		if(lockType.startsWith(IDeviceName.NAME_LOCK_PPL_KB4_S) || lockType.startsWith(IDeviceName.NAME_LOCK_PPL_KB4_s)) {
			return true;
		}
		return false;
	}

	// 这个不要随便动，需要跟IOS端统一的，用来区分设备类型
	public interface IDeviceName {
		// P3_ 开头为蓝牙、指纹挂锁，与 iOS 保持一致（忽略大小写）。
		String NAME_LOCK_PADLOCK = "P3_";

		// 网关(G2开头的，在添加设备时，转为Gateway)
		String NAME_GATEWAY = "Gateway";

		// PPL-DB开头为横闩锁
		String NAME_LOCK_DEADBOLT = "PPL-DB";

		// PPL_KB或KEYBOX开头为密码盒
		String NAME_LOCK_KEY_BOX = "PPL_KB";
		String NAME_LOCK_KEY_BOX_2 = "KEYBOX";
		String NAME_LOCK_KEY_BOX_3 = "PPL_kb";
		String NAME_LOCK_JP_PPL = "JP_PPL";

		// 门锁（kjx 生态锁，统一处理）
		String NAME_LOCK_KJX_DOOR_LOCK = "KJX_DOOR_LOCK";

		//曼哈顿把手锁
		String NAME_LOCK_MANHATTAN_MOON_LOCK = "PPL_ML";//PPL_ML

		String NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER = "PPL_ml";//PPL_ML

		// K4
		String NAME_LOCK_PPL_KB4 = "PPL_KB4";
		String NAME_LOCK_PPL_KB4_S = "S";
		String NAME_LOCK_PPL_KB4_s = "s";
	}

	public interface IModelNum {
		// 网关
		String NAME_GATEWAY = "1";//
		// 横闩锁
		String NAME_LOCK_DEADBOLT = "2";
		// 密码盒
		String NAME_LOCK_KEY_BOX = "3";
		// 门锁（kjx 生态锁，统一处理）
		String NAME_LOCK_KJX_DOOR_LOCK = "4";

		//曼哈顿锁
		String NAME_LOCK_MANHATTAN_MOON_LOCK = "5";
	}

}
