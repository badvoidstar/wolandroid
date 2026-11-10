package dev.wolandroid;

import dev.wolandroid.net.Ipv4Subnet;
import dev.wolandroid.net.MagicPacket;

public final class Device {
    public final String name;
    public final String mac;
    public final String ip;
    public final String subnet;
    public final boolean seen;
    public final boolean savedMac;

    public Device(String name, String mac, String ip, String subnet, boolean seen, boolean savedMac) {
        this.name = name.trim();
        this.mac = mac.isEmpty() ? "" : MagicPacket.normalizeMac(mac);
        long address = Ipv4Subnet.parseAddress(ip);
        Ipv4Subnet range = Ipv4Subnet.parse(subnet);
        if (!range.isHost(address)) {
            throw new IllegalArgumentException("The device IP must be a host in the selected subnet");
        }
        this.ip = Ipv4Subnet.format(address);
        this.subnet = range.toString();
        this.seen = seen;
        this.savedMac = savedMac && !this.mac.isEmpty();
    }

    public Device mergeDiscovery(Device found) {
        if (!ip.equals(found.ip) || !subnet.equals(found.subnet)) {
            throw new IllegalArgumentException("Cannot merge different device addresses");
        }
        // A newly observed MAC is authoritative; never give a replacement device the old name.
        boolean replaced = !found.mac.isEmpty() && !mac.isEmpty() && !found.mac.equals(mac);
        return new Device(found.name.isEmpty() && !replaced ? name : found.name,
                found.mac.isEmpty() ? mac : found.mac, ip, subnet, found.seen,
                found.mac.isEmpty() && !mac.isEmpty());
    }
}
