package com.populstay.populife.net;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 文档 app-sciener-api-audit.md 中的接口统一使用 version=1。 */
final class ScienerApiVersion {
    private static final Set<String> ENDPOINTS = new HashSet<>(Arrays.asList(
            // 键盘密码（KeyboardController）
            "keyboardPwd/get",
            "keyboardPwd/resetKeyboardPwd",

            // 电子钥匙（KeyController）
            "key/send",
            "key/v2/send",
            "key/receive",
            "key/share/receive",
            "key/del",
            "key/v5/del",
            "key/freeze",
            "key/unfreeze",
            "key/changePeriod",
            "key/authorize",
            "key/unauthorize",

            // 指纹（FingerprintController）
            "fingerprint/add",
            "fingerprint/update",
            "fingerprint/delete",
            "fingerprint/clear-all",
            "fingerprint/add/missing",

            // IC 卡（IccController）
            "icc/add",
            "icc/edit",
            "icc/delete",
            "icc/empty",
            "icc/internal/upload",

            // NFC（NfcController）
            "nfc/add",
            "nfc/edit",
            "nfc/delete",
            "nfc/empty",
            "nfc/internal/upload",

            // 网关（GatewayController）
            "gateway/init/success",
            "gateway/add",
            "gateway/delete",
            "gateway/freeze",
            "gateway/unfreeze",
            "gateway/list",
            "gateway/lock/list",
            "gateway/unlock",
            "gateway/lock",
            "gateway/is/freeze",
            "gateway/userId/get",
            "gateway/v2/lock/queryOpenState",
            "gateway/v5/lock/list",

            // 锁（LockController）
            "lock/init",
            "lock/init/v3",
            "lock/deleteAllKey",
            "lock/resetKey",
            "lock/cleanKey",
            "lock/changeDeletePwd",
            "lock/rename",
            "lock/updateElectricQuantity",
            "lock/queryDate",
            "lock/updateDate",
            "lock/transfer",
            "lock/admin-password/modify",
            "lock/updateLockData",
            "lock/changeTimezoneRawOffset",
            "lock/specialValue/set",
            "lock/baseInfo/get",
            "lock/v2/baseInfo/get",
            "lock/queryElectricQuantity",
            "lock/v3-v2",
            "lock/v3-v2timestamp",
            "lock/del",
            "lock/del/v2",
            "lock/v5/del",

            // 用户（UserController）
            "user/register",
            "user/retrieve/password",
            "user/modify/password",
            "user/delete",
            "user/v5/delete",
            "user/get"
    ));

    private ScienerApiVersion() {
    }

    static void addTo(String url, Map<String, Object> params) {
        if (url == null) {
            return;
        }
        String path = url.startsWith("/") ? url.substring(1) : url;
        if (ENDPOINTS.contains(path)) {
            params.put("version", 1);
        }
    }
}
