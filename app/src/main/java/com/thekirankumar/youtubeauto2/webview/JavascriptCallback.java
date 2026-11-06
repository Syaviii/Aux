// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.webview;

import android.os.Handler;
import android.webkit.JavascriptInterface;

/* JADX INFO: loaded from: classes.dex */
public class JavascriptCallback {
    private JSCallbacks callbacks;
    private final Handler handler = new Handler();

    public interface JSCallbacks {
        void onHideKeyboardFromJS();

        void onJSVideoEvent(String str);

        void onShowKeyboardFromJS(String str);

        void onVideoCurrentTimeResult(int i);

        void onVideoElementDiscovered();
    }

    public JavascriptCallback(JSCallbacks jSCallbacks) {
        this.callbacks = jSCallbacks;
    }

    @JavascriptInterface
    public void onPlaybackSample(String sample) {
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubePlayback",sample);
    }

    @JavascriptInterface
    public void onVideoEvent(final String str) {
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                JavascriptCallback.this.callbacks.onJSVideoEvent(str);
            }
        });
    }

    @JavascriptInterface
    public void onVideoCurrentTimeResult(final int i) {
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                JavascriptCallback.this.callbacks.onVideoCurrentTimeResult(i);
            }
        });
    }

    @JavascriptInterface
    public void onVideoDiscovered() {
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                JavascriptCallback.this.callbacks.onVideoElementDiscovered();
            }
        });
    }

    @JavascriptInterface
    public void showKeyboard(final String str) {
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                JavascriptCallback.this.callbacks.onShowKeyboardFromJS(str);
            }
        });
    }

    @JavascriptInterface
    public void hideKeyboard() {
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                JavascriptCallback.this.callbacks.onHideKeyboardFromJS();
            }
        });
    }
}
