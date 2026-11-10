package dev.wolandroid.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Ipv4SubnetTest {
    @Test
    public void derivesActualPrefixInsteadOfAssumingSlash24() {
        Ipv4Subnet subnet = Ipv4Subnet.parse("192.168.5.17/23");
        assertEquals("192.168.4.0/23", subnet.toString());
        assertEquals("192.168.4.1", Ipv4Subnet.format(subnet.firstHost()));
        assertEquals("192.168.5.254", Ipv4Subnet.format(subnet.lastHost()));
        assertEquals("192.168.5.255", Ipv4Subnet.format(subnet.broadcast()));
        assertEquals(510, subnet.hostCount());
        assertFalse(subnet.isHost(Ipv4Subnet.parseAddress("192.168.4.0")));
        assertFalse(subnet.isHost(Ipv4Subnet.parseAddress("192.168.5.255")));
        assertTrue(subnet.isHost(Ipv4Subnet.parseAddress("192.168.5.10")));
    }

    @Test
    public void handlesPointToPointAndUnsignedAddressSpaceWithoutOverflow() {
        assertEquals(2, Ipv4Subnet.parse("10.0.0.0/31").hostCount());
        assertEquals(1, Ipv4Subnet.parse("10.0.0.1/32").hostCount());
        Ipv4Subnet entire = Ipv4Subnet.parse("0.0.0.0/0");
        assertEquals(4294967294L, entire.hostCount());
        assertEquals("255.255.255.255", Ipv4Subnet.format(entire.broadcast()));
    }

    @Test
    public void refusesMalformedOrHostnameInputs() {
        for (String invalid : new String[]{"router/24", "192.168.1.256/24", "192.168.1/24",
                "192.168.1.1/-1", "192.168.1.1/33", "192.168.1.1/24/24", "192.168.1.1"}) {
            assertThrows(IllegalArgumentException.class, () -> Ipv4Subnet.parse(invalid));
        }
    }
}
