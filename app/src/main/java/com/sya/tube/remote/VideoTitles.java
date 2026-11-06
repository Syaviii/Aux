package com.sya.tube.remote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

/** Video titles from YouTube's public oEmbed endpoint, a few hundred bytes per lookup, cached for the session */
public final class VideoTitles {
    private static final OkHttpClient http = new OkHttpClient();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "TubeTitles"));
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Calls back on a worker thread, only when a title was found */
    public static void lookup(String id, Consumer<String> done) {
        String cached = cache.get(id);
        if (cached != null) { done.accept(cached); return; }
        worker.execute(() -> {
            Request request = new Request.Builder().url("https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v=" + id).build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;
                String title = new JSONObject(response.body().string()).optString("title", "");
                if (title.isEmpty()) return;
                cache.put(id, title);
                done.accept(title);
            } catch (Exception ignored) { }
        });
    }

    private VideoTitles() {}
}
