package com.datacollector.android.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
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

    /** Overall opacity of the face [0..255]. 255 = fully opaque, default = semi-transparent. */
    private int globalAlpha = 178;  // ~70% opacity — lets screen content show through

    private FaceStyle style = FaceStyle.CLASSIC;
    private float currentStress = 0f;
    private float targetStress = 0f;
    private ValueAnimator stressAnimator;

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint faceShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint faceOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint irisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spiralPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweatHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyebrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nosePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mouthPath = new Path();
    private final Path spiralPath = new Path();
    private final Path eyebrowPath = new Path();
    private final Path nosePath = new Path();
    private final RectF eyeOval = new RectF();
    private final RectF tmpOval = new RectF();

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
        mouthFillPaint.setStyle(Paint.Style.FILL);
        darkCirclePaint.setStyle(Paint.Style.FILL);
        spiralPaint.setStyle(Paint.Style.STROKE);
        spiralPaint.setStrokeCap(Paint.Cap.ROUND);
        sweatPaint.setStyle(Paint.Style.FILL);
        sweatHighlightPaint.setStyle(Paint.Style.FILL);
        sweatHighlightPaint.setColor(Color.WHITE);
        blushPaint.setStyle(Paint.Style.FILL);
        eyebrowPaint.setStyle(Paint.Style.STROKE);
        eyebrowPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setColor(Color.WHITE);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightSmallPaint.setColor(0xCCFFFFFF);
        highlightSmallPaint.setStyle(Paint.Style.FILL);
        eyePaint.setStyle(Paint.Style.FILL);
        eyeWhitePaint.setStyle(Paint.Style.FILL);
        eyeWhitePaint.setColor(Color.WHITE);
        irisPaint.setStyle(Paint.Style.FILL);
        pupilPaint.setStyle(Paint.Style.FILL);
        pupilPaint.setColor(0xFF1A1A2E);
        faceShadowPaint.setStyle(Paint.Style.FILL);
        faceOutlinePaint.setStyle(Paint.Style.STROKE);
        faceOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        nosePaint.setStyle(Paint.Style.STROKE);
        nosePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void applyStyleColors() {
        eyePaint.setColor(style.lineColor);
        mouthPaint.setColor(style.lineColor);
        eyebrowPaint.setColor(style.lineColor);
        nosePaint.setColor(withAlpha(style.lineColor, 60));
        irisPaint.setColor(darken(style.lineColor, 0.3f));
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
     * Set the overall opacity of the face.
     * @param alpha 0 (fully transparent) to 255 (fully opaque). Default is 178 (~70%).
     */
    public void setGlobalAlpha(int alpha) {
        globalAlpha = Math.max(0, Math.min(255, alpha));
        invalidate();
    }

    public int getGlobalAlpha() {
        return globalAlpha;
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

        // Apply uniform semi-transparency to the entire face composite.
        // saveLayerAlpha ensures overlapping elements blend correctly
        // instead of each having individual alpha artifacts.
        if (globalAlpha < 255) {
            canvas.saveLayerAlpha(0, 0, w, h, globalAlpha);
        }

        drawFace(canvas, cx, cy, r, s);
        drawEyebrows(canvas, cx, cy, r, s);
        drawEyes(canvas, cx, cy, r, s);
        drawNose(canvas, cx, cy, r, s);
        drawMouth(canvas, cx, cy, r, s);
        drawDarkCircles(canvas, cx, cy, r, s);
        drawBlush(canvas, cx, cy, r, s);
        drawSweatDrop(canvas, cx, cy, r, s);
        drawSpiral(canvas, cx, cy, r, s);

        if (globalAlpha < 255) {
            canvas.restore();
        }
    }

    private void drawFace(Canvas canvas, float cx, float cy, float r, float s) {
        // 1. Subtle drop shadow beneath the face (lighter for semi-transparent look)
        faceShadowPaint.setColor(0x10000000);
        canvas.drawCircle(cx, cy + r * 0.06f, r * 1.02f, faceShadowPaint);

        // 2. Main face fill with radial gradient (lighter top-left, darker bottom-right)
        int baseColor = lerpColor(style.faceHappy, style.faceExhausted, s);
        int lightColor = lighten(baseColor, 0.18f);
        int darkColor = darken(baseColor, 0.10f);
        RadialGradient faceGrad = new RadialGradient(
                cx - r * 0.25f, cy - r * 0.25f, r * 1.6f,
                lightColor, darkColor, Shader.TileMode.CLAMP);
        facePaint.setShader(faceGrad);
        facePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r, facePaint);
        facePaint.setShader(null);

        // 3. Soft inner highlight (top-left crescent for 3D feel)
        int hlColor = withAlpha(Color.WHITE, 45);
        RadialGradient hlGrad = new RadialGradient(
                cx - r * 0.30f, cy - r * 0.35f, r * 0.7f,
                hlColor, 0x00FFFFFF, Shader.TileMode.CLAMP);
        faceShadowPaint.setShader(hlGrad);
        canvas.drawCircle(cx, cy, r, faceShadowPaint);
        faceShadowPaint.setShader(null);

        // 4. Thin face outline for definition
        faceOutlinePaint.setColor(withAlpha(darken(baseColor, 0.25f), 40));
        faceOutlinePaint.setStrokeWidth(r * 0.025f);
        canvas.drawCircle(cx, cy, r - r * 0.012f, faceOutlinePaint);
    }

    private void drawEyebrows(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.12f) return;

        float browAlpha = Math.min(1f, (s - 0.12f) / 0.25f);
        eyebrowPaint.setAlpha((int) (browAlpha * 255));
        eyebrowPaint.setStrokeWidth(r * 0.05f);

        float eyeSpacing = r * 0.36f;
        float eyeY = cy - r * 0.15f;
        float browY = eyeY - r * 0.30f;

        float innerDrop = lerp(0f, r * 0.14f, s);
        float browLen = r * 0.18f;

        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeSpacing;
            eyebrowPath.reset();
            // Smoother cubic bezier eyebrow
            float startX = ex - browLen;
            float endX = ex + browLen;
            float startY = browY + (side == -1 ? innerDrop : 0);
            float endY = browY + (side == 1 ? innerDrop : 0);
            eyebrowPath.moveTo(startX, startY);
            eyebrowPath.cubicTo(
                    ex - browLen * 0.3f, browY - r * 0.08f,
                    ex + browLen * 0.3f, browY - r * 0.08f,
                    endX, endY);
            canvas.drawPath(eyebrowPath, eyebrowPaint);
        }
    }

    private void drawEyes(Canvas canvas, float cx, float cy, float r, float s) {
        float eyeSpacing = r * 0.36f;
        float eyeY = cy - r * 0.15f;

        float eyeRadiusX = r * lerp(0.15f, 0.11f, s);
        float eyeRadiusY = r * lerp(0.15f, 0.045f, s);

        float irisR = r * lerp(0.09f, 0.05f, s);
        float pupilR = r * lerp(0.045f, 0.025f, s);
        float hlR = r * lerp(0.035f, 0.018f, s);
        float hlSmallR = r * lerp(0.018f, 0.008f, s);

        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * eyeSpacing;

            if (s < 0.85f) {
                // Eye white (slightly off-white for warmth)
                eyeOval.set(ex - eyeRadiusX, eyeY - eyeRadiusY,
                        ex + eyeRadiusX, eyeY + eyeRadiusY);
                eyeWhitePaint.setColor(0xFFFAFAFA);
                canvas.drawOval(eyeOval, eyeWhitePaint);

                // Thin eye outline
                Paint eyeOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
                eyeOutline.setStyle(Paint.Style.STROKE);
                eyeOutline.setColor(withAlpha(style.lineColor, 50));
                eyeOutline.setStrokeWidth(r * 0.015f);
                canvas.drawOval(eyeOval, eyeOutline);

                // Iris with gradient
                int irisColor = darken(style.lineColor, 0.15f);
                int irisEdge = darken(style.lineColor, 0.35f);
                RadialGradient irisGrad = new RadialGradient(
                        ex - irisR * 0.15f, eyeY - irisR * 0.15f, irisR,
                        irisColor, irisEdge, Shader.TileMode.CLAMP);
                irisPaint.setShader(irisGrad);
                canvas.drawCircle(ex, eyeY, irisR, irisPaint);
                irisPaint.setShader(null);

                // Pupil
                canvas.drawCircle(ex, eyeY, pupilR, pupilPaint);

                // Main highlight (top-left)
                if (s < 0.7f) {
                    canvas.drawCircle(ex - irisR * 0.25f, eyeY - irisR * 0.25f, hlR, highlightPaint);
                    // Secondary small highlight (bottom-right)
                    canvas.drawCircle(ex + irisR * 0.30f, eyeY + irisR * 0.20f, hlSmallR, highlightSmallPaint);
                }
            } else {
                // X-eyes for extreme stress
                float xLen = r * 0.10f;
                float xAlpha = (s - 0.85f) / 0.15f;
                Paint xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                xPaint.setStrokeWidth(r * 0.055f);
                xPaint.setColor(style.lineColor);
                xPaint.setAlpha((int) (255 * xAlpha));
                xPaint.setStyle(Paint.Style.STROKE);
                xPaint.setStrokeCap(Paint.Cap.ROUND);

                // Fade out normal eye
                eyeOval.set(ex - eyeRadiusX, eyeY - eyeRadiusY,
                        ex + eyeRadiusX, eyeY + eyeRadiusY);
                eyeWhitePaint.setColor(0xFFFAFAFA);
                eyeWhitePaint.setAlpha((int) (255 * (1f - xAlpha)));
                canvas.drawOval(eyeOval, eyeWhitePaint);
                eyeWhitePaint.setAlpha(255);

                canvas.drawLine(ex - xLen, eyeY - xLen, ex + xLen, eyeY + xLen, xPaint);
                canvas.drawLine(ex + xLen, eyeY - xLen, ex - xLen, eyeY + xLen, xPaint);
            }
        }
    }

    private void drawNose(Canvas canvas, float cx, float cy, float r, float s) {
        // Subtle small nose — just a tiny curved line
        nosePaint.setStrokeWidth(r * 0.025f);
        nosePaint.setColor(withAlpha(style.lineColor, 55));
        float noseY = cy + r * 0.08f;
        nosePath.reset();
        nosePath.moveTo(cx - r * 0.03f, noseY);
        nosePath.quadTo(cx, noseY + r * 0.06f, cx + r * 0.03f, noseY);
        canvas.drawPath(nosePath, nosePaint);
    }

    private void drawMouth(Canvas canvas, float cx, float cy, float r, float s) {
        mouthPaint.setStrokeWidth(r * 0.055f);

        float mouthY = cy + r * 0.35f;
        float mouthHalfW = r * lerp(0.28f, 0.22f, s);

        float curveOffset = lerp(r * 0.22f, -r * 0.18f, s);

        if (s > 0.75f) {
            // Wobbly/wavy mouth for high stress
            float waviness = (s - 0.75f) / 0.25f;
            float wave = waviness * r * 0.08f;

            mouthPath.reset();
            mouthPath.moveTo(cx - mouthHalfW, mouthY);
            mouthPath.cubicTo(
                    cx - mouthHalfW * 0.5f, mouthY + curveOffset + wave,
                    cx + mouthHalfW * 0.5f, mouthY + curveOffset - wave,
                    cx + mouthHalfW, mouthY);
            canvas.drawPath(mouthPath, mouthPaint);
        } else if (s < 0.25f) {
            // Happy open smile — draw filled mouth interior
            mouthPath.reset();
            mouthPath.moveTo(cx - mouthHalfW, mouthY);
            mouthPath.quadTo(cx, mouthY + curveOffset, cx + mouthHalfW, mouthY);
            canvas.drawPath(mouthPath, mouthPaint);

            // Subtle tongue/mouth fill for big smile
            float openness = (1f - s / 0.25f) * 0.5f;
            if (openness > 0.1f) {
                mouthFillPaint.setColor(withAlpha(darken(style.lineColor, 0.5f), (int)(openness * 80)));
                Path fillPath = new Path();
                fillPath.moveTo(cx - mouthHalfW * 0.8f, mouthY + r * 0.02f);
                fillPath.quadTo(cx, mouthY + curveOffset * 0.7f, cx + mouthHalfW * 0.8f, mouthY + r * 0.02f);
                fillPath.close();
                canvas.drawPath(fillPath, mouthFillPaint);
            }
        } else {
            // Normal mouth
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
        if (s > 0.45f) return;

        float blushAlpha = (1f - s / 0.45f);
        float eyeSpacing = r * 0.36f;
        float blushY = cy + r * 0.10f;
        float blushRx = r * 0.13f;
        float blushRy = r * 0.07f;

        for (int side = -1; side <= 1; side += 2) {
            float bx = cx + side * (eyeSpacing + r * 0.10f);
            // Radial gradient blush for soft, natural look
            RadialGradient blushGrad = new RadialGradient(
                    bx, blushY, blushRx,
                    withAlpha(style.blushTint, (int)(blushAlpha * 65)),
                    withAlpha(style.blushTint, 0),
                    Shader.TileMode.CLAMP);
            blushPaint.setShader(blushGrad);
            tmpOval.set(bx - blushRx, blushY - blushRy, bx + blushRx, blushY + blushRy);
            canvas.drawOval(tmpOval, blushPaint);
        }
        blushPaint.setShader(null);
    }

    private void drawSweatDrop(Canvas canvas, float cx, float cy, float r, float s) {
        if (s < 0.5f) return;

        float intensity = (s - 0.5f) / 0.5f;
        int alpha = (int) (intensity * 200);
        sweatPaint.setColor(withAlpha(style.sweatTint, alpha));

        float dropX = cx + r * 0.62f;
        float dropY = cy - r * 0.50f;
        float dropR = r * 0.07f * (0.5f + intensity * 0.5f);

        Path dropPath = new Path();
        dropPath.moveTo(dropX, dropY - dropR * 2.5f);
        dropPath.quadTo(dropX + dropR * 1.3f, dropY, dropX, dropY + dropR);
        dropPath.quadTo(dropX - dropR * 1.3f, dropY, dropX, dropY - dropR * 2.5f);
        dropPath.close();
        canvas.drawPath(dropPath, sweatPaint);

        // Small white highlight on the sweat drop
        sweatHighlightPaint.setAlpha((int)(intensity * 160));
        canvas.drawCircle(dropX - dropR * 0.25f, dropY - dropR * 0.6f, dropR * 0.25f, sweatHighlightPaint);
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

    /** Lighten a color by blending towards white. factor in [0,1]. */
    private static int lighten(int color, float factor) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = r + (int) ((255 - r) * factor);
        g = g + (int) ((255 - g) * factor);
        b = b + (int) ((255 - b) * factor);
        return Color.argb(Color.alpha(color),
                Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    /** Darken a color by blending towards black. factor in [0,1]. */
    private static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * (1f - factor));
        int g = (int) (Color.green(color) * (1f - factor));
        int b = (int) (Color.blue(color) * (1f - factor));
        return Color.argb(Color.alpha(color),
                Math.max(0, r), Math.max(0, g), Math.max(0, b));
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
