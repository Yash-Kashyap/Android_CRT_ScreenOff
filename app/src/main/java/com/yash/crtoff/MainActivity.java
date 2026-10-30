package com.yash.crtoff;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings / setup screen. */
public class MainActivity extends Activity {

    private static final int BG = 0xFF0E0F14, CARD = 0xFF1A1C24, ACCENT = 0xFF7FD1FF,
            TEXT = 0xFFECEDF3, SUBTEXT = 0xFF9AA0B4, OK = 0xFF5FD38D, WARN = 0xFFFFB45C;

    private Prefs prefs;
    private TextView serviceStatus;
    private Button serviceButton;
    private FrameLayout rootFrame;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        rootFrame = new FrameLayout(this);
        rootFrame.addView(buildUi());
        setContentView(rootFrame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = CrtService.isEnabled(this);
        serviceStatus.setText(on ? R.string.granted : R.string.required);
        serviceStatus.setTextColor(on ? OK : WARN);
        serviceButton.setEnabled(!on);
        serviceButton.setAlpha(on ? 0.35f : 1f);
        serviceButton.setText(on ? R.string.granted_short : R.string.turn_on);
    }

    // ------------------------------------------------------------------ UI

    @SuppressWarnings("deprecation")
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(col);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = i.top; bottom = i.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        TextView title = text(getString(R.string.app_name), 30, TEXT);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        col.addView(title);
        TextView sub = text(getString(R.string.tagline), 14, SUBTEXT);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        // --- Setup
        LinearLayout c1 = card(col);
        c1.addView(header(getString(R.string.setup)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(getString(R.string.service_name), 15, TEXT));
        serviceStatus = text("", 13, SUBTEXT);
        texts.addView(serviceStatus);
        texts.addView(small(getString(R.string.service_hint)));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serviceButton = button(getString(R.string.turn_on), false);
        serviceButton.setOnClickListener(v -> openAccessibility());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        blp.leftMargin = dp(10);
        row.addView(serviceButton, blp);
        c1.addView(row);
        Button info = button(getString(R.string.open_app_info), false);
        info.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        c1.addView(info, matchWrap(dp(10)));
        c1.addView(small(getString(R.string.restricted_hint)));

        // --- Preview
        LinearLayout c2 = card(col);
        c2.addView(header(getString(R.string.preview)));
        Button play = button(getString(R.string.preview_play), true);
        play.setOnClickListener(v -> preview(1f));
        c2.addView(play, matchWrap(dp(10)));
        Button slow = button(getString(R.string.preview_slow), false);
        slow.setOnClickListener(v -> preview(8f));
        c2.addView(slow, matchWrap(dp(8)));
        c2.addView(small(getString(R.string.preview_hint)));

        // --- Power on
        LinearLayout cOn = card(col);
        cOn.addView(header(getString(R.string.power_on)));
        cOn.addView(sw(getString(R.string.crt_on), prefs.crtOn(), prefs::setCrtOn), matchWrap(dp(8)));
        cOn.addView(small(getString(R.string.crt_on_hint)));
        Button previewOn = button(getString(R.string.preview_on), false);
        previewOn.setOnClickListener(v -> previewOn());
        cOn.addView(previewOn, matchWrap(dp(10)));

        // --- Triggers
        LinearLayout c3 = card(col);
        c3.addView(header(getString(R.string.triggers)));
        c3.addView(sw(getString(R.string.floating), prefs.floatingButton(), on -> {
            prefs.setFloatingButton(on);
            CrtService.refresh();
        }), matchWrap(dp(8)));
        slider(c3, getString(R.string.button_size), 36, 80, prefs.buttonSizeDp(), " dp", v -> {
            prefs.setButtonSizeDp(v);
            CrtService.refresh();
        });
        slider(c3, getString(R.string.button_opacity), 15, 100, prefs.buttonOpacity(), " %", v -> {
            prefs.setButtonOpacity(v);
            CrtService.refresh();
        });
        c3.addView(small(getString(R.string.floating_hint)));
        c3.addView(sw(getString(R.string.volume_double), prefs.volumeDoublePress(), on -> {
            prefs.setVolumeDoublePress(on);
            CrtService.refresh();
        }), matchWrap(dp(12)));
        c3.addView(small(getString(R.string.volume_hint)));
        Button tile = button(getString(R.string.add_tile), false);
        tile.setOnClickListener(v -> addTile());
        c3.addView(tile, matchWrap(dp(12)));
        Button shortcut = button(getString(R.string.add_shortcut), false);
        shortcut.setOnClickListener(v -> addShortcut());
        c3.addView(shortcut, matchWrap(dp(8)));
        c3.addView(small(getString(R.string.side_key_hint)));

        // --- Sound
        LinearLayout c4 = card(col);
        c4.addView(header(getString(R.string.sound)));
        c4.addView(sw(getString(R.string.lock_sound), prefs.sounds(), prefs::setSounds), matchWrap(dp(8)));
        slider(c4, getString(R.string.sound_volume), 0, 100, prefs.soundVolume(), " %", prefs::setSoundVolume,
                () -> Sounds.preview(this));
        c4.addView(small(getString(R.string.sound_hint)));

        // --- Good to know
        LinearLayout c5 = card(col);
        c5.addView(header(getString(R.string.tips)));
        c5.addView(small(getString(R.string.tips_body)));
        return scroll;
    }

