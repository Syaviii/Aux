package com.sya.tube.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.sya.tube.settings.TubePrefs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/** Jellyfin and Plex servers the driver signed in to, browsed and played through their own web APIs */
public final class MediaServers {
    public static final String JELLYFIN = "jellyfin", PLEX = "plex";

    public static final class Server {
        public final String id, type, name, url, userId, token, user;
        public Server(String id, String type, String name, String url, String userId, String token, String user) {
            this.id = id; this.type = type; this.name = name; this.url = url.replaceAll("/+$", ""); this.userId = userId; this.token = token; this.user = user;
        }
        JSONObject json() throws org.json.JSONException {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("url", url).put("userId", userId).put("token", token).put("user", user);
        }
        static Server from(JSONObject o) {
            return new Server(o.optString("id"), o.optString("type"), o.optString("name"), o.optString("url"), o.optString("userId"), o.optString("token"), o.optString("user"));
        }
        public String kind() { return PLEX.equals(type) ? "Plex" : "Jellyfin"; }
    }

    /** A folder to open or a video to play, as the library page lists it */
    public static final class Entry {
        public final String id, title, subtitle, image;
        public final boolean folder;
        public final long durationMs, positionMs;
        Entry(String id, String title, String subtitle, String image, boolean folder, long durationMs, long positionMs) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.image = image; this.folder = folder; this.durationMs = durationMs; this.positionMs = positionMs;
        }
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build();
    private static final OkHttpClient PROBE = HTTP.newBuilder().connectTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build();
    private static final MediaType JSON = MediaType.get("application/json");

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences("media_servers", 0); }

    public static synchronized List<Server> list(Context context) {
        List<Server> out = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs(context).getString("servers", "[]"));
            for (int i = 0; i < saved.length(); i++) out.add(Server.from(saved.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static Server get(Context context, String id) {
        for (Server server : list(context)) if (server.id.equals(id)) return server;
        return null;
    }

    public static synchronized void add(Context context, Server server) {
        List<Server> all = list(context);
        all.removeIf(s -> s.url.equals(server.url) && s.type.equals(server.type));
        all.add(server);
        save(context, all);
    }

    public static synchronized void remove(Context context, String id) {
        List<Server> all = list(context);
        all.removeIf(s -> s.id.equals(id));
        save(context, all);
    }

    private static void save(Context context, List<Server> servers) {
        JSONArray out = new JSONArray();
        try { for (Server s : servers) out.put(s.json()); } catch (Exception ignored) { }
        prefs(context).edit().putString("servers", out.toString()).apply();
    }

    /** One random id for this install, both servers list it as the playing device */
    public static String deviceId(Context context) {
        SharedPreferences p = prefs(context);
        String id = p.getString("device", null);
        if (id == null) { id = UUID.randomUUID().toString(); p.edit().putString("device", id).apply(); }
        return id;
    }

    public static String normalizeUrl(String typed) {
        String url = typed.trim();
        if (!url.contains("://")) url = "http://" + url;
        return url.replaceAll("/+$", "");
    }

    // Jellyfin

    private static String jellyfinAuth(Context context, String token) {
        return "MediaBrowser Client=\"Aux\", Device=\"Android\", DeviceId=\"" + deviceId(context) + "\", Version=\"" + com.thekirankumar.youtubeauto2.BuildConfig.VERSION_NAME + "\"" + (token == null ? "" : ", Token=\"" + token + "\"");
    }

    private static Request.Builder jellyfin(Context context, Server server, String path) {
        String auth = jellyfinAuth(context, server.token);
        return new Request.Builder().url(server.url + path).header("Authorization", auth).header("X-Emby-Authorization", auth).header("Accept", "application/json");
    }

    /** Signs in with a user name and password, the password is sent to the server once and never stored */
    public static Server signInJellyfin(Context context, String address, String user, String password) throws Exception {
        String url = normalizeUrl(address);
        String name = "Jellyfin";
        try (Response info = HTTP.newCall(new Request.Builder().url(url + "/System/Info/Public").build()).execute()) {
            if (!info.isSuccessful() || info.body() == null) throw new java.io.IOException("No Jellyfin server at this address");
            name = new JSONObject(info.body().string()).optString("ServerName", name);
        }
        String auth = jellyfinAuth(context, null);
        RequestBody body = RequestBody.create(new JSONObject().put("Username", user).put("Pw", password).toString(), JSON);
        try (Response response = HTTP.newCall(new Request.Builder().url(url + "/Users/AuthenticateByName").post(body).header("Authorization", auth).header("X-Emby-Authorization", auth).build()).execute()) {
            if (response.code() == 401) throw new SecurityException("Incorrect username or password");
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Sign in failed (" + response.code() + ")");
            JSONObject result = new JSONObject(response.body().string());
            JSONObject account = result.getJSONObject("User");
            return new Server(UUID.randomUUID().toString(), JELLYFIN, name, url, account.getString("Id"), result.getString("AccessToken"), account.optString("Name", user));
        }
    }

    private static JSONObject jellyfinGet(Context context, Server server, String path) throws Exception {
        try (Response response = HTTP.newCall(jellyfin(context, server, path).build()).execute()) {
            if (response.code() == 401) throw new SecurityException("Sign in again by adding this server in Settings");
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Server answered " + response.code());
            String text = response.body().string();
            return text.trim().startsWith("[") ? new JSONObject().put("Items", new JSONArray(text)) : new JSONObject(text);
        }
    }

    private static Entry jellyfinEntry(JSONObject item) {
        String type = item.optString("Type");
        boolean folder = item.optBoolean("IsFolder") || "Series".equals(type) || "Season".equals(type) || "BoxSet".equals(type);
        String subtitle = "";
        if ("Episode".equals(type)) {
            subtitle = item.optString("SeriesName");
            if (item.has("ParentIndexNumber") && item.has("IndexNumber")) subtitle += (subtitle.isEmpty() ? "" : " · ") + "S" + item.optInt("ParentIndexNumber") + " E" + item.optInt("IndexNumber");
        } else if (item.has("ProductionYear")) subtitle = String.valueOf(item.optInt("ProductionYear"));
        if (folder && item.has("ChildCount")) subtitle = item.optInt("ChildCount") + " items";
        long duration = item.optLong("RunTimeTicks") / 10_000;
        JSONObject user = item.optJSONObject("UserData");
        long position = user == null ? 0 : user.optLong("PlaybackPositionTicks") / 10_000;
        return new Entry(item.optString("Id"), item.optString("Name"), subtitle, "/Items/" + item.optString("Id") + "/Images/Primary?fillWidth=400&quality=80", folder, duration, position);
    }

    private static List<Entry> jellyfinEntries(JSONObject result) {
        List<Entry> out = new ArrayList<>();
        JSONArray items = result.optJSONArray("Items");
        if (items != null) for (int i = 0; i < items.length(); i++) out.add(jellyfinEntry(items.optJSONObject(i)));
        return out;
    }

    // Plex

    private static Request.Builder plex(Context context, String url, String token) {
        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/json").header("X-Plex-Product", "Aux")
            .header("X-Plex-Version", com.thekirankumar.youtubeauto2.BuildConfig.VERSION_NAME).header("X-Plex-Client-Identifier", deviceId(context))
            .header("X-Plex-Platform", "Android").header("X-Plex-Device", "Android");
        if (token != null && !token.isEmpty()) builder.header("X-Plex-Token", token);
        return builder;
    }

    private static JSONObject plexGet(Context context, Server server, String path) throws Exception {
        try (Response response = HTTP.newCall(plex(context, server.url + path, server.token).build()).execute()) {
            if (response.code() == 401) throw new SecurityException("Sign in again by adding this server in Settings");
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Server answered " + response.code());
            return new JSONObject(response.body().string()).optJSONObject("MediaContainer");
        }
    }

    /** A sign in code to enter at plex.tv/link, the first value is the code's id for polling, the second the code */
    public static String[] plexPin(Context context) throws Exception {
        RequestBody form = new FormBody.Builder().add("strong", "false").build();
        try (Response response = HTTP.newCall(plex(context, "https://plex.tv/api/v2/pins", null).post(form).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Plex didn't answer (" + response.code() + ")");
            JSONObject pin = new JSONObject(response.body().string());
            return new String[]{pin.getString("id"), pin.getString("code")};
        }
    }

    /** The account token once the code was entered at plex.tv/link, null while it still waits */
    public static String plexPinToken(Context context, String pinId) throws Exception {
        try (Response response = HTTP.newCall(plex(context, "https://plex.tv/api/v2/pins/" + pinId, null).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            String token = new JSONObject(response.body().string()).optString("authToken", "");
            return token.isEmpty() || "null".equals(token) ? null : token;
        }
    }

    /** Every server the Plex account can reach, each at the first address that answers from this phone */
    public static List<Server> plexServers(Context context, String accountToken) throws Exception {
        List<Server> out = new ArrayList<>();
        JSONArray resources;
        try (Response response = HTTP.newCall(plex(context, "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", accountToken).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Plex didn't list servers (" + response.code() + ")");
            resources = new JSONArray(response.body().string());
        }
        for (int i = 0; i < resources.length(); i++) {
            JSONObject resource = resources.getJSONObject(i);
            if (!resource.optString("provides").contains("server")) continue;
            String token = resource.optString("accessToken", accountToken);
            JSONArray connections = resource.optJSONArray("connections");
            if (connections == null) continue;
            // Home network first, then the server's own internet address, Plex's relay last
            List<JSONObject> order = new ArrayList<>();
            for (int pass = 0; pass < 3; pass++) for (int j = 0; j < connections.length(); j++) {
                JSONObject c = connections.getJSONObject(j);
                int rank = c.optBoolean("relay") ? 2 : c.optBoolean("local") ? 0 : 1;
                if (rank == pass) order.add(c);
            }
            for (JSONObject connection : order) {
                String uri = connection.optString("uri");
                if (plexReachable(context, uri, token)) { out.add(new Server(UUID.randomUUID().toString(), PLEX, resource.optString("name", "Plex"), uri, "", token, "")); break; }
            }
        }
        return out;
    }

    /** A Plex server added by its address, for a server that lets this network in without an account or with a token */
    public static Server plexByAddress(Context context, String address, String token) throws Exception {
        String url = normalizeUrl(address);
        try (Response response = HTTP.newCall(plex(context, url + "/", token).build()).execute()) {
            if (response.code() == 401) throw new SecurityException("Use your Plex account to sign in to this server");
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("No Plex server at this address");
            JSONObject container = new JSONObject(response.body().string()).optJSONObject("MediaContainer");
            String name = container == null ? "Plex" : container.optString("friendlyName", "Plex");
            return new Server(UUID.randomUUID().toString(), PLEX, name, url, "", token == null ? "" : token, "");
        }
    }

    private static boolean plexReachable(Context context, String uri, String token) {
        try (Response response = PROBE.newCall(plex(context, uri + "/identity", token).build()).execute()) { return response.isSuccessful(); }
        catch (Exception ignored) { return false; }
    }

    private static Entry plexEntry(JSONObject item) {
        String type = item.optString("type");
        boolean folder = "show".equals(type) || "season".equals(type) || "collection".equals(type);
        String subtitle = "";
        if ("episode".equals(type)) subtitle = item.optString("grandparentTitle") + " · S" + item.optInt("parentIndex") + " E" + item.optInt("index");
        else if ("season".equals(type)) subtitle = item.optInt("leafCount") + " episodes";
        else if ("show".equals(type)) subtitle = item.optInt("childCount") + " seasons";
        else if (item.has("year")) subtitle = String.valueOf(item.optInt("year"));
        String thumb = item.optString("thumb", item.optString("grandparentThumb", ""));
        String image = thumb.isEmpty() ? "" : "/photo/:/transcode?width=400&height=225&minSize=1&upscale=1&url=" + Uri.encode(thumb);
        return new Entry("meta:" + item.optString("ratingKey"), item.optString("title"), subtitle, image, folder, item.optLong("duration"), item.optLong("viewOffset"));
    }

    private static List<Entry> plexEntries(JSONObject container) {
        List<Entry> out = new ArrayList<>();
        JSONArray items = container == null ? null : container.optJSONArray("Metadata");
        if (items != null) for (int i = 0; i < items.length(); i++) out.add(plexEntry(items.optJSONObject(i)));
        return out;
    }

    // Browsing, shared by both kinds

    /** Videos started and not finished, the server's own Continue Watching or On Deck */
    public static List<Entry> resume(Context context, Server server) throws Exception {
        if (PLEX.equals(server.type)) return plexEntries(plexGet(context, server, "/library/onDeck"));
        try { return jellyfinEntries(jellyfinGet(context, server, "/UserItems/Resume?userId=" + server.userId + "&Limit=16&MediaTypes=Video&Fields=UserData")); }
        catch (java.io.IOException olderServer) { return jellyfinEntries(jellyfinGet(context, server, "/Users/" + server.userId + "/Items/Resume?Limit=16&MediaTypes=Video&Fields=UserData")); }
    }

    public static List<Entry> libraries(Context context, Server server) throws Exception {
        List<Entry> out = new ArrayList<>();
        if (PLEX.equals(server.type)) {
            JSONObject container = plexGet(context, server, "/library/sections");
            JSONArray sections = container == null ? null : container.optJSONArray("Directory");
            if (sections != null) for (int i = 0; i < sections.length(); i++) {
                JSONObject s = sections.getJSONObject(i);
                String type = s.optString("type");
                if (!"movie".equals(type) && !"show".equals(type)) continue;
                out.add(new Entry("section:" + s.optString("key"), s.optString("title"), "show".equals(type) ? "Shows" : "Movies", "", true, 0, 0));
            }
            return out;
        }
        for (Entry e : jellyfinEntries(jellyfinGet(context, server, "/Users/" + server.userId + "/Views"))) out.add(new Entry(e.id, e.title, "", e.image, true, 0, 0));
        return out;
    }

    public static List<Entry> children(Context context, Server server, String parent) throws Exception {
        if (PLEX.equals(server.type)) {
            String path = parent.startsWith("section:") ? "/library/sections/" + parent.substring(8) + "/all" : "/library/metadata/" + parent.substring(5) + "/children";
            return plexEntries(plexGet(context, server, path));
        }
        return jellyfinEntries(jellyfinGet(context, server, "/Users/" + server.userId + "/Items?ParentId=" + Uri.encode(parent)
            + "&SortBy=ParentIndexNumber,IndexNumber,SortName&SortOrder=Ascending&Fields=UserData,ChildCount&Limit=500"));
    }

    public static String title(Context context, Server server, String id) {
        try {
            if (PLEX.equals(server.type)) {
                if (id.startsWith("section:")) for (Entry e : libraries(context, server)) if (e.id.equals(id)) return e.title;
                JSONObject container = plexGet(context, server, "/library/metadata/" + id.substring(5));
                JSONArray items = container == null ? null : container.optJSONArray("Metadata");
                return items == null || items.length() == 0 ? "" : items.getJSONObject(0).optString("title");
            }
            return jellyfinGet(context, server, "/Users/" + server.userId + "/Items/" + Uri.encode(id)).optString("Name");
        } catch (Exception ignored) { return ""; }
    }

    /** The picked video and the ones after it in its season, ready for the player, with each one's place to continue from */
    public static Queue queue(Context context, Server server, String itemId) throws Exception {
        List<JSONObject> siblings = new ArrayList<>();
        JSONObject picked;
        if (PLEX.equals(server.type)) {
            JSONObject container = plexGet(context, server, "/library/metadata/" + itemId.substring(5));
            picked = container.getJSONArray("Metadata").getJSONObject(0);
            if ("episode".equals(picked.optString("type")) && picked.has("parentRatingKey")) {
                JSONArray items = plexGet(context, server, "/library/metadata/" + picked.optString("parentRatingKey") + "/children").optJSONArray("Metadata");
                if (items != null) for (int i = 0; i < items.length(); i++) siblings.add(items.getJSONObject(i));
            }
        } else {
            picked = jellyfinGet(context, server, "/Users/" + server.userId + "/Items/" + Uri.encode(itemId) + "?Fields=UserData");
            if ("Episode".equals(picked.optString("Type")) && picked.has("SeasonId")) {
                JSONArray items = jellyfinGet(context, server, "/Users/" + server.userId + "/Items?ParentId=" + picked.optString("SeasonId")
                    + "&SortBy=ParentIndexNumber,IndexNumber,SortName&SortOrder=Ascending&Fields=UserData").optJSONArray("Items");
                if (items != null) for (int i = 0; i < items.length(); i++) siblings.add(items.getJSONObject(i));
            }
        }
        if (siblings.isEmpty()) siblings.add(picked);
        Queue queue = new Queue();
        for (JSONObject item : siblings) {
            Bundle playable = PLEX.equals(server.type) ? plexPlayable(context, server, item) : jellyfinPlayable(context, server, item);
            if (playable == null) continue;
            if (playable.getString("item", "").equals(PLEX.equals(server.type) ? itemId.substring(5) : itemId)) queue.index = queue.items.size();
            queue.items.add(playable);
        }
        return queue;
    }

    public static final class Queue {
        public final ArrayList<Bundle> items = new ArrayList<>();
        public int index;
    }

    /** On mobile data below 1080p the server converts the video down, otherwise the original file plays as it is */
    private static int transcodeHeight(Context context) {
        if (!TubePrefs.onMobileData(context)) return 0;
        String quality = TubePrefs.get(context).getString(TubePrefs.QUALITY_MOBILE, "hd720");
        return "medium".equals(quality) ? 360 : "large".equals(quality) ? 480 : "hd720".equals(quality) ? 720 : 0;
    }

    private static int bitrateFor(int height) { return height <= 360 ? 800_000 : height <= 480 ? 1_500_000 : 3_000_000; }

    private static Bundle jellyfinPlayable(Context context, Server server, JSONObject item) {
        String id = item.optString("Id");
        if (item.optBoolean("IsFolder")) return null;
        Entry entry = jellyfinEntry(item);
        Bundle b = new Bundle();
        b.putString("title", entry.title);
        b.putString("subtitle", entry.subtitle.isEmpty() ? server.name : entry.subtitle);
        b.putString("key", "jf:" + server.id + ":" + id);
        b.putString("server", server.id);
        b.putString("item", id);
        b.putLong("start", entry.positionMs);
        // Jellyfin 10 reads api_key, newer servers read ApiKey for converted streams, both are sent
        String key = "&api_key=" + server.token + "&ApiKey=" + server.token;
        String direct = server.url + "/Videos/" + id + "/stream?static=true&MediaSourceId=" + id + key;
        int height = transcodeHeight(context);
        int bitrate = bitrateFor(height);
        String transcode = server.url + "/Videos/" + id + "/master.m3u8?MediaSourceId=" + id + key + "&VideoCodec=h264&AudioCodec=aac"
            + "&VideoBitrate=" + bitrate + "&MaxStreamingBitrate=" + (bitrate + 192_000) + "&AudioBitrate=128000&TranscodingMaxAudioChannels=2&SegmentContainer=ts"
            + (height > 0 ? "&MaxHeight=" + height + "&MaxWidth=" + height * 16 / 9 : "&MaxHeight=1080") + "&DeviceId=" + deviceId(context) + "&PlaySessionId=" + UUID.randomUUID();
        // A file the phone can't decode plays converted instead
        if (height > 0) { b.putString("uri", transcode); b.putString("mime", "application/x-mpegURL"); b.putString("method", "Transcode"); }
        else { b.putString("uri", direct); b.putString("fallback", transcode); b.putString("method", "DirectPlay"); }
        return b;
    }

    private static Bundle plexPlayable(Context context, Server server, JSONObject item) {
        String type = item.optString("type");
        if (!"movie".equals(type) && !"episode".equals(type) && !"clip".equals(type)) return null;
        JSONArray media = item.optJSONArray("Media");
        JSONObject part = media == null || media.length() == 0 ? null : media.optJSONObject(0).optJSONArray("Part") == null ? null : media.optJSONObject(0).optJSONArray("Part").optJSONObject(0);
        if (part == null) return null;
        Entry entry = plexEntry(item);
        String rk = item.optString("ratingKey");
        Bundle b = new Bundle();
        b.putString("title", entry.title);
        b.putString("subtitle", entry.subtitle.isEmpty() ? server.name : entry.subtitle);
        b.putString("key", "plex:" + server.id + ":" + rk);
        b.putString("server", server.id);
        b.putString("item", rk);
        b.putLong("start", entry.positionMs);
        b.putLong("duration", entry.durationMs);
        String token = server.token.isEmpty() ? "" : "X-Plex-Token=" + server.token;
        String direct = server.url + part.optString("key") + (token.isEmpty() ? "" : "?" + token);
        int height = transcodeHeight(context);
        String session = UUID.randomUUID().toString();
        String transcode = server.url + "/video/:/transcode/universal/start.m3u8?path=" + Uri.encode("/library/metadata/" + rk) + "&mediaIndex=0&partIndex=0&protocol=hls"
            + "&fastSeek=1&directPlay=0&directStream=1&directStreamAudio=1&videoResolution=" + (height > 0 ? height * 16 / 9 + "x" + height : "1920x1080")
            + "&maxVideoBitrate=" + bitrateFor(height > 0 ? height : 1080) / 1000 + "&session=" + session + "&X-Plex-Session-Identifier=" + session
            + "&X-Plex-Client-Identifier=" + deviceId(context) + "&X-Plex-Product=Aux&X-Plex-Platform=Chrome" + (token.isEmpty() ? "" : "&" + token);
        if (height > 0) { b.putString("uri", transcode); b.putString("mime", "application/x-mpegURL"); }
        else { b.putString("uri", direct); b.putString("fallback", transcode); }
        return b;
    }

    /** Proxied through the library page, a server's own address would be plain http inside an https page, and its token stays out of the page */
    public static byte[] image(Context context, Server server, String path) {
        String url = server.url + path;
        Request request = PLEX.equals(server.type) ? plex(context, url, server.token).header("Accept", "image/*").build() : jellyfin(context, server, path).header("Accept", "image/*").build();
        try (Response response = HTTP.newCall(request).execute()) {
            return response.isSuccessful() && response.body() != null ? response.body().bytes() : null;
        } catch (Exception ignored) { return null; }
    }

    /** Tells the server where playback is, its own apps then show the same place to continue from */
    public static void report(Context context, Bundle item, String state, long positionMs, long durationMs) {
        Server server = item == null ? null : get(context, item.getString("server", ""));
        if (server == null) return;
        String id = item.getString("item");
        new Thread(() -> {
            try {
                Request request;
                if (PLEX.equals(server.type)) {
                    String url = server.url + "/:/timeline?ratingKey=" + id + "&key=" + Uri.encode("/library/metadata/" + id) + "&state=" + ("stop".equals(state) ? "stopped" : "start".equals(state) ? "playing" : state)
                        + "&time=" + positionMs + "&duration=" + durationMs;
                    request = plex(context, url, server.token).build();
                } else {
                    String path = "start".equals(state) ? "/Sessions/Playing" : "stop".equals(state) ? "/Sessions/Playing/Stopped" : "/Sessions/Playing/Progress";
                    JSONObject body = new JSONObject().put("ItemId", id).put("MediaSourceId", id).put("PositionTicks", positionMs * 10_000).put("IsPaused", "paused".equals(state))
                        .put("CanSeek", true).put("PlayMethod", item.getString("method", "DirectPlay"));
                    request = jellyfin(context, server, path).post(RequestBody.create(body.toString(), JSON)).build();
                }
                try (Response ignored = HTTP.newCall(request).execute()) { }
            } catch (Exception ignored) { }
        }, "TubeReport").start();
    }

    private MediaServers() {}
}
