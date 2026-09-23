package com.populstay.populife;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.populstay.populife.helper.LockRecordOutbox;
import com.populstay.populife.helper.LockRecordStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LockRecordStoreTest {
    @Test public void committedRecordAndRetryStateSurviveDatabaseReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "test-record-outbox-" + UUID.randomUUID() + ".db";
        LockRecordStore store = new LockRecordStore(context, name);
        try {
            LockRecordOutbox.Entry e = new LockRecordOutbox.Entry();
            e.id = "one"; e.userId = "user-a"; e.server = "server-a";
            e.endpoint = "operation/log/keyboard/add";
            e.payload = "{\"lockId\":42,\"records\":\"[{\\\"operateDate\\\":123}]\"}";
            e.createdAt = 123;
            store.insert(e);
            store.defer(e.id, 2, 90000);
            store.close();
            store = new LockRecordStore(context, name);
            assertNull(store.next("user-b", "server-a"));
            assertNull(store.next("user-a", "server-b"));
            LockRecordOutbox.Entry restored = store.next("user-a", "server-a");
            assertNotNull(restored);
            assertEquals(e.payload, restored.payload);
            assertEquals(e.endpoint, restored.endpoint);
            assertEquals(123, restored.createdAt);
            assertEquals(2, restored.attempts);
            assertEquals(90000, restored.nextAttemptAt);
            store.delete(e.id);
            store.close();
            store = new LockRecordStore(context, name);
            assertNull(store.next("user-a", "server-a"));
        } finally {
            store.close();
            context.deleteDatabase(name);
        }
    }
}
