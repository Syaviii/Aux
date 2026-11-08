package com.sya.tube.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class CarPlaybackRecoveryTest {
    @Test public void pausedReconnectRestoresPositionAndPauseTogether() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 640_374, false, true);
        CarPlaybackRecovery.Snapshot resume = recovery.consume("video");
        assertEquals(640_374, resume.positionMs);
        assertFalse(resume.playing);
        assertTrue(resume.fullscreen);
        assertNull(recovery.consume("video"));
    }

    @Test public void pauseWhileOfflineOverridesTheEarlierPlayingRecovery() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 480_000, true, true);
        recovery.setPlaying("video", false);
        assertFalse(recovery.consume("video").playing);
    }

    @Test public void leavingFullscreenWhileOfflineOverridesRecoveryContext() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 480_000, true, true);
        recovery.setFullscreen("video", false);
        assertFalse(recovery.consume("video").fullscreen);
    }

    @Test public void navigationCannotRestoreAnEarlierVideo() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("old", 480_000, false, true);
        recovery.navigate("new");
        assertNull(recovery.consume("new"));
        assertNull(recovery.consume("old"));
    }

    @Test public void aBackwardSeekReplacesTheEarlierRecoveryPosition() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 480_000, true, true);
        recovery.seek("video", 90_000);
        assertEquals(90_000, recovery.consume("video").positionMs);
    }

    @Test public void nativeSeekThenNetworkErrorKeepsTheSeekInsteadOfInitialHandoffTime() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 40_000, false, true);
        recovery.seek("video", 479_000);
        recovery.setPlaying("video", true);
        long failedPosition = CarPlaybackRecovery.nativePosition(true, 479_000, 5_000);
        recovery.begin("video", failedPosition, true, true);
        CarPlaybackRecovery.Snapshot resume = recovery.consume("video");
        assertEquals(479_000, resume.positionMs);
        assertTrue(resume.playing);
        assertTrue(resume.fullscreen);
    }

    @Test public void anotherNativeSeekAfterFailureReplacesTheRecoveryTargetWhilePaused() {
        CarPlaybackRecovery recovery = new CarPlaybackRecovery();
        recovery.begin("video", 479_000, true, true);
        recovery.setPlaying("video", false);
        recovery.seek("video", 90_000);
        CarPlaybackRecovery.Snapshot resume = recovery.consume("video");
        assertEquals(90_000, resume.positionMs);
        assertFalse(resume.playing);
        assertTrue(resume.fullscreen);
    }

    @Test public void nativeErrorUsesCurrentPositionEvenWhenItIsABackwardSeekToZero() {
        assertEquals(0, CarPlaybackRecovery.nativePosition(true, 0, 300_000));
    }

    @Test public void nativePreparationWithoutAMediaItemRetainsTheInitialPosition() {
        assertEquals(300_000, CarPlaybackRecovery.nativePosition(false, 0, 300_000));
    }

    @Test public void offlineUnbufferedSeekShowsOfflineInsteadOfPlaying() {
        assertEquals(CarPlaybackRecovery.Feedback.OFFLINE,
                CarPlaybackRecovery.feedback(false, true, true, false));
    }

    @Test public void anOfflineFrozenClockShowsOfflineEvenWithoutAWaitingEvent() {
        assertEquals(CarPlaybackRecovery.Feedback.OFFLINE,
                CarPlaybackRecovery.feedback(false, true, false, true));
    }

    @Test public void bufferedOfflinePlaybackDoesNotClaimItStopped() {
        assertEquals(CarPlaybackRecovery.Feedback.NONE,
                CarPlaybackRecovery.feedback(false, true, false, false));
    }

    @Test public void pausedVideoDoesNotClaimItIsBuffering() {
        assertEquals(CarPlaybackRecovery.Feedback.NONE,
                CarPlaybackRecovery.feedback(false, false, true, true));
    }

    @Test public void onlineUnbufferedSeekShowsBuffering() {
        assertEquals(CarPlaybackRecovery.Feedback.BUFFERING,
                CarPlaybackRecovery.feedback(true, true, true, false));
    }
}
