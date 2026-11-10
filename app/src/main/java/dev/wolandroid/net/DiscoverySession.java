package dev.wolandroid.net;

import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.wolandroid.Device;

public final class DiscoverySession implements AutoCloseable {
    public static final int MAX_HOSTS = 4096;
    private static final int[] PORTS = {80, 443, 22, 445};
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor();
    private final ExecutorService probes = Executors.newFixedThreadPool(32);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final NetworkEnvironment environment;
    private final Ipv4Subnet subnet;
    private final Listener listener;

    public interface Listener {
        void onDevice(Device device);
        void onProgress(int completed, int total);
        void onComplete();
        void onError(String message);
    }

    public DiscoverySession(NetworkEnvironment environment, Ipv4Subnet subnet, Listener listener) {
        this.environment = environment;
        this.subnet = subnet;
        this.listener = listener;
    }

    public void start() {
        coordinator.execute(this::scan);
    }

    private void scan() {
        try {
            if (subnet.hostCount() > MAX_HOSTS) {
                throw new IOException("This subnet is too large to scan. Choose a range of at most "
                        + MAX_HOSTS + " addresses (for example a /24).");
            }
            if (!environment.canScan(subnet)) {
                throw new IOException("The selected network has no route to this subnet");
            }
            Map<String, String> initialArp = ArpTable.read(environment.interfaceName);
            CompletionService<Device> results = new ExecutorCompletionService<>(probes);
            int total = (int) subnet.hostCount();
            for (long ip = subnet.firstHost(); ip <= subnet.lastHost(); ip++) {
                if (cancelled.get()) return;
                long address = ip;
                results.submit(() -> probe(address, initialArp));
            }
            List<Device> found = new ArrayList<>();
            for (int completed = 1; completed <= total; completed++) {
                if (cancelled.get()) return;
                Device device = results.take().get();
                if (device != null) {
                    found.add(device);
                    listener.onDevice(device);
                }
                if (completed % 8 == 0 || completed == total) listener.onProgress(completed, total);
            }
            Map<String, String> finalArp = ArpTable.read(environment.interfaceName);
            Set<String> reported = new HashSet<>();
            for (Device device : found) {
                reported.add(device.ip);
                String mac = finalArp.get(device.ip);
                if (mac != null && !mac.equals(device.mac)) {
                    listener.onDevice(new Device(device.name, mac, device.ip, device.subnet,
                            device.seen, false));
                }
            }
            for (Map.Entry<String, String> neighbor : finalArp.entrySet()) {
                if (cancelled.get()) return;
                long address = Ipv4Subnet.parseAddress(neighbor.getKey());
                if (!reported.contains(neighbor.getKey()) && subnet.isHost(address)) {
                    listener.onDevice(new Device(DnsLookup.reverse(environment, address),
                            neighbor.getValue(), neighbor.getKey(), subnet.toString(), false, true));
                }
            }
            if (!cancelled.get()) listener.onComplete();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (IOException | ExecutionException | CancellationException | SecurityException
                 | RejectedExecutionException error) {
            if (!cancelled.get()) {
                Log.e("WOLAndroid", "Discovery failed", error);
                listener.onError("Discovery failed: " + error.getMessage());
            }
        } finally {
            probes.shutdownNow();
            coordinator.shutdown();
        }
    }

    private Device probe(long address, Map<String, String> neighbors) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) return null;
        String ip = Ipv4Subnet.format(address);
        boolean self = environment.ownAddresses.contains(ip);
        boolean responding = self || reachable(address);
        String mac = neighbors.getOrDefault(ip, "");
        if (!responding && mac.isEmpty()) return null;
        String name = DnsLookup.reverse(environment, address);
        if (self && name.isEmpty()) name = Build.MODEL;
        return new Device(name, mac, ip, subnet.toString(), responding, !responding);
    }

    private boolean reachable(long address) {
        for (int port : PORTS) {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) return false;
            try (Socket socket = environment.network.getSocketFactory().createSocket()) {
                socket.connect(new InetSocketAddress(Ipv4Subnet.toInetAddress(address), port), 300);
                return true;
            } catch (ConnectException error) {
                // A TCP refusal is positive evidence of a host, unlike a timeout or no-route error.
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof ErrnoException
                            && ((ErrnoException) cause).errno == OsConstants.ECONNREFUSED) return true;
                }
            } catch (IOException expected) {
                Log.v("WOLAndroid", "No probe reply from " + Ipv4Subnet.format(address) + ":" + port);
            }
        }
        return false;
    }

    @Override
    public void close() {
        cancelled.set(true);
        coordinator.shutdownNow();
        probes.shutdownNow();
    }
}
