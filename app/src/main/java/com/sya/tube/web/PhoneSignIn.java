package com.sya.tube.web;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.CookieManager;
import androidx.core.app.NotificationCompat;
import com.thekirankumar.youtubeauto2.R;

/**
 * Signing in to Patreon on the phone for the car. The car and phone share one cookie store, so once the phone's page
 * is signed in the car's page is too. The phone is reached with a notification, opening it shows Patreon's sign-in
 */
public final class PhoneSignIn {
    public static final String EXTRA_PAGE = "aux.signInPage";
    private static final String CHANNEL = "aux_sign_in";
    private static final int ID = 4107;

    /** Puts a notification on the phone that opens the sign-in page in Aux */
    public static void request(Context context, String page) {
        Context app = context.getApplicationContext();
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Sign in for the car", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(app, com.thekirankumar.youtubeauto2.activity.MainPhoneActivity.class)
            .putExtra(EXTRA_PAGE, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(app, ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        manager.notify(ID, new NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_aux_notification)
            .setContentTitle("Sign in to Patreon")
            .setContentText("Tap to sign in here, the car screen continues by itself")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build());
    }

    /** Patreon keeps a session cookie only while signed in */
    public static boolean signedIn() {
        String cookies = CookieManager.getInstance().getCookie("https://www.patreon.com/");
        return cookies != null && cookies.matches("(?s).*(^|;\\s*)session_id=[^;]+.*");
    }

    public static void done(Context context) {
        NotificationManager manager = context.getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(ID);
    }

    private PhoneSignIn() {}
}
