package com.populstay.populife.helper;

/** 先持久化再发送。所有状态由同一个后台执行器访问；失败不会删除记录。 */
public final class LockRecordOutbox {
    public static final class Entry {
        public String id, userId, server, endpoint, payload;
        public long createdAt, nextAttemptAt;
        public int attempts;
    }

    public interface Store {
        void insert(Entry entry);
        Entry next(String userId, String server);
        void delete(String id);
        void defer(String id, int attempts, long nextAttemptAt);
    }

    public interface Environment {
        String userId();
        String server();
        boolean online();
        long now();
    }

    public interface Runner {
        void execute(Runnable action);
        void wakeAfter(Runnable action, long delayMillis);
    }

    public interface Completion { void complete(boolean success); }
    public interface Transport { void send(Entry entry, Completion completion); }
    public interface Callback { void complete(boolean uploaded, boolean saved); }

    private final Store store;
    private final Environment environment;
    private final Runner runner;
    private final Transport transport;
    private Entry inFlight;
    private Callback immediateCallback;
    private long requestSequence;

    public LockRecordOutbox(Store store, Environment environment, Runner runner, Transport transport) {
        this.store = store;
        this.environment = environment;
        this.runner = runner;
        this.transport = transport;
    }

    public void enqueue(Entry entry, Callback callback) {
        runner.execute(() -> {
            if (entry.userId == null || entry.userId.trim().isEmpty()) {
                callback.complete(false, false);
                return;
            }
            try {
                store.insert(entry);
            } catch (RuntimeException error) {
                callback.complete(false, false);
                return;
            }
            // 已有请求时直接告知“已保存待补传”，不持有页面等待整个队列。
            if (inFlight != null || !canSend(entry)) {
                callback.complete(false, true);
                pump();
                return;
            }
            send(entry, callback);
        });
    }

    public void retry() { runner.execute(this::pump); }

    private boolean canSend(Entry entry) {
        return environment.online() && entry.userId.equals(environment.userId())
                && entry.server.equals(environment.server());
    }

    private void pump() {
        if (inFlight != null || !environment.online()) return;
        String userId = environment.userId();
        if (userId == null || userId.trim().isEmpty()) return;
        try {
            Entry next = store.next(userId, environment.server());
            if (next == null) return;
            long delay = next.nextAttemptAt - environment.now();
            if (delay > 0) {
                runner.wakeAfter(this::pump, delay);
            } else if (canSend(next)) {
                send(next, null);
            }
        } catch (RuntimeException error) {
            // 存储暂不可用时保留原数据，避免忙循环。
            runner.wakeAfter(this::pump, 30_000);
        }
    }

    private void send(Entry entry, Callback callback) {
        inFlight = entry;
        immediateCallback = callback;
        long sequence = ++requestSequence;
        try {
            transport.send(entry, success -> runner.execute(() -> finish(entry, sequence, success)));
        } catch (RuntimeException error) {
            finish(entry, sequence, false);
        }
    }

    private void finish(Entry entry, long sequence, boolean success) {
        if (inFlight != entry || requestSequence != sequence) return; // 忽略旧尝试的迟到回调。
        Callback callback = immediateCallback;
        inFlight = null;
        immediateCallback = null;
        boolean storageAvailable = true;
        try {
            if (success) {
                store.delete(entry.id);
            } else {
                int attempts = Math.min(entry.attempts + 1, 31);
                store.defer(entry.id, attempts, environment.now() + retryDelay(attempts));
            }
        } catch (RuntimeException error) {
            storageAvailable = false;
        }
        if (callback != null) callback.complete(success, true);
        if (storageAvailable) pump();
        else runner.wakeAfter(this::pump, 30_000);
    }

    static long retryDelay(int attempts) {
        return Math.min(30_000L << Math.min(Math.max(attempts - 1, 0), 6), 30 * 60_000L);
    }
}
