package com.populstay.populife.helper;

import org.junit.Test;
import static org.junit.Assert.*;
import com.populstay.populife.helper.LockTimezoneCalibration.*;

public class LockTimezoneCalibrationTest {
    private static final class Fake implements Actions, Result {
        Callback<Void> update;
        Callback<State> load;
        Callback<Long> clock;
        int succeeded, failed;
        public void updateServer(Callback<Void> cb) { update = cb; }
        public void loadServer(Callback<State> cb) { load = cb; }
        public void syncAndVerifyClock(State state, Callback<Long> cb) { clock = cb; }
        public void success(State state, long time) { succeeded++; }
        public void failure() { failed++; }
    }

    @Test public void serverSuccessAloneDoesNotCompleteCalibration() {
        Fake f = new Fake();
        LockTimezoneCalibration task = new LockTimezoneCalibration(12, 28800000, f, f);
        task.start();
        f.update.success(null); // 兼容旧接口 data=null，再从详情读取新凭据。
        assertEquals(0, f.succeeded);
        f.load.success(new State(12, 28800000, "updated-data"));
        assertEquals(0, f.succeeded);
        f.clock.success(1791524700000L);
        assertEquals(1, f.succeeded);
        assertFalse(task.isRunning());
    }

    @Test public void staleMissingOrWrongLockStateNeverReachesHardware() {
        for (State state : new State[]{null, new State(12, null, "data"),
                new State(12, -36000000, "data"), new State(13, 28800000, "data"),
                new State(12, 28800000, ""), new State(12, 28800000, null)}) {
            Fake f = new Fake();
            new LockTimezoneCalibration(12, 28800000, f, f).start();
            f.update.success(null);
            f.load.success(state);
            assertNull(f.clock);
            assertEquals(1, f.failed);
            assertEquals(0, f.succeeded);
        }
    }

    @Test public void zeroAndFractionalHourOffsetsRemainExact() {
        for (int offset : new int[]{0, 19800000, 20700000, -12600000}) {
            Fake f = new Fake();
            new LockTimezoneCalibration(12, offset, f, f).start();
            f.update.success(null);
            f.load.success(new State(12, offset, "data"));
            f.clock.success(1791524700000L);
            assertEquals(1, f.succeeded);
        }
    }

    @Test public void timeoutIgnoresLateServerSuccess() {
        Fake f = new Fake();
        LockTimezoneCalibration task = new LockTimezoneCalibration(12, 0, f, f);
        task.start(); task.fail(); f.update.success(null);
        assertNull(f.load);
        assertEquals(1, f.failed);
    }

    @Test public void deviceFailureAndLateCallbacksCannotReportSuccess() {
        Fake f = new Fake();
        new LockTimezoneCalibration(12, 0, f, f).start();
        f.update.success(null);
        f.load.success(new State(12, 0, "data"));
        f.clock.failure(); f.clock.success(1791524700000L); f.clock.failure();
        assertEquals(1, f.failed);
        assertEquals(0, f.succeeded);
    }

    @Test public void serverFailureNeverStartsDeviceOperation() {
        Fake f = new Fake();
        new LockTimezoneCalibration(12, 0, f, f).start();
        f.update.failure();
        assertNull(f.load); assertNull(f.clock);
        assertEquals(1, f.failed);
    }
}
