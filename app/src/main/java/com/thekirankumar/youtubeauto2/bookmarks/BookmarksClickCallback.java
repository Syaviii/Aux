// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.bookmarks;

/* JADX INFO: loaded from: classes.dex */
public interface BookmarksClickCallback {
    void onBookmarkAddCurrentPage();

    void onBookmarkDelete(Bookmark bookmark);

    void onBookmarkFragmentClose();

    void onBookmarkSelected(Bookmark bookmark);
}
