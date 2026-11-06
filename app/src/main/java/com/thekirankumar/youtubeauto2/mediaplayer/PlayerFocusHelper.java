// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.mediaplayer;

import android.content.Context;

/* JADX INFO: loaded from: classes.dex */
public class PlayerFocusHelper extends PlayerAdapter {
    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    public boolean isPlaying() {
        return false;
    }

    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    protected void onPause() {
    }

    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    protected void onPlay() {
    }

    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    protected void onStop() {
    }

    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    public void seekTo(long j) {
    }

    @Override // com.thekirankumar.youtubeauto2.mediaplayer.PlayerAdapter
    public void setVolume(float f) {
    }

    public PlayerFocusHelper(Context context) {
        super(context);
    }
}
