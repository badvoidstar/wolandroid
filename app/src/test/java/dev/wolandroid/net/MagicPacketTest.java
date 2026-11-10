package dev.wolandroid.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Test;

public class MagicPacketTest {
    @Test
    public void buildsExactWakeOnLanPayload() {
        byte[] packet = MagicPacket.create("a8-bb-cc-dd-ee-02");
        assertEquals(102, packet.length);
        assertArrayEquals(new byte[]{-1, -1, -1, -1, -1, -1}, Arrays.copyOfRange(packet, 0, 6));
        byte[] mac = {(byte) 0xa8, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, 2};
        for (int repeat = 0; repeat < 16; repeat++) {
            assertArrayEquals(mac, Arrays.copyOfRange(packet, 6 + repeat * 6, 12 + repeat * 6));
        }
    }

    @Test
    public void normalizesMacAndRejectsNonDeviceAddresses() {
        assertEquals("AA:BB:CC:DD:EE:02", MagicPacket.normalizeMac(" aabbccddee02 "));
        for (String invalid : new String[]{"", "not-a-mac", "AA:BB:CC:DD:EE",
                "FF:FF:FF:FF:FF:FF", "00:00:00:00:00:00", "01:00:5E:00:00:01"}) {
            assertThrows(IllegalArgumentException.class, () -> MagicPacket.create(invalid));
        }
    }
}
