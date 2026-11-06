package com.sya.tube.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.preference.PreferenceManager;

/** Settings read from more than one place, with the defaults the settings page shows */
public final class TubePrefs {
    public static final String SPEED = "pref_speed";
    public static final String QUALITY_MOBILE = "pref_quality_mobile";
    public static final String QUALITY_WIFI = "pref_quality_wifi";
    public static final String SPONSORBLOCK = "pref_sponsorblock";
    public static final String HOME_CHANNELS = "pref_home_channels";
    public static final String SKIP_SEEKS = "pref_skip_seeks";
    public static final String CONTINUE_ROW = "pref_continue_row";
    public static final float[] SPEEDS = {1f, 1.25f, 1.5f, 1.75f, 2f};

    /** SponsorBlock's categories in the order the settings page lists them, with what each one does by default */
    public static final String[][] SEGMENTS = {
        {"sponsor", "Sponsors", "Paid promotion and ad reads", "skip", "sponsor"},
        {"selfpromo", "Self promotion", "Merch, Patreon and the creator's other channels", "skip", "self promotion"},
        {"interaction", "Like and subscribe reminders", "Requests for likes, subscriptions or comments", "skip", "reminder"},
        {"intro", "Intros", "Intro animations and countdowns", "off", "intro"},
        {"outro", "End screens and credits", "Closing sequences and credits", "off", "endcard"},
        {"preview", "Previews and recaps", "Clips of upcoming or earlier scenes", "off", "preview"},
        {"filler", "Tangents and jokes", "Off-topic parts of the video", "off", "tangent"},
        {"music_offtopic", "Non-music parts", "Talking in music videos", "off", "non-music part"},
    };

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static float speed(Context context) {
        try { return Float.parseFloat(get(context).getString(SPEED, "1")); } catch (RuntimeException ignored) { return 1f; }
    }

    public static void setSpeed(Context context, float speed) {
        get(context).edit().putString(SPEED, speedText(speed)).apply();
    }

    public static String speedText(float speed) {
        return speed == (int) speed ? (int) speed + "" : String.valueOf(speed);
    }

    /** The phone's internet comes over mobile data, which is almost always the case in a car */
    public static boolean onMobileData(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = connectivity == null ? null : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (caps == null) return true;
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    /** The quality a new video starts at, mobile data and Wi-Fi each have their own */
    public static String startQuality(Context context) {
        return onMobileData(context) ? get(context).getString(QUALITY_MOBILE, "hd720") : get(context).getString(QUALITY_WIFI, "hd720");
    }

    /** The steering wheel's and the car's next and previous buttons jump 10 seconds instead of changing the video */
    public static boolean skipSeeks(Context context) {
        return get(context).getBoolean(SKIP_SEEKS, true);
    }

    public static boolean sponsorBlockOn(Context context) {
        return get(context).getBoolean(SPONSORBLOCK, true);
    }

    /** skip, ask or off */
    public static String segmentMode(Context context, String category) {
        for (String[] segment : SEGMENTS) if (segment[0].equals(category)) return get(context).getString("pref_sb_" + category, segment[3]);
        return "off";
    }

    public static String segmentName(String category) {
        for (String[] segment : SEGMENTS) if (segment[0].equals(category)) return segment[1];
        return "Segment";
    }

    /** One segment of the kind, as a notice names it */
    public static String segmentNoun(String category) {
        for (String[] segment : SEGMENTS) if (segment[0].equals(category)) return segment[4];
        return "segment";
    }

    private TubePrefs() {}
}
