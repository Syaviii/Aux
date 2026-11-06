package com.sya.tube.library;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Videos stored on the phone, from Android's media index, grouped by the folder each one is in */
public final class PhoneVideos {
    public static final class Video {
        public final long id, durationMs, addedAt;
        public final String title, folder;
        Video(long id, String title, String folder, long durationMs, long addedAt) { this.id = id; this.title = title; this.folder = folder; this.durationMs = durationMs; this.addedAt = addedAt; }
        public Uri uri() { return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id); }
        public String key() { return "phone:" + id; }
    }

    /** Android 13 split storage access by kind, videos are asked for on their own */
    public static final String PERMISSION = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;

    public static boolean allowed(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Newest first, only one folder when a folder is given */
    public static List<Video> list(Context context, String folder) {
        List<Video> out = new ArrayList<>();
        if (!allowed(context)) return out;
        String[] columns = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED};
        String where = folder == null ? null : MediaStore.Video.Media.BUCKET_DISPLAY_NAME + "=?";
        String[] args = folder == null ? null : new String[]{folder};
        try (Cursor c = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns, where, args, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(1) == null ? "Video" : c.getString(1);
                int dot = name.lastIndexOf('.');
                out.add(new Video(c.getLong(0), dot > 0 ? name.substring(0, dot) : name, c.getString(2) == null ? "Videos" : c.getString(2), c.getLong(3), c.getLong(4) * 1000));
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    /** Folder names with how many videos each has, the folder with the newest video first */
    public static Map<String, Integer> folders(List<Video> videos) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Video v : videos) out.merge(v.folder, 1, Integer::sum);
        return out;
    }

    public static byte[] thumbnail(Context context, long id) {
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
            Bitmap bitmap = Build.VERSION.SDK_INT >= 29
                ? context.getContentResolver().loadThumbnail(uri, new android.util.Size(640, 360), null)
                : MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
            if (bitmap == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            return out.toByteArray();
        } catch (Exception ignored) { return null; }
    }

    /** The picked video with the rest of its folder after it, oldest to newest like an episode list */
    public static MediaServers.Queue queue(Context context, long id) {
        MediaServers.Queue queue = new MediaServers.Queue();
        String folder = null;
        for (Video v : list(context, null)) if (v.id == id) { folder = v.folder; break; }
        List<Video> videos = list(context, folder);
        java.util.Collections.reverse(videos);
        for (Video v : videos) {
            if (v.id == id) queue.index = queue.items.size();
            Bundle b = new Bundle();
            b.putString("uri", v.uri().toString());
            b.putString("title", v.title);
            b.putString("subtitle", v.folder);
            b.putString("key", v.key());
            b.putLong("start", MediaPositions.position(context, v.key()));
            queue.items.add(b);
        }
        return queue;
    }

    private PhoneVideos() {}
}
