package com.yash.crtoff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Invisible: started by the home-screen shortcut and the Quick Settings tile. */
public class TriggerActivity extends Activity {
    public static final String EXTRA_DELAY = "delay";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Launched as an app (e.g. Samsung side key double-press): wait for the launch transition.
        long def = Intent.ACTION_MAIN.equals(getIntent().getAction()) ? 260 : 160;
        long delay = getIntent().getLongExtra(EXTRA_DELAY, def);
        if (!CrtService.trigger(delay)) {
            Toast.makeText(this, R.string.need_service_toast, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
