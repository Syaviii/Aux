// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.bookmarks;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.thekirankumar.youtubeauto2.application.MyApplication;
import java.util.ArrayList;
public final class BookmarkStore extends SQLiteOpenHelper {
    private static BookmarkStore instance;
    public static synchronized BookmarkStore get() {
        if(instance==null) instance=new BookmarkStore(); return instance;
    }
    private BookmarkStore() { super(MyApplication.getInstance(),"bookmarks.sqlite",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT,url TEXT NOT NULL UNIQUE,title TEXT,thumbnail BLOB,favicon BLOB)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int current) { throw new IllegalStateException("Unsupported bookmark schema"); }
    public synchronized void save(Bookmark b) {
        ContentValues v=new ContentValues(); v.put("url",b.getUrl()); v.put("title",b.getTitle());
        v.put("thumbnail",b.getThumbnail()); v.put("favicon",b.getFavicon());
        if(getWritableDatabase().update("bookmarks",v,"url=?",new String[]{b.getUrl()})==0)
            getWritableDatabase().insertOrThrow("bookmarks",null,v);
    }
    public synchronized ArrayList<Bookmark> all() {
        ArrayList<Bookmark> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,url,title,thumbnail,favicon FROM bookmarks ORDER BY id",null)) {
            while(c.moveToNext()) { Bookmark b=new Bookmark(); b.setCreatedAt(c.getLong(0));
                b.setUrl(c.getString(1)); b.setTitle(c.getString(2)); b.setThumbnail(c.getBlob(3)); b.setFavicon(c.getBlob(4)); result.add(b); }
        } return result;
    }
    public synchronized void delete(Bookmark b) {
        if(!b.isPreventDelete()) getWritableDatabase().delete("bookmarks","url=?",new String[]{b.getUrl()});
    }
}
