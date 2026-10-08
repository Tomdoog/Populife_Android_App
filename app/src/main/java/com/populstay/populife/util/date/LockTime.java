package com.populstay.populife.util.date;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/** 锁固定使用绑定时保存的 UTC 偏移，手机旅行或切换时区不改变密码时段。 */
public final class LockTime {
    private LockTime() {}

    public static TimeZone zone(int offsetMillis) {
        return new SimpleTimeZone(offsetMillis, "Lock");
    }

    public static String hours(int offsetMillis) {
        return BigDecimal.valueOf(offsetMillis).divide(BigDecimal.valueOf(3600000))
                .stripTrailingZeros().toPlainString();
    }

    public static long parse(String value, String pattern, int offsetMillis) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(zone(offsetMillis));
        format.setLenient(false);
        try { return format.parse(value).getTime(); }
        catch (ParseException e) { throw new IllegalArgumentException("Invalid lock time", e); }
    }

    public static String format(long time, String pattern, int offsetMillis) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(zone(offsetMillis));
        return format.format(new Date(time));
    }
}
