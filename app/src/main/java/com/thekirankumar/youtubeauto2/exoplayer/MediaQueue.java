// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.exoplayer;

/* JADX INFO: loaded from: classes.dex */
interface MediaQueue {
    boolean hasNext();

    boolean hasPrevious();

    void next();

    void previous();

    void resetPosition(int i);
}
