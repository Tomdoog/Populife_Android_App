package com.populock.manhattan.sdk.util;
import org.junit.Test;
import static org.junit.Assert.*;

public class LockTimeZoneTest {
    @Test public void encodesSupportedOffsets() {
        assertEquals(0, LockTimeZone.encode(0));
        assertEquals(8, LockTimeZone.encode(28800000));
        assertEquals(19, LockTimeZone.encode(-25200000));
        assertEquals(23, LockTimeZone.encode(-39600000));
        assertEquals(12, LockTimeZone.encode(43200000));
    }
    @Test(expected = IllegalArgumentException.class)
    public void refusesHalfHourInsteadOfTruncating() { LockTimeZone.encode(19800000); }
    @Test(expected = IllegalArgumentException.class)
    public void refusesQuarterHourInsteadOfTruncating() { LockTimeZone.encode(20700000); }
    @Test(expected = IllegalArgumentException.class)
    public void refusesAmbiguousNegativeTwelve() { LockTimeZone.encode(-43200000); }
    @Test(expected = IllegalArgumentException.class)
    public void refusesPositiveOffsetOverProtocolRange() { LockTimeZone.encode(50400000); }
}
