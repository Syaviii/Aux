// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.webview;

import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceView;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import android.widget.VideoView;
import androidx.core.view.ViewCompat;

/* JADX INFO: loaded from: classes.dex */
public class VideoEnabledWebChromeClient extends WebChromeClient implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private View activityNonVideoView;
    private ViewGroup activityVideoView;
    private boolean isVideoFullscreen;
    private View loadingView;
    private ToggledFullscreenCallback toggledFullscreenCallback;
    private boolean videoStretchingEnabled;
    private View.OnTouchListener videoTouchListener;
    private WebChromeClient.CustomViewCallback videoViewCallback;
    private FrameLayout videoViewContainer;
    private VideoEnabledWebView webView;

    public interface ToggledFullscreenCallback {
        void toggledFullscreen(boolean z);
    }

    @Override // android.media.MediaPlayer.OnErrorListener
    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
        return false;
    }

    public VideoEnabledWebChromeClient() {
    }

    public VideoEnabledWebChromeClient(View view, ViewGroup viewGroup) {
        this.activityNonVideoView = view;
        this.activityVideoView = viewGroup;
        this.loadingView = null;
        this.webView = null;
        this.isVideoFullscreen = false;
    }

    public VideoEnabledWebChromeClient(View view, ViewGroup viewGroup, View view2) {
        this.activityNonVideoView = view;
        this.activityVideoView = viewGroup;
        this.loadingView = view2;
        this.webView = null;
        this.isVideoFullscreen = false;
    }

    public VideoEnabledWebChromeClient(View view, ViewGroup viewGroup, View view2, VideoEnabledWebView videoEnabledWebView) {
        this.activityNonVideoView = view;
        this.activityVideoView = viewGroup;
        this.loadingView = view2;
        this.webView = videoEnabledWebView;
        this.isVideoFullscreen = false;
    }

    public void setVideoStretchingEnabled(boolean z) {
        this.videoStretchingEnabled = z;
        handleVideoStretching();
    }

    public View.OnTouchListener getVideoTouchListener() {
        return this.videoTouchListener;
    }

    public void setVideoTouchListener(View.OnTouchListener onTouchListener) {
        this.videoTouchListener = onTouchListener;
    }

    public boolean isVideoFullscreen() {
        return this.isVideoFullscreen;
    }

    public void setOnToggledFullscreen(ToggledFullscreenCallback toggledFullscreenCallback) {
        this.toggledFullscreenCallback = toggledFullscreenCallback;
    }

    @Override // android.webkit.WebChromeClient
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback customViewCallback) {
        Log.d(getClass().getName(), "onShowCustomView() called with: view = [" + view + "], callback = [" + customViewCallback + "]");
        if (view instanceof FrameLayout) {
            FrameLayout frameLayout = (FrameLayout) view;
            View focusedChild = frameLayout.getFocusedChild();
            if (focusedChild != null) {
                focusedChild.requestFocus();
                focusedChild.setOnKeyListener(new View.OnKeyListener() {
                    @Override // android.view.View.OnKeyListener
                    public boolean onKey(View view2, int i, KeyEvent keyEvent) {
                        if (keyEvent.getAction() != 0 || i != 19) {
                            return false;
                        }
                        VideoEnabledWebChromeClient.this.webView.goBack();
                        return true;
                    }
                });
            }
            this.isVideoFullscreen = true;
            if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics","stage webFullscreenShown elapsed="+android.os.SystemClock.elapsedRealtime());
            this.videoViewContainer = frameLayout;
            this.videoViewCallback = customViewCallback;
            this.activityNonVideoView.setVisibility(android.view.View.INVISIBLE);
            this.activityVideoView.addView(this.videoViewContainer, new ViewGroup.LayoutParams(-1, -1));
            this.activityVideoView.setVisibility(android.view.View.VISIBLE);
            if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) {
                view.post(() -> logFullscreenView(view, 0));
            }
            this.activityVideoView.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
            if (focusedChild != null) {
                focusedChild.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
                View.OnTouchListener onTouchListener = this.videoTouchListener;
                if (onTouchListener != null) {
                    focusedChild.setOnTouchListener(onTouchListener);
                }
            }
            if (focusedChild instanceof VideoView) {
                VideoView videoView = (VideoView) focusedChild;
                videoView.setOnPreparedListener(this);
                videoView.setOnCompletionListener(this);
                videoView.setOnErrorListener(this);
            } else {
                VideoEnabledWebView videoEnabledWebView = this.webView;
                if (videoEnabledWebView != null && videoEnabledWebView.getSettings().getJavaScriptEnabled()) {
                    handleVideoStretching();
                }
            }
            ToggledFullscreenCallback toggledFullscreenCallback = this.toggledFullscreenCallback;
            if (toggledFullscreenCallback != null) {
                toggledFullscreenCallback.toggledFullscreen(true);
            }
        }
    }

    private void logFullscreenView(View view, int depth) {
        String surface = "";
        if (view instanceof SurfaceView) {
            SurfaceView videoSurface = (SurfaceView) view;
            surface = " surfaceValid=" + videoSurface.getHolder().getSurface().isValid();
            if (Build.VERSION.SDK_INT >= 36) surface += " composition=" + videoSurface.getCompositionOrder();
        }
        Log.d("TubeFullscreenView", depth + " " + view.getClass().getName()
                + " shown=" + view.isShown() + " hardware=" + view.isHardwareAccelerated()
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " background=" + view.getBackground() + surface);
        if (view instanceof ViewGroup && depth < 8) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) logFullscreenView(group.getChildAt(i), depth + 1);
        }
    }

    private void handleVideoStretching() {
        String str = ("javascript:_ytrp_html5_video = document.getElementsByTagName('video')[0];") + "if (_ytrp_html5_video !== undefined) {var originalWidth = _ytrp_html5_video.style.width; var originalHeight = _ytrp_html5_video.style.height; var originalTop = _ytrp_html5_video.style.top;";
        if (this.videoStretchingEnabled && isVideoFullscreen()) {
            str = str + "_ytrp_html5_video.style.objectFit='fill'; _ytrp_html5_video.style.height=window.innerHeight+'px';_ytrp_html5_video.style.width=window.innerWidth+'px';_ytrp_html5_video.style.top=0;";
        }
        this.webView.loadUrl(str + "}");
    }

    @Override // android.webkit.WebChromeClient
    public void onShowCustomView(View view, int i, WebChromeClient.CustomViewCallback customViewCallback) {
        onShowCustomView(view, customViewCallback);
    }

    @Override // android.webkit.WebChromeClient
    public void onHideCustomView() {
        if (this.isVideoFullscreen) {
            VideoEnabledWebView videoEnabledWebView = this.webView;
            if (videoEnabledWebView != null && videoEnabledWebView.getSettings().getJavaScriptEnabled()) {
                this.webView.loadUrl("javascript:_ytrp_html5_video = document.getElementsByTagName('video')[0];");
            }
            this.activityVideoView.setVisibility(android.view.View.INVISIBLE);
            this.activityVideoView.removeView(this.videoViewContainer);
            this.activityNonVideoView.setVisibility(android.view.View.VISIBLE);
            WebChromeClient.CustomViewCallback customViewCallback = this.videoViewCallback;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            this.isVideoFullscreen = false;
            this.videoViewContainer = null;
            this.videoViewCallback = null;
            ToggledFullscreenCallback toggledFullscreenCallback = this.toggledFullscreenCallback;
            if (toggledFullscreenCallback != null) {
                toggledFullscreenCallback.toggledFullscreen(false);
            }
        }
    }

    @Override // android.webkit.WebChromeClient
    public View getVideoLoadingProgressView() {
        View view = this.loadingView;
        if (view != null) {
            view.setVisibility(android.view.View.VISIBLE);
            return this.loadingView;
        }
        return super.getVideoLoadingProgressView();
    }

    @Override // android.media.MediaPlayer.OnPreparedListener
    public void onPrepared(MediaPlayer mediaPlayer) {
        View view = this.loadingView;
        if (view != null) {
            view.setVisibility(android.view.View.GONE);
        }
    }

    @Override // android.media.MediaPlayer.OnCompletionListener
    public void onCompletion(MediaPlayer mediaPlayer) {
        onHideCustomView();
    }

    public boolean onBackPressed() {
        if (!this.isVideoFullscreen) {
            return false;
        }
        onHideCustomView();
        return true;
    }

    @Override // android.webkit.WebChromeClient
    public void onPermissionRequest(PermissionRequest permissionRequest) {
        if (Build.VERSION.SDK_INT >= 21) {
            permissionRequest.grant(permissionRequest.getResources());
        }
    }

    @Override // android.webkit.WebChromeClient
    public void onGeolocationPermissionsShowPrompt(String str, GeolocationPermissions.Callback callback) {
        super.onGeolocationPermissionsShowPrompt(str, callback);
        callback.invoke(str, true, true);
    }
}
