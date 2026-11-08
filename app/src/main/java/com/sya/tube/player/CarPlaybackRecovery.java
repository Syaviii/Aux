package com.sya.tube.player;

/** Position and user intent carried across a car playback reload. */
public final class CarPlaybackRecovery {
    public enum Feedback { NONE, BUFFERING, OFFLINE }

    public static final class Snapshot {
        public final String videoId;
        public final long positionMs;
        public final boolean playing, fullscreen;
        private Snapshot(String videoId, long positionMs, boolean playing, boolean fullscreen) {
            this.videoId = videoId;
            this.positionMs = Math.max(0, positionMs);
            this.playing = playing;
            this.fullscreen = fullscreen;
        }
    }

    private Snapshot pending;

    public void begin(String videoId, long positionMs, boolean playing, boolean fullscreen) {
        pending = videoId == null ? null : new Snapshot(videoId, positionMs, playing, fullscreen);
    }
    public Snapshot pending(String videoId) {
        return pending != null && pending.videoId.equals(videoId) ? pending : null;
    }
    public Snapshot consume(String videoId) {
        Snapshot result = pending(videoId);
        if (result != null) pending = null;
        return result;
    }
    public void navigate(String videoId) {
        if (pending(videoId) == null) pending = null;
    }
    public void setPlaying(String videoId, boolean playing) {
        Snapshot value = pending(videoId);
        if (value != null) begin(videoId, value.positionMs, playing, value.fullscreen);
    }
    public void setFullscreen(String videoId, boolean fullscreen) {
        Snapshot value = pending(videoId);
        if (value != null) begin(videoId, value.positionMs, value.playing, fullscreen);
    }
    public void seek(String videoId, long positionMs) {
        Snapshot value = pending(videoId);
        if (value != null) begin(videoId, positionMs, value.playing, value.fullscreen);
    }
    public static Feedback feedback(boolean online, boolean playWhenReady, boolean buffering, boolean stalled) {
        if (!playWhenReady || !buffering && !stalled) return Feedback.NONE;
        return online ? Feedback.BUFFERING : Feedback.OFFLINE;
    }
    public static long nativePosition(boolean hasMediaItem, long currentMs, long initialMs) {
        return Math.max(0, hasMediaItem ? currentMs : initialMs);
    }
}
