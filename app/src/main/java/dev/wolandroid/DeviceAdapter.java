package dev.wolandroid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import dev.wolandroid.net.Ipv4Subnet;

final class DeviceAdapter extends BaseAdapter {
    private final Context context;
    private final List<Device> devices = new ArrayList<>();

    DeviceAdapter(Context context) {
        this.context = context;
    }

    void submit(Collection<Device> values) {
        devices.clear();
        devices.addAll(values);
        devices.sort(Comparator.comparingLong(device -> Ipv4Subnet.parseAddress(device.ip)));
        notifyDataSetChanged();
    }

    @Override public int getCount() { return devices.size(); }
    @Override public Device getItem(int position) { return devices.get(position); }
    @Override public long getItemId(int position) { return Ipv4Subnet.parseAddress(getItem(position).ip); }
    @Override public boolean hasStableIds() { return true; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        View row = recycled != null ? recycled
                : LayoutInflater.from(context).inflate(R.layout.device_row, parent, false);
        Device device = getItem(position);
        String name = device.name.isEmpty() ? context.getString(R.string.unknown_name) : device.name;
        String mac = device.mac.isEmpty() ? context.getString(R.string.unknown_mac) : device.mac;
        String state = context.getString(device.seen ? R.string.seen : R.string.saved);
        if (device.savedMac) state += "\n" + context.getString(R.string.cached_mac);
        ((TextView) row.findViewById(R.id.name)).setText(name);
        ((TextView) row.findViewById(R.id.mac)).setText(mac);
        ((TextView) row.findViewById(R.id.ip)).setText(device.ip);
        ((TextView) row.findViewById(R.id.device_state)).setText(state);
        row.setContentDescription(context.getString(R.string.device_description,
                name, mac, device.ip, state));
        return row;
    }
}