    // ------------------------------------------------------------------ actions

    /** Plays the real animation over this screen (snapshot of the app itself), then comes back. */
    private void preview(float scale) {
        View content = rootFrame.getChildAt(0);
        Bitmap snap = Bitmap.createBitmap(rootFrame.getWidth(), rootFrame.getHeight(), Bitmap.Config.ARGB_8888);
        content.draw(new Canvas(snap));
        CrtView v = new CrtView(this);
        v.setTimeScale(scale);
        v.setClickable(true);
        rootFrame.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setListener(() -> v.postDelayed(() -> v.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> {
                    rootFrame.removeView(v);
                    snap.recycle();
                }).start(), 450));
        v.play(snap);
    }

    /** Power-on preview over this screen (slowed 3x so it's easy to see). */
    private void previewOn() {
        CrtView v = new CrtView(this);
        v.setTimeScale(3f);
        rootFrame.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.holdBlack();
        v.setListener(() -> rootFrame.removeView(v));
        v.postDelayed(v::playOn, 500);
    }

    private void openAccessibility() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.service_name)
                .setMessage(R.string.service_dialog)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(new ComponentName(this, CrtTileService.class),
                    getString(R.string.tile_label), Icon.createWithResource(this, R.drawable.ic_tile),
                    getMainExecutor(), r -> { });
        } else {
            Toast.makeText(this, R.string.tile_manual, Toast.LENGTH_LONG).show();
        }
    }

    private void addShortcut() {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(this, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, TriggerActivity.class).setAction(Intent.ACTION_VIEW);
        ShortcutInfo info = new ShortcutInfo.Builder(this, "crt_off_pin")
                .setShortLabel(getString(R.string.shortcut_label))
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_shortcut))
                .setIntent(i)
                .build();
        sm.requestPinShortcut(info, null);
    }

    // ------------------------------------------------------------------ helpers

    private interface IntSink { void set(int v); }
    private interface BoolSink { void set(boolean v); }

    private Switch sw(String label, boolean checked, BoolSink sink) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(15);
        s.setChecked(checked);
        s.setOnCheckedChangeListener((b, on) -> sink.set(on));
        return s;
    }

    private void slider(LinearLayout parent, String label, int min, int max, int value, String unit, IntSink sink) {
        slider(parent, label, min, max, value, unit, sink, null);
    }

    private void slider(LinearLayout parent, String label, int min, int max, int value, String unit,
                        IntSink sink, Runnable onRelease) {
        TextView t = text(getString(R.string.slider_value, label, value, unit), 15, TEXT);
        t.setPadding(0, dp(8), 0, 0);
        parent.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                t.setText(getString(R.string.slider_value, label, p + min, unit));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                sink.set(s.getProgress() + min);
                if (onRelease != null) onRelease.run();
            }
        });
        parent.addView(sb, matchWrap(dp(4)));
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        c.setBackground(bg);
        parent.addView(c, matchWrap(dp(12)));
        return c;
    }

    private TextView header(String s) {
        TextView t = text(s, 13, ACCENT);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView small(CharSequence s) {
        TextView t = text(s, 13, SUBTEXT);
        t.setPadding(0, dp(4), 0, 0);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private TextView text(CharSequence s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(primary ? 0xFF0E0F14 : TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        if (primary) {
            bg.setColor(ACCENT);
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(dp(1), 0xFF3A3E4E);
        }
        b.setBackground(bg);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setMinHeight(dp(42));
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
