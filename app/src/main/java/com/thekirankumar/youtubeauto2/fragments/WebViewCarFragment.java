// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.speech.SpeechRecognizer;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.github.zagum.speechrecognitionview.RecognitionProgressView;
import com.google.android.exoplayer2.C;
import com.thekirankumar.youtubeauto2.activity.MainCarActivity;
import com.thekirankumar.youtubeauto2.bookmarks.Bookmark;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarkUtils;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarksFragment;
import com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment;
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService;
import com.thekirankumar.youtubeauto2.utils.AdBlocker;
import com.thekirankumar.youtubeauto2.utils.CarEditText;
import com.sya.tube.legacy.SearchController;
import com.sya.tube.legacy.SearchCallback;
import com.sya.tube.legacy.SearchItem;
import com.thekirankumar.youtubeauto2.utils.MyRecognitionListener;
import com.thekirankumar.youtubeauto2.utils.SearchMode;
import com.thekirankumar.youtubeauto2.utils.WebviewUtils;
import com.thekirankumar.youtubeauto2.webview.JavascriptCallback;
import com.thekirankumar.youtubeauto2.webview.VideoEnabledWebChromeClient;
import com.thekirankumar.youtubeauto2.webview.VideoWebView;
import com.thekirankumar.youtubeauto2.webview.PlaybackOwner;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.thekirankumar.youtubeauto2.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;

