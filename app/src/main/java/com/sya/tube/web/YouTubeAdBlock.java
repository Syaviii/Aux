package com.sya.tube.web;

import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;

/**
 * YouTube serves its ads from its own servers, so blocking hosts barely helps. The ad schedule travels inside the player
 * data instead, this removes it before the page reads it (the approach uBlock Origin uses), hides promoted feed items,
 * and as a last resort finishes any ad that still starts and restores the sound afterwards
 */
public final class YouTubeAdBlock {
    private static final String SCRIPT = "(()=>{if(window.__tubeAds)return;window.__tubeAds=true;"
        + "const KEYS=['adPlacements','adSlots','playerAds','adBreakHeartbeatParams'];"
        + "const prune=o=>{if(!o||typeof o!=='object')return o;for(const k of KEYS)if(k in o){try{delete o[k];}catch(e){}}"
        + "if(o.playerResponse)prune(o.playerResponse);if(Array.isArray(o))o.forEach(prune);return o;};"
        + "const hasAds=o=>o&&typeof o==='object'&&(KEYS.some(k=>k in o)||(o.playerResponse&&KEYS.some(k=>k in o.playerResponse))||(Array.isArray(o)&&o.some(hasAds)));"
        // Player data arrives through JSON.parse, fetch responses and the initial page global
        + "const parse=JSON.parse;JSON.parse=function(){const r=parse.apply(this,arguments);try{if(hasAds(r))prune(r);}catch(e){}return r;};"
        + "const json=Response.prototype.json;Response.prototype.json=function(){return json.call(this).then(r=>{try{if(hasAds(r))prune(r);}catch(e){}return r;});};"
        + "let initial;try{Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,get(){return initial;},set(v){initial=prune(v);}});}catch(e){}"
        // Promoted cards in the feed and search results
        + "const css='ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,ad-slot-renderer,ytm-ad-slot-renderer,"
        // Plain element selectors only, :has() rules make the page recheck its whole tree on every change and slow loading
        + "ytm-promoted-sparkles-text-search-renderer,.ytm-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,#player-ads,.ytp-ad-overlay-container"
        + "{display:none!important}';"
        + "const style=()=>{if(document.getElementById('tube-adblock'))return;const s=document.createElement('style');s.id='tube-adblock';s.textContent=css;(document.head||document.documentElement).appendChild(s);};"
        + "style();document.addEventListener('DOMContentLoaded',style);"
        // Anything that still plays is skipped, or run to its end, muted while it lasts
        + "const SKIP='.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button-container button,button[class*=\"skip-ad\"],button[class*=\"ad-skip\"]';"
        + "let silenced=false;setInterval(()=>{const p=document.querySelector('#movie_player,.html5-video-player');if(!p)return;const v=p.querySelector('video')||document.querySelector('video');"
        + "if(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')){const s=document.querySelector(SKIP);if(s)s.click();"
        + "if(v){if(!v.muted){v.muted=true;silenced=true;}if(isFinite(v.duration)&&v.duration>0&&v.currentTime<v.duration-0.1)v.currentTime=v.duration;}}"
        + "else if(silenced&&v){v.muted=false;silenced=false;}},300);"
        + "})();";

    // Ads and pure telemetry, watch history and playback reporting are left alone so history and recommendations keep working
    private static final String[] AD_PATHS = {"/pagead/", "/api/stats/ads", "/ptracking", "get_midroll_", "/youtubei/v1/log_event", "doubleclick.net", "googleadservices.com", "googlesyndication.com", "/ad_data_204", "/generate_204?ad", "/csi_204", "/api/stats/qoe"};

    public static void apply(WebView webView, boolean enabled) {
        if (enabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, Collections.singleton("https://*.youtube.com"));
        }
    }

    /** Ad and ad tracking requests that never need to leave the phone */
    public static boolean isAdRequest(String url) {
        if (url == null) return false;
        for (String path : AD_PATHS) if (url.contains(path)) return true;
        return false;
    }

    private YouTubeAdBlock() {}
}
