package dev.wolandroid.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.wolandroid.Device;

public final class NetworkEnvironment {
    public final Network network;
    public final boolean vpn;
    public final String interfaceName;
    public final String key;
    public final List<Ipv4Subnet> suggestions;
    public final Set<String> ownAddresses = new LinkedHashSet<>();
    public final List<InetAddress> dnsServers;
    private final List<Ipv4Subnet> localSubnets = new ArrayList<>();
    private final List<RouteInfo> routes;

    NetworkEnvironment(Network network, boolean vpn, LinkProperties properties) {
        this.network = network;
        this.vpn = vpn;
        interfaceName = properties.getInterfaceName();
        key = network.getNetworkHandle() + ":" + properties;
        dnsServers = new ArrayList<>(properties.getDnsServers());
        routes = new ArrayList<>(properties.getRoutes());
        for (LinkAddress link : properties.getLinkAddresses()) {
            if (link.getAddress() instanceof Inet4Address) {
                String address = link.getAddress().getHostAddress();
                ownAddresses.add(address);
                localSubnets.add(new Ipv4Subnet(Ipv4Subnet.parseAddress(address), link.getPrefixLength()));
            }
        }
        Set<Ipv4Subnet> candidates = new LinkedHashSet<>();
        if (vpn) {
            List<Ipv4Subnet> routed = new ArrayList<>();
            for (RouteInfo route : routes) {
                IpPrefix destination = route.getDestination();
                if (route.getType() == RouteInfo.RTN_UNICAST
                        && destination.getAddress() instanceof Inet4Address
                        && destination.getPrefixLength() > 0 && destination.getPrefixLength() < 31) {
                    routed.add(new Ipv4Subnet(
                            Ipv4Subnet.parseAddress(destination.getAddress().getHostAddress()),
                            destination.getPrefixLength()));
                }
            }
            routed.sort(Comparator.comparingLong(Ipv4Subnet::hostCount));
            candidates.addAll(routed);
        }
        for (Ipv4Subnet subnet : localSubnets) {
            if (!vpn || subnet.prefix() < 31) candidates.add(subnet);
        }
        suggestions = new ArrayList<>(candidates);
    }

    public static NetworkEnvironment detect(Context context) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) throw new IOException("Android network service is unavailable");
        Network active = manager.getActiveNetwork();
        Network wifi = null;
        Network vpn = null;
        for (Network candidate : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(candidate);
            if (capabilities == null) continue;
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (vpn == null || candidate.equals(active)) vpn = candidate;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (wifi == null || candidate.equals(active)) wifi = candidate;
            }
        }
        Network selected = vpn != null ? vpn : wifi;
        if (selected == null) throw new IOException("Connect to Wi-Fi or a VPN to discover and wake devices");
        LinkProperties properties = manager.getLinkProperties(selected);
        if (properties == null) throw new IOException("Network configuration is not available yet");
        return new NetworkEnvironment(selected, vpn != null, properties);
    }

    public long wakeDestination(Device device) throws IOException {
        long ip = Ipv4Subnet.parseAddress(device.ip);
        Ipv4Subnet savedSubnet = Ipv4Subnet.parse(device.subnet);
        if (!vpn) {
            for (Ipv4Subnet local : localSubnets) {
                if (local.isHost(ip)) return local.prefix() < 31 ? local.broadcast() : ip;
            }
            throw new IOException("This device is not on the current Wi-Fi subnet");
        }
        long destination = savedSubnet.prefix() < 31 ? savedSubnet.broadcast() : ip;
        InetAddress address = Ipv4Subnet.toInetAddress(destination);
        for (RouteInfo route : routes) {
            if (route.getType() == RouteInfo.RTN_UNICAST && route.getDestination().contains(address)) {
                return destination;
            }
        }
        throw new IOException("The VPN has no route to this device's subnet");
    }

    public boolean canScan(Ipv4Subnet subnet) {
        if (vpn) {
            for (RouteInfo route : routes) {
                if (route.getType() == RouteInfo.RTN_UNICAST
                        && route.getDestination().contains(Ipv4Subnet.toInetAddress(subnet.firstHost()))
                        && route.getDestination().contains(Ipv4Subnet.toInetAddress(subnet.lastHost()))) {
                    return true;
                }
            }
            return false;
        }
        for (Ipv4Subnet local : localSubnets) {
            if (local.contains(subnet.firstHost()) && local.contains(subnet.lastHost())) return true;
        }
        return false;
    }
}
