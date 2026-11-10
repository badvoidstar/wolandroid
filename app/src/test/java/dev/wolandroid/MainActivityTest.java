package dev.wolandroid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.ConnectivityManager;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class MainActivityTest {
    @Test
    public void normalLaunchShowsTheUiAndExplainsAnOfflineNetworkWithoutCrashing() {
        ConnectivityManager manager = RuntimeEnvironment.getApplication()
                .getSystemService(ConnectivityManager.class);
        Shadows.shadowOf(manager).clearAllNetworks();
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            TextView status = activity.findViewById(R.id.status);
            assertTrue(status.getText().toString().contains("Connect to Wi-Fi or a VPN"));
            assertFalse(activity.isFinishing());
            Button refresh = activity.findViewById(R.id.refresh);
            refresh.performClick();
            assertTrue(status.getText().toString().contains("Connect to Wi-Fi or a VPN"));
        }
    }
}
