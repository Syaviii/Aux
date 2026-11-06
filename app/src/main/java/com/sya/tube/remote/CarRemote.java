package com.sya.tube.remote;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/**
 * The phone screen and the car screen run in the same process, so the phone drives the car directly through this,
 * everything here runs on the main thread
 */
public final class CarRemote {
    public interface Car {
        void remotePlay(String url);
        void remoteToggle();
        void remoteSeek(long deltaMs);
        void remoteSeekTo(long positionMs);
        void remoteSkipAd();
        void remoteSkip(boolean next);
        void remoteQuality(String value);
        void remoteFullscreen();
        void remoteState(Consumer<State> out);
        /** The id of the video the car shows, or null when it is not on a video */
        String remoteVideoId();
        /** Play or pause, used by the steering wheel buttons and the phone's media controls */
        void remoteSetPlaying(boolean playing);
        void remoteChapter(boolean next);
        /** The current video's chapters, empty when it has none or they are not known yet */
        java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> remoteChapters();
        /** Playback speed for this video and the ones after it */
        void remoteSpeed(float speed);
        /** A phone video or media server video picked on the phone's library page */
        void remotePlayLink(String link);
    }

    /** The video id in a watch, shorts or youtu.be address, or null */
    public static String videoId(String url) {
        String playable = playableUrl(url);
        if (playable == null) return null;
        return android.net.Uri.parse(playable).getQueryParameter("v");
    }

    public static final class State {
        public final String title, quality;
        public final boolean playing, fullscreen, ad;
        public final long positionMs, durationMs;
        public final float speed;

        public State(String title, boolean playing, long positionMs, long durationMs, String quality, boolean fullscreen, boolean ad, float speed) {
            this.title = title; this.playing = playing; this.positionMs = positionMs; this.durationMs = durationMs; this.quality = quality; this.fullscreen = fullscreen; this.ad = ad; this.speed = speed;
        }
    }

    private static WeakReference<Car> car = new WeakReference<>(null);

    public static void attach(Car value) { car = new WeakReference<>(value); }

    public static void detach(Car value) { if (car.get() == value) car.clear(); }

    /** The car screen while Android Auto shows Tube, otherwise null */
    public static Car car() { return car.get(); }

    /** A watch or shorts address the car can play, or null for anything else */
    public static String playableUrl(String url) {
        if (url == null) return null;
        android.net.Uri uri = android.net.Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be"))) return null;
        String path = uri.getPath() == null ? "" : uri.getPath();
        String id = "/watch".equals(path) ? uri.getQueryParameter("v") : host.equals("youtu.be") && path.length() > 1 ? path.substring(1) : null;
        if (id == null || id.isEmpty()) return null;
        android.net.Uri.Builder out = new android.net.Uri.Builder().scheme("https").authority("m.youtube.com").path("/watch").appendQueryParameter("v", id);
        String list = uri.getQueryParameter("list");
        if (list != null) out.appendQueryParameter("list", list);
        String start = uri.getQueryParameter("t");
        if (start != null) out.appendQueryParameter("t", start);
        return out.build().toString();
    }

    private CarRemote() {}
}
