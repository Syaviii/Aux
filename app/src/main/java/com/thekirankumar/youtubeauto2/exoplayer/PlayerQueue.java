// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.exoplayer;
import java.io.File;
import java.net.URI;
import java.util.*;
public final class PlayerQueue implements MediaQueue {
    public static final String FILE_PREFIX="file://";
    private File[] queue;
    private int index;
    public PlayerQueue(String path) {
        if(path==null || path.trim().isEmpty()) { queue=new File[0]; return; }
        File requested;
        try { requested=path.startsWith(FILE_PREFIX)?new File(URI.create(path)):new File(path); }
        catch(IllegalArgumentException e) { requested=new File(path.startsWith(FILE_PREFIX)?path.substring(7):path); }
        File parent=requested.getAbsoluteFile().getParentFile();
        File[] files=parent==null?null:parent.listFiles(f->f.isFile()&&isMedia(f.getName()));
        queue=files==null?new File[0]:files;
        Arrays.sort(queue,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        for(int i=0;i<queue.length;i++) if(queue[i].getAbsoluteFile().equals(requested.getAbsoluteFile())) { index=i; return; }
        // Never play a neighboring file when the requested one disappeared.
        queue=requested.isFile()&&isMedia(requested.getName())?new File[]{requested}:new File[0];
    }
    public static boolean isMedia(String name) { return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|avi|mov|m4v|mp3|m4a|aac|flac|ogg|wav|m3u8?)$"); }
    public int currentIndex() { return index; }
    public File[] getCurrentQueue() { return queue.clone(); }
    public String current() { return queue.length==0?null:queue[index].getPath(); }
    @Override public boolean hasNext() { return index+1<queue.length; }
    @Override public boolean hasPrevious() { return index>0; }
    @Override public void next() { if(hasNext()) index++; }
    @Override public void previous() { if(hasPrevious()) index--; }
    @Override public void resetPosition(int value) { if(value>=0&&value<queue.length) index=value; }
}
