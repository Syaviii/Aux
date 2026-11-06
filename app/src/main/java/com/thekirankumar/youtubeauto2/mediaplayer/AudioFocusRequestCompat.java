// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.mediaplayer;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import androidx.media.AudioAttributesCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/* JADX INFO: loaded from: classes.dex */
public class AudioFocusRequestCompat {
    private final boolean mAcceptsDelayedFocusGain;
    private final AudioAttributesCompat mAudioAttributesCompat;
    private final Handler mFocusChangeHandler;
    private final int mFocusGain;
    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener;
    private final boolean mPauseOnDuck;

    @Retention(RetentionPolicy.SOURCE)
    public @interface FocusGain {
    }

    private AudioFocusRequestCompat(int i, AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener, Handler handler, AudioAttributesCompat audioAttributesCompat, boolean z, boolean z2) {
        this.mFocusGain = i;
        this.mOnAudioFocusChangeListener = onAudioFocusChangeListener;
        this.mFocusChangeHandler = handler;
        this.mAudioAttributesCompat = audioAttributesCompat;
        this.mPauseOnDuck = z;
        this.mAcceptsDelayedFocusGain = z2;
    }

    public int getFocusGain() {
        return this.mFocusGain;
    }

    public AudioAttributesCompat getAudioAttributesCompat() {
        return this.mAudioAttributesCompat;
    }

    public boolean willPauseWhenDucked() {
        return this.mPauseOnDuck;
    }

    public boolean acceptsDelayedFocusGain() {
        return this.mAcceptsDelayedFocusGain;
    }

    AudioManager.OnAudioFocusChangeListener getOnAudioFocusChangeListener() {
        return this.mOnAudioFocusChangeListener;
    }

    Handler getFocusChangeHandler() {
        return this.mFocusChangeHandler;
    }

    AudioAttributes getAudioAttributes() {
        AudioAttributesCompat audioAttributesCompat = this.mAudioAttributesCompat;
        if (audioAttributesCompat != null) {
            return (AudioAttributes) audioAttributesCompat.unwrap();
        }
        return null;
    }

    @androidx.annotation.RequiresApi(26)
    AudioFocusRequest getAudioFocusRequest() {
        return new AudioFocusRequest.Builder(this.mFocusGain).setAudioAttributes(getAudioAttributes()).setAcceptsDelayedFocusGain(this.mAcceptsDelayedFocusGain).setWillPauseWhenDucked(this.mPauseOnDuck).setOnAudioFocusChangeListener(this.mOnAudioFocusChangeListener, this.mFocusChangeHandler).build();
    }

    public static final class Builder {
        private boolean mAcceptsDelayedFocusGain;
        private AudioAttributesCompat mAudioAttributesCompat;
        private Handler mFocusChangeHandler;
        private int mFocusGain;
        private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener;
        private boolean mPauseOnDuck;

        public Builder(int i) {
            this.mFocusGain = i;
        }

        public Builder(AudioFocusRequestCompat audioFocusRequestCompat) {
            this.mFocusGain = audioFocusRequestCompat.mFocusGain;
            this.mOnAudioFocusChangeListener = audioFocusRequestCompat.mOnAudioFocusChangeListener;
            this.mFocusChangeHandler = audioFocusRequestCompat.mFocusChangeHandler;
            this.mAudioAttributesCompat = audioFocusRequestCompat.mAudioAttributesCompat;
            this.mPauseOnDuck = audioFocusRequestCompat.mPauseOnDuck;
            this.mAcceptsDelayedFocusGain = audioFocusRequestCompat.mAcceptsDelayedFocusGain;
        }

        public Builder setFocusGain(int i) {
            this.mFocusGain = i;
            return this;
        }

        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener) {
            return setOnAudioFocusChangeListener(onAudioFocusChangeListener, new Handler(Looper.getMainLooper()));
        }

        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener, Handler handler) {
            this.mOnAudioFocusChangeListener = onAudioFocusChangeListener;
            this.mFocusChangeHandler = handler;
            return this;
        }

        public Builder setAudioAttributes(AudioAttributesCompat audioAttributesCompat) {
            this.mAudioAttributesCompat = audioAttributesCompat;
            return this;
        }

        public Builder setWillPauseWhenDucked(boolean z) {
            this.mPauseOnDuck = z;
            return this;
        }

        public Builder setAcceptsDelayedFocusGain(boolean z) {
            this.mAcceptsDelayedFocusGain = z;
            return this;
        }

        public AudioFocusRequestCompat build() {
            return new AudioFocusRequestCompat(this.mFocusGain, this.mOnAudioFocusChangeListener, this.mFocusChangeHandler, this.mAudioAttributesCompat, this.mPauseOnDuck, this.mAcceptsDelayedFocusGain);
        }
    }
}
