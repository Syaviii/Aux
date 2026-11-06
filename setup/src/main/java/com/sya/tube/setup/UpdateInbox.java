package com.sya.tube.setup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.pm.PackageInfoCompat;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Process-owned validation survives Activity recreation; only a verified APK is persisted as update.apk. */
final class UpdateInbox {
    static File pending;
    static boolean busy;
    static String problem;
    private static int generation;
    private static WeakReference<Runnable> observer = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    static void observe(Runnable changed) { observer = new WeakReference<>(changed); }
    private static void notifyChanged() { Runnable changed = observer.get(); if (changed != null) changed.run(); }
    static void read(Context context, Uri incoming) {
        Context app = context.getApplicationContext();
        int id = ++generation;
        busy = true;
        problem = null;
        notifyChanged();
        WORKER.execute(() -> {
            File directory = new File(app.getCacheDir(), "install");
            directory.mkdirs();
            File destination = new File(directory, "update.apk");
            File candidate = incoming == null ? destination : new File(directory, "incoming-" + UUID.randomUUID() + ".apk");
            String failure = null;
            try {
                if (incoming != null) {
                    if (!"content".equals(incoming.getScheme())) throw new IOException("Unsupported update source");
                    try (InputStream in = app.getContentResolver().openInputStream(incoming); OutputStream out = new FileOutputStream(candidate)) {
                        if (in == null) throw new IOException("Missing update");
                        byte[] buffer = new byte[1 << 16];
                        long total = 0;
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            if ((total += n) > 64L * 1024 * 1024) throw new IOException("Update is too large");
                            out.write(buffer, 0, n);
                        }
                    }
                }
                PackageManager pm = app.getPackageManager();
                PackageInfo archive = pm.getPackageArchiveInfo(candidate.getPath(), PackageManager.GET_SIGNATURES);
                PackageInfo own = pm.getPackageInfo(app.getPackageName(), PackageManager.GET_SIGNATURES);
                if (archive == null || !SetupActivity.TUBE.equals(archive.packageName) || archive.signatures == null
                    || archive.signatures.length == 0 || !java.util.Arrays.equals(own.signatures, archive.signatures))
                    throw new IOException("That update isn't a genuine Aux build");
                try {
                    PackageInfo current = pm.getPackageInfo(SetupActivity.TUBE, 0);
                    if (PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(current))
                        throw new IOException("That update is older than your installed Aux");
                } catch (PackageManager.NameNotFoundException absent) { }
            } catch (Exception error) { failure = error instanceof IOException ? error.getMessage() : "Couldn't read the update"; }
            final String result = failure;
            MAIN.post(() -> {
                if (id != generation) { if (incoming != null) candidate.delete(); return; }
                busy = false;
                if (result == null && (incoming == null || candidate.renameTo(destination))) {
                    pending = destination;
                    problem = null;
                } else {
                    candidate.delete();
                    problem = result == null ? "Couldn't save the update. Try again" : result;
                }
                notifyChanged();
            });
        });
    }
    private UpdateInbox() { }
}
