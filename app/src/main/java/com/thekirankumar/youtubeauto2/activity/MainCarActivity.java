// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.thekirankumar.youtubeauto2.R;
import com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment;
import com.sya.tube.legacy.SearchController;
import com.sya.tube.car.CarBridge;
import java.lang.ref.WeakReference;
import java.util.HashSet;
public class MainCarActivity extends AppCompatActivity implements com.sya.tube.legacy.CarUiHost {
    private static WeakReference<MainCarActivity> current=new WeakReference<>(null);
    private final HashSet<ActivityCallbacks> callbacks=new HashSet<>();
    private SearchController search;
    public static MainCarActivity current() { return current.get(); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); current=new WeakReference<>(this); search=new SearchController(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(6150);
        setContentView(R.layout.activity_car_main);
        if(state==null) { WebViewCarFragment fragment=new WebViewCarFragment(); fragment.setCarHost(this); getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).commitNow(); }
        startService(new android.content.Intent(this,com.thekirankumar.youtubeauto2.service.MyMediaBrowserService.class));
    }
    public SearchController getSearchController() { return search; }
    public android.view.Window getCarWindow() { return getWindow(); }
    public SearchController getCarInputController() { return search; }
    public void addActivityCallback(ActivityCallbacks c) { callbacks.add(c); }
    public void removeActivityCallback(ActivityCallbacks c) { callbacks.remove(c); }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); for(ActivityCallbacks c:new HashSet<>(callbacks)) c.onConfigChanged();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); for(ActivityCallbacks c:new HashSet<>(callbacks)) c.onWindowFocusChanged(hasFocus);
    }
    @Override protected void onDestroy() { if(current.get()==this) current.clear(); super.onDestroy(); }
    public interface ActivityCallbacks { void onConfigChanged(); void onWindowFocusChanged(boolean focused); }
}
