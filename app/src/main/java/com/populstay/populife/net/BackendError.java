package com.populstay.populife.net;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;

/** 仅在后端 code=960 时映射 msg JSON 中的 errcode，保留外层业务码的原有处理。 */
public final class BackendError {
    private BackendError() {}

    public enum Credential { UNKNOWN, PASSCODE, FINGERPRINT, CARD }

    public enum Reason {
        GENERIC(R.string.backend_error_generic),
        CLIENT_NOT_FOUND(R.string.backend_error_client_not_found),
        INVALID_CLIENT(R.string.backend_error_invalid_client),
        AUTHORIZATION_MISSING(R.string.backend_error_authorization_missing),
        AUTHORIZATION_EXPIRED(R.string.backend_error_authorization_expired),
        AUTHORIZATION_REFRESH_INVALID(R.string.backend_error_authorization_refresh_invalid),
        INCORRECT_ACCOUNT_CREDENTIALS(R.string.backend_error_incorrect_account_credentials),
        ADMIN_REQUIRED(R.string.backend_error_admin_required),
        INVALID_USERNAME(R.string.backend_error_invalid_username),
        USER_ALREADY_EXISTS(R.string.backend_error_user_already_exists),
        USER_DELETION_NOT_ALLOWED(R.string.backend_error_user_deletion_not_allowed),
        INVALID_PASSWORD_ENCODING(R.string.backend_error_invalid_password_encoding),
        REQUEST_LIMIT_EXCEEDED(R.string.backend_error_request_limit_exceeded),
        SERVICE_REGION_MISMATCH(R.string.backend_error_service_region_mismatch),
        INVALID_REQUEST_TIME(R.string.backend_error_invalid_request_time),
        INVALID_REQUEST_FORMAT(R.string.backend_error_invalid_request_format),
        SERVICE_ERROR(R.string.backend_error_service),
        INVALID_PARAMETERS(R.string.backend_error_invalid_parameters),
        PERMISSION_DENIED(R.string.backend_error_permission_denied),
        ACCOUNT_HAS_LOCKS(R.string.backend_error_account_has_locks),
        LOCK_NOT_FOUND(R.string.backend_error_lock_not_found),
        LOCK_FROZEN(R.string.backend_error_lock_frozen),
        CANNOT_TRANSFER_LOCK_TO_SELF(R.string.backend_error_cannot_transfer_lock_to_self),
        OPERATION_UNSUPPORTED(R.string.backend_error_operation_unsupported),
        STORAGE_FULL(R.string.backend_error_storage_full),
        PASSCODE_STORAGE_FULL(R.string.backend_error_passcode_full),
        FINGERPRINT_STORAGE_FULL(R.string.backend_error_fingerprint_full),
        CARD_STORAGE_FULL(R.string.backend_error_card_full),
        NB_DEVICE_NOT_REGISTERED(R.string.backend_error_nb_device_not_registered),
        AUTO_LOCK_TIME_OUT_OF_RANGE(R.string.backend_error_auto_lock_time_out_of_range),
        KEY_NOT_FOUND(R.string.backend_error_key_not_found),
        GROUP_NAME_ALREADY_EXISTS(R.string.backend_error_group_name_already_exists),
        GROUP_NOT_FOUND(R.string.backend_error_group_not_found),
        KEY_RECIPIENT_ACCOUNT_LINKED(R.string.backend_error_key_recipient_account_linked),
        CANNOT_SEND_KEY_TO_SELF(R.string.backend_error_cannot_send_key_to_self),
        CANNOT_SEND_KEY_TO_ADMIN(R.string.backend_error_cannot_send_key_to_admin),
        KEY_PERIOD_CHANGE_NOT_ALLOWED(R.string.backend_error_key_period_change_not_allowed),
        KEY_RECIPIENT_NOT_REGISTERED(R.string.backend_error_key_recipient_not_registered),
        LOCK_PASSCODE_DATA_MISSING(R.string.backend_error_lock_passcode_data_missing),
        PASSCODE_NOT_FOUND_OR_INVALID(R.string.backend_error_passcode_not_found_or_invalid),
        INVALID_PASSCODE_LENGTH(R.string.backend_error_invalid_passcode_length),
        PASSCODE_ALREADY_EXISTS(R.string.backend_error_passcode_already_exists),
        UNUSED_PASSCODE_CANNOT_BE_CHANGED(R.string.backend_error_unused_passcode_cannot_be_changed),
        CUSTOM_PASSCODE_STORAGE_FULL(R.string.backend_error_custom_passcode_full),
        DEVICE_NOT_CONNECTED_TO_GATEWAY(R.string.backend_error_gateway_not_connected),
        GATEWAY_OFFLINE(R.string.backend_error_gateway_offline),
        GATEWAY_BUSY(R.string.backend_error_gateway_busy),
        CANNOT_TRANSFER_GATEWAY_TO_SELF(R.string.backend_error_cannot_transfer_gateway_to_self),
        WIFI_NOT_CONFIGURED(R.string.backend_error_wifi_not_configured),
        WIFI_POWER_SAVING_MODE(R.string.backend_error_wifi_power_saving_mode),
        LOCK_OFFLINE(R.string.backend_error_lock_offline),
        LOCK_BUSY(R.string.backend_error_lock_busy),
        GATEWAY_NOT_FOUND(R.string.backend_error_gateway_not_found),
        CARD_NOT_FOUND(R.string.backend_error_card_not_found),
        FINGERPRINT_NOT_FOUND(R.string.backend_error_fingerprint_not_found),
        RANDOM_PASSCODE_PERIOD_DELETED(R.string.backend_error_period_deleted);

