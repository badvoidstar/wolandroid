package dev.wolandroid.net;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.junit.Test;

public class ArpTableTest {
    @Test
    public void acceptsOnlyCompletedUnicastNeighborsOnTheSelectedInterface() throws IOException {
        String table = "IP address HW type Flags HW address Mask Device\n"
                + "192.168.1.2 0x1 0x2 aa:bb:cc:dd:ee:02 * wlan0\n"
                + "192.168.1.3 0x1 0x0 00:00:00:00:00:00 * wlan0\n"
                + "192.168.1.4 0x1 0x2 aa:bb:cc:dd:ee:04 * tun0\n"
                + "192.168.1.5 0x1 0x2 ff:ff:ff:ff:ff:ff * wlan0\n";
        Map<String, String> parsed = ArpTable.parse(new StringReader(table), "wlan0");
        assertEquals(1, parsed.size());
        assertEquals("AA:BB:CC:DD:EE:02", parsed.get("192.168.1.2"));
    }
}
