package com.lioo.autoclick;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/** 为步进按钮增加按住连续调整，同时保留普通点击和无障碍点击。 */
final class LongPressStepper {
    private static final long INITIAL_DELAY_MS = 350L;
    private static final long REPEAT_INTERVAL_MS = 65L;

    private LongPressStepper() {
    }

    static void attach(View button, Runnable increment) {
        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] held = {false};
        boolean[] repeated = {false};

        Runnable repeatTask = new Runnable() {
            @Override
            public void run() {
                if (!held[0] || !button.isPressed()) {
                    return;
                }
                repeated[0] = true;
                increment.run();
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };

        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                held[0] = true;
                repeated[0] = false;
                handler.removeCallbacks(repeatTask);
                handler.postDelayed(repeatTask, INITIAL_DELAY_MS);
                return false;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                held[0] = false;
                handler.removeCallbacks(repeatTask);
                if (repeated[0]) {
                    view.setPressed(false);
                    return true;
                }
            }
            return false;
        });
    }
}
