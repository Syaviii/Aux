package com.sya.tube.remote;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** The last videos watched on the car with where each one stopped, newest first, finished videos drop off */
public final class RecentVideos {
    public static final class Item {
        public final String id, title;
        public final long positionMs, durationMs;
        Item(String id, String title, long positionMs, long durationMs) { this.id = id; this.title = title; this.positionMs = positionMs; this.durationMs = durationMs; }

        /** A watch address that continues where this video stopped */
        public String url() { return "https://m.youtube.com/watch?v=" + id + (positionMs > 5000 ? "&t=" + positionMs / 1000 + "s" : ""); }

        public float progress() { return durationMs > 0 ? Math.min(1f, positionMs / (float) durationMs) : 0f; }
    }

    private static final String PREF = "recent_videos";
    private static final int LIMIT = 12;

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences("car", 0); }

    public static List<Item> list(Context context) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs(context).getString(PREF, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.getJSONObject(i);
                out.add(new Item(o.getString("id"), o.optString("title", ""), o.optLong("pos"), o.optLong("dur")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Records where a video is now, it moves to the front, and a video watched to the end is removed */
    public static void update(Context context, String id, String title, long positionMs, long durationMs) {
        if (context == null || id == null) return;
        List<Item> items = list(context);
        // The page reports a bare "YouTube" until the video's own title arrives, the title already known stays then
        if (title == null || title.trim().isEmpty() || title.equals("YouTube"))
            for (Item item : items) if (item.id.equals(id)) { title = item.title; break; }
        items.removeIf(item -> item.id.equals(id));
        boolean finished = durationMs > 0 && positionMs > durationMs * 0.95;
        // A live stream has no length and nothing to continue later
        if (!finished && durationMs > 0 && positionMs > 5000) items.add(0, new Item(id, title == null || title.equals("YouTube") ? "" : title, positionMs, durationMs));
        while (items.size() > LIMIT) items.remove(items.size() - 1);
        JSONArray out = new JSONArray();
        try {
            for (Item item : items) out.put(new JSONObject().put("id", item.id).put("title", item.title).put("pos", item.positionMs).put("dur", item.durationMs));
        } catch (Exception ignored) { }
        prefs(context).edit().putString(PREF, out.toString()).apply();
    }

    private RecentVideos() {}
}
