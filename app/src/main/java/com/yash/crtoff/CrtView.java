package com.yash.crtoff;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/**
 * The Android 2.3 / 4.x "electron beam" screen-off, measured frame by frame from reference
 * footage (30 fps and 60 fps captures, ~330-350 ms end to end):
 *
 * <ol>
 *   <li><b>Warm-up, 67 ms</b>: the picture dims slightly and a vertically mirrored "ghost" of the
 *       screen fades in on top of it; the picture starts to squeeze (to ~97 %).</li>
 *   <li><b>Vertical collapse, 117 ms</b>: the picture squeezes toward the centre line at a nearly
 *       even pace, easing into the line, while it brightens and finally turns white.</li>
 *   <li><b>The line, 167 ms</b>: the bright full-width line thins, shortens a little and fades.</li>
 * </ol>
 *
 * With a screenshot of the screen ({@code shot}) the real picture is squeezed. Without one
 * ("mask mode", Android 9-10) black shutters close in around the live screen in the same shape.
 */
public class CrtView extends View {

    public interface Listener { void onCrtFinished(); }

    public static final float WARM_MS = 67f, SQUEEZE_MS = 117f, LINE_MS = 167f;
    public static final long TOTAL_MS = (long) (WARM_MS + SQUEEZE_MS + LINE_MS);

    /** Power-on (the CyanogenMod-era "CRT on"): line grows across, then opens into the screen. */
    public static final float ON_LINE_MS = 90f, ON_OPEN_MS = 160f;
    public static final long ON_TOTAL_MS = (long) (ON_LINE_MS + ON_OPEN_MS);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint black = new Paint();
    private final Paint fill = new Paint();
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF band = new RectF();
    private final ColorMatrix bright = new ColorMatrix();
    private final Rect src = new Rect();
    private final float density;

    private Listener listener;
    private Bitmap shot;
    private long start = -1;
    private boolean finished;
    private long clockOverride = -1; // tests
    private float timeScale = 1f;    // >1 = slow motion preview
    private boolean onMode;          // power-on instead of power-off
    private boolean holdBlack;       // solid black, waiting for the screen to wake

