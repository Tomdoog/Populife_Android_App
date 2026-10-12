package com.populstay.populife.net;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.net.BackendError.Reason;
import org.junit.Test;

import static com.populstay.populife.net.BackendError.Credential.*;
import static com.populstay.populife.net.BackendError.Reason.*;
import static org.junit.Assert.*;

public class BackendErrorTest {
    @Test public void confirmedNestedCodesHaveSpecificBusinessReasons() {
        assertEquals(AUTHORIZATION_EXPIRED, BackendError.reasonFor(wrappedError(10004), UNKNOWN));
        assertEquals(ADMIN_REQUIRED, BackendError.reasonFor(wrappedError(20002), UNKNOWN));
        assertEquals(DEVICE_NOT_CONNECTED_TO_GATEWAY, BackendError.reasonFor(wrappedError(-2012), UNKNOWN));
        assertEquals(LOCK_BUSY, BackendError.reasonFor(wrappedError(-3037), UNKNOWN));
        assertEquals(OPERATION_UNSUPPORTED, BackendError.reasonFor(wrappedError(-4043), UNKNOWN));
        assertEquals(SERVICE_ERROR, BackendError.reasonFor(wrappedError(90000), UNKNOWN));
        assertEquals(RANDOM_PASSCODE_PERIOD_DELETED, BackendError.reasonFor(wrappedError(-1026), UNKNOWN));
        assertEquals(GENERIC, BackendError.reasonFor(wrappedError(1), UNKNOWN));
    }

    @Test public void actualBackendResponseMapsInvalidParameters() {
        JSONObject response = JSON.parseObject("{\"success\":false,\"code\":960,"
                + "\"msg\":\"{\\\"errcode\\\":-3,\\\"errmsg\\\":\\\"Invalid Parameter.\\\","
                + "\\\"description\\\":\\\"The end time should be after the beginning time.\\\"}\","
                + "\"data\":null}");
        assertEquals(INVALID_PARAMETERS, BackendError.reasonFor(response, PASSCODE));
        assertEquals(R.string.backend_error_invalid_parameters,
                BackendError.messageRes(response, R.string.operation_fail, PASSCODE));
        assertEquals(Integer.valueOf(960), response.getInteger("code"));
        assertFalse(response.getBooleanValue("success"));
    }

    @Test public void fullStorageUsesTheCredentialBeingAdded() {
        JSONObject response = wrappedError(-4056);
        assertEquals(PASSCODE_STORAGE_FULL, BackendError.reasonFor(response, PASSCODE));
        assertEquals(FINGERPRINT_STORAGE_FULL, BackendError.reasonFor(response, FINGERPRINT));
        assertEquals(CARD_STORAGE_FULL, BackendError.reasonFor(response, CARD));
        assertEquals(STORAGE_FULL, BackendError.reasonFor(response, UNKNOWN));
    }

    @Test public void knownNestedReasonOverridesGenericPageFailure() {
        assertEquals(R.string.backend_error_operation_unsupported,
                BackendError.messageRes(wrappedError(-4043), R.string.operation_fail, UNKNOWN));
        assertEquals(R.string.backend_error_fingerprint_full,
                BackendError.messageRes(wrappedError(-4056), R.string.operation_fail, FINGERPRINT));
    }

    @Test public void non960ResponsesKeepExistingPageMappings() {
        for (int code : new int[]{200, 951, 500, 10004, -2012, 1, 30003, -3002}) {
            JSONObject response = wrappedError(-2012);
            response.put("code", code);
            assertNull(BackendError.reasonFor(response, UNKNOWN));
            assertEquals(R.string.note_gateway_donot_exists,
                    BackendError.messageRes(response, R.string.note_gateway_donot_exists, UNKNOWN));
        }
    }

    @Test public void everyOfficialErrorCodeHasAMapping() {
        // 科技侠中英文官方错误码表（2026-10-12），不包含成功码 0。
        int[] codes = {1, 10000, 10001, 10003, 10004, 10007, 10011, 20002,
                30002, 30003, 30004, 30005, 30006, 30008, 80000, 80002, 90000,
                -3, -2018, -4063, -1003, -2025, -3011, -4043, -4056, -4067,
                -4082, -1008, -1016, -1018, -1027, -2019, -2020, -2023, -4064,
                -1007, -2009, -3006, -3007, -3008, -3009, -2012, -3002, -3003,
                -3016, -3034, -3035, -3036, -3037, -4037, -1021, -1023};
        for (int code : codes) {
            Reason reason = BackendError.reasonFor(wrappedError(code), UNKNOWN);
            assertNotNull("Missing error code: " + code, reason);
            if (code != 1) {
                assertNotEquals("Specific reason required: " + code, GENERIC, reason);
            }
        }
        assertNull(BackendError.reasonFor(wrappedError(0), UNKNOWN));
    }

    @Test public void similarFailuresKeepTheirActualObjectAndCause() {
        assertEquals(LOCK_BUSY, BackendError.reasonFor(wrappedError(-3037), UNKNOWN));
        assertEquals(GATEWAY_BUSY, BackendError.reasonFor(wrappedError(-3003), UNKNOWN));
        assertEquals(LOCK_OFFLINE, BackendError.reasonFor(wrappedError(-3036), UNKNOWN));
        assertEquals(GATEWAY_OFFLINE, BackendError.reasonFor(wrappedError(-3002), UNKNOWN));
        assertEquals(INCORRECT_ACCOUNT_CREDENTIALS,
                BackendError.reasonFor(wrappedError(10007), UNKNOWN));
        assertEquals(PASSCODE_NOT_FOUND_OR_INVALID,
                BackendError.reasonFor(wrappedError(-2009), UNKNOWN));
        assertEquals(PERMISSION_DENIED, BackendError.reasonFor(wrappedError(-2018), UNKNOWN));
        assertEquals(ADMIN_REQUIRED, BackendError.reasonFor(wrappedError(20002), UNKNOWN));
    }

    @Test public void missingMalformedAndUnknownNestedErrorsUseFallback() {
        assertNull(BackendError.reasonFor(null, UNKNOWN));
        assertNull(BackendError.reasonFor(new JSONObject(), UNKNOWN));
        for (String msg : new String[]{null, "", "not json", "null", "[]", "{}",
                "{\"errcode\":null}", "{\"errcode\":true}", "{\"errcode\":-3.5}",
                "{\"errcode\":987654}", "{\"errmsg\":\"Invalid Parameter.\"}"}) {
            JSONObject response = wrappedError(-2012);
            response.put("msg", msg);
            assertEquals(msg, R.string.operation_fail,
                    BackendError.messageRes(response, R.string.operation_fail, UNKNOWN));
        }
    }

    @Test public void mappingUsesErrorCodeInsteadOfTechnicalText() {
        JSONObject response = wrappedError(-3);
        response.put("msg", "{\"errcode\":-3,\"errmsg\":\"Other text\",\"description\":\"其他参数错误\"}");
        assertEquals(INVALID_PARAMETERS, BackendError.reasonFor(response, UNKNOWN));
    }

    private static JSONObject wrappedError(int errorCode) {
        JSONObject detail = new JSONObject();
        detail.put("errcode", errorCode);
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("code", 960);
        response.put("msg", detail.toJSONString());
        return response;
    }
}
