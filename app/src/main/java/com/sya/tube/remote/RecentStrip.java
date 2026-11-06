package com.sya.tube.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.thekirankumar.youtubeauto2.R;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** A "Continue watching" row of recent videos, shown above YouTube's home feed on the car and on the phone */
public final class RecentStrip {
    private static final OkHttpClient http = new OkHttpClient();
    private static final ExecutorService loader = Executors.newFixedThreadPool(2, r -> new Thread(r, "TubeThumbs"));
    private static final LruCache<String, Bitmap> thumbs = new LruCache<>(24);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private final LinearLayout root, row;
    private final Context context;
    private final Consumer<RecentVideos.Item> open;
    private String shownKey;
    // Short car screens like 800x480 get smaller cards with one line of title, otherwise the row would leave the feed a sliver
    private final boolean compact;

    public RecentStrip(LinearLayout container, Consumer<RecentVideos.Item> open) {
        root = container;
        context = container.getContext();
        this.open = open;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        compact = metrics.heightPixels / metrics.density < 520;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(compact ? 4 : 6), 0, dp(compact ? 4 : 8));
        TextView label = new TextView(context);
        label.setText("Continue watching");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 12 : 14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(context.getColor(R.color.tube_on_surface));
        label.setPadding(dp(2), 0, 0, dp(compact ? 3 : 6));
        root.addView(label);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(context);
        scroll.addView(row);
        root.addView(scroll);
    }

    /** Shows the row on YouTube's home feed when there is something to continue, hides it everywhere else */
    public void update(boolean onHome) {
        // Off by default, the row is tall enough on a car screen to crowd out the feed under it
        boolean wanted = onHome && com.sya.tube.settings.TubePrefs.get(context).getBoolean(com.sya.tube.settings.TubePrefs.CONTINUE_ROW, false);
        List<RecentVideos.Item> items = wanted ? RecentVideos.list(context) : java.util.Collections.emptyList();
        if (items.isEmpty()) { root.setVisibility(View.GONE); shownKey = null; return; }
        StringBuilder key = new StringBuilder();
        for (RecentVideos.Item item : items) key.append(item.id).append(item.positionMs / 30_000).append(',');
        root.setVisibility(View.VISIBLE);
        if (key.toString().equals(shownKey)) return;
        shownKey = key.toString();
        row.removeAllViews();
        for (RecentVideos.Item item : items) row.addView(card(item));
    }

    private View card(RecentVideos.Item item) {
        int width = dp(compact ? 104 : 144), height = dp(compact ? 58 : 81);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.highlighting_drawable);
        card.setFocusable(true);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setContentDescription("Continue " + item.title);
        card.setOnClickListener(v -> open.accept(item));
        FrameLayout frame = new FrameLayout(context);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setColor(context.getColor(R.color.tube_surface_high));
        placeholder.setCornerRadius(dp(8));
        frame.setBackground(placeholder);
        frame.setClipToOutline(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(width, height));
        View track = new View(context);
        track.setBackgroundColor(Color.argb(110, 255, 255, 255));
        frame.addView(track, new FrameLayout.LayoutParams(width, dp(3), android.view.Gravity.BOTTOM));
        View played = new View(context);
        played.setBackgroundColor(context.getColor(R.color.tube_accent));
        frame.addView(played, new FrameLayout.LayoutParams(Math.max(dp(4), (int) (width * item.progress())), dp(3), android.view.Gravity.BOTTOM));
        card.addView(frame, new LinearLayout.LayoutParams(width, height));
        TextView title = new TextView(context);
        title.setText(item.title.isEmpty() ? "Video" : item.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 11 : 12);
        title.setTextColor(context.getColor(R.color.tube_on_surface));
        title.setMaxLines(compact ? 1 : 2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(width, -2));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
        place.setMarginEnd(dp(6));
        card.setLayoutParams(place);
        thumbnail(item.id, image);
        return card;
    }

    private void thumbnail(String id, ImageView into) {
        into.setTag(id);
        Bitmap cached = thumbs.get(id);
        if (cached != null) { into.setImageBitmap(cached); return; }
        loader.execute(() -> {
            Request request = new Request.Builder().url("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg").build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;
                Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                if (bitmap == null) return;
                thumbs.put(id, bitmap);
                main.post(() -> { if (id.equals(into.getTag())) into.setImageBitmap(bitmap); });
            } catch (Exception ignored) { }
        });
    }

    /** True for YouTube's home feed, the only page the row is shown on */
    public static boolean isHome(String url) {
        if (url == null) return false;
        android.net.Uri uri = android.net.Uri.parse(url);
        String host = uri.getHost(), path = uri.getPath();
        return host != null && host.endsWith("youtube.com") && (path == null || path.isEmpty() || path.equals("/"));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
