package com.sya.tube.library;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** Where each phone video or media server video stopped, so picking it again continues there */
public final class MediaPositions {
    private static final String PREFS = "media_positions";
    private static final int LIMIT = 300;

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS, 0); }

    public static void save(Context context, String key, long positionMs, long durationMs) {
        if (context == null || key == null || durationMs <= 0) return;
        SharedPreferences.Editor edit = prefs(context).edit();
        // Watched to the end, or barely started, there is nothing to continue
        if (positionMs > durationMs * 0.95 || positionMs < 5000) edit.remove(key);
        else edit.putString(key, positionMs + "," + durationMs + "," + System.currentTimeMillis());
        edit.apply();
        if (prefs(context).getAll().size() > LIMIT) prune(context);
    }

    public static long position(Context context, String key) {
        String value = key == null ? null : prefs(context).getString(key, null);
        if (value == null) return 0;
        try { return Long.parseLong(value.split(",")[0]); } catch (RuntimeException ignored) { return 0; }
    }

    public static float progress(Context context, String key) {
        String value = key == null ? null : prefs(context).getString(key, null);
        if (value == null) return 0;
        try { String[] parts = value.split(","); return Math.min(1f, Long.parseLong(parts[0]) / (float) Long.parseLong(parts[1])); } catch (RuntimeException ignored) { return 0; }
    }

    private static void prune(Context context) {
        Map<String, ?> all = prefs(context).getAll();
        String oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                long at = Long.parseLong(String.valueOf(entry.getValue()).split(",")[2]);
                if (at < oldestAt) { oldestAt = at; oldest = entry.getKey(); }
            } catch (RuntimeException ignored) { oldest = entry.getKey(); break; }
        }
        if (oldest != null) prefs(context).edit().remove(oldest).apply();
    }

    private MediaPositions() {}
}
