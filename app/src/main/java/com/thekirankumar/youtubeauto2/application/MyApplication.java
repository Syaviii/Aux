// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.application;
import android.app.Application;
import com.thekirankumar.youtubeauto2.utils.AdBlocker;
public class MyApplication extends Application {
    private static MyApplication instance;
    public static MyApplication getInstance() { return instance; }
    @Override public void onCreate() {
        super.onCreate(); instance=this; com.sya.tube.diag.Diag.init(this); AdBlocker.init(this);
        // The process starts when the car launcher icon is tapped, so warming here overlaps with Android Auto opening the car screen
        String home=getSharedPreferences(com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment.PREFS,0).getString(com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment.HOME_URL,com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment.YOUTUBE_HOME_URL_BASE);
        com.sya.tube.web.WebWarmUp.start(this,home);
    }
}
