package dev.wolandroid;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import java.io.IOException;

final class WakeShortcuts {
    static final String ACTION_WAKE = "dev.wolandroid.action.WAKE";
    static final String EXTRA_TARGET = "target_id";

    private WakeShortcuts() {}

    static Intent intent(Context context, String targetId) {
        return new Intent(context, ShortcutWakeActivity.class).setAction(ACTION_WAKE)
                .putExtra(EXTRA_TARGET, targetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }

    static void pin(Context context, Device device, DeviceStore store) throws IOException {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            throw new IOException(context.getString(R.string.shortcut_unsupported));
        }
        String id = store.saveShortcut(device);
        String label = context.getString(R.string.shortcut_label,
                device.name.isEmpty() ? device.ip : device.name);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, id)
                .setShortLabel(label)
                .setLongLabel(label + " (" + device.mac + ")")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent(context, id))
                .build();
        if (!manager.requestPinShortcut(shortcut, null)) {
            throw new IOException(context.getString(R.string.shortcut_rejected));
        }
    }
}
