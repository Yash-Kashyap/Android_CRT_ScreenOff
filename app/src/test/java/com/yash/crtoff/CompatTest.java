package com.yash.crtoff;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@Config(sdk = {28, 30, 34})
public class CompatTest {

    @Test
    public void settingsScreenStartsAndPreviews() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertNotNull(c.get());
        c.get().getWindow().getDecorView().measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        c.get().getWindow().getDecorView().layout(0, 0, 1080, 2340);
        c.pause().stop().destroy();
    }

    @Test
    public void serviceRunsTheWholeSequence() {
        CrtService s = Robolectric.setupService(CrtService.class);
        s.onServiceConnected();
        assertTrue(CrtService.isRunning());
        assertTrue(CrtService.trigger(0));
        // capture -> overlay -> 350 ms animation -> lock
        for (int i = 0; i < 60; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        s.onDestroy();
    }

    @Test
    public void triggerWithoutServiceOpensSettings() {
        ActivityController<TriggerActivity> c = Robolectric.buildActivity(TriggerActivity.class).setup();
        assertTrue(c.get().isFinishing());
    }

    @Test
    public void serviceHandlesScreenOffAndOn() {
        CrtService s = Robolectric.setupService(CrtService.class);
        s.onServiceConnected();
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        app.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF));
        for (int i = 0; i < 30; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        app.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_SCREEN_ON));
        for (int i = 0; i < 40; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        s.onDestroy();
    }

    @Test
    public void sideKeyEntryLaunches() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .setClassName("com.yash.crtoff", "com.yash.crtoff.TriggerActivity");
        ActivityController<TriggerActivity> c = Robolectric.buildActivity(TriggerActivity.class, i).setup();
        assertTrue(c.get().isFinishing());
    }

    /** Samsung's Side key "Open app" starts the package's launch entry: it must be CRT off now. */
    @Test
    public void packageLaunchEntryIsScreenOff() throws Exception {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        java.util.List<android.content.pm.ResolveInfo> l = ctx.getPackageManager().queryIntentActivities(
                new android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                        .setPackage("com.yash.crtoff"), 0);
        assertTrue("launcher entries: " + l.size(), l.size() >= 2);
        String first = l.get(0).activityInfo.name;
        assertTrue("first launch entry was " + first, first.endsWith("CrtOffNow"));
    }

    private static android.view.KeyEvent key(int action, int code) {
        return new android.view.KeyEvent(action, code);
    }

    /** Xperia camera key: one press is left for the shutter, a quick double press plays the CRT. */
    @Test
    public void cameraKeyDoublePress() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        CrtService s = Robolectric.setupService(CrtService.class);
        s.onServiceConnected();
        int D = android.view.KeyEvent.ACTION_DOWN, U = android.view.KeyEvent.ACTION_UP;
        int F = android.view.KeyEvent.KEYCODE_FOCUS, C = android.view.KeyEvent.KEYCODE_CAMERA;

        // switched off: nothing is taken
        assertFalse(s.onKeyEvent(key(D, C)));
        assertFalse(s.onKeyEvent(key(U, C)));

        new Prefs(app).setCameraDoublePress(true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        // first press passes through untouched
        assertFalse(s.onKeyEvent(key(D, F)));
        assertFalse(s.onKeyEvent(key(D, C)));
        assertFalse(s.onKeyEvent(key(U, C)));
        assertFalse(s.onKeyEvent(key(U, F)));
        assertFalse(s.isBusyForTest());
        // second press within the window: swallowed and plays the CRT off
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        assertFalse(s.onKeyEvent(key(D, F)));
        assertTrue(s.onKeyEvent(key(D, C)));
        assertTrue(s.isBusyForTest());
        assertTrue(s.onKeyEvent(key(U, C)));
        assertTrue(s.onKeyEvent(key(U, F)));
        for (int i = 0; i < 60; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        s.onDestroy();
    }

    @Test
    public void cameraPressesTooFarApartDoNothing() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        new Prefs(app).setCameraDoublePress(true);
        CrtService s = Robolectric.setupService(CrtService.class);
        s.onServiceConnected();
        int D = android.view.KeyEvent.ACTION_DOWN, C = android.view.KeyEvent.KEYCODE_CAMERA;
        assertFalse(s.onKeyEvent(key(D, C)));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900));
        assertFalse(s.onKeyEvent(key(D, C)));
        assertFalse(s.isBusyForTest());
        s.onDestroy();
    }

    /** With no lock-screen app answering, CRT Off plays its own click at the cue. */
    @Test
    public void crtCuePlaysOwnClickWhenNobodyAnswers() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        app.getSystemService(android.media.AudioManager.class)
                .setRingerMode(android.media.AudioManager.RINGER_MODE_NORMAL);
        CrtService s = Robolectric.setupService(CrtService.class);
        s.onServiceConnected();
        Sounds.playedForTest().clear();
        s.onCrtSoundCue();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(Sounds.playedForTest().contains(R.raw.lock));
        s.onDestroy();
    }

    /** The click is cued shortly before the line vanishes, not after the animation. */
    @Test
    public void soundCueComesBeforeTheEnd() {
        assertTrue(CrtView.SOUND_LEAD_MS > 0 && CrtView.SOUND_LEAD_MS < CrtView.LINE_MS);
    }
}
