package dev.wolandroid.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public final class Ipv4Subnet {
    private final long network;
    private final int prefix;
    private final long mask;

    public Ipv4Subnet(long address, int prefix) {
        if (address < 0 || address > 0xffffffffL || prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid IPv4 subnet");
        }
        this.prefix = prefix;
        mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        network = address & mask;
    }

    public static Ipv4Subnet parse(String value) {
        String[] parts = value.trim().split("/", -1);
        if (parts.length != 2 || !parts[1].matches("[0-9]{1,2}")) {
            throw new IllegalArgumentException("Use an IPv4 subnet such as 192.168.1.0/24");
        }
        return new Ipv4Subnet(parseAddress(parts[0]), Integer.parseInt(parts[1]));
    }

    public static long parseAddress(String value) {
        String[] octets = value.trim().split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Enter a numeric IPv4 address");
        }
        long result = 0;
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("Enter a numeric IPv4 address");
            }
            int number = Integer.parseInt(octet);
            if (number > 255) {
                throw new IllegalArgumentException("IPv4 octets must be between 0 and 255");
            }
            result = (result << 8) | number;
        }
        return result;
    }

    public static String format(long address) {
        return ((address >>> 24) & 255) + "." + ((address >>> 16) & 255) + "."
                + ((address >>> 8) & 255) + "." + (address & 255);
    }

    public static InetAddress toInetAddress(long address) {
        byte[] bytes = {(byte) (address >>> 24), (byte) (address >>> 16),
                (byte) (address >>> 8), (byte) address};
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public boolean contains(long address) {
        return (address & mask) == network;
    }

    public boolean isHost(long address) {
        return contains(address) && address >= firstHost() && address <= lastHost();
    }

    public long firstHost() {
        return prefix >= 31 ? network : network + 1;
    }

    public long lastHost() {
        return prefix >= 31 ? broadcast() : broadcast() - 1;
    }

    public long hostCount() {
        return lastHost() - firstHost() + 1;
    }

    public long broadcast() {
        return network | (~mask & 0xffffffffL);
    }

    public int prefix() {
        return prefix;
    }

    @Override
    public String toString() {
        return format(network) + "/" + prefix;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ipv4Subnet
                && ((Ipv4Subnet) other).network == network
                && ((Ipv4Subnet) other).prefix == prefix;
    }

    @Override
    public int hashCode() {
        return Objects.hash(network, prefix);
    }
}
