package com.yash.crtoff;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * The engine. An accessibility service is the only non-root way for an app to
 * (1) draw above everything including the status bar and lock screen,
 * (2) take a snapshot of the current screen for the animation (Android 11+), and
 * (3) switch the screen off without breaking fingerprint unlock (GLOBAL_ACTION_LOCK_SCREEN).
 * It reads no screen content and only receives this app's own events.
 */
public class CrtService extends AccessibilityService implements CrtView.Listener {

    private static volatile CrtService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private CrtView crtView;
    private Bitmap shot;
    private boolean busy;
    private View bubble;
    private long lastVolDown;
    private boolean swallowNextVolUp;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                handler.postDelayed(CrtService.this::removeOverlay, 150);
            }
        }
    };

    // ------------------------------------------------------------------ public API

    public static boolean isRunning() { return instance != null; }

    /** Plays the CRT off and switches the screen off. Returns false if the service is off. */
    public static boolean trigger(long delayMs) {
        CrtService s = instance;
        if (s == null) return false;
        s.handler.post(() -> s.start(delayMs));
        return true;
    }

    /** Re-reads settings (floating button, volume key). */
    public static void refresh() {
        CrtService s = instance;
        if (s != null) s.handler.post(s::applySettings);
    }

    public static boolean isEnabled(Context c) {
        if (instance != null) return true;
        String list = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(list)) return false;
        String me = c.getPackageName() + "/" + CrtService.class.getName();
        String meShort = c.getPackageName() + "/." + CrtService.class.getSimpleName();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        Sounds.init(this);
        applySettings();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        instance = null;
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        removeOverlay();
        removeBubble();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    private void applySettings() {
        Prefs p = new Prefs(this);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            if (p.volumeDoublePress()) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        removeBubble();
        if (p.floatingButton()) addBubble();
    }

    // ------------------------------------------------------------------ volume key trigger

    /** Double-press Volume Down: the first press still changes volume; the second switches off. */
    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN || !new Prefs(this).volumeDoublePress()) {
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            long now = SystemClock.uptimeMillis();
            if (now - lastVolDown < 400) {
                lastVolDown = 0;
                swallowNextVolUp = true;
                start(40);
                return true;
            }
            lastVolDown = now;
        } else if (e.getAction() == KeyEvent.ACTION_UP && swallowNextVolUp) {
            swallowNextVolUp = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the animation

    private void start(long delayMs) {
        if (busy) return;
        busy = true;
        if (bubble != null) bubble.setVisibility(View.INVISIBLE);
        // let the shade / button / launcher settle so the snapshot shows the real screen
        handler.postDelayed(this::capture, Math.max(40, delayMs));
    }

    private void capture() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult r) {
                        Bitmap soft = null;
                        HardwareBuffer hb = r.getHardwareBuffer();
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                            if (hw != null) soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                        } catch (Exception ignored) {
                        } finally {
                            hb.close();
                        }
                        showOverlay(soft);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        showOverlay(null); // e.g. a secure window: use the shutter version
                    }
                });
                return;
            } catch (Exception ignored) {
            }
        }
        showOverlay(null);
    }

    private WindowManager.LayoutParams fullScreenParams(boolean touchable) {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dm.widthPixels, dm.heightPixels,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | (touchable ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        lp.windowAnimations = 0;
        return lp;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showOverlay(Bitmap screenshot) {
        removeOverlay();
        busy = true;
        shot = screenshot;
        crtView = new CrtView(this);
        crtView.setListener(this);
        crtView.setOnTouchListener((v, e) -> true); // swallow touches during the animation
        try {
            wm.addView(crtView, fullScreenParams(true));
            crtView.play(shot);
        } catch (Exception e) {
            crtView = null;
            busy = false;
            restoreBubble();
        }
    }

    @Override
    public void onCrtFinished() {
        Sounds.playLock(this);
        boolean ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        // Black stays up until the screen is off (removed on SCREEN_OFF), with a safety net.
        handler.postDelayed(this::removeOverlay, ok ? 2000 : 0);
    }

    private void removeOverlay() {
        if (crtView != null) {
            try { wm.removeView(crtView); } catch (Exception ignored) { }
            crtView = null;
        }
        if (shot != null) {
            shot.recycle();
            shot = null;
        }
        busy = false;
        restoreBubble();
    }

    // ------------------------------------------------------------------ floating button

    private void restoreBubble() {
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addBubble() {
        Prefs p = new Prefs(this);
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(p.buttonSizeDp() * d);
        BubbleView v = new BubbleView(this, p.buttonOpacity() / 100f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        lp.x = p.buttonX() >= 0 ? p.buttonX() : dm.widthPixels - size - Math.round(12 * d);
        lp.y = p.buttonY() >= 0 ? p.buttonY() : Math.round(dm.heightPixels * 0.55f);
        final float[] down = new float[4];
        final boolean[] dragged = {false};
        int slop = Math.round(8 * d);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX(); down[1] = e.getRawY(); down[2] = lp.x; down[3] = lp.y;
                    dragged[0] = false;
                    view.setScaleX(0.9f);
                    view.setScaleY(0.9f);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragged[0] && Math.hypot(dx, dy) > slop) dragged[0] = true;
                    if (dragged[0]) {
                        lp.x = Math.round(down[2] + dx);
                        lp.y = Math.round(down[3] + dy);
                        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    if (dragged[0]) {
                        // snap to the nearest side edge
                        DisplayMetrics m = new DisplayMetrics();
                        wm.getDefaultDisplay().getRealMetrics(m);
                        int margin = Math.round(8 * d);
                        lp.x = (lp.x + size / 2 < m.widthPixels / 2) ? margin : m.widthPixels - size - margin;
                        lp.y = Math.max(margin, Math.min(m.heightPixels - size - margin, lp.y));
                        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
                        new Prefs(this).setButtonPos(lp.x, lp.y);
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        view.performClick();
                        start(60);
                    }
                    return true;
            }
            return false;
        });
        try {
            wm.addView(v, lp);
            bubble = v;
        } catch (Exception ignored) {
        }
    }

    private void removeBubble() {
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
    }

    /** A small round power button. */
    static class BubbleView extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);

        BubbleView(Context c, float opacity) {
            super(c);
            bg.setColor(Color.argb(Math.round(255 * Math.max(0.15f, opacity)), 20, 22, 28));
            fg.setStyle(Paint.Style.STROKE);
            fg.setStrokeCap(Paint.Cap.ROUND);
            fg.setColor(Color.argb(Math.round(255 * Math.max(0.4f, opacity)), 255, 255, 255));
            setContentDescription(c.getString(R.string.button_desc));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
            c.drawCircle(w / 2, h / 2, r * 0.96f, bg);
            fg.setStrokeWidth(r * 0.12f);
            float ir = r * 0.42f;
            c.drawArc(w / 2 - ir, h / 2 - ir, w / 2 + ir, h / 2 + ir, -60, 300, false, fg);
            c.drawLine(w / 2, h / 2 - ir * 1.25f, w / 2, h / 2 - ir * 0.15f, fg);
        }

        @Override
        public boolean performClick() { return super.performClick(); }
    }
}
