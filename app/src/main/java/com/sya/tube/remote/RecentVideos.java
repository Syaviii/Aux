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
        // A frame taken from the video while it played, for videos without a public picture, empty when there is none
        final String frame;
        Item(String id, String title, long positionMs, long durationMs, String frame) { this.id = id; this.title = title; this.positionMs = positionMs; this.durationMs = durationMs; this.frame = frame == null ? "" : frame; }

        /** The card picture: the video's public one, or the frame kept from playing it, null when there is neither */
        public String picture() {
            String shared = Media.thumbnail(id);
            return shared != null ? shared : frame.isEmpty() ? null : frame;
        }

        /** The video's address, a YouTube address also continues where it stopped */
        public String url() { return Media.url(id, positionMs); }

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
                out.add(new Item(o.getString("id"), o.optString("title", ""), o.optLong("pos"), o.optLong("dur"), o.optString("frame", "")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Records where a video is now, it moves to the front, and a video watched to the end is removed */
    public static void update(Context context, String id, String title, long positionMs, long durationMs) {
        if (context == null || id == null) return;
        List<Item> items = list(context);
        // The page reports a bare "YouTube" until the video's own title arrives, the title already known stays then
        title = Media.cleanTitle(id, title);
        String frame = "";
        for (Item item : items) if (item.id.equals(id)) { frame = item.frame; break; }
        if (title.isEmpty() || title.equals("YouTube") || title.equals("Patreon") || title.equals("Twitch"))
            for (Item item : items) if (item.id.equals(id)) { title = item.title; break; }
        items.removeIf(item -> item.id.equals(id));
        boolean finished = durationMs > 0 && positionMs > durationMs * 0.95;
        // A live stream has no length and nothing to continue later, clips are too short to come back to and start over by themselves
        if (!finished && durationMs > 0 && positionMs > 5000 && !Media.isLive(id) && !id.startsWith(Media.TWITCH_CLIP)) items.add(0, new Item(id, title, positionMs, durationMs, frame));
        while (items.size() > LIMIT) items.remove(items.size() - 1);
        save(context, items);
    }

    private static void save(Context context, List<Item> items) {
        JSONArray out = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject o = new JSONObject().put("id", item.id).put("title", item.title).put("pos", item.positionMs).put("dur", item.durationMs);
                if (!item.frame.isEmpty()) o.put("frame", item.frame);
                out.put(o);
            }
        } catch (Exception ignored) { }
        prefs(context).edit().putString(PREF, out.toString()).apply();
    }

    /** True when a video on the list still needs a frame for its card */
    public static boolean wantsFrame(Context context, String id) {
        if (context == null || id == null || Media.thumbnail(id) != null) return false;
        for (Item item : list(context)) if (item.id.equals(id)) return item.frame.isEmpty();
        return false;
    }

    /** Keeps a small picture taken from the video as its card picture, a data: address */
    public static void setFrame(Context context, String id, String frame) {
        if (context == null || id == null || frame == null || !frame.startsWith("data:image/") || frame.length() > 40_000) return;
        List<Item> items = list(context);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.id.equals(id)) items.set(i, new Item(item.id, item.title, item.positionMs, item.durationMs, frame));
        }
        save(context, items);
    }

    /** Where a video stopped, 0 when it is not on the list */
    public static long position(Context context, String id) {
        for (Item item : list(context)) if (item.id.equals(id)) return item.positionMs;
        return 0;
    }

    private RecentVideos() {}
}
