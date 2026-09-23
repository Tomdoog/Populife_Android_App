package com.populstay.populife.helper;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class LockRecordOutboxTest {
    private final MemoryStore store = new MemoryStore();
    private final TestEnvironment environment = new TestEnvironment();
    private final TestRunner runner = new TestRunner();
    private final TestTransport transport = new TestTransport();
    private LockRecordOutbox queue = new LockRecordOutbox(store, environment, runner, transport);
    private final List<String> results = new ArrayList<>();

    private LockRecordOutbox.Entry entry(String id) {
        LockRecordOutbox.Entry e = new LockRecordOutbox.Entry();
        e.id = id; e.userId = "user-a"; e.server = "server-a";
        e.endpoint = "operation/log/add";
        e.payload = "{\"lockId\":42,\"keyId\":7,\"event\":1,\"userId\":\"user-a\"}";
        e.createdAt = 123;
        return e;
    }

    private void enqueue(LockRecordOutbox.Entry e) {
        queue.enqueue(e, (uploaded, saved) -> results.add(uploaded + ":" + saved));
        runner.drain();
    }

    private void complete(boolean success) {
        transport.completions.get(transport.completions.size() - 1).complete(success);
        runner.drain();
    }

    @Test public void savesBeforeSendingAndOnlyRemovesAfterAcknowledgement() {
        enqueue(entry("one"));
        assertEquals(1, store.entries.size());
        assertEquals(1, transport.sent.size());
        assertTrue(results.isEmpty());
        complete(true);
        assertTrue(store.entries.isEmpty());
        assertEquals("true:true", results.get(0));
    }

    @Test public void offlineRecordSurvivesRestartAndReplaysOriginalPayload() {
        environment.online = false;
        LockRecordOutbox.Entry original = entry("one");
        enqueue(original);
        assertTrue(transport.sent.isEmpty());
        assertEquals("false:true", results.get(0));
        queue = new LockRecordOutbox(store, environment, runner, transport);
        environment.online = true;
        queue.retry(); runner.drain();
        assertEquals(original.payload, transport.sent.get(0).payload);
        assertEquals(123, transport.sent.get(0).createdAt);
        complete(true);
        assertTrue(store.entries.isEmpty());
    }

    @Test public void processDeathBeforeAckLeavesRecordForAtLeastOnceReplay() {
        enqueue(entry("one"));
        TestTransport afterRestart = new TestTransport();
        queue = new LockRecordOutbox(store, environment, runner, afterRestart);
        queue.retry(); runner.drain();
        assertEquals(1, afterRestart.sent.size());
        assertEquals(transport.sent.get(0).id, afterRestart.sent.get(0).id);
    }

    @Test public void failureBacksOffAndDoesNotBlockOtherRecords() {
        enqueue(entry("one"));
        enqueue(entry("two"));
        assertEquals(1, transport.sent.size());
        complete(false);
        assertEquals(30_000, store.entries.get("one").nextAttemptAt);
        assertEquals("two", transport.sent.get(1).id);
        complete(true);
        assertEquals(30_000, runner.delay);
        environment.now = 30_000;
        runner.wake();
        assertEquals("one", transport.sent.get(2).id);
        complete(false);
        assertEquals(90_000, store.entries.get("one").nextAttemptAt);
    }

    @Test public void concurrentTriggersDoNotSendSameRecordTwice() {
        enqueue(entry("one"));
        queue.retry(); queue.retry(); queue.retry(); runner.drain();
        assertEquals(1, transport.sent.size());
        LockRecordOutbox.Completion callback = transport.completions.get(0);
        callback.complete(true); callback.complete(false); runner.drain();
        assertTrue(store.entries.isEmpty());
        assertEquals(1, results.size());
    }

    @Test public void accountAndServerSwitchNeverReassignPendingRecords() {
        environment.online = false;
        enqueue(entry("one"));
        environment.online = true;
        environment.user = "user-b";
        queue.retry(); runner.drain();
        assertTrue(transport.sent.isEmpty());
        environment.user = "user-a";
        environment.server = "server-b";
        queue.retry(); runner.drain();
        assertTrue(transport.sent.isEmpty());
        environment.server = "server-a";
        queue.retry(); runner.drain();
        assertEquals(1, transport.sent.size());
        assertEquals("user-a", transport.sent.get(0).userId);
    }

    @Test public void logoutDuringUploadStopsNextOldAccountRecord() {
        enqueue(entry("one")); enqueue(entry("two"));
        environment.user = "";
        complete(true);
        assertEquals(1, transport.sent.size());
        assertTrue(store.entries.containsKey("two"));
    }

    @Test public void sdkBatchIsReplayedWithoutRewritingItsDatesOrContent() {
        environment.online = false;
        LockRecordOutbox.Entry e = entry("sdk");
        e.endpoint = "operation/log/keyboard/add";
        e.payload = "{\"records\":\"[{\\\"recordType\\\":7,\\\"operateDate\\\":123}]\",\"lockId\":42}";
        enqueue(e);
        environment.online = true;
        queue.retry(); runner.drain();
        assertEquals(e.endpoint, transport.sent.get(0).endpoint);
        assertEquals(e.payload, transport.sent.get(0).payload);
    }

    @Test public void diskFailureNeverPretendsRecordWasSaved() {
        store.failInsert = true;
        enqueue(entry("one"));
        assertEquals("false:false", results.get(0));
        assertTrue(transport.sent.isEmpty());
    }

    @Test public void synchronousTransportFailureIsRetained() {
        transport.failSend = true;
        enqueue(entry("one"));
        assertEquals(1, store.entries.size());
        assertEquals("false:true", results.get(0));
        assertEquals(30_000, runner.delay);
    }

    @Test public void retryDelayIsBounded() {
        assertEquals(30_000, LockRecordOutbox.retryDelay(1));
        assertEquals(60_000, LockRecordOutbox.retryDelay(2));
        assertEquals(1_800_000, LockRecordOutbox.retryDelay(31));
    }

    @Test public void lateCallbackCannotAcknowledgeANewerAttempt() {
        enqueue(entry("one"));
        LockRecordOutbox.Completion old = transport.completions.get(0);
        complete(false);
        environment.now = 30_000;
        runner.wake();
        old.complete(true); runner.drain();
        assertEquals(1, store.entries.size());
        complete(true);
        assertTrue(store.entries.isEmpty());
    }

    private final class TestTransport implements LockRecordOutbox.Transport {
        final List<LockRecordOutbox.Entry> sent = new ArrayList<>();
        final List<LockRecordOutbox.Completion> completions = new ArrayList<>();
        boolean failSend;
        public void send(LockRecordOutbox.Entry entry, LockRecordOutbox.Completion completion) {
            assertTrue("must commit before network", store.entries.containsKey(entry.id));
            if (failSend) throw new IllegalStateException("transport unavailable");
            sent.add(entry); completions.add(completion);
        }
    }

    private static final class TestEnvironment implements LockRecordOutbox.Environment {
        String user = "user-a", server = "server-a";
        boolean online = true;
        long now;
        public String userId() { return user; }
        public String server() { return server; }
        public boolean online() { return online; }
        public long now() { return now; }
    }

    private static final class TestRunner implements LockRecordOutbox.Runner {
        final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        Runnable wakeup;
        long delay;
        public void execute(Runnable action) { actions.add(action); }
        public void wakeAfter(Runnable action, long millis) { wakeup = action; delay = millis; }
        void drain() { while (!actions.isEmpty()) actions.remove().run(); }
        void wake() { execute(wakeup); drain(); }
    }

    private static final class MemoryStore implements LockRecordOutbox.Store {
        final Map<String, LockRecordOutbox.Entry> entries = new LinkedHashMap<>();
        boolean failInsert;
        public void insert(LockRecordOutbox.Entry e) {
            if (failInsert) throw new IllegalStateException("disk full");
            entries.put(e.id, e);
        }
        public LockRecordOutbox.Entry next(String userId, String server) {
            return entries.values().stream().filter(e -> e.userId.equals(userId) && e.server.equals(server))
                    .min(Comparator.comparingLong(e -> e.nextAttemptAt)).orElse(null);
        }
        public void delete(String id) { entries.remove(id); }
        public void defer(String id, int attempts, long at) {
            entries.get(id).attempts = attempts;
            entries.get(id).nextAttemptAt = at;
        }
    }
}
