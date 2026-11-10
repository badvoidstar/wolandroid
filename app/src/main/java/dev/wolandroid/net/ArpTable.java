package dev.wolandroid.net;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class ArpTable {
    private ArpTable() {}

    public static Map<String, String> read(String interfaceName) {
        // Android 10 closed /proc/net to ordinary apps. Do not use hidden APIs or root workarounds.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return new HashMap<>();
        try (FileReader reader = new FileReader("/proc/net/arp")) {
            return parse(reader, interfaceName);
        } catch (IOException | SecurityException error) {
            Log.w("WOLAndroid", "ARP table unavailable; MACs must be entered manually", error);
            return new HashMap<>();
        }
    }

    static Map<String, String> parse(Reader source, String interfaceName) throws IOException {
        Map<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 6 || !fields[5].equals(interfaceName)) continue;
            try {
                if ((Integer.decode(fields[2]) & 2) == 0) continue;
                String ip = Ipv4Subnet.format(Ipv4Subnet.parseAddress(fields[0]));
                result.put(ip, MagicPacket.normalizeMac(fields[3]));
            } catch (IllegalArgumentException ignored) {
                // Headers and incomplete neighbor entries are not devices.
            }
        }
        return result;
    }
}
