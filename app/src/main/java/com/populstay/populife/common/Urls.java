package com.populstay.populife.common;

import com.populock.manhattan.sdk.constant.LockUrls;
import com.populstay.populife.BuildConfig;
import com.populstay.populife.constant.Constant;

/**
 * 网络请求 URL 常量
 * Created by Jerry
 */
public class Urls {

	// 旧正式地址 https://server.populife.yigululock.com
	// 旧测试环境 http://test.server.populife.co
	// 新测试环境 http://testv2.server.populife.co  http://114.67.117.65:2100 http://47.254.73.159:3110

	/**
	 * 项目地址,由渠道 flavor 注入(见 app/build.gradle):
	 * overseas → https://v2.server.populife.co 、domestic → https://server.populife.szmuen.cn
	 * 原来靠手工改这一行切渠道,漏改就会让国内包连海外服务器,现在交给构建保证。
	 */
	public static final String BASE_URL = BuildConfig.BASE_URL;
	//public static final String BASE_URL = "http://47.254.73.159:3110";// 测试环境

	public static final String APP_CHECK_UPDATE = "app/version/get"; //（post）App 版本更新，获取最新版本
	public static final String SIGN_UP = "user/register"; //（post）用户注册
	//public static final String SIGN_IN = "user/login"; //（post）用户登录
	public static final String SIGN_IN = "user/login/bypass"; //（post）用户登录
	public static final String SIGN_IN_NEW_DEVICE_PUSH = "user/relogin/push/notice"; //（post）重登录推送通知（用户在另一设备登录时，向前一设备推送下线通知）
	public static final String QUERY_LATEST_DEVICE_ID = "user/get/deviceId"; //（get）查询最新的设备id, 判断是否已经在异地登录
	public static final String VERIFICATION_CODE_REGISTER_OR_BIND = "user/send/code"; //（post）用户注册、绑定手机号、绑定邮箱地址时发送的验证码
	public static final String VERIFICATION_CODE_RESETPWD_DELETEACCOUNT_NEWDEVICELOGIN = "user/retrieve/password/send/code"; //（post）获取验证码：1、（忘记密码后）重置密码时；2、删除账号；3、异地新设备登录，需要验证
	public static final String VERIFICATION_CODE_VALIDATE = "user/validate/verifycode"; //（get）检验系统发送的验证码与用户输入的验证码是否一致

	/*public static final String USER_LOGIN_BYCODE_SEND_CODE = VERIFICATION_CODE_RESETPWD_DELETEACCOUNT_NEWDEVICELOGIN;// 验证码登陆前，发送验证雄码
	public static final String USER_LOGIN_BYCODE = VERIFICATION_CODE_VALIDATE;// 1、验证码登录 2、异地新设备登录，发送验证*/
	public static final String USER_LOGIN_BYCODE_SEND_CODE = "user/login/bycode/send/code";// 验证码登陆前，发送验证雄码
	public static final String USER_LOGIN_BYCODE = "user/login/bycode";// 1、验证码登录 2、异地新设备登录，发送验证

	public static final String USER_LOCK_INFO = "user/login/first/get"; //（get）登录成功后调用，查询用户拥有的锁及附属的相关信息
	public static final String NICKNAME_MODIFY = "user/nickname/modify"; //（post）修改用户昵称
	public static final String AVATAR_UPLOAD = "/user/avatar/upload"; //（post）上传头像
	public static final String USER_INFO = "user/get"; //（get）获取用户信息（头像、昵称、手机号、邮箱地址等）
	public static final String ACCOUNT_BIND_EMAIL = "user/email/bind"; //（post）绑定邮箱
	public static final String ACCOUNT_BIND_PHONE = "user/phone/bind"; //（post）绑定手机号码
	public static final String ACCOUNT_PWD_RESET = "user/retrieve/password"; //（post）（忘记密码后）重置密码
	public static final String ACCOUNT_PWD_MODIFY = "user/modify/password"; //（post）修改密码
	public static final String ACCOUNT_PWD_VERIFY = "user/validate/password"; //（post）用于重置密码、清空钥匙、重置钥匙操作时，验证当前用户的登录密码
	public static final String DELETE_ACCOUNT = "user/delete"; //（post）删除用户

