package com.sya.tube.web;

import android.os.Build;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;

/** Car-oriented WebView settings that make playback and scrolling cheaper without changing the YouTube interface */
public final class WebTuning {
    // Same approach as the h264ify extension, YouTube then serves H.264, which every phone decodes in hardware at the lowest cost
    private static final String PREFER_H264 = "(()=>{if(window.__tubeH264)return;window.__tubeH264=true;"
        + "const blocked=/vp0?9|av01|av1|vp8/i;"
        + "for(const MS of [window.MediaSource,window.ManagedMediaSource,window.WebKitMediaSource]){if(MS&&MS.isTypeSupported){const original=MS.isTypeSupported.bind(MS);MS.isTypeSupported=t=>blocked.test(t||'')?false:original(t);}}"
        + "const canPlay=HTMLMediaElement.prototype.canPlayType;HTMLMediaElement.prototype.canPlayType=function(t){return blocked.test(t||'')?'':canPlay.call(this,t);};"
        + "const caps=navigator.mediaCapabilities;if(caps&&caps.decodingInfo){const decoding=caps.decodingInfo.bind(caps);caps.decodingInfo=c=>{const t=(c&&c.video&&c.video.contentType)||'';return blocked.test(t)?Promise.resolve({supported:false,smooth:false,powerEfficient:false}):decoding(c);};}"
        + "})();";

    // Offscreen feed and result cards skip layout and paint until they scroll near the screen, the reserved size keeps
    // the scroll position steady, the car screen shows three or four cards at a time out of dozens on the page
    private static final String LIGHT_LISTS = "(()=>{if(window.__tubeLists)return;window.__tubeLists=true;"
        + "const css='ytm-rich-item-renderer,ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-playlist-video-renderer,ytm-compact-playlist-renderer"
        + "{content-visibility:auto;contain-intrinsic-size:auto 320px}';"
        + "const add=()=>{if(document.getElementById('tube-lists'))return;const s=document.createElement('style');s.id='tube-lists';s.textContent=css;(document.head||document.documentElement).appendChild(s);};"
        + "add();document.addEventListener('DOMContentLoaded',add);})();";

    // Media events do not bubble but they can be captured at the document, so every video announces itself the moment it
    // starts loading, without watching the whole page for new elements while YouTube builds it
    private static final String MEDIA_DISCOVERY = "(()=>{if(window.__tubeMedia)return;window.__tubeMedia=true;"
        + "const found=e=>{const v=e.target;if(!v||v.tagName!=='VIDEO'||v.dataset.tubeDiscovered)return;v.dataset.tubeDiscovered='1';if(window.nativecallbacks)window.nativecallbacks.onVideoDiscovered();};"
        // YouTube reuses one video element for every video on a watch page, each new load is reported as well
        + "document.addEventListener('loadstart',e=>{found(e);if(e.target&&e.target.tagName==='VIDEO'&&window.nativecallbacks)window.nativecallbacks.onVideoEvent('loading');},true);"
        + "document.addEventListener('play',found,true);})();";

    // On the phone Tube's own bar has Home and search, YouTube's header is hidden and the space it keeps for it goes to the feed
    private static final String PHONE_CHROME = "(()=>{if(window.__tubePhone)return;window.__tubePhone=true;"
        + "const css='ytm-mobile-topbar-renderer{display:none!important}ytm-app{padding-top:0!important}"
        + "ytm-feed-filter-chip-bar-renderer{top:0!important}';"
        + "const add=()=>{if(document.getElementById('tube-phone'))return;const s=document.createElement('style');s.id='tube-phone';s.textContent=css;(document.head||document.documentElement).appendChild(s);};"
        + "add();document.addEventListener('DOMContentLoaded',add);})();";

    // The simpler car page, comments, Shorts shelves and shopping or info panels go, the video list and its buttons stay as they are
    private static final String CAR_CLEAN = "(()=>{if(window.__tubeClean)return;window.__tubeClean=true;"
        + "const css='yt-video-metadata-carousel-view-model,ytm-comments-entry-point-header-renderer,ytm-reel-shelf-renderer,ytm-shorts-lockup-view-model,"
        + "ytm-rich-section-renderer:has(ytm-shorts-lockup-view-model),ytm-merch-shelf-renderer,ytm-ticket-shelf-renderer,ytm-clarification-renderer,"
        + "ytm-info-panel-container-renderer,ytm-paid-content-overlay-renderer{display:none!important}"
        // YouTube keeps a header sized gap above its chips though it hides the header, the chips move up into it
        + "ytm-app{padding-top:0!important}ytm-feed-filter-chip-bar-renderer{top:0!important}';"
        + "const add=()=>{if(document.getElementById('tube-clean'))return;const s=document.createElement('style');s.id='tube-clean';s.textContent=css;(document.head||document.documentElement).appendChild(s);};"
        + "add();document.addEventListener('DOMContentLoaded',add);})();";

    // YouTube's mobile site stops the video when the page reports itself hidden, the car screen is hidden whenever the driver
    // switches to Maps, so the car page always reports itself visible and sound behind another app keeps going
    private static final String ALWAYS_VISIBLE = "(()=>{if(window.__tubeVisible)return;window.__tubeVisible=true;"
        + "try{Object.defineProperty(Document.prototype,'visibilityState',{get:()=>'visible',configurable:true});"
        + "Object.defineProperty(Document.prototype,'hidden',{get:()=>false,configurable:true});"
        + "Object.defineProperty(Document.prototype,'webkitVisibilityState',{get:()=>'visible',configurable:true});"
        + "Object.defineProperty(Document.prototype,'webkitHidden',{get:()=>false,configurable:true});}catch(e){}"
        + "for(const type of ['visibilitychange','webkitvisibilitychange'])document.addEventListener(type,e=>e.stopImmediatePropagation(),true);})();";

    public static void applyAlwaysVisible(WebView webView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(webView, ALWAYS_VISIBLE, Collections.singleton("https://*.youtube.com"));
    }

    public static void applyCarClean(WebView webView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(webView, CAR_CLEAN, Collections.singleton("https://*.youtube.com"));
    }

    /** The phone page under Tube's top bar, it also skips offscreen cards the same way the car screen does */
    public static void applyPhone(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, PHONE_CHROME, Collections.singleton("https://*.youtube.com"));
        WebViewCompat.addDocumentStartJavaScript(webView, LIGHT_LISTS, Collections.singleton("https://*.youtube.com"));
    }

    /** Lets the WebView skip its page-wide element observer, returns false where document start scripts are unavailable */
    public static boolean installMediaDiscovery(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false;
        WebViewCompat.addDocumentStartJavaScript(webView, MEDIA_DISCOVERY, Collections.singleton("*"));
        return true;
    }

    public static void apply(WebView webView, boolean preferH264) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, LIGHT_LISTS, Collections.singleton("https://*.youtube.com"));
        }
        if (Build.VERSION.SDK_INT >= 26) {
            // The car screen is the only visible content, so the renderer must not be deprioritized under memory or CPU pressure
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        if (preferH264 && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, PREFER_H264, Collections.singleton("https://*.youtube.com"));
        }
    }

    private WebTuning() {}
}
