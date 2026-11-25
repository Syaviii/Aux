package com.thekirankumar.youtubeauto2.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class SearchUrls {
    private SearchUrls() { }
    public static String forQuery(String query, boolean youtube) {
        return forQuery(query, youtube ? SearchMode.YOUTUBE : SearchMode.GOOGLE);
    }

    /** The results page for a query on the service being browsed */
    public static String forQuery(String query, SearchMode mode) {
        if (query == null || query.trim().isEmpty()) return null;
        String encoded;
        try { encoded=URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
        if (mode == SearchMode.PATREON) return "https://www.patreon.com/explore/search?query=" + encoded;
        if (mode == SearchMode.ALL) return com.sya.tube.library.LibraryPages.SEARCH + "?q=" + encoded.replace("+", "%20");
        if (mode == SearchMode.TWITCH) return "https://m.twitch.tv/search?term=" + encoded;
        return (mode == SearchMode.YOUTUBE ? "https://www.youtube.com/results?search_query=" : "https://www.google.com/search?q=") + encoded;
    }
}
