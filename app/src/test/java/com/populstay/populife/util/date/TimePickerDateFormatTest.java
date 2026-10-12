package com.populstay.populife.util.date;

import com.bigkoo.pickerview.view.WheelTime;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class TimePickerDateFormatTest {
    private TimeZone originalZone;
    private DateFormat originalPickerFormat;

    @Before public void saveDefaults() {
        originalZone = TimeZone.getDefault();
        originalPickerFormat = WheelTime.dateFormat;
    }

    @After public void restoreDefaults() {
        TimeZone.setDefault(originalZone);
        WheelTime.dateFormat = originalPickerFormat;
    }

    @Test public void cyclicStartAtTwoStaysAtTwoAfterReturningFromHawaii() throws Exception {
        installInZone("Pacific/Honolulu");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        Date selected = WheelTime.dateFormat.parse("2026-10-12 02:00:00");

        assertEquals("02:00", DateUtil.getDateToString(selected, DateUtil.DATE_FORMAT_HH_00));
        assertEquals("2026-10-12 02:00", DateUtil.getDateToString(selected,
                DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
    }

    @Test public void switchingZonesRepeatedlyPreservesDateAndMinutes() throws Exception {
        installInZone("Asia/Shanghai");
        for (String zone : new String[]{"Pacific/Honolulu", "Asia/Kathmandu", "Asia/Shanghai"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Date selected = WheelTime.dateFormat.parse("2026-10-12 00:15:00");
            assertEquals(zone, "2026-10-12 00:15", DateUtil.getDateToString(selected,
                    DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
        }
    }

    @Test public void unchangedZonePreservesSelection() throws Exception {
        installInZone("Pacific/Honolulu");
        Date selected = WheelTime.dateFormat.parse("2026-10-12 23:45:00");
        assertEquals("2026-10-12 23:45", DateUtil.getDateToString(selected,
                DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
    }

    private void installInZone(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        WheelTime.dateFormat = new TimePickerDateFormat();
    }
}
