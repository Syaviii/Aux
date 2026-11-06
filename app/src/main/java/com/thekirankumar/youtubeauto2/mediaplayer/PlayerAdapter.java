// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.mediaplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

/* JADX INFO: loaded from: classes.dex */
public abstract class PlayerAdapter {
    private static final IntentFilter AUDIO_NOISY_INTENT_FILTER = new IntentFilter("android.media.AUDIO_BECOMING_NOISY");
    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    private static final float MEDIA_VOLUME_DUCK = 0.2f;
    private final Context mApplicationContext;
    private final AudioFocusHelper mAudioFocusHelper;
    private final AudioManager mAudioManager;
    private boolean mAudioNoisyReceiverRegistered = false;
    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("android.media.AUDIO_BECOMING_NOISY".equals(intent.getAction()) && PlayerAdapter.this.isPlaying()) {
                PlayerAdapter.this.pause();
            }
        }
    };
    private boolean mPlayOnAudioFocus = false;

    public abstract boolean isPlaying();

    protected abstract void onPause();

    protected abstract void onPlay();

    protected abstract void onStop();

    public abstract void seekTo(long j);

    public abstract void setVolume(float f);

    public PlayerAdapter(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.mApplicationContext = applicationContext;
        this.mAudioManager = (AudioManager) applicationContext.getSystemService(android.content.Context.AUDIO_SERVICE);
        this.mAudioFocusHelper = new AudioFocusHelper();
    }

    public final void play() {
        if (this.mAudioFocusHelper.requestAudioFocus()) {
            registerAudioNoisyReceiver();
            onPlay();
        }
    }

    public final void pause() {
        if (!this.mPlayOnAudioFocus) {
            this.mAudioFocusHelper.abandonAudioFocus();
        }
        unregisterAudioNoisyReceiver();
        onPause();
    }

    public final void stop() {
        this.mAudioFocusHelper.abandonAudioFocus();
        unregisterAudioNoisyReceiver();
        onStop();
    }

    private void registerAudioNoisyReceiver() {
        if (this.mAudioNoisyReceiverRegistered) {
            return;
        }
        androidx.core.content.ContextCompat.registerReceiver(this.mApplicationContext, this.mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        this.mAudioNoisyReceiverRegistered = true;
    }

    private void unregisterAudioNoisyReceiver() {
        if (this.mAudioNoisyReceiverRegistered) {
            this.mApplicationContext.unregisterReceiver(this.mAudioNoisyReceiver);
            this.mAudioNoisyReceiverRegistered = false;
        }
    }

    private final class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
        private AudioFocusHelper() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean requestAudioFocus() {
            return PlayerAdapter.this.mAudioManager.requestAudioFocus(this, 3, 1) == 1;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void abandonAudioFocus() {
            PlayerAdapter.this.mAudioManager.abandonAudioFocus(this);
        }

        @Override // android.media.AudioManager.OnAudioFocusChangeListener
        public void onAudioFocusChange(int i) {
            if (i == -3) {
                PlayerAdapter.this.setVolume(PlayerAdapter.MEDIA_VOLUME_DUCK);
                return;
            }
            if (i == -2) {
                if (PlayerAdapter.this.isPlaying()) {
                    PlayerAdapter.this.mPlayOnAudioFocus = true;
                    PlayerAdapter.this.pause();
                    return;
                }
                return;
            }
            if (i == -1) {
                PlayerAdapter.this.mAudioManager.abandonAudioFocus(this);
                PlayerAdapter.this.mPlayOnAudioFocus = false;
                PlayerAdapter.this.stop();
            } else {
                if (i != 1) {
                    return;
                }
                if (PlayerAdapter.this.mPlayOnAudioFocus && !PlayerAdapter.this.isPlaying()) {
                    PlayerAdapter.this.play();
                } else if (PlayerAdapter.this.isPlaying()) {
                    PlayerAdapter.this.setVolume(1.0f);
                }
                PlayerAdapter.this.mPlayOnAudioFocus = false;
            }
        }
    }
}
