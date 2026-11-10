package dev.wolandroid.net;

import java.util.Arrays;
import java.util.Locale;

public final class MagicPacket {
    private MagicPacket() {}

    public static String normalizeMac(String value) {
        String compact = value.trim().replace(":", "").replace("-", "");
        if (!compact.matches("[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Enter six hexadecimal MAC octets, e.g. AA:BB:CC:DD:EE:02");
        }
        byte[] bytes = decode(compact);
        if ((bytes[0] & 1) != 0 || compact.equals("000000000000")) {
            throw new IllegalArgumentException("Use a device's unicast MAC address, not zero or multicast");
        }
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) normalized.append(':');
            normalized.append(compact, i * 2, i * 2 + 2);
        }
        return normalized.toString().toUpperCase(Locale.ROOT);
    }

    public static byte[] create(String mac) {
        byte[] address = decode(normalizeMac(mac).replace(":", ""));
        byte[] packet = new byte[102];
        Arrays.fill(packet, 0, 6, (byte) 0xff);
        for (int repetition = 0; repetition < 16; repetition++) {
            System.arraycopy(address, 0, packet, 6 + repetition * 6, 6);
        }
        return packet;
    }

    private static byte[] decode(String compact) {
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
