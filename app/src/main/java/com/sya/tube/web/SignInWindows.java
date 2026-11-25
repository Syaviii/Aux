package com.sya.tube.web;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sign-in windows a page opens, such as "Continue with Google" or Apple on Patreon. The provider's page reports back to
 * the page that opened it, so it runs in its own window above that page rather than replacing it, and closes itself
 * when signing in finishes
 */
public final class SignInWindows {
    private static final Object TAG = new Object();

    /** Lets a page open windows and use the cookies a sign-in provider's frames need */
    @SuppressLint("SetJavaScriptEnabled")
    public static void prepare(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        // Google's sign in turns away browsers it recognises as embedded by the wv marker
        String agent = settings.getUserAgentString();
        if (agent != null && agent.contains("; wv)"))
            settings.setUserAgentString(agent.replace("; wv)", ")").replaceFirst("Version/\\d+(\\.\\d+)* ", ""));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);
    }

    /** Opens the window the page asked for above it, the page keeps running underneath */
    public static boolean open(WebView opener, Message result) {
        if (!(opener.getParent() instanceof ViewGroup) || result == null) return false;
        ViewGroup host = (ViewGroup) opener.getParent();
        android.content.Context context = opener.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout frame = new LinearLayout(context);
        frame.setTag(TAG);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(Color.rgb(15, 15, 15));
        frame.setClickable(true);

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (16 * density), 0, (int) (8 * density), 0);
        TextView title = new TextView(context);
        title.setText("Loading sign in…");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = new TextView(context);
        close.setText("Close");
        close.setTextColor(Color.WHITE);
        close.setTextSize(16);
        close.setGravity(Gravity.CENTER);
        close.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
        close.setContentDescription("Close sign in");
        bar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (48 * density)));
        frame.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * density)));

        // A window opened from Aux's own browser gets the same page callbacks, so the car keyboard can type into it
        WebView made;
        com.thekirankumar.youtubeauto2.webview.JavascriptCallback callbacks = opener instanceof com.thekirankumar.youtubeauto2.webview.VideoWebView ? ((com.thekirankumar.youtubeauto2.webview.VideoWebView) opener).pageCallbacks : null;
        if (callbacks != null) {
            made = new com.thekirankumar.youtubeauto2.webview.VideoWebView(context);
            made.addJavascriptInterface(callbacks, "nativecallbacks");
        } else made = new WebView(context);
        final WebView window = made;
        prepare(window);
        // Dark until the provider's page draws, a blank white window reads as nothing happening
        window.setBackgroundColor(Color.rgb(15, 15, 15));
        window.setWebViewClient(new WebViewClient() {
            private boolean decided;
            // Sites also open plain links in a new window (Patreon's search results do), only a sign-in provider keeps
            // its window, anything else goes back into the page that opened it
            private boolean keep(WebView view, String url) {
                if (decided || url == null || url.startsWith("about:") || url.startsWith("javascript:")) return true;
                decided = true;
                if (isSignIn(url)) return true;
                remove(frame, window);
                opener.loadUrl(url);
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) { return !keep(view, request.getUrl().toString()); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { if (keep(view, url)) super.onPageStarted(view, url, icon); }
            @Override public void onPageFinished(WebView view, String url) { if ("Loading sign in…".contentEquals(title.getText())) title.setText("Sign in"); }
            // The page renderer crashed or was stopped for memory, the window closes rather than the whole app
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) { remove(frame, window); return true; }
        });
        window.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String text) { title.setText(text == null || text.isEmpty() ? "Sign in" : text); }
            // Sign-in flows keep to one window, a further window from inside it is not opened
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, Message message) { return false; }
            @Override public void onCloseWindow(WebView view) { remove(frame, window); }
        });
        frame.addView(window, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        close.setOnClickListener(v -> remove(frame, window));

        host.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.bringToFront();
        WebView.WebViewTransport transport = (WebView.WebViewTransport) result.obj;
        transport.setWebView(window);
        result.sendToTarget();
        return true;
    }

    /** Google, Apple, Facebook and other sign-in pages, which report back to the page that opened them */
    static boolean isSignIn(String url) {
        android.net.Uri uri = android.net.Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost(), path = uri.getPath() == null ? "" : uri.getPath();
        return host.equals("accounts.google.com") || host.endsWith(".apple.com") || host.endsWith("facebook.com")
            || host.endsWith("discord.com") || host.endsWith("twitch.tv") && path.startsWith("/login") || host.equals("id.twitch.tv")
            || path.contains("oauth") || path.contains("/login") || path.contains("signin") || path.contains("/auth");
    }

    /** Closes the window the page asked to close */
    public static void close(WebView window) {
        if (window == null) return;
        View frame = (View) window.getParent();
        if (frame != null && frame.getTag() == TAG) remove(frame, window);
    }

    /** The sign-in window open above a page, or null */
    public static WebView top(WebView opener) {
        if (opener == null || !(opener.getParent() instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) opener.getParent();
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            if (child.getTag() == TAG) return (WebView) ((ViewGroup) child).getChildAt(1);
        }
        return null;
    }

    /** Back closes an open sign-in window before anything else, true when it did */
    public static boolean closeTop(WebView opener) {
        if (opener == null || !(opener.getParent() instanceof ViewGroup)) return false;
        ViewGroup host = (ViewGroup) opener.getParent();
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            if (child.getTag() != TAG) continue;
            WebView window = (WebView) ((ViewGroup) child).getChildAt(1);
            if (window.canGoBack()) window.goBack(); else remove(child, window);
            return true;
        }
        return false;
    }

    private static void remove(View frame, WebView window) {
        if (frame.getParent() instanceof ViewGroup) ((ViewGroup) frame.getParent()).removeView(frame);
        window.stopLoading();
        window.destroy();
        CookieManager.getInstance().flush();
    }

    private SignInWindows() {}
}
