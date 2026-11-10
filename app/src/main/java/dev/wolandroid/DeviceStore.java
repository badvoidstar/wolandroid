package dev.wolandroid;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DeviceStore {
    private final SharedPreferences preferences;

    public DeviceStore(Context context) {
        preferences = context.getSharedPreferences("devices", Context.MODE_PRIVATE);
    }

    public List<Device> load(String subnet) throws IOException {
        try {
            JSONArray array = new JSONArray(preferences.getString("subnet:" + subnet, "[]"));
            List<Device> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Device device = decode(array.getJSONObject(i));
                if (!device.subnet.equals(subnet)) throw new IOException("Saved device subnet is invalid");
                result.add(device);
            }
            return result;
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Saved devices could not be read", error);
        }
    }

    public void save(String subnet, Collection<Device> devices) throws IOException {
        JSONArray array = new JSONArray();
        try {
            for (Device device : devices) array.put(encode(device));
        } catch (JSONException error) {
            throw new IOException("Saved devices could not be encoded", error);
        }
        if (!preferences.edit().putString("subnet:" + subnet, array.toString()).commit()) {
            throw new IOException("Android could not save device details");
        }
    }

    public String saveShortcut(Device device) throws IOException {
        if (device.mac.isEmpty()) throw new IOException("A MAC address is required for a wake shortcut");
        String id = UUID.randomUUID().toString();
        try {
            if (!preferences.edit().putString("shortcut:" + id, encode(device).toString()).commit()) {
                throw new IOException("Android could not save the shortcut target");
            }
        } catch (JSONException error) {
            throw new IOException("Shortcut target could not be encoded", error);
        }
        return id;
    }

    public Device loadShortcut(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IOException("Invalid wake shortcut");
        String json = preferences.getString("shortcut:" + id, null);
        if (json == null) throw new IOException("This shortcut no longer has a saved device; recreate it");
        try {
            Device device = decode(new JSONObject(json));
            if (device.mac.isEmpty()) throw new IOException("The shortcut has no MAC address");
            return device;
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Shortcut target could not be read", error);
        }
    }

    private static JSONObject encode(Device device) throws JSONException {
        return new JSONObject().put("name", device.name).put("mac", device.mac)
                .put("ip", device.ip).put("subnet", device.subnet);
    }

    private static Device decode(JSONObject object) throws JSONException {
        return new Device(object.getString("name"), object.getString("mac"),
                object.getString("ip"), object.getString("subnet"), false, true);
    }
}
