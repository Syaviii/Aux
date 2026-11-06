package com.sya.tube.player;

import android.content.Context;
import com.sya.tube.settings.TubePrefs;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sponsor segments from the SponsorBlock community database. Only the first four characters of the video id's hash are
 * sent, the server answers with every video sharing them, so it never learns which video is playing
 */
public final class SponsorBlock {
    public static final class Segment {
        public final long startMs, endMs;
        public final String category, mode;
        Segment(long startMs, long endMs, String category, String mode) { this.startMs = startMs; this.endMs = endMs; this.category = category; this.mode = mode; }

        public String name() { return TubePrefs.segmentName(category); }

        public JSONObject json() throws org.json.JSONException {
            return new JSONObject().put("s", startMs / 1000.0).put("e", endMs / 1000.0).put("c", category).put("m", mode).put("n", name());
        }
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "TubeSponsorBlock"));
    private static final android.util.LruCache<String, List<Segment>> CACHE = new android.util.LruCache<>(32);
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Looks the video up off the main thread and hands back the segments to act on, empty when there are none or it is off */
    public static void lookup(Context context, String videoId, java.util.function.Consumer<List<Segment>> done) {
        Context app = context.getApplicationContext();
        if (videoId == null || !TubePrefs.sponsorBlockOn(app)) { done.accept(Collections.emptyList()); return; }
        WORKER.execute(() -> {
            List<Segment> found = fetch(app, videoId);
            MAIN.post(() -> done.accept(found));
        });
    }

    static List<Segment> fetch(Context context, String videoId) {
        List<String> wanted = new ArrayList<>();
        for (String[] segment : TubePrefs.SEGMENTS) if (!"off".equals(TubePrefs.segmentMode(context, segment[0]))) wanted.add(segment[0]);
        if (wanted.isEmpty()) return Collections.emptyList();
        String key = videoId + "\n" + String.join(",", wanted);
        List<Segment> cached = CACHE.get(key);
        if (cached != null) return cached;
        List<Segment> out = new ArrayList<>();
        try {
            String url = "https://sponsor.ajay.app/api/skipSegments/" + hashPrefix(videoId) + "?categories=" + android.net.Uri.encode(new JSONArray(wanted).toString());
            try (Response response = HTTP.newCall(new Request.Builder().url(url).header("User-Agent", "Aux").build()).execute()) {
                // 404 means no video with that prefix has segments
                if (response.code() == 404) { CACHE.put(key, out); return out; }
                if (!response.isSuccessful() || response.body() == null) return out;
                JSONArray videos = new JSONArray(response.body().string());
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject video = videos.getJSONObject(i);
                    if (!videoId.equals(video.optString("videoID"))) continue;
                    JSONArray segments = video.getJSONArray("segments");
                    for (int j = 0; j < segments.length(); j++) {
                        JSONObject segment = segments.getJSONObject(j);
                        if (!"skip".equals(segment.optString("actionType", "skip"))) continue;
                        JSONArray range = segment.getJSONArray("segment");
                        long start = (long) (range.getDouble(0) * 1000), end = (long) (range.getDouble(1) * 1000);
                        String category = segment.optString("category");
                        // A segment shorter than a second is not worth a jump
                        if (end - start < 1000) continue;
                        out.add(new Segment(start, end, category, TubePrefs.segmentMode(context, category)));
                    }
                }
            }
            Collections.sort(out, (a, b) -> Long.compare(a.startMs, b.startMs));
            CACHE.put(key, out);
            com.sya.tube.diag.Diag.log("sponsorblock", videoId + " segments=" + out.size());
        } catch (Exception error) {
            com.sya.tube.diag.Diag.log("sponsorblock", "lookup failed " + error.getClass().getSimpleName());
        }
        return out;
    }

    /** Settings changed, the next lookup asks again */
    public static void forget() { CACHE.evictAll(); }

    public static String json(List<Segment> segments) {
        JSONArray out = new JSONArray();
        try { for (Segment segment : segments) out.put(segment.json()); } catch (org.json.JSONException ignored) { }
        return out.toString();
    }

    private static String hashPrefix(String videoId) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(videoId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("%02x%02x", hash[0], hash[1]);
    }

    private SponsorBlock() {}
}
