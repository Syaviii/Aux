// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.bookmarks;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import com.thekirankumar.youtubeauto2.exoplayer.PlayerQueue;
import com.thekirankumar.youtubeauto2.utils.GridAutofitLayoutManager;
import com.thekirankumar.youtubeauto2.utils.MemoryStorage;
import java.util.ArrayList;
import androidx.annotation.Nullable;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class BookmarksFragment extends Fragment implements BookmarksClickCallback {
    public static final float BOOKMARK_VIEW_SIZE_IN_DP = 180.0f;
    private ArrayList<Bookmark> bookmarks;
    private BookmarksAdapter adapter;
    private BookmarksClickCallback listener;
    private RecyclerView recyclerView;

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.bookmarks_fragment, viewGroup, false);
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                BookmarksFragment.this.listener.onBookmarkFragmentClose();
            }
        });
        toolbar.setTitle(R.string.bookmarks);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        this.recyclerView = recyclerView;
        recyclerView.setOnKeyListener(new View.OnKeyListener() {
            @Override // android.view.View.OnKeyListener
            public boolean onKey(View view2, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() != 1 || i != 4) {
                    return false;
                }
                BookmarksFragment.this.listener.onBookmarkFragmentClose();
                return true;
            }
        });
        this.recyclerView.setLayoutManager(new GridAutofitLayoutManager(getContext(), (int) TypedValue.applyDimension(1, 180.0f, view.getResources().getDisplayMetrics())));
        this.recyclerView.setItemAnimator(new DefaultItemAnimator());
        ArrayList<Bookmark> preburntBookmarks = getPreburntBookmarks();
        preburntBookmarks.addAll(BookmarkStore.get().all());
        final BookmarksAdapter bookmarksAdapter = new BookmarksAdapter(preburntBookmarks);
        this.adapter = bookmarksAdapter;
        bookmarksAdapter.setBookmarksClickCallback(this);
        this.recyclerView.setAdapter(bookmarksAdapter);
        bookmarksAdapter.notifyDataSetChanged();
        this.recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override // android.view.ViewTreeObserver.OnGlobalLayoutListener
            public void onGlobalLayout() {
                BookmarksFragment.this.recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                BookmarksFragment.this.recyclerView.smoothScrollToPosition(0);
                if (BookmarksFragment.this.recyclerView.getChildCount() > 0) {
                    BookmarksFragment.this.recyclerView.getChildAt(0).requestFocus();
                }
            }
        });
    }

    private void refresh() {
        ArrayList<Bookmark> entries=getPreburntBookmarks(); entries.addAll(BookmarkStore.get().all());
        if(adapter!=null) { adapter.setBookmarks(entries); adapter.notifyDataSetChanged(); }
    }
    @Override // androidx.fragment.app.Fragment
    public void onResume() {
        super.onResume();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public ArrayList<Bookmark> getPreburntBookmarks() {
        ArrayList<Bookmark> arrayList = new ArrayList<>();
        arrayList.add(new Bookmark("YouTube", R.drawable.youtube_favicon, "https://youtube.com", R.drawable.youtube_bookmark));
        arrayList.add(new Bookmark("YouTube TV", R.drawable.youtube_favicon, "https://youtube.com/tv", R.drawable.youtube_bookmark));
        String[] storageDirectories = MemoryStorage.getStorageDirectories(getContext());
        String sdCardPath = MemoryStorage.getSdCardPath();
        for (String str : storageDirectories) {
            if (sdCardPath.startsWith(str)) {
                arrayList.add(new Bookmark(getString(R.string.internal_storage), 0, PlayerQueue.FILE_PREFIX + str, R.drawable.external_storage));
            } else {
                arrayList.add(new Bookmark(getString(R.string.external_storage), 0, PlayerQueue.FILE_PREFIX + str, R.drawable.external_storage));
            }
        }
        arrayList.add(new Bookmark("Plex.tv", 0, "https://app.plex.tv", R.drawable.plex));
        return arrayList;
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Context context) {
        super.onAttach(context);
        findParentListener(context);
    }

    @Override // androidx.fragment.app.Fragment
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        findParentListener(activity);
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void findParentListener(Context context) {
        if (getParentFragment() != null && (getParentFragment() instanceof BookmarksClickCallback)) {
            this.listener = (BookmarksClickCallback) getParentFragment();
            return;
        }
        if (context instanceof BookmarksClickCallback) {
            this.listener = (BookmarksClickCallback) context;
            return;
        }
        throw new IllegalStateException(context + " should implement " + BookmarksClickCallback.class.getSimpleName());
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkSelected(Bookmark bookmark) {
        this.listener.onBookmarkSelected(bookmark);
        this.listener.onBookmarkFragmentClose();
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkAddCurrentPage() {
        this.listener.onBookmarkAddCurrentPage();
        refresh();
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkDelete(Bookmark bookmark) {
        this.listener.onBookmarkDelete(bookmark);
        refresh();
    }

    @Override // com.thekirankumar.youtubeauto2.bookmarks.BookmarksClickCallback
    public void onBookmarkFragmentClose() {
        this.listener.onBookmarkFragmentClose();
    }
}
