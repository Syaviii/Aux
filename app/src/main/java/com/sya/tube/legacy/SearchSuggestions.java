package com.sya.tube.legacy;

import android.util.LruCache;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;

/** Reuse connections and discard responses from superseded or closed searches. */
public final class SearchSuggestions {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
    private final LruCache<String, List<SearchItem>> cache = new LruCache<>(32);
    private final AtomicLong generation = new AtomicLong();
    private volatile Call active;
    public interface Result { void accept(List<SearchItem> items); }
    public void cancel() {
        generation.incrementAndGet();
        Call previous = active;
        active = null;
        if (previous != null) previous.cancel();
    }
    public void load(String baseUrl, String query, Result result) {
        cancel();
        long id = generation.get();
        String key = baseUrl + query;
        List<SearchItem> cached = cache.get(key);
        if (cached != null) { result.accept(cached); return; }
        try {
            Call call = CLIENT.newCall(new Request.Builder().url(baseUrl + URLEncoder.encode(query, "UTF-8")).build());
            active = call;
            call.enqueue(new Callback() {
                @Override public void onFailure(Call failed, IOException error) {
                    if (id == generation.get()) result.accept(Collections.emptyList());
                }
                @Override public void onResponse(Call completed, Response response) {
                    List<SearchItem> items = new ArrayList<>();
                    try (Response closed = response) {
                        if (closed.isSuccessful() && closed.body() != null) {
                            JSONArray values = new JSONArray(closed.body().string()).getJSONArray(1);
                            for (int i = 0; i < Math.min(values.length(), 8); i++)
                                items.add(new SearchItem.Builder().setTitle(values.getString(i)).build());
                            cache.put(key, Collections.unmodifiableList(items));
                        }
                    } catch (IOException | JSONException error) { items.clear(); }
                    if (id == generation.get()) result.accept(items);
                }
            });
        } catch (IOException error) { result.accept(Collections.emptyList()); }
    }
}
