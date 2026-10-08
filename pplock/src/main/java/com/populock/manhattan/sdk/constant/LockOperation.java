package com.populock.manhattan.sdk.constant;

/**
 * Created by Jerry
 */
public enum LockOperation {
	/**
	 * 初始化锁请求
	 */
	INIT_LOCK_REQUEST,
	/**
	 * 初始化锁认证
	 */
	INIT_LOCK_VERIFY,
	/**
	 * 查询锁版本
	 */
	GET_LOCK_VERSION,
	/**
	 * 查询锁信息
	 */
	GET_LOCK_INFO,
	/**
	 * 查询锁电量
	 */
	GET_BATTERY_LEVEL,
	/**
	 * 查询锁时间
	 */
	GET_LOCK_TIME,
	/**
	 * 查询自动上锁时间
	 */
	GET_AUTO_LOCK_TIME,
	/**
	 * 查询上锁状态（上锁/打开）
	 */
	GET_LOCK_STATUS,
	/**
	 * 查询锁操作记录
	 */
	GET_OPERATE_LOG,
	/**
	 * 查询锁固件版本
	 */
	GET_FIRMWARE_VERSION,
	/**
	 * 身份认证
	 */
	AUTH_VERIFY,
	/**
	 * 管理员开锁
	 */
	ADMIN_UNLOCK,
	/**
	 * 管理员闭锁
	 */
	ADMIN_LOCK,
	/**
	 * 普通用户开锁
	 */
	USER_UNLOCK,
	/**
	 * 普通用户闭锁
	 */
	USER_LOCK,
	/**
	 * （管理员）删除锁
	 */
	DELETE_LOCK,
	/**
	 * 设置锁时间
	 */
	SET_LOCK_TIME,
	/**
	 * 设置自动闭锁
	 */
	SET_AUTO_LOCK_TIME,
	/**
	 * 重置键盘密码
	 */
	RESET_KEYBOARD_PWD,
	/**
	 * 添加自定义键盘密码
	 */
	ADD_KEYBOARD_PWD,
	/**
	 * 重置电子钥匙
	 */
	RESET_EKEY,
	/**
	 * 删除单个键盘密码
	 */
	DELETE_KEYBOARD_PWD,
	/**
	 * 修改管理员键盘密码
	 */
	SET_ADMIN_KEYBOARD_PWD,
	/**
	 * 修改普通键盘密码
	 */
	MODIFY_KEYBOARD_PWD,

	/**
	 * 修改普通键盘密码有效期
	 */
	MODIFY_KEYBOARD_PWD_VALID,

	/**
	 * 修改指纹有效期
	 */
	MODIFY_FINGERPRINT_VALID,

	/**
	 * 修改卡片有效期
	 */
	MODIFY_CARD_VALID,
	/**
	 * 寻找我的设备
	 */
	FIND_MY_DEVICE,
	/**
	 * 添加指纹
	 */
	ADD_FINGERPRINT,

	/**
	 * 添加卡片
	 */
	ADD_CARD,
	/**
	 * 删除指纹
	 */
	DEL_FINGERPRINT,

	/**
	 * 删除卡片
	 */
	DEL_CARD,

	/**
	 * 清空指纹
	 */
	CLEAR_FINGERS,

	/**
	 * 清空卡片
	 */
	CLEAR_CARDS,

	/**
	 * 初始化锁失败删除锁，直接删除不认证加快速度
	 */
	INIT_LOCK_DELETE,
	/**
	 * 添加管理员,绑定锁
	 */
	ADD_ADMIN,
    INIT_LOCK_TIME,
}
