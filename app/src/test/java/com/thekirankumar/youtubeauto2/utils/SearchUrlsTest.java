package com.thekirankumar.youtubeauto2.utils;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchUrlsTest {
    @Test public void googleQueryUsesRequestPathInsteadOfObsoleteHash() {
        assertEquals("https://www.google.com/search?q=Kia+K5", SearchUrls.forQuery(" Kia K5 ",false));
    }
    @Test public void specialCharactersCannotBecomeExtraQueryParameters() {
        assertEquals("https://www.youtube.com/results?search_query=rock+%26+roll+%23live", SearchUrls.forQuery("rock & roll #live",true));
    }
    @Test public void emptyQueriesDoNotNavigate() {
        assertNull(SearchUrls.forQuery(null,true));
        assertNull(SearchUrls.forQuery("  ",false));
    }
}
