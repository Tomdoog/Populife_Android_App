package com.populstay.populife;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.populstay.populife.activity.ActivateDeviceActivity;
import com.populstay.populife.activity.AddDeviceFailActivity;
import com.populstay.populife.activity.HelpActivity;
import com.populstay.populife.activity.IcCardBluetoothAddActivity;
import com.populstay.populife.activity.LockAddGuideActivity;
import com.populstay.populife.activity.LockAutoLockingActivity;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.entity.Key;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.util.locale.LocalManageUtils;
import com.populstay.populife.util.locale.SPUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Page-only regression checks. Never starts scanning, enrollment, pairing or unlocking. */
@RunWith(AndroidJUnit4.class)
public class PadlockUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> activities = new ArrayList<>();
    private final Key originalKey = MyApplication.CURRENT_KEY;
    private int originalLanguage;

    @Before public void selectTestLanguage() {
        originalLanguage = SPUtils.getInstance(instrumentation.getTargetContext()).getLanguage();
        String language = InstrumentationRegistry.getArguments().getString("padlockLanguage");
        if (language != null) instrumentation.runOnMainSync(() ->
                LocalManageUtils.setSelectLanguage(instrumentation.getTargetContext(), Integer.parseInt(language)));
    }

    private Intent intent(Class<? extends Activity> type, String lockType) {
        return new Intent(instrumentation.getTargetContext(), type)
                .putExtra("KEY_LOCK_TYPE", lockType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    private Activity launch(Intent intent) {
        // A rejected page finishes in onCreate, so it never reaches the resumed state
        // required by startActivitySync. Observe creation for both accepted/rejected pages.
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                intent.getComponent().getClassName(), null, false);
        instrumentation.getTargetContext().startActivity(intent);
        Activity activity = monitor.waitForActivityWithTimeout(10000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Activity was not created", activity);
        activities.add(activity);
        instrumentation.waitForIdleSync();
        return activity;
    }

    private String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private void assertText(Activity activity, int id, int string) {
        assertEquals(activity.getString(string), text(activity, id));
    }

    private void screenshot(String name) {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("padlockScreenshots"))) return;
        instrumentation.waitForIdleSync();
        // Main-thread idleness does not wait for the system's task transition animation.
        android.os.SystemClock.sleep(750);
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "padlock-ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            bitmap.recycle();
        }
    }

    private void call(Activity activity, String name, Class<?>[] types, Object... args) {
        try {
            Method method = activity.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(activity, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @After public void finishPages() {
        instrumentation.runOnMainSync(() -> {
            for (Activity activity : activities) activity.finish();
            MyApplication.CURRENT_KEY = originalKey;
            LocalManageUtils.setSelectLanguage(instrumentation.getTargetContext(), originalLanguage);
        });
        instrumentation.waitForIdleSync();
    }

    @Test public void padlockIdentityAndImageRoles() {
        assertTrue(HomeDeviceInfo.isPadlock("P3_123456"));
        assertTrue(HomeDeviceInfo.isPadlock("p3_123456"));
        assertFalse(HomeDeviceInfo.isPadlock("P30_123456"));
        assertFalse(HomeDeviceInfo.isPadlock(null));
        assertEquals(R.drawable.padlock_outline, HomeDeviceInfo.getIconByName("P3_123456"));
        assertEquals(R.drawable.product_padlock, HomeDeviceInfo.getProductPictureByName("P3_123456"));
        assertNotEquals(R.drawable.padlock_outline, HomeDeviceInfo.getIconByName("K4_123456"));
    }

    @Test public void pairingPrecheckRequiresAllChecksAndShowsSafety() {
        Activity a = launch(intent(LockAddGuideActivity.class, "P3_test"));
        instrumentation.runOnMainSync(() -> {
            assertText(a, R.id.ck_battery_install, R.string.padlock_battery_hint);
            assertText(a, R.id.cb_confirm_time, R.string.padlock_distance_hint);
            assertText(a, R.id.padlock_safety_hint, R.string.padlock_pairing_safety);
            assertEquals(View.VISIBLE, a.findViewById(R.id.padlock_safety_hint).getVisibility());
            assertFalse(a.findViewById(R.id.tv_lock_add_guide_next).isEnabled());
            // Simulate system checks passing; no permissions or device operations are performed.
            for (int id : new int[]{R.id.cb_bt_open, R.id.cb_lbs_open, R.id.cb_net_open})
                ((CheckBox) a.findViewById(id)).setChecked(true);
            ((CheckBox) a.findViewById(R.id.ck_battery_install)).setChecked(true);
            assertFalse(a.findViewById(R.id.tv_lock_add_guide_next).isEnabled());
            ((CheckBox) a.findViewById(R.id.cb_confirm_time)).setChecked(true);
            assertTrue(a.findViewById(R.id.tv_lock_add_guide_next).isEnabled());
            ((CheckBox) a.findViewById(R.id.ck_battery_install)).setChecked(false);
            assertFalse(a.findViewById(R.id.tv_lock_add_guide_next).isEnabled());
        });
        screenshot("pairing-precheck");
    }

    @Test public void fingerprintPrecheckHasItsOwnSafety() {
        Activity a = launch(intent(LockAddGuideActivity.class, "p3_test")
                .putExtra("KEY_FROM", KeyPwdConstant.IFrom.FROM_FINGERPRINT_CARD));
        instrumentation.runOnMainSync(() -> {
            assertText(a, R.id.ck_battery_install, R.string.padlock_fingerprint_battery_hint);
            assertText(a, R.id.padlock_safety_hint, R.string.padlock_fingerprint_safety);
        });
        screenshot("fingerprint-precheck");
    }

    @Test public void pairingActivationUsesBeepAndOpenShackle() {
        Activity a = launch(intent(ActivateDeviceActivity.class, "P3_test"));
        instrumentation.runOnMainSync(() -> {
            assertText(a, R.id.add_device_activate_hint1, R.string.padlock_pairing_safety);
            assertText(a, R.id.add_device_activate_hint2, R.string.padlock_pairing_activation);
            assertEquals(View.VISIBLE, a.findViewById(R.id.add_device_activate_hint2).getVisibility());
        });
        screenshot("pairing-activation");
    }

    @Test public void fingerprintProgressUsesSdkTotalIncludingNonTwoCounts() {
        Activity a = launch(intent(IcCardBluetoothAddActivity.class, "P3_test"));
        instrumentation.runOnMainSync(() -> {
            assertText(a, R.id.add_device_activate_hint1, R.string.padlock_fingerprint_keep_open);
            assertText(a, R.id.add_device_activate_hint2, R.string.padlock_fingerprint_activation);
            assertText(a, R.id.tv_hint_success, R.string.padlock_start_fingerprint);
            call(a, "refreshStatus", new Class<?>[]{boolean.class}, false);
            assertText(a, R.id.add_device_activate_hint2, R.string.padlock_fingerprint_retry);
            for (int[] count : new int[][]{{0, 2}, {1, 2}, {2, 2}, {3, 5}, {5, 5}}) {
                call(a, "updateNoteText", new Class<?>[]{int.class, int.class}, count[0], count[1]);
                assertTrue(text(a, R.id.add_device_activate_hint1).contains(
                        a.getString(R.string.padlock_fingerprint_progress, count[0], count[1])));
            }
            call(a, "updateNoteText", new Class<?>[]{int.class, int.class}, 0, 0);
            assertText(a, R.id.add_device_activate_hint1, R.string.padlock_fingerprint_enrollment);
        });
        screenshot("fingerprint-enrollment");
    }

    @Test public void autoLockRejectsExplicitAndCachedPadlock() {
        Key key = new Key();
        key.setLockName("P3_test");
        Activity explicit = launch(intent(LockAutoLockingActivity.class, "P3_test")
                .putExtra("KEY_KEY", key));
        instrumentation.runOnMainSync(() -> assertTrue(explicit.isFinishing()));
        instrumentation.runOnMainSync(() -> MyApplication.CURRENT_KEY = key);
        Activity cached = launch(intent(LockAutoLockingActivity.class, "P3_test"));
        instrumentation.runOnMainSync(() -> assertTrue(cached.isFinishing()));
    }

    @Test public void helpIsModelSpecific() {
        Activity p3 = launch(intent(HelpActivity.class, "P3_test"));
        instrumentation.runOnMainSync(() -> {
            assertText(p3, R.id.lock_help_content_1, R.string.padlock_help_pairing);
            assertText(p3, R.id.lock_help_content_2, R.string.padlock_help_bound);
            assertText(p3, R.id.lock_help_content_3, R.string.padlock_wake_help);
        });
        Activity other = launch(intent(HelpActivity.class, "K4_test"));
        instrumentation.runOnMainSync(() -> assertNotEquals(other.getString(R.string.padlock_help_pairing),
                text(other, R.id.lock_help_content_1)));
    }

    @Test public void retryHelpPreservesFailedRollbackWarning() {
        for (int state : new int[]{AddDeviceFailActivity.ROLLBACK_NONE,
                AddDeviceFailActivity.ROLLBACK_DONE, AddDeviceFailActivity.ROLLBACK_FAILED}) {
            Activity a = launch(intent(AddDeviceFailActivity.class, "P3_test")
                    .putExtra("KEY_ROLLBACK_STATE", state));
            instrumentation.runOnMainSync(() -> assertText(a, R.id.tv_the_following_detail,
                    state == AddDeviceFailActivity.ROLLBACK_FAILED
                            ? R.string.add_fail_rollback_failed_detail : R.string.padlock_scan_retry));
        }
    }
}
