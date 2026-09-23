package com.populstay.populife.helper;

import org.junit.Test;

import static org.junit.Assert.*;

public class LockRecordResultTest {
    @Test public void acceptsExplicitBusinessSuccess() {
        assertTrue(LockRecordResult.parse("{\"code\":200,\"data\":null}").isSuccess());
        assertTrue(LockRecordResult.parse("{\"code\":\"200\"}").isSuccess());
    }

    @Test public void httpSuccessMustNotHideBusinessRejection() {
        LockRecordResult result = LockRecordResult.parse("{\"code\":403,\"message\":\"denied\"}");
        assertFalse(result.isSuccess());
        assertEquals("403", result.getCode());
        assertFalse(LockRecordResult.parse("{\"code\":920}").isSuccess());
        assertFalse(LockRecordResult.parse("{\"code\":0}").isSuccess());
    }

    @Test public void missingBusinessCodeDoesNotConfirmUpload() {
        for (String body : new String[]{null, "", "null", "{}", "{\"code\":null}",
                "{\"success\":true}", "{\"data\":{\"code\":200}}"}) {
            assertFalse(String.valueOf(body), LockRecordResult.parse(body).isSuccess());
        }
    }

    @Test public void malformedOrNonObjectResponsesFailWithoutCrashing() {
        for (String body : new String[]{"<html>Bad gateway</html>", "{", "[]", "200", "true"}) {
            assertFalse(body, LockRecordResult.parse(body).isSuccess());
        }
    }

    @Test public void wrongCodeTypesMustNotBeCoercedToSuccess() {
        for (String body : new String[]{"{\"code\":200.9}", "{\"code\":true}",
                "{\"code\":[200]}", "{\"code\":{\"value\":200}}", "{\"code\":4294967496}"}) {
            assertFalse(body, LockRecordResult.parse(body).isSuccess());
        }
    }

    @Test public void diagnosticCodeDoesNotEchoArbitraryServerContent() {
        LockRecordResult result = LockRecordResult.parse("{\"code\":\"private response content\"}");
        assertEquals("invalid_response", result.getCode());
    }
}
