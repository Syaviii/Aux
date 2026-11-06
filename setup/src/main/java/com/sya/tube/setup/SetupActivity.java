package com.sya.tube.setup;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Android Auto only lists a projection app whose install the system package installer started with the Play Store recorded as
 * the installer, so Setup hands the bundled Aux to the system installer with that attribution.
 * Shell installs may be rejected even when they request the same installer name
 */
public class SetupActivity extends Activity {
    static final String TUBE = "dev.syavi.aux";
    private static final String PLAY = "com.android.vending", HAND_OVER = "dev.syavi.aux.setup.INSTALL_UPDATE";
    private static final String SYSTEM_INSTALLER = "com.google.android.packageinstaller";
    private static final int INSTALL_REQUEST = 1;
    // An update Aux downloaded and handed over, installed instead of the copy inside Setup
    private final Runnable updateChanged = () -> refresh();
    // Aux's own colors, so Setup looks like part of the same app
    private static final int BACKGROUND = 0xFF0F0F0F, SURFACE = 0xFF1C1C1C, SURFACE_HIGH = 0xFF2A2A2A, ON_SURFACE = 0xFFF1F1F1, MUTED = 0xFFAAAAAA;
    private TextView status, action;
    private LinearLayout steps;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(24));
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.ic_aux_setup);
        root.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = text("Aux Setup", 28, ON_SURFACE, true);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);
        status = text("", 15, MUTED, false);
        status.setPadding(0, dp(4), 0, dp(20));
        root.addView(status);
        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setBackground(rounded(SURFACE, dp(18)));
        root.addView(steps, new LinearLayout.LayoutParams(-1, -2));
        action = pill("", true);
        LinearLayout.LayoutParams actionPlace = new LinearLayout.LayoutParams(-1, dp(52));
        actionPlace.topMargin = dp(20);
        root.addView(action, actionPlace);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(root);
        setContentView(scroll);
        File pending = new File(new File(getCacheDir(), "install"), "update.apk");
        UpdateInbox.observe(updateChanged);
        if (pending.exists() && !UpdateInbox.busy && UpdateInbox.pending == null) UpdateInbox.read(this, null);
        if (state == null) takeHandedApk(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeHandedApk(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Aux hands its downloaded update here, it is only kept when it is Aux signed with the same key as Setup */
    private void takeHandedApk(Intent intent) {
        if (intent == null || intent.getData() == null || !HAND_OVER.equals(intent.getAction())) return;
        UpdateInbox.read(this, intent.getData());
    }

    private static long versionCode(PackageInfo info) {
        return androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info);
    }

    private void refresh() {
        if (UpdateInbox.busy) {
            status.setText("Checking the update...");
            setAction("Checking...", null);
            steps.removeAllViews();
            return;
        }
        PackageInfo installed = installed();
        boolean current = installed != null && versionCode(installed) >= bundledVersion();
        boolean listed = installed != null && carReady();
        boolean mic = installed != null && micGranted();
        steps.removeAllViews();
        View.OnClickListener installAux = v -> install();
        View.OnClickListener allowMic = v -> openTube();
        step(1, "Install Aux", current ? "Version " + installed.versionName : installed == null ? "Not installed" : "Version " + archiveVersion() + " is ready",
            current, installed == null ? "Install" : "Update", installAux);
        // The record is shown so a phone that still won't list Aux can be checked from a screenshot
        step(2, "Show it in Android Auto", listed ? "Marked as a Play Store app" : installed == null ? "Done by step 1" : "Reinstalls over it, settings stay\n" + record(),
            listed, installed == null ? null : "Fix", installAux);
        step(3, "Allow the microphone", "For voice search in the car", mic, installed == null ? null : "Allow", allowMic);
        if (!getPackageManager().canRequestPackageInstalls() && !(current && listed)) {
            status.setText("Allow Aux Setup to install apps first");
            setAction("Allow installs", installAux);
        } else if (!current) {
            status.setText(installed == null ? "Three steps to Aux in your car" : "A new version is ready");
            setAction(installed == null ? "Install Aux" : "Update Aux", installAux);
        } else if (!listed) {
            status.setText("Android Auto hides Aux until it's installed as a Play Store app");
            setAction("Fix Aux for Android Auto", installAux);
        } else if (!mic) {
            status.setText("Allow the microphone for voice search");
            setAction("Allow the microphone", allowMic);
        } else {
            status.setText("All set. Reconnect your car and open Aux");
            setAction("Open Aux", v -> openTube());
        }
        if (UpdateInbox.problem != null) status.setText(UpdateInbox.problem);
    }

    private void setAction(String label, View.OnClickListener click) {
        action.setText(label);
        action.setOnClickListener(click);
        action.setAlpha(click == null ? .5f : 1f);
        action.setEnabled(click != null);
    }

    /** One numbered row of the checklist with its own button, a done step shows a check and no button */
    private void step(int number, String title, String detail, boolean done, String button, View.OnClickListener click) {
        if (steps.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(0x1CFFFFFF);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) * 2 / 3));
            place.setMarginStart(dp(64));
            steps.addView(line, place);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));
        TextView badge = text(done ? "✓" : String.valueOf(number), 15, done ? Color.BLACK : ON_SURFACE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(done ? ON_SURFACE : SURFACE_HIGH, dp(16)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(16), 0, dp(8), 0);
        labels.addView(text(title, 16, ON_SURFACE, false));
        labels.addView(text(detail, 13, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (!done && button != null) {
            TextView go = pill(button, false);
            go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            go.setPadding(dp(16), 0, dp(16), 0);
            go.setOnClickListener(click);
            go.setMinWidth(dp(48));
            go.setContentDescription(button + " " + title);
            row.addView(go, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        steps.addView(row);
    }

    private File apkToInstall() throws IOException {
        if (UpdateInbox.pending != null && UpdateInbox.pending.exists()) return UpdateInbox.pending;
        File apk = new File(new File(getCacheDir(), "install"), "tube.apk");
        apk.getParentFile().mkdirs();
        // The copy is reused when it was made after this Setup was installed, two Aux builds can have the very same size
        try {
            long installedAt = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
            if (apk.length() > 0 && apk.lastModified() > installedAt) return apk;
        } catch (PackageManager.NameNotFoundException impossible) { }
        try (InputStream in = getAssets().open("tube.apk"); OutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[1 << 16];
            for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
        }
        return apk;
    }

    private String archiveVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(apkToInstall().getPath(), 0);
            return info == null ? "" : info.versionName;
        } catch (Exception error) { return ""; }
    }

    private TextView text(String value, int sp, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (strong) view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return view;
    }

    private TextView pill(String label, boolean primary) {
        TextView pill = text(label, 15, primary ? Color.BLACK : ON_SURFACE, true);
        pill.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable shape = rounded(primary ? ON_SURFACE : SURFACE_HIGH, dp(26));
        pill.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), shape, null));
        pill.setFocusable(true);
        pill.setClickable(true);
        return pill;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private boolean micGranted() {
        return getPackageManager().checkPermission(android.Manifest.permission.RECORD_AUDIO, TUBE) == PackageManager.PERMISSION_GRANTED;
    }

    private void openTube() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TUBE);
        if (launch != null) startActivity(launch);
    }

    private void install() {
        if (UpdateInbox.busy) return;
        UpdateInbox.problem = null;
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", apkToInstall());
            // The system installer creates the session, so it is the initiating package and records this installer name
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            // Only the system installer records the attribution, another installer app would take its place as the initiator
            for (android.content.pm.ResolveInfo option : getPackageManager().queryIntentActivities(intent, 0)) {
                if ((option.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) { intent.setPackage(option.activityInfo.packageName); break; }
            }
            startActivityForResult(intent, INSTALL_REQUEST);
        } catch (Exception error) {
            UpdateInbox.problem = "Could not open the system installer. Try again";
            refresh();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != INSTALL_REQUEST) return;
        PackageInfo now = installed();
        if (UpdateInbox.pending != null && now != null && versionCode(now) >= bundledVersion()) { UpdateInbox.pending.delete(); UpdateInbox.pending = null; }
        if (result == RESULT_CANCELED) UpdateInbox.problem = "Installation cancelled. You can try again";
        refresh();
    }

    private PackageInfo installed() {
        try { return getPackageManager().getPackageInfo(TUBE, 0); }
        catch (PackageManager.NameNotFoundException missing) { return null; }
    }

    private long bundledVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(apkToInstall().getPath(), 0);
            return info == null ? Long.MAX_VALUE : versionCode(info);
        } catch (Exception error) { return Long.MAX_VALUE; }
    }

    /**
     * Android Auto's own rule: the package that started the install counts, unless it is the system installer, then the recorded
     * installer counts, and that has to be the Play Store. An adb install is started by the shell and fails it
     */
    private boolean carReady() {
        if (Build.VERSION.SDK_INT < 30) return PLAY.equals(getPackageManager().getInstallerPackageName(TUBE));
        try {
            android.content.pm.InstallSourceInfo source = getPackageManager().getInstallSourceInfo(TUBE);
            String counted = source.getInitiatingPackageName();
            if (SYSTEM_INSTALLER.equals(counted)) counted = source.getInstallingPackageName();
            return PLAY.equals(counted);
        } catch (PackageManager.NameNotFoundException missing) { return false; }
    }

    private String record() {
        if (Build.VERSION.SDK_INT < 30) return "Installer " + getPackageManager().getInstallerPackageName(TUBE);
        try {
            android.content.pm.InstallSourceInfo source = getPackageManager().getInstallSourceInfo(TUBE);
            return "Installer " + source.getInstallingPackageName() + ", started by " + source.getInitiatingPackageName();
        } catch (PackageManager.NameNotFoundException missing) { return ""; }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
