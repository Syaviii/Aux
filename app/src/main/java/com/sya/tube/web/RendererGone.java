package com.sya.tube.web;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * The page renderer crashed or Android stopped it for memory. Left alone the whole app closes, so the dead browser is
 * dropped and the screen is built again with a new one
 */
public final class RendererGone {
    public static boolean rebuild(Fragment fragment, WebView view, RenderProcessGoneDetail detail) {
        // Only Android 8 and later report this at all
        Log.w("AuxWeb", "page renderer gone, crashed " + (android.os.Build.VERSION.SDK_INT >= 26 && detail.didCrash()));
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.destroy();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!fragment.isAdded() || fragment.isRemoving()) return;
            FragmentManager manager = fragment.getParentFragmentManager();
            manager.beginTransaction().detach(fragment).commitNowAllowingStateLoss();
            manager.beginTransaction().attach(fragment).commitNowAllowingStateLoss();
        });
        return true;
    }

    private RendererGone() {}
}
