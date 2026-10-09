package com.populstay.populife.helper;

/** 服务端修改、详情核验和实体锁校时全部完成后，才能报告时区同步成功。 */
public final class LockTimezoneCalibration {
    public interface Callback<T> {
        void success(T value);
        void failure();
    }

    public static final class State {
        public final int lockId;
        public final Integer offset;
        public final String lockData;

        public State(int lockId, Integer offset, String lockData) {
            this.lockId = lockId;
            this.offset = offset;
            this.lockData = lockData;
        }
    }

    public interface Actions {
        void updateServer(Callback<Void> callback);
        void loadServer(Callback<State> callback);
        void syncAndVerifyClock(State state, Callback<Long> callback);
    }

    public interface Result {
        void success(State state, long lockTime);
        void failure();
    }

    private final int lockId;
    private final int offset;
    private final Actions actions;
    private final Result result;
    private int stage;

    public LockTimezoneCalibration(int lockId, int offset, Actions actions, Result result) {
        this.lockId = lockId;
        this.offset = offset;
        this.actions = actions;
        this.result = result;
    }

    public boolean isRunning() { return stage > 0 && stage < 4; }

    public void start() {
        if (stage != 0) return;
        stage = 1;
        actions.updateServer(new Callback<Void>() {
            public void success(Void ignored) {
                if (stage != 1) return;
                stage = 2;
                actions.loadServer(new Callback<State>() {
                    public void success(State state) {
                        if (stage != 2) return;
                        if (state == null || state.lockId != lockId || state.offset == null
                                || state.offset != offset || state.lockData == null
                                || state.lockData.trim().isEmpty()) {
                            fail();
                            return;
                        }
                        stage = 3;
                        actions.syncAndVerifyClock(state, new Callback<Long>() {
                            public void success(Long time) {
                                if (stage != 3) return;
                                if (time == null || time <= 0) { fail(); return; }
                                stage = 4;
                                result.success(state, time);
                            }
                            public void failure() { if (stage == 3) fail(); }
                        });
                    }
                    public void failure() { if (stage == 2) fail(); }
                });
            }
            public void failure() { if (stage == 1) fail(); }
        });
    }

    /** 超时后屏蔽迟到的成功回调，避免把不完整同步显示为成功。 */
    public void fail() {
        if (!isRunning()) return;
        stage = 4;
        result.failure();
    }

    public void cancel() { stage = 4; }
}
