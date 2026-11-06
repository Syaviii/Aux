// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;
import android.content.Context;
import android.content.Intent;
import android.media.session.PlaybackState;
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService;
public final class BroadcastFromUI {
    private static void send(Context context,String title,int state) {
        Intent intent=new Intent(MyMediaBrowserService.WEBVIEW_EVENT).setPackage(context.getPackageName());
        intent.putExtra(MyMediaBrowserService.MEDIA_TITLE,title); intent.putExtra(MyMediaBrowserService.PLAYBACK_STATE,state);
        context.sendBroadcast(intent);
    }
    public static void broadcastTitle(Context context,String title) { send(context,title,PlaybackState.STATE_PAUSED); }
    public static void broadCastPlaying(Context context,String title) { send(context,title,PlaybackState.STATE_PLAYING); }
    public static void broadCastPaused(Context context,String title) { send(context,title,PlaybackState.STATE_PAUSED); }
    public static void broadCastLoading(Context context) { send(context,null,PlaybackState.STATE_BUFFERING); }
    public static void broadcastState(Context context,String title,int state) { send(context,title,state); }
}
