package com.thekirankumar.youtubeauto2.exoplayer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;
public class PlayerQueueTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void missingFileNeverPlaysItsNeighbors() throws Exception {
        folder.newFile("another.mp4");
        PlayerQueue queue=new PlayerQueue(new File(folder.getRoot(),"missing.mp4").getPath());
        assertNull(queue.current()); assertFalse(queue.hasNext()); assertFalse(queue.hasPrevious());
    }
    @Test public void encodedUriSelectsCorrectFileAndBoundsNavigation() throws Exception {
        File first=folder.newFile("A video.mp4"), second=folder.newFile("b.MKV");
        folder.newFile("notes.txt");
        PlayerQueue queue=new PlayerQueue(second.toURI().toString().replace("file:/","file:///"));
        assertEquals(second.getPath(),queue.current()); assertEquals(2,queue.getCurrentQueue().length);
        assertFalse(queue.hasNext()); queue.next(); assertEquals(second.getPath(),queue.current());
        queue.previous(); assertEquals(first.getPath(),queue.current());
        assertFalse(queue.hasPrevious()); queue.previous(); assertEquals(first.getPath(),queue.current());
    }
    @Test public void unavailablePathsRemainEmpty() throws Exception {
        assertNull(new PlayerQueue(null).current());
        assertNull(new PlayerQueue("").current());
        assertNull(new PlayerQueue(folder.getRoot().getPath()).current());
        assertFalse(PlayerQueue.isMedia("video.mp4.exe"));
    }
}
