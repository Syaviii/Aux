package com.sya.tube.diag;

import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A short history of what Aux did, the phone menu shares it so problems seen in the car can be diagnosed without plugging the
 * phone in. It stays in memory unless the driver turns on keeping a log, a crash is only kept when crash reports are on
 */
public final class Diag {
    public static final String KEEP_LOG = "pref_diagnostics_log";
    public static final String CRASH_REPORTS = "pref_crash_reports";
    private static final int MAX_LINES = 800;
    private static final long MAX_FILE_BYTES = 256 * 1024;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> new Thread(r, "AuxDiag"));
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static File file, crashFile;
    private static Context app;

    public static void init(Context context) {
        if (app != null) return;
        app = context.getApplicationContext();
        crashFile = new File(app.getFilesDir(), "crash-report.txt");
        File log = new File(app.getFilesDir(), "diag.log");
        // Test builds always keep the log, release builds only when the setting is on
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG || PreferenceManager.getDefaultSharedPreferences(app).getBoolean(KEEP_LOG, true)) {
            file = log;
            // The previous run's tail is kept, it usually holds the moment something went wrong
            if (file.exists()) {
                try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                    ArrayDeque<String> previous = new ArrayDeque<>();
                    for (String line; (line = in.readLine()) != null; ) { previous.add(line); if (previous.size() > MAX_LINES / 2) previous.removeFirst(); }
                    synchronized (lines) { for (String entry : previous) lines.add(DiagnosticPrivacy.clean(entry)); }
                } catch (IOException ignored) {}
            }
        } else if (log.exists()) log.delete();
        Thread.UncaughtExceptionHandler fallback = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            java.io.StringWriter trace = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(trace));
            String text = DiagnosticPrivacy.clean(trace.toString());
            writeNow("CRASH " + thread.getName() + " " + (text.length() > 3000 ? text.substring(0, 3000) : text).replace('\n', '|'));
            saveCrash(thread, text);
            if (fallback != null) fallback.uncaughtException(thread, error);
        });
        log("app", "start " + version(app));
    }

    /** Written on the crashing thread for the next start to offer, only with crash reports turned on */
    private static void saveCrash(Thread thread, String trace) {
        try {
            if (!PreferenceManager.getDefaultSharedPreferences(app).getBoolean(CRASH_REPORTS, true)) return;
            StringBuilder out = new StringBuilder();
            out.append("Aux ").append(version(app)).append('\n');
            out.append("Android ").append(Build.VERSION.RELEASE).append(" (sdk ").append(Build.VERSION.SDK_INT).append(") ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            out.append("Android Auto ").append(packageVersion(app, "com.google.android.projection.gearhead")).append('\n');
            out.append("Thread ").append(thread.getName()).append("\n\n").append(trace.length() > 6000 ? trace.substring(0, 6000) : trace);
            try (FileWriter writer = new FileWriter(crashFile, false)) { writer.write(out.toString()); }
        } catch (Exception ignored) {}
    }

    /** The crash from the last run, waiting to be offered, or null */
    public static String pendingCrash() {
        if (crashFile == null || !crashFile.exists()) return null;
        try { return DiagnosticPrivacy.clean(new String(java.nio.file.Files.readAllBytes(crashFile.toPath()), java.nio.charset.StandardCharsets.UTF_8)); }
        catch (IOException error) { return null; }
    }

    public static void clearCrash() {
        if (crashFile != null) crashFile.delete();
    }

    /** Turning the log off removes what was kept */
    public static void setKeepLog(boolean keep) {
        if (app == null) return;
        File log = new File(app.getFilesDir(), "diag.log");
        if (keep || com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) file = log;
        else { file = null; WRITER.execute(log::delete); }
    }

    public static void log(String tag, String message) {
        String line = stamp() + " " + tag + " " + DiagnosticPrivacy.clean(message);
        synchronized (lines) {
            lines.add(line);
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
        File target = file;
        if (target != null) WRITER.execute(() -> append(target, line));
    }

    /** Written on the crashing thread itself, the process is about to end */
    private static void writeNow(String message) {
        String line = stamp() + " " + DiagnosticPrivacy.clean(message);
        synchronized (lines) { lines.add(line); }
        if (file != null) append(file, line);
    }

    private static void append(File target, String line) {
        try {
            if (target.length() > MAX_FILE_BYTES) {
                StringBuilder kept = new StringBuilder();
                synchronized (lines) { for (String each : lines) kept.append(each).append('\n'); }
                try (FileWriter out = new FileWriter(target, false)) { out.write(kept.toString()); }
            } else {
                try (FileWriter out = new FileWriter(target, true)) { out.write(line); out.write('\n'); }
            }
        } catch (IOException ignored) {}
    }

    private static String stamp() {
        synchronized (CLOCK) { return CLOCK.format(new Date()); }
    }

    /** A plain text report, short enough to paste into a message */
    public static String report(Context context) {
        StringBuilder out = new StringBuilder("Aux diagnostics\n");
        out.append("app ").append(version(context)).append('\n');
        out.append("android ").append(Build.VERSION.RELEASE).append(" sdk ").append(Build.VERSION.SDK_INT).append(' ').append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("android auto ").append(packageVersion(context, "com.google.android.projection.gearhead")).append('\n');
        out.append("webview ").append(packageVersion(context, "com.google.android.webview")).append('\n');
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                InstallSourceInfo source = context.getPackageManager().getInstallSourceInfo(context.getPackageName());
                out.append("install initiating=").append(source.getInitiatingPackageName()).append(" installing=").append(source.getInstallingPackageName()).append('\n');
            } catch (Exception ignored) {}
        }
        out.append("prefs omitted for privacy\n");
        out.append("car fps ").append(com.sya.tube.player.YoutubeStreams.carFrameRate).append('\n');
        out.append("events\n");
        synchronized (lines) { for (String line : lines) out.append(line).append('\n'); }
        return DiagnosticPrivacy.clean(out.toString());
    }

    private static String version(Context context) {
        return packageVersion(context, context.getPackageName());
    }

    private static String packageVersion(Context context, String pkg) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
            return info.versionName + " (" + (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode) + ")";
        } catch (Exception missing) { return "missing"; }
    }

    private Diag() {}
}
