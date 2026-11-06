// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.webview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class VideoEnabledWebView extends WebView {
    private boolean addedJavascriptInterface;
    private VideoEnabledWebChromeClient videoEnabledWebChromeClient;

    @Override // android.webkit.WebView
    public void onPause() {
        super.onPause();
    }

    public VideoEnabledWebView(Context context) {
        super(context);
        this.addedJavascriptInterface = false;
    }

    public VideoEnabledWebView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.addedJavascriptInterface = false;
    }

    public VideoEnabledWebView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.addedJavascriptInterface = false;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public boolean isVideoFullscreen() {
        VideoEnabledWebChromeClient videoEnabledWebChromeClient = this.videoEnabledWebChromeClient;
        return videoEnabledWebChromeClient != null && videoEnabledWebChromeClient.isVideoFullscreen();
    }

    public void exitFullScreen() {
        this.videoEnabledWebChromeClient.onHideCustomView();
    }

    @Override // android.webkit.WebView
    public void setWebChromeClient(WebChromeClient webChromeClient) {
        getSettings().setJavaScriptEnabled(true);
        if (webChromeClient instanceof VideoEnabledWebChromeClient) {
            this.videoEnabledWebChromeClient = (VideoEnabledWebChromeClient) webChromeClient;
        }
        super.setWebChromeClient(webChromeClient);
    }

    @Override // android.webkit.WebView
    public void loadData(String str, String str2, String str3) {
        addJavascriptInterface();
        super.loadData(str, str2, str3);
    }

    @Override // android.webkit.WebView
    public void loadDataWithBaseURL(String str, String str2, String str3, String str4, String str5) {
        addJavascriptInterface();
        super.loadDataWithBaseURL(str, str2, str3, str4, str5);
    }

    @Override // android.webkit.WebView
    public void loadUrl(String str) {
        addJavascriptInterface();
        super.loadUrl(str);
    }

    @Override // android.webkit.WebView
    public void loadUrl(String str, Map<String, String> map) {
        addJavascriptInterface();
        super.loadUrl(str, map);
    }

    private void addJavascriptInterface() {
        if (this.addedJavascriptInterface) {
            return;
        }
        addJavascriptInterface(new JavascriptInterface(), "_VideoEnabledWebView");
        this.addedJavascriptInterface = true;
    }

    public class JavascriptInterface {
        public JavascriptInterface() {
        }

        @android.webkit.JavascriptInterface
        public void notifyVideoEnd() {
            Log.d("___", "GOT IT");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    if (VideoEnabledWebView.this.videoEnabledWebChromeClient != null) {
                        VideoEnabledWebView.this.videoEnabledWebChromeClient.onHideCustomView();
                    }
                }
            });
        }
    }
}
