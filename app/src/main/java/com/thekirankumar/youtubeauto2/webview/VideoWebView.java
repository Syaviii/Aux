// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.webview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

/* JADX INFO: loaded from: classes.dex */
public class VideoWebView extends VideoEnabledWebView {
    // Chromium pauses a video whose page is hidden, while the car keeps sound playing behind another app the page is
    // told its window is still visible
    private boolean keepVisible;
    public void setKeepVisible(boolean value) { keepVisible=value; }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(keepVisible?android.view.View.VISIBLE:visibility); }

    private float TOUCH_TOLERANCE;
    private float lastDownX;
    private float lastDownY;
    private static String videoListenersScript;

    private void init() {
    }

    public VideoWebView(Context context) {
        super(context);
        this.TOUCH_TOLERANCE = 20.0f;
        init();
    }

    public VideoWebView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.TOUCH_TOLERANCE = 20.0f;
        init();
    }

    public VideoWebView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.TOUCH_TOLERANCE = 20.0f;
        init();
    }

    @Override // android.webkit.WebView, android.view.ViewGroup, android.view.View
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        if (keyEvent.getAction() == 0 && keyEvent.getKeyCode() == 23) {
            showKeyboardIfInput();
        }
        return super.dispatchKeyEvent(keyEvent);
    }

    @Override // android.webkit.WebView, android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getAction() == 0) {
            this.lastDownX = motionEvent.getX();
            this.lastDownY = motionEvent.getY();
        }
        if (motionEvent.getAction() == 1 && Math.abs(this.lastDownX - motionEvent.getX()) < this.TOUCH_TOLERANCE && Math.abs(this.lastDownY - motionEvent.getY()) < this.TOUCH_TOLERANCE) {
            showKeyboardIfInput();
        }
        return super.onTouchEvent(motionEvent);
    }

    public void playNextTrack() {
        evaluateJavascript("(function(){var shorts=location.pathname.indexOf('/shorts/')===0;var selector=shorts?'#navigation-button-down button,button[aria-label=\"Next video\"]':'.ytp-next-button,a[aria-label=\"Play next video\"]';var next=Array.from(document.querySelectorAll(selector)).find(function(b){return !b.disabled&&b.getBoundingClientRect().width>0;});if(next){next.click();return;}var p=document.querySelector('#movie_player,.html5-video-player');if(!shorts&&p&&p.getPlaylist&&p.getPlaylist()){p.nextVideo();return;}if(!shorts){var related=document.querySelector('ytm-item-section-renderer[section-identifier=\"related-items\"] ytm-video-with-context-renderer a[href*=\"/watch?\"],ytm-compact-video-renderer a[href*=\"/watch?\"]');if(related){related.click();return;}}var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v&&isFinite(v.duration))v.currentTime=v.duration;})()",null);
    }

    public void pauseVideo() {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})()",null);
    }

    public void playPreviousTrack() {
        evaluateJavascript("(function(){if(location.pathname.indexOf('/shorts/')===0){var shortPrev=document.querySelector('#navigation-button-up button,button[aria-label=\"Previous video\"]');if(shortPrev&&!shortPrev.disabled){shortPrev.click();return;}}var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v&&v.currentTime>3){v.currentTime=0;return;}var prev=document.querySelector('.ytp-prev-button');if(prev&&!prev.disabled){prev.click();return;}history.back();})()",null);
    }

    public void togglePlayback() {
        evaluateJavascript("(function(){var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(!v)return;if(v.paused){var p=v.play();if(p)p.catch(function(){});}else v.pause();})()",null);
    }

    public void playVideo() {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v){var p=v.play();if(p)p.catch(function(){});}})()",null);
    }

    public void scrollActiveElementIntoView(boolean z) {
        loadUrl("javascript:setTimeout(function() { const element = document.activeElement;\nconst elementRect = element.getBoundingClientRect();\nconst absoluteElementTop = elementRect.top + window.pageYOffset;\nconst middle = absoluteElementTop - (window.innerHeight / 2);\nwindow.scrollTo(0, middle);},500);");
    }

    // Each request gets a token, an exit or a newer request makes pending retries of an older one stop
    private int fullscreenToken;

    @Override public void exitFullScreen() { fullscreenToken++; super.exitFullScreen(); }

    public void requestFullScreen() { requestFullScreen(++fullscreenToken, 0); }

    private void requestFullScreen(int token, int attempt) {
        if (isVideoFullscreen() || token != fullscreenToken) return;
        // A page that is still loading can swallow the activation touch, so the request is checked and repeated twice
        postDelayed(() -> { if (token == fullscreenToken && !isVideoFullscreen() && attempt < 2) requestFullScreen(token, attempt + 1); }, 900);
        // Modern Chromium requires a real WebView touch for fullscreen activation.
        // Forward the native toolbar press to a short-lived DOM button.
        // The player's own video, a lingering preview from a results page can come first in the document
        evaluateJavascript("(function(){var v=(window.tubeActiveVideo&&window.tubeActiveVideo())||document.querySelector('#movie_player video,.html5-video-player video')||document.querySelector('video');if(!v||document.fullscreenElement)return null;var b=document.createElement('button');b.id='tube-fullscreen-activation';b.style.cssText='position:fixed;left:8px;top:8px;width:40px;height:40px;z-index:2147483647;opacity:0.01';b.onclick=function(e){e.preventDefault();e.stopPropagation();var p=v.requestFullscreen?v.requestFullscreen():v.webkitRequestFullscreen();if(p)p.catch(function(){});b.remove();};document.documentElement.appendChild(b);setTimeout(function(){b.remove();},1500);return {x:28,y:28,width:innerWidth};})()", value -> {
            if (value == null || "null".equals(value)) return;
            try {
                org.json.JSONObject point = new org.json.JSONObject(value);
                float scale = getWidth() / (float) point.getDouble("width");
                float x = (float) point.getDouble("x") * scale;
                float y = (float) point.getDouble("y") * scale;
                long now = android.os.SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                dispatchTouchEvent(down);
                down.recycle();
                postDelayed(() -> {
                    MotionEvent up = MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
                    dispatchTouchEvent(up);
                    up.recycle();
                }, 80);
            } catch (org.json.JSONException ignored) { }
        });
    }

    // Set once a document start listener reports new videos, then only videos already on the page need a scan
    private boolean captureDiscovery;

    public void useCaptureDiscovery() { captureDiscovery = com.sya.tube.web.WebTuning.installMediaDiscovery(this); }

    public void discoverVideoElements() {
        if (captureDiscovery) {
            evaluateJavascript("(function(){if(!window.nativecallbacks)return;var found=false;document.querySelectorAll('video').forEach(function(v){if(v.dataset.tubeDiscovered)return;v.dataset.tubeDiscovered='1';found=true;});if(found)window.nativecallbacks.onVideoDiscovered();})()", null);
            return;
        }
        evaluateJavascript("(function(){if(!window.nativecallbacks)return;function scan(){var found=false;document.querySelectorAll('video').forEach(function(v){if(v.dataset.tubeDiscovered)return;v.dataset.tubeDiscovered='1';found=true;});if(found)window.nativecallbacks.onVideoDiscovered();}scan();if(!window.tubeObserver){var queued=false;window.tubeObserver=new MutationObserver(function(records){if(queued)return;var found=records.some(function(r){return Array.from(r.addedNodes).some(function(n){return n.nodeType===1&&(n.tagName==='VIDEO'||n.querySelector('video'));});});if(!found)return;queued=true;setTimeout(function(){queued=false;scan();},150);});window.tubeObserver.observe(document.documentElement,{childList:true,subtree:true});}})()",null);
    }

    public void playVideoIfPausedFlag() {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&window.pausedDueToMinimize){window.pausedDueToMinimize=false;var p=v.play();if(p)p.catch(function(){});}})()",null);
    }

    public void pauseVideoAndSetFlag() {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&!v.paused){window.pausedDueToMinimize=true;v.pause();}})()",null);
    }

    public void attachVideoListeners() {
        if(videoListenersScript==null) {
            try(java.io.InputStream input=com.thekirankumar.youtubeauto2.application.MyApplication.getInstance().getAssets().open("video-listeners.js")) {
                java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096]; int count;
                while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                videoListenersScript=output.toString("UTF-8");
            } catch(java.io.IOException error) { android.util.Log.w("Tube","Video listeners unavailable",error); return; }
        }
        boolean diagnostic=com.thekirankumar.youtubeauto2.BuildConfig.DEBUG && android.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_playback_diagnostics",false);
        evaluateJavascript("window.tubeDebugPlayback="+diagnostic+";"+videoListenersScript,null);
    }

    public void showPlaybackSettings(Runnable fallback) {
        String click="(function(){var b=document.querySelector('.ytp-settings-button,button[aria-label=\"Playback Settings\"],button.player-settings-icon');if(!b)return false;b.click();return true;})()";
        evaluateJavascript(click, value -> {
            if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeControls","Settings DOM result="+value);
            if ("true".equals(value)) return;
            evaluateJavascript("(function(){var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(!v)return null;var r=v.getBoundingClientRect();return {x:r.left+r.width*.6,y:r.top+r.height*.4,width:innerWidth};})()", point -> {
                Runnable open=() -> evaluateJavascript(click,retried -> {
                    if(!"true".equals(retried)) fallback.run();
                });
                try {
                    org.json.JSONObject p=new org.json.JSONObject(point);
                    float scale=getWidth()/(float)p.getDouble("width");
                    float x=(float)p.getDouble("x")*scale,y=(float)p.getDouble("y")*scale;
                    long now=android.os.SystemClock.uptimeMillis();
                    MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
                    dispatchTouchEvent(down); down.recycle();
                    postDelayed(() -> {
                        MotionEvent up=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);
                        dispatchTouchEvent(up); up.recycle(); postDelayed(open,300);
                    },80);
                } catch(org.json.JSONException error) { fallback.run(); }
            });
        });
    }

    public void findAndClickFirstVideo() {
        evaluateJavascript("(function(){var el=document.querySelector(\"a[href^='/watch']\");if(el)el.click();})()",null);
    }

    public void setAspectRatio(String str) {
        if(!("contain".equals(str)||"fill".equals(str)||"cover".equals(str))) return;
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.style.objectFit='"+str+"';})()",null);
    }

    public void showKeyboardIfInput() {
        evaluateJavascript("setTimeout(function(){var el=document.activeElement;if(!el||!window.nativecallbacks)return;if(el instanceof HTMLInputElement||el.isContentEditable){window.tubeEditingElement=el;window.nativecallbacks.showKeyboard(el.isContentEditable?el.innerText:el.value);}},300)",null);
    }

    public void sendKeyboardEnterEvent() {
        evaluateJavascript("(function(){var el=window.tubeEditingElement||document.activeElement;if(!el)return;var q=(el.value||el.innerText||'').trim();var host=location.hostname;var name=el.name||'';if(q&&(/(^|\\.)youtube\\.com$/.test(host))&&(name==='search_query'||el.type==='search')){location.href='https://www.youtube.com/results?search_query='+encodeURIComponent(q);return;}if(q&&(/(^|\\.)google\\.com$/.test(host))&&(name==='q'||el.type==='search')){location.href='https://www.google.com/search?q='+encodeURIComponent(q);return;}var form=el.form||el.closest('form');if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return;}['keydown','keypress','keyup'].forEach(function(type){el.dispatchEvent(new KeyboardEvent(type,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));});})()",null);
    }

    public void seekBySeconds(int i) {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+"+i+");})()",null);
    }

    public void getCurrentVideoTimeResult() {
        evaluateJavascript("(function(){var v=document.querySelector('video');if(v&&window.nativecallbacks)window.nativecallbacks.onVideoCurrentTimeResult(Math.floor(v.currentTime));})()",null);
    }

    public void enterEditText(String str) {
        evaluateJavascript("(function(){var el=window.tubeEditingElement||document.activeElement;if(!el)return;var value="+org.json.JSONObject.quote(str)+";if(el.isContentEditable)el.innerText=value;else el.value=value;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));})()",null);
    }

    public void applyPreferredQuality(String preference) {
        if(!java.util.Arrays.asList("auto","large","hd720","hd1080").contains(preference)) return;
        // This is the website player's current interface, not the retired iframe quality API.
        evaluateJavascript("(function(){if(!/(^|\\.)youtube\\.com$/.test(location.hostname)||!(location.pathname==='/watch'||location.pathname.indexOf('/shorts/')===0))return;var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');var p=(v&&v.closest('.html5-video-player'))||document.querySelector('#movie_player,.html5-video-player');if(!p||typeof p.getAvailableQualityLevels!=='function')return;var pref="+org.json.JSONObject.quote(preference)+";var levels=p.getAvailableQualityLevels();if(!levels||!levels.length)return;var chosen='auto';if(pref!=='auto'){var order=['tiny','small','medium','large','hd720','hd1080','hd1440','hd2160'];var cap=order.indexOf(pref);for(var i=cap;i>=0;i--)if(levels.indexOf(order[i])>=0){chosen=order[i];break;}}var key=location.href+'|'+pref+'|'+chosen;if(p.tubeQualityKey===key)return;p.tubeQualityKey=key;try{if(typeof p.setPlaybackQualityRange==='function')p.setPlaybackQualityRange(chosen,chosen);if(typeof p.setPlaybackQuality==='function')p.setPlaybackQuality(chosen);}catch(e){p.tubeQualityKey=null;}})()",null);
    }
}