	public static final String USER_MESSAGE_LIST = "user/message/list"; //（get）获取用户消息列表数据
	public static final String USER_MESSAGE_CLEAR_ALL = "user/message/empty"; //（post）清空消息当前用户所有的消息
	public static final String USER_MESSAGE_ITEM_DETAIL = "user/message/get"; //（get）根据消息id获取单条用户消息详情，并更新阅读状态为“已读”
	public static final String USER_MESSAGE_ITEM_DELETE = "user/message/delete"; //（post）删除单条消息

	public static final String USER_FEEDBACK_LIST = "feedback/get"; //（get）获取当前登录用户提交的意见反馈列表信息，根据提交时间由近到远排列，最多取20条数据
	public static final String USER_FEEDBACK_ADD = "feedback/add"; //（post）用户对app应用提出的意见或建议
	public static final String USER_FEEDBACK_ITEM_DELETE = "feedback/delete"; //（post）删除单条意见反馈

	public static final String KEY_RECEIVE = "key/receive"; //（get）钥匙 - 接收钥匙
	public static final String LOCK_GROUP_LIST = "home/get"; //（get）获取家庭（分组）信息和分组下锁的数量
	public static final String GET_HOME_MY_OWN = "home/my-own/get"; //家庭（分组） - 获取本人创建的空间（设备绑定家庭时，获取家庭列表都用这个接口）
	public static final String LOCK_GROUP_ADD = "home/add"; //（post）添加家庭（分组），方便对锁分类管理
	public static final String LOCK_GROUP_MODIFY = "home/modify"; //（post）修改家庭（分组）信息
	public static final String LOCK_GROUP_DELETE = "home/delete"; //（post）删除家庭（分组）信息，将关联的锁改为未关联家庭（未分组）
	public static final String LOCK_GROUP_GET_DEVICE = "home/device/get"; //（get）根据当前用户id、组id，获取家庭中的设备
	public static final String LOCK_GROUP_BIND = "setting/lock/home/bind"; //（post）根据当前用户id、锁id、以及选择的分组id绑定锁分组，将当前用户拥有的锁绑定在自己的分组下，方便对锁进行分类

