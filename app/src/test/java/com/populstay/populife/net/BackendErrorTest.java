package com.populstay.populife.net;

import com.populstay.populife.R;
import org.junit.Test;

import static com.populstay.populife.net.BackendError.Credential.*;
import static com.populstay.populife.net.BackendError.Reason.*;
import static org.junit.Assert.*;

public class BackendErrorTest {
    @Test public void confirmedCodesHaveSpecificBusinessReasons() {
        assertEquals(AUTHORIZATION_EXPIRED, BackendError.reasonFor(10004, UNKNOWN));
        assertEquals(ADMIN_REQUIRED, BackendError.reasonFor(20002, UNKNOWN));
        assertEquals(DEVICE_NOT_CONNECTED_TO_GATEWAY, BackendError.reasonFor(-2012, UNKNOWN));
        assertEquals(GATEWAY_UNAVAILABLE, BackendError.reasonFor(-3037, UNKNOWN));
        assertEquals(REMOTE_UNLOCK_UNAVAILABLE, BackendError.reasonFor(-4043, UNKNOWN));
        assertEquals(SERVICE_ERROR, BackendError.reasonFor(90000, UNKNOWN));
        assertEquals(RANDOM_PASSCODE_PERIOD_DELETED, BackendError.reasonFor(-1026, UNKNOWN));
        assertEquals(GENERIC, BackendError.reasonFor(1, UNKNOWN));
    }

    @Test public void fullStorageUsesTheCredentialBeingAdded() {
        assertEquals(PASSCODE_STORAGE_FULL, BackendError.reasonFor(-4056, PASSCODE));
        assertEquals(FINGERPRINT_STORAGE_FULL, BackendError.reasonFor(-4056, FINGERPRINT));
        assertEquals(CARD_STORAGE_FULL, BackendError.reasonFor(-4056, CARD));
        assertEquals(GENERIC, BackendError.reasonFor(-4056, UNKNOWN));
    }

    @Test public void knownBusinessReasonOverridesGenericPageFailure() {
        assertEquals(R.string.backend_error_remote_unlock_unavailable,
                BackendError.messageRes(-4043, R.string.operation_fail, UNKNOWN));
        assertEquals(R.string.backend_error_fingerprint_full,
                BackendError.messageRes(-4056, R.string.operation_fail, FINGERPRINT));
    }

    @Test public void otherCodesKeepExistingPageMappings() {
        assertNull(BackendError.reasonFor(951, UNKNOWN));
        assertEquals(R.string.note_gateway_donot_exists,
                BackendError.messageRes(951, R.string.note_gateway_donot_exists, UNKNOWN));
        assertEquals(R.string.operation_fail,
                BackendError.messageRes(987654, R.string.operation_fail, UNKNOWN));
        assertNull(BackendError.reasonFor(200, UNKNOWN));
    }
}
