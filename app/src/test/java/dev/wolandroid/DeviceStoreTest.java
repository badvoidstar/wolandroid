package dev.wolandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DeviceStoreTest {
    private Context context;
    private DeviceStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("devices", Context.MODE_PRIVATE).edit().clear().commit();
        store = new DeviceStore(context);
    }

    @Test
    public void keepsSleepingDevicesAndIndependentShortcutSnapshotAfterStoreRecreation() throws IOException {
        Device target = new Device("desktop", "AA:BB:CC:DD:EE:02", "192.168.1.10",
                "192.168.1.0/24", true, false);
        store.save(target.subnet, Collections.singleton(target));
        String id = store.saveShortcut(target);
        DeviceStore reopened = new DeviceStore(context);
        Device saved = reopened.load(target.subnet).get(0);
        assertFalse(saved.seen);
        assertTrue(saved.savedMac);
        store.save(target.subnet, Collections.emptyList());
        assertEquals(target.mac, reopened.loadShortcut(id).mac);
        assertEquals(target.subnet, reopened.loadShortcut(id).subnet);
        assertTrue(reopened.load("10.0.0.0/24").isEmpty());
    }

    @Test
    public void rejectsCorruptAndUnboundTargetsInsteadOfUsingDefaults() {
        assertThrows(IOException.class, () -> store.loadShortcut(null));
        assertThrows(IOException.class, () -> store.loadShortcut("6d804e61-7851-4685-983a-94cc1ad82fea"));
        context.getSharedPreferences("devices", Context.MODE_PRIVATE).edit()
                .putString("subnet:192.168.1.0/24", "broken-json").commit();
        assertThrows(IOException.class, () -> store.load("192.168.1.0/24"));
        Device noMac = new Device("", "", "192.168.1.10", "192.168.1.0/24", true, false);
        assertThrows(IOException.class, () -> store.saveShortcut(noMac));
    }

    @Test
    public void shortcutTargetsOnlyTheIsolatedWakeActivityWithPersistentId() {
        Intent intent = WakeShortcuts.intent(context, "target-id");
        assertEquals(ShortcutWakeActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(WakeShortcuts.ACTION_WAKE, intent.getAction());
        assertEquals("target-id", intent.getStringExtra(WakeShortcuts.EXTRA_TARGET));
        assertEquals(1, intent.getExtras().size());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0);
    }
}
