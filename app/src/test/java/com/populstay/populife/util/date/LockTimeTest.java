package com.populstay.populife.util.date;

import org.junit.Test;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class LockTimeTest {
    private static final String PATTERN = "yyyy-MM-dd HH:mm";

    @Test public void passwordWindowDoesNotMoveWhenPhoneTravels() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            long start = LockTime.parse("2026-10-08 09:00", PATTERN, 8 * 3600000);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(start, LockTime.parse("2026-10-08 09:00", PATTERN, 8 * 3600000));
            assertEquals("2026-10-08 01:00", LockTime.format(start, PATTERN, 0));
            assertEquals("2026-10-08 09:00", LockTime.format(start, PATTERN, 8 * 3600000));
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void fractionalZonesKeepMinutesAndServerParameterPrecision() {
        assertEquals("5.5", LockTime.hours(19800000));
        assertEquals("5.75", LockTime.hours(20700000));
        assertEquals("-3.5", LockTime.hours(-12600000));
        assertEquals("8", LockTime.hours(28800000));
        assertEquals("0", LockTime.hours(0));
        long value = LockTime.parse("2026-10-08 09:00", PATTERN, 20700000);
        assertEquals("2026-10-08 03:15", LockTime.format(value, PATTERN, 0));
    }

    @Test public void negativeZoneHandlesDateRollover() {
        long start = LockTime.parse("2026-10-08 23:00", PATTERN, -7 * 3600000);
        assertEquals("2026-10-09 06:00", LockTime.format(start, PATTERN, 0));
    }

    @Test public void savedOffsetDoesNotFollowPhonesDaylightSavingRules() {
        long winter = LockTime.parse("2026-12-01 09:00", PATTERN, -7 * 3600000);
        assertEquals("2026-12-01 16:00", LockTime.format(winter, PATTERN, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDatesCannotSilentlyBecomeCurrentTime() {
        LockTime.parse("2026-02-30 09:00", PATTERN, 0);
    }
}