	public static final String LOCK_INIT = "lock/init/v3"; //（post）初始化锁,(谁初始化锁，谁就是该锁的管理员。初始化后，之前所有的钥匙密码失效)
	public static final String MH_LOCK_INIT = "lock/mhd_init"; //（post）曼哈顿初始化锁,(谁初始化锁，谁就是该锁的管理员。初始化后，之前所有的钥匙密码失效)
	public static final String LOCK_ADMIN_DELETE = "lock/del"; //（post）删除锁(只有管理员可以操作)
	public static final String LOCK_ADMIN_DELETE_V2 = "lock/del/v2"; //（post）删除锁V2(只需 userId+lockId,不需要密码;删除准备已验过密码后用它做账号移除)
	public static final String LOCK_NAME_MODIFY = "lock/rename"; //（post）修改锁名称
	public static final String LOCK_BIND_HOME = "lock/bind-home"; //（post）绑定一个家庭
	public static final String LOCK_LIST = "lock/list"; //（post）锁列表,实指该用户所拥有的钥匙列表
	public static final String LOCK_GET = "lock/get"; //（post）获取当前用户的锁(返回含 mac),用于自动上传前按 mac 去重
	public static final String LOCK_FIRMWARE_INFO = "setting/lock/version"; //（get）根据参数锁id检查锁固件版本和是否有新固件版本可升级
	public static final String LOCK_OPERATE_RECORDS_GET = "operation/log/get"; //（get）查询锁所有的操作记录，按操作日期（天）分组，默认取最近20条，从操作时间由近至远排列
	public static final String LOCK_OPERATE_RECORDS_DELETE = "operation/log/remove"; //（post）根据操作记录id删除一条操作记录
	public static final String LOCK_OPERATE_RECORDS_CLEAR = "operation/log/remove-all"; //（post）根据锁id删除锁下所有的操作记录
	public static final String LOCK_EKEY_SEND = "key/send"; //（get）管理员或被授权用户可以用向其他用户发送钥匙，向同一个用户发送多个钥匙时，只有最后一个钥匙有效（废弃）
	public static final String LOCK_EKEY_V2_SEND = "key/v2/send"; //（get）管理员或被授权用户可以用向其他用户发送钥匙，向同一个用户发送多个钥匙时，只有最后一个钥匙有效
	public static final String LOCK_EKEY_SHARE_RECEIVE = "key/share/receive";// 钥匙 - 再次分享钥匙(指定接收者)
	public static final String LOCK_KEYBOARD_LIST_V2 = "key/keyboard/list/v2"; //（get）密码列表（V2）
	public static final String LOCK_KEY_LIST_V2 = "key/list/v2"; //（get）钥匙列表
	public static final String LOCK_PASSCODE_GENERATE = "keyboardPwd/get"; //（post）获取键盘密码，是指生成键盘密码
	public static final String LOCK_PASSCODE_DELETE = "keyboardPwd/delete"; //（post）删除单个键盘密码
	public static final String LOCK_PASSCODE_MODIFY = "keyboardPwd/change"; //（post）修改键盘密码, 只能修改三代锁， 密码版本为4的密码，支持修改密码和修改密码期限
	public static final String LOCK_PASSCODE_ALIAS_MODIFY = "keyboardPwd/changeAlias"; //（post）修改密码别名
	public static final String LOCK_OPERATE_APP_LOG_ADD = "operation/log/add"; //（post）用户通过app开锁、闭锁或输入键盘密码开锁、以及远程开锁等操作都需记录日志，本接口供app开锁、闭锁操作时调用
	public static final String LOCK_OPERATE_LOG_KEYBOARD_ADD = "operation/log/keyboard/add"; //（post）上传密码操作记录(获取sdk的开锁的记录（只传recordType=4或者7类型的记录数据）)
	public static final String LOCK_EKEY_LIST_MANAGEMENT = "key/list"; //（post）钥匙列表
	public static final String LOCK_EKEY_MODIFY_ALIAS = "key/changeAlias"; //（post）修改钥匙别名
	public static final String LOCK_EKEY_MODIFY_PERIOD = "key/changePeriod"; //（post）修改钥匙有效期
	public static final String LOCK_EKEY_RESET = "lock/resetKey"; //（post）重置普通钥匙(只允许管理员操作，必需先通过SDK重置钥匙，再调用该接口)
	public static final String LOCK_EKEY_CLEAR = "lock/cleanKey"; //（post）清空钥匙
	public static final String LOCK_PASSCODE_LIST = "keyboardPwd/list"; //（post）键盘密码列表
	public static final String LOCK_EKEY_FREEZE = "key/freeze"; //（post）冻结用户的钥匙（钥匙被冻结后不可以对锁作开锁、闭锁、修改设置等操作）
	public static final String LOCK_EKEY_UN_FREEZE = "key/unfreeze"; //（post）解冻钥匙
	public static final String LOCK_EKEY_AUTHORIZE = "key/authorize"; //（post）授予普通钥匙用户管理锁的权限，如发送钥匙和获取密码等权限(单次钥匙不能授权)
	public static final String LOCK_EKEY_UN_AUTHORIZE = "key/unauthorize"; //（post）解除授予普通钥匙用户的管理锁的权限
	public static final String LOCK_EKEY_DELETE = "key/del"; //（post）删除钥匙,适用于普通用户和授权用户。钥匙列表的删除，普通用户/授权用户在设置页面删除锁的操作
	public static final String LOCK_ADMIN_KEYBOARD_PWD_MODIFY = "lock/admin-password/modify"; //（post）修改锁管理员的键盘密码，必须是管理员本人才可以修改锁管理员的键盘密码
	public static final String LOCK_USER_LIST = "lock/user/list"; //（post）锁用户管理列表
	public static final String LOCK_EKEY_LIST_EXPIRING = "lock/expire/key/list"; //（post）即将到期钥匙列表
	public static final String LOCK_EKEY_RECORD = "operation/log/get/4/key"; //（get）查询钥匙所有的操作记录，按操作日期（天）分组，默认取最近20条，从操作时间由近至远排列
	public static final String LOCK_PASSCODE_RECORD = "operation/log/get/4/password"; //（get）查询键盘密码所有的操作记录，按操作日期（天）分组，默认取最近20条，从操作时间由近至远排列
	public static final String LOCK_PASSCODE_RESET = "keyboardPwd/resetKeyboardPwd"; //（post）重置键盘密码，用于密码用完后，调用该接口将会生成一批新密码，旧密码全部失效
	public static final String LOCK_PASSCODE_ADD = "keyboardPwd/add"; //（post）添加键盘密码，是指自定义键盘密码
    public static final String LOCK_UPLOAD_BATTERY = "lock/updateElectricQuantity"; //（post）上传锁电量
    public static final String LOCK_GET_BATTERY = "lock/queryElectricQuantity"; //（post）上传锁电量
	public static final String KEY_KEYBOARD_LIST = "key/keyboard/list"; //（get）钥匙 - 密钥列表（已废弃，已更换为 V2 版本）

