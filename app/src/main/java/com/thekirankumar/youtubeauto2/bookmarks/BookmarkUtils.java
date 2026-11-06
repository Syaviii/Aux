// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.bookmarks;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.ByteArrayOutputStream;
import com.thekirankumar.youtubeauto2.webview.VideoWebView;
public final class BookmarkUtils {
    public static void addBookmark(VideoWebView view) {
        String url=view.getUrl(); if(url==null||url.isBlank()||url.equals("about:blank")) return;
        Bookmark b=new Bookmark(); b.setUrl(url); b.setTitle(view.getTitle()==null?url:view.getTitle());
        int w=view.getWidth(),h=view.getHeight();
        if(w>0&&h>0) {
            Bitmap image=Bitmap.createBitmap(540,Math.max(1,Math.min(720,540*h/w)),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image); canvas.scale(540f/w,540f/w); view.draw(canvas);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG,80,bytes);
            b.setThumbnail(bytes.toByteArray()); image.recycle();
        }
        if(view.getFavicon()!=null) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); view.getFavicon().compress(Bitmap.CompressFormat.PNG,100,bytes); b.setFavicon(bytes.toByteArray());
        }
        BookmarkStore.get().save(b);
    }
    public static void deleteBookmark(Bookmark b) { BookmarkStore.get().delete(b); }
}
