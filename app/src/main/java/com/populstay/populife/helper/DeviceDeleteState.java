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
	/** MAC → lockId 索引,用于"同一把锁被重新添加后作废旧记录",见 {@link #clearOnLockAdded} */
	private static final String KEY_MAC_PREFIX = "device_delete_state_mac_";

	private static String key(String userId, int lockId) {
		return KEY_PREFIX + (userId == null ? "" : userId) + "_" + lockId;
	}

	private static String macKey(String userId, String lockMac) {
		return KEY_MAC_PREFIX + (userId == null ? "" : userId) + "_"
				+ (lockMac == null ? "" : lockMac.toUpperCase());
	}

	public static void set(String userId, int lockId, String state) {
		set(userId, lockId, state, null);
	}

	/**
	 * @param lockMac 锁的 MAC。异常记录必须同时绑到物理锁上:lockId 是云端记录的 id,重新添加同一把锁时
	 *                服务端可能换发新 id,只按 lockId 记录会导致旧记录永远挂着、或错扣到新设备头上。
	 */
	public static void set(String userId, int lockId, String state, String lockMac) {
		PeachPreference.putStr(key(userId, lockId), state);
		if (!StringUtil.isBlank(lockMac)) {
			PeachPreference.putStr(macKey(userId, lockMac), String.valueOf(lockId));
		}
	}

	/**
	 * 同一把锁被重新添加(初始化成功)时调用:作废这把锁上已经失效的删除记录,并返回云端那条需要清理的幽灵 lockId。
	 *
	 * 分两种情况,取决于服务端是否复用 lockId:
	 *  - 复用(新旧 id 相同):记录指向的就是刚添加的这台设备,必须清掉,否则用户一进详情页就被判成
	 *    "待账号移除",点 Remove 等于把刚添加成功的设备删掉。此时没有幽灵,返回 0。
	 *  - 换发新 id:旧 id 对应的是云端那条没删干净的幽灵记录,返回它交给调用方静默移除。
	 *    移除成功前不清本地记录 —— 万一移除失败(如断网),详情页的恢复弹窗仍能兜底引导用户。
	 *
	 * @param newLockId 服务端本次返回的 lockId
	 * @param lockMac   锁的 MAC,用于找回旧 lockId 的记录
	 * @return 需要静默从账号移除的幽灵 lockId;没有则返回 0
	 */
	public static int onSameLockReAdded(String userId, int newLockId, String lockMac) {
		clear(userId, newLockId);
		if (StringUtil.isBlank(lockMac)) {
			return 0;
		}
		String oldLockId = PeachPreference.getStr(macKey(userId, lockMac));
		if (StringUtil.isBlank(oldLockId)) {
			return 0;
		}
		int ghostLockId;
		try {
			ghostLockId = Integer.parseInt(oldLockId);
		} catch (NumberFormatException e) {
			PeachPreference.putStr(macKey(userId, lockMac), "");
			return 0;
		}
		if (ghostLockId == newLockId) {
			// 服务端复用了 lockId,旧记录已随上面的 clear 一并作废
			PeachPreference.putStr(macKey(userId, lockMac), "");
			return 0;
		}
		return ghostLockId;
	}

	/** 幽灵记录已从账号移除:清掉它的状态与 MAC 索引 */
	public static void clearGhost(String userId, int ghostLockId, String lockMac) {
		clear(userId, ghostLockId);
		if (!StringUtil.isBlank(lockMac)) {
			PeachPreference.putStr(macKey(userId, lockMac), "");
		}
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