	public static final String GATEWAY_LIST = "gateway/list"; //（get）查询用户下网关列表信息
	public static final String GATEWAY_GET_USER_KEY_ID = "gateway/userId/get"; //（get）通过用户id获取sciener用户主键id
	public static final String GATEWAY_ADD = "gateway/add"; //（post）使用SDK添加网关后调用该接口绑定网关名称
	public static final String GATEWAY_INIT_CHECK_SUCCESS = "gateway/init/success"; //（get）使用SDK添加网关后调用该接口，查询网关是否添加成功
	public static final String GATEWAY_DELETE = "gateway/delete"; //（post）根据网关id删除网关
	public static final String GATEWAY_BINDED_LOCK_LIST = "gateway/lock/list"; //（get）根据网关id查询网关下的锁列表信息
	public static final String GATEWAY_LOCK_TIME_READ = "lock/queryDate"; //（post）网关读取锁时间
	public static final String GATEWAY_LOCK_TIME_CALIBRATE = "lock/updateDate"; //（post）网关校准锁时间
	public static final String GATEWAY_REMOTE_UNLOCK = "gateway/unlock"; //（post）网关远程开锁，利用网关远程打开锁id对应的锁
	public static final String GATEWAY_LOCK_FREEZE = "gateway/freeze"; //（post）利用网关远程冻结锁，冻结后，所有开锁方式都禁用，但蓝牙还是可以连接的，以便冻结后还可以解冻
	public static final String GATEWAY_LOCK_UNFREEZE = "gateway/unfreeze"; //（post）利用网关远程网关解冻锁
	public static final String LOCK_SPECIAL_VALUE_MODIFY = "lock/specialValue/set"; //（post）修改锁的特征值
	public static final String GATEWAY_REMOTE_LOCK = "gateway/lock"; //（post）网关网关闭锁，利用网关远程关闭锁id对应的锁
	public static final String GATEWAY_REMOTE_LOCK_QUERY_OPEN_STATE = "gateway/v2/lock/queryOpenState"; //（get）GatewayGroupV2 - 查询锁开关状态

	public static final String GATEWAY_MODIFY_NAME = "gateway/modify-name"; //（post）网关 - 修改网关名称
	public static final String GATEWAY_SET_HOME = "gateway/set-home"; //（post）网关 - 设置家庭

	// 网关 - 锁网关列表v5
	public static final String GATEWAY_V5_LOCK_LIST = "gateway/v5/lock/list";

	public static final String IC_CARD_LIST = "icc/get"; //（get）分页获取或按关键词搜索（卡号或备注）锁id下所有的IC卡
	public static final String IC_CARD_ADD = "icc/add"; //（post）通过蓝牙在锁上添加IC卡后调用该接口
	public static final String IC_CARD_DELETE = "icc/delete"; //（post）通过卡号删除IC卡（蓝牙删除/网关删除）
	public static final String IC_CARD_CLEAR = "icc/empty"; //（post）删除锁id下所有的IC卡，用户id对应必须是锁管理员
	public static final String IC_CARD_UPLOAD = "icc/internal/upload"; //（post）上传锁内ic卡，如果records参数为空则删除该锁下所有的ic卡
	public static final String IC_CARD_INFO_UPDATE = "icc/edit"; //（post）编辑、更新单个 IC 卡
	public static final String IC_CARD_RECORD = "operation/log/get/4/icc"; //（get）查询IC卡所有的操作记录，按操作日期（天）分组，默认取最近20条，从操作时间由近至远排列

