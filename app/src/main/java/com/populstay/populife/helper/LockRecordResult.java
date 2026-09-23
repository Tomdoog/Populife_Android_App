package com.populstay.populife.helper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/** HTTP 成功不代表记录已入库；只有业务 code=200 才允许进入同步成功流程。 */
public final class LockRecordResult {
    private final String code;

    private LockRecordResult(String code) {
        this.code = code;
    }

    public static LockRecordResult parse(String response) {
        try {
            JSONObject body = JSON.parseObject(response);
            Object value = body == null ? null : body.get("code");
            if (value instanceof Number || value instanceof String) {
                String code = value.toString();
                if (code.matches("-?[0-9]{1,10}")) {
                    return new LockRecordResult(code);
                }
            }
        } catch (RuntimeException ignored) {
            // 空响应、HTML 或无效 JSON 均不能当成上传成功。
        }
        return new LockRecordResult("invalid_response");
    }

    public boolean isSuccess() {
        return "200".equals(code);
    }

    public String getCode() {
        return code;
    }
}
