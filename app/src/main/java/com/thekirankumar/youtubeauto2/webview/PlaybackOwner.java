package com.thekirankumar.youtubeauto2.webview;
import java.lang.ref.WeakReference;
public final class PlaybackOwner {
    private static WeakReference<VideoWebView> active=new WeakReference<>(null);
    private static boolean nativeSelected;
    public static boolean isActive(VideoWebView view) { return !nativeSelected&&active.get()==view; }
    public static boolean isNativeSelected() { return nativeSelected; }
    public static void playing(VideoWebView view) {
        VideoWebView previous=active.get(); active=new WeakReference<>(view); nativeSelected=false;
        com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.pauseForWebPlayback();
        if(previous!=null&&previous!=view) previous.pauseVideo();
    }
    public static void nativePlaying() { nativeSelected=true; VideoWebView previous=active.get(); if(previous!=null)previous.pauseVideo(); }
    public static void nativeClosed() {
        if(!nativeSelected)return;
        nativeSelected=false; VideoWebView previous=active.get();
        if(previous!=null) com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPaused(previous.getContext(),previous.getTitle());
    }
    public static boolean isContentVideo(VideoWebView view) {
        String url=view.getUrl();
        if(url==null)return false;
        android.net.Uri uri=android.net.Uri.parse(url); String host=uri.getHost(),path=uri.getPath();
        return host==null || !(host.equals("youtube.com")||host.endsWith(".youtube.com")) || path!=null&&(path.startsWith("/watch")||path.startsWith("/shorts")||path.startsWith("/tv"));
    }
}
