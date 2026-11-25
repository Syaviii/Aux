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
        /** The key of the video the car shows (see Media), or null when it is not on a video */
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
        /** A place picked on the phone's start page, the car goes there too */
        void remoteOpen(String url);
    }

    /** The key of the video an address shows (see Media), or null */
    public static String videoId(String url) { return Media.key(url); }

    public static final class State {
        public final String title, quality;
        public final boolean playing, fullscreen, ad;
        /** Audio sync applies, only Aux's own player (YouTube handoff, phone and server videos) has it */
        public final boolean syncable;
        public final long positionMs, durationMs;
        public final float speed;
        /** A live stream, it has no length to seek in and plays at the broadcast's own pace */
        public final boolean live;

        public State(String title, boolean playing, long positionMs, long durationMs, String quality, boolean fullscreen, boolean ad, float speed, boolean syncable) {
            this(title, playing, positionMs, durationMs, quality, fullscreen, ad, speed, syncable, false);
        }

        public State(String title, boolean playing, long positionMs, long durationMs, String quality, boolean fullscreen, boolean ad, float speed, boolean syncable, boolean live) {
            this.syncable = syncable; this.live = live;
            this.title = title; this.playing = playing; this.positionMs = positionMs; this.durationMs = durationMs; this.quality = quality; this.fullscreen = fullscreen; this.ad = ad; this.speed = speed;
        }
    }

    private static WeakReference<Car> car = new WeakReference<>(null);

    public static void attach(Car value) { car = new WeakReference<>(value); }

    public static void detach(Car value) { if (car.get() == value) car.clear(); }

    /** The car screen while Android Auto shows Tube, otherwise null */
    public static Car car() { return car.get(); }

    /** A YouTube, Patreon or Twitch address the car can play, or null for anything else */
    public static String playableUrl(String url) { return Media.playableUrl(url); }

    private CarRemote() {}
}
