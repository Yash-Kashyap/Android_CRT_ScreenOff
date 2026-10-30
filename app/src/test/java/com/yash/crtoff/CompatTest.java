package com.yash.crtoff;

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
}
