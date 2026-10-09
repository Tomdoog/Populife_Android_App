package com.populstay.populife.helper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/** 手机、锁、服务器使用同一个毫秒偏移；缺失的字段不能被当作 UTC。 */
public final class BindingTimeEvidence {
    static boolean verified(String json) {
        try {
            JSONObject info = JSON.parseObject(json);
            return info != null && info.getBooleanValue("bindingTimeVerified")
                    && info.getInteger("timezoneRawOffset") != null
                    && info.getLongValue("verifiedLockTime") > 0;
        } catch (RuntimeException e) { return false; }
    }

    public static boolean clockMatches(long written, long elapsed, long read) {
        return written > 0 && elapsed >= 0 && read > 0 && Math.abs(read - (written + elapsed)) <= 5000;
    }

    static boolean serverMatches(JSONObject local, JSONObject cloud) {
        if (local == null || cloud == null) return false;
        try {
            Integer expected = local.getInteger("timezoneRawOffset");
            Integer actual = cloud.getInteger("timezoneRawOffSet");
            if (actual == null) actual = cloud.getInteger("timezoneRawOffset");
            return expected != null && expected.equals(actual);
        } catch (RuntimeException e) { return false; }
    }
}
