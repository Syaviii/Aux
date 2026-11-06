package com.sya.tube.update;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.InstallSourceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.thekirankumar.youtubeauto2.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * New versions come from the project's GitHub Releases. The release's Aux APK is downloaded and handed to the system
 * installer, Android itself checks that it is signed with the same key as the installed Aux before it replaces it
 */
public final class Updates {
    public static final class Release {
        public final String version, notes, page, apk;
        public final long size;
        Release(String version, String notes, String page, String apk, long size) { this.version = version; this.notes = notes; this.page = page; this.apk = apk; this.size = size; }
        public boolean newer() { return compare(version, BuildConfig.VERSION_NAME) > 0; }
    }

    private static final String PREFS = "updates";
    private static final long AUTO_EVERY_MS = TimeUnit.HOURS.toMillis(12);
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "AuxUpdates"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String PLAY = "com.android.vending";

    /** True when this build knows where its releases are published */
    public static boolean enabled() { return !BuildConfig.UPDATE_REPO.isEmpty(); }

    public static String repoPage() { return "https://github.com/" + BuildConfig.UPDATE_REPO; }

    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS, 0); }

    public static long lastChecked(Context context) { return prefs(context).getLong("checked", 0); }

    /** The newest release seen at the last check, or null */
    public static Release known(Context context) {
        SharedPreferences p = prefs(context);
        String version = p.getString("version", null);
        return version == null ? null : new Release(version, p.getString("notes", ""), p.getString("page", ""), p.getString("apk", ""), p.getLong("size", 0));
    }

    /** A version the driver chose to skip stays quiet until a newer one appears */
    public static void dismiss(Context context, String version) { prefs(context).edit().putString("dismissed", version).apply(); }

    public static boolean dismissed(Context context, Release release) { return release.version.equals(prefs(context).getString("dismissed", null)); }

    /** Checks at most every twelve hours on its own, a forced check always asks GitHub, null comes back when nothing is known */
    public static void check(Context context, boolean force, Consumer<Release> done) {
        Context app = context.getApplicationContext();
        if (!enabled()) { done.accept(null); return; }
        if (!force && System.currentTimeMillis() - lastChecked(app) < AUTO_EVERY_MS) { done.accept(known(app)); return; }
        WORKER.execute(() -> {
            Release found = null;
            try {
                Request request = new Request.Builder().url(BuildConfig.UPDATE_API + "/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest")
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "Aux/" + BuildConfig.VERSION_NAME).build();
                try (Response response = HTTP.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject release = new JSONObject(response.body().string());
                        String version = release.optString("tag_name").replaceFirst("^[vV]", "");
                        JSONArray assets = release.optJSONArray("assets");
                        String apk = null;
                        long size = 0;
                        if (assets != null) for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            // The app itself, not Aux Setup
                            if (asset.optString("name").matches("(?i)aux-[0-9][0-9.]*\\.apk")) { apk = asset.optString("browser_download_url"); size = asset.optLong("size"); }
                        }
                        if (ReleaseVersion.valid(version) && apk != null) {
                            found = new Release(version, release.optString("body"), release.optString("html_url"), apk, size);
                            prefs(app).edit().putString("version", found.version).putString("notes", found.notes).putString("page", found.page).putString("apk", found.apk).putLong("size", size).apply();
                        }
                    }
                }
                prefs(app).edit().putLong("checked", System.currentTimeMillis()).apply();
            } catch (Exception error) {
                com.sya.tube.diag.Diag.log("updates", "check failed " + error.getClass().getSimpleName());
            }
            Release result = found != null ? found : known(app);
            MAIN.post(() -> done.accept(result));
        });
    }

    public interface Progress {
        void onProgress(int percent);
        void onDone(boolean started, String problem);
    }

    /**
     * Downloads the release and opens the system installer. Aux is updated the same way it was first installed, so a copy
     * Aux Setup installed for Android Auto keeps that, and a plain install stays plain
     */
    public static void install(Context context, Release release, Progress progress) {
        Context app = context.getApplicationContext();
        if (!ReleaseVersion.valid(release.version)) { progress.onDone(false, "Invalid release version"); return; }
        // Aux Setup installs handed updates itself, Aux only needs install permission for the system installer
        if (Build.VERSION.SDK_INT >= 26 && !viaSetup(app) && !app.getPackageManager().canRequestPackageInstalls()) {
            Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(allow);
            progress.onDone(false, "Allow Aux to install updates, then tap Update again");
            return;
        }
        WORKER.execute(() -> {
            try {
                File dir = new File(app.getCacheDir(), "updates");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File apk = new File(dir, "Aux-" + release.version + ".apk");
                try (Response response = HTTP.newCall(new Request.Builder().url(release.apk).header("User-Agent", "Aux/" + BuildConfig.VERSION_NAME).build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("Download failed (" + response.code() + ")");
                    long total = response.body().contentLength() > 0 ? response.body().contentLength() : release.size;
                    try (InputStream in = response.body().byteStream(); OutputStream out = new FileOutputStream(apk)) {
                        byte[] buffer = new byte[1 << 16];
                        long read = 0;
                        int shown = -1;
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            out.write(buffer, 0, n);
                            read += n;
                            int percent = total > 0 ? (int) (read * 100 / total) : 0;
                            if (percent != shown) { shown = percent; int p = percent; MAIN.post(() -> progress.onProgress(p)); }
                        }
                    }
                }
                MAIN.post(() -> {
                    try { app.startActivity(installIntent(app, apk)); progress.onDone(true, null); }
                    catch (Exception error) { progress.onDone(false, "Couldn't open the installer"); }
                });
            } catch (Exception error) {
                com.sya.tube.diag.Diag.log("updates", "download failed " + error.getClass().getSimpleName() + " " + error.getMessage());
                MAIN.post(() -> progress.onDone(false, "Couldn't download the update, check your connection"));
            }
        });
    }

    static final String SETUP = "dev.syavi.aux.setup", SETUP_INSTALL = "dev.syavi.aux.setup.INSTALL_UPDATE", APK_TYPE = "application/vnd.android.package-archive";

    private static Intent installIntent(Context app, File apk) {
        Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".files", apk);
        // Setup uses the same system-installer attribution for first installs and updates
        if (viaSetup(app)) return new Intent(SETUP_INSTALL).setDataAndType(uri, APK_TYPE).setPackage(SETUP).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (installedForAndroidAuto(app)) {
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY);
        }
        // Only the system installer records where an app came from, another installer would take its place
        for (android.content.pm.ResolveInfo option : app.getPackageManager().queryIntentActivities(intent, 0))
            if ((option.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) { intent.setPackage(option.activityInfo.packageName); break; }
        return intent;
    }

    static boolean viaSetup(Context app) {
        if (Build.VERSION.SDK_INT < 30 || !installedForAndroidAuto(app)) return false;
        Intent probe = new Intent(SETUP_INSTALL).setDataAndType(Uri.parse("content://" + app.getPackageName() + ".files/updates/probe.apk"), APK_TYPE).setPackage(SETUP);
        return probe.resolveActivity(app.getPackageManager()) != null;
    }

    /** Aux Setup's install records the Play Store as the installer, see the README for why */
    static boolean installedForAndroidAuto(Context app) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo source = app.getPackageManager().getInstallSourceInfo(app.getPackageName());
                return PLAY.equals(source.getInstallingPackageName()) || PLAY.equals(source.getInitiatingPackageName());
            }
            return PLAY.equals(app.getPackageManager().getInstallerPackageName(app.getPackageName()));
        } catch (Exception error) { return false; }
    }

    /** Compares dotted version numbers, 1.10.0 is newer than 1.9.2 */
    static int compare(String a, String b) {
        return ReleaseVersion.compare(a, b);
    }

    private Updates() {}
}
