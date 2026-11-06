// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.mediaplayer;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import androidx.media.AudioAttributesCompat;

/* JADX INFO: loaded from: classes.dex */
public class AudioFocusHelper {
    private static final String TAG = "AudioFocusHelper";
    private DefaultAudioFocusListener mDefaultChangeListener;
    private final AudioFocusHelperImpl mImpl;

    interface AudioFocusHelperImpl {
        void abandonAudioFocus();

        boolean requestAudioFocus(AudioFocusRequestCompat audioFocusRequestCompat);

        boolean willPauseWhenDucked();
    }

    public AudioFocusHelper(Context context) {
        AudioManager audioManager = (AudioManager) context.getApplicationContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            this.mImpl = new AudioFocusHelperImplApi26(audioManager);
        } else {
            this.mImpl = new AudioFocusHelperImplBase(audioManager);
        }
    }

    public AudioManager.OnAudioFocusChangeListener getListenerForPlayer(AudioFocusAwarePlayer audioFocusAwarePlayer) {
        DefaultAudioFocusListener defaultAudioFocusListener = this.mDefaultChangeListener;
        if (defaultAudioFocusListener != null && defaultAudioFocusListener.getPlayer().equals(audioFocusAwarePlayer)) {
            return this.mDefaultChangeListener;
        }
        DefaultAudioFocusListener defaultAudioFocusListener2 = new DefaultAudioFocusListener(this.mImpl, audioFocusAwarePlayer);
        this.mDefaultChangeListener = defaultAudioFocusListener2;
        return defaultAudioFocusListener2;
    }

    public boolean requestAudioFocus(AudioFocusRequestCompat audioFocusRequestCompat) {
        return this.mImpl.requestAudioFocus(audioFocusRequestCompat);
    }

    public void abandonAudioFocus(AudioFocusRequestCompat audioFocusRequestCompat) {
        this.mImpl.abandonAudioFocus();
    }

    private static class AudioFocusHelperImplBase implements AudioFocusHelperImpl {
        AudioFocusRequestCompat mAudioFocusRequestCompat;
        final AudioManager mAudioManager;

        AudioFocusHelperImplBase(AudioManager audioManager) {
            this.mAudioManager = audioManager;
        }

        @Override // com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImpl
        public boolean requestAudioFocus(AudioFocusRequestCompat audioFocusRequestCompat) {
            this.mAudioFocusRequestCompat = audioFocusRequestCompat;
            if (audioFocusRequestCompat.acceptsDelayedFocusGain()) {
                Log.w(AudioFocusHelper.TAG, "Cannot request delayed focus", new UnsupportedOperationException("Cannot request delayed focus on API " + Build.VERSION.SDK_INT).fillInStackTrace());
            }
            return this.mAudioManager.requestAudioFocus(this.mAudioFocusRequestCompat.getOnAudioFocusChangeListener(), this.mAudioFocusRequestCompat.getAudioAttributesCompat().getLegacyStreamType(), this.mAudioFocusRequestCompat.getFocusGain()) == 1;
        }

        @Override // com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImpl
        public void abandonAudioFocus() {
            AudioFocusRequestCompat audioFocusRequestCompat = this.mAudioFocusRequestCompat;
            if (audioFocusRequestCompat == null) {
                return;
            }
            this.mAudioManager.abandonAudioFocus(audioFocusRequestCompat.getOnAudioFocusChangeListener());
        }

        @Override // com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImpl
        public boolean willPauseWhenDucked() {
            AudioFocusRequestCompat audioFocusRequestCompat = this.mAudioFocusRequestCompat;
            if (audioFocusRequestCompat == null) {
                return false;
            }
            AudioAttributesCompat audioAttributesCompat = audioFocusRequestCompat.getAudioAttributesCompat();
            return this.mAudioFocusRequestCompat.willPauseWhenDucked() || (audioAttributesCompat != null && audioAttributesCompat.getContentType() == 1);
        }
    }

    @androidx.annotation.RequiresApi(26)
    private static class AudioFocusHelperImplApi26 extends AudioFocusHelperImplBase {
        private AudioFocusRequest mAudioFocusRequest;

        AudioFocusHelperImplApi26(AudioManager audioManager) {
            super(audioManager);
        }

        @Override // com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImplBase, com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImpl
        public boolean requestAudioFocus(AudioFocusRequestCompat audioFocusRequestCompat) {
            this.mAudioFocusRequestCompat = audioFocusRequestCompat;
            this.mAudioFocusRequest = audioFocusRequestCompat.getAudioFocusRequest();
            return this.mAudioManager.requestAudioFocus(this.mAudioFocusRequest) == 1;
        }

        @Override // com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImplBase, com.thekirankumar.youtubeauto2.mediaplayer.AudioFocusHelper.AudioFocusHelperImpl
        public void abandonAudioFocus() {
            this.mAudioManager.abandonAudioFocusRequest(this.mAudioFocusRequest);
        }
    }

    private static class DefaultAudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
        private static final float MEDIA_VOLUME_DUCK = 0.2f;
        private final AudioFocusHelperImpl mImpl;
        private final AudioFocusAwarePlayer mPlayer;
        private boolean mResumeOnFocusGain;

        private DefaultAudioFocusListener(AudioFocusHelperImpl audioFocusHelperImpl, AudioFocusAwarePlayer audioFocusAwarePlayer) {
            this.mResumeOnFocusGain = false;
            this.mImpl = audioFocusHelperImpl;
            this.mPlayer = audioFocusAwarePlayer;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public AudioFocusAwarePlayer getPlayer() {
            return this.mPlayer;
        }

        @Override // android.media.AudioManager.OnAudioFocusChangeListener
        public void onAudioFocusChange(int i) {
            if (i != -3) {
                if (i != -2) {
                    if (i == -1) {
                        this.mResumeOnFocusGain = false;
                        this.mPlayer.stop();
                        this.mImpl.abandonAudioFocus();
                        return;
                    } else {
                        if (i != 1) {
                            return;
                        }
                        if (this.mResumeOnFocusGain) {
                            this.mPlayer.play();
                            this.mResumeOnFocusGain = false;
                            return;
                        } else {
                            if (this.mPlayer.isPlaying()) {
                                this.mPlayer.setVolume(1.0f);
                                return;
                            }
                            return;
                        }
                    }
                }
            } else if (!this.mImpl.willPauseWhenDucked()) {
                this.mPlayer.setVolume(MEDIA_VOLUME_DUCK);
                return;
            }
            this.mResumeOnFocusGain = this.mPlayer.isPlaying();
            this.mPlayer.pause();
        }
    }
}
