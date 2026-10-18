package com.lioo.autoclick;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Circular floating control using the existing app logo without its transparent padding. */
final class LogoBallView extends View {
    private final Bitmap logo;
    private final Rect logoBounds;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path circle = new Path();

    LogoBallView(Context context) {
        super(context);
        logo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        logoBounds = contentBounds(logo);
        setElevation(dp(6));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        circle.reset();
        circle.addCircle(getWidth() / 2f, getHeight() / 2f, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(circle);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1769FF);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(logo, logoBounds,
                new RectF(0, 0, getWidth(), getHeight()), paint);
        canvas.restore();

        if (isSelected()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xFFB4DCFF);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                    radius - paint.getStrokeWidth() / 2f, paint);
        }
    }

    private Rect contentBounds(Bitmap bitmap) {
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = 0;
        int bottom = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 32) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return left > right ? new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight())
                : new Rect(left, top, right + 1, bottom + 1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