	public static final String FINGERPRINT_LIST = "fingerprint/list"; //（post）分页获取对应用户的指纹列表数据
	public static final String FINGERPRINT_ADD = "fingerprint/add"; //（post）通过蓝牙在锁上添加指纹后调用该接口
	public static final String FINGERPRINT_DELETE = "fingerprint/delete"; //（post）删除指纹（蓝牙删除/网关删除）
	public static final String FINGERPRINT_CLEAR = "fingerprint/clear-all"; //（post）清除指纹，用户id对应必须是锁的管理员
	public static final String FINGERPRINT_UPLOAD = "fingerprint/add/missing"; //（post）查询锁里的指纹列表与后台指纹对比，如果不存在的就添加
	public static final String FINGERPRINT_INFO_UPDATE = "fingerprint/update"; //（post）更新单个指纹信息
	public static final String FINGERPRINT_RECORD = "operation/log/get/4/fingerprint"; //（get）查询指纹所有的操作记录，按操作日期（天）分组，默认取最近20条，从操作时间由近至远排列


	public static final String LOCK_GET_BASEINFO = "lock/baseInfo/get"; //（get）获取锁信息
	public static final String LOCK_USER_CHECK = "lock/user/check"; //（post）校验用户（是否是该锁管理员及校验密码）


	public static final String REPAIR_APPLY_SUBMIT = "/repair-apply/submit"; //（post）维修服务 - 提交维修申请
	public static final String REPAIR_APPLY_GET = "repair-apply/get"; //（get）维修服务 - 获取用户的维修申请列表
	public static final String PAYPAL_REPAIR_SERVICE_DEVICE_LIST = "paypal/repair-service/device/list"; //（get）支付 - 获取设备列表
	public static final String PAYPAL_REPAIR_SERVICE_PAY_ORDER = "paypal/repair-service/pay/order"; //（get）支付 - 创建预支付订单（维修服务费）该接口会返回支付需要的参数，在支付前调用
	public static final String PAYPAL_SUCCESS_REPAIR_SERVICE_PAY_ORDER = "paypal/success/repair-service/pay/order"; //（post）支付 - 支付成功后，回调的接口（维修服务费）
	public static final String PAYPAL_REPAIR_PAY_ORDER = "paypal/repair/pay/order"; //（get）支付 - 创建预支付订单（维修费） 该接口会返回支付需要的参数，在支付前调用
	public static final String PAYPAL_SUCCESS_REPAIR_PAY_ORDER = "paypal/success/repair/pay/order"; //（post）支付 - 支付成功后，回调的接口（维修费）


	public static final String USER_LANGUAGE_SWITCH = "user/language/switch"; //（get）通过参数locale指要切换的目标语言，语言切换后，仅消息和推送通知生效，语言切换后会在客户端一直生效，直到下次切换新的语言


	public static final String KEY_KEYBOARD_LIST_V2_TOTALNUM = "key/keyboard/list/v2/totalnum";//密码列表V2总数量
	public static final String KEY_LIST_V2_TOTALNUM = "key/list/v2/totalnum";//钥匙列表v2总数
    public static final String LOCK_TRANSFER = "lock/transfer"; //（post）转移锁
    public static final String LOCK_EKEY_PERMISSION_GET = "key/permission/get"; //（get）钥匙权限
    public static final String LOCK_EKEY_PERMISSION_POST = "key/permission/update"; //（post）钥匙权限
    public static final String LOCK_NOTIFICATION_GET = "lock/setting/v5/notification/get"; //（post）获取通知状态
    public static final String LOCK_NOTIFICATION_POST = "lock/setting/v5/notification/update"; //（post）更新通知状态
    public static final String LOCK_UNLOCK_TYPE_POST = "lock/unlock_type/save"; //（post）保存开锁类型
    public static final String LOCK_DATA_UPDATE_POST = "lock/updateLockData"; //（post）修改锁的特征值 新版


}
