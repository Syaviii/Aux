// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.bookmarks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.thekirankumar.youtubeauto2.utils.AspectRatioFrameLayout;
import java.util.ArrayList;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder> {
    private Bookmark addCurrentBookmark = new Bookmark();
    private ArrayList<Bookmark> bookmarks;
    private BookmarksClickCallback bookmarksClickCallback;
    private boolean deleteMode;

    public BookmarksAdapter(ArrayList<Bookmark> arrayList) {
        this.bookmarks = arrayList;
        setHasStableIds(true);
    }

    public void setBookmarksClickCallback(BookmarksClickCallback bookmarksClickCallback) {
        this.bookmarksClickCallback = bookmarksClickCallback;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public long getItemId(int i) {
        if (i >= this.bookmarks.size()) {
            return 0L;
        }
        Bookmark bookmark = this.bookmarks.get(i);
        if (true) {
            return bookmark.getCreatedAt();
        }
        return 0L;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public BookmarkViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new BookmarkViewHolder(viewGroup.getContext(), viewGroup);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onBindViewHolder(BookmarkViewHolder bookmarkViewHolder, int i) {
        if (i < this.bookmarks.size()) {
            final Bookmark bookmark = this.bookmarks.get(i);
            bookmarkViewHolder.setBookmark(bookmark);
            bookmarkViewHolder.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override // android.view.View.OnKeyListener
                public boolean onKey(View view, int i2, KeyEvent keyEvent) {
                    if (keyEvent.getAction() != 1 || i2 != 4) {
                        return false;
                    }
                    BookmarksAdapter.this.bookmarksClickCallback.onBookmarkFragmentClose();
                    return true;
                }
            });
            bookmarkViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    if (!BookmarksAdapter.this.deleteMode && BookmarksAdapter.this.bookmarksClickCallback != null) {
                        BookmarksAdapter.this.bookmarksClickCallback.onBookmarkSelected(bookmark);
                    }
                    if (BookmarksAdapter.this.deleteMode) {
                        BookmarksAdapter.this.deleteMode = false;
                    }
                    BookmarksAdapter.this.redrawDeleteMode();
                }
            });
            bookmarkViewHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    BookmarksAdapter.this.bookmarksClickCallback.onBookmarkDelete(bookmark);
                }
            });
            bookmarkViewHolder.itemView.setLongClickable(true);
            bookmarkViewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override // android.view.View.OnLongClickListener
                public boolean onLongClick(View view) {
                    if (BookmarksAdapter.this.deleteMode) {
                        BookmarksAdapter.this.deleteMode = false;
                    } else {
                        BookmarksAdapter.this.deleteMode = true;
                    }
                    BookmarksAdapter.this.redrawDeleteMode();
                    return true;
                }
            });
            if (bookmark.isPreventDelete()) {
                bookmarkViewHolder.setDeleteMode(false);
                return;
            } else {
                bookmarkViewHolder.setDeleteMode(this.deleteMode);
                return;
            }
        }
        bookmarkViewHolder.title.setText(R.string.add_bookmark);
        bookmarkViewHolder.itemView.setOnLongClickListener(null);
        bookmarkViewHolder.itemView.setLongClickable(false);
        bookmarkViewHolder.itemView.setOnKeyListener(null);
        bookmarkViewHolder.favicon.setVisibility(View.GONE);
        bookmarkViewHolder.setArtworkStyle(true);
        bookmarkViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (BookmarksAdapter.this.deleteMode) {
                    BookmarksAdapter.this.deleteMode = false;
                }
                BookmarksAdapter.this.redrawDeleteMode();
                if (BookmarksAdapter.this.bookmarksClickCallback != null) {
                    BookmarksAdapter.this.bookmarksClickCallback.onBookmarkAddCurrentPage();
                }
            }
        });
        bookmarkViewHolder.thumbnail.setImageResource(R.drawable.bookmark_ic_add);
        bookmarkViewHolder.setDeleteMode(false);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.bookmarks.size() + 1;
    }

    public void setBookmarks(ArrayList<Bookmark> arrayList) {
        this.bookmarks = arrayList;
    }

    public class BookmarkViewHolder extends RecyclerView.ViewHolder {
        private final ImageView delete;
        private final ImageView favicon;
        private final ImageView thumbnail;
        private final TextView title;

        public BookmarkViewHolder(Context context, ViewGroup viewGroup) {
            super(((LayoutInflater) context.getSystemService(android.content.Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.bookmark_layout, viewGroup, false));
            AspectRatioFrameLayout aspectRatioFrameLayout = this.itemView.findViewById(R.id.bookmark_artwork);
            aspectRatioFrameLayout.setAspectRatio(16f / 9f);
            aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
            this.title = (TextView) this.itemView.findViewById(R.id.bookmark_title);
            this.thumbnail = (ImageView) this.itemView.findViewById(R.id.bookmark_thumbnail);
            this.favicon = (ImageView) this.itemView.findViewById(R.id.bookmark_favicon);
            this.delete = (ImageView) this.itemView.findViewById(R.id.bookmark_delete);
        }

        public void setBookmark(Bookmark bookmark) {
            this.title.setText(bookmark.getTitle());
            this.delete.setContentDescription(itemView.getContext().getString(R.string.bookmark_delete, bookmark.getTitle()));
            setArtworkStyle(bookmark.isPreventDelete());
            byte[] thumbnail = bookmark.getThumbnail();
            int thumbnailResource = bookmark.getThumbnailResource();
            int faviconResource = bookmark.getFaviconResource();
            if (thumbnail != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
                this.thumbnail.setImageBitmap(BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, options));
            } else {
                this.thumbnail.setImageResource(thumbnailResource);
            }
            byte[] favicon = bookmark.getFavicon();
            if (favicon != null) {
                this.favicon.setVisibility(android.view.View.VISIBLE);
                this.favicon.setImageBitmap(BitmapFactory.decodeByteArray(favicon, 0, favicon.length));
            } else if (faviconResource > 0) {
                this.favicon.setVisibility(android.view.View.VISIBLE);
                this.favicon.setImageResource(faviconResource);
            } else {
                this.favicon.setVisibility(android.view.View.GONE);
                this.favicon.setImageBitmap(null);
            }
        }

        private void setArtworkStyle(boolean icon) {
            int padding = icon ? (int) (32 * itemView.getResources().getDisplayMetrics().density) : 0;
            thumbnail.setPadding(padding, padding, padding, padding);
            thumbnail.setScaleType(icon ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        }

        public void setDeleteMode(boolean z) {
            if (z) {
                this.delete.setVisibility(android.view.View.VISIBLE);
            } else {
                this.delete.setVisibility(android.view.View.GONE);
            }
        }
    }

    // Delete mode shows or hides the delete button on every card, the payload redraws them in place without the change fade
    private void redrawDeleteMode() { notifyItemRangeChanged(0, getItemCount(), "deleteMode"); }
}
