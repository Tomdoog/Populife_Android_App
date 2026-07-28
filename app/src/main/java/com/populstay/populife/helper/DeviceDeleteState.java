package com.populstay.populife.helper;

import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

/**
 * 设备删除的异常态记录(存 SharedPreferences,按 userId + lockId 区分)。
 *
 * 删除是一条跨"蓝牙物理锁"和"云端账号"两个系统的链路:
 *   删除准备(lock/user/check 验密码+身份+联网) → 物理删除(蓝牙) → 账号移除(lock/del/v2)
 * 中间任何一步断了都可能让两侧状态脱节。本类记录"删到哪一步、结果如何",
 * 供设备详情页在用户下次进入时引导其完成同一笔删除,避免幽灵设备。
 *
 * 状态含义(见产品《设备删除一致性与异常恢复》):
 *  - {@link #STATE_DELETING}   已发出蓝牙删除指令、还没拿到结果就中断(如 App 被杀)。SDK 回调覆盖不到,
 *                              靠"发指令前先写此记录、下次进详情页对账"来兜底,按"结果未知"处理。
 *  - {@link #STATE_UNKNOWN}    物理删除结果未知(蓝牙超时/断连)。不知道锁到底删没删,
 *                              需用户靠近设备实测(原钥匙能开=未删→恢复;初始密码能开=已删→移除)。
 *  - {@link #STATE_PENDING_ACCOUNT} 物理删除已确认成功,但账号移除失败/超时/App 退出。
 *                              锁已重置、云端还留着,必须完成账号移除。
 *
 * 无记录(空串)= 正常,无异常。
 */
public class DeviceDeleteState {

	/** 已发删除指令、结果未定(防 App 被杀);对账时并入"结果未知"处理 */
	public static final String STATE_DELETING = "deleting";
	/** 物理删除结果未知(蓝牙超时/断连) */
	public static final String STATE_UNKNOWN = "unknown";
	/** 物理删除已成功、等待账号移除 */
	public static final String STATE_PENDING_ACCOUNT = "pending_account";

	private static final String KEY_PREFIX = "device_delete_state_";

	private static String key(String userId, int lockId) {
		return KEY_PREFIX + (userId == null ? "" : userId) + "_" + lockId;
	}

	public static void set(String userId, int lockId, String state) {
		PeachPreference.putStr(key(userId, lockId), state);
	}

	/** 返回当前状态;无记录返回空串 */
	public static String get(String userId, int lockId) {
		String v = PeachPreference.getStr(key(userId, lockId));
		return v == null ? "" : v;
	}

	public static void clear(String userId, int lockId) {
		PeachPreference.putStr(key(userId, lockId), "");
	}

	public static boolean isPendingAccount(String userId, int lockId) {
		return STATE_PENDING_ACCOUNT.equals(get(userId, lockId));
	}

	/**
	 * 是否为"结果待确认"态:UNKNOWN,或 DELETING(发了指令没等到结果就中断,当作未知)。
	 */
	public static boolean isResultUnknown(String userId, int lockId) {
		String s = get(userId, lockId);
		return STATE_UNKNOWN.equals(s) || STATE_DELETING.equals(s);
	}

	/** 是否存在任何删除异常记录(供详情页判断要不要拦截) */
	public static boolean hasAbnormal(String userId, int lockId) {
		String s = get(userId, lockId);
		return !StringUtil.isBlank(s);
	}
}
