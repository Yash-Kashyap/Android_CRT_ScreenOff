package com.yash.crtoff;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings. */
public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("crt", Context.MODE_PRIVATE);
    }

    public boolean floatingButton() { return sp.getBoolean("floating", true); }
    public void setFloatingButton(boolean v) { sp.edit().putBoolean("floating", v).apply(); }

    public int buttonSizeDp() { return sp.getInt("button_size", 52); }
    public void setButtonSizeDp(int v) { sp.edit().putInt("button_size", v).apply(); }

    public int buttonOpacity() { return sp.getInt("button_opacity", 70); }
    public void setButtonOpacity(int v) { sp.edit().putInt("button_opacity", v).apply(); }

    public int buttonX() { return sp.getInt("button_x", -1); }
    public int buttonY() { return sp.getInt("button_y", -1); }
    public void setButtonPos(int x, int y) { sp.edit().putInt("button_x", x).putInt("button_y", y).apply(); }

    public boolean volumeDoublePress() { return sp.getBoolean("volume_double", false); }
    public void setVolumeDoublePress(boolean v) { sp.edit().putBoolean("volume_double", v).apply(); }

    public boolean sounds() { return sp.getBoolean("sounds", true); }
    public void setSounds(boolean v) { sp.edit().putBoolean("sounds", v).apply(); }

    public int soundVolume() { return sp.getInt("sound_volume", 80); }
    public void setSoundVolume(int v) { sp.edit().putInt("sound_volume", Math.max(0, Math.min(100, v))).apply(); }
}
