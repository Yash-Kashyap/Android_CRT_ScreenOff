package com.yash.crtoff;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Renders the CRT animation at the reference footage's 59.94 fps for side-by-side comparison. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CrtRenderTest {
    private final File out = new File(System.getProperty("frames.out", "build/frames"), "crt");

    private void render(Bitmap shot, boolean mask, String prefix) throws Exception {
        int W = 330, H = 560;
        CrtView v = new CrtView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        v.play(mask ? null : shot);
        out.mkdirs();
        for (int k = 0; k < 26; k++) {
            // reference frame 515 (index 2 here) is the first changed frame -> t = 0
            long t = Math.round((k - 2) * 1000.0 / 59.94);
            Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (mask) c.drawBitmap(shot, 0, 0, null); else c.drawColor(Color.BLACK);
            if (t < 0) {
                c.drawBitmap(shot, 0, 0, null);
            } else {
                v.setFrameTime(t);
                v.draw(c);
            }
            try (FileOutputStream fo = new FileOutputStream(new File(out, String.format("%s%02d.png", prefix, k + 1)))) {
                b.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
        }
    }

    @Test
    public void framesMatchReferenceTiming() throws Exception {
        Bitmap shot;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("ref_screen.png")) {
            shot = BitmapFactory.decodeStream(in);
        }
        render(shot, false, "ours_");
        render(shot, true, "mask_");
        assertTrue(CrtView.TOTAL_MS >= 330 && CrtView.TOTAL_MS <= 360);
    }

    @Test
    public void powerOnFrames() throws Exception {
        Bitmap shot;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("ref_screen.png")) {
            shot = BitmapFactory.decodeStream(in);
        }
        int W = 330, H = 560;
        CrtView v = new CrtView(RuntimeEnvironment.getApplication());
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        v.playOn();
        out.mkdirs();
        for (int k = 0; k < 18; k++) {
            long t = Math.round(k * 1000.0 / 59.94);
            Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawBitmap(shot, 0, 0, null);   // the live lock screen underneath
            v.setFrameTime(t);
            v.draw(c);
            try (FileOutputStream fo = new FileOutputStream(new File(out, String.format("on_%02d.png", k + 1)))) {
                b.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
        }
        assertTrue(CrtView.ON_TOTAL_MS == 250);
    }
}
