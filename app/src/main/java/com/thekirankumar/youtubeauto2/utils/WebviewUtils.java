// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.thekirankumar.youtubeauto2.webview.VideoEnabledWebView;

/* JADX INFO: loaded from: classes.dex */
public class WebviewUtils {
    private static final String FILE_BROWSER_CSS_PATH = "file:///android_asset/filebrowser.css";
    private static final String FILE_BROWSER_SCRIPT_PATH = "file:///android_asset/filebrowser.js";

    public static void setDesktopMode(WebView webView, boolean z) {
        String strReplace;
        WebSettings settings = webView.getSettings();
        if (z) {
            strReplace = settings.getUserAgentString().replace("Mobile", "eliboM").replace("Android", "diordnA");
        } else {
            strReplace = settings.getUserAgentString().replace("eliboM", "Mobile").replace("diordnA", "Android");
        }
        settings.setUserAgentString(strReplace);
        settings.setUseWideViewPort(z);
        settings.setLoadWithOverviewMode(z);
        settings.setSupportZoom(z);
        settings.setBuiltInZoomControls(z);
    }

    public static void injectCustomSelectDropDown(WebView webView) {
        Log.d("DropDown", "Yep");
        webView.loadUrl("javascript:for(i=0;i<document.getElementsByTagName('select').length;i++){document.getElementsByTagName('select')[i].onclick=function(){if(this.selectedIndex < this.options.length-1) {this.selectedIndex++} else {this.selectedIndex=0}this.dispatchEvent(new Event('change', { bubbles: true }));};}");
    }

    public static void injectQualityControl(WebView webView) {
        // Older YouTube controls may exist, but the current player has its own quality menu.
        webView.evaluateJavascript("(function(){var menu=document.querySelector('ytm-menu');var actions=document.querySelector('.slim-video-metadata-actions');if(menu&&actions&&!actions.contains(menu))actions.appendChild(menu);})()",null);
    }

    public static void injectNightModeCss(WebView webView) {
        String url = webView.getUrl();
        Log.d("NightModeInjected", url);
        if (url != null) {
            webView.loadUrl("javascript:var css = 'html {background-color: #000 !important;}\ndiv.page-container{ color: #fff !important;}\ndiv.mobile-topbar-header-content{ color: hsla(0, 0%, 100%, 0.6) !important;}\nytm-mobile-topbar-renderer.sticky-player {background-color: #000 !important;}\nc3-icon.mobile-topbar-logo{ color: #fff !important;}\nytm-pivot-bar-renderer{ background-color: #000 !important; color: #fff !important;}\ndiv.slim-video-metadata-actions{color:hsla(0, 33.3%, 97.1%, 0.6);}\nytm-app.sticky-player {padding-top: 10px !important;}\n.player-container.sticky-player {top: 0px !important;}';\nvar head  = document.getElementsByTagName('head')[0];\nvar style  = document.createElement('style');\nstyle.type = 'text/css';\nvar topbar=document.querySelector('ytm-mobile-topbar-renderer'); if(topbar) topbar.style.display='none';\nstyle.appendChild(document.createTextNode(css));\nhead.appendChild(style);");
        }
    }

    public static void injectFileListingHack(VideoEnabledWebView view) {
        // LocalFileBrowser supplies the directory table; current WebView no longer auto-indexes it.
    }
}