    public CrtView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        black.setColor(Color.BLACK);
    }

    public void setListener(Listener l) { listener = l; }

    /** Solid black (screen is off): waits for {@link #playOn()}. */
    public void holdBlack() {
        holdBlack = true;
        onMode = false;
        start = -1;
        invalidate();
    }

    /** Power-on: the live screen is revealed by a line that grows and opens up. */
    public void playOn() {
        holdBlack = false;
        onMode = true;
        shot = null;
        start = SystemClock.uptimeMillis();
        finished = false;
        postInvalidateOnAnimation();
    }

    public void play(Bitmap screenshotOrNull) {
        holdBlack = false;
        onMode = false;
        shot = screenshotOrNull;
        start = SystemClock.uptimeMillis();
        finished = false;
        postInvalidateOnAnimation();
    }

    public boolean isFinished() { return finished; }

    /** 1 = real speed; 5 = five times slower (preview). */
    public void setTimeScale(float s) { timeScale = Math.max(1f, s); }

    /** Test hook: render the frame at {@code ms} after start, without the clock. */
    void setFrameTime(long ms) { clockOverride = ms; invalidate(); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int a255(float v) { return Math.max(0, Math.min(255, Math.round(v * 255))); }

    @Override
    protected void onDraw(Canvas c) {
        if (holdBlack) {
            c.drawColor(Color.BLACK);
            return;
        }
        if (start < 0 && clockOverride < 0) return;
        float t = clockOverride >= 0 ? clockOverride : (SystemClock.uptimeMillis() - start) / timeScale;
        if (onMode) {
            drawOn(c, t);
            return;
        }
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float lineH = Math.max(2f, 1.6f * density);

        float sx = 1f, sy, white, dark, ghost, glowUp, lineAlpha = 1f;
        if (t < WARM_MS) {
            float e = t / WARM_MS;
            sy = 1f - 0.03f * e;
            dark = 0.30f * e;
            ghost = 0.45f * e;
            white = 0f;
            glowUp = 0f;
        } else if (t < WARM_MS + SQUEEZE_MS) {
            float e = (t - WARM_MS) / SQUEEZE_MS;
            // measured: ~95, 78, 60, 43, 25, 12, 4 % height on consecutive 60 fps frames
            float f = 1f - (float) Math.pow(1f - e, 1.3);
            sy = lerp(0.97f, lineH / h, f);
            glowUp = (float) Math.pow(e, 0.6);         // picture brightens ("glows") at once...
            white = Math.max(0f, (e - 0.45f) / 0.55f); // ...and turns fully white toward the end
            dark = 0.30f * Math.max(0f, 1f - e * 4f);  // the warm-up dimming lifts immediately
            ghost = 0.45f * (1f - e) * (1f - e);       // the mirrored ghost fades out quickly
        } else if (t < TOTAL_MS) {
            float e = (t - WARM_MS - SQUEEZE_MS) / LINE_MS;
            sy = (lineH / h) * (1f - 0.5f * e);        // thins
            sx = 1f - 0.4f * e * e;                     // shortens a little, late
            white = 1f;
            glowUp = 1f;
            dark = 0f;
            ghost = 0f;
            lineAlpha = 1f - (float) Math.pow(e, 2.2); // stays bright, then fades
        } else {
            c.drawColor(Color.BLACK);
            if (!finished) {
                finished = true;
                if (listener != null && clockOverride < 0) main.post(listener::onCrtFinished);
            }
            return;
        }

        float bw = w * sx, bh = Math.max(lineH * 0.5f, h * sy);
        band.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);

        if (shot != null && !shot.isRecycled()) {
            c.drawColor(Color.BLACK);
            src.set(0, 0, shot.getWidth(), shot.getHeight());
            if (glowUp > 0.005f) {
                float k = 1f + 2.2f * glowUp, o = 255f * 0.6f * glowUp;
                bright.set(new float[]{k, 0, 0, 0, o, 0, k, 0, 0, o, 0, 0, k, 0, o, 0, 0, 0, 1, 0});
                img.setColorFilter(new ColorMatrixColorFilter(bright));
            } else {
                img.setColorFilter(null);
            }
            img.setAlpha(255);
            c.drawBitmap(shot, src, band, img);
            if (ghost > 0.005f) {             // the vertically mirrored ghost image
                int s = c.save();
                c.scale(1f, -1f, cx, cy);
                img.setAlpha(a255(ghost));
                c.drawBitmap(shot, src, band, img);
                c.restoreToCount(s);
            }
        } else {
            // Mask mode: black everywhere except the band (the real screen shows through).
            c.drawRect(0, 0, w, Math.max(0, band.top), black);
            c.drawRect(0, Math.min(h, band.bottom), w, h, black);
            c.drawRect(0, band.top, Math.max(0, band.left), band.bottom, black);
            c.drawRect(Math.min(w, band.right), band.top, w, band.bottom, black);
        }
        if (dark > 0.005f) {
            fill.setColor(Color.argb(a255(dark), 0, 0, 0));
            c.drawRect(band, fill);
        }
        if (shot == null) white = Math.max(white, 0.8f * glowUp); // shutters: lighten instead
        if (white > 0.005f) {
            fill.setColor(Color.argb(a255(white * lineAlpha), 255, 255, 255));
            c.drawRect(band, fill);
            if (t >= WARM_MS && (t >= WARM_MS + SQUEEZE_MS || sy < 0.06f)) {
                // phosphor halo around the line
                float gh = 7f * density;
                glow.setShader(new LinearGradient(0, cy - gh, 0, cy + gh,
                        new int[]{0x00FFFFFF, Color.argb(a255(0.35f * lineAlpha), 220, 235, 255), 0x00FFFFFF},
                        null, Shader.TileMode.CLAMP));
                c.drawRect(band.left, cy - gh, band.right, cy + gh, glow);
            }
        }
        if (clockOverride < 0) postInvalidateOnAnimation();
    }

    private void drawOn(Canvas c, float t) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float lineH = Math.max(2f, 1.6f * density);
        float sx, sy, white;
        if (t < ON_LINE_MS) {                         // a dot of light stretches into a line
            float e = t / ON_LINE_MS;
            float f = 1f - (1f - e) * (1f - e);
            sx = 0.02f + 0.98f * f;
            sy = lineH / h;
            white = 1f;
        } else if (t < ON_TOTAL_MS) {                 // the line opens up into the picture
            float e = (t - ON_LINE_MS) / ON_OPEN_MS;
            float f = 1f - (float) Math.pow(1f - e, 2.2);
            sx = 1f;
            sy = lerp(lineH / h, 1f, f);
            white = 1f - (float) Math.pow(e, 0.7);
        } else {
            if (!finished) {
                finished = true;
                if (listener != null && clockOverride < 0) main.post(listener::onCrtFinished);
            }
            return; // fully transparent: the live screen
        }
        float bw = w * sx, bh = Math.max(lineH * 0.5f, h * sy);
        band.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
        c.drawRect(0, 0, w, Math.max(0, band.top), black);
        c.drawRect(0, Math.min(h, band.bottom), w, h, black);
        c.drawRect(0, band.top, Math.max(0, band.left), band.bottom, black);
        c.drawRect(Math.min(w, band.right), band.top, w, band.bottom, black);
        if (white > 0.005f) {
            fill.setColor(Color.argb(a255(white), 255, 255, 255));
            c.drawRect(band, fill);
        }
        if (t < ON_LINE_MS + ON_OPEN_MS * 0.3f) {
            float gh = 7f * density;
            glow.setShader(new LinearGradient(0, cy - gh, 0, cy + gh,
                    new int[]{0x00FFFFFF, Color.argb(a255(0.35f), 220, 235, 255), 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(band.left, cy - gh, band.right, cy + gh, glow);
        }
        if (clockOverride < 0) postInvalidateOnAnimation();
    }
}
