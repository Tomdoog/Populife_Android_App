package com.populstay.populife.helper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.RestClientBuilder;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.net.NetworkUtil;
import com.populstay.populife.util.storage.PeachPreference;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** App 操作及 SDK 原始记录统一走持久化队列；自动补传静默进行。 */
public final class LockRecordUploader {
    private static final class Holder {
        static final LockRecordUploader INSTANCE = new LockRecordUploader();
    }

    public static LockRecordUploader getInstance() { return Holder.INSTANCE; }

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> wakeup;
    private final LockRecordOutbox outbox;

    private LockRecordUploader() {
        outbox = new LockRecordOutbox(new LockRecordStore(BaseApplication.getApplication()),
                new LockRecordOutbox.Environment() {
                    public String userId() { return PeachPreference.readUserId(); }
                    public String server() { return Urls.BASE_URL; }
                    public boolean online() { return NetworkUtil.isNetConnected(); }
                    public long now() { return System.currentTimeMillis(); }
                }, new LockRecordOutbox.Runner() {
                    public void execute(Runnable action) { worker.execute(action); }
                    public void wakeAfter(Runnable action, long delayMillis) {
                        if (wakeup != null) wakeup.cancel(false);
                        wakeup = worker.schedule(action, delayMillis, TimeUnit.MILLISECONDS);
                    }
                }, this::send);
    }

    public void retry() { outbox.retry(); }

    public void uploadAppEvent(int lockId, int keyId, int event, LockRecordOutbox.Callback callback) {
        JSONObject params = new JSONObject();
        params.put("keyId", keyId);
        params.put("event", event);
        enqueue(Urls.LOCK_OPERATE_APP_LOG_ADD, lockId, params, callback);
    }

    public void uploadRecords(int lockId, String records, LockRecordOutbox.Callback callback) {
        JSONObject params = new JSONObject();
        params.put("records", records);
        enqueue(Urls.LOCK_OPERATE_LOG_KEYBOARD_ADD, lockId, params, callback);
    }

    private void enqueue(String endpoint, int lockId, JSONObject params, LockRecordOutbox.Callback callback) {
        LockRecordOutbox.Entry entry = new LockRecordOutbox.Entry();
        entry.id = UUID.randomUUID().toString();
        entry.userId = PeachPreference.readUserId();
        entry.server = Urls.BASE_URL;
        entry.endpoint = endpoint;
        entry.createdAt = System.currentTimeMillis();
        params.put("userId", entry.userId);
        params.put("lockId", lockId);
        entry.payload = params.toJSONString();
        outbox.enqueue(entry, (uploaded, saved) -> BaseApplication.getHandler().post(() -> {
            // 切换账号后不向新账号的页面发送旧操作提示。
            if (entry.userId != null && entry.userId.equals(PeachPreference.readUserId())) {
                callback.complete(uploaded, saved);
            }
        }));
    }

    private void send(LockRecordOutbox.Entry entry, LockRecordOutbox.Completion completion) {
        // 和账号切换同在主线程校验，重试始终重放原参数，不读取 CURRENT_KEY。
        BaseApplication.getHandler().post(() -> {
            if (!entry.userId.equals(PeachPreference.readUserId()) || !entry.server.equals(Urls.BASE_URL)) {
                completion.complete(false);
                return;
            }
            try {
                JSONObject payload = JSON.parseObject(entry.payload);
                RestClientBuilder request = RestClient.builder().url(entry.endpoint)
                        .params("userId", entry.userId).params("lockId", payload.getInteger("lockId"));
                // 使用固定参数名，避免 RestClient 的 WeakHashMap 回收动态 JSON 键。
                if (Urls.LOCK_OPERATE_APP_LOG_ADD.equals(entry.endpoint)) {
                    request.params("keyId", payload.getInteger("keyId")).params("event", payload.getInteger("event"));
                } else if (Urls.LOCK_OPERATE_LOG_KEYBOARD_ADD.equals(entry.endpoint)) {
                    request.params("records", payload.getString("records"));
                } else {
                    completion.complete(false);
                    return;
                }
                request
                        .success(response -> {
                            LockRecordResult result = LockRecordResult.parse(response);
                            PeachLogger.d("[RECORD_OUTBOX] id=" + entry.id + " code=" + result.getCode());
                            completion.complete(result.isSuccess());
                        })
                        .failure(() -> {
                            PeachLogger.w("RECORD_OUTBOX", "id=" + entry.id + " network failure");
                            completion.complete(false);
                        })
                        .error((code, message) -> {
                            PeachLogger.w("RECORD_OUTBOX", "id=" + entry.id + " HTTP=" + code);
                            completion.complete(false);
                        })
                        .build().post();
            } catch (RuntimeException error) {
                completion.complete(false);
            }
        });
    }
}
