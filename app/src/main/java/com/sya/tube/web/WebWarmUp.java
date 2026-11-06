package com.sya.tube.web;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.OptIn;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewStartUpConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Starts WebView work at process start, while Android Auto is still bringing up the car screen.
 * Each step runs only when the installed WebView supports it, older WebViews simply skip it
 */
public final class WebWarmUp {
    private static final String[] ORIGINS = {"https://m.youtube.com", "https://www.youtube.com", "https://i.ytimg.com", "https://yt3.ggpht.com"};
    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(r -> new Thread(r, "TubeWebWarmUp"));
    private static boolean started;

    public static void start(Context context, String homeUrl) {
        if (started) return;
        started = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        try {
            // Loads Chromium off the main thread so the car activity's WebView constructor does not pay for it
            WebViewCompat.startUpWebView(app, new WebViewStartUpConfig.Builder(BACKGROUND).build(), new androidx.webkit.WebViewOutcomeReceiver<androidx.webkit.WebViewStartUpResult, androidx.webkit.WebViewStartupException>() {
                @Override public void onResult(androidx.webkit.WebViewStartUpResult result) { main.post(() -> warm(homeUrl)); }
                @Override public void onError(androidx.webkit.WebViewStartupException error) { main.post(() -> warm(homeUrl)); }
            });
        } catch (Throwable unsupported) {
            main.post(() -> warm(homeUrl));
        }
    }

    @OptIn(markerClass = {Profile.ExperimentalWarmUpRendererProcess.class, Profile.ExperimentalPreconnect.class, Profile.ExperimentalUrlPrefetch.class})
    private static void warm(String homeUrl) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return;
            Profile profile = ProfileStore.getInstance().getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) profile.warmUpRendererProcess();
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)) for (String origin : ORIGINS) profile.preconnect(origin);
            if (homeUrl != null && homeUrl.startsWith("https://") && WebViewFeature.isFeatureSupported(WebViewFeature.PROFILE_URL_PREFETCH)) {
                profile.prefetchUrlAsync(homeUrl, null, BACKGROUND, new androidx.webkit.WebViewOutcomeReceiver<Void, androidx.webkit.PrefetchException>() {
                    @Override public void onResult(Void ignored) {}
                    @Override public void onError(androidx.webkit.PrefetchException error) {}
                });
            }
            debug("warm renderer=" + WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS) + " preconnect=" + WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT) + " prefetch=" + WebViewFeature.isFeatureSupported(WebViewFeature.PROFILE_URL_PREFETCH));
        } catch (Throwable error) {
            debug("warm failed " + error.getClass().getSimpleName());
        }
    }

    private static void debug(String message) {
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics", "stage " + message.replace(' ', '_') + " elapsed=" + android.os.SystemClock.elapsedRealtime());
    }

    private WebWarmUp() {}
}
