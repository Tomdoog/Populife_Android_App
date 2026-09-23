package com.populstay.populife;

import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.populstay.populife.activity.LockOperateRecordActivity;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.entity.Key;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** 只验证页面参数，不创建页面、连接蓝牙或请求生产接口。 */
@RunWith(AndroidJUnit4.class)
public class LockRecordIdentityTest {
    @Test public void stalePageAndGlobalIdsCannotOverrideCurrentKey() {
        assertIdentity(101, 202);
    }

    @Test public void negativeLockIdIsAlsoPreserved() {
        assertIdentity(-101, -202);
    }

    private void assertIdentity(int pageId, int keyId) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Key original = MyApplication.CURRENT_KEY;
            try {
                Key staleKey = new Key();
                staleKey.setLockId(303);
                MyApplication.CURRENT_KEY = staleKey;
                Key currentKey = new Key();
                currentKey.setLockId(keyId);
                LockOperateRecordActivity activity = new LockOperateRecordActivity();
                activity.setIntent(new Intent().putExtra("key_lock_id", pageId)
                        .putExtra("KEY_KEY", currentKey));
                Method read = LockOperateRecordActivity.class.getDeclaredMethod("getIntentData");
                read.setAccessible(true);
                assertEquals(true, read.invoke(activity));
                Field id = LockOperateRecordActivity.class.getDeclaredField("mLockId");
                id.setAccessible(true);
                assertEquals(keyId, id.getInt(activity));
                assertSame(currentKey, MyApplication.CURRENT_KEY);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                MyApplication.CURRENT_KEY = original;
            }
        });
    }
}
