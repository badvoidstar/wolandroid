package dev.wolandroid;

import android.annotation.SuppressLint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import android.widget.ListView;

import java.util.function.Consumer;

final class DeviceListGestures {
    private DeviceListGestures() {}

    @SuppressLint("ClickableViewAccessibility") // TalkBack uses the native item-click action below.
    static void attach(ListView list, Consumer<Device> wake, Consumer<Device> options) {
        int slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        class RowGestures extends GestureDetector.SimpleOnGestureListener {
            private Device firstTap;
            private float downX;
            private float downY;
            private boolean moved;

            @Override public boolean onDown(MotionEvent event) { return true; }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                firstTap = itemAt(list, event);
                return false;
            }

            @Override public boolean onDoubleTap(MotionEvent event) { return true; }

            @Override
            public boolean onDoubleTapEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    moved = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    moved |= Math.abs(event.getX() - downX) > slop
                            || Math.abs(event.getY() - downY) > slop;
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    moved = true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    Device secondTap = itemAt(list, event);
                    if (!moved && firstTap != null && secondTap != null
                            && firstTap.ip.equals(secondTap.ip)
                            && firstTap.subnet.equals(secondTap.subnet)) wake.accept(secondTap);
                    firstTap = null;
                }
                return true;
            }

            void cancelTap() {
                firstTap = null;
                moved = true;
            }
        }
        RowGestures gestures = new RowGestures();
        GestureDetector detector = new GestureDetector(list.getContext(), gestures);
        detector.setIsLongpressEnabled(false);
        AccessibilityManager accessibility = list.getContext()
                .getSystemService(AccessibilityManager.class);
        list.setOnTouchListener((view, event) -> {
            if (accessibility == null || !accessibility.isTouchExplorationEnabled()) {
                detector.onTouchEvent(event);
            }
            // ListView owns the same events, including its native velocity tracking and fling decay.
            return false;
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            gestures.cancelTap();
            options.accept((Device) parent.getItemAtPosition(position));
            return true;
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (accessibility != null && accessibility.isTouchExplorationEnabled()) {
                wake.accept((Device) parent.getItemAtPosition(position));
            }
        });
    }

    private static Device itemAt(ListView list, MotionEvent event) {
        int position = list.pointToPosition((int) event.getX(), (int) event.getY());
        return position == ListView.INVALID_POSITION ? null : (Device) list.getItemAtPosition(position);
    }
}
