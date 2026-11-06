package com.sya.tube.usage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class UsagePolicy {
    static final long HOUR = 3_600_000L, DAY = 86_400_000L;
    static final Set<String> CATEGORIES = Set.of("handoff_failed", "native_playback_failed", "web_stall", "update_check_failed", "update_download_failed");
    boolean enabled = true;
    String installId = UUID.randomUUID().toString();
    long lastAttempt = -1, successDay = -1, snapshotDay = -1, failureDay = -1;
    int failures;
    String successSignature = "";
    boolean projection;
    final Map<String, Integer> errors = new LinkedHashMap<>();

    void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        installId = value ? UUID.randomUUID().toString() : null;
        errors.clear(); projection = false; snapshotDay = -1;
        successDay = -1; successSignature = ""; failures = 0; failureDay = -1;
    }
    boolean due(long now, String signature) {
        rollDay(now);
        return enabled && (lastAttempt < 0 || now - lastAttempt >= HOUR)
                && failures < 3 && (successDay != day(now) || !successSignature.equals(signature));
    }
    void attempt(long now) { rollDay(now); lastAttempt = now; }
    void success(long now, String signature) { successDay = day(now); successSignature = signature; failures = 0; }
    void failure(long now) { rollDay(now); failures = Math.min(3, failures + 1); }
    void error(String category, long now) {
        if (!enabled || category == null || !CATEGORIES.contains(category)) return;
        rollDay(now); errors.put(category, Math.min(10000, errors.getOrDefault(category, 0) + 1));
    }
    void projection(boolean value, long now) { if (enabled) { rollDay(now); projection |= value; } }
    void rollDay(long now) {
        long day = day(now);
        if (day != snapshotDay) { errors.clear(); projection = false; snapshotDay = day; }
        if (day != failureDay) { failures = 0; failureDay = day; }
    }
    static String optional(String value) {
        String clean = metadata(value, 80);
        if ("Unknown".equals(clean) || java.util.regex.Pattern.compile("(?i)\\b[A-HJ-NPR-Z0-9]{17}\\b").matcher(clean).find()) return "";
        return clean;
    }
    boolean acceptsCompletion(String id) { return enabled && installId != null && installId.equals(id); }
    long nextDelay(long now, String signature) {
        long hourly = Math.max(1000, lastAttempt + HOUR - now);
        long tomorrow = (day(now) + 1) * DAY - now;
        if (failures >= 3) return Math.max(hourly, tomorrow);
        if (hourly > 1000) return hourly;
        if (successDay == day(now) && successSignature.equals(signature)) return Math.max(hourly, tomorrow);
        return hourly;
    }
    static long day(long now) { return Math.floorDiv(now, DAY); }
    static String metadata(String value, int max) {
        if (value == null || value.isBlank() || !value.matches("[\\p{L}\\p{N} ._()+-]+")) return "Unknown";
        String clean = value.trim();
        return clean.substring(0, Math.min(max, clean.length()));
    }
}
