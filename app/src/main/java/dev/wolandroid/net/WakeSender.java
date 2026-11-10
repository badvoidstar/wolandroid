package dev.wolandroid.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import dev.wolandroid.Device;

public final class WakeSender {
    private WakeSender() {}

    public static void send(NetworkEnvironment environment, Device device) throws IOException {
        byte[] bytes = MagicPacket.create(device.mac);
        long destination = environment.wakeDestination(device);
        try (DatagramSocket socket = new DatagramSocket(null)) {
            environment.network.bindSocket(socket);
            socket.setBroadcast(true);
            socket.bind(null);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                    Ipv4Subnet.toInetAddress(destination), 9);
            for (int attempt = 0; attempt < 3; attempt++) socket.send(packet);
        }
    }
}
