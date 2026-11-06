package com.sya.tube.usage;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import com.sya.tube.settings.TubePrefs;
import com.thekirankumar.youtubeauto2.BuildConfig;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

public final class Usage {
    public static final String ENABLED = "pref_usage_reports";
    private static Usage instance;
    private final Context app;
    private final SharedPreferences state, settings;
    private final UsagePolicy policy = new UsagePolicy();
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).writeTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private ScheduledFuture<?> pending;
    private Call active;
    private int foreground;
    private boolean projected;

    public static synchronized void init(Application application) {
        if (instance == null) instance = new Usage(application);
    }
    private static synchronized Usage get(Context context) {
        if (instance == null) instance = new Usage(context.getApplicationContext());
        return instance;
    }
    public static boolean enabled(Context context) { return TubePrefs.get(context).getBoolean(ENABLED, true); }
    public static void error(Context context, String category) { get(context).recordError(category); }
    public static void projection(Context context, boolean connected) { get(context).setProjection(connected); }
    public static String vehicle(Context context) { return get(context).state.getString("vehicle", ""); }
    public static String unit(Context context) { return get(context).state.getString("unit", ""); }
    public static void setVehicle(Context context, String vehicle, String unit) { get(context).saveVehicle(vehicle, unit); }

    private Usage(Context context) {
        app = context;
        state = app.getSharedPreferences("aux_usage", Context.MODE_PRIVATE);
        settings = TubePrefs.get(app);
        policy.installId = state.getString("install_id", policy.installId);
        try { policy.installId = UUID.fromString(policy.installId).toString(); }
        catch (RuntimeException invalid) { policy.installId = UUID.randomUUID().toString(); }
        policy.lastAttempt = state.getLong("last_attempt", -1);
        policy.successDay = state.getLong("success_day", -1);
        policy.successSignature = state.getString("success_signature", "");
        policy.snapshotDay = state.getLong("snapshot_day", -1);
        policy.failureDay = state.getLong("failure_day", -1);
        policy.failures = Math.max(0, Math.min(3, state.getInt("failures", 0)));
        policy.projection = state.getBoolean("projection", false);
        for (String category : UsagePolicy.CATEGORIES) {
            int count = Math.max(0, Math.min(10000, state.getInt(category, 0)));
            if (count > 0) policy.errors.put(category, count);
        }
        policy.setEnabled(enabled(app));
        worker.setRemoveOnCancelPolicy(true);
        preferenceListener = (prefs, key) -> { if (ENABLED.equals(key)) refreshEnabled(); };
        settings.registerOnSharedPreferenceChangeListener(preferenceListener);
        if (app instanceof Application) ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityStarted(Activity activity) { visibility(true); }
            public void onActivityStopped(Activity activity) { visibility(false); }
            public void onActivityCreated(Activity activity, Bundle saved) {}
            public void onActivityResumed(Activity activity) {}
            public void onActivityPaused(Activity activity) {}
            public void onActivitySaveInstanceState(Activity activity, Bundle saved) {}
            public void onActivityDestroyed(Activity activity) {}
        });
        persist();
    }
    private synchronized void refreshEnabled() {
        policy.setEnabled(enabled(app));
        if (!policy.enabled) {
            if (pending != null) pending.cancel(false);
            pending = null;
            if (active != null) active.cancel();
        } else policy.projection(projected, System.currentTimeMillis());
        persist();
        schedule(0);
    }
    private synchronized void visibility(boolean visible) {
        foreground = Math.max(0, foreground + (visible ? 1 : -1));
        schedule(0);
    }
    private synchronized void setProjection(boolean connected) {
        projected = connected;
        policy.projection(connected, System.currentTimeMillis());
        persist(); schedule(0);
    }
    private synchronized void recordError(String category) {
        if (category == null || !UsagePolicy.CATEGORIES.contains(category) || !policy.enabled) return;
        policy.error(category, System.currentTimeMillis());
        persist(); schedule(0);
    }
    private synchronized void saveVehicle(String vehicle, String unit) {
        state.edit().putString("vehicle", UsagePolicy.optional(vehicle)).putString("unit", UsagePolicy.optional(unit)).apply();
        schedule(0);
    }
    private boolean canWork() { return policy.enabled && !BuildConfig.USAGE_ENDPOINT.isEmpty() && (foreground > 0 || projected); }
    private synchronized void schedule(long delay) {
        if (pending != null) pending.cancel(false);
        pending = null;
        if (canWork() && active == null) pending = worker.schedule(this::report, delay, TimeUnit.MILLISECONDS);
    }
    private JSONObject snapshot() throws Exception {
        JSONObject json = new JSONObject().put("schema", 1).put("install_id", policy.installId)
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("android_version", UsagePolicy.metadata(Build.VERSION.RELEASE, 32))
                .put("manufacturer", UsagePolicy.metadata(Build.MANUFACTURER, 80))
                .put("model", UsagePolicy.metadata(Build.MODEL, 80))
                .put("projection", policy.projection).put("errors", new JSONObject(policy.errors))
                .put("test", BuildConfig.DEBUG || emulator());
        String vehicle = UsagePolicy.optional(state.getString("vehicle", "")), unit = UsagePolicy.optional(state.getString("unit", ""));
        if (!vehicle.isEmpty()) json.put("vehicle", vehicle);
        if (!unit.isEmpty()) json.put("unit", unit);
        return json;
    }
    private static boolean emulator() {
        String fingerprint = Build.FINGERPRINT.toLowerCase(Locale.ROOT), model = Build.MODEL.toLowerCase(Locale.ROOT);
        return fingerprint.startsWith("generic") || fingerprint.contains("emulator") || fingerprint.contains("sdk_gphone")
                || model.contains("emulator") || model.contains("sdk_gphone") || model.contains("google_sdk")
                || Build.HARDWARE.equals("goldfish") || Build.HARDWARE.equals("ranchu");
    }
    private void report() {
        final Call call;
        final String signature, id;
        final long now = System.currentTimeMillis();
        synchronized (this) {
            pending = null;
            if (!canWork() || active != null) return;
            policy.rollDay(now); policy.projection(projected, now);
            try {
                String body = snapshot().toString();
                signature = body;
                if (!policy.due(now, signature)) { schedule(policy.nextDelay(now, signature)); return; }
                Request request = new Request.Builder().url(BuildConfig.USAGE_ENDPOINT)
                        .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8"))).build();
                call = http.newCall(request);
                active = call; id = policy.installId;
                policy.attempt(now);
                persist();
            } catch (Exception invalidConfiguration) { schedule(UsagePolicy.HOUR); return; }
        }
        boolean accepted = false;
        try (Response response = call.execute()) { accepted = response.code() == 202 || response.code() == 204; }
        catch (Exception ignored) { /* The next bounded attempt uses the latest snapshot. */ }
        synchronized (this) {
            active = null;
            if (policy.acceptsCompletion(id) && !call.isCanceled()) {
                if (accepted) policy.success(now, signature); else policy.failure(System.currentTimeMillis());
                persist();
            }
            schedule(policy.nextDelay(System.currentTimeMillis(), null));
        }
    }
    private void persist() {
        SharedPreferences.Editor edit = state.edit().putLong("last_attempt", policy.lastAttempt)
                .putLong("success_day", policy.successDay).putString("success_signature", policy.successSignature)
                .putLong("snapshot_day", policy.snapshotDay).putLong("failure_day", policy.failureDay)
                .putInt("failures", policy.failures).putBoolean("projection", policy.projection);
        if (policy.installId == null) edit.remove("install_id"); else edit.putString("install_id", policy.installId);
        for (String category : UsagePolicy.CATEGORIES) {
            if (policy.errors.containsKey(category)) edit.putInt(category, policy.errors.get(category)); else edit.remove(category);
        }
        edit.apply();
    }
}
