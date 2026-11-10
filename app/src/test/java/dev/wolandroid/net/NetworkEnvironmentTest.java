package dev.wolandroid.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import dev.wolandroid.Device;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NetworkEnvironmentTest {
    @Test
    public void wifiUsesTheRealInterfacePrefixAndRejectsOffSubnetTargets() throws IOException {
        NetworkEnvironment wifi = environment(false, "192.168.4.20/23", "192.168.4.0/23");
        Device target = target("192.168.5.10", "192.168.5.0/24");
        assertEquals("192.168.5.255", Ipv4Subnet.format(wifi.wakeDestination(target)));
        assertTrue(wifi.canScan(Ipv4Subnet.parse("192.168.5.0/24")));
        assertFalse(wifi.canScan(Ipv4Subnet.parse("192.168.0.0/16")));
        assertThrows(IOException.class, () -> wifi.wakeDestination(target("10.0.0.10", "10.0.0.0/24")));
    }

    @Test
    public void vpnUsesTheRemoteSubnetInsteadOfTheTunnelPrefix() throws IOException {
        NetworkEnvironment vpn = environment(true, "10.8.0.2/32", "192.168.10.0/24");
        assertEquals("192.168.10.0/24", vpn.suggestions.get(0).toString());
        assertEquals("192.168.10.255",
                Ipv4Subnet.format(vpn.wakeDestination(target("192.168.10.20", "192.168.10.0/24"))));
        assertTrue(vpn.canScan(Ipv4Subnet.parse("192.168.10.0/24")));
        assertFalse(vpn.canScan(Ipv4Subnet.parse("192.168.11.0/24")));
        assertThrows(IOException.class,
                () -> vpn.wakeDestination(target("192.168.11.20", "192.168.11.0/24")));
    }

    @Test
    public void fullTunnelRequiresExplicitLanRangeAndPointToPointUsesUnicast() throws IOException {
        NetworkEnvironment vpn = environment(true, "10.8.0.2/32", "0.0.0.0/0");
        assertTrue(vpn.suggestions.isEmpty());
        assertTrue(vpn.canScan(Ipv4Subnet.parse("192.168.10.0/24")));
        NetworkEnvironment pointToPoint = environment(false, "10.0.0.0/31", "10.0.0.0/31");
        assertEquals("10.0.0.1",
                Ipv4Subnet.format(pointToPoint.wakeDestination(target("10.0.0.1", "10.0.0.0/31"))));
    }

    private static NetworkEnvironment environment(boolean vpn, String link, String route) {
        String interfaceName = vpn ? "tun0" : "wlan0";
        LinkProperties properties = new LinkProperties();
        properties.setInterfaceName(interfaceName);
        LinkAddress address = ReflectionHelpers.callConstructor(LinkAddress.class,
                ClassParameter.from(String.class, link));
        ReflectionHelpers.callInstanceMethod(properties, "addLinkAddress",
                ClassParameter.from(LinkAddress.class, address));
        Ipv4Subnet range = Ipv4Subnet.parse(route);
        IpPrefix prefix = new IpPrefix(Ipv4Subnet.toInetAddress(range.firstHost()), range.prefix());
        properties.addRoute(ReflectionHelpers.callConstructor(RouteInfo.class,
                ClassParameter.from(IpPrefix.class, prefix),
                ClassParameter.from(InetAddress.class, null),
                ClassParameter.from(String.class, interfaceName)));
        Network network = Network.fromNetworkHandle(0x0000002acafed00dL);
        return new NetworkEnvironment(network, vpn, properties);
    }

    private static Device target(String ip, String subnet) {
        return new Device("desktop", "AA:BB:CC:DD:EE:02", ip, subnet, false, true);
    }
}
