package com.sya.tube.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Videos picked on the phone to play after the current one on the car, kept across restarts, used on the main thread */
public final class UpNext {
    public static final class Item {
        public final String id, url;
        public String title;
        Item(String id, String url, String title) { this.id = id; this.url = url; this.title = title; }
    }

    private static final String PREF = "up_next";
    private static List<Item> items;
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences("car", 0); }

    private static List<Item> load(Context context) {
        if (items != null) return items;
        items = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs(context).getString(PREF, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.getJSONObject(i);
                items.add(new Item(o.getString("id"), o.getString("url"), o.optString("title", "")));
            }
        } catch (Exception ignored) { }
        return items;
    }

    private static void save(Context context) {
        JSONArray out = new JSONArray();
        try { for (Item item : items) out.put(new JSONObject().put("id", item.id).put("url", item.url).put("title", item.title)); } catch (Exception ignored) { }
        prefs(context).edit().putString(PREF, out.toString()).apply();
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }

    public static List<Item> list(Context context) { return new ArrayList<>(load(context)); }

    public static int size(Context context) { return load(context).size(); }

    /** Adds a video to the end of the queue and looks its title up in the background */
    public static void add(Context context, String url) {
        String id = CarRemote.videoId(url);
        if (id == null) return;
        Context app = context.getApplicationContext();
        Item item = new Item(id, url, "");
        load(app).add(item);
        save(app);
        VideoTitles.lookup(id, title -> main.post(() -> { item.title = title; save(app); }));
    }

    /** The next queued video, removed from the queue, or null when it is empty */
    public static Item pop(Context context) {
        List<Item> queue = load(context);
        if (queue.isEmpty()) return null;
        Item next = queue.remove(0);
        save(context);
        return next;
    }

    public static void clear(Context context) { load(context).clear(); save(context); }

    public static void addListener(Runnable listener) { listeners.add(listener); }

    public static void removeListener(Runnable listener) { listeners.remove(listener); }

    private UpNext() {}
}
