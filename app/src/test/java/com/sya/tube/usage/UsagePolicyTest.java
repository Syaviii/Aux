package com.sya.tube.usage;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class UsagePolicyTest {
    private static final long DAY = 86_400_000L, HOUR = 3_600_000L, NOW = 100 * DAY;

    @Test public void startsEnabledWithRandomInstallationId() {
        UsagePolicy p = new UsagePolicy();
        assertTrue(p.enabled);
        assertEquals(p.installId, java.util.UUID.fromString(p.installId).toString());
    }
    @Test public void optOutClearsIdentityAndErrorsAndOptInCreatesNewId() {
        UsagePolicy p = new UsagePolicy();
        String id = p.installId;
        p.error("web_stall", NOW);
        p.setEnabled(false);
        assertNull(p.installId);
        assertTrue(p.errors.isEmpty());
        assertFalse(p.due(NOW, "phone"));
        p.error("web_stall", NOW);
        assertTrue(p.errors.isEmpty());
        p.setEnabled(true);
        assertNotEquals(id, p.installId);
    }
    @Test public void dailySnapshotUsesUtcDayAndDoesNotRepeatUnchanged() {
        UsagePolicy p = new UsagePolicy();
        assertTrue(p.due(NOW, "phone"));
        p.attempt(NOW); p.success(NOW, "phone");
        assertFalse(p.due(NOW + HOUR, "phone"));
        assertTrue(p.due(NOW + DAY, "phone"));
    }
    @Test public void changesAndMidnightStillRespectHourlyFloor() {
        UsagePolicy p = new UsagePolicy();
        long late = NOW + DAY - 1000;
        p.attempt(late); p.success(late, "old");
        assertFalse(p.due(late + 1000, "new"));
        assertTrue(p.due(late + HOUR, "new"));
    }
    @Test public void retriesStopAfterThreeFailuresInOneUtcDay() {
        UsagePolicy p = new UsagePolicy();
        for (int i = 0; i < 3; i++) {
            assertTrue(p.due(NOW + i * HOUR, "phone"));
            p.attempt(NOW + i * HOUR); p.failure(NOW + i * HOUR);
        }
        assertFalse(p.due(NOW + 3 * HOUR, "phone"));
        assertTrue(p.due(NOW + DAY, "phone"));
    }
    @Test public void failedAttemptDoesNotRetryImmediatelyOrOnClockRollback() {
        UsagePolicy p = new UsagePolicy();
        p.attempt(NOW); p.failure(NOW);
        assertFalse(p.due(NOW + HOUR - 1, "phone"));
        assertFalse(p.due(NOW - HOUR, "phone"));
        assertTrue(p.due(NOW + HOUR, "phone"));
    }
    @Test public void onlyKnownErrorCategoriesAreCountedAndCountsAreBounded() {
        UsagePolicy p = new UsagePolicy();
        p.error("https://youtube.com/watch?v=private", NOW);
        p.error("title: private", NOW);
        for (int i = 0; i < 10001; i++) p.error("web_stall", NOW);
        assertEquals(Map.of("web_stall", 10000), p.errors);
    }
    @Test public void projectionAndErrorsAreDailyAndProjectionStaysTrueAfterDisconnect() {
        UsagePolicy p = new UsagePolicy();
        p.projection(true, NOW); p.projection(false, NOW);
        p.error("handoff_failed", NOW);
        assertTrue(p.projection);
        p.rollDay(NOW + DAY);
        assertFalse(p.projection);
        assertTrue(p.errors.isEmpty());
    }
    @Test public void metadataRejectsUrlsAccountsAndControlsAndBoundsLength() {
        assertEquals("Unknown", UsagePolicy.metadata("https://host/private", 80));
        assertEquals("Unknown", UsagePolicy.metadata("name@example.com", 80));
        assertEquals("Unknown", UsagePolicy.metadata("car\nsecret", 80));
        assertEquals("Pixel 9 Pro", UsagePolicy.metadata("Pixel 9 Pro", 80));
        assertEquals("Unknown", UsagePolicy.metadata("Kia, 2023", 80));
        assertEquals("Unknown", UsagePolicy.metadata("Owner's car", 80));
        assertEquals(80, UsagePolicy.metadata("a".repeat(90), 80).length());
    }
    @Test public void unchangedSnapshotSleepsUntilNextDayInsteadOfPolling() {
        UsagePolicy p = new UsagePolicy();
        p.attempt(NOW); p.success(NOW, "phone");
        assertEquals(HOUR, p.nextDelay(NOW, "phone"));
        assertEquals(DAY - HOUR, p.nextDelay(NOW + HOUR, "phone"));
        assertEquals(1000, p.nextDelay(NOW + HOUR, "changed"));
        assertEquals(1000, p.nextDelay(NOW + HOUR, null));
    }
    @Test public void optionalVehicleDetailsRejectVinAndSensitiveText() {
        assertEquals("", UsagePolicy.optional("VIN 1HGCM82633A004352"));
        assertEquals("", UsagePolicy.optional("https://host/private"));
        assertEquals("", UsagePolicy.optional("person@example.com"));
        assertEquals("", UsagePolicy.optional(""));
        assertEquals("Kia Sportage 2023", UsagePolicy.optional("Kia Sportage 2023"));
    }
    @Test public void cancelledIdentityCannotAcceptACompletion() {
        UsagePolicy p = new UsagePolicy();
        String id = p.installId;
        assertTrue(p.acceptsCompletion(id));
        p.setEnabled(false);
        assertFalse(p.acceptsCompletion(id));
        p.setEnabled(true);
        assertFalse(p.acceptsCompletion(id));
        assertTrue(p.acceptsCompletion(p.installId));
    }
    @Test public void optOutAndOptInCannotBypassHourlyLimit() {
        UsagePolicy p = new UsagePolicy();
        p.attempt(NOW); p.setEnabled(false); p.setEnabled(true);
        assertFalse(p.due(NOW + 1, "phone"));
    }
}
