package dev.wolandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceTest {
    @Test
    public void keepsButLabelsSavedMacWhenAndroidCannotRediscoverIt() {
        Device saved = new Device("desktop", "AA:BB:CC:DD:EE:02", "192.168.1.10",
                "192.168.1.0/24", false, true);
        Device found = new Device("", "", saved.ip, saved.subnet, true, false);
        Device merged = saved.mergeDiscovery(found);
        assertEquals("desktop", merged.name);
        assertEquals(saved.mac, merged.mac);
        assertTrue(merged.savedMac);
        assertTrue(merged.seen);
    }

    @Test
    public void doesNotAssignOldNameToADifferentMacAtSameIp() {
        Device old = new Device("old", "AA:BB:CC:DD:EE:02", "192.168.1.10",
                "192.168.1.0/24", false, true);
        Device replacement = new Device("", "AA:BB:CC:DD:EE:04", old.ip, old.subnet, true, false);
        Device merged = old.mergeDiscovery(replacement);
        assertEquals("", merged.name);
        assertEquals(replacement.mac, merged.mac);
        assertFalse(merged.savedMac);
    }

    @Test
    public void rejectsTargetsOutsideTheBoundSubnet() {
        assertThrows(IllegalArgumentException.class, () -> new Device("wrong subnet",
                "AA:BB:CC:DD:EE:02", "10.0.0.4", "192.168.1.0/24", false, true));
    }
}
