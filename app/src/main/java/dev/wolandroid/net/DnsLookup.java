package dev.wolandroid.net;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ThreadLocalRandom;

final class DnsLookup {
    private DnsLookup() {}

    static String reverse(NetworkEnvironment environment, long address) {
        int attempted = 0;
        for (InetAddress server : environment.dnsServers) {
            if (Thread.currentThread().isInterrupted()) return "";
            String name = query(environment, server, 53, address);
            if (!name.isEmpty()) return name;
            if (++attempted == 2) break;
        }
        if (Thread.currentThread().isInterrupted()) return "";
        // Legacy unicast mDNS PTR queries also work with hosts advertising only a .local name.
        return query(environment, Ipv4Subnet.toInetAddress(address), 5353, address);
    }

    private static String query(NetworkEnvironment environment, InetAddress server, int port, long address) {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            environment.network.bindSocket(socket);
            socket.bind(null);
            socket.setSoTimeout(350);
            socket.connect(server, port);
            int id = ThreadLocalRandom.current().nextInt(65536);
            byte[] query = DnsMessage.ptrQuery(id, address);
            socket.send(new DatagramPacket(query, query.length));
            byte[] buffer = new byte[4096];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return DnsMessage.readPtr(buffer, reply.getLength(), id, address);
        } catch (SocketTimeoutException expected) {
            return "";
        } catch (IOException error) {
            Log.d("WOLAndroid", "No usable PTR response from " + server.getHostAddress(), error);
            return "";
        }
    }
}
