package com.sya.tube.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class HandoffPositionTest {
    @Test public void preparationLeadDoesNotBecomePlaybackPositionWhenClockDisappears() {
        HandoffPosition position = new HandoffPosition(180_000);
        assertEquals(197_000, position.prepare(185, 12_000));
        assertEquals(185_000, position.target(Double.NaN, 197_000));
    }
    @Test public void stalledVideoContinuesAtTheLastWatchedFrame() {
        HandoffPosition position = new HandoffPosition(180_000);
        assertEquals(180_000, position.target(180, 192_000));
    }
    @Test public void backwardSeekDuringPreparationIsRespected() {
        HandoffPosition position = new HandoffPosition(300_000);
        position.prepare(305, 8_000);
        assertEquals(90_000, position.target(90, 313_000));
    }
    @Test public void movingVideoUsesItsActualCurrentPositionWithoutSkippingAhead() {
        HandoffPosition position = new HandoffPosition(180_000);
        assertEquals(195_000, position.target(195, 192_000));
    }
    @Test public void invalidClocksRetainTheLastConfirmedPosition() {
        HandoffPosition position = new HandoffPosition(180_000);
        assertEquals(180_000, position.target(Double.POSITIVE_INFINITY, 192_000));
        assertEquals(180_000, position.target(-1, 192_000));
    }
    @Test public void backwardSeekDoesNotWaitForTheOldFutureFrame() {
        HandoffPosition position = new HandoffPosition(300_000);
        position.prepare(305, 8_000);
        position.target(90, 313_000);
        assertTrue(position.movedBack());
        assertFalse(position.waitingForWeb(313_000, false));
        position.target(91, 313_000);
        assertFalse(position.waitingForWeb(313_000, false));
    }
    @Test public void movingVideoStillWaitsForThePreparedFrame() {
        HandoffPosition position = new HandoffPosition(300_000);
        position.prepare(305, 8_000);
        position.target(307, 313_000);
        assertFalse(position.movedBack());
        assertTrue(position.waitingForWeb(313_000, false));
        assertFalse(position.waitingForWeb(313_000, true));
    }
}
