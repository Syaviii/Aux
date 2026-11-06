package com.sya.tube.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/**
 * Channels followed in Tube itself, no Google account involved. Each channel's newest videos come from YouTube's public
 * RSS feed, which is a few kilobytes per channel instead of a whole channel page
 */
public final class Subscriptions {
    public static final class Channel {
        public final String id, name, avatar;
        public Channel(String id, String name, String avatar) { this.id = id; this.name = name == null ? "" : name; this.avatar = avatar == null ? "" : avatar; }
    }

    public static final class Video {
        public final String id, title, channelId, channel;
        public final long published, views;
        public final boolean shorts;
        Video(String id, String title, String channelId, String channel, long published, long views, boolean shorts) {
            this.id = id; this.title = title; this.channelId = channelId; this.channel = channel; this.published = published; this.views = views; this.shorts = shorts;
        }
    }

    private static final String PREFS = "subscriptions";
    private static final long FRESH_MS = 30 * 60_000;
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build();
    private static final ExecutorService REFRESH = Executors.newSingleThreadExecutor(r -> new Thread(r, "TubeFeed"));
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static volatile List<Video> feed;
    private static volatile long fetchedAt;
    private static volatile boolean refreshing;
    /** Goes up each time the feed changes, an open channels page reloads when it sees a new value */
    public static volatile int version;

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS, 0); }

    public static synchronized List<Channel> list(Context context) {
        List<Channel> out = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs(context).getString("channels", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.getJSONObject(i);
                out.add(new Channel(o.getString("id"), o.optString("name"), o.optString("avatar")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static boolean follows(Context context, String channelId) {
        for (Channel channel : list(context)) if (channel.id.equals(channelId)) return true;
        return false;
    }

    private static synchronized void save(Context context, List<Channel> channels) {
        JSONArray out = new JSONArray();
        try { for (Channel c : channels) out.put(new JSONObject().put("id", c.id).put("name", c.name).put("avatar", c.avatar)); } catch (Exception ignored) { }
        prefs(context).edit().putString("channels", out.toString()).apply();
        fetchedAt = 0;
        version++;
        for (Runnable listener : listeners) listener.run();
    }

    /** Adds channels not followed yet, returns how many were new */
    public static synchronized int add(Context context, List<Channel> channels) {
        List<Channel> all = list(context);
        Set<String> known = new HashSet<>();
        for (Channel c : all) known.add(c.id);
        int added = 0;
        for (Channel c : channels) if (c.id.startsWith("UC") && known.add(c.id)) { all.add(c); added++; }
        if (added > 0) save(context, all);
        return added;
    }

    public static synchronized void remove(Context context, String channelId) {
        List<Channel> all = list(context);
        all.removeIf(c -> c.id.equals(channelId));
        save(context, all);
        if (feed != null) { List<Video> kept = new ArrayList<>(feed); kept.removeIf(v -> v.channelId.equals(channelId)); feed = kept; }
    }

    public static synchronized void replaceAll(Context context, List<Channel> channels) { save(context, channels); }

    public static void addListener(Runnable listener) { listeners.add(listener); }
    public static void removeListener(Runnable listener) { listeners.remove(listener); }

    /** The newest videos across every followed channel, newest first, a stale or missing feed is refreshed in the background */
    public static List<Video> feed(Context context) {
        if (feed == null) feed = readCache(context);
        if (System.currentTimeMillis() - fetchedAt > FRESH_MS) refresh(context);
        return feed;
    }

    public static boolean isRefreshing() { return refreshing; }

    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        if (refreshing) return;
        refreshing = true;
        REFRESH.execute(() -> {
            try {
                List<Channel> channels = list(app);
                List<Video> all = Collections.synchronizedList(new ArrayList<>());
                List<Video> before = feed == null ? new ArrayList<>() : new ArrayList<>(feed);
                // A few feeds at a time, a large import stays gentle on the connection
                ExecutorService pool = Executors.newFixedThreadPool(6);
                for (Channel channel : channels) pool.execute(() -> {
                    List<Video> found = channelVideos(channel.id);
                    // A channel that can't be read right now keeps the videos it had, instead of vanishing from the list
                    if (found.isEmpty()) for (Video v : before) if (v.channelId.equals(channel.id)) found.add(v);
                    all.addAll(found);
                });
                pool.shutdown();
                pool.awaitTermination(90, TimeUnit.SECONDS);
                List<Video> sorted = new ArrayList<>(all);
                sorted.sort((a, b) -> Long.compare(b.published, a.published));
                if (sorted.size() > 400) sorted = new ArrayList<>(sorted.subList(0, 400));
                // Nothing came back at all, most likely no signal, the last feed stays
                if (!sorted.isEmpty() || channels.isEmpty()) { feed = sorted; fetchedAt = System.currentTimeMillis(); writeCache(app, sorted); version++; }
                com.sya.tube.diag.Diag.log("feed", "channels=" + channels.size() + " videos=" + sorted.size());
            } catch (Exception error) {
                com.sya.tube.diag.Diag.log("feed", "refresh failed " + error.getClass().getSimpleName());
            } finally { refreshing = false; }
        });
    }

    /** One channel's newest videos from its feed, or from its Videos tab when YouTube doesn't serve the feed */
    public static List<Video> channelVideos(String channelId) {
        List<Video> out = new ArrayList<>();
        Request request = new Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId).header("User-Agent", "Mozilla/5.0").build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) parse(response.body().byteStream(), out);
        } catch (Exception ignored) { }
        if (out.isEmpty()) out.addAll(channelTab(channelId));
        return out;
    }

    /** YouTube's RSS feeds answer 404 for some channels at times, the channel page's Videos tab is read instead, a little heavier */
    private static List<Video> channelTab(String channelId) {
        List<Video> out = new ArrayList<>();
        try {
            com.sya.tube.player.YoutubeStreams.ready();
            var service = org.schabi.newpipe.extractor.ServiceList.YouTube;
            var info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, "https://www.youtube.com/channel/" + channelId);
            for (var tab : info.getTabs()) {
                if (!tab.getContentFilters().contains(org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS)) continue;
                var videos = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, tab);
                for (var item : videos.getRelatedItems()) {
                    if (!(item instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
                    var stream = (org.schabi.newpipe.extractor.stream.StreamInfoItem) item;
                    String id = android.net.Uri.parse(stream.getUrl()).getQueryParameter("v");
                    if (id == null) continue;
                    long published = stream.getUploadDate() == null ? 0 : stream.getUploadDate().offsetDateTime().toInstant().toEpochMilli();
                    out.add(new Video(id, stream.getName(), channelId, info.getName(), published, Math.max(0, stream.getViewCount()), stream.isShortFormContent()));
                    if (out.size() >= 15) break;
                }
                break;
            }
        } catch (Exception error) {
            com.sya.tube.diag.Diag.log("feed", "channel " + channelId + " unavailable " + error.getClass().getSimpleName());
        }
        return out;
    }

    private static void parse(InputStream stream, List<Video> out) throws Exception {
        XmlPullParser xml = Xml.newPullParser();
        xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xml.setInput(stream, "UTF-8");
        String channel = "", channelId = "", id = null, title = null, link = "", author = null;
        long published = 0, views = 0;
        boolean entry = false;
        for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = xml.getName();
                if ("entry".equals(name)) { entry = true; id = title = author = null; link = ""; published = views = 0; }
                else if ("yt:videoId".equals(name) && entry) id = xml.nextText();
                else if ("yt:channelId".equals(name)) { String value = xml.nextText(); if (!entry) channelId = value; }
                else if ("title".equals(name)) { String value = xml.nextText(); if (entry) title = value; else channel = value; }
                else if ("name".equals(name) && entry) author = xml.nextText();
                else if ("link".equals(name) && entry && "alternate".equals(xml.getAttributeValue(null, "rel"))) link = String.valueOf(xml.getAttributeValue(null, "href"));
                else if ("published".equals(name) && entry) published = time(xml.nextText());
                else if ("media:statistics".equals(name)) { try { views = Long.parseLong(xml.getAttributeValue(null, "views")); } catch (Exception ignored) { } }
            } else if (event == XmlPullParser.END_TAG && "entry".equals(xml.getName())) {
                entry = false;
                if (id != null && title != null) out.add(new Video(id, title, channelId, author == null ? channel : author, published, views, link.contains("/shorts/")));
            }
        }
    }

    private static long time(String iso) {
        try { return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli(); } catch (Exception ignored) { return 0; }
    }

    private static java.io.File cacheFile(Context context) { return new java.io.File(context.getCacheDir(), "channel_feed.json"); }

    private static List<Video> readCache(Context context) {
        List<Video> out = new ArrayList<>();
        try {
            byte[] data = java.nio.file.Files.readAllBytes(cacheFile(context).toPath());
            JSONObject root = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            fetchedAt = root.optLong("at");
            JSONArray videos = root.getJSONArray("videos");
            for (int i = 0; i < videos.length(); i++) {
                JSONObject v = videos.getJSONObject(i);
                out.add(new Video(v.getString("id"), v.optString("t"), v.optString("cid"), v.optString("c"), v.optLong("p"), v.optLong("v"), v.optBoolean("s")));
            }
        } catch (Exception ignored) { fetchedAt = 0; }
        return out;
    }

    private static void writeCache(Context context, List<Video> videos) {
        try {
            JSONArray out = new JSONArray();
            for (Video v : videos) out.put(new JSONObject().put("id", v.id).put("t", v.title).put("cid", v.channelId).put("c", v.channel).put("p", v.published).put("v", v.views).put("s", v.shorts));
            java.nio.file.Files.write(cacheFile(context).toPath(), new JSONObject().put("at", fetchedAt).put("videos", out).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    private Subscriptions() {}
}
