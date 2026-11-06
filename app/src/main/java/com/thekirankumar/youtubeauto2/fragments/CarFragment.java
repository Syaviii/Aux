// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.fragments;

import androidx.fragment.app.Fragment;

/* JADX INFO: loaded from: classes.dex */
public class CarFragment extends Fragment {
    private String mTitle;

    public String getTitle() {
        return this.mTitle;
    }

    public void setTitle(String str) {
        this.mTitle = str;
    }

    public void setTitle(int i) {
        this.mTitle = getContext().getString(i);
    }
}
