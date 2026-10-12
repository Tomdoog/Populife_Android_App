package com.populstay.populife.net;

import com.populstay.populife.R;

/** 仅映射后端业务码；HTTP 状态码和蓝牙 SDK 错误不能传入此处。 */
public final class BackendError {
    private BackendError() {}

    public enum Credential { UNKNOWN, PASSCODE, FINGERPRINT, CARD }

    public enum Reason {
        AUTHORIZATION_EXPIRED(R.string.backend_error_authorization_expired),
        ADMIN_REQUIRED(R.string.backend_error_admin_required),
        DEVICE_NOT_CONNECTED_TO_GATEWAY(R.string.backend_error_gateway_not_connected),
        GATEWAY_UNAVAILABLE(R.string.backend_error_gateway_unavailable),
        REMOTE_UNLOCK_UNAVAILABLE(R.string.backend_error_remote_unlock_unavailable),
        PASSCODE_STORAGE_FULL(R.string.backend_error_passcode_full),
        FINGERPRINT_STORAGE_FULL(R.string.backend_error_fingerprint_full),
        CARD_STORAGE_FULL(R.string.backend_error_card_full),
        SERVICE_ERROR(R.string.backend_error_service),
        RANDOM_PASSCODE_PERIOD_DELETED(R.string.backend_error_period_deleted),
        GENERIC(R.string.backend_error_generic);

        public final int messageRes;

        Reason(int messageRes) {
            this.messageRes = messageRes;
        }
    }

    public static Reason reasonFor(int code, Credential credential) {
        switch (code) {
            case 10004: return Reason.AUTHORIZATION_EXPIRED;
            case 20002: return Reason.ADMIN_REQUIRED;
            case -2012: return Reason.DEVICE_NOT_CONNECTED_TO_GATEWAY;
            case -3037: return Reason.GATEWAY_UNAVAILABLE;
            case -4043: return Reason.REMOTE_UNLOCK_UNAVAILABLE;
            case -4056:
                if (credential == Credential.PASSCODE) return Reason.PASSCODE_STORAGE_FULL;
                if (credential == Credential.FINGERPRINT) return Reason.FINGERPRINT_STORAGE_FULL;
                if (credential == Credential.CARD) return Reason.CARD_STORAGE_FULL;
                return Reason.GENERIC;
            case 90000: return Reason.SERVICE_ERROR;
            case -1026: return Reason.RANDOM_PASSCODE_PERIOD_DELETED;
            case 1: return Reason.GENERIC;
            default: return null;
        }
    }

    public static int messageRes(int code, int fallbackRes, Credential credential) {
        Reason reason = reasonFor(code, credential);
        // 未纳入本次映射的业务码继续使用页面已有提示。
        return reason == null ? fallbackRes : reason.messageRes;
    }
}
