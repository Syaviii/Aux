// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.exoplayer2.C;
import com.thekirankumar.youtubeauto2.BuildConfig;
import com.thekirankumar.youtubeauto2.activity.SettingsPhoneActivity;
import com.thekirankumar.youtubeauto2.bookmarks.Bookmark;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarkUtils;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback;
import com.thekirankumar.youtubeauto2.bookmarks.BookmarksFragment;
import com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment;
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService;
import com.thekirankumar.youtubeauto2.utils.AdBlocker;
import com.thekirankumar.youtubeauto2.utils.WebviewUtils;
import com.thekirankumar.youtubeauto2.webview.VideoEnabledWebChromeClient;
import com.thekirankumar.youtubeauto2.webview.VideoEnabledWebView;
import com.thekirankumar.youtubeauto2.webview.VideoWebView;
import com.thekirankumar.youtubeauto2.webview.PlaybackOwner;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class WebViewPhoneFragment extends CarFragment implements BookmarksClickCallback, ExoPlayerFragment.OnFragmentInteractionListener, com.thekirankumar.youtubeauto2.webview.JavascriptCallback.JSCallbacks, com.sya.tube.web.WebSegments.Listener {
    private static final String BOOKMARKS_FRAGMENT_TAG = "bookmarks";
    private static final String PLAYER_FRAGMENT_TAG = "player";
    private static final String PREFS = "phone_prefs";
    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 10;
    private final String TAG = "WebViewCarFragment";
    private EditText editText;
    private MediaBrowserCompat mediaBrowser;
    private MediaSessionCompat mediaSessionCompat;
    private ProgressBar progressBar;
    private SharedPreferences sharedPrefs;
    private VideoWebView webView;
    private android.content.BroadcastReceiver playbackReceiver;

    private long getAvailableActions() {
        return 3632L;
    }

    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public WebViewCarFragment.AspectRatio getAspectRatio() {
        return WebViewCarFragment.AspectRatio.CONTAIN;
    }

    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public void onNativePlayerControlsVisibilityChange(int i) {
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Activity activity) {
        super.onAttach(activity);
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

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.fragment_webview_phone, viewGroup, false);
    }

    // The phone screen has no action bar, the top bar's menu button opens the same items as a popup
    private void showMenu(View anchor) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.phone_menu);
        popup.getMenu().findItem(R.id.back).setEnabled(webView != null && webView.canGoBack());
        popup.getMenu().findItem(R.id.follow_channel).setVisible(webView != null && com.sya.tube.library.SubscriptionImport.canFollowFrom(webView.getUrl()));
        popup.setOnMenuItemClickListener(this::onOptionsItemSelected);
        popup.show();
    }

    /** Leaves whatever covers the page and opens YouTube's home feed, or scrolls it back to the top when already there */
    private void goHome() {
        if (webView == null) return;
        if (getChildFragmentManager().findFragmentByTag(PLAYER_FRAGMENT_TAG) != null) hideNativePlayer();
        if (getChildFragmentManager().findFragmentByTag(BOOKMARKS_FRAGMENT_TAG) != null) hideBookmarksScreen();
        if (webView.isVideoFullscreen()) webView.exitFullScreen();
        editText.setText("");
        editText.clearFocus();
        hideKeyboard(editText);
        Uri current = webView.getUrl() == null ? null : Uri.parse(webView.getUrl());
        boolean atHome = current != null && current.getHost() != null && current.getHost().endsWith("youtube.com")
            && (current.getPath() == null || current.getPath().isEmpty() || current.getPath().equals("/"));
        if (atHome) webView.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null);
        else webView.loadUrl(PHONE_HOME_URL);
    }

    private static final String PREF_CAR_HINT_DISMISSED = "phone_car_hint_dismissed";
    private static final String PHONE_HOME_URL = "https://m.youtube.com/";

    /** The search field shows the query on a results page and stays empty elsewhere, raw page addresses are noise here */
    private void showQuery(String url) {
        if (editText == null || editText.hasFocus() || url == null) return;
        String query = null;
        try { query = Uri.parse(url).getQueryParameter("search_query"); } catch (Exception ignored) { }
        String text = query == null ? "" : query;
        if (!text.equals(editText.getText().toString())) editText.setText(text);
    }

    @Override // androidx.fragment.app.Fragment
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.back) {
            this.webView.goBack();
        } else if (menuItem.getItemId() == R.id.refresh) {
            this.webView.reload();
        } else if (menuItem.getItemId() == R.id.bookmark_button) {
            showBookmarksScreen();
        } else if (menuItem.getItemId() == R.id.library) {
            webView.loadUrl(com.sya.tube.library.LibraryPages.HOME);
        } else if (menuItem.getItemId() == R.id.follow_channel) {
            followChannel();
        } else if (menuItem.getItemId() == R.id.settings) {
            startActivity(new Intent(getContext(), (Class<?>) SettingsPhoneActivity.class));
        }
        return true;
    }

    // Following needs the channel's id, a handle or a video page only says it after a lookup
    private final java.util.concurrent.ExecutorService work = java.util.concurrent.Executors.newSingleThreadExecutor();
    private void followChannel() {
        String url = webView == null ? null : webView.getUrl();
        if (url == null) return;
        toast("Finding the channel");
        work.execute(() -> {
            try {
                com.sya.tube.library.Subscriptions.Channel channel = com.sya.tube.library.SubscriptionImport.resolve(url);
                if (webView != null) webView.post(() -> {
                    if (getContext() == null) return;
                    if (com.sya.tube.library.Subscriptions.follows(getContext(), channel.id)) {
                        new AlertDialog.Builder(requireContext()).setTitle(channel.name).setMessage("You follow this channel in Aux")
                            .setPositiveButton("Unfollow", (d, w) -> { com.sya.tube.library.Subscriptions.remove(getContext(), channel.id); toast("Unfollowed " + channel.name); })
                            .setNegativeButton(android.R.string.cancel, null).show();
                        return;
                    }
                    com.sya.tube.library.Subscriptions.add(getContext(), java.util.Collections.singletonList(channel));
                    com.sya.tube.library.Subscriptions.refresh(getContext());
                    toast("Following " + channel.name + ", find it in Library");
                });
            } catch (Exception error) {
                com.sya.tube.diag.Diag.log("follow", "lookup failed " + error.getClass().getSimpleName() + " " + error.getMessage());
                if (webView != null) webView.post(() -> toast("Couldn't find this channel, check your connection"));
            }
        });
    }

    private void unlock() {
        new android.app.AlertDialog.Builder(requireContext()).setTitle("Android Auto")
            .setMessage("Aux uses the development car surface. Enable Unknown sources in Android Auto developer settings. Root changes to Google services are not needed.")
            .setPositiveButton(android.R.string.ok, null).show();
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        new com.sya.tube.remote.CarRemoteBar(view.findViewById(R.id.car_remote)).setOnCarTick(() -> followCar(false));
        // A recent video continues on the car while it is connected, otherwise here on the phone
        recentStrip=new com.sya.tube.remote.RecentStrip(view.findViewById(R.id.recent_strip),item->{
            com.sya.tube.remote.CarRemote.Car car=com.sya.tube.remote.CarRemote.car();
            if(car!=null){car.remotePlay(item.url());toast("Playing on the car");}
            else if(webView!=null)webView.loadUrl(item.url());
        });
        this.progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        this.webView = (VideoWebView) view.findViewById(R.id.web_view);
        this.editText = (EditText) view.findViewById(R.id.edittext_url);
        view.findViewById(R.id.home_button).setOnClickListener(v -> goHome());
        view.findViewById(R.id.menu_button).setOnClickListener(this::showMenu);
        View clear = view.findViewById(R.id.search_clear);
        clear.setOnClickListener(v -> { editText.setText(""); editText.requestFocus(); });
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) { clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE); }
        });
        View hint = view.findViewById(R.id.car_hint);
        if (!getSharedPrefs().getBoolean(PREF_CAR_HINT_DISMISSED, false)) hint.setVisibility(View.VISIBLE);
        view.findViewById(R.id.car_hint_close).setOnClickListener(v -> {
            hint.setVisibility(View.GONE);
            getSharedPrefs().edit().putBoolean(PREF_CAR_HINT_DISMISSED, true).apply();
        });
        this.webView.addJavascriptInterface(new com.thekirankumar.youtubeauto2.webview.JavascriptCallback(this),"nativecallbacks");
        com.sya.tube.web.WebSegments.attach(this.webView,this);
        final Context appContext=requireContext().getApplicationContext();
        playbackReceiver=new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context context,Intent intent) {
                if(webView==null || !PlaybackOwner.isActive(webView) || !isResumed() || getChildFragmentManager().findFragmentByTag(PLAYER_FRAGMENT_TAG)!=null) return;
                long action=intent.getLongExtra(MyMediaBrowserService.ACTION_TYPE,0);
                if(action==android.media.session.PlaybackState.ACTION_PLAY) webView.playVideo();
                else if(action==android.media.session.PlaybackState.ACTION_PAUSE||action==android.media.session.PlaybackState.ACTION_STOP) webView.pauseVideo();
                else if(action==android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT) webView.playNextTrack();
                else if(action==android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS) webView.goBack();
            }
        };
        ContextCompat.registerReceiver(requireContext(),playbackReceiver,new android.content.IntentFilter(MyMediaBrowserService.PLAYER_EVENT),ContextCompat.RECEIVER_NOT_EXPORTED);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if(getChildFragmentManager().findFragmentByTag(PLAYER_FRAGMENT_TAG)!=null) { hideNativePlayer(); return; }
                if(getChildFragmentManager().findFragmentByTag(BOOKMARKS_FRAGMENT_TAG)!=null) { hideBookmarksScreen(); return; }
                if(webView.isVideoFullscreen()) { webView.exitFullScreen(); return; }
                if(webView.canGoBack()) { webView.goBack(); return; }
                setEnabled(false); requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        getActivity().startService(new Intent(getActivity(), (Class<?>) MyMediaBrowserService.class));
        MediaBrowserCompat mediaBrowserCompat = new MediaBrowserCompat(getActivity(), new ComponentName(getActivity(), (Class<?>) MyMediaBrowserService.class), new MediaBrowserCompat.ConnectionCallback() {
            @Override // android.support.v4.media.MediaBrowserCompat.ConnectionCallback
            public void onConnected() {
                try {
                    Log.v("MainActivity", "connected");
                    MediaControllerCompat.setMediaController(WebViewPhoneFragment.this.getActivity(), new MediaControllerCompat(WebViewPhoneFragment.this.getActivity(), WebViewPhoneFragment.this.mediaBrowser.getSessionToken()));
                } catch (IllegalStateException unavailable) {
                    Log.w("Tube", "Media session unavailable", unavailable);
                }
            }

            @Override // android.support.v4.media.MediaBrowserCompat.ConnectionCallback
            public void onConnectionSuspended() {
                super.onConnectionSuspended();
            }

            @Override // android.support.v4.media.MediaBrowserCompat.ConnectionCallback
            public void onConnectionFailed() {
                super.onConnectionFailed();
            }
        }, null);
        this.mediaBrowser = mediaBrowserCompat;
        mediaBrowserCompat.connect();
        ViewGroup viewGroup = (ViewGroup) view.findViewById(R.id.container);
        ViewGroup viewGroup2 = (ViewGroup) view.findViewById(R.id.full_screen_view);
        this.webView.setWebViewClient(new CustomWebViewClient() {
            private Map<String, Boolean> mLoadedUrls = new java.util.concurrent.ConcurrentHashMap<>();

            @Override // android.webkit.WebViewClient
            public WebResourceResponse shouldInterceptRequest(WebView webView, android.webkit.WebResourceRequest request) { return shouldInterceptRequest(webView,request.getUrl().toString()); }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, String str) {
                WebResourceResponse library=com.sya.tube.library.LibraryPages.intercept(appContext,str);
                if(library!=null) return library;
                WebResourceResponse local=com.thekirankumar.youtubeauto2.utils.LocalFileBrowser.intercept(str);
                if(local!=null) return local;
                boolean zBooleanValue;
                if (!this.mLoadedUrls.containsKey(str)) {
                    zBooleanValue = AdBlocker.isAd(str) || blockAds && com.sya.tube.web.YouTubeAdBlock.isAdRequest(str);
                    this.mLoadedUrls.put(str, Boolean.valueOf(zBooleanValue));
                } else {
                    zBooleanValue = this.mLoadedUrls.get(str).booleanValue();
                }
                if (zBooleanValue) {
                    return AdBlocker.createEmptyResource();
                }
                return super.shouldInterceptRequest(webView, str);
            }
        });
        this.webView.setWebChromeClient(new VideoEnabledWebChromeClient(viewGroup, viewGroup2, new ProgressBar(getActivity()), this.webView));
        this.webView.useCaptureDiscovery();
        com.sya.tube.web.WebTuning.applyPhone(this.webView);
        blockAds=android.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_block_ads", true);
        com.sya.tube.web.YouTubeAdBlock.apply(this.webView, blockAds);
        this.webView.getSettings().setJavaScriptEnabled(true);
        // Google's sign in turns away browsers it recognises as embedded by the wv marker, signing in here also signs the car
        // in since both screens share one cookie store
        String agent=this.webView.getSettings().getUserAgentString();
        if(agent!=null)this.webView.getSettings().setUserAgentString(agent.replace("; wv)",")").replaceFirst("Version/\\d+(\\.\\d+)* ",""));
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        this.webView.getSettings().setAllowFileAccess(true);
        this.webView.getSettings().setDomStorageEnabled(true);
        WebviewUtils.setDesktopMode(this.webView, PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_enable_desktop_mode", false));
        if (Build.VERSION.SDK_INT >= 16) {
            this.webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            this.webView.getSettings().setAllowFileAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= 17) {
            this.webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }
        this.webView.getSettings().setAllowContentAccess(true);
        this.webView.loadUrl(getSharedPrefs().getString(WebViewCarFragment.HOME_URL, WebViewCarFragment.YOUTUBE_HOME_URL_BASE));
        this.webView.requestFocus();
        this.webView.setOnKeyListener(new View.OnKeyListener() {
            @Override // android.view.View.OnKeyListener
            public boolean onKey(View view2, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() != 0) {
                    return false;
                }
                WebView webView = (WebView) view2;
                if (i != 4 || !webView.canGoBack()) {
                    return false;
                }
                webView.goBack();
                return true;
            }
        });
        this.editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != 6 && i != 2 && i != 3 && (keyEvent == null || keyEvent.getKeyCode() != 66)) {
                    return false;
                }
                WebViewPhoneFragment webViewPhoneFragment = WebViewPhoneFragment.this;
                webViewPhoneFragment.onDone(webViewPhoneFragment.editText, WebViewPhoneFragment.this.webView);
                WebViewPhoneFragment webViewPhoneFragment2 = WebViewPhoneFragment.this;
                webViewPhoneFragment2.hideKeyboard(webViewPhoneFragment2.editText);
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideKeyboard(EditText editText) {
        InputMethodManager inputMethodManager = (InputMethodManager) editText.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onDone(EditText editText, VideoEnabledWebView videoEnabledWebView) {
        String string = editText.getText().toString().trim();
        editText.clearFocus();
        if (string.isEmpty()) return;
        // A pasted link opens as is, anything else is a YouTube search
        if (URLUtil.isValidUrl(string) && string.contains("://")) { videoEnabledWebView.loadUrl(string); return; }
        if (!string.contains(" ") && string.matches("[\\w.-]+\\.[a-z]{2,}(/.*)?") && URLUtil.isValidUrl("https://" + string)) { videoEnabledWebView.loadUrl("https://" + string); return; }
        videoEnabledWebView.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(string));
    }

    // A crash from the last run is offered once, with crash reports turned on, the driver sees the text before sending it
    private void offerCrashReport() {
        String crash = com.sya.tube.diag.Diag.pendingCrash();
        if (crash == null || getContext() == null) return;
        com.sya.tube.diag.Diag.clearCrash();
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext()).setTitle("Aux closed unexpectedly")
            .setMessage("A crash report is ready. It has the Aux and Android versions, the phone model and where in Aux it happened, no account details\n\n" + crash.split("\n\n")[0])
            .setNeutralButton("Not now", null)
            .setNegativeButton("Share", (d, w) -> {
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Aux crash report").putExtra(Intent.EXTRA_TEXT, crash);
                startActivity(Intent.createChooser(share, "Send the crash report"));
            });
        if (com.sya.tube.update.Updates.enabled()) dialog.setPositiveButton("Report on GitHub", (d, w) -> {
            String body = "**What happened**\n\n\n**Crash report**\n```\n" + (crash.length() > 5000 ? crash.substring(0, 5000) : crash) + "\n```";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(com.sya.tube.update.Updates.repoPage() + "/issues/new?title=" + Uri.encode("Crash: ") + "&body=" + Uri.encode(body))));
        });
        dialog.show();
    }

    // A newer release shows as a card under the search bar until it is installed or waved off
    private View updateCard;
    private void offerUpdate() {
        if (getContext() == null || !com.sya.tube.update.Updates.enabled()) return;
        com.sya.tube.update.Updates.check(requireContext(), false, release -> {
            if (getView() == null || release == null || !release.newer() || com.sya.tube.update.Updates.dismissed(requireContext(), release)) return;
            if (updateCard != null) return;
            com.sya.tube.settings.SettingsUi ui = new com.sya.tube.settings.SettingsUi(requireContext());
            LinearLayout card = new LinearLayout(requireContext());
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_hint_card);
            card.setPadding(ui.dp(14), ui.dp(8), ui.dp(8), ui.dp(8));
            TextView text = ui.text("Aux " + release.version + " is available", 14, requireContext().getColor(R.color.tube_on_surface), false);
            card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            TextView later = ui.pill("Later", false);
            later.setOnClickListener(v -> { com.sya.tube.update.Updates.dismiss(requireContext(), release.version); removeUpdateCard(); });
            card.addView(later, new LinearLayout.LayoutParams(-2, ui.dp(34)));
            TextView update = ui.pill("Update", true);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, ui.dp(34));
            place.setMarginStart(ui.dp(8));
            card.addView(update, place);
            update.setOnClickListener(v -> com.sya.tube.update.Updates.install(requireContext(), release, new com.sya.tube.update.Updates.Progress() {
                @Override public void onProgress(int percent) { text.setText("Downloading Aux " + release.version + ", " + percent + "%"); }
                @Override public void onDone(boolean started, String problem) {
                    if (problem != null) { text.setText("Aux " + release.version + " is available"); toast(problem); }
                    else removeUpdateCard();
                }
            }));
            LinearLayout container = getView().findViewById(R.id.container);
            LinearLayout.LayoutParams cardPlace = new LinearLayout.LayoutParams(-1, -2);
            cardPlace.setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(8));
            container.addView(card, 1, cardPlace);
            updateCard = card;
        });
    }

    private void removeUpdateCard() {
        if (updateCard != null && updateCard.getParent() instanceof ViewGroup) ((ViewGroup) updateCard.getParent()).removeView(updateCard);
        updateCard = null;
    }

    private String lastCarVideoId;
    private com.sya.tube.remote.RecentStrip recentStrip;

    /**
     * Opening the phone shows the video the car plays, and a phone still on the car's previous video follows it to the
     * next one, a phone browsing anything else is left where it is
     */
    private void followCar(boolean opening) {
        com.sya.tube.remote.CarRemote.Car car=com.sya.tube.remote.CarRemote.car();
        if(car==null||webView==null||!isResumed())return;
        String carId=car.remoteVideoId();
        if(carId==null)return;
        String phoneId=com.sya.tube.remote.CarRemote.videoId(webView.getUrl());
        boolean showingOldCarVideo=lastCarVideoId!=null&&lastCarVideoId.equals(phoneId);
        boolean carChanged=!carId.equals(lastCarVideoId);
        lastCarVideoId=carId;
        if(carId.equals(phoneId))return;
        if(opening||carChanged&&showingOldCarVideo) webView.loadUrl("https://m.youtube.com/watch?v="+carId);
    }

    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
        followCar(true);
        offerCrashReport();
        offerUpdate();
        if(recentStrip!=null&&webView!=null)recentStrip.update(com.sya.tube.remote.RecentStrip.isHome(webView.getUrl()));
        this.webView.requestFocus();
        // YouTube needs no phone media or microphone permission. Request optional access only from the menu.
        WebviewUtils.setDesktopMode(this.webView,PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("pref_enable_desktop_mode",false));
    }

    @Override public void onPause() { if(webView!=null) webView.pauseVideo(); android.webkit.CookieManager.getInstance().flush(); super.onPause(); }
    @Override public void onDestroyView() {
        if(playbackReceiver!=null) { requireContext().unregisterReceiver(playbackReceiver); playbackReceiver=null; }
        if(mediaBrowser!=null) { mediaBrowser.disconnect(); mediaBrowser=null; }
        if(webView!=null) { webView.destroy(); webView=null; }
        super.onDestroyView();
    }
    @Override public void onVideoElementDiscovered() { if(webView!=null) { webView.attachVideoListeners(); applySegments(); } }

    private void applyWebSpeed() {
        if(webView==null||getContext()==null)return;
        String speed=String.valueOf(com.sya.tube.settings.TubePrefs.speed(getContext()));
        webView.evaluateJavascript("(()=>{let v=window.tubeActiveVideo?window.tubeActiveVideo():document.querySelector('video');let p=document.querySelector('#movie_player,.html5-video-player');"
            +"if(!v||Math.abs(v.playbackRate-"+speed+")<0.01)return;if(p&&p.setPlaybackRate)p.setPlaybackRate("+speed+");v.playbackRate="+speed+";})()",null);
    }

    // SponsorBlock on the phone's own playback, the same settings as the car
    private String segmentsVideoId;
    private java.util.List<com.sya.tube.player.SponsorBlock.Segment> segments=java.util.Collections.emptyList();
    private android.widget.TextView segmentButton;
    private void lookupSegments(String id) {
        if(id==null||id.equals(segmentsVideoId)||getContext()==null)return;
        segmentsVideoId=id;segments=java.util.Collections.emptyList();
        onSegmentOfferGone();
        com.sya.tube.player.SponsorBlock.lookup(getContext(),id,found->{ if(id.equals(segmentsVideoId)){segments=found;applySegments();} });
    }
    private void applySegments() { if(webView!=null&&segmentsVideoId!=null)com.sya.tube.web.WebSegments.apply(webView,segmentsVideoId,segments); }
    @Override public void onSegmentSkipped(String category) { toast("Skipped "+com.sya.tube.settings.TubePrefs.segmentNoun(category)); }
    @Override public void onSegmentOffer(String category) {
        if(getView()==null)return;
        if(segmentButton==null){
            segmentButton=new android.widget.TextView(getContext());
            segmentButton.setTextColor(android.graphics.Color.BLACK);
            segmentButton.setTextSize(15);
            segmentButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            segmentButton.setGravity(android.view.Gravity.CENTER);
            int pad=com.sya.tube.car.CarStyle.dp(getContext(),18);
            segmentButton.setPadding(pad,0,pad,0);
            segmentButton.setBackground(com.sya.tube.car.CarStyle.rounded(android.graphics.Color.WHITE,com.sya.tube.car.CarStyle.dp(getContext(),22)));
            segmentButton.setOnClickListener(v->{com.sya.tube.web.WebSegments.skipOffered(webView);onSegmentOfferGone();});
        }
        segmentButton.setText("Skip "+com.sya.tube.settings.TubePrefs.segmentNoun(category));
        if(segmentButton.getParent()==null){
            android.widget.FrameLayout.LayoutParams place=new android.widget.FrameLayout.LayoutParams(-2,com.sya.tube.car.CarStyle.dp(getContext(),44),android.view.Gravity.BOTTOM|android.view.Gravity.END);
            int margin=com.sya.tube.car.CarStyle.dp(getContext(),16);
            place.setMargins(margin,margin,margin,margin*5);
            ((ViewGroup)getView()).addView(segmentButton,place);
        }
    }
    @Override public void onSegmentOfferGone() {
        if(segmentButton!=null&&segmentButton.getParent() instanceof ViewGroup)((ViewGroup)segmentButton.getParent()).removeView(segmentButton);
    }

    // A phone video or server video picked here plays on the car while it is connected, otherwise on the phone
    private void playLibraryLink(String link) {
        com.sya.tube.remote.CarRemote.Car car=com.sya.tube.remote.CarRemote.car();
        if(car!=null){car.remotePlayLink(link);toast("Playing on the car");return;}
        if(getContext()==null)return;
        Context app=getContext().getApplicationContext();
        work.execute(()->{
            try {
                com.sya.tube.library.MediaServers.Queue queue=com.sya.tube.library.LibraryPages.resolve(app,link);
                if(webView!=null)webView.post(()->showMediaPlayer(queue));
            } catch(Exception error) {
                if(webView!=null)webView.post(()->toast(error instanceof SecurityException&&error.getMessage()!=null?error.getMessage():"Can't play this video right now"));
            }
        });
    }

    private void showMediaPlayer(com.sya.tube.library.MediaServers.Queue queue) {
        if(!isAdded()||getView()==null||queue.items.isEmpty())return;
        getView().findViewById(R.id.full_screen_view).setVisibility(View.VISIBLE);
        webView.pauseVideo();
        getChildFragmentManager().beginTransaction().replace(R.id.full_screen_view,ExoPlayerFragment.media(queue.items,queue.index),PLAYER_FRAGMENT_TAG).commitAllowingStateLoss();
    }
    @Override public void onJSVideoEvent(String event) {
        if(webView==null || getContext()==null || getChildFragmentManager().findFragmentByTag(PLAYER_FRAGMENT_TAG)!=null) return;
        // With the car screen up the phone is only a remote, a video starting here would steal playback from the car
        if("playing".equals(event)&&com.sya.tube.remote.CarRemote.car()!=null){
            webView.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())",null);
            return;
        }
        if(!PlaybackOwner.isContentVideo(webView)) return;
        if("playing".equals(event)) { PlaybackOwner.playing(webView); applyWebSpeed(); }
        if(!PlaybackOwner.isActive(webView))return;
        String title=webView.getTitle();
        if("playing".equals(event)) com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPlaying(requireContext(),title);
        else if("pause".equals(event)||"ended".equals(event)) com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastPaused(requireContext(),title);
        else if("waiting".equals(event)) com.thekirankumar.youtubeauto2.utils.BroadcastFromUI.broadCastLoading(requireContext());
    }
    @Override public void onShowKeyboardFromJS(String value) { }
    @Override public void onHideKeyboardFromJS() { }
    @Override public void onVideoCurrentTimeResult(int time) { }

    private void handleExternalSdCard() {
        if (isReadExternalFilesGranted()) {
            return;
        }
        requestPermissionForReadExtertalStorage();
    }

    public void requestPermissionForReadExtertalStorage() {
        try {
            ActivityCompat.requestPermissions(getActivity(), new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 10);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isReadExternalFilesGranted() {
        return ContextCompat.checkSelfPermission(getContext(), "android.permission.READ_EXTERNAL_STORAGE") == 0;
    }

    public void askForRecordAudioPermission() {
        if (ActivityCompat.checkSelfPermission(getActivity(), "android.permission.RECORD_AUDIO") != 0) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), "android.permission.RECORD_AUDIO")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Need Microphone Permission");
                builder.setMessage("To use voice input feature in your car, you need to grant permissions. With voice input, you can search on Youtube with your voice. \nFor e.g., say 'Coldplay' and it will search for Coldplay on Youtube");
                builder.setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                        ActivityCompat.requestPermissions(WebViewPhoneFragment.this.getActivity(), new String[]{"android.permission.RECORD_AUDIO"}, 1);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                builder.show();
                return;
            }
            ActivityCompat.requestPermissions(getActivity(), new String[]{"android.permission.RECORD_AUDIO"}, 1);
        }
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkSelected(Bookmark bookmark) {
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

    private void hideBookmarksScreen() {
        if (isAdded()) {
            getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.GONE);
            FragmentManager childFragmentManager = getChildFragmentManager();
            Fragment fragmentFindFragmentByTag = childFragmentManager.findFragmentByTag("bookmarks");
            if (fragmentFindFragmentByTag != null) {
                FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
                fragmentTransactionBeginTransaction.remove(fragmentFindFragmentByTag);
                fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                fragmentTransactionBeginTransaction.commitAllowingStateLoss();
            }
        }
        this.webView.requestFocus();
    }

    private void showBookmarksScreen() {
        if (!isAdded() || getView() == null) {
            return;
        }
        getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.VISIBLE);
        FragmentManager childFragmentManager = getChildFragmentManager();
        BookmarksFragment bookmarksFragment = (BookmarksFragment) childFragmentManager.findFragmentByTag("bookmarks");
        if (bookmarksFragment == null) {
            bookmarksFragment = new BookmarksFragment();
        }
        FragmentTransaction fragmentTransactionBeginTransaction = childFragmentManager.beginTransaction();
        fragmentTransactionBeginTransaction.replace(R.id.full_screen_view, bookmarksFragment, "bookmarks");
        fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        fragmentTransactionBeginTransaction.commitAllowingStateLoss();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showNativePlayer(String str) {
        if (isAdded()) {
            getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.VISIBLE);
            this.webView.pauseVideo();
            FragmentManager fragmentManager = getChildFragmentManager();
            ExoPlayerFragment exoPlayerFragmentNewInstance = (ExoPlayerFragment) fragmentManager.findFragmentByTag(PLAYER_FRAGMENT_TAG);
            if (exoPlayerFragmentNewInstance == null) {
                exoPlayerFragmentNewInstance = ExoPlayerFragment.newInstance(str);
            }
            FragmentTransaction fragmentTransactionBeginTransaction = fragmentManager.beginTransaction();
            fragmentTransactionBeginTransaction.replace(R.id.full_screen_view, exoPlayerFragmentNewInstance, PLAYER_FRAGMENT_TAG);
            fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            fragmentTransactionBeginTransaction.commitAllowingStateLoss();
        }
    }

    private void hideNativePlayer() {
        if (isAdded()) {
            getView().findViewById(R.id.full_screen_view).setVisibility(android.view.View.GONE);
            FragmentManager fragmentManager = getChildFragmentManager();
            Fragment fragmentFindFragmentByTag = fragmentManager.findFragmentByTag(PLAYER_FRAGMENT_TAG);
            if (fragmentFindFragmentByTag != null) {
                FragmentTransaction fragmentTransactionBeginTransaction = fragmentManager.beginTransaction();
                fragmentTransactionBeginTransaction.remove(fragmentFindFragmentByTag);
                fragmentTransactionBeginTransaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
                fragmentTransactionBeginTransaction.commitAllowingStateLoss();
            }
        }
        this.webView.requestFocus();
    }

    // Read on the WebView's network thread
    private volatile boolean blockAds=true;
    private String lastSentToCar;
    private long lastSentAt;

    /**
     * While Android Auto shows Tube, picking a video on the phone plays it on the car screen instead,
     * the phone returns to the list it came from so the next pick is one tap away
     */
    private boolean sendToCar(String url) {
        com.sya.tube.remote.CarRemote.Car car=com.sya.tube.remote.CarRemote.car();
        String playable=com.sya.tube.remote.CarRemote.playableUrl(url);
        if(car==null||playable==null||webView==null)return false;
        // The car already plays it, the phone just shows its page instead of restarting it on the car
        if(com.sya.tube.remote.CarRemote.videoId(playable).equals(car.remoteVideoId()))return false;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-com.thekirankumar.youtubeauto2.activity.MainPhoneActivity.lastTouchAt>5000)return false;
        // The same pick arrives through more than one hook, only the first one counts
        if(playable.equals(lastSentToCar)&&now-lastSentAt<3000)return true;
        lastSentToCar=playable;lastSentAt=now;
        webView.evaluateJavascript("document.querySelectorAll('video').forEach(v=>{v.muted=true;v.pause();})",null);
        webView.postDelayed(()->{ if(webView!=null&&webView.canGoBack()&&com.sya.tube.remote.CarRemote.playableUrl(webView.getUrl())!=null)webView.goBack(); },150);
        if(car.remoteVideoId()==null||getContext()==null){ car.remotePlay(playable); toast("Playing on the car"); return true; }
        // Something already plays on the car, so the pick can replace it or wait its turn
        new AlertDialog.Builder(requireContext()).setTitle("Play on the car")
            .setItems(new String[]{"Play now","Play next"},(dialog,which)->{
                com.sya.tube.remote.CarRemote.Car target=com.sya.tube.remote.CarRemote.car();
                if(target==null||getContext()==null)return;
                if(which==0){target.remotePlay(playable);toast("Playing on the car");}
                else{com.sya.tube.remote.UpNext.add(getContext(),playable);toast("Added to Up next");}
            })
            .setNegativeButton(android.R.string.cancel,null).show();
        return true;
    }

    private void toast(String message) {
        if(getContext()!=null)android.widget.Toast.makeText(getContext(),message,android.widget.Toast.LENGTH_SHORT).show();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload) {
            super.doUpdateVisitedHistory(view,url,reload);
            if(sendToCar(url))return;
            lookupSegments(com.sya.tube.remote.CarRemote.videoId(url));
            if(getContext()!=null) getSharedPrefs().edit().putString(WebViewCarFragment.HOME_URL,url).apply();
            showQuery(url);
            if(recentStrip!=null)recentStrip.update(com.sya.tube.remote.RecentStrip.isHome(url));
            if(webView!=null) webView.discoverVideoElements();
        }
        private CustomWebViewClient() {
        }

        @Override // android.webkit.WebViewClient
        public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest webResourceRequest) {
            return super.shouldInterceptRequest(webView, webResourceRequest);
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
            return shouldOverrideUrlLoading(webView,webResourceRequest.getUrl().toString());
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, final String str) {
            if(com.sya.tube.library.LibraryPages.isPlayLink(str)){playLibraryLink(str);return true;}
            if(sendToCar(str))return true;
            if (str.startsWith("file:///") && com.thekirankumar.youtubeauto2.exoplayer.PlayerQueue.isMedia(android.net.Uri.parse(str).getLastPathSegment()==null?"":android.net.Uri.parse(str).getLastPathSegment())) {
                webView.post(new Runnable() {
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            WebViewPhoneFragment.this.showNativePlayer(URLDecoder.decode(str, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                });
                return true;
            }
            if (!str.startsWith("intent://")) {
                return false;
            }
            try {
                Context context = webView.getContext();
                new Intent();
                Intent uri = Intent.parseUri(str, Intent.URI_INTENT_SCHEME);
                if (uri == null) {
                    return false;
                }
                webView.stopLoading();
                if (context.getPackageManager().resolveActivity(uri, 65536) != null) {
                    context.startActivity(uri);
                } else {
                    webView.loadUrl(uri.getStringExtra("browser_fallback_url"));
                }
                return true;
            } catch (URISyntaxException e) {
                Log.e("WebViewCarFragment", "Can't resolve intent://", e);
                return false;
            }
        }

        @Override // android.webkit.WebViewClient
        public void onPageStarted(WebView webView, String str, Bitmap bitmap) {
            super.onPageStarted(webView, str, bitmap);
            WebViewPhoneFragment.this.progressBar.setVisibility(android.view.View.VISIBLE);
        }

        @Override // android.webkit.WebViewClient
        public void onPageFinished(WebView webView, String str) {
            showQuery(str);
            WebViewPhoneFragment.this.progressBar.setVisibility(android.view.View.GONE);
            Log.d("WebViewCarFragment", "page finished " + str);
            super.onPageFinished(webView, str);
            WebviewUtils.injectNightModeCss(WebViewPhoneFragment.this.webView);
            WebviewUtils.injectCustomSelectDropDown(WebViewPhoneFragment.this.webView);
            WebviewUtils.injectQualityControl(WebViewPhoneFragment.this.webView);
            WebviewUtils.injectFileListingHack(WebViewPhoneFragment.this.webView);
            WebViewPhoneFragment.this.webView.discoverVideoElements();
            WebViewPhoneFragment.this.getSharedPrefs().edit().putString(WebViewCarFragment.HOME_URL, str).commit();
        }
    }
}
