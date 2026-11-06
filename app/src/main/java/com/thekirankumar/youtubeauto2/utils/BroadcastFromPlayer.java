// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.PlaybackStateCompat;
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService;

/* JADX INFO: loaded from: classes.dex */
public class BroadcastFromPlayer {
    public static void broadcastPlayClicked(Context context) {
        Intent intent = new Intent(MyMediaBrowserService.PLAYER_EVENT);
        intent.putExtra(MyMediaBrowserService.ACTION_TYPE, 4L);
        intent.setPackage(context.getPackageName()); context.sendBroadcast(intent);
    }

    public static void broadcastPauseClicked(Context context) {
        Intent intent = new Intent(MyMediaBrowserService.PLAYER_EVENT);
        intent.putExtra(MyMediaBrowserService.ACTION_TYPE, 2L);
        intent.setPackage(context.getPackageName()); context.sendBroadcast(intent);
    }

    public static void broadcastNextClicked(Context context) {
        Intent intent = new Intent(MyMediaBrowserService.PLAYER_EVENT);
        intent.putExtra(MyMediaBrowserService.ACTION_TYPE, 32L);
        intent.setPackage(context.getPackageName()); context.sendBroadcast(intent);
    }

    public static void broadcastPreviousClicked(Context context) {
        Intent intent = new Intent(MyMediaBrowserService.PLAYER_EVENT);
        intent.putExtra(MyMediaBrowserService.ACTION_TYPE, 16L);
        intent.setPackage(context.getPackageName()); context.sendBroadcast(intent);
    }

    public static void broadcastVoiceSearch(Context context, String str) {
        Intent intent = new Intent(MyMediaBrowserService.PLAYER_EVENT);
        intent.putExtra(MyMediaBrowserService.ACTION_TYPE, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
        intent.putExtra("query", str);
        intent.setPackage(context.getPackageName()); context.sendBroadcast(intent);
    }
}
