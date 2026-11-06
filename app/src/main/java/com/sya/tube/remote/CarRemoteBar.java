package com.sya.tube.remote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.sya.tube.player.SyncedRenderersFactory;
import com.thekirankumar.youtubeauto2.R;

/** Phone side controls for whatever plays on the car screen, shown only while Android Auto runs Tube */
public final class CarRemoteBar {
    private static final String[] QUALITIES = {"auto", "large", "hd720", "hd1080"};
    private static final String[] QUALITY_LABELS = {"Auto", "480p", "720p", "1080p"};
    private final LinearLayout root;
    private final Context context;
    private final int onSurface, muted, accent, surfaceHigh;
    private final TextView title, position, duration, sync;
    private final ImageButton play, fullscreen;
    private final TextView skipAd, speed;
    private float shownSpeed = 1f;
    private final android.widget.SeekBar seek;
    private boolean dragging;
    private final TextView[] qualities = new TextView[QUALITIES.length];
    private String shownQuality;
    private final Runnable tick = this::refresh;
    private final ChapterMarks marks;
    private final LinearLayout chapterRow, upNextRow;
    private final TextView chapter, upNext;
    private final Runnable upNextChanged = this::showUpNext;
    private Runnable onCarTick;

    /** Runs on every refresh while the car screen is up, the phone page uses it to follow what the car plays */
    public void setOnCarTick(Runnable value) { onCarTick = value; }

