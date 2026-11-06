package com.sya.tube.player;

public final class HandoffPosition {
    private long lastWebPositionMs;
    private boolean movedBack;
    public HandoffPosition(long positionMs) { lastWebPositionMs = Math.max(0, positionMs); }
    public long prepare(double seconds, long leadMs) {
        remember(seconds);
        return lastWebPositionMs + Math.max(0, leadMs);
    }
    public long target(double seconds, long preparedMs) {
        remember(seconds);
        return lastWebPositionMs;
    }
    public boolean waitingForWeb(long preparedMs, boolean stalled) {
        return !stalled && !movedBack && lastWebPositionMs < preparedMs - 40;
    }
    public boolean movedBack() { return movedBack; }
    private void remember(double seconds) {
        if (Double.isFinite(seconds) && seconds >= 0 && seconds < Long.MAX_VALUE / 1000.0) {
            long position = (long) (seconds * 1000);
            if (position < lastWebPositionMs - 250) movedBack = true;
            lastWebPositionMs = position;
        }
    }
}
