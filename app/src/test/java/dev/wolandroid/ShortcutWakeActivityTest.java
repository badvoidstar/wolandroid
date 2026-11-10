package dev.wolandroid;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShortcutWakeActivityTest {
    @Test
    public void invalidShortcutFinishesWithoutStartingTheMainUi() {
        Intent intent = WakeShortcuts.intent(RuntimeEnvironment.getApplication(),
                "6d804e61-7851-4685-983a-94cc1ad82fea");
        try (ActivityController<ShortcutWakeActivity> controller =
                     Robolectric.buildActivity(ShortcutWakeActivity.class, intent).create()) {
            ShortcutWakeActivity activity = controller.get();
            assertTrue(activity.isFinishing());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }
}