/* JADX INFO: loaded from: classes.dex */
public class WebViewCarFragment extends CarFragment implements MainCarActivity.ActivityCallbacks, JavascriptCallback.JSCallbacks, SafetyWarningFragment.FragmentInteractionListener, BookmarksClickCallback, ExoPlayerFragment.OnFragmentInteractionListener, com.sya.tube.remote.CarRemote.Car, com.sya.tube.web.WebSegments.Listener {
    private static final String ASPECT_RATIO_KEY = "aspect_ratio";
    public static final int AUTOSUGGEST_DEBOUNCE_DELAY_MILLIS = 500;
    public static final String BOOKMARKS_FRAGMENT_TAG = "bookmarks";
    private static final String FULLSCREEN_KEY = "fullscreen";
    public static final String GOOGLE_AUTOSUGGEST_URL_BASE = "https://suggestqueries.google.com/complete/search?client=chrome&q=";
    public static final String GOOGLE_SEARCH_URL_BASE = "https://www.google.com/search?q=";
    public static final String HOME_URL = "home_url";
    public static final String JAVASCRIPT_INTERFACE = "nativecallbacks";
    private static final String PLAYER_FRAGMENT_TAG = "player";
    public static final String PREFS = "car";
    public static final String SAFETY_WARNING_FRAGMENT_TAG = "safety";
    private static final String TAG = "WebViewCarFragment";
    public static final String YOUTUBE_AUTOSUGGEST_URL_BASE = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=";
    public static final String YOUTUBE_HOME_URL_BASE = "https://www.youtube.com";
    public static final String YOUTUBE_OFFLINE_URL_BASE = "file:///" + Environment.getExternalStorageDirectory().getPath() + "/";
    public static final String YOUTUBE_SEARCH_URL_BASE = "https://www.youtube.com/results?search_query=";
    private com.sya.tube.legacy.CarUiHost carHost;
    public void setCarHost(com.sya.tube.legacy.CarUiHost value) { carHost=value; }
    private Button aspectButton;
    private TextView carSpeedView;
    private boolean clickFirstVideoAfterPageLoad;
    private View cornerControls;
    private AspectRatio currentAspectRatio;
    private Integer currentVideoTime;
    private CarEditText fakeEditText;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean isParkingEngaged;
    private MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    private MediaSessionCompat mediaSessionCompat;
    private boolean nativePlayerActive;
    // True while the native player is being prepared underneath the web fullscreen view
    private boolean nativeHandoffPending;
    private boolean nativeHandoffResumePlaying;
    private int nativeHandoffSyncAttempts;
    private static final long HANDOFF_LEAD_MS=5000;
    // Once streams are resolved only the player's own start remains, a few seconds of lead covers it with buffer to spare
    private static final long HANDOFF_RESOLVED_LEAD_MS=3000;
    private static final long PREFETCH_DELAY_MS=3000;
    // Auto fullscreen runs once per video, and never again for a video the driver chose to watch on the page
    private String autoFullscreenVideoId;
    private String autoFullscreenDeclinedId;
    private String handoffRetryVideoId;
    private long handoffBufferWaitStart;
    // Where the web video was at the last handoff check and when it last moved, a web video that stops moving ends the wait
    private long handoffLastWeb=-2, handoffWebMovedAt;
    private int nativeStartAttempts;
    private long handoffPrepareAt;
    private String leftVideoId;
    private long leftPositionMs;
    // The video the native player was started for, pending or active
    private String nativeVideoId;
    private String unmutedVideoId;
    // Read on the WebView's network thread
    private volatile boolean blockAds=true;
    // A video sent from the phone always goes fullscreen, even with auto fullscreen turned off
    private String remoteFullscreenId;
    // YouTube marks the player while an ad plays, the native player has no ads so it must only take over the real video
    private static final String AD_SELECTOR=".ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button-container button,[class*=\"skip-ad\"] button,button[class*=\"skip-ad\"],button[class*=\"ad-skip\"]";
    private static final String AD_STATE_JS="(()=>{let p=document.querySelector('#movie_player,.html5-video-player');let ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));let s=ad?document.querySelector('"+AD_SELECTOR+"'):null;let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');return {ad:ad,playing:!!(v&&!v.paused),skip:!!(s&&!s.disabled&&!s.closest('[style*=\"display: none\"],[hidden]'))}})()";
    private android.widget.Button skipAdButton;
    private boolean adWatchPosted, autoAdWaitPosted, nativeAfterAdPosted;
    private final Runnable adWatch=()->{adWatchPosted=false;watchAds();};
    private ExoPlayerFragment nativePlayerFragment;
    private ProgressBar progressBar;
    private Runnable searchRunnable;
    private SharedPreferences sharedPrefs;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private View toolbar;
    private boolean warningScreenOpen;
    private VideoWebView webView;
    private BroadcastReceiver playbackReceiver;
    private boolean isSearchShown = false;
    private String videoQuality = "hd720";
    private String qualityVideoId;
    private SearchMode searchMode = SearchMode.YOUTUBE;
    private Runnable toolbarHideRunnable = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            WebViewCarFragment.this.hideToolbar();
        }
    };
    private boolean isNightMode = true;
    private boolean warningAccepted = true;
    private Runnable cornerControlsHideRunnable = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            WebViewCarFragment.this.hideCornerControls(true);
        }
    };
    private int playerState = 2;
    private final com.sya.tube.legacy.SearchSuggestions suggestions = new com.sya.tube.legacy.SearchSuggestions();
    private long suggestionGeneration;
    private View loadErrorPanel;

    public enum AspectRatio {
        CONTAIN,
        FILL,
        COVER
    }

    @Override // com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks
    public void onJSVideoEvent(String str) {
        debugStage("jsVideo "+str+" native="+nativePlayerActive);
        if(getContext()==null || webView==null || nativePlayerActive) return;
        if("loading".equals(str)){maybeAutoFullscreen(true);return;}
        if("ended".equals(str)&&getContext()!=null&&com.sya.tube.remote.UpNext.size(getContext())>0&&PlaybackOwner.isContentVideo(webView)){playNextVideo();return;}
        if(!PlaybackOwner.isContentVideo(webView)) return;
        if("playing".equals(str)) PlaybackOwner.playing(webView);
        if(!PlaybackOwner.isActive(webView)){debugStage("jsVideo inactive");return;}
        String title=webView.getTitle();
        if("playing".equals(str)) {
            setPlaybackControls(true,true);
            applyVideoQuality();
            applyWebSpeed();
            unmuteAutoplay();
            maybeAutoFullscreen();
            playerState=android.media.session.PlaybackState.STATE_PLAYING;
            com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPlaying(getContext(),title);
        } else if("pause".equals(str)||"ended".equals(str)) {
            setPlaybackControls(true,false);
            playerState=android.media.session.PlaybackState.STATE_PAUSED;
            com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPaused(getContext(),title);
        } else if("waiting".equals(str)) {
            // A new video buffers before it plays, going fullscreen now means the first frame already appears fullscreen
            maybeAutoFullscreen(true);
            playerState=android.media.session.PlaybackState.STATE_BUFFERING;
            com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastLoading(getContext());
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        HandlerThread handlerThread = new HandlerThread("autosuggest");
        this.handlerThread = handlerThread;
        handlerThread.start();
        this.handler = new Handler(this.handlerThread.getLooper());
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
        playbackReceiver=new BroadcastReceiver() {
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                if (intent == null || WebViewCarFragment.this.webView == null || !PlaybackOwner.isActive(WebViewCarFragment.this.webView) || WebViewCarFragment.this.nativePlayerActive) {
                    return;
                }
                long longExtra = intent.getLongExtra(MyMediaBrowserService.ACTION_TYPE, 0L);
                if (longExtra == 4) {
                    WebViewCarFragment.this.webView.playVideo();
                    return;
                }
                if (longExtra == 2 || longExtra == 1) {
                    WebViewCarFragment.this.webView.pauseVideo();
                    return;
                }
                if (longExtra == 32) {
                    WebViewCarFragment.this.playNextVideo();
                    return;
                }
                if (longExtra == 16) {
                    WebViewCarFragment.this.playPreviousVideo();
                } else if (longExtra == PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) {
                    WebViewCarFragment.this.clickFirstVideoAfterPageLoad = true;
                    WebViewCarFragment.this.search(intent.getStringExtra("query"));
                }
            }
        };
        ContextCompat.registerReceiver(context,playbackReceiver,new IntentFilter(MyMediaBrowserService.PLAYER_EVENT),ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.fragment_webview_car, viewGroup, false);
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(final View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        com.sya.tube.remote.CarRemote.attach(this);
        recentStrip=new com.sya.tube.remote.RecentStrip(view.findViewById(R.id.recent_strip),item->{if(webView==null)return;remoteFullscreenId=item.id;webView.loadUrl(item.url());});
        ExoPlayerFragment.keepPlayingWhenHidden=keepAudioWhenHidden();
        startCallWatch();
        startOnlineWatch();
        videoQuality = com.sya.tube.settings.TubePrefs.startQuality(requireContext());
        qualityVideoId = null;
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().remove("pref_video_quality").apply();
        this.webView = (VideoWebView) view.findViewById(R.id.web_view);
        setPlaybackControls(false, false);
        view.findViewById(R.id.native_play_pause).setOnClickListener(v -> {if(nativePlayerActive&&nativePlayerFragment!=null)nativePlayerFragment.toggle();else webView.togglePlayback();});
        view.findViewById(R.id.native_next_video).setOnClickListener(v -> playNextVideo());
        view.findViewById(R.id.native_previous_video).setOnClickListener(v -> playPreviousVideo());
        view.findViewById(R.id.native_video_settings).setOnClickListener(v -> {
            if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) Log.i("TubeControls","Settings clicked");
            // Over native playback the panel opens on top of the video, so audio sync can be tuned while watching
            if(nativePlayerActive){showQualityChoices();return;}
            boolean returning=nativePlayerActive;
            if(returning)leaveNativeVideo(true);
            if(webView.isVideoFullscreen()) webView.exitFullScreen();
            webView.postDelayed(()->webView.showPlaybackSettings(() -> {
                if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) Log.i("TubeControls","Settings fallback");
                if(getView()!=null) showQualityChoices();
            }),returning?1500:0);
        });
        com.sya.tube.legacy.CarUiHost mainCarActivity = carHost;
        CarEditText carEditText = (CarEditText) view.findViewById(R.id.fake_edittext);
        this.fakeEditText = carEditText;
        setupEditTextMirroring(carEditText);
        final SearchController searchController = mainCarActivity.getSearchController();
        this.webView.addJavascriptInterface(new JavascriptCallback(this), JAVASCRIPT_INTERFACE);
        com.sya.tube.web.WebSegments.attach(this.webView,this);
        final Context appContext=requireContext().getApplicationContext();
        view.findViewById(R.id.library_button).setOnClickListener(v->{
            if(nativeHandoffPending)cancelNativeHandoff();
            if(nativePlayerActive)leaveNativeVideo(false);
            if(webView.isVideoFullscreen())webView.exitFullScreen();
            webView.loadUrl(com.sya.tube.library.LibraryPages.HOME);
        });
        mainCarActivity.getCarWindow().setVolumeControlStream(3);
        ImageButton imageButton = (ImageButton) view.findViewById(R.id.home_button);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                if(nativePlayerActive)leaveNativeVideo(false);
                WebViewCarFragment.this.webView.loadUrl(homePage());
            }
        });
        imageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override // android.view.View.OnLongClickListener
            public boolean onLongClick(View view2) {
                if(nativePlayerActive)leaveNativeVideo(false);
                // The old file browser needs storage access Android no longer grants, the Library lists phone videos instead
                WebViewCarFragment.this.webView.loadUrl(com.sya.tube.library.LibraryPages.BASE + "phone");
                return true;
            }
        });
        this.cornerControls = view.findViewById(R.id.fullscreen_corner_controls);
        Button button = (Button) view.findViewById(R.id.aspect_button);
        this.aspectButton = button;
        Button qualityButton=(Button)view.findViewById(R.id.quality_button);
        qualityButton.setOnClickListener(v -> showQualityChoices());
        updateQualityLabel();
        button.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                WebViewCarFragment.this.toggleAspectRatio();
                WebViewCarFragment.this.showAndHideCornerControlsAnimation();
                WebViewCarFragment.this.showAndHideToolbarAnimation();
            }
        });
        this.aspectButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override // android.view.View.OnFocusChangeListener
            public void onFocusChange(View view2, boolean z) {
                WebViewCarFragment.this.showAndHideCornerControlsAnimation();
                WebViewCarFragment.this.showAndHideToolbarAnimation();
            }
        });
        this.progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        ((ImageButton) view.findViewById(R.id.refresh_button)).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                if(nativePlayerActive)leaveNativeVideo(false);
                WebViewCarFragment.this.webView.reload();
            }
        });
        ((ImageButton) view.findViewById(R.id.voice_button)).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                if (WebViewCarFragment.this.speechRecognizer != null) {
                    WebViewCarFragment.this.speechRecognizer.stopListening();
                    WebViewCarFragment.this.speechRecognizer.destroy();
                    WebViewCarFragment.this.speechRecognizer = null;
                    return;
                }
                WebViewCarFragment.this.webView.pauseVideoAndSetFlag();
                if(nativePlayerFragment!=null)nativePlayerFragment.pause();
                final RecognitionProgressView recognitionProgressView = (RecognitionProgressView) view.findViewById(R.id.speech_view);
                recognitionProgressView.setOnClickListener(new View.OnClickListener() {
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view3) {
                        recognitionProgressView.stop();
                        recognitionProgressView.setVisibility(android.view.View.GONE);
                    }
                });
                MyRecognitionListener myRecognitionListener = new MyRecognitionListener(new MyRecognitionListener.OnCompleteListener() {
                    @Override // com.thekirankumar.youtubeauto2.utils.MyRecognitionListener.OnCompleteListener
                    public void onEnd() {
                    }

                    @Override // com.thekirankumar.youtubeauto2.utils.MyRecognitionListener.OnCompleteListener
                    public void onVoiceRecognitionComplete(String str) {
                        WebViewCarFragment.this.search(str);
                        if (WebViewCarFragment.this.speechRecognizer != null) {
                            WebViewCarFragment.this.speechRecognizer.destroy();
                            WebViewCarFragment.this.speechRecognizer = null;
                        }
                        recognitionProgressView.stop();
                        recognitionProgressView.setVisibility(android.view.View.GONE);
                    }

                    @Override // com.thekirankumar.youtubeauto2.utils.MyRecognitionListener.OnCompleteListener
                    public void onError(int i) {
                        recognitionProgressView.stop();
                        recognitionProgressView.setVisibility(android.view.View.GONE);
                        showCarNotice(voiceErrorText(i));
                        if (WebViewCarFragment.this.speechRecognizer != null) {
                            WebViewCarFragment.this.speechRecognizer.destroy();
                            WebViewCarFragment.this.speechRecognizer = null;
                        }
                    }
                });
                Context context = WebViewCarFragment.this.getContext();
                // The car screen cannot show a permission prompt, the grant happens once on the phone
                if (!isRecordAudioGranted()) { showCarNotice("Open Aux on your phone once and allow the microphone, then voice search works here"); return; }
                if (!SpeechRecognizer.isRecognitionAvailable(context)) { showCarNotice("Voice search needs the Google app on the phone"); return; }
                WebViewCarFragment.this.speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                WebViewCarFragment.this.speechRecognizerIntent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
                WebViewCarFragment.this.speechRecognizerIntent.putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form");
                WebViewCarFragment.this.speechRecognizer.startListening(WebViewCarFragment.this.speechRecognizerIntent);
                recognitionProgressView.setVisibility(android.view.View.VISIBLE);
                recognitionProgressView.play();
                recognitionProgressView.setSpeechRecognizer(WebViewCarFragment.this.speechRecognizer);
                recognitionProgressView.setRecognitionListener(myRecognitionListener);
                recognitionProgressView.setColors(new int[]{ContextCompat.getColor(context, R.color.color1), ContextCompat.getColor(context, R.color.color2), ContextCompat.getColor(context, R.color.color3), ContextCompat.getColor(context, R.color.color4), ContextCompat.getColor(context, R.color.color5)});
            }
        });
        handleVoiceVisibility();
        final ImageButton imageButton2 = (ImageButton) view.findViewById(R.id.fullscreen_button);
        imageButton2.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                if(exitVideoFullscreen())return;
                // Fullscreen is requested first so it appears at once, the native handoff then prepares behind it
                if(webView.getUrl()!=null&&webView.getUrl().contains("youtube.com/watch?")){webView.requestFullScreen();startNativeVideo();return;}
                WebViewCarFragment.this.webView.requestFullScreen();
            }
        });
        ((ImageButton) view.findViewById(R.id.search_youtube_button)).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                if (!WebViewCarFragment.this.isSearchShown) {
                    WebViewCarFragment.this.isSearchShown = true;
                    WebViewCarFragment.this.searchMode = SearchMode.YOUTUBE;
                    mainCarActivity.getSearchController().setSearchHint("YouTube Search");
                    mainCarActivity.getSearchController().showSearchBox();
                    return;
                }
                WebViewCarFragment.this.isSearchShown = false;
                mainCarActivity.getSearchController().hideSearchBox();
            }
        });
        ((ImageButton) view.findViewById(R.id.bookmark_button)).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                WebViewCarFragment.this.showBookmarksScreen();
            }
        });
        this.toolbar = view.findViewById(R.id.toolbar);
        View viewFindViewById = view.findViewById(R.id.webview_container);
        ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.full_screen_view);
        WebviewUtils.setDesktopMode(this.webView, PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_enable_desktop_mode", false));
        this.webView.setWebViewClient(new CustomWebViewClient() {
            private final android.util.LruCache<String, Boolean> blockedHosts = new android.util.LruCache<>(128);

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView webView, android.webkit.WebResourceRequest request) { return shouldInterceptRequest(webView,request.getUrl().toString()); }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, String str) {
                WebResourceResponse library=com.sya.tube.library.LibraryPages.intercept(appContext,str);
                if(library!=null) return library;
                WebResourceResponse local=com.thekirankumar.youtubeauto2.utils.LocalFileBrowser.intercept(str);
                if(local!=null) return local;
                String host=android.net.Uri.parse(str).getHost();
                Boolean cached=host==null?null:blockedHosts.get(host);
                boolean zBooleanValue=cached==null?AdBlocker.isAd(str):cached;
                // Store only blocked hosts: the host list loads asynchronously and a
                // negative result during startup must not prevent later filtering.
                if(zBooleanValue&&host!=null) blockedHosts.put(host,true);
                // Path matches stay out of the host cache, YouTube's own host also serves the videos
                if (zBooleanValue || blockAds && com.sya.tube.web.YouTubeAdBlock.isAdRequest(str)) {
                    return AdBlocker.createEmptyResource();
                }
                return super.shouldInterceptRequest(webView, str);
            }
        });
        VideoEnabledWebChromeClient videoEnabledWebChromeClient = new VideoEnabledWebChromeClient(viewFindViewById, viewGroup, new ProgressBar(getContext()), this.webView) {
            @Override public void onReceivedTitle(WebView view,String title) {
                super.onReceivedTitle(view,title);
                if(getContext()!=null && WebViewCarFragment.this.webView!=null && PlaybackOwner.isActive(WebViewCarFragment.this.webView) && PlaybackOwner.isContentVideo(WebViewCarFragment.this.webView))
                    com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadcastState(getContext(),title,playerState);
            }
        };
        this.webView.setWebChromeClient(videoEnabledWebChromeClient);
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.getSettings().setAllowFileAccess(true);
        this.webView.getSettings().setDomStorageEnabled(true);
        this.webView.getSettings().setDatabaseEnabled(true);
        this.webView.getSettings().setSupportZoom(true);
        this.webView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        this.webView.getSettings().setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= 16) {
            this.webView.getSettings().setAllowFileAccessFromFileURLs(false);
        }
        this.webView.getSettings().setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 17) {
            this.webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }
        this.webView.getSettings().setAllowFileAccess(true);
        // The web player only bridges the first seconds before the native player takes over with its own H.264 stream, and
        // forcing H.264 here measurably slows that start, so YouTube picks the codec unless the setting asks otherwise
        com.sya.tube.web.WebTuning.apply(this.webView, PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_prefer_h264", false));
        if(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_car_clean", true)) com.sya.tube.web.WebTuning.applyCarClean(this.webView);
        if(keepAudioWhenHidden()) com.sya.tube.web.WebTuning.applyAlwaysVisible(this.webView);
        this.webView.useCaptureDiscovery();
        blockAds=PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_block_ads", true);
        com.sya.tube.web.YouTubeAdBlock.apply(this.webView, blockAds);
        if (this.isNightMode) {
            this.webView.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        }
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                final String string = WebViewCarFragment.this.getSharedPrefs().getString(WebViewCarFragment.HOME_URL, WebViewCarFragment.YOUTUBE_HOME_URL_BASE);
                final int i = WebViewCarFragment.this.getSharedPrefs().getInt(WebViewCarFragment.ASPECT_RATIO_KEY, 0);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override // java.lang.Runnable
                    public void run() {
                        WebViewCarFragment.this.webView.loadUrl(string);
                        // Fullscreen belongs to the current video view, not a saved button preference.
                        WebViewCarFragment.this.getSharedPrefs().edit().remove(FULLSCREEN_KEY).apply();
                        if (AspectRatio.values().length > i) {
                            WebViewCarFragment.this.setAspectRatio(AspectRatio.values()[i]);
                        }
                    }
                });
            }
        });
        this.webView.requestFocus();
        this.webView.setOnKeyListener(new View.OnKeyListener() {
            @Override // android.view.View.OnKeyListener
            public boolean onKey(View view2, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == 1) {
                    if (i != 4) {
                        if (i == 19 && WebViewCarFragment.this.webView.isVideoFullscreen()) {
                            WebViewCarFragment.this.showAndHideToolbarAnimation();
                            WebViewCarFragment.this.showAndHideCornerControlsAnimation();
                            return true;
                        }
                    } else if (!backHandledByHost && WebViewCarFragment.this.goBack()) {
                        return true;
                    }
                }
                return false;
            }
        });
        searchController.setSearchCallback(new C094220(mainCarActivity, view, searchController));
        videoEnabledWebChromeClient.setOnToggledFullscreen(new VideoEnabledWebChromeClient.ToggledFullscreenCallback() {
            @Override // com.thekirankumar.youtubeauto2.webview.VideoEnabledWebChromeClient.ToggledFullscreenCallback
            public void toggledFullscreen(boolean z) {
                // Leaving web fullscreen for the native player is not a return to the page
                if (!z && (WebViewCarFragment.this.nativePlayerActive || WebViewCarFragment.this.nativeHandoffPending)) return;
                imageButton2.setActivated(z);
                imageButton2.setImageResource(z ? R.drawable.tube_ic_fullscreen_exit : R.drawable.tube_ic_fullscreen);
                imageButton2.setContentDescription(z ? "Exit fullscreen" : "Fullscreen");
                if (z) WebViewCarFragment.this.scheduleAdWatch(300);
                else WebViewCarFragment.this.showSkipAd(false);
                if (!z) {
                    WebViewCarFragment.this.showToolbar();
                    WebViewCarFragment.this.hideCornerControls(false);
                    return;
                }
                searchController.hideSearchBox();
                WebViewCarFragment.this.hideToolbar();
                WebViewCarFragment.this.hideCornerControls(false);
            }
        });
        videoEnabledWebChromeClient.setVideoTouchListener(new View.OnTouchListener() {
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view2, MotionEvent motionEvent) {
                if (motionEvent.getAction() != 0) {
                    return false;
                }
                // Presentation dispatch handles car surface touches once, including Chromium
                // custom views without a focused child. Do not toggle those controls again.
                if (mainCarActivity instanceof com.sya.tube.car.CarPresentation) {
                    return false;
                }
                WebViewCarFragment.this.toggleToolbarAnimation();
                WebViewCarFragment.this.toggleCornerControlsAnimation();
                return false;
            }
        });
        mainCarActivity.addActivityCallback(this);
    }

    /* JADX INFO: renamed from: com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment$20 */
    class C094220 implements SearchCallback {
        final /* synthetic */ com.sya.tube.legacy.CarUiHost val$carUiController;
        final /* synthetic */ SearchController val$searchController;
        final /* synthetic */ View val$view;

        C094220(com.sya.tube.legacy.CarUiHost carUiController, View view, SearchController searchController) {
            this.val$carUiController = carUiController;
            this.val$view = view;
            this.val$searchController = searchController;
        }

        @Override // com.google.android.apps.auto.sdk.SearchCallback
        public void onSearchItemSelected(SearchItem searchItem) {
            WebViewCarFragment.this.search(String.valueOf(searchItem.getTitle()));
            WebViewCarFragment.this.isSearchShown = false;
            this.val$carUiController.getSearchController().hideSearchBox();
        }

        @Override // com.google.android.apps.auto.sdk.SearchCallback
        public boolean onSearchSubmitted(String str) {
            WebViewCarFragment.this.search(str);
            WebViewCarFragment.this.isSearchShown = false;
            this.val$carUiController.getSearchController().hideSearchBox();
            return true;
        }

        @Override public void onSearchClosed() {
            WebViewCarFragment.this.isSearchShown=false;
            suggestionGeneration++;
            suggestions.cancel();
            handler.removeCallbacks(searchRunnable);
        }

        @Override // com.google.android.apps.auto.sdk.SearchCallback
        public void onSearchTextChanged(final String str) {
            handler.removeCallbacks(searchRunnable);
            suggestions.cancel();
            final long generation = ++suggestionGeneration;
            if (str.trim().isEmpty()) { val$searchController.setSearchItems(java.util.Collections.emptyList()); return; }
            final String base = searchMode == SearchMode.YOUTUBE ? YOUTUBE_AUTOSUGGEST_URL_BASE : GOOGLE_AUTOSUGGEST_URL_BASE;
            searchRunnable = () -> suggestions.load(base, str, items -> val$view.post(() -> {
                if (getView()!=null && isSearchShown && generation==suggestionGeneration)
                    val$searchController.setSearchItems(items);
            }));
            handler.postDelayed(searchRunnable, 300L);
        }
    }

    private void setupEditTextMirroring(final CarEditText carEditText) {
        carEditText.addTextChangedListener(new TextWatcher() {
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                WebViewCarFragment.this.webView.enterEditText(carEditText.getText().toString());
            }
        });
        carEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != android.view.inputmethod.EditorInfo.IME_ACTION_DONE && i != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH && (keyEvent == null || keyEvent.getKeyCode()!=KeyEvent.KEYCODE_ENTER || keyEvent.getAction()!=KeyEvent.ACTION_UP)) {
                    return false;
                }
                WebViewCarFragment.this.webView.sendKeyboardEnterEvent();
                WebViewCarFragment.this.onHideKeyboardFromJS();
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleAspectRatio() {
        int iOrdinal = this.currentAspectRatio.ordinal();
        setAspectRatio(AspectRatio.values()[iOrdinal >= AspectRatio.values().length + (-1) ? 0 : iOrdinal + 1]);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public SharedPreferences getSharedPrefs() {
        SharedPreferences sharedPreferences = this.sharedPrefs;
        if (sharedPreferences != null) {
            return sharedPreferences;
        }
        SharedPreferences sharedPreferences2 = getContext().getSharedPreferences(PREFS, 0);
        this.sharedPrefs = sharedPreferences2;
        return sharedPreferences2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void search(String str) {
        if(webView==null) return;
        String url=com.thekirankumar.youtubeauto2.utils.SearchUrls.forQuery(str,searchMode==SearchMode.YOUTUBE);
        if(url==null) return;
        if(webView.isVideoFullscreen()) webView.exitFullScreen();
        if(nativePlayerActive) hideNativePlayer();
        this.webView.loadUrl(url);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean goBack() {
        if (exitVideoFullscreen()) return true;
        if (!this.webView.canGoBack() || !this.webView.hasFocus()) {
            return false;
        }
        this.webView.goBack();
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideToolbar() {
        this.toolbar.animate().cancel();
        this.toolbar.clearAnimation();
        this.toolbar.setTranslationY(-this.toolbar.getMeasuredHeight());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideCornerControls(boolean z) {
        this.cornerControls.clearFocus();
        if (z) {
            this.cornerControls.setVisibility(android.view.View.VISIBLE);
            this.cornerControls.animate().cancel();
            this.cornerControls.clearAnimation();
            this.cornerControls.setTranslationX(this.cornerControls.getMeasuredWidth());
            return;
        }
        this.cornerControls.animate().cancel();
        this.cornerControls.setTranslationX(500.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showToolbar() {
        this.toolbar.setVisibility(android.view.View.VISIBLE);
        this.toolbar.removeCallbacks(this.toolbarHideRunnable);
        // Surface-only playback must not depend on another animation frame to reveal controls.
        this.toolbar.animate().cancel();
        this.toolbar.clearAnimation();
        this.toolbar.setTranslationY(0.0f);
    }

    public boolean onCarSurfaceTouch(float x,float y) {
        if(webView==null || toolbar==null || !(webView.isVideoFullscreen()||nativePlayerActive)) return false;
        ViewGroup overlay=(ViewGroup)getView().findViewById(R.id.overlay_container);
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) Log.d("TubeCarSurface","fullscreen touch "+x+","+y+" overlay="+overlay.getChildCount()+" toolbar="+toolbar.getTranslationY()+" corner="+cornerControls.getTranslationX());
        // The skip ad button lives in the overlay but must not block the toolbar reveal for the rest of the screen
        if(skipAdButton!=null&&skipAdButton.getParent()==overlay) {
            android.graphics.Rect skip=new android.graphics.Rect();
            if(skipAdButton.getGlobalVisibleRect(skip)&&skip.contains((int)x,(int)y)) return false;
            if(overlay.getChildCount()>1) return false;
        } else if(overlay.getChildCount()>0) return false;
        if(nativePlayerActive&&toolbar.getTranslationY()==0)return false;
        android.graphics.Rect controls=new android.graphics.Rect();
        if(cornerControls.getVisibility()==View.VISIBLE && cornerControls.getTranslationX()==0 && cornerControls.getGlobalVisibleRect(controls) && controls.contains((int)x,(int)y)) return false;
        android.graphics.Rect area=new android.graphics.Rect();
        toolbar.getGlobalVisibleRect(area);
        if(toolbar.getTranslationY()<0 || y>area.bottom) {
            showAndHideToolbarAnimation();
            showAndHideCornerControlsAnimation();
            if(nativePlayerActive&&nativePlayerFragment!=null)nativePlayerFragment.revealControls();
            // Revealing chrome should not also pause the HTML video underneath it.
            return true;
        }
        return false;
    }

    private void applyVideoQuality() {
        if(webView!=null) webView.applyPreferredQuality(videoQuality);
    }

    private void setPlaybackControls(boolean enabled, boolean playing) {
        if(getView()==null) return;
        for(int id:new int[]{R.id.native_play_pause,R.id.native_next_video,R.id.native_previous_video,R.id.fullscreen_button,R.id.native_video_settings}) {
            View control=getView().findViewById(id);
            control.setEnabled(enabled);
            control.setAlpha(enabled?1f:.45f);
        }
        ImageButton control=getView().findViewById(R.id.native_play_pause);
        control.setImageResource(playing?R.drawable.ic_video_pause:R.drawable.ic_video_play);
        control.setContentDescription(playing?"Pause video":"Play video");
    }

    private void clearLoadError() {
        if(loadErrorPanel!=null) {
            android.view.ViewParent parent=loadErrorPanel.getParent();
            if(parent instanceof ViewGroup) ((ViewGroup)parent).removeView(loadErrorPanel);
            loadErrorPanel=null;
        }
    }

    // A page that failed for lack of signal loads again by itself the moment the phone is back online
    private String retryWhenOnline;
    private android.net.ConnectivityManager.NetworkCallback onlineWatch;
    private boolean isOnline() {
        if(getContext()==null)return true;
        android.net.ConnectivityManager connectivity=getContext().getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities caps=connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private void startOnlineWatch() {
        if(getContext()==null||onlineWatch!=null)return;
        onlineWatch=new android.net.ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(android.net.Network network){
                if(webView!=null)webView.postDelayed(()->{
                    if(webView==null||retryWhenOnline==null)return;
                    String url=retryWhenOnline;retryWhenOnline=null;
                    com.sya.tube.diag.Diag.log("signal","back online, loading "+url);
                    clearLoadError();
                    String id=com.sya.tube.remote.CarRemote.videoId(url);
                    if(id!=null)remoteFullscreenId=id;
                    webView.loadUrl(url);
                },1500);
            }
        };
        try { getContext().getSystemService(android.net.ConnectivityManager.class).registerDefaultNetworkCallback(onlineWatch); } catch(RuntimeException ignored) { onlineWatch=null; }
    }
    private void stopOnlineWatch() {
        if(onlineWatch==null||getContext()==null)return;
        try { getContext().getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(onlineWatch); } catch(RuntimeException ignored) {}
        onlineWatch=null;
    }

    private void showLoadError(String url) {
        if(getView()==null) return;
        clearLoadError();
        retryWhenOnline=url;
        progressBar.setVisibility(View.GONE);
        android.widget.LinearLayout panel=new android.widget.LinearLayout(getContext());
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setBackground(com.sya.tube.car.CarStyle.card(getContext()));
        int padding=com.sya.tube.car.CarStyle.dp(getContext(),24);
        panel.setPadding(padding,padding,padding,padding);
        TextView heading=com.sya.tube.car.CarStyle.text(getContext(),"Can't load this page",22,true);
        heading.setGravity(android.view.Gravity.CENTER);
        panel.addView(heading);
        TextView message=com.sya.tube.car.CarStyle.text(getContext(),"Check your connection, then try again",16,false);
        message.setGravity(android.view.Gravity.CENTER);
        message.setPadding(0,com.sya.tube.car.CarStyle.dp(getContext(),6),0,com.sya.tube.car.CarStyle.dp(getContext(),18));
        panel.addView(message);
        Button retry=com.sya.tube.car.CarStyle.button(getContext(),"Try again",true);
        retry.setOnClickListener(v -> { clearLoadError(); if(webView!=null) webView.loadUrl(url); });
        panel.addView(retry,new android.widget.LinearLayout.LayoutParams(com.sya.tube.car.CarStyle.dp(getContext(),220),-2));
        android.widget.FrameLayout overlay=getView().findViewById(R.id.overlay_container);
        int width=Math.min((int)(460*getResources().getDisplayMetrics().density),Math.max(1,getView().getWidth()-padding*2));
        overlay.addView(panel,new android.widget.FrameLayout.LayoutParams(width,-2,android.view.Gravity.CENTER));
        loadErrorPanel=panel;
    }

    private void resetQualityForVideo(String url) {
        if(url==null) return;
        dropNativeForOtherVideo(url);
        forgetVideoMarks(url);
        android.net.Uri uri=android.net.Uri.parse(url);
        String host=uri.getHost();
        if(host==null || !(host.equals("youtube.com") || host.endsWith(".youtube.com"))) return;
        String path=uri.getPath();
        String id="/watch".equals(path)?uri.getQueryParameter("v"):(path!=null&&path.startsWith("/shorts/")?path.substring(8):null);
        if(id!=null && !id.equals(qualityVideoId)) {
            qualityVideoId=id;
            // The weak signal step only ever lowers the quality the network setting asks for
            String start=getContext()==null?"hd720":com.sya.tube.settings.TubePrefs.startQuality(getContext());
            videoQuality=android.os.SystemClock.elapsedRealtime()<weakSignalUntil&&weakQuality!=null&&qualityHeight(weakQuality)<qualityHeight(start)?weakQuality:start;
            if("/watch".equals(path))lookupSegments(id);
            updateQualityLabel();
            applyVideoQuality();
            // Native fullscreen reuses this lookup, so pressing fullscreen no longer waits several seconds for stream extraction,
            // it starts after the web player has had time to begin so the two do not compete, a fullscreen press before then resolves directly
            if("/watch".equals(path)&&webView!=null) {
                final String videoId=id;
                webView.postDelayed(()->{ if(videoId.equals(qualityVideoId)) com.sya.tube.player.YoutubeStreams.prefetch(url,qualityHeight(videoQuality)); },PREFETCH_DELAY_MS);
                // Next and previous change the address after the new video has already started, so its playing event can carry the old id
                for(long delay:new long[]{1200,3500}) webView.postDelayed(()->{ if(videoId.equals(qualityVideoId)) maybeAutoFullscreen(); },delay);
            }
        }
    }

    private void updateQualityLabel() {
        if(getView()==null) return;
        String quality=videoQuality;
        ((Button)getView().findViewById(R.id.quality_button)).setText("Quality: "+qualityName(quality));
    }

    /**
     * A native player belongs to one video, when the page moves on (home, search, a pick from the phone, autoplay) a handoff still
     * preparing is cancelled and an active one is closed, otherwise it would finish later and replace the new video with the old one
     */
    private void dropNativeForOtherVideo(String url) {
        if(nativeVideoId==null||!(nativeHandoffPending||nativePlayerActive))return;
        android.net.Uri uri=android.net.Uri.parse(url);
        String id="/watch".equals(uri.getPath())?uri.getQueryParameter("v"):null;
        if(nativeVideoId.equals(id))return;
        debugStage("nativeDropped for "+(id==null?uri.getPath():id)+" was "+nativeVideoId);
        if(nativeHandoffPending){cancelNativeHandoff();return;}
        // The page already shows something else, so nothing is seeked or resumed on it
        hideNativePlayer();
        if(getView()!=null){ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(false);button.setImageResource(R.drawable.tube_ic_fullscreen);button.setContentDescription("Fullscreen");}
        showToolbar();hideCornerControls(false);
    }

    /** Remembers the current video at its current time as the page Tube opens with next, then runs done */
    public void saveResumePoint(Runnable done) {
        String id=watchVideoId();
        if(id==null||webView==null||getContext()==null){done.run();return;}
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.LongConsumer save=ms->{
            if(!finished.compareAndSet(false,true))return;
            String url="https://m.youtube.com/watch?v="+id+(ms>5000?"&t="+(ms/1000)+"s":"");
            if(getContext()!=null)getSharedPrefs().edit().putString(HOME_URL,url).apply();
            debugStage("resume point "+url);
            done.run();
        };
        if(nativePlayerActive&&nativePlayerFragment!=null){save.accept(nativePlayerFragment.position());return;}
        // A page out of view for a minute may not answer, the time read when it left the screen is used then
        long cached=id.equals(leftVideoId)?leftPositionMs:0;
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');return v?v.currentTime:0})()",value->{
            long ms=cached;
            try { ms=Math.max(cached,(long)(Double.parseDouble(value)*1000)); } catch(Exception ignored) {}
            save.accept(ms);
        });
        webView.postDelayed(()->save.accept(cached),1500);
    }

    /** Read while the page is still live, the moment the car screen goes out of view, and saved as the place to continue from */
    private void rememberLeavingPosition() {
        String id=remoteVideoId();
        leftVideoId=id;
        if(id==null||webView==null)return;
        if(nativePlayerActive&&nativePlayerFragment!=null){leftPositionMs=nativePlayerFragment.position();writeResumePoint(id,leftPositionMs);return;}
        leftPositionMs=id.equals(trackedVideoId)?trackedPositionMs:0;
        writeResumePoint(id,leftPositionMs);
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');return v?v.currentTime:0})()",value->{
            try { long ms=(long)(Double.parseDouble(value)*1000); if(ms>leftPositionMs){leftPositionMs=ms;writeResumePoint(id,ms);} } catch(Exception ignored) {}
        });
    }

    private void writeResumePoint(String id,long ms) {
        if(getContext()==null||id==null)return;
        // A playlist keeps its place, so turning the car back on continues the list in order
        String list=null,index=null;
        if(webView!=null&&webView.getUrl()!=null){android.net.Uri page=android.net.Uri.parse(webView.getUrl());if(id.equals(page.getQueryParameter("v"))){list=page.getQueryParameter("list");index=page.getQueryParameter("index");}}
        getSharedPrefs().edit().putString(HOME_URL,"https://m.youtube.com/watch?v="+id+(list!=null?"&list="+list+(index!=null?"&index="+index:""):"")+(ms>5000?"&t="+(ms/1000)+"s":"")).apply();
    }

    // The watchdog's view of the current video, turning the car off tears the car screen down at once, so the place to
    // continue from is kept current while a video plays
    private String trackedVideoId,watchedId;
    private long trackedPositionMs,watchedPos=-1,stalledMs,watchedSince,lastSeekAt,lastResumeWrite;
    private final java.util.ArrayDeque<Long> stallTimes=new java.util.ArrayDeque<>();
    // A weak signal lowers the quality for a while, later videos start lower too until five minutes pass without trouble
    private long weakSignalUntil;
    private String weakQuality;
    private boolean recoveredOnce;
    // A live stream has nothing to continue later, it stays off the recent list
    private boolean liveVideo;
    private static final String WATCH_JS="(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');let p=document.querySelector('#movie_player,.html5-video-player');return {title:document.title.replace(/ - YouTube$/,''),playing:!!(v&&!v.paused&&!v.ended),pos:v?v.currentTime:0,dur:v&&isFinite(v.duration)?v.duration:0,ad:!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'))),live:!!(p&&p.getVideoData&&p.getVideoData().isLive)}})()";

    /**
     * Every two seconds while the car shows a video, it keeps the resume point, the recent list and the phone's media controls
     * current, and catches a picture that froze or keeps stalling on a weak signal
     */
    private final Runnable playbackWatchdog=new Runnable() {
        @Override public void run() {
            if(webView==null||getContext()==null)return;
            webView.postDelayed(this,2000);
            if(nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.isMedia()){
                boolean playing=nativePlayerFragment.playing()&&nativePlayerFragment.state()!=com.google.android.exoplayer2.Player.STATE_ENDED;
                com.thekirankumar.youtubeauto2.service.MyMediaBrowserService.publishCar(getContext(),nativePlayerFragment.title(),playing,nativePlayerFragment.position(),nativePlayerFragment.duration());
                long now=android.os.SystemClock.elapsedRealtime();
                observedPlaying=playing;observedAt=now;if(playing)lastPlayingAt=now;
                updateChapterData(null);
                return;
            }
            String id=remoteVideoId();
            if(id==null){watchedId=null;com.thekirankumar.youtubeauto2.service.MyMediaBrowserService.publishCar(getContext(),null,false,0,0);updateChapterData(null);return;}
            updateChapterData(id);
            if(nativePlayerActive&&nativePlayerFragment!=null){
                liveVideo=false;
                boolean ended=nativePlayerFragment.state()==com.google.android.exoplayer2.Player.STATE_ENDED;
                watch(id,nativePlayerFragment.title(),nativePlayerFragment.position(),nativePlayerFragment.duration(),nativePlayerFragment.playing()&&!ended,false);
                return;
            }
            webView.evaluateJavascript(WATCH_JS,value->{
                try {
                    org.json.JSONObject s=new org.json.JSONObject(value);
                    if(id.equals(remoteVideoId())&&!nativePlayerActive){liveVideo=s.optBoolean("live");watch(id,s.optString("title"),(long)(s.optDouble("pos")*1000),(long)(s.optDouble("dur")*1000),s.optBoolean("playing"),s.optBoolean("ad"));}
                } catch(Exception ignored) {}
            });
        }
    };

    private void watch(String id,String title,long pos,long dur,boolean playing,boolean ad) {
        if(getContext()==null)return;
        long now=android.os.SystemClock.elapsedRealtime();
        com.thekirankumar.youtubeauto2.service.MyMediaBrowserService.publishCar(getContext(),title,playing,pos,dur);
        observedPlaying=playing;observedAt=now;
        // Playback that stops just as the signal goes is picked up again at the same spot once the phone is back online
        if(playing)lastPlayingAt=now;
        else if(retryWhenOnline==null&&now-lastPlayingAt<15_000&&id.equals(trackedVideoId)&&!isOnline()){
            writeResumePoint(id,trackedPositionMs);
            retryWhenOnline=getSharedPrefs().getString(HOME_URL,null);
            com.sya.tube.diag.Diag.log("signal","lost signal at "+trackedPositionMs/1000+"s");
            showCarNotice("No signal, the video continues when it returns");
        }
        if(!id.equals(watchedId)){watchedId=id;watchedSince=now;watchedPos=pos;stalledMs=0;recoveredOnce=false;}
        // An ad runs on its own clock, it says nothing about the video
        if(ad){watchedPos=pos;stalledMs=0;return;}
        if(playing&&pos>0){
            trackedVideoId=id;trackedPositionMs=pos;
            if(now-lastResumeWrite>=10_000){lastResumeWrite=now;writeResumePoint(id,pos);if(!liveVideo)com.sya.tube.remote.RecentVideos.update(getContext(),id,title,pos,dur);}
        }
        // A new video or a seek buffers on purpose, only playback that should be moving counts
        boolean settling=now-watchedSince<8000||now-lastSeekAt<5000||now-lastWeakStep<15_000;
        if(!playing||settling){watchedPos=pos;stalledMs=0;return;}
        if(pos-watchedPos<300){
            stalledMs+=2000;
            if(stalledMs==6000)weakSignal("stalled 6 s");
            if(stalledMs>=12000){stalledMs=0;recoverFrozen(id,pos);}
        } else {
            if(stalledMs>=2000)noteStall(now);
            stalledMs=0;
        }
        watchedPos=pos;
    }

    private void noteStall(long now) {
        stallTimes.addLast(now);
        while(!stallTimes.isEmpty()&&now-stallTimes.peekFirst()>120_000)stallTimes.removeFirst();
        if(stallTimes.size()>=2){stallTimes.clear();weakSignal("stalled twice in two minutes");}
    }

    /** Steps the quality down one level and keeps later videos there while the signal stays weak */
    private long lastWeakStep;
    private void weakSignal(String why) {
        long now=android.os.SystemClock.elapsedRealtime();
        // No signal at all is not a weak one, the quality stays and the video continues once the phone is back online
        if(!isOnline())return;
        weakSignalUntil=now+300_000;
        // One step a minute at most, the reload after a step stalls by itself and must not count as more trouble
        if(now-lastWeakStep<60_000)return;
        lastWeakStep=now;
        String lower="hd1080".equals(videoQuality)?"hd720":"hd720".equals(videoQuality)||"auto".equals(videoQuality)?"large":"large".equals(videoQuality)?"medium":null;
        com.sya.tube.diag.Diag.log("signal",why+" at "+videoQuality+(lower==null?", already lowest":", now "+lower));
        if(lower==null)return;
        weakQuality=lower;
        showCarNotice("Weak signal, playing at "+qualityName(lower));
        lastSeekAt=android.os.SystemClock.elapsedRealtime();
        chooseQuality(lower);
    }

    /** A picture that stopped moving for 12 seconds is reloaded at the same spot, a second freeze also lowers the quality */
    private void recoverFrozen(String id,long pos) {
        com.sya.tube.diag.Diag.log("signal","video froze at "+pos/1000+"s"+(recoveredOnce?", again":""));
        lastSeekAt=android.os.SystemClock.elapsedRealtime();
        if(recoveredOnce){weakSignal("froze again");return;}
        recoveredOnce=true;
        com.sya.tube.player.YoutubeStreams.invalidate("https://m.youtube.com/watch?v="+id,qualityHeight(videoQuality));
        // The player may already have dropped back to zero, the last time seen playing is where it continues
        long resume=id.equals(trackedVideoId)?Math.max(pos,trackedPositionMs):pos;
        writeResumePoint(id,resume);
        String url=getSharedPrefs().getString(HOME_URL,"https://m.youtube.com/watch?v="+id);
        if(!isOnline()){retryWhenOnline=url;showCarNotice("No signal, the video continues when it returns");return;}
        showCarNotice("Video froze, reloading it");
        if(nativePlayerActive){leaveNativeVideo(true);webView.requestFullScreen();startNativeVideo();return;}
        remoteFullscreenId=id;
        webView.loadUrl(url);
    }

    private static int qualityHeight(String quality) {
        return "tiny".equals(quality)?144:"small".equals(quality)?240:"medium".equals(quality)?360:"large".equals(quality)?480:"hd1080".equals(quality)?1080:720;
    }

    private static String qualityName(String quality) {
        return "auto".equals(quality)?"Auto":qualityHeight(quality)+"p";
    }
    /** Leaving a video clears what was decided for it, opening it again later is a fresh start that goes fullscreen with sound */
    private void forgetVideoMarks(String url) {
        android.net.Uri uri=android.net.Uri.parse(url);
        String id="/watch".equals(uri.getPath())?uri.getQueryParameter("v"):null;
        if(autoFullscreenVideoId!=null&&!autoFullscreenVideoId.equals(id))autoFullscreenVideoId=null;
        if(autoFullscreenDeclinedId!=null&&!autoFullscreenDeclinedId.equals(id))autoFullscreenDeclinedId=null;
        if(unmutedVideoId!=null&&!unmutedVideoId.equals(id))unmutedVideoId=null;
    }

    private void chooseQuality(String value) {
        videoQuality=value;
        applyVideoQuality(); updateQualityLabel();
        // The native player is restarted through the usual seamless handoff at the new quality
        if(nativePlayerActive){leaveNativeVideo(true);webView.requestFullScreen();startNativeVideo();}
    }

    // Commands from the phone screen, see CarRemote

    @Override public void remotePlay(String url) {
        if(webView==null)return;
        android.net.Uri uri=android.net.Uri.parse(url);
        remoteFullscreenId=uri.getQueryParameter("v");
        autoFullscreenDeclinedId=null;autoFullscreenVideoId=null;
        if(nativeHandoffPending)cancelNativeHandoff();
        if(nativePlayerActive)leaveNativeVideo(false);
        if(webView.isVideoFullscreen())webView.exitFullScreen();
        debugStage("remotePlay");
        webView.loadUrl(url);
    }

    @Override public void remoteToggle() {
        if(nativePlayerActive&&nativePlayerFragment!=null){nativePlayerFragment.toggle();return;}
        if(webView!=null)webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()",null);
    }

    @Override public void remoteSetPlaying(boolean playing) {
        if(nativePlayerActive&&nativePlayerFragment!=null){if(playing)nativePlayerFragment.play();else nativePlayerFragment.pause();return;}
        if(webView!=null){if(playing)webView.playVideo();else webView.pauseVideo();}
    }

    // Chapters of the current video, looked up once per video off the main thread, the native player brings its own
    private final java.util.concurrent.ExecutorService chapterLookup=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String chaptersVideoId;
    private java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> pageChapters=java.util.Collections.emptyList();

    @Override public java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> remoteChapters() {
        if(nativePlayerActive&&nativePlayerFragment!=null&&!nativePlayerFragment.chapters().isEmpty())return nativePlayerFragment.chapters();
        String id=remoteVideoId();
        return id!=null&&id.equals(chaptersVideoId)?pageChapters:java.util.Collections.emptyList();
    }

    @Override public void remoteChapter(boolean next) {
        java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> chapters=remoteChapters();
        if(chapters.size()<2)return;
        long pos=nativePlayerActive&&nativePlayerFragment!=null?nativePlayerFragment.position():Math.max(0,watchedPos);
        long target=-1;
        if(next){for(var chapter:chapters)if(chapter.startMs>pos+1000){target=chapter.startMs;break;}}
        else{
            // Back goes to the start of this chapter, or to the one before when already near its start
            for(int i=chapters.size()-1;i>=0;i--)if(chapters.get(i).startMs<=pos){target=pos-chapters.get(i).startMs>3000||i==0?chapters.get(i).startMs:chapters.get(i-1).startMs;break;}
        }
        if(target>=0)remoteSeekTo(target);
    }

    /** Keep chapter data available to the phone remote without adding controls to the car toolbar. */
    private void updateChapterData(String id) {
        if(getView()==null)return;
        if(id!=null&&!id.equals(chaptersVideoId)&&!(nativePlayerActive&&nativePlayerFragment!=null&&!nativePlayerFragment.chapters().isEmpty())){
            chaptersVideoId=id;pageChapters=java.util.Collections.emptyList();
            String url="https://m.youtube.com/watch?v="+id;
            int quality=qualityHeight(videoQuality);
            chapterLookup.execute(()->{var found=com.sya.tube.player.YoutubeStreams.chapters(url,quality);if(webView!=null)webView.post(()->{if(id.equals(chaptersVideoId))pageChapters=found;});});
        }
    }

    @Override public void remoteSeek(long deltaMs) {
        lastSeekAt=android.os.SystemClock.elapsedRealtime();
        if(nativePlayerActive&&nativePlayerFragment!=null){nativePlayerFragment.seekBy(deltaMs);return;}
        if(webView!=null)webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+"+(deltaMs/1000.0)+");})()",null);
    }

    @Override public void remoteSeekTo(long positionMs) {
        lastSeekAt=android.os.SystemClock.elapsedRealtime();
        if(nativePlayerActive&&nativePlayerFragment!=null){nativePlayerFragment.seekBy(positionMs-nativePlayerFragment.position());return;}
        if(webView!=null)webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v)v.currentTime="+(positionMs/1000.0)+";})()",null);
    }

    @Override public void remoteSkipAd() { skipAd(); }

    @Override public void remoteSkip(boolean next) {
        // The next video keeps the car in fullscreen like a skip from the car screen
        if(next)playNextVideo();else playPreviousVideo();
    }

    @Override public void remoteQuality(String value) { chooseQuality(value); }

    @Override public void remoteSpeed(float speed) {
        if(getContext()==null)return;
        com.sya.tube.settings.TubePrefs.setSpeed(getContext(),speed);
        if(nativePlayerActive&&nativePlayerFragment!=null)nativePlayerFragment.setSpeed(speed);
        else applyWebSpeed();
    }

    @Override public void remotePlayLink(String link) { playLibraryLink(link); }

    private void applyWebSpeed() {
        if(webView==null||getContext()==null)return;
        String speed=String.valueOf(com.sya.tube.settings.TubePrefs.speed(getContext()));
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');let p=document.querySelector('#movie_player,.html5-video-player');"
            +"if(!v||Math.abs(v.playbackRate-"+speed+")<0.01)return;if(p&&p.setPlaybackRate)p.setPlaybackRate("+speed+");v.playbackRate="+speed+";})()",null);
    }

    /** Home opens YouTube, or the followed channels when the settings ask for that */
    private String homePage() {
        return getContext()!=null&&com.sya.tube.settings.TubePrefs.get(getContext()).getBoolean(com.sya.tube.settings.TubePrefs.HOME_CHANNELS,false)?com.sya.tube.library.LibraryPages.CHANNELS:YOUTUBE_HOME_URL_BASE;
    }

    // Phone videos and media server videos open in the native player, the lookup can take a moment on a slow server
    private final java.util.concurrent.ExecutorService libraryWork=java.util.concurrent.Executors.newSingleThreadExecutor();
    private void playLibraryLink(String link) {
        if(getContext()==null)return;
        Context app=getContext().getApplicationContext();
        debugStage("libraryPlay");
        libraryWork.execute(()->{
            try {
                com.sya.tube.library.MediaServers.Queue queue=com.sya.tube.library.LibraryPages.resolve(app,link);
                if(webView!=null)webView.post(()->showMediaPlayer(queue));
            } catch(Exception error) {
                com.sya.tube.diag.Diag.log("library","play failed "+error.getClass().getSimpleName()+" "+error.getMessage());
                if(webView!=null)webView.post(()->showCarNotice(error instanceof SecurityException&&error.getMessage()!=null?error.getMessage():"Can't play this video right now"));
            }
        });
    }

    private void showMediaPlayer(com.sya.tube.library.MediaServers.Queue queue) {
        if(getView()==null||webView==null||queue.items.isEmpty())return;
        if(nativeHandoffPending)cancelNativeHandoff();
        if(nativePlayerActive)hideNativePlayer();
        if(webView.isVideoFullscreen())webView.exitFullScreen();
        webView.pauseVideo();
        nativeVideoId=null;
        nativePlayerFragment=ExoPlayerFragment.media(queue.items,queue.index);
        nativePlayerActive=true;
        webView.setVisibility(View.INVISIBLE);
        getView().findViewById(R.id.full_screen_view).setVisibility(View.VISIBLE);
        getChildFragmentManager().beginTransaction().add(R.id.full_screen_view,nativePlayerFragment,PLAYER_FRAGMENT_TAG).commitNowAllowingStateLoss();
        ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(true);button.setImageResource(R.drawable.tube_ic_fullscreen_exit);button.setContentDescription("Exit fullscreen");
        setPlaybackControls(true,true);hideToolbar();hideCornerControls(false);
    }

    // SponsorBlock, looked up once per video and handed to whichever player shows it
    private String segmentsVideoId;
    private java.util.List<com.sya.tube.player.SponsorBlock.Segment> segments=java.util.Collections.emptyList();
    private android.widget.Button segmentButton;
    private void lookupSegments(String id) {
        if(id==null||id.equals(segmentsVideoId)||getContext()==null)return;
        segmentsVideoId=id;segments=java.util.Collections.emptyList();
        onSegmentOfferGone();
        com.sya.tube.player.SponsorBlock.lookup(getContext(),id,found->{
            if(!id.equals(segmentsVideoId))return;
            segments=found;
            applySegments();
        });
    }
    private void applySegments() {
        if(segmentsVideoId==null)return;
        if(webView!=null)com.sya.tube.web.WebSegments.apply(webView,segmentsVideoId,segments);
        if(nativePlayerFragment!=null&&segmentsVideoId.equals(nativeVideoId))nativePlayerFragment.setSegments(segments);
    }
    @Override public void onSegmentSkipped(String category) {
        // A skip jumps past what was buffered, the player fills up again like a fresh start, so it gets a fresh start's grace
        lastSeekAt=android.os.SystemClock.elapsedRealtime()+10_000;stalledMs=0;
        showCarNotice("Skipped "+com.sya.tube.settings.TubePrefs.segmentNoun(category));
    }
    @Override public void onSegmentOffer(String category) {
        if(getView()==null)return;
        android.widget.FrameLayout overlay=getView().findViewById(R.id.overlay_container);
        if(segmentButton==null){
            segmentButton=com.sya.tube.car.CarStyle.button(getContext(),"",true);
            segmentButton.setTextSize(18);
            int pad=com.sya.tube.car.CarStyle.dp(getContext(),22);
            segmentButton.setPadding(pad,0,pad,0);
            segmentButton.setOnClickListener(v->{
                lastSeekAt=android.os.SystemClock.elapsedRealtime();
                if(nativePlayerActive&&nativePlayerFragment!=null)nativePlayerFragment.skipOfferedSegment();
                else com.sya.tube.web.WebSegments.skipOffered(webView);
                onSegmentOfferGone();
            });
        }
        segmentButton.setText("Skip "+com.sya.tube.settings.TubePrefs.segmentNoun(category));
        if(segmentButton.getParent()==null){
            int margin=com.sya.tube.car.CarStyle.dp(getContext(),24);
            android.widget.FrameLayout.LayoutParams place=new android.widget.FrameLayout.LayoutParams(-2,com.sya.tube.car.CarStyle.dp(getContext(),56),android.view.Gravity.BOTTOM|android.view.Gravity.END);
            place.setMargins(margin,margin,margin,margin);
            overlay.addView(segmentButton,place);
        }
    }
    @Override public void onSegmentOfferGone() {
        if(segmentButton!=null&&segmentButton.getParent() instanceof ViewGroup)((ViewGroup)segmentButton.getParent()).removeView(segmentButton);
    }

    @Override public void remoteFullscreen() {
        if(webView==null)return;
        if(exitVideoFullscreen())return;
        String id=watchVideoId();
        if(id==null)return;
        autoFullscreenDeclinedId=null;
        webView.requestFullScreen();startNativeVideo();
    }

    @Override public String remoteVideoId() {
        if(nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.isMedia())return null;
        if(nativePlayerActive&&nativeVideoId!=null)return nativeVideoId;
        return watchVideoId();
    }

    @Override public void remoteState(java.util.function.Consumer<com.sya.tube.remote.CarRemote.State> out) {
        boolean fullscreen=isVideoFullscreenShown();
        if(nativePlayerActive&&nativePlayerFragment!=null){
            // The native player plays the stream itself, it never has ads
            out.accept(new com.sya.tube.remote.CarRemote.State(nativePlayerFragment.title(),nativePlayerFragment.playing(),nativePlayerFragment.position(),nativePlayerFragment.duration(),nativePlayerFragment.isMedia()?null:videoQuality,fullscreen,false,nativePlayerFragment.speed()));
            return;
        }
        if(webView==null)return;
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');let p=document.querySelector('#movie_player,.html5-video-player');return {title:document.title.replace(/ - YouTube$/,''),playing:!!(v&&!v.paused),pos:v?v.currentTime:0,dur:v&&isFinite(v.duration)?v.duration:0,ad:!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'))),rate:v?v.playbackRate:1}})()",value->{
            try {
                org.json.JSONObject state=new org.json.JSONObject(value);
                // A page without a video is nothing playing, its title would read as a video name on the phone
                out.accept(new com.sya.tube.remote.CarRemote.State(watchVideoId()==null?"":state.optString("title"),state.optBoolean("playing"),(long)(state.optDouble("pos")*1000),(long)(state.optDouble("dur")*1000),videoQuality,fullscreen,state.optBoolean("ad"),(float)state.optDouble("rate",1)));
            } catch(Exception ignored) {}
        });
    }

    /** Toasts land on the phone, not the car, so short messages show in the car overlay instead */
    private void showCarNotice(String message) {
        com.sya.tube.diag.Diag.log("notice",message);
        if(getView()==null)return;
        android.widget.FrameLayout overlay=getView().findViewById(R.id.overlay_container);
        android.widget.TextView notice=com.sya.tube.car.CarStyle.text(getContext(),message,18,false);
        notice.setTextColor(getContext().getColor(R.color.tube_on_surface));
        notice.setBackground(com.sya.tube.car.CarStyle.rounded(getContext().getColor(R.color.tube_surface_high),com.sya.tube.car.CarStyle.dp(getContext(),28)));
        int pad=com.sya.tube.car.CarStyle.dp(getContext(),16);
        notice.setPadding(pad+pad/2,pad,pad+pad/2,pad);
        android.widget.FrameLayout.LayoutParams place=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);
        place.bottomMargin=pad*2;
        overlay.addView(notice,place);
        notice.postDelayed(()->overlay.removeView(notice),5000);
    }

    private static String voiceErrorText(int error) {
        switch(error) {
            case SpeechRecognizer.ERROR_NO_MATCH: case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Didn't catch that, tap the mic and try again";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Open Aux on your phone once and allow the microphone";
            case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Voice search needs a data connection";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Voice search is busy, try again in a moment";
            case SpeechRecognizer.ERROR_AUDIO: return "The microphone isn't available right now";
            default: return "Voice search failed (code "+error+")";
        }
    }

    private static String syncValue(int offsetMs) {
        if(offsetMs==0) return "Off";
        return offsetMs>0?"Picture delayed "+offsetMs+" ms":"Picture early "+(-offsetMs)+" ms";
    }

    private static String syncText(int offsetMs) {
        if(offsetMs==0) return "Audio sync: off";
        return "Audio sync: "+(offsetMs>0?"picture delayed "+offsetMs+" ms":"picture early "+(-offsetMs)+" ms");
    }

    private void showQualityChoices() {
        String[] values={"auto","medium","large","hd720","hd1080"};
        String current=videoQuality;
        int index=java.util.Arrays.asList(values).indexOf(current);
        // A separate Dialog window cannot receive the host's forwarded surface taps.
        // Keep choices inside CarStream's existing overlay, just like bookmarks.
        android.widget.FrameLayout overlay=(android.widget.FrameLayout)getView().findViewById(R.id.overlay_container);
        android.widget.FrameLayout scrim=new android.widget.FrameLayout(getContext());
        scrim.setBackgroundColor(0xb3000000);
        scrim.setOnClickListener(v -> overlay.removeView(scrim));
        android.content.Context context=getContext();
        android.widget.LinearLayout panel=new android.widget.LinearLayout(context);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackground(com.sya.tube.car.CarStyle.card(context));
        int padding=com.sya.tube.car.CarStyle.dp(context,24);
        int gap=com.sya.tube.car.CarStyle.dp(context,12);
        panel.setPadding(padding,padding-gap/2,padding,padding);
        panel.setOnClickListener(v -> {});
        android.widget.LinearLayout header=new android.widget.LinearLayout(context);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(com.sya.tube.car.CarStyle.text(context,"Video settings",22,true),new android.widget.LinearLayout.LayoutParams(0,-2,1));
        Button done=com.sya.tube.car.CarStyle.button(context,"Done",true);
        done.setOnClickListener(v -> overlay.removeView(scrim));
        header.addView(done,new android.widget.LinearLayout.LayoutParams(com.sya.tube.car.CarStyle.dp(context,120),-2));
        panel.addView(header);
        String[] labels={"Auto","360p","480p","720p","1080p"};
        // One row per setting keeps the whole panel inside the car screen's height
        android.widget.LinearLayout qualities=com.sya.tube.car.CarStyle.segment(context);
        for(int which=0;which<values.length;which++) {
            final int choice=which;
            Button option=com.sya.tube.car.CarStyle.chip(context,labels[which],which==index);
            option.setPadding(com.sya.tube.car.CarStyle.dp(context,8),0,com.sya.tube.car.CarStyle.dp(context,8),0);
            option.setSingleLine();
            option.setOnClickListener(v -> { overlay.removeView(scrim); chooseQuality(values[choice]); });
            qualities.addView(option,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        }
        android.widget.LinearLayout.LayoutParams row=new android.widget.LinearLayout.LayoutParams(-1,-2);
        row.topMargin=gap;
        panel.addView(qualities,row);
        // Car audio arrives later than the picture over Android Auto, holding the picture back lines them up again
        android.widget.LinearLayout sync=com.sya.tube.car.CarStyle.segment(context);
        Button earlier=com.sya.tube.car.CarStyle.chip(context,"−",false);
        earlier.setContentDescription("Picture earlier");
        android.widget.LinearLayout syncText=new android.widget.LinearLayout(context);
        syncText.setOrientation(android.widget.LinearLayout.VERTICAL);
        syncText.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView syncLabel=com.sya.tube.car.CarStyle.text(context,"Audio sync",13,false);
        syncLabel.setGravity(android.view.Gravity.CENTER);
        syncText.addView(syncLabel);
        android.widget.TextView syncValue=com.sya.tube.car.CarStyle.text(context,"",16,true);
        syncValue.setGravity(android.view.Gravity.CENTER);
        syncText.addView(syncValue);
        Runnable showSync=()->syncValue.setText(syncValue(com.sya.tube.player.SyncedRenderersFactory.offsetMs(context)));
        showSync.run();
        earlier.setOnClickListener(v->{com.sya.tube.player.SyncedRenderersFactory.adjust(context,-com.sya.tube.player.SyncedRenderersFactory.STEP_MS);showSync.run();});
        Button later=com.sya.tube.car.CarStyle.chip(context,"+",false);
        later.setContentDescription("Picture later");
        later.setOnClickListener(v->{com.sya.tube.player.SyncedRenderersFactory.adjust(context,com.sya.tube.player.SyncedRenderersFactory.STEP_MS);showSync.run();});
        int step=com.sya.tube.car.CarStyle.dp(context,88);
        sync.addView(earlier,new android.widget.LinearLayout.LayoutParams(step,-2));
        sync.addView(syncText,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        sync.addView(later,new android.widget.LinearLayout.LayoutParams(step,-2));
        android.widget.LinearLayout.LayoutParams syncRow=new android.widget.LinearLayout.LayoutParams(-1,-2);
        syncRow.topMargin=gap;
        panel.addView(sync,syncRow);
        android.widget.LinearLayout aspects=com.sya.tube.car.CarStyle.segment(context);
        for(AspectRatio ratio:AspectRatio.values()) {
            Button option=com.sya.tube.car.CarStyle.chip(context,getHumanText(ratio),ratio==currentAspectRatio);
            option.setOnClickListener(v -> {
                setAspectRatio(ratio);
                overlay.removeView(scrim);
            });
            aspects.addView(option,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        }
        android.widget.LinearLayout.LayoutParams aspectRow=new android.widget.LinearLayout.LayoutParams(-1,-2);
        aspectRow.topMargin=gap;
        panel.addView(aspects,aspectRow);
        android.widget.LinearLayout speeds=com.sya.tube.car.CarStyle.segment(context);
        float currentSpeed=nativePlayerActive&&nativePlayerFragment!=null?nativePlayerFragment.speed():com.sya.tube.settings.TubePrefs.speed(context);
        for(float speed:com.sya.tube.settings.TubePrefs.SPEEDS) {
            Button option=com.sya.tube.car.CarStyle.chip(context,com.sya.tube.settings.TubePrefs.speedText(speed)+"x",Math.abs(speed-currentSpeed)<0.01f);
            option.setPadding(com.sya.tube.car.CarStyle.dp(context,8),0,com.sya.tube.car.CarStyle.dp(context,8),0);
            option.setSingleLine();
            option.setOnClickListener(v -> { overlay.removeView(scrim); remoteSpeed(speed); });
            speeds.addView(option,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        }
        android.widget.LinearLayout.LayoutParams speedRow=new android.widget.LinearLayout.LayoutParams(-1,-2);
        speedRow.topMargin=gap;
        panel.addView(speeds,speedRow);
        // Shown when the fullscreen player has a caption track loaded, switching it takes effect at once
        if(nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.hasCaptions()){
            android.widget.LinearLayout captions=com.sya.tube.car.CarStyle.segment(context);
            boolean on=nativePlayerFragment.captionsOn();
            for(int which=0;which<2;which++){
                boolean choice=which==1;
                Button option=com.sya.tube.car.CarStyle.chip(context,choice?"Captions on":"Captions off",choice==on);
                option.setOnClickListener(v->{
                    overlay.removeView(scrim);
                    if(nativePlayerFragment!=null)nativePlayerFragment.setCaptionsOn(choice);
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("pref_car_captions",choice).apply();
                    // YouTube's own player follows, so the next video starts the same way
                    if(webView!=null)webView.evaluateJavascript("(()=>{let p=document.querySelector('#movie_player,.html5-video-player');if(p&&p."+(choice?"loadModule":"unloadModule")+")p."+(choice?"loadModule":"unloadModule")+"('captions');})()",null);
                });
                captions.addView(option,new android.widget.LinearLayout.LayoutParams(0,-2,1));
            }
            android.widget.LinearLayout.LayoutParams captionRow=new android.widget.LinearLayout.LayoutParams(-1,-2);
            captionRow.topMargin=gap;
            panel.addView(captions,captionRow);
        }
        int width=Math.min(com.sya.tube.car.CarStyle.dp(context,560),Math.max(1,getView().getWidth()-padding*2));
        // A short car screen scrolls the panel instead of cutting its last rows off
        android.widget.ScrollView scroller=new android.widget.ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,-2));
        int margin=com.sya.tube.car.CarStyle.dp(context,12);
        android.widget.FrameLayout.LayoutParams place=new android.widget.FrameLayout.LayoutParams(width,-2,android.view.Gravity.CENTER);
        place.topMargin=margin;place.bottomMargin=margin;
        scrim.addView(scroller,place);
        overlay.addView(scrim,new android.widget.FrameLayout.LayoutParams(-1,-1));
        done.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showCornerControls() {
        this.cornerControls.setVisibility(android.view.View.VISIBLE);
        this.cornerControls.removeCallbacks(this.cornerControlsHideRunnable);
        this.cornerControls.animate().cancel();
        this.cornerControls.clearAnimation();
        this.cornerControls.setTranslationX(0.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAndHideToolbarAnimation() {
        this.toolbar.removeCallbacks(this.toolbarHideRunnable);
        showToolbar();
        this.toolbar.postDelayed(this.toolbarHideRunnable, 8000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAndHideCornerControlsAnimation() {
        this.cornerControls.removeCallbacks(this.cornerControlsHideRunnable);
        showCornerControls();
        this.cornerControls.postDelayed(this.cornerControlsHideRunnable, 8000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleToolbarAnimation() {
        if (this.toolbar.getTranslationY() == 0.0f) {
            hideToolbar();
        } else {
            showAndHideToolbarAnimation();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleCornerControlsAnimation() {
        if (this.cornerControls.getTranslationX() == 0.0f) {
            hideCornerControls(true);
        } else {
            showAndHideCornerControlsAnimation();
        }
    }

    private boolean isRecordAudioGranted() {
        return ContextCompat.checkSelfPermission(getContext(), "android.permission.RECORD_AUDIO") == 0;
    }

    private String getSearchUrlBase() {
        if (this.searchMode == SearchMode.YOUTUBE) {
            return YOUTUBE_SEARCH_URL_BASE;
        }
        if (this.searchMode == SearchMode.GOOGLE) {
            return GOOGLE_SEARCH_URL_BASE;
        }
        return null;
    }

    @Override // androidx.fragment.app.Fragment
    public void onStart() {
        super.onStart();
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
        backgroundAudio=false;
        if(webView!=null)webView.setKeepVisible(false);
        ExoPlayerFragment.keepPlayingWhenHidden=keepAudioWhenHidden();
        if(webView!=null){webView.removeCallbacks(playbackWatchdog);webView.postDelayed(playbackWatchdog,2000);}
        if(recentStrip!=null&&webView!=null)recentStrip.update(com.sya.tube.remote.RecentStrip.isHome(webView.getUrl()));
        // Coming back to Tube continues whatever was playing when the driver switched away, instead of a paused black screen
        if(webView!=null&&!nativePlayerActive){
            webView.onResume();
            webView.evaluateJavascript("(function(){var v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');if(v&&window.pausedDueToMinimize){window.pausedDueToMinimize=false;v.play();}})()",null);
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void onStop() {
        super.onStop();
    }

    public void resumePlayback() {
        if (isAdded() && !this.nativePlayerActive) {
            gainAudioFocus();
        }
        VideoWebView videoWebView = this.webView;
        if (videoWebView != null) {
            videoWebView.requestFocus();
            this.webView.playVideo();
        }
        handleVoiceVisibility();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void gainAudioFocus() {
        // WebView and the native player request audio focus when playback starts.
        // Do not steal focus simply because the browser was opened.
    }

    // Sound behind another app, see onPause
    private boolean backgroundAudio;
    private com.sya.tube.remote.RecentStrip recentStrip;
    private boolean keepAudioWhenHidden() {
        return getContext()!=null&&PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_background_audio",true);
    }
    private boolean isPlayingNow() {
        if(nativePlayerActive&&nativePlayerFragment!=null)return nativePlayerFragment.playing();
        return playerState==android.media.session.PlaybackState.STATE_PLAYING;
    }
    /** True while the car screen is out of view and its sound still plays, the car screen is kept open for it */
    public boolean isPlayingInBackground() {
        // Page events can stop arriving while the car screen is hidden, so this goes by what the watchdog last saw move
        return backgroundAudio&&observedPlaying&&android.os.SystemClock.elapsedRealtime()-observedAt<10_000;
    }
    private boolean observedPlaying;
    private long lastPlayingAt;
    private long observedAt;

    // A phone call pauses the video and hanging up continues it, the phone's audio mode covers ringing, calls and voice chats
    private android.media.AudioManager.OnModeChangedListener callWatch;
    private boolean pausedForCall;
    private void startCallWatch() {
        if(Build.VERSION.SDK_INT<31||getContext()==null||callWatch!=null)return;
        android.media.AudioManager audio=getContext().getSystemService(android.media.AudioManager.class);
        callWatch=mode->{
            boolean call=mode==android.media.AudioManager.MODE_RINGTONE||mode==android.media.AudioManager.MODE_IN_CALL||mode==android.media.AudioManager.MODE_IN_COMMUNICATION||mode==android.media.AudioManager.MODE_CALL_SCREENING;
            if(call&&!pausedForCall&&isPlayingNow()){pausedForCall=true;com.sya.tube.diag.Diag.log("car","paused for a call");remoteSetPlaying(false);}
            else if(mode==android.media.AudioManager.MODE_NORMAL&&pausedForCall){pausedForCall=false;com.sya.tube.diag.Diag.log("car","call ended, playing again");resumeAfterCall(4);}
        };
        audio.addOnModeChangedListener(androidx.core.content.ContextCompat.getMainExecutor(getContext()),callWatch);
    }
    // The phone app lets go of the audio a moment after the call ends, a play request before that is refused, so it is retried
    private void resumeAfterCall(int attempts) {
        if(webView==null||attempts<=0||pausedForCall)return;
        webView.postDelayed(()->{
            if(webView==null||pausedForCall||isPlayingNow())return;
            remoteSetPlaying(true);
            resumeAfterCall(attempts-1);
        },1200);
    }
    private void stopCallWatch() {
        if(callWatch==null||getContext()==null||Build.VERSION.SDK_INT<31)return;
        getContext().getSystemService(android.media.AudioManager.class).removeOnModeChangedListener(callWatch);
        callWatch=null;
    }

    private void loseAudioFocus() {
        // Playback is paused before this method; the renderer owns audio focus.
    }

    private void handleVoiceVisibility() {
        if (getView() != null) {
            // Always shown, a tap without the microphone grant explains how to allow it instead of the button silently missing
            getView().findViewById(R.id.voice_button).setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void onPause() {
        rememberLeavingPosition();
        // With the setting on, switching to Maps or another app keeps the sound going, the watchdog keeps running meanwhile
        if(keepAudioWhenHidden()&&isPlayingNow()){
            backgroundAudio=true;
            if(webView!=null&&!nativePlayerActive)webView.setKeepVisible(true);
            com.sya.tube.diag.Diag.log("car","kept playing behind another app");
            super.onPause();
            return;
        }
        if(nativePlayerFragment!=null)nativePlayerFragment.pause();
        VideoWebView videoWebView = this.webView;
        if (videoWebView != null) {
            videoWebView.pauseVideoAndSetFlag();
            videoWebView.onPause();
        }
        super.onPause();
        loseAudioFocus();
        if(webView!=null){
            webView.removeCallbacks(playbackWatchdog);
            // A video page was saved with its time by rememberLeavingPosition, anything else is saved as it is
            if(leftVideoId==null) getSharedPrefs().edit().putString(HOME_URL,webView.getUrl()).apply();
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void onDetach() {
        if(playbackReceiver!=null) { requireContext().unregisterReceiver(playbackReceiver); playbackReceiver=null; }
        super.onDetach();
    }

    @Override // androidx.fragment.app.Fragment
    public void onDestroy() {
        suggestions.cancel();
        stopCallWatch();
        stopOnlineWatch();
        if(getContext()!=null)com.thekirankumar.youtubeauto2.service.MyMediaBrowserService.publishCar(getContext(),null,false,0,0);
        VideoWebView videoWebView = this.webView;
        if (videoWebView != null) {
            videoWebView.destroy();
        }
        HandlerThread handlerThread = this.handlerThread;
        if (handlerThread != null) {
            handlerThread.quit();
        }
        MediaSessionCompat mediaSessionCompat = this.mediaSessionCompat;
        if (mediaSessionCompat != null) {
            mediaSessionCompat.release();
        }
        super.onDestroy();
    }

    @Override // androidx.fragment.app.Fragment
    public void onDestroyView() {
        com.sya.tube.remote.CarRemote.detach(this);
        if(toolbar!=null) toolbar.removeCallbacks(toolbarHideRunnable);
        if(cornerControls!=null) cornerControls.removeCallbacks(cornerControlsHideRunnable);
        if(handler!=null) handler.removeCallbacksAndMessages(null);
        carHost.removeActivityCallback(this);
        if(mediaBrowser!=null) { mediaBrowser.disconnect(); mediaBrowser=null; }
        if(speechRecognizer!=null) { speechRecognizer.destroy(); speechRecognizer=null; }
        if(webView!=null) { webView.destroy(); webView=null; }
        super.onDestroyView();
    }

    @Override // com.thekirankumar.youtubeauto2.activity.MainCarActivity.ActivityCallbacks
    public void onConfigChanged() {
        if(webView!=null) WebviewUtils.injectNightModeCss(webView);
    }

    @Override // com.thekirankumar.youtubeauto2.activity.MainCarActivity.ActivityCallbacks
    public void onWindowFocusChanged(boolean z) {
        if (z && webView!=null && fakeEditText!=null && !com.sya.tube.car.CarBridge.isSearchVisible()) {
            onHideKeyboardFromJS();
        }
    }

    @Override // com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks
    public void onVideoElementDiscovered() {
        if(webView==null) return;
        if(PlaybackOwner.isContentVideo(webView)) setPlaybackControls(true,playerState==android.media.session.PlaybackState.STATE_PLAYING);
        AspectRatio aspectRatio = this.currentAspectRatio;
        if (aspectRatio != null) {
            this.webView.setAspectRatio(aspectRatio.name().toLowerCase());
        }
        this.webView.attachVideoListeners();
        applyVideoQuality();
        applySegments();
        // A video starting to load on a watch page is the picked video, fullscreen can begin before its first frame
        maybeAutoFullscreen(true);
        Integer num = this.currentVideoTime;
        if (num != null) {
            this.webView.seekBySeconds(num.intValue());
            this.currentVideoTime = null;
        }
    }

    @Override // com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks
    public void onVideoCurrentTimeResult(int i) {
        this.currentVideoTime = Integer.valueOf(i);
    }

    @Override // com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks
    public void onShowKeyboardFromJS(String str) {
        this.fakeEditText.setText(str);
        carHost.getCarInputController().startInput(this.fakeEditText);
        CarEditText carEditText = this.fakeEditText;
        carEditText.setSelection(carEditText.getText().length());
        ViewGroup.LayoutParams layoutParams = this.webView.getLayoutParams();
        layoutParams.height = (int) TypedValue.applyDimension(1, 150.0f, getResources().getDisplayMetrics());
        this.webView.setLayoutParams(layoutParams);
        this.webView.scrollActiveElementIntoView(true);
    }

    @Override // com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks
    public void onHideKeyboardFromJS() {
        if(webView==null || carHost==null) return;
        carHost.getCarInputController().stopInput();
        ViewGroup.LayoutParams layoutParams = this.webView.getLayoutParams();
        layoutParams.height = -1;
        this.webView.setLayoutParams(layoutParams);
    }

    public void setParkingBrake(boolean z) {
        this.isParkingEngaged = z;
        if (!this.warningAccepted) {
            showWarningScreen();
        } else {
            hideWarningScreen();
        }
    }

    private void hideWarningScreen() {
        if (this.warningScreenOpen) {
            if (isAdded()) {
                this.webView.setVisibility(android.view.View.VISIBLE);
                this.toolbar.setVisibility(android.view.View.VISIBLE);
                FragmentManager childFragmentManager = getChildFragmentManager();
                Fragment fragmentFindFragmentByTag = childFragmentManager.findFragmentByTag(SAFETY_WARNING_FRAGMENT_TAG);
                if (fragmentFindFragmentByTag != null) {
                    this.warningScreenOpen = false;
                    FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
                    fragmentTransactionBeginTransaction.remove(fragmentFindFragmentByTag);
                    fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                    fragmentTransactionBeginTransaction.commitAllowingStateLoss();
                }
            }
            this.webView.requestFocus();
        }
    }

    private void showWarningScreen() {
        if (isAdded()) {
            this.warningScreenOpen = true;
            FragmentManager childFragmentManager = getChildFragmentManager();
            SafetyWarningFragment safetyWarningFragment = (SafetyWarningFragment) childFragmentManager.findFragmentByTag(SAFETY_WARNING_FRAGMENT_TAG);
            if (safetyWarningFragment == null) {
                safetyWarningFragment = new SafetyWarningFragment();
            }
            FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
            fragmentTransactionBeginTransaction.replace(R.id.overlay_container, safetyWarningFragment, SAFETY_WARNING_FRAGMENT_TAG);
            fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            fragmentTransactionBeginTransaction.commitNow();
            this.webView.setVisibility(android.view.View.GONE);
            this.toolbar.setVisibility(android.view.View.GONE);
        }
    }

    private void hideBookmarksScreen() {
        if (isAdded()) {
            if (!this.nativePlayerActive) {
                this.webView.setVisibility(android.view.View.VISIBLE);
                this.toolbar.setVisibility(android.view.View.VISIBLE);
                showToolbar();
            }
            FragmentManager childFragmentManager = getChildFragmentManager();
            Fragment fragmentFindFragmentByTag = childFragmentManager.findFragmentByTag(BOOKMARKS_FRAGMENT_TAG);
            if (fragmentFindFragmentByTag != null) {
                FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
                fragmentTransactionBeginTransaction.remove(fragmentFindFragmentByTag);
                fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                fragmentTransactionBeginTransaction.commitNow();
            }
        }
        if (this.nativePlayerActive) {
            return;
        }
        this.webView.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showBookmarksScreen() {
        if (isAdded()) {
            this.webView.setVisibility(android.view.View.INVISIBLE);
            this.toolbar.setVisibility(android.view.View.GONE);
            FragmentManager childFragmentManager = getChildFragmentManager();
            BookmarksFragment bookmarksFragment = (BookmarksFragment) childFragmentManager.findFragmentByTag(BOOKMARKS_FRAGMENT_TAG);
            if (bookmarksFragment == null) {
                bookmarksFragment = new BookmarksFragment();
            }
            FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
            fragmentTransactionBeginTransaction.add(R.id.overlay_container, bookmarksFragment, BOOKMARKS_FRAGMENT_TAG);
            fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            fragmentTransactionBeginTransaction.commitAllowingStateLoss();
        }
    }

    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public void onNativePlayerControlsVisibilityChange(int i) {
        if (i == 0) {
            showToolbar();
            showCornerControls();
        } else if (i == 8) {
            hideToolbar();
            hideCornerControls(true);
        }
    }
    @Override public void onNativePlaybackChanged(boolean playing) { if(nativePlayerActive)setPlaybackControls(true,playing); }
    @Override public void onNativeSkip(boolean next) { if(next)playNextVideo();else playPreviousVideo(); }
    private void playNextVideo() {
        if(webView==null)return;
        if(nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.isMedia()){nativePlayerFragment.next();return;}
        // A video queued from the phone comes before YouTube's own next one
        com.sya.tube.remote.UpNext.Item queued=getContext()==null?null:com.sya.tube.remote.UpNext.pop(getContext());
        if(queued!=null){debugStage("upNext "+queued.id);remotePlay(queued.url);return;}
        if(nativePlayerActive)leaveNativeVideo(false);
        if(webView.isVideoFullscreen())webView.exitFullScreen();
        webView.playNextTrack();
    }
    private void playPreviousVideo() {
        if(webView==null)return;
        if(nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.isMedia()){nativePlayerFragment.previous();return;}
        if(nativePlayerActive&&nativePlayerFragment!=null) {
            if(nativePlayerFragment.position()>3000){nativePlayerFragment.restart();return;}
            leaveNativeVideo(false);
        }
        webView.playPreviousTrack();
    }
    @Override public void onNativeVideoFailure() {
        // Before the swap the web fullscreen video is still playing, so a failed native start simply stays on it
        if(nativeHandoffPending){
            cancelNativeHandoff();
            // A rejected stream link usually works after a fresh lookup, the player already dropped the bad one from the cache
            String id=watchVideoId();
            if(id!=null&&!id.equals(handoffRetryVideoId)&&webView!=null&&webView.isVideoFullscreen()){
                handoffRetryVideoId=id;
                debugStage("handoffRetry");
                startNativeVideo();
            }
            return;
        }
        if(!nativePlayerActive||webView==null)return;
        leaveNativeVideo(true);webView.requestFullScreen();
    }
    private String watchVideoId() {
        String url=webView==null?null:webView.getUrl();
        if(url==null)return null;
        android.net.Uri uri=android.net.Uri.parse(url);
        String host=uri.getHost();
        if(host==null||!(host.equals("youtube.com")||host.endsWith(".youtube.com"))||!"/watch".equals(uri.getPath()))return null;
        return uri.getQueryParameter("v");
    }
    // The projection host must see back itself, a key consumed here never gets acknowledged to Android Auto
    private boolean backHandledByHost;
    public void setBackHandledByHost(boolean value) { backHandledByHost=value; }
    public boolean isVideoFullscreenShown() {
        return nativePlayerActive||nativeHandoffPending||webView!=null&&webView.isVideoFullscreen();
    }
    /** Back and the fullscreen button both land here, returning to the page at the current spot */
    private boolean exitVideoFullscreen() {
        if(webView==null||!nativeHandoffPending&&!nativePlayerActive&&!webView.isVideoFullscreen())return false;
        autoFullscreenDeclinedId=watchVideoId();
        if(nativeHandoffPending){cancelNativeHandoff();if(webView.isVideoFullscreen())webView.exitFullScreen();return true;}
        if(nativePlayerActive){leaveNativeVideo(true);return true;}
        webView.exitFullScreen();
        return true;
    }
    private void maybeAutoFullscreen() { maybeAutoFullscreen(false); }
    private void maybeAutoFullscreen(boolean early) {
        if(webView==null||getView()==null||nativePlayerActive||nativeHandoffPending||webView.isVideoFullscreen()||isSearchShown||warningScreenOpen){
            debugStage("autoSkip native="+nativePlayerActive+" pending="+nativeHandoffPending+" fs="+(webView!=null&&webView.isVideoFullscreen())+" search="+isSearchShown+" warning="+warningScreenOpen);
            return;
        }
        String id=watchVideoId();
        if(!PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_auto_fullscreen",true)&&(id==null||!id.equals(remoteFullscreenId)))return;
        if(id==null||id.equals(autoFullscreenVideoId)||id.equals(autoFullscreenDeclinedId)){debugStage("autoSkip id="+id+" done="+autoFullscreenVideoId+" declined="+autoFullscreenDeclinedId);return;}
        webView.evaluateJavascript(AD_STATE_JS,value->{
            if(webView==null||getView()==null||!id.equals(watchVideoId())||id.equals(autoFullscreenVideoId))return;
            // Ads stay on the page where YouTube's own skip button works, fullscreen waits for the real video
            if(value!=null&&value.contains("\"ad\":true")){
                debugStage("autoWaitAd");
                if(!autoAdWaitPosted){autoAdWaitPosted=true;webView.postDelayed(()->{autoAdWaitPosted=false;maybeAutoFullscreen();},1000);}
                return;
            }
            if(nativePlayerActive||nativeHandoffPending||webView.isVideoFullscreen())return;
            // A paused page stays a page, the playing event brings this back once the video starts, an early call
            // comes from a video that is buffering to play, which is the same intent a moment sooner
            if(!early&&(value==null||!value.contains("\"playing\":true")))return;
            autoFullscreenVideoId=id;
            debugStage("autoFullscreen");
            webView.requestFullScreen();startNativeVideo();
        });
    }
    /**
     * YouTube's mobile site starts the first video muted behind a tap to unmute badge, there is no tap in the car,
     * so each new video gets its sound once, a mute the driver chooses later stays muted
     */
    private void unmuteAutoplay() {
        if(webView==null)return;
        String id=watchVideoId();
        boolean firstPlay=id!=null&&!id.equals(unmutedVideoId);
        if(id!=null)unmutedVideoId=id;
        webView.evaluateJavascript("(()=>{let p=document.querySelector('#movie_player,.html5-video-player');let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');"
            +"let badge=document.querySelector('.ytp-unmute,.ytp-unmute-inner,.ytp-muted-autoplay-endscreen-overlay,[class*=\"unmute\"]');"
            +"if(!v||!("+firstPlay+"||(badge&&badge.offsetParent)))return false;"
            +"if(p&&typeof p.isMuted==='function'&&p.isMuted())p.unMute();if(p&&typeof p.getVolume==='function'&&p.getVolume()===0)p.setVolume(100);"
            +"v.muted=false;if(v.volume===0)v.volume=1;if(badge&&badge.click&&badge.offsetParent)badge.click();return true})()",
            value->{ if("true".equals(value))debugStage("unmuted"); });
    }
    /** While web fullscreen shows an ad, offers a skip button whenever YouTube allows skipping */
    private void watchAds() {
        if(webView==null||getView()==null)return;
        if(!webView.isVideoFullscreen()||nativePlayerActive){showSkipAd(false);return;}
        webView.evaluateJavascript(AD_STATE_JS,value->{
            showSkipAd(value!=null&&value.contains("\"skip\":true"));
            if(webView!=null&&webView.isVideoFullscreen()&&!nativePlayerActive)scheduleAdWatch(700);
        });
    }
    private void scheduleAdWatch(long delay) {
        if(adWatchPosted||webView==null)return;
        adWatchPosted=true;
        webView.postDelayed(adWatch,delay);
    }
    private void showSkipAd(boolean show) {
        if(getView()==null)return;
        android.widget.FrameLayout overlay=getView().findViewById(R.id.overlay_container);
        if(!show){ if(skipAdButton!=null&&skipAdButton.getParent()==overlay)overlay.removeView(skipAdButton); return; }
        if(skipAdButton==null){
            skipAdButton=com.sya.tube.car.CarStyle.button(getContext(),"Skip ad",true);
            skipAdButton.setTextSize(18);
            android.graphics.drawable.Drawable skip=getContext().getDrawable(R.drawable.tube_ic_skip).mutate();
            skip.setTint(android.graphics.Color.BLACK);
            skipAdButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,skip,null);
            skipAdButton.setCompoundDrawablePadding(com.sya.tube.car.CarStyle.dp(getContext(),8));
            int pad=com.sya.tube.car.CarStyle.dp(getContext(),22);
            skipAdButton.setPadding(pad,0,pad-4,0);
            skipAdButton.setOnClickListener(v->skipAd());
        }
        if(skipAdButton.getParent()==null){
            int margin=(int)(24*getResources().getDisplayMetrics().density);
            android.widget.FrameLayout.LayoutParams place=new android.widget.FrameLayout.LayoutParams(-2,(int)(56*getResources().getDisplayMetrics().density),android.view.Gravity.BOTTOM|android.view.Gravity.END);
            place.setMargins(margin,margin,margin,margin);
            overlay.addView(skipAdButton,place);
        }
    }
    private void skipAd() {
        if(webView==null)return;
        debugStage("skipAd");
        // The page's own button is pressed first, if the ad is still showing it is run to its end, which is what skipping does
        webView.evaluateJavascript("(()=>{let s=document.querySelector('"+AD_SELECTOR+"');if(s)s.click();setTimeout(()=>{let p=document.querySelector('#movie_player,.html5-video-player');if(p&&p.classList.contains('ad-showing')){let v=document.querySelector('video');if(v&&isFinite(v.duration))v.currentTime=v.duration;}},400)})()",null);
        showSkipAd(false);
        scheduleAdWatch(1200);
    }
    private void startNativeVideo() {
        String url=webView.getUrl();
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');let p=document.querySelector('#movie_player,.html5-video-player'),b=document.querySelector('.ytp-subtitles-button');let track=p&&p.getOption?p.getOption('captions','track'):null;return v?{time:v.currentTime,playing:!v.paused,speed:v.playbackRate,ad:!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'))),captions:!!(track&&(track.languageCode||track.language_code)&&(!b||b.getAttribute('aria-pressed')!=='false')),capLang:track?(track.languageCode||track.language_code||null):null,capAuto:!!(track&&track.kind==='asr')}:null})()",value->{
            if(webView==null||getView()==null||nativePlayerActive||nativeHandoffPending)return;
            // YouTube rewrites the address after load (it adds tracking parameters), only a different video is a real change
            String id=android.net.Uri.parse(url).getQueryParameter("v");
            if(id==null||!id.equals(watchVideoId())){debugStage("nativeSkip page moved on");return;}
            // A restored page can ask before its player exists, it is asked again shortly instead of settling for web playback
            if(value==null||"null".equals(value)){
                if(nativeStartAttempts++<10){debugStage("nativeWait no video yet");webView.postDelayed(()->{if(webView!=null&&!nativePlayerActive&&!nativeHandoffPending&&id.equals(watchVideoId()))startNativeVideo();},700);}
                else debugStage("nativeSkip no video");
                return;
            }
            nativeStartAttempts=0;
            try {
                org.json.JSONObject state=new org.json.JSONObject(value);
                // Captions YouTube showed carry over to the native player, a track is side loaded either way so the settings panel can turn them on
                boolean captionsOn=state.optBoolean("captions")||PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_car_captions",false);
                String captionsLang=state.isNull("capLang")?java.util.Locale.getDefault().getLanguage():state.optString("capLang");
                // The ad's clock is not the video's, so the handoff waits in web fullscreen until the real video plays
                if(state.optBoolean("ad")){
                    debugStage("nativeWaitAd");
                    if(!nativeAfterAdPosted){nativeAfterAdPosted=true;webView.postDelayed(()->{nativeAfterAdPosted=false;if(webView!=null&&webView.isVideoFullscreen()&&!nativePlayerActive&&!nativeHandoffPending&&id.equals(watchVideoId()))startNativeVideo();},1000);}
                    return;
                }
                int quality=qualityHeight(videoQuality);
                // The web video is already fullscreen and keeps playing while the native player prepares underneath it
                nativeHandoffPending=true;nativeHandoffSyncAttempts=0;handoffBufferWaitStart=0;handoffLastWeb=-2;handoffWebMovedAt=0;
                nativeVideoId=android.net.Uri.parse(url).getQueryParameter("v");
                nativeHandoffResumePlaying=state.getBoolean("playing");
                getView().findViewById(R.id.full_screen_view).setVisibility(View.VISIBLE);
                // Prepare a few seconds ahead, by the time the native player is ready the web video has reached that point and the buffer already covers it
                nativePlayerFragment=ExoPlayerFragment.handoff(url,(long)(state.getDouble("time")*1000)+HANDOFF_LEAD_MS,(float)state.optDouble("speed",1),quality,captionsLang,state.optBoolean("capAuto"));
                nativePlayerFragment.requireArguments().putBoolean("captionsOn",captionsOn);
                getChildFragmentManager().beginTransaction().add(R.id.full_screen_view,nativePlayerFragment,PLAYER_FRAGMENT_TAG).commitNowAllowingStateLoss();
                applySegments();
                ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(true);button.setImageResource(R.drawable.tube_ic_fullscreen_exit);button.setContentDescription("Exit fullscreen");
                // The page's fullscreen can miss its activation tap, the toolbar then stays until the native player takes over
                if(webView.isVideoFullscreen()){hideToolbar();hideCornerControls(false);}
                else debugStage("handoffWithoutWebFullscreen");
            } catch(org.json.JSONException error){debugStage("nativeSkip unreadable state "+value);webView.requestFullScreen();}
        });
    }
    @Override public void onNativeHandoffReady() {
        // How long this phone takes from preparing to a ready first frame, smoothed, sets the lead for the next handoff
        if(handoffPrepareAt>0&&getContext()!=null){
            long took=android.os.SystemClock.elapsedRealtime()-handoffPrepareAt;
            SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getContext());
            long previous=prefs.getLong("pref_handoff_prepare_ms",-1);
            long smoothed=previous<0?took:(previous*2+took)/3;
            prefs.edit().putLong("pref_handoff_prepare_ms",smoothed).apply();
            debugStage("handoffReady after "+took+" ms, typical "+smoothed);
            handoffPrepareAt=0;
        }
        syncNativeHandoff();
    }
    /** Stream lookup takes seconds on a phone connection, the native player starts a little ahead of where the web video is by then */
    @Override public void handoffStartPosition(long requested, java.util.function.LongConsumer start) {
        if(webView==null){start.accept(requested);return;}
        long typical=PreferenceManager.getDefaultSharedPreferences(getContext()).getLong("pref_handoff_prepare_ms",-1);
        // The lead covers this phone's usual prepare time with margin, so the web video reaches the prepared frame after it is ready
        long lead=typical<0?HANDOFF_RESOLVED_LEAD_MS:Math.max(2000,Math.min(12000,typical*3/2+800));
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');return v?v.currentTime:null})()",value->{
            long position=requested;
            try { position=Math.max(requested,(long)(Double.parseDouble(value)*1000)+lead); } catch(Exception ignored) {}
            handoffPrepareAt=android.os.SystemClock.elapsedRealtime();
            debugStage("handoffPrepare at "+position+" requested "+requested+" lead "+lead);
            start.accept(position);
        });
    }
    /** Waits until the native buffer covers the web player's current position, then continues natively from that exact moment */
    private void syncNativeHandoff() {
        if(!nativeHandoffPending||webView==null||nativePlayerFragment==null)return;
        if(nativeVideoId!=null&&!nativeVideoId.equals(watchVideoId())){debugStage("handoffCancelled page moved on");cancelNativeHandoff();return;}
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');return v?v.currentTime:null})()",value->{
            if(!nativeHandoffPending||nativePlayerFragment==null)return;
            double seconds;
            try { seconds=Double.parseDouble(value); } catch(NumberFormatException error) { seconds=-1; }
            long web=seconds<0?-1:(long)(seconds*1000);
            long prepared=nativePlayerFragment.position();
            long now0=android.os.SystemClock.elapsedRealtime();
            if(web!=handoffLastWeb){handoffLastWeb=web;handoffWebMovedAt=now0;}
            else if(handoffWebMovedAt==0)handoffWebMovedAt=now0;
            // Paused, or left on the page when its fullscreen did not take, the web video never reaches the prepared frame
            boolean webStuck=now0-handoffWebMovedAt>1500;
            if(webStuck&&web<prepared-40)debugStage("handoffWebStuck web="+web+" prepared="+prepared);
            if(!webStuck&&web>=0&&web<prepared-40&&++nativeHandoffSyncAttempts<400) {
                // The native player waits at the prepared frame, it starts the moment the web video reaches it so no seek is needed
                long remaining=prepared-web;
                webView.postDelayed(this::syncNativeHandoff,Math.max(10,Math.min(250,remaining-60)));
                return;
            }
            // The web video already passed the prepared point, so continue from where it is now
            // A stuck web video sits behind the prepared frame, starting there would skip the seconds in between
            long target=webStuck&&web>=0?web:web<0||web<prepared+80?prepared:web+150;
            // Taking over with an empty buffer freezes right after the switch on a slow connection, the web video keeps
            // playing meanwhile, so waiting for a little buffer costs nothing, past 8 seconds it switches anyway
            long ahead=nativePlayerFragment.bufferedPosition()-target;
            long now=android.os.SystemClock.elapsedRealtime();
            if(handoffBufferWaitStart==0)handoffBufferWaitStart=now;
            if(!webStuck&&ahead<2500&&now-handoffBufferWaitStart<8000){
                webView.postDelayed(this::syncNativeHandoff,250);
                return;
            }
            debugStage("handoffStart web="+web+" prepared="+prepared+" target="+target+" ahead="+ahead+" waited="+(now-handoffBufferWaitStart));
            handoffBufferWaitStart=0;
            nativePlayerFragment.seekAndPlay(target,nativeHandoffResumePlaying);
        });
    }
    @Override public void onNativeHandoffPlaying() {
        if(!nativeHandoffPending||webView==null||getView()==null)return;
        if(nativeVideoId!=null&&!nativeVideoId.equals(watchVideoId())){debugStage("handoffCancelled before swap");cancelNativeHandoff();return;}
        nativeHandoffPending=false;
        debugStage("handoffSwap");
        showSkipAd(false);
        // Keep the paused page ready for settings and a position-preserving return.
        webView.evaluateJavascript("(()=>{window.tubeNativePlayback=true;let p=document.querySelector('#movie_player,.html5-video-player');if(p&&p.pauseVideo)p.pauseVideo();document.querySelectorAll('video').forEach(v=>v.pause());if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});})()",null);
        // Marked active first so the web fullscreen exit below keeps the exit icon and the hidden toolbar
        nativePlayerActive=true;
        if(webView.isVideoFullscreen())webView.exitFullScreen();
        webView.setVisibility(View.INVISIBLE);
        getView().findViewById(R.id.full_screen_view).setVisibility(View.VISIBLE);
        setPlaybackControls(true,nativeHandoffResumePlaying);
        hideToolbar();hideCornerControls(false);
    }
    private static void debugStage(String message) {
        com.sya.tube.diag.Diag.log("car",message);
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics","stage "+message.replace(' ','_')+" elapsed="+android.os.SystemClock.elapsedRealtime());
    }
    private void cancelNativeHandoff() {
        if(!nativeHandoffPending)return;
        nativeHandoffPending=false;
        Fragment pending=getChildFragmentManager().findFragmentByTag(PLAYER_FRAGMENT_TAG);
        if(pending!=null)getChildFragmentManager().beginTransaction().remove(pending).commitNowAllowingStateLoss();
        nativePlayerFragment=null;
        if(getView()!=null&&!webView.isVideoFullscreen())getView().findViewById(R.id.full_screen_view).setVisibility(View.GONE);
        ImageButton button=getView()==null?null:getView().findViewById(R.id.fullscreen_button);
        if(button!=null){button.setActivated(false);button.setImageResource(R.drawable.tube_ic_fullscreen);button.setContentDescription("Fullscreen");}
        showToolbar();
    }
    private void leaveNativeVideo(boolean resume) {
        if(!nativePlayerActive||nativePlayerFragment==null)return;
        // A phone or server video has no page under it, closing it shows the library page again
        if(nativePlayerFragment.isMedia()){
            hideNativePlayer();
            ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(false);button.setImageResource(R.drawable.tube_ic_fullscreen);button.setContentDescription("Fullscreen");
            setPlaybackControls(false,false);showToolbar();hideCornerControls(false);
            return;
        }
        long position=nativePlayerFragment.position();boolean playing=nativePlayerFragment.playing();
        hideNativePlayer();webView.onResume();
        webView.evaluateJavascript("(()=>{window.tubeNativePlayback=false;let p=document.querySelector('#movie_player,.html5-video-player');if(p){p.seekTo("+(position/1000.0)+",true);"+(resume&&playing?"p.playVideo();":"p.pauseVideo();")+"}})()",null);
        ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(false);button.setImageResource(R.drawable.tube_ic_fullscreen);button.setContentDescription("Fullscreen");
        setPlaybackControls(true,resume&&playing);showToolbar();hideCornerControls(false);
    }

    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public AspectRatio getAspectRatio() {
        return this.currentAspectRatio;
    }

    public void setAspectRatio(AspectRatio aspectRatio) {
        this.currentAspectRatio = aspectRatio;
        this.aspectButton.setText(getHumanText(aspectRatio));
        if (this.nativePlayerActive) {
            this.nativePlayerFragment.setAspectRatio(aspectRatio);
        } else {
            this.webView.setAspectRatio(this.currentAspectRatio.name().toLowerCase());
        }
        getSharedPrefs().edit().putInt(ASPECT_RATIO_KEY, this.currentAspectRatio.ordinal()).apply();
    }

    @Override // com.thekirankumar.youtubeauto2.fragments.SafetyWarningFragment.FragmentInteractionListener
    public void onReadyToExitSafetyInstructions(SafetyWarningFragment safetyWarningFragment) {
        this.warningAccepted = true;
        hideWarningScreen();
    }

    /* JADX INFO: renamed from: com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment$27 */
    static /* synthetic */ class C094927 {

        /* JADX INFO: renamed from: $SwitchMap$com$thekirankumar$youtubeauto2$fragments$WebViewCarFragment$AspectRatio */
        static final /* synthetic */ int[] f503x542521dc;

        static {
            int[] iArr = new int[AspectRatio.values().length];
            f503x542521dc = iArr;
            try {
                iArr[AspectRatio.FILL.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                f503x542521dc[AspectRatio.COVER.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                f503x542521dc[AspectRatio.CONTAIN.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
        }
    }

    private String getHumanText(AspectRatio aspectRatio) {
        int i = C094927.f503x542521dc[aspectRatio.ordinal()];
        if (i == 1) {
            return getString(R.string.aspect_ratio_fill);
        }
        if (i != 2) {
            return i != 3 ? "" : getString(R.string.aspect_ratio_contain);
        }
        return getString(R.string.aspect_ratio_cover);
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkSelected(Bookmark bookmark) {
        hideNativePlayer();
        this.webView.loadUrl(bookmark.getUrl());
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkAddCurrentPage() {
        BookmarkUtils.addBookmark(this.webView);
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkDelete(Bookmark bookmark) {
        BookmarkUtils.deleteBookmark(bookmark);
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkFragmentClose() {
        hideBookmarksScreen();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showNativePlayer(String str) {
        if (isAdded()) {
            this.nativePlayerActive = true;
            this.webView.setVisibility(android.view.View.INVISIBLE);
            showCornerControls();
            getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.VISIBLE);
            FragmentManager childFragmentManager = getChildFragmentManager();
            ExoPlayerFragment exoPlayerFragmentNewInstance = (ExoPlayerFragment) childFragmentManager.findFragmentByTag(PLAYER_FRAGMENT_TAG);
            if (exoPlayerFragmentNewInstance == null) {
                exoPlayerFragmentNewInstance = ExoPlayerFragment.newInstance(str);
            }
            this.nativePlayerFragment = exoPlayerFragmentNewInstance;
            FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
            fragmentTransactionBeginTransaction.add(R.id.full_screen_view, exoPlayerFragmentNewInstance, PLAYER_FRAGMENT_TAG);
            fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            fragmentTransactionBeginTransaction.commitAllowingStateLoss();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideNativePlayer() {
        boolean remote=nativePlayerActive&&nativePlayerFragment!=null&&nativePlayerFragment.getArguments().containsKey("youtube_url");
        long position=remote?nativePlayerFragment.position():0;
        if (isAdded()) {
            this.nativePlayerActive = false;
            this.nativePlayerFragment = null;
            this.webView.setVisibility(android.view.View.VISIBLE);
            getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.GONE);
            FragmentManager childFragmentManager = getChildFragmentManager();
            Fragment fragmentFindFragmentByTag = childFragmentManager.findFragmentByTag(PLAYER_FRAGMENT_TAG);
            if (fragmentFindFragmentByTag != null) {
                FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
                fragmentTransactionBeginTransaction.remove(fragmentFindFragmentByTag);
                fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                fragmentTransactionBeginTransaction.commitNowAllowingStateLoss();
            }
        }
        if(remote&&webView!=null&&getView()!=null) {
            webView.onResume();
            webView.evaluateJavascript("(()=>{window.tubeNativePlayback=false;let p=document.querySelector('#movie_player,.html5-video-player');if(p){p.seekTo("+(position/1000.0)+",true);p.pauseVideo();}})()",null);
            ImageButton button=getView().findViewById(R.id.fullscreen_button);button.setActivated(false);button.setImageResource(R.drawable.tube_ic_fullscreen);button.setContentDescription("Fullscreen");
            showToolbar();hideCornerControls(false);setPlaybackControls(true,false);
        }
        if(this.webView!=null)this.webView.requestFocus();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload) {
            super.doUpdateVisitedHistory(view,url,reload);
            if(!PlaybackOwner.isContentVideo(WebViewCarFragment.this.webView)) {
                setPlaybackControls(false,false);
                if(PlaybackOwner.isActive(WebViewCarFragment.this.webView)&&getContext()!=null) {
                    playerState=android.media.session.PlaybackState.STATE_PAUSED;
                    com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPaused(getContext(),view.getTitle());
                }
            }
            resetQualityForVideo(url);
            if(getContext()!=null) getSharedPrefs().edit().putString(WebViewCarFragment.HOME_URL,url).apply();
            if(recentStrip!=null) recentStrip.update(com.sya.tube.remote.RecentStrip.isHome(url));
            if(webView!=null) webView.discoverVideoElements();
        }
        private CustomWebViewClient() {
        }

        @Override // android.webkit.WebViewClient
        public void onPageStarted(WebView webView, String str, Bitmap bitmap) {
            super.onPageStarted(webView, str, bitmap);
            setPlaybackControls(false,false);
            clearLoadError();
            resetQualityForVideo(str);
            WebViewCarFragment.this.progressBar.setVisibility(android.view.View.VISIBLE);
        }

        @Override // android.webkit.WebViewClient
        public void onUnhandledKeyEvent(WebView webView, KeyEvent keyEvent) {
            super.onUnhandledKeyEvent(webView, keyEvent);
            if (keyEvent.getAction() == 1) {
                if (!WebViewCarFragment.this.warningScreenOpen || keyEvent.getKeyCode() != 23) {
                    if (WebViewCarFragment.this.webView.isVideoFullscreen()) {
                        if (keyEvent.getKeyCode() == 19) {
                            WebViewCarFragment.this.showToolbar();
                            WebViewCarFragment.this.showCornerControls();
                            WebViewCarFragment.this.cornerControls.requestFocus(android.view.View.FOCUS_UP);
                            return;
                        }
                        if (keyEvent.getKeyCode() == 20) {
                            WebViewCarFragment.this.cornerControls.clearFocus();
                            WebViewCarFragment.this.hideToolbar();
                            WebViewCarFragment.this.hideCornerControls(true);
                            return;
                        } else {
                            if (keyEvent.getKeyCode() == 23) {
                                if (WebViewCarFragment.this.playerState == 3) {
                                    WebViewCarFragment.this.webView.pauseVideo();
                                    return;
                                } else {
                                    WebViewCarFragment.this.webView.playVideo();
                                    return;
                                }
                            }
                            if (keyEvent.getKeyCode() == 22) {
                                WebViewCarFragment.this.webView.seekBySeconds(10);
                                return;
                            } else {
                                if (keyEvent.getKeyCode() == 21) {
                                    WebViewCarFragment.this.webView.seekBySeconds(-10);
                                    return;
                                }
                                return;
                            }
                        }
                    }
                    return;
                }
                WebViewCarFragment.this.onReadyToExitSafetyInstructions(null);
            }
        }

        @Override // android.webkit.WebViewClient
        public void onPageFinished(WebView webView, String str) {
            WebViewCarFragment.this.progressBar.setVisibility(android.view.View.GONE);
            super.onPageFinished(webView, str);
            WebViewCarFragment.this.getSharedPrefs().edit().putString(WebViewCarFragment.HOME_URL, str).apply();
            WebviewUtils.injectNightModeCss(WebViewCarFragment.this.webView);
            WebviewUtils.injectCustomSelectDropDown(WebViewCarFragment.this.webView);
            WebviewUtils.injectQualityControl(WebViewCarFragment.this.webView);
            WebviewUtils.injectFileListingHack(WebViewCarFragment.this.webView);
            WebViewCarFragment.this.webView.discoverVideoElements();
            if (WebViewCarFragment.this.clickFirstVideoAfterPageLoad) {
                WebViewCarFragment.this.clickFirstVideoAfterPageLoad = false;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override // java.lang.Runnable
                    public void run() {
                        WebViewCarFragment.this.webView.findAndClickFirstVideo();
                    }
                }, 1000L);
            }
        }

        @Override // android.webkit.WebViewClient
        public void onReceivedError(WebView webView, WebResourceRequest webResourceRequest, WebResourceError webResourceError) {
            super.onReceivedError(webView, webResourceRequest, webResourceError);
            if(webResourceRequest.isForMainFrame()) showLoadError(webResourceRequest.getUrl().toString());
        }

        @Override public boolean shouldOverrideUrlLoading(WebView webView,WebResourceRequest request) {
            return shouldOverrideUrlLoading(webView,request.getUrl().toString());
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, String str) {
            if (com.sya.tube.library.LibraryPages.isPlayLink(str)) { playLibraryLink(str); return true; }
            if (WebViewCarFragment.this.nativePlayerActive) {
                WebViewCarFragment.this.hideNativePlayer();
            }
            if (str.startsWith("file:///") && com.thekirankumar.youtubeauto2.exoplayer.PlayerQueue.isMedia(android.net.Uri.parse(str).getLastPathSegment()==null?"":android.net.Uri.parse(str).getLastPathSegment())) {
                try {
                    WebViewCarFragment.this.showNativePlayer(URLDecoder.decode(str, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                return true;
            }
            if (str.startsWith("intent://")) {
                try {
                    Context context = webView.getContext();
                    new Intent();
                    Intent uri = Intent.parseUri(str, Intent.URI_INTENT_SCHEME);
                    if (uri != null) {
                        webView.stopLoading();
                        if (context.getPackageManager().resolveActivity(uri, 65536) != null) {
                            context.startActivity(uri);
                        } else {
                            webView.loadUrl(uri.getStringExtra("browser_fallback_url"));
                        }
                        return true;
                    }
                } catch (URISyntaxException e2) {
                    Log.e(WebViewCarFragment.TAG, "Can't resolve intent://", e2);
                }
            }
            return super.shouldOverrideUrlLoading(webView, str);
        }
    }
}
