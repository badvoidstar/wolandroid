package dev.wolandroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.wolandroid.net.NetworkEnvironment;
import dev.wolandroid.net.WakeSender;

public final class ShortcutWakeActivity extends Activity {
    private ExecutorService worker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        Device device;
        try {
            if (!WakeShortcuts.ACTION_WAKE.equals(getIntent().getAction())) {
                throw new IOException("Unrecognized shortcut action");
            }
            device = new DeviceStore(this).loadShortcut(
                    getIntent().getStringExtra(WakeShortcuts.EXTRA_TARGET));
        } catch (IOException error) {
            fail(error);
            finishAndRemoveTask();
            return;
        }
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                WakeSender.send(NetworkEnvironment.detect(getApplicationContext()), device);
            } catch (IOException | IllegalArgumentException | SecurityException error) {
                Log.e("WOLAndroid", "Shortcut wake failed", error);
                runOnUiThread(() -> fail(error));
            } finally {
                worker.shutdown();
                runOnUiThread(this::finishAndRemoveTask);
            }
        });
    }

    private void fail(Exception error) {
        Log.e("WOLAndroid", "Shortcut could not wake device", error);
        Toast.makeText(this, getString(R.string.wake_error, error.getMessage()), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (worker != null) worker.shutdownNow();
        super.onDestroy();
    }
}
