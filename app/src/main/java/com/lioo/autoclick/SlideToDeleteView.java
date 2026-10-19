package com.lioo.autoclick;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 只有滑块到达末端才会触发的防误操作删除控件。 */
final class SlideToDeleteView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackBounds = new RectF();
    private final RectF thumbBounds = new RectF();
    private final Runnable action;
    private float thumbX;
    private float downOffset;
    private boolean dragging;
    private boolean completed;

    SlideToDeleteView(Context context, Runnable action) {
        super(context);
        this.action = action;
        setContentDescription("向右完整滑动确认删除");
        setFocusable(true);
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        paint.setColor(0xFFFFECEA);
        trackBounds.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(trackBounds, radius, radius, paint);

        paint.setColor(0xFF9B4040);
        paint.setTextSize(sp(14));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText("向右滑动确认删除", getWidth() / 2f, baseline, paint);

        float thumbSize = getHeight() - dp(8);
        float top = dp(4);
        paint.setColor(0xFFBA1A1A);
        thumbBounds.set(thumbX + dp(4), top,
                thumbX + dp(4) + thumbSize, top + thumbSize);
        canvas.drawRoundRect(thumbBounds, thumbSize / 2f, thumbSize / 2f, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(sp(21));
        canvas.drawText("››", thumbX + dp(4) + thumbSize / 2f, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (completed) {
            return true;
        }
        float thumbSize = getHeight() - dp(8);
        float max = Math.max(0, getWidth() - getHeight());
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (event.getX() > thumbX + getHeight()) {
                return false;
            }
            dragging = true;
            downOffset = event.getX() - thumbX;
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging) {
            thumbX = Math.max(0, Math.min(max, event.getX() - downOffset));
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && dragging) {
            dragging = false;
            if (max == 0 || thumbX >= max * 0.92f) {
                completed = true;
                thumbX = max;
                invalidate();
                performClick();
                postDelayed(action, 100);
            } else {
                animateBack();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            animateBack();
            return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    private void animateBack() {
        ValueAnimator animator = ValueAnimator.ofFloat(thumbX, 0);
        animator.setDuration(180);
        animator.addUpdateListener(value -> {
            thumbX = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
