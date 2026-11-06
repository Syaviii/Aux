package com.sya.tube.web;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.sya.tube.player.SponsorBlock;
import java.util.List;

/** SponsorBlock on YouTube's own player, the page jumps over a segment the moment its video reaches one */
public final class WebSegments {
    public interface Listener {
        void onSegmentSkipped(String category);
        /** A segment set to ask is playing, the driver can skip it until it ends */
        void onSegmentOffer(String category);
        void onSegmentOfferGone();
    }

    // Media events do not bubble, a capturing listener on the document still sees every video's time updates
    private static final String HOOK = "if(!window.__tubeSegHook){window.__tubeSegHook=true;"
        + "document.addEventListener('timeupdate',e=>{const v=e.target;if(!v||v.tagName!=='VIDEO'||window.tubeNativePlayback)return;"
        + "const st=window.__tubeSegs;if(!st||new URLSearchParams(location.search).get('v')!==st.id)return;"
        + "const p=document.querySelector('#movie_player,.html5-video-player');if(p&&p.classList.contains('ad-showing'))return;"
        + "const t=v.currentTime;let inside=null;for(const s of st.list){if(t>=s.s&&t<s.e-0.4){inside=s;break;}}"
        + "if(inside&&inside.m==='skip'&&!inside.done){inside.done=true;v.currentTime=inside.e;if(window.tubesegments)tubesegments.skipped(inside.c);return;}"
        + "const offer=inside&&inside.m==='ask'&&!inside.done?inside:null;"
        + "if(offer!==st.offer){st.offer=offer;if(window.tubesegments){if(offer)tubesegments.offer(offer.c);else tubesegments.gone();}}},true);"
        + "window.__tubeSegSkip=()=>{const st=window.__tubeSegs;const v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');"
        + "if(st&&st.offer&&v){v.currentTime=st.offer.e;st.offer.done=true;st.offer=null;if(window.tubesegments)tubesegments.gone();}};}";

    public static void attach(WebView webView, Listener listener) {
        Handler main = new Handler(Looper.getMainLooper());
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void skipped(String category) { main.post(() -> listener.onSegmentSkipped(category)); }
            @JavascriptInterface public void offer(String category) { main.post(() -> listener.onSegmentOffer(category)); }
            @JavascriptInterface public void gone() { main.post(listener::onSegmentOfferGone); }
        }, "tubesegments");
    }

    /** Hands the page this video's segments, a segment already skipped is left alone when the same list comes again */
    public static void apply(WebView webView, String videoId, List<SponsorBlock.Segment> segments) {
        // The id is written into the script, so it must look like a YouTube id and nothing else
        if (webView == null || videoId == null || !videoId.matches("[\\w-]{6,20}")) return;
        webView.evaluateJavascript("(()=>{" + HOOK + "const st=window.__tubeSegs;if(st&&st.id==='" + videoId + "'&&st.count===" + segments.size() + ")return;"
            + "const list=" + SponsorBlock.json(segments) + ";window.__tubeSegs={id:'" + videoId + "',list:list,count:list.length,offer:null};})()", null);
    }

    public static void skipOffered(WebView webView) {
        if (webView != null) webView.evaluateJavascript("(()=>{if(window.__tubeSegSkip)window.__tubeSegSkip();})()", null);
    }

    private WebSegments() {}
}
