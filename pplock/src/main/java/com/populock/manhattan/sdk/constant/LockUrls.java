package com.populock.manhattan.sdk.constant;

import com.populock.manhattan.sdk.BuildConfig;

/**
 * Created by Jerry
 */
public class LockUrls {
	/**
	 * 项目地址,由渠道 flavor 注入(见 pplock/build.gradle):
	 * overseas → https://v2.server.populife.co/ 、domestic → https://server.populife.szmuen.cn/
	 * 原来靠手工改这一行切渠道,漏改就会让国内包连海外服务器,现在交给构建保证。
	 */
	public static final String BASE_URL = BuildConfig.BASE_URL;
	//public static final String BASE_URL = "http://47.254.73.159:3110";// 测试环境
	/** 当前无人引用,但一并跟随渠道,免得日后被用上时又成了一个硬编码的漏改点 */
	public static final String MANHATTAN_BASE_URL = BuildConfig.BASE_URL;
	public static final String INIT_LOCK_C1_GET = "private-lib/c1-get"; //（get）获取门锁绑定 C1 -- @{@"aesKey":random};//auth/c1/get
	public static final String AUTH_VERIFY_ADMIN_C1_GET = "auth/admin/get"; //（get）管理员身份认证，获取 C1 -- @{@"userId":userId,@"keyId":keyId,@"lockId":lockId,@"randomStr":random};
	public static final String AUTH_VERIFY_USERUNLOCK_C1_GET = "auth/user/get"; //（get）普通用户身份认证，获取 C1 -- @{@"userId":userId,@"keyId":keyId,@"lockId":lockId,@"randomStr":random};
	public static final String RESET_EKEY_UPLOAD_K2 = "auth/v2/k2/update"; //（post）重置钥匙，上传 K2 -- @{@"userId":userId,@"lockId":lockId,@"ciphertext":newK2};
	public static final String RESET_PWD_UPLOAD_LOCK_KEY = "private-lib/lockKey-update"; //（post）重置密码，上传 lockKey -- .params("userId", userId).params("lockId", lockId).params("ciphertext", ciphertext)//auth/v2/lockKey/update
	public static final String OPERATE_LOG_UPLOAD = "operation/log/keyboard/add"; //（post）锁操作记录上传
	public static final String GET_CIPHERTEXT_ADDPWD_DELPWD = "private-lib/ciphertext-get"; //（post）获取密文：1、添加自定义键盘密码时 2、删除键盘密码时 3、普通用户开锁验证时 -- lockId=&text= //auth/ciphertext/get
	public static final String GET_CIPHERTEXT_MODIFY_PWD = "private-lib/modify-password/ciphertext-get"; //（post）获取修改密码时的密文 -- lockId=&d1=&d2= //auth/modify-password/ciphertext/get
	public static final String POPULIFE_AUTH_VERIFY_ADMIN_C1_GET = "private-lib/get-k1-admin"; //（get）管理员身份认证，获取 C1
}
