package com.populstay.populife.helper;
import com.alibaba.fastjson.JSON;
import org.junit.Test;
import static org.junit.Assert.*;

public class BindingTimeEvidenceTest {
    @Test public void credentialsAloneNeverCompleteTimeInitialization() {
        assertFalse(BindingTimeEvidence.verified("{\"timestamp\":1791424800000,\"timezoneRawOffset\":28800000}"));
        assertFalse(BindingTimeEvidence.verified("{\"bindingTimeVerified\":false}"));
        assertFalse(BindingTimeEvidence.verified("{\"bindingTimeVerified\":true}"));
        assertFalse(BindingTimeEvidence.verified("null"));
    }
    @Test public void persistedReadbackSupportsRetryAfterRestart() {
        assertTrue(BindingTimeEvidence.verified("{\"bindingTimeVerified\":true,\"timezoneRawOffset\":0,\"verifiedLockTime\":1791424800000}"));
    }
    @Test public void readbackAllowsBluetoothDelayButRejectsWrongTime() {
        long phone = 1791424800000L;
        assertTrue(BindingTimeEvidence.clockMatches(phone, 2400, phone + 2000));
        assertFalse(BindingTimeEvidence.clockMatches(phone, 2400, phone + 3600000));
        assertFalse(BindingTimeEvidence.clockMatches(phone, 10000, phone));
        assertFalse(BindingTimeEvidence.clockMatches(phone, 2000, 0));
    }
    @Test public void serverMustReturnPersistedOffsetNotJustHttpSuccess() {
        assertFalse(BindingTimeEvidence.serverMatches(JSON.parseObject("{\"timezoneRawOffset\":28800000}"), JSON.parseObject("{\"code\":200}")));
        assertFalse(BindingTimeEvidence.serverMatches(JSON.parseObject("{\"timezoneRawOffset\":28800000}"), JSON.parseObject("{\"timezoneRawOffSet\":0}")));
        assertTrue(BindingTimeEvidence.serverMatches(JSON.parseObject("{\"timezoneRawOffset\":28800000}"), JSON.parseObject("{\"timezoneRawOffSet\":28800000}")));
    }
    @Test public void utcIsValidButMissingOffsetIsNotUtc() {
        assertTrue(BindingTimeEvidence.serverMatches(JSON.parseObject("{\"timezoneRawOffset\":0}"), JSON.parseObject("{\"timezoneRawOffset\":0}")));
        assertFalse(BindingTimeEvidence.serverMatches(JSON.parseObject("{}"), JSON.parseObject("{\"timezoneRawOffset\":0}")));
    }
}
