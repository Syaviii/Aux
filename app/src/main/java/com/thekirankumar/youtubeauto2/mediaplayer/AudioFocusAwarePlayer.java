// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.mediaplayer;

/* JADX INFO: loaded from: classes.dex */
public interface AudioFocusAwarePlayer {
    boolean isPlaying();

    void pause();

    void play();

    void setVolume(float f);

    void stop();
}
