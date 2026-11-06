// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.activity;

import android.os.Bundle;
import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;
import com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment;
import com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class MainPhoneActivity extends AppCompatActivity implements ExoPlayerFragment.OnFragmentInteractionListener {
    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public void onNativePlayerControlsVisibilityChange(int i) {
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_phone_main);
        // Test builds can be made to crash on purpose to check crash reports, release builds have no such path
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG && getIntent().getBooleanExtra("aux.testCrash", false))
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { throw new IllegalStateException("Test crash from a test build"); }, 1500);
        // The car screen cannot show permission prompts, so the microphone for voice search and, on Android 13 and later,
        // the media controls notification are asked for here once
        java.util.List<String> wanted = new java.util.ArrayList<>();
        wanted.add(android.Manifest.permission.RECORD_AUDIO);
        if (android.os.Build.VERSION.SDK_INT >= 33) wanted.add(android.Manifest.permission.POST_NOTIFICATIONS);
        wanted.removeIf(p -> androidx.core.content.ContextCompat.checkSelfPermission(this, p) == android.content.pm.PackageManager.PERMISSION_GRANTED);
        if (!wanted.isEmpty()) androidx.core.app.ActivityCompat.requestPermissions(this, wanted.toArray(new String[0]), 7);
    }

    // Picks are sent to the car only right after a real tap, reopening the phone on an old video page must not replace what plays
    public static long lastTouchAt;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        lastTouchAt = android.os.SystemClock.elapsedRealtime();
        return super.dispatchTouchEvent(event);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyUp(int i, KeyEvent keyEvent) {
        return super.onKeyUp(i, keyEvent);
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        return super.onKeyDown(i, keyEvent);
    }

    @Override // com.thekirankumar.youtubeauto2.exoplayer.ExoPlayerFragment.OnFragmentInteractionListener
    public WebViewCarFragment.AspectRatio getAspectRatio() {
        return WebViewCarFragment.AspectRatio.CONTAIN;
    }
}
