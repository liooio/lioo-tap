package com.lioo.autoclick;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/** Handles ACTION_OUTSIDE at the window root because ViewGroup does not route it to children. */
final class OutsideTouchLayout extends LinearLayout {
    interface Listener {
        void onOutsideTouch(float screenX, float screenY);
    }

    private final Listener listener;

    OutsideTouchLayout(Context context, Listener listener) {
        super(context);
        this.listener = listener;
    }

    static boolean contains(View view, float screenX, float screenY) {
        if (view == null) {
            return false;
        }
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        return screenX >= position[0] && screenX < position[0] + view.getWidth()
                && screenY >= position[1] && screenY < position[1] + view.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            listener.onOutsideTouch(event.getRawX(), event.getRawY());
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
}
