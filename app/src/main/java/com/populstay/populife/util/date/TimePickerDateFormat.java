package com.populstay.populife.util.date;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 时间选择器返回的是手机本地时间，不能沿用首次打开选择器时的时区。 */
public final class TimePickerDateFormat extends SimpleDateFormat {
    public TimePickerDateFormat() {
        super("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    @Override
    public Date parse(String text, ParsePosition position) {
        // WheelTime 持有静态格式器；每次确认都同步时区，避免与页面显示相差数小时。
        setTimeZone(TimeZone.getDefault());
        return super.parse(text, position);
    }
}