    public CarRemoteBar(LinearLayout container) {
        root = container;
        context = container.getContext();
        onSurface = context.getColor(R.color.tube_on_surface);
        muted = context.getColor(R.color.tube_muted);
        accent = context.getColor(R.color.tube_accent);
        surfaceHigh = context.getColor(R.color.tube_surface_high);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(context.getColor(R.color.tube_surface));
        float r = dp(20);
        card.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(card);
        root.setPadding(dp(16), dp(12), dp(12), dp(8));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(12, accent);
        label.setText("Playing on your car");
        heading.addView(label);
        title = text(15, onSurface);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(title);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        skipAd = pill("Skip ad", true, v -> car(CarRemote.Car::remoteSkipAd));
        skipAd.setVisibility(View.GONE);
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(-2, dp(32));
        skipParams.setMarginStart(dp(8));
        header.addView(skipAd, skipParams);
        // One tap moves to the next speed, after 2x it goes back to normal
        speed = pill("1x", false, v -> {
            float next = com.sya.tube.settings.TubePrefs.SPEEDS[0];
            float[] speeds = com.sya.tube.settings.TubePrefs.SPEEDS;
            for (int i = 0; i < speeds.length; i++) if (Math.abs(speeds[i] - shownSpeed) < 0.01f) next = speeds[(i + 1) % speeds.length];
            float chosen = next;
            showSpeed(chosen);
            car(c -> c.remoteSpeed(chosen));
        });
        speed.setBackground(ripple(surfaceHigh, dp(16)));
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(-2, dp(32));
        speedParams.setMarginStart(dp(8));
        header.addView(speed, speedParams);
        fullscreen = icon(R.drawable.tube_ic_fullscreen, "Fullscreen on the car", dp(40), dp(10), v -> car(CarRemote.Car::remoteFullscreen));
        header.addView(fullscreen);
        root.addView(header);

        seek = new android.widget.SeekBar(context);
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setThumbTintList(ColorStateList.valueOf(accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)));
        seek.setPadding(dp(4), 0, dp(4), 0);
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) { if (fromUser) position.setText(clock(progress)); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { dragging = false; car(c -> c.remoteSeekTo(bar.getProgress())); }
        });
        // Chapter starts are drawn as small marks over the seek bar, like YouTube's own
        android.widget.FrameLayout seekFrame = new android.widget.FrameLayout(context);
        seekFrame.addView(seek, new android.widget.FrameLayout.LayoutParams(-1, -1));
        marks = new ChapterMarks(context, dp(4), dp(4));
        seekFrame.addView(marks, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, dp(28));
        seekParams.topMargin = dp(6);
        root.addView(seekFrame, seekParams);
        LinearLayout times = new LinearLayout(context);
        times.setPadding(dp(4), 0, dp(4), 0);
        position = text(12, muted);
        times.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
        duration = text(12, muted);
        times.addView(duration);
        root.addView(times);

        LinearLayout transport = row();
        transport.addView(icon(R.drawable.ic_video_previous, "Previous video", dp(48), dp(12), v -> car(c -> c.remoteSkip(false))), weight());
        transport.addView(jump(R.drawable.tube_ic_replay, "Back 10 seconds", -10_000), weight());
        play = icon(R.drawable.ic_video_pause, "Play or pause", dp(56), dp(14), v -> car(CarRemote.Car::remoteToggle));
        play.setBackground(ripple(Color.WHITE, dp(28)));
        play.setImageTintList(ColorStateList.valueOf(Color.BLACK));
        transport.addView(play, new LinearLayout.LayoutParams(dp(56), dp(56)));
        transport.addView(jump(R.drawable.tube_ic_forward, "Forward 10 seconds", 10_000), weight());
        transport.addView(icon(R.drawable.ic_video_next, "Next video", dp(48), dp(12), v -> car(c -> c.remoteSkip(true))), weight());
        root.addView(transport);

        chapterRow = row();
        chapterRow.setVisibility(View.GONE);
        chapterRow.addView(icon(R.drawable.tube_ic_chapter_previous, "Previous chapter", dp(40), dp(10), v -> car(c -> c.remoteChapter(false))));
        chapter = text(13, onSurface);
        chapter.setGravity(Gravity.CENTER);
        chapter.setSingleLine(true);
        chapter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chapterRow.addView(chapter, new LinearLayout.LayoutParams(0, -2, 1));
        chapterRow.addView(icon(R.drawable.tube_ic_chapter_next, "Next chapter", dp(40), dp(10), v -> car(c -> c.remoteChapter(true))));
        root.addView(chapterRow);

        upNextRow = row();
        upNextRow.setVisibility(View.GONE);
        upNextRow.setPadding(dp(4), dp(2), 0, 0);
        upNext = text(13, muted);
        upNext.setSingleLine(true);
        upNext.setEllipsize(android.text.TextUtils.TruncateAt.END);
        upNextRow.addView(upNext, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = pill("Clear", false, v -> UpNext.clear(context));
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        upNextRow.addView(clear, new LinearLayout.LayoutParams(-2, dp(30)));
        root.addView(upNextRow);
        showUpNext();

        LinearLayout settings = row();
        settings.setPadding(0, dp(8), 0, dp(4));
        LinearLayout segment = new LinearLayout(context);
        segment.setBackground(rounded(surfaceHigh, dp(18)));
        segment.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < QUALITIES.length; i++) {
            String value = QUALITIES[i];
            qualities[i] = pill(QUALITY_LABELS[i], false, v -> { showQuality(value); car(c -> c.remoteQuality(value)); });
            segment.addView(qualities[i], new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        settings.addView(segment, new LinearLayout.LayoutParams(0, dp(36), 1));
        LinearLayout stepper = new LinearLayout(context);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setBackground(rounded(surfaceHigh, dp(18)));
        stepper.addView(step("−", "Audio earlier", -SyncedRenderersFactory.STEP_MS), new LinearLayout.LayoutParams(dp(34), dp(36)));
        sync = text(12, onSurface);
        sync.setGravity(Gravity.CENTER);
        sync.setMinWidth(dp(62));
        stepper.addView(sync);
        stepper.addView(step("+", "Audio later", SyncedRenderersFactory.STEP_MS), new LinearLayout.LayoutParams(dp(34), dp(36)));
        LinearLayout.LayoutParams stepperParams = new LinearLayout.LayoutParams(-2, dp(36));
        stepperParams.setMarginStart(dp(8));
        settings.addView(stepper, stepperParams);
        root.addView(settings);
        showSync(SyncedRenderersFactory.offsetMs(context));

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { UpNext.addListener(upNextChanged); showUpNext(); refresh(); }
            @Override public void onViewDetachedFromWindow(View view) { UpNext.removeListener(upNextChanged); root.removeCallbacks(tick); }
        });
    }

    private void refresh() {
        root.removeCallbacks(tick);
        CarRemote.Car car = CarRemote.car();
        root.setVisibility(car == null ? View.GONE : View.VISIBLE);
        if (car != null) { car.remoteState(this::show); if (onCarTick != null) onCarTick.run(); }
        root.postDelayed(tick, 1000);
    }

    private void show(CarRemote.State state) {
        String name = state.title == null || state.title.isEmpty() ? "Nothing playing yet" : state.title;
        if (!name.contentEquals(title.getText())) title.setText(name);
        if (!dragging) {
            position.setText(state.ad ? "Ad" : state.durationMs > 0 ? clock(state.positionMs) : "");
            duration.setText(!state.ad && state.durationMs > 0 ? clock(state.durationMs) : "");
            seek.setMax((int) Math.max(0, state.durationMs));
            seek.setProgress((int) Math.max(0, state.positionMs));
        }
        // Seeking during an ad would move the ad, not the video
        seek.setEnabled(state.durationMs > 0 && !state.ad);
        skipAd.setVisibility(state.ad ? View.VISIBLE : View.GONE);
        play.setImageResource(state.playing ? R.drawable.ic_video_pause : R.drawable.ic_video_play);
        fullscreen.setImageResource(state.fullscreen ? R.drawable.tube_ic_fullscreen_exit : R.drawable.tube_ic_fullscreen);
        showQuality(state.quality);
        if (Math.abs(state.speed - shownSpeed) > 0.01f) showSpeed(state.speed);
        showChapters(state.positionMs, state.durationMs);
    }

    private void showChapters(long positionMs, long durationMs) {
        CarRemote.Car car = CarRemote.car();
        java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> chapters = car == null ? java.util.Collections.emptyList() : car.remoteChapters();
        marks.show(chapters, durationMs);
        if (chapters.size() < 2) { chapterRow.setVisibility(View.GONE); return; }
        String current = chapters.get(0).title;
        for (var item : chapters) if (item.startMs <= positionMs) current = item.title;
        if (!current.contentEquals(chapter.getText())) chapter.setText(current);
        chapterRow.setVisibility(View.VISIBLE);
    }

    private void showUpNext() {
        java.util.List<UpNext.Item> queue = UpNext.list(context);
        upNextRow.setVisibility(queue.isEmpty() ? View.GONE : View.VISIBLE);
        if (queue.isEmpty()) return;
        String first = queue.get(0).title.isEmpty() ? "a video" : queue.get(0).title;
        upNext.setText("Up next: " + first + (queue.size() > 1 ? " and " + (queue.size() - 1) + " more" : ""));
    }

    /** Thin marks at each chapter start, laid over the seek bar between its padding */
    private static final class ChapterMarks extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        private final int left, right;
        private long[] starts = new long[0];
        private long duration;

        ChapterMarks(Context context, int left, int right) {
            super(context);
            this.left = left; this.right = right;
            paint.setColor(Color.argb(200, 255, 255, 255));
        }

        void show(java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> chapters, long durationMs) {
            long[] next = new long[Math.max(0, chapters.size() - 1)];
            for (int i = 1; i < chapters.size(); i++) next[i - 1] = chapters.get(i).startMs;
            if (durationMs == duration && java.util.Arrays.equals(next, starts)) return;
            starts = next; duration = durationMs;
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            if (duration <= 0 || starts.length == 0) return;
            float width = getWidth() - left - right, middle = getHeight() / 2f, half = getResources().getDisplayMetrics().density * 3;
            for (long start : starts) {
                float x = left + width * start / duration;
                canvas.drawRect(x - 1.5f, middle - half, x + 1.5f, middle + half, paint);
            }
        }
    }

    private void showQuality(String quality) {
        if (quality != null && quality.equals(shownQuality)) return;
        shownQuality = quality;
        for (int i = 0; i < QUALITIES.length; i++) {
            boolean on = QUALITIES[i].equals(quality);
            qualities[i].setBackground(on ? ripple(onSurface, dp(15)) : ripple(Color.TRANSPARENT, dp(15)));
            qualities[i].setTextColor(on ? Color.BLACK : onSurface);
        }
    }

    private void showSpeed(float value) {
        shownSpeed = value;
        speed.setText(com.sya.tube.settings.TubePrefs.speedText(value) + "x");
        speed.setContentDescription("Speed " + com.sya.tube.settings.TubePrefs.speedText(value));
    }

    private void showSync(int offsetMs) {
        sync.setText(offsetMs == 0 ? "Sync 0" : "Sync " + (offsetMs > 0 ? "+" : "") + offsetMs);
        sync.setContentDescription("Audio sync " + offsetMs + " milliseconds");
    }

    private void car(java.util.function.Consumer<CarRemote.Car> action) {
        CarRemote.Car car = CarRemote.car();
        if (car != null) { action.accept(car); root.postDelayed(this::refresh, 300); }
    }

    private static String clock(long ms) {
        long seconds = ms / 1000;
        return seconds >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) : String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(48), 1); }

    private TextView text(int sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private TextView pill(String label, boolean filled, View.OnClickListener click) {
        TextView pill = text(13, filled ? Color.BLACK : onSurface);
        pill.setText(label);
        pill.setGravity(Gravity.CENTER);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setPadding(dp(12), 0, dp(12), 0);
        pill.setBackground(ripple(filled ? onSurface : Color.TRANSPARENT, dp(16)));
        pill.setOnClickListener(click);
        return pill;
    }

    private TextView step(String label, String description, int delta) {
        TextView step = text(18, onSurface);
        step.setText(label);
        step.setGravity(Gravity.CENTER);
        step.setContentDescription(description);
        step.setBackground(ripple(Color.TRANSPARENT, dp(18)));
        step.setOnClickListener(v -> showSync(SyncedRenderersFactory.adjust(context, delta)));
        return step;
    }

    /** A circular arrow with the jump length written in its middle, like the player icons people already know */
    private View jump(int drawable, String description, long deltaMs) {
        FrameLayout frame = new FrameLayout(context);
        frame.setContentDescription(description);
        frame.setBackground(ripple(Color.TRANSPARENT, dp(24)));
        frame.setOnClickListener(v -> car(c -> c.remoteSeek(deltaMs)));
        ImageView arrow = new ImageView(context);
        arrow.setImageResource(drawable);
        arrow.setImageTintList(ColorStateList.valueOf(onSurface));
        frame.addView(arrow, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        TextView seconds = text(9, onSurface);
        seconds.setText("10");
        seconds.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams secondsParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        secondsParams.topMargin = dp(2);
        frame.addView(seconds, secondsParams);
        return frame;
    }

    private ImageButton icon(int drawable, String description, int size, int padding, View.OnClickListener click) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(padding, padding, padding, padding);
        button.setContentDescription(description);
        button.setBackground(ripple(Color.TRANSPARENT, size / 2));
        button.setImageTintList(ColorStateList.valueOf(onSurface));
        button.setOnClickListener(click);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
    }

    private Drawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), color == Color.TRANSPARENT ? null : rounded(color, radius), rounded(Color.WHITE, radius));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
