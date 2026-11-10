package dev.wolandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DeviceListGesturesTest {
    private ListView list;
    private final List<Device> wakes = new ArrayList<>();
    private final List<Device> options = new ArrayList<>();
    private long clock;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        list = new ListView(activity);
        activity.setContentView(list);
        DeviceAdapter adapter = new DeviceAdapter(activity);
        List<Device> devices = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            devices.add(new Device("host " + i, "AA:BB:CC:DD:EE:02", "192.168.1." + i,
                    "192.168.1.0/24", true, false));
        }
        adapter.submit(devices);
        list.setAdapter(adapter);
        DeviceListGestures.attach(list, wakes::add, options::add);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1200);
        clock = SystemClock.uptimeMillis() + 10;
    }

    @Test
    public void doubleTapWakesTheRowUnderTheFingerOnce() {
        float y = list.getChildAt(0).getHeight() / 2f;
        tap(100, y, clock);
        tap(100, y, clock + 100);
        assertEquals(1, wakes.size());
        assertEquals("192.168.1.1", wakes.get(0).ip);
    }

    @Test
    public void aSwipeFromAnyColumnScrollsWithoutSendingAWake() {
        for (float x : new float[]{100, 550, 1000}) {
            long down = clock;
            event(MotionEvent.ACTION_DOWN, x, 1000, down, down);
            event(MotionEvent.ACTION_MOVE, x, 750, down, down + 30);
            event(MotionEvent.ACTION_MOVE, x, 400, down, down + 60);
            event(MotionEvent.ACTION_UP, x, 300, down, down + 80);
            clock += 1000;
        }
        assertTrue(wakes.isEmpty());
        assertTrue(list.getFirstVisiblePosition() > 0 || list.getChildAt(0).getTop() < 0);
    }

    @Test
    public void longPressOffersDeviceOptions() {
        Device first = (Device) list.getItemAtPosition(0);
        assertTrue(list.getOnItemLongClickListener().onItemLongClick(
                list, list.getChildAt(0), 0, list.getItemIdAtPosition(0)));
        assertEquals(first.ip, options.get(0).ip);
        assertTrue(wakes.isEmpty());
    }

    @Test
    public void tapFollowedByDragDoesNotWake() {
        float y = list.getChildAt(0).getHeight() / 2f;
        tap(100, y, clock);
        long down = clock + 100;
        event(MotionEvent.ACTION_DOWN, 100, y, down, down);
        event(MotionEvent.ACTION_MOVE, 100, y + 100, down, down + 30);
        event(MotionEvent.ACTION_UP, 100, y, down, down + 60);
        assertTrue(wakes.isEmpty());
    }

    @Test
    public void tapFollowedByLongPressDoesNotAlsoWake() {
        float y = list.getChildAt(0).getHeight() / 2f;
        tap(100, y, clock);
        long down = clock + 100;
        event(MotionEvent.ACTION_DOWN, 100, y, down, down);
        list.getOnItemLongClickListener().onItemLongClick(
                list, list.getChildAt(0), 0, list.getItemIdAtPosition(0));
        event(MotionEvent.ACTION_UP, 100, y, down, down + 600);
        assertEquals(1, options.size());
        assertTrue(wakes.isEmpty());
    }

    private void tap(float x, float y, long down) {
        event(MotionEvent.ACTION_DOWN, x, y, down, down);
        event(MotionEvent.ACTION_UP, x, y, down, down + 20);
    }

    private void event(int action, float x, float y, long down, long time) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        list.dispatchTouchEvent(event);
        event.recycle();
    }
}