        public final int messageRes;

        Reason(int messageRes) {
            this.messageRes = messageRes;
        }
    }

    public static Reason reasonFor(JSONObject response, Credential credential) {
        if (response == null || !Integer.valueOf(960).equals(response.get("code"))) {
            return null;
        }
        Object msg = response.get("msg");
        if (!(msg instanceof String)) return null;
        try {
            JSONObject error = JSON.parseObject((String) msg);
            if (error == null || !(error.get("errcode") instanceof Integer)) return null;
            return reasonForErrorCode(error.getInteger("errcode"), credential);
        } catch (RuntimeException malformedMessage) {
            return null;
        }
    }

    private static Reason reasonForErrorCode(int code, Credential credential) {
        switch (code) {
            case 1: return Reason.GENERIC;
            case 10000: return Reason.CLIENT_NOT_FOUND;
            case 10001: return Reason.INVALID_CLIENT;
            case 10003: return Reason.AUTHORIZATION_MISSING;
            case 10004: return Reason.AUTHORIZATION_EXPIRED;
            case 10011: return Reason.AUTHORIZATION_REFRESH_INVALID;
            case 10007: return Reason.INCORRECT_ACCOUNT_CREDENTIALS;
            case 20002: return Reason.ADMIN_REQUIRED;
            case 30002: return Reason.INVALID_USERNAME;
            case 30003: return Reason.USER_ALREADY_EXISTS;
            case 30004: return Reason.USER_DELETION_NOT_ALLOWED;
            case 30005: return Reason.INVALID_PASSWORD_ENCODING;
            case 30006: return Reason.REQUEST_LIMIT_EXCEEDED;
            case 30008: return Reason.SERVICE_REGION_MISMATCH;
            case 80000: return Reason.INVALID_REQUEST_TIME;
            case 80002: return Reason.INVALID_REQUEST_FORMAT;
            case 90000: return Reason.SERVICE_ERROR;
            case -3: return Reason.INVALID_PARAMETERS;
            case -2018: return Reason.PERMISSION_DENIED;
            case -4063: return Reason.ACCOUNT_HAS_LOCKS;
            case -1003: return Reason.LOCK_NOT_FOUND;
            case -2025: return Reason.LOCK_FROZEN;
            case -3011: return Reason.CANNOT_TRANSFER_LOCK_TO_SELF;
            case -4043: return Reason.OPERATION_UNSUPPORTED;
            case -4056:
                if (credential == Credential.PASSCODE) return Reason.PASSCODE_STORAGE_FULL;
                if (credential == Credential.FINGERPRINT) return Reason.FINGERPRINT_STORAGE_FULL;
                if (credential == Credential.CARD) return Reason.CARD_STORAGE_FULL;
                return Reason.STORAGE_FULL;
            case -4067: return Reason.NB_DEVICE_NOT_REGISTERED;
            case -4082: return Reason.AUTO_LOCK_TIME_OUT_OF_RANGE;
            case -1008: return Reason.KEY_NOT_FOUND;
            case -1016: return Reason.GROUP_NAME_ALREADY_EXISTS;
            case -1018: return Reason.GROUP_NOT_FOUND;
            case -1027: return Reason.KEY_RECIPIENT_ACCOUNT_LINKED;
            case -2019: return Reason.CANNOT_SEND_KEY_TO_SELF;
            case -2020: return Reason.CANNOT_SEND_KEY_TO_ADMIN;
            case -2023: return Reason.KEY_PERIOD_CHANGE_NOT_ALLOWED;
            case -4064: return Reason.KEY_RECIPIENT_NOT_REGISTERED;
            case -1007: return Reason.LOCK_PASSCODE_DATA_MISSING;
            case -2009: return Reason.PASSCODE_NOT_FOUND_OR_INVALID;
            case -3006: return Reason.INVALID_PASSCODE_LENGTH;
            case -3007: return Reason.PASSCODE_ALREADY_EXISTS;
            case -3008: return Reason.UNUSED_PASSCODE_CANNOT_BE_CHANGED;
            case -3009: return Reason.CUSTOM_PASSCODE_STORAGE_FULL;
            case -2012: return Reason.DEVICE_NOT_CONNECTED_TO_GATEWAY;
            case -3002: return Reason.GATEWAY_OFFLINE;
            case -3003: return Reason.GATEWAY_BUSY;
            case -3016: return Reason.CANNOT_TRANSFER_GATEWAY_TO_SELF;
            case -3034: return Reason.WIFI_NOT_CONFIGURED;
            case -3035: return Reason.WIFI_POWER_SAVING_MODE;
            case -3036: return Reason.LOCK_OFFLINE;
            case -3037: return Reason.LOCK_BUSY;
            case -4037: return Reason.GATEWAY_NOT_FOUND;
            case -1021: return Reason.CARD_NOT_FOUND;
            case -1023: return Reason.FINGERPRINT_NOT_FOUND;
            case -1026: return Reason.RANDOM_PASSCODE_PERIOD_DELETED;
            default: return null;
        }
    }

    public static int messageRes(JSONObject response, int fallbackRes, Credential credential) {
        Reason reason = reasonFor(response, credential);
        // 未知错误码继续使用页面已有提示。
        return reason == null ? fallbackRes : reason.messageRes;
    }
}
