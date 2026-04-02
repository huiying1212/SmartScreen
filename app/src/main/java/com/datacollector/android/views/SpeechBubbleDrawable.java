package com.datacollector.android.views;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Speech-bubble drawable with a small triangular tail.
 * The tail can point downward (bubble above icon) or upward (bubble below icon),
 * and its horizontal position is adjustable.
 */
public class SpeechBubbleDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private float cornerRadius = 32f;
    private float tailWidth = 20f;
    private float tailHeight = 14f;
    /** 0..1, horizontal position of the tail relative to bubble width */
    private float tailPosition = 0.5f;
    /** true = tail at bottom (bubble above icon), false = tail at top (bubble below icon) */
    private boolean tailAtBottom = true;

    public SpeechBubbleDrawable() {
        paint.setColor(0xD8FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x0C000000);
        shadowPaint.setStyle(Paint.Style.FILL);
    }

    public void setTailAtBottom(boolean atBottom) {
        this.tailAtBottom = atBottom;
        invalidateSelf();
    }

    public void setTailPosition(float position) {
        this.tailPosition = Math.max(0.1f, Math.min(0.9f, position));
        invalidateSelf();
    }

    public float getTailHeight() {
        return tailHeight;
    }

    @Override
    public void draw(Canvas canvas) {
        float w = getBounds().width();
        float h = getBounds().height();
        if (w == 0 || h == 0) return;

        float bodyTop, bodyBottom;
        if (tailAtBottom) {
            bodyTop = 0;
            bodyBottom = h - tailHeight;
        } else {
            bodyTop = tailHeight;
            bodyBottom = h;
        }

        // Shadow (slight offset)
        rect.set(1, bodyTop + 2, w - 1, bodyBottom + 2);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shadowPaint);

        // Main bubble body
        path.reset();
        rect.set(0, bodyTop, w, bodyBottom);
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);

        // Tail triangle
        float tailCx = w * tailPosition;
        float tailLeft = tailCx - tailWidth / 2f;
        float tailRight = tailCx + tailWidth / 2f;

        Path tailPath = new Path();
        if (tailAtBottom) {
            tailPath.moveTo(tailLeft, bodyBottom - 1);
            tailPath.lineTo(tailCx, h);
            tailPath.lineTo(tailRight, bodyBottom - 1);
        } else {
            tailPath.moveTo(tailLeft, bodyTop + 1);
            tailPath.lineTo(tailCx, 0);
            tailPath.lineTo(tailRight, bodyTop + 1);
        }
        tailPath.close();
        path.addPath(tailPath);

        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
