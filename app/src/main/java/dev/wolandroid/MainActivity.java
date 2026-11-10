package dev.wolandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import dev.wolandroid.net.DiscoverySession;
import dev.wolandroid.net.Ipv4Subnet;
import dev.wolandroid.net.MagicPacket;
import dev.wolandroid.net.NetworkEnvironment;
import dev.wolandroid.net.WakeSender;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService wakeWorker = Executors.newSingleThreadExecutor();
    private final Map<String, Device> devices = new LinkedHashMap<>();
    private final Runnable networkRefresh = () -> refreshNetwork(false);
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { scheduleNetworkRefresh(); }
                @Override public void onLost(Network network) { scheduleNetworkRefresh(); }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    scheduleNetworkRefresh();
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    scheduleNetworkRefresh();
                }
            };
    private DeviceStore store;
    private DeviceAdapter adapter;
    private NetworkEnvironment environment;
    private Ipv4Subnet subnet;
    private DiscoverySession scan;
    private int generation;
    private boolean started;
    private boolean registered;
    private boolean loaded;
    private TextView status;
    private Button subnetButton;
    private Button refreshButton;
    private Button addButton;
    private ProgressBar progress;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        int horizontal = dp(16);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(horizontal + bars.left, bars.top, horizontal + bars.right, bars.bottom);
            } else {
                view.setPadding(horizontal + insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(), horizontal + insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        store = new DeviceStore(this);
        adapter = new DeviceAdapter(this);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        subnetButton = findViewById(R.id.subnet);
        refreshButton = findViewById(R.id.refresh);
        addButton = findViewById(R.id.add);
        ListView list = findViewById(R.id.devices);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty));
        DeviceListGestures.attach(list, this::wake, this::options);
        subnetButton.setOnClickListener(view -> chooseSubnet());
        refreshButton.setOnClickListener(view -> {
            if (scan != null) {
                stopScan();
                status.setText(getResources().getQuantityString(
                        R.plurals.scan_stopped, devices.size(), devices.size()));
                persist();
            } else {
                refreshNetwork(true);
            }
        });
        addButton.setOnClickListener(view -> edit(null, null));
        findViewById(R.id.help).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.help).setMessage(R.string.help_text)
                .setPositiveButton(android.R.string.ok, null).show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager != null) {
            try {
                NetworkRequest request = new NetworkRequest.Builder()
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build();
                manager.registerNetworkCallback(request, networkCallback);
                registered = true;
            } catch (SecurityException error) {
                showError(error);
            }
        }
        refreshNetwork(true);
    }

    @Override
    protected void onStop() {
        started = false;
        main.removeCallbacks(networkRefresh);
        stopScan();
        persist();
        if (registered) {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            if (manager != null) manager.unregisterNetworkCallback(networkCallback);
            registered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        wakeWorker.shutdownNow();
        super.onDestroy();
    }

    private void scheduleNetworkRefresh() {
        main.removeCallbacks(networkRefresh);
        main.postDelayed(networkRefresh, 350);
    }

    private void refreshNetwork(boolean force) {
        if (!started) return;
        try {
            NetworkEnvironment next = NetworkEnvironment.detect(this);
            if (!force && environment != null && environment.key.equals(next.key)) return;
            stopScan();
            persist();
            environment = next;
            String saved = getPreferences(Context.MODE_PRIVATE).getString("subnet:" + next.vpn, "");
            Ipv4Subnet preferred = null;
            if (!saved.isEmpty()) {
                try {
                    preferred = Ipv4Subnet.parse(saved);
                } catch (IllegalArgumentException error) {
                    Log.w("WOLAndroid", "Invalid saved subnet; selecting the network subnet", error);
                }
            }
            subnet = preferred != null && next.canScan(preferred) ? preferred
                    : (next.suggestions.isEmpty() ? null : next.suggestions.get(0));
            subnetButton.setEnabled(true);
            refreshButton.setEnabled(true);
            addButton.setEnabled(subnet != null);
            updateSubnetLabel();
            if (subnet == null) {
                loaded = false;
                devices.clear();
                adapter.submit(devices.values());
                status.setText(R.string.subnet_needed);
            } else {
                startScan();
            }
        } catch (IOException | SecurityException error) {
            stopScan();
            persist();
            environment = null;
            subnetButton.setEnabled(false);
            addButton.setEnabled(false);
            showError(error);
        }
    }

    private void updateSubnetLabel() {
        if (environment == null || subnet == null) {
            subnetButton.setText(R.string.select_subnet);
        } else {
            subnetButton.setText(getString(R.string.network_subnet,
                    getString(environment.vpn ? R.string.vpn : R.string.wifi), subnet.toString()));
        }
    }

    private void chooseSubnet() {
        if (environment == null) return;
        List<String> choices = new ArrayList<>();
        for (Ipv4Subnet candidate : environment.suggestions) choices.add(candidate.toString());
        choices.add(getString(R.string.manual_subnet));
        new AlertDialog.Builder(this).setTitle(R.string.select_subnet)
                .setItems(choices.toArray(new String[0]), (dialog, position) -> {
                    if (position == choices.size() - 1) {
                        EditText input = new EditText(this);
                        input.setHint(R.string.subnet_hint);
                        input.setSingleLine(true);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        AlertDialog editor = new AlertDialog.Builder(this)
                                .setTitle(R.string.select_subnet).setView(input)
                                .setPositiveButton(R.string.scan, null)
                                .setNegativeButton(R.string.cancel, null).create();
                        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(view -> {
                                    try {
                                        selectSubnet(Ipv4Subnet.parse(input.getText().toString()));
                                        editor.dismiss();
                                    } catch (IllegalArgumentException error) {
                                        input.setError(error.getMessage());
                                    }
                                }));
                        editor.show();
                    } else {
                        try {
                            selectSubnet(Ipv4Subnet.parse(choices.get(position)));
                        } catch (IllegalArgumentException error) {
                            showError(error);
                        }
                    }
                }).show();
    }

    private void selectSubnet(Ipv4Subnet selected) {
        if (selected.hostCount() > DiscoverySession.MAX_HOSTS) {
            throw new IllegalArgumentException("Select at most 4,096 hosts; narrow this range explicitly");
        }
        if (environment == null || !environment.canScan(selected)) {
            throw new IllegalArgumentException("The selected network has no route to this subnet");
        }
        stopScan();
        persist();
        subnet = selected;
        getPreferences(Context.MODE_PRIVATE).edit()
                .putString("subnet:" + environment.vpn, selected.toString()).apply();
        updateSubnetLabel();
        addButton.setEnabled(true);
        startScan();
    }

    private void startScan() {
        stopScan();
        devices.clear();
        loaded = false;
        try {
            for (Device device : store.load(subnet.toString())) devices.put(device.ip, device);
            loaded = true;
        } catch (IOException error) {
            adapter.submit(devices.values());
            showError(error);
            return;
        }
        adapter.submit(devices.values());
        int token = generation;
        status.setText(R.string.scan_starting);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        refreshButton.setText(R.string.stop);
        scan = new DiscoverySession(environment, subnet, new DiscoverySession.Listener() {
            @Override
            public void onDevice(Device found) {
                main.post(() -> {
                    if (!started || token != generation) return;
                    Device previous = devices.get(found.ip);
                    devices.put(found.ip, previous == null ? found : previous.mergeDiscovery(found));
                    adapter.submit(devices.values());
                });
            }

            @Override
            public void onProgress(int completed, int total) {
                main.post(() -> {
                    if (!started || token != generation) return;
                    progress.setProgress(completed * 100 / total);
                    status.setText(getResources().getQuantityString(
                            R.plurals.scanning, total, completed, total, devices.size()));
                });
            }

            @Override
            public void onComplete() {
                main.post(() -> {
                    if (!started || token != generation) return;
                    stopScan();
                    status.setText(getResources().getQuantityString(
                            R.plurals.scan_complete, devices.size(), devices.size()));
                    persist();
                });
            }

            @Override
            public void onError(String message) {
                main.post(() -> {
                    if (!started || token != generation) return;
                    stopScan();
                    status.setText(message);
                    persist();
                });
            }
        });
        scan.start();
    }

    private void stopScan() {
        generation++;
        if (scan != null) scan.close();
        scan = null;
        if (progress != null) progress.setVisibility(View.INVISIBLE);
        if (refreshButton != null) refreshButton.setText(R.string.refresh);
    }

    private boolean persist() {
        if (!loaded || subnet == null) return false;
        try {
            store.save(subnet.toString(), devices.values());
            return true;
        } catch (IOException error) {
            showError(error);
            return false;
        }
    }

    private void options(Device device) {
        stopScan();
        status.setText(getResources().getQuantityString(
                R.plurals.scan_stopped, devices.size(), devices.size()));
        persist();
        new AlertDialog.Builder(this).setTitle(device.name.isEmpty() ? device.ip : device.name)
                .setItems(new String[]{getString(R.string.wake_now), getString(R.string.create_shortcut),
                        getString(R.string.edit_device), getString(R.string.forget_device)}, (dialog, which) -> {
                    if (which == 0) wake(device);
                    else if (which == 1) pin(device);
                    else if (which == 2) edit(device, null);
                    else {
                        devices.remove(device.ip);
                        adapter.submit(devices.values());
                        persist();
                    }
                }).show();
    }

    private void edit(Device previous, Consumer<Device> afterSave) {
        if (subnet == null) return;
        stopScan();
        status.setText(getResources().getQuantityString(
                R.plurals.scan_stopped, devices.size(), devices.size()));
        String editingSubnet = subnet.toString();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), 0);
        TextView hint = new TextView(this);
        hint.setText(R.string.edit_hint);
        form.addView(hint);
        EditText name = field(form, R.string.device_name, R.string.device_name, previous == null ? "" : previous.name);
        EditText mac = field(form, R.string.mac_address, R.string.mac_hint, previous == null ? "" : previous.mac);
        EditText ip = field(form, R.string.ip_address, R.string.ip_hint, previous == null ? "" : previous.ip);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle(previous == null ? R.string.add_device : R.string.edit_device).setView(scroll)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String normalized;
            try {
                normalized = MagicPacket.normalizeMac(mac.getText().toString());
            } catch (IllegalArgumentException error) {
                mac.setError(error.getMessage());
                return;
            }
            if (name.length() > 253) {
                name.setError("Use a name of at most 253 characters");
                return;
            }
            try {
                Device edited = new Device(name.getText().toString(), normalized, ip.getText().toString(),
                        editingSubnet, previous != null && previous.seen, true);
                if (subnet == null || !editingSubnet.equals(subnet.toString())) {
                    throw new IllegalArgumentException("The network changed; reopen the device editor");
                }
                if (previous != null) devices.remove(previous.ip);
                devices.put(edited.ip, edited);
                loaded = true;
                adapter.submit(devices.values());
                if (persist()) {
                    editor.dismiss();
                    if (afterSave != null) afterSave.accept(edited);
                }
            } catch (IllegalArgumentException error) {
                ip.setError(error.getMessage());
            }
        }));
        editor.show();
    }

    private EditText field(LinearLayout form, int labelResource, int hintResource, String value) {
        TextView label = new TextView(this);
        label.setText(labelResource);
        label.setPadding(0, dp(12), 0, 0);
        form.addView(label);
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setHint(hintResource);
        field.setText(value);
        label.setLabelFor(field.getId());
        form.addView(field);
        return field;
    }

    private void wake(Device device) {
        if (device.mac.isEmpty()) {
            Toast.makeText(this, R.string.missing_mac, Toast.LENGTH_LONG).show();
            edit(device, this::wake);
            return;
        }
        wakeWorker.execute(() -> {
            try {
                WakeSender.send(NetworkEnvironment.detect(getApplicationContext()), device);
                main.post(() -> {
                    if (!isDestroyed()) Toast.makeText(this, getString(R.string.packet_sent,
                            device.name.isEmpty() ? device.ip : device.name), Toast.LENGTH_LONG).show();
                });
            } catch (IOException | IllegalArgumentException | SecurityException error) {
                main.post(() -> {
                    if (!isDestroyed()) showError(error);
                });
            }
        });
    }

    private void pin(Device device) {
        if (device.mac.isEmpty()) {
            Toast.makeText(this, R.string.missing_mac, Toast.LENGTH_LONG).show();
            edit(device, this::pin);
            return;
        }
        try {
            WakeShortcuts.pin(this, device, store);
            Toast.makeText(this, R.string.shortcut_requested, Toast.LENGTH_LONG).show();
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException error) {
            showError(error);
        }
    }

    private void showError(Exception error) {
        Log.e("WOLAndroid", "Operation failed", error);
        status.setText(error.getMessage());
        if (started) Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
