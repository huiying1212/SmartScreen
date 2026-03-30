package com.datacollector.android.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Continuously-animated emoji face driven by a single {@code stress} value in [0, 1].
 * <p>
 * All facial features are interpolated — there are no discrete "mood stages".
 * A {@link FaceStyle} controls the color palette / visual tone without changing the
 * mood logic, so users pick a *look* rather than a fixed emotion.
 * <p>
 * Usage: call {@link #setStress(float)} whenever the UUT value changes; the view
 * will animate smoothly from its current state to the new target over ~800 ms.
 */
public class MoodFaceView extends View {

    /**
     * Visual style presets — same face geometry, different color palettes.
     * Each style defines the face gradient endpoints, line color, blush tint,
     * dark-circle tint, sweat tint, and spiral tint.
     */
    public enum FaceStyle {
        CLASSIC("经典",
                0xFFFFD54F, 0xFFFFB300, 0xFF333333, 0xFFFF80AB,
                0xFFCE93D8, 0xFF64B5F6, 0xFF9E9E9E),
        WARM("暖阳",
                0xFFFFCC80, 0xFFFF8A65, 0xFF5D4037, 0xFFFF8A80,
                0xFFCE93D8, 0xFF81D4FA, 0xFFBCAAA4),
        COOL("清凉",
                0xFFB3E5FC, 0xFF4FC3F7, 0xFF37474F, 0xFFB39DDB,
                0xFF90CAF9, 0xFF80DEEA, 0xFF78909C),
        MONO("水墨",
                0xFFE0E0E0, 0xFF9E9E9E, 0xFF212121, 0xFFBDBDBD,
                0xFF757575, 0xFF90A4AE, 0xFF616161),
        MATCHA("抹茶",
                0xFFC8E6C9, 0xFF81C784, 0xFF33691E, 0xFFA5D6A7,
                0xFF80CBC4, 0xFF80DEEA, 0xFF66BB6A),
        SUNSET("落日",
                0xFFFFE0B2, 0xFFFF7043, 0xFF4E342E, 0xFFEF9A9A,
                0xFFCE93D8, 0xFFFFAB91, 0xFFBCAAA4);

        public final String labelCn;
        /** Face fill at stress=0 */
        public final int faceHappy;
        /** Face fill at stress=1 */
        public final int faceExhausted;
        /** Stroke/line color for eyes, mouth, eyebrows */
        public final int lineColor;
        /** Blush tint */
        public final int blushTint;
        /** Dark-circle tint */
        public final int darkCircleTint;
        /** Sweat-drop tint */
        public final int sweatTint;
        /** Spiral tint */
        public final int spiralTint;

        FaceStyle(String labelCn,
                  int faceHappy, int faceExhausted, int lineColor, int blushTint,
                  int darkCircleTint, int sweatTint, int spiralTint) {
            this.labelCn = labelCn;
            this.faceHappy = faceHappy;
            this.faceExhausted = faceExhausted;
            this.lineColor = lineColor;
            this.blushTint = blushTint;
            this.darkCircleTint = darkCircleTint;
            this.sweatTint = sweatTint;
            this.spiralTint = spiralTint;
        }

        public static FaceStyle fromName(String name) {
            if (name != null) {
                for (FaceStyle s : values()) {
                    if (s.name().equals(name) || s.labelCn.equals(name)) return s;
                }
            }
            return CLASSIC;
        }
    }

    private FaceStyle style = FaceStyle.CLASSIC;
    private float currentStress = 0f;
    private float targetStress = 0f;
    private ValueAnimator stressAnimator;

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spiralPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyebrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mouthPath = new Path();
    private final Path spiralPath = new Path();
    private final Path eyebrowPath = new Path();
    private final RectF eyeOval = new RectF();

    public MoodFaceView(Context context) {
        super(context);
        init();
    }

    public MoodFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MoodFaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        applyStyleColors();

        mouthPaint.setStyle(Paint.Style.STROKE);
        mouthPaint.setStrokeCap(Paint.Cap.ROUND);
        darkCirclePaint.setStyle(Paint.Style.FILL);
        spiralPaint.setStyle(Paint.Style.STROKE);
        spiralPaint.setStrokeCap(Paint.Cap.ROUND);
        sweatPaint.setStyle(Paint.Style.FILL);
        blushPaint.setStyle(Paint.Style.FILL);
        eyebrowPaint.setStyle(Paint.Style.STROKE);
        eyebrowPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setColor(Color.WHITE);
        highlightPaint.setStyle(Paint.Style.FILL);
        eyePaint.setStyle(Paint.Style.FILL);
    }

    private void applyStyleColors() {
        eyePaint.setColor(style.lineColor);
        mouthPaint.setColor(style.lineColor);
        eyebrowPaint.setColor(style.lineColor);
    }

    public void setFaceStyle(FaceStyle newStyle) {
        if (newStyle == null || newStyle == style) return;
        style = newStyle;
        applyStyleColors();
        invalidate();
    }

    public FaceStyle getFaceStyle() {
        return style;
    }

    /**
     * Smoothly animate to a new stress level.
     *
     * @param stress value in [0, 1] where 0 = perfectly happy, 1 = total exhaustion
     */
    public void setStress(float stress) {
        stress = Math.max(0f, Math.min(1f, stress));
        if (Float.compare(stress, targetStress) == 0) return;
        targetStress = stress;

        if (stressAnimator != null && stressAnimator.isRunning()) {
            stressAnimator.cancel();
        }

        stressAnimator = ValueAnimator.ofFloat(currentStress, targetStress);
        stressAnimator.setDuration(800);
        stressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        stressAnimator.addUpdateListener(anim -> {
            currentStress = (float) anim.getAnimatedValue();
            invalidate();
        });
        stressAnimator.start();
    }

    /**
     * Snap to a stress level without animation (e.g. on first display).
     */
    public void setStressImmediate(float stress) {
        stress = Math.max(0f, Math.min(1f, stress));
        currentStress = stress;
        targetStress = stress;
        if (stressAnimator != null) stressAnimator.cancel();
        invalidate();
    }

    public float getCurrentStress() {
        return currentStress;
    }

    // ── Drawing ──────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        float r = size * 0.46f;
        float s = currentStress;

        drawFace(canvas, cx, cy, r, s);
        drawEyebrows(canvas, cx, cy, r, s);
        drawEyes(canvas, cx, cy, r, s);
        drawMouth(canvas, cx, cy, r, s);
        drawDarkCircles(canvas, cx, cy, r, s);
        drawBlush(canvas, cx, cy, r, s);
        drawSweatDrop(canvas, cx, cy, r, s);
        drawSpiral(canvas, cx, cy, r, s);
    }

    private void drawFace(Canvas canvas, float cx, float cy, float r, float s) {
        facePaint.setColor(lerpColor(style.faceHappy, style.faceExhausted, s));
        facePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r, facePaint);
    }

    private void drawEyebrows(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.15f) return;

        float browAlpha = Math.min(1f, (s - 0.15f) / 0.25f);
        eyebrowPaint.setAlpha((int) (browAlpha * 255));
        eyebrowPaint.setStrokeWidth(r * 0.06f);

        float eyeSpacing = r * 0.36f;
        float eyeY = cy - r * 0.15f;
        float browY = eyeY - r * 0.28f;

        float innerDrop = lerp(0f, r * 0.12f, s);

        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeSpacing;
            eyebrowPath.reset();
            eyebrowPath.moveTo(ex - r * 0.16f, browY + (side == -1 ? innerDrop : 0));
            eyebrowPath.quadTo(ex, browY - r * 0.06f,
                    ex + r * 0.16f, browY + (side == 1 ? innerDrop : 0));
            canvas.drawPath(eyebrowPath, eyebrowPaint);
        }
    }

    private void drawEyes(Canvas canvas, float cx, float cy, float r, float s) {
        float eyeSpacing = r * 0.36f;
        float eyeY = cy - r * 0.15f;

        float eyeRadiusX = r * lerp(0.13f, 0.10f, s);
        float eyeRadiusY = r * lerp(0.13f, 0.04f, s);

        float pupilR = r * lerp(0.06f, 0.03f, s);
        float highlightR = r * lerp(0.035f, 0.015f, s);

        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeSpacing;

            if (s < 0.85f) {
                eyeOval.set(ex - eyeRadiusX, eyeY - eyeRadiusY,
                        ex + eyeRadiusX, eyeY + eyeRadiusY);
                canvas.drawOval(eyeOval, eyePaint);

                if (s < 0.7f) {
                    canvas.drawCircle(ex, eyeY, highlightR, highlightPaint);
                }
            } else {
                float xLen = r * 0.10f;
                float xAlpha = (s - 0.85f) / 0.15f;
                Paint xPaint = new Paint(mouthPaint);
                xPaint.setStrokeWidth(r * 0.06f);
                xPaint.setAlpha((int) (255 * xAlpha));
                xPaint.setStyle(Paint.Style.STROKE);

                eyeOval.set(ex - eyeRadiusX, eyeY - eyeRadiusY,
                        ex + eyeRadiusX, eyeY + eyeRadiusY);
                eyePaint.setAlpha((int) (255 * (1f - xAlpha)));
                canvas.drawOval(eyeOval, eyePaint);
                eyePaint.setAlpha(255);

                canvas.drawLine(ex - xLen, eyeY - xLen, ex + xLen, eyeY + xLen, xPaint);
                canvas.drawLine(ex + xLen, eyeY - xLen, ex - xLen, eyeY + xLen, xPaint);
            }
        }
    }

    private void drawMouth(Canvas canvas, float cx, float cy, float r, float s) {
        mouthPaint.setStrokeWidth(r * 0.07f);

        float mouthY = cy + r * 0.35f;
        float mouthHalfW = r * 0.30f;

        float curveOffset = lerp(r * 0.22f, -r * 0.18f, s);

        if (s > 0.75f) {
            float waviness = (s - 0.75f) / 0.25f;
            float wave = waviness * r * 0.08f;

            mouthPath.reset();
            mouthPath.moveTo(cx - mouthHalfW, mouthY);
            mouthPath.cubicTo(
                    cx - mouthHalfW * 0.5f, mouthY + curveOffset + wave,
                    cx + mouthHalfW * 0.5f, mouthY + curveOffset - wave,
                    cx + mouthHalfW, mouthY);
            canvas.drawPath(mouthPath, mouthPaint);
        } else {
            mouthPath.reset();
            mouthPath.moveTo(cx - mouthHalfW, mouthY);
            mouthPath.quadTo(cx, mouthY + curveOffset, cx + mouthHalfW, mouthY);
            canvas.drawPath(mouthPath, mouthPaint);
        }
    }

    private void drawDarkCircles(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.3f) return;

        float intensity = (s - 0.3f) / 0.7f;
        int alpha = (int) (intensity * 90);
        darkCirclePaint.setColor(withAlpha(style.darkCircleTint, alpha));

        float eyeSpacing = r * 0.36f;
        float eyeY = cy - r * 0.15f;
        float dcY = eyeY + r * lerp(0.14f, 0.10f, s);
        float dcRx = r * 0.14f;
        float dcRy = r * 0.06f * intensity;

        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeSpacing;
            eyeOval.set(ex - dcRx, dcY - dcRy, ex + dcRx, dcY + dcRy);
            canvas.drawOval(eyeOval, darkCirclePaint);
        }
    }

    private void drawBlush(Canvas canvas, float cx, float cy, float r, float s) {
        if (s > 0.4f) return;

        float blushAlpha = (1f - s / 0.4f) * 50;
        blushPaint.setColor(withAlpha(style.blushTint, (int) blushAlpha));

        float eyeSpacing = r * 0.36f;
        float blushY = cy + r * 0.08f;
        float blushR = r * 0.10f;

        for (int side = -1; side <= 1; side += 2) {
            canvas.drawCircle(cx + side * (eyeSpacing + r * 0.08f), blushY, blushR, blushPaint);
        }
    }

    private void drawSweatDrop(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.5f) return;

        float intensity = (s - 0.5f) / 0.5f;
        int alpha = (int) (intensity * 200);
        sweatPaint.setColor(withAlpha(style.sweatTint, alpha));

        float dropX = cx + r * 0.62f;
        float dropY = cy - r * 0.50f;
        float dropR = r * 0.06f * (0.5f + intensity * 0.5f);

        Path dropPath = new Path();
        dropPath.moveTo(dropX, dropY - dropR * 2.5f);
        dropPath.quadTo(dropX + dropR * 1.2f, dropY, dropX, dropY + dropR);
        dropPath.quadTo(dropX - dropR * 1.2f, dropY, dropX, dropY - dropR * 2.5f);
        dropPath.close();
        canvas.drawPath(dropPath, sweatPaint);
    }

    private void drawSpiral(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.75f) return;

        float intensity = (s - 0.75f) / 0.25f;
        int alpha = (int) (intensity * 180);
        spiralPaint.setColor(withAlpha(style.spiralTint, alpha));
        spiralPaint.setStrokeWidth(r * 0.04f);

        float sx = cx;
        float sy = cy - r * 1.12f;
        float spiralR = r * 0.12f * (0.5f + intensity * 0.5f);

        spiralPath.reset();
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = t * 3f * (float) Math.PI;
            float rr = spiralR * (0.3f + t * 0.7f);
            float px = sx + rr * (float) Math.cos(angle);
            float py = sy + rr * (float) Math.sin(angle) * 0.6f;
            if (i == 0) spiralPath.moveTo(px, py);
            else spiralPath.lineTo(px, py);
        }
        canvas.drawPath(spiralPath, spiralPaint);
    }

    // ── Utilities ────────────────────────────────────────────────

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a = lerp(Color.alpha(c1), Color.alpha(c2), t);
        int r = lerp(Color.red(c1), Color.red(c2), t);
        int g = lerp(Color.green(c1), Color.green(c2), t);
        int b = lerp(Color.blue(c1), Color.blue(c2), t);
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = dpToPx(56);
        int w = resolveSize(defaultSize, widthMeasureSpec);
        int h = resolveSize(defaultSize, heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (stressAnimator != null) {
            stressAnimator.cancel();
            stressAnimator = null;
        }
    }
}
