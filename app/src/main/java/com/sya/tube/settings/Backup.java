package com.sya.tube.settings;

import android.content.Context;
import android.content.SharedPreferences;
import com.sya.tube.library.MediaServers;
import com.sya.tube.library.Subscriptions;
import com.thekirankumar.youtubeauto2.bookmarks.Bookmark;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarkStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Everything worth keeping across a reinstall or a new phone, in one file. Google sign in and media server tokens stay
 * out, a backup file can end up anywhere, so servers only keep their addresses and are signed in to again
 */
public final class Backup {
    private static final String[] CAR_KEYS = {"recent_videos", "up_next"};

    public static String export(Context context) throws Exception {
        JSONObject root = new JSONObject().put("app", "Aux").put("format", 1).put("version", com.thekirankumar.youtubeauto2.BuildConfig.VERSION_NAME).put("created", System.currentTimeMillis());
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> entry : TubePrefs.get(context).getAll().entrySet()) {
            Object value = entry.getValue();
            if (!entry.getKey().startsWith("pref_") || entry.getKey().equals("pref_handoff_prepare_ms")) continue;
            String type = value instanceof Boolean ? "b" : value instanceof Integer ? "i" : value instanceof Long ? "l" : value instanceof Float ? "f" : value instanceof String ? "s" : null;
            if (type != null) settings.put(entry.getKey(), new JSONObject().put("t", type).put("v", value));
        }
        root.put("settings", settings);
        SharedPreferences car = context.getSharedPreferences("car", 0);
        JSONObject carState = new JSONObject();
        for (String key : CAR_KEYS) if (car.contains(key)) carState.put(key, car.getString(key, ""));
        carState.put("aspect_ratio", car.getInt("aspect_ratio", 0));
        root.put("car", carState);
        JSONArray channels = new JSONArray();
        for (Subscriptions.Channel c : Subscriptions.list(context)) channels.put(new JSONObject().put("id", c.id).put("name", c.name).put("avatar", c.avatar));
        root.put("channels", channels);
        JSONArray servers = new JSONArray();
        for (MediaServers.Server s : MediaServers.list(context)) servers.put(new JSONObject().put("type", s.type).put("name", s.name).put("url", s.url).put("user", s.user));
        root.put("servers", servers);
        JSONArray bookmarks = new JSONArray();
        for (Bookmark b : BookmarkStore.get().all()) if (!b.isPreventDelete()) bookmarks.put(new JSONObject().put("url", b.getUrl()).put("title", b.getTitle()));
        root.put("bookmarks", bookmarks);
        JSONObject positions = new JSONObject();
        for (Map.Entry<String, ?> entry : context.getSharedPreferences("media_positions", 0).getAll().entrySet()) positions.put(entry.getKey(), String.valueOf(entry.getValue()));
        root.put("positions", positions);
        return root.toString(2);
    }

    /** Restores a file made by export, returns what came back for the confirmation */
    public static String restore(Context context, String text) throws Exception {
        JSONObject root = new JSONObject(text);
        // Backups made before the rename say Tube
        String app = root.optString("app");
        if (!"Aux".equals(app) && !"Tube".equals(app)) throw new IllegalArgumentException("This isn't an Aux backup");
        SharedPreferences.Editor edit = TubePrefs.get(context).edit();
        JSONObject settings = root.optJSONObject("settings");
        int restored = 0;
        if (settings != null) for (java.util.Iterator<String> keys = settings.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!key.startsWith("pref_")) continue;
            JSONObject item = settings.getJSONObject(key);
            switch (item.optString("t")) {
                case "b": edit.putBoolean(key, item.getBoolean("v")); break;
                case "i": edit.putInt(key, item.getInt("v")); break;
                case "l": edit.putLong(key, item.getLong("v")); break;
                case "f": edit.putFloat(key, (float) item.getDouble("v")); break;
                case "s": edit.putString(key, item.getString("v")); break;
                default: continue;
            }
            restored++;
        }
        edit.apply();
        JSONObject car = root.optJSONObject("car");
        if (car != null) {
            SharedPreferences.Editor carEdit = context.getSharedPreferences("car", 0).edit();
            for (String key : CAR_KEYS) if (car.has(key)) carEdit.putString(key, car.getString(key));
            if (car.has("aspect_ratio")) carEdit.putInt("aspect_ratio", car.getInt("aspect_ratio"));
            carEdit.apply();
        }
        List<Subscriptions.Channel> channels = new ArrayList<>();
        JSONArray savedChannels = root.optJSONArray("channels");
        if (savedChannels != null) for (int i = 0; i < savedChannels.length(); i++) {
            JSONObject c = savedChannels.getJSONObject(i);
            channels.add(new Subscriptions.Channel(c.getString("id"), c.optString("name"), c.optString("avatar")));
        }
        int newChannels = Subscriptions.add(context, channels);
        JSONArray bookmarks = root.optJSONArray("bookmarks");
        int savedBookmarks = 0;
        if (bookmarks != null) for (int i = 0; i < bookmarks.length(); i++) {
            JSONObject b = bookmarks.getJSONObject(i);
            Bookmark bookmark = new Bookmark();
            bookmark.setUrl(b.getString("url"));
            bookmark.setTitle(b.optString("title"));
            BookmarkStore.get().save(bookmark);
            savedBookmarks++;
        }
        JSONObject positions = root.optJSONObject("positions");
        if (positions != null) {
            SharedPreferences.Editor posEdit = context.getSharedPreferences("media_positions", 0).edit();
            for (java.util.Iterator<String> keys = positions.keys(); keys.hasNext(); ) { String key = keys.next(); posEdit.putString(key, positions.getString(key)); }
            posEdit.apply();
        }
        com.sya.tube.player.SponsorBlock.forget();
        StringBuilder out = new StringBuilder("Restored " + restored + " settings, " + newChannels + " new channels and " + savedBookmarks + " bookmarks");
        // Servers this phone is already signed in to need nothing, the rest are named so they can be added again
        List<JSONObject> missing = new ArrayList<>();
        JSONArray servers = root.optJSONArray("servers");
        if (servers != null) for (int i = 0; i < servers.length(); i++) {
            JSONObject s = servers.getJSONObject(i);
            boolean known = false;
            for (MediaServers.Server have : MediaServers.list(context)) known |= have.type.equals(s.optString("type")) && have.url.equals(s.optString("url"));
            if (!known) missing.add(s);
        }
        if (!missing.isEmpty()) {
            out.append("\n\nSign in again to ");
            for (int i = 0; i < missing.size(); i++) {
                JSONObject s = missing.get(i);
                out.append(i == 0 ? "" : i == missing.size() - 1 ? " and " : ", ").append(s.optString("name")).append(" (").append(s.optString("url")).append(")");
            }
            out.append(" under Media servers");
        }
        return out.toString();
    }

    private Backup() {}
}
