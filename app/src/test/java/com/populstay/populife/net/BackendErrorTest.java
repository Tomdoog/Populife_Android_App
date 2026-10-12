package com.populstay.populife.net;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import org.junit.Test;

import static com.populstay.populife.net.BackendError.Credential.*;
import static com.populstay.populife.net.BackendError.Reason.*;
import static org.junit.Assert.*;

public class BackendErrorTest {
    @Test public void confirmedNestedCodesHaveSpecificBusinessReasons() {
        assertEquals(AUTHORIZATION_EXPIRED, BackendError.reasonFor(wrappedError(10004), UNKNOWN));
        assertEquals(ADMIN_REQUIRED, BackendError.reasonFor(wrappedError(20002), UNKNOWN));
        assertEquals(DEVICE_NOT_CONNECTED_TO_GATEWAY, BackendError.reasonFor(wrappedError(-2012), UNKNOWN));
        assertEquals(GATEWAY_UNAVAILABLE, BackendError.reasonFor(wrappedError(-3037), UNKNOWN));
        assertEquals(REMOTE_UNLOCK_UNAVAILABLE, BackendError.reasonFor(wrappedError(-4043), UNKNOWN));
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
        assertEquals(GENERIC, BackendError.reasonFor(response, UNKNOWN));
    }

    @Test public void knownNestedReasonOverridesGenericPageFailure() {
        assertEquals(R.string.backend_error_remote_unlock_unavailable,
                BackendError.messageRes(wrappedError(-4043), R.string.operation_fail, UNKNOWN));
        assertEquals(R.string.backend_error_fingerprint_full,
                BackendError.messageRes(wrappedError(-4056), R.string.operation_fail, FINGERPRINT));
    }

    @Test public void non960ResponsesKeepExistingPageMappings() {
        for (int code : new int[]{200, 951, 500, 10004, -2012, 1}) {
            JSONObject response = wrappedError(-2012);
            response.put("code", code);
            assertNull(BackendError.reasonFor(response, UNKNOWN));
            assertEquals(R.string.note_gateway_donot_exists,
                    BackendError.messageRes(response, R.string.note_gateway_donot_exists, UNKNOWN));
        }
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
