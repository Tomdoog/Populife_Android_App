package com.populstay.populife.helper;

import android.os.SystemClock;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.util.log.PeachLogger;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populock.manhattan.sdk.service.BleService;
import com.ttlock.bl.sdk.callback.GetLockTimeCallback;
import com.ttlock.bl.sdk.callback.SetLockTimeCallback;
import com.ttlock.bl.sdk.entity.LockError;
import java.util.concurrent.atomic.AtomicBoolean;

/** 初始化凭据先落盘，再写时间、读回验证；失败保留原绑定时区供下次重试。 */
public final class BindingTimeSync {
    public interface Callback { void onDone(boolean success); }
    private static BindingTimeSync pendingMht;
    private final OfflineLock lock;
    private final JSONObject info;
    private final Callback callback;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final int offset;
    private long writeTime;
    private long writeElapsed;
    private final Runnable timeout = () -> finish(false, 0);

    private BindingTimeSync(OfflineLock lock, Callback callback) {
        this.lock = lock;
        this.info = JSON.parseObject(lock.getLockDataJson());
        this.offset = info.getIntValue("timezoneRawOffset");
        this.callback = callback;
    }

    public static boolean verified(OfflineLock lock) {
        return BindingTimeEvidence.verified(lock.getLockDataJson());
    }

    public static void sync(OfflineLock lock, Callback callback) {
        BaseApplication.getHandler().post(() -> {
            BindingTimeSync task;
            try { task = new BindingTimeSync(lock, callback); }
            catch (RuntimeException e) { callback.onDone(false); return; }
            try { task.start(); }
            catch (RuntimeException e) { task.finish(false, 0); }
        });
    }

    private boolean save() {
        lock.setLockDataJson(info.toJSONString());
        return PopulifeDBUtil.getInstance(BaseApplication.getApplication()).save(lock);
    }

    private void start() {
        // 旧待同步数据缺少偏移时，不得以手机当前时区冒充锁时区。
        if (!info.containsKey("timezoneRawOffset") || info.get("timezoneRawOffset") == null
                || info.getLongValue("timestamp") <= 0) {
            finish(false, 0);
            return;
        }
        if (verified(lock)) { finish(true, info.getLongValue("verifiedLockTime")); return; }
        info.put("bindingTimeVerified", false);
        if (!save()) { finish(false, 0); return; }
        BaseApplication.getHandler().postDelayed(timeout, 30000);
        if (lock.getLockType() < 0) {
            try { com.populock.manhattan.sdk.util.LockTimeZone.encode(offset); }
            catch (IllegalArgumentException e) { finish(false, 0); return; }
            pendingMht = this;
            MyApplication.pplBleSession.setOperation(LockOperation.INIT_LOCK_TIME);
            if (MyApplication.sPPLOCK.isConnected(lock.getLockMac())) writeMht();
            else MyApplication.sPPLOCK.connect(lock.getLockMac());
        } else {
            writeTt();
        }
    }

    private void beginWrite() {
        writeTime = System.currentTimeMillis();
        writeElapsed = SystemClock.elapsedRealtime();
        info.put("phoneTimeAtSync", writeTime);
        PeachLogger.d("BindingTimeSync phone timestamp=" + writeTime + " offsetMillis=" + offset);
    }

    private void writeTt() {
        com.ttlock.bl.sdk.entity.LockData data = com.ttlock.bl.sdk.util.EncryptionUtil
                .parseLockData(info.getString("lockData"));
        if (data == null) { finish(false, 0); return; }
        data.setTimezoneRawOffset(offset);
        final String encoded = data.encodeLockData();
        info.put("lockData", encoded);
        if (!save()) { finish(false, 0); return; }
        MyApplication.mTTLockAPI.connectLock(encoded, new com.ttlock.bl.sdk.callback.ConnectLockCallback() {
            @Override public void onConnectSuccess() {
                if (finished.get()) return;
                // SDK 3.5.6 的已连接分支直接回调成功，却保留连接命令，
                // 后续校时会被判为操作繁忙。连接已完成，释放本次回调后再发命令。
                MyApplication.mTTLockAPI.clearAllCallback();
                BaseApplication.getHandler().post(() -> {
                    if (!finished.get()) writeConnectedTt(encoded);
                });
            }
            @Override public void onFail(LockError error) { failTt("connect", error); }
        });
    }

    private void writeConnectedTt(String encoded) {
        beginWrite();
        MyApplication.mTTLockAPI.setLockTime(writeTime, encoded, lock.getLockMac(), new SetLockTimeCallback() {
            @Override public void onSetTimeSuccess() {
                if (finished.get()) return;
                MyApplication.mTTLockAPI.getLockTime(encoded, lock.getLockMac(), new GetLockTimeCallback() {
                    @Override public void onGetLockTimeSuccess(long time) { verify(time); }
                    @Override public void onFail(LockError error) { failTt("read", error); }
                });
            }
            @Override public void onFail(LockError error) { failTt("write", error); }
        });
    }

    private void failTt(String stage, LockError error) {
        PeachLogger.d("BindingTimeSync failed stage=" + stage + " error="
                + (error == null ? "unknown" : error.name()));
        finish(false, 0);
    }

    public static void onMhtConnected(String mac) {
        BindingTimeSync task = pendingMht;
        if (task != null && task.lock.getLockMac().equalsIgnoreCase(mac) && !task.finished.get()) {
            task.writeMht();
        }
    }

    private void writeMht() {
        beginWrite();
        BleService.sLockBleSession.setSetLockTimeCallback(new com.populock.manhattan.sdk.callback.SetLockTimeCallback() {
            @Override public void onSuccess() {
                if (finished.get()) return;
                BleService.sLockBleSession.setGetLockTimeCallback(new com.populock.manhattan.sdk.callback.GetLockTimeCallback() {
                    @Override public void onSuccess(long time) { verify(time); }
                    @Override public void onFail(com.populock.manhattan.sdk.entity.LockError error) { finish(false, 0); }
                });
                BleService.getBleService().getLockTime(offset);
            }
            @Override public void onFail(com.populock.manhattan.sdk.entity.LockError error) { finish(false, 0); }
        });
        BleService.getBleService().setLockTime(writeTime, offset);
    }

    private void verify(long time) {
        finish(BindingTimeEvidence.clockMatches(writeTime,
                SystemClock.elapsedRealtime() - writeElapsed, time), time);
    }

    private void finish(boolean success, long readTime) {
        BaseApplication.getHandler().post(() -> {
            if (!finished.compareAndSet(false, true)) return;
            BaseApplication.getHandler().removeCallbacks(timeout);
            if (!success && lock.getLockType() >= 0) MyApplication.mTTLockAPI.clearAllCallback();
            if (pendingMht == this) {
                pendingMht = null;
                MyApplication.sPPLOCK.disconnect();
            }
            boolean saved = success;
            if (success) {
                info.put("bindingTimeVerified", true);
                info.put("verifiedLockTime", readTime);
                saved = save();
                if (!saved) {
                    info.put("bindingTimeVerified", false);
                    lock.setLockDataJson(info.toJSONString());
                }
            }
            PeachLogger.d("BindingTimeSync lock verified=" + saved + " readTimestamp=" + readTime
                    + " offsetMillis=" + offset);
            callback.onDone(saved);
        });
    }
}
