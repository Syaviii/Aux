package com.thekirankumar.youtubeauto2.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class SearchUrls {
    private SearchUrls() { }
    public static String forQuery(String query, boolean youtube) {
        if (query == null || query.trim().isEmpty()) return null;
        String encoded;
        try { encoded=URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
        return (youtube ? "https://www.youtube.com/results?search_query=" : "https://www.google.com/search?q=") + encoded;
    }
}
