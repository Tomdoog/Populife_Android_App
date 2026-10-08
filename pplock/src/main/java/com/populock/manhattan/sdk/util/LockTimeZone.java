package com.populock.manhattan.sdk.util;

/** 曼哈顿协议仅支持整小时时区；不能静默截断半小时、45 分钟偏移。 */
public final class LockTimeZone {
    private LockTimeZone() {}

    public static int encode(int offsetMillis) {
        int hours = offsetMillis / 3600000;
        // 协议旧实现的 13..23 表示 UTC-1..UTC-11；12 与 UTC+12 冲突，拒绝猜测。
        if (offsetMillis % 3600000 != 0 || hours < -11 || hours > 12) {
            throw new IllegalArgumentException("Unsupported lock timezone: " + offsetMillis);
        }
        return hours < 0 ? 12 - hours : hours;
    }
}
