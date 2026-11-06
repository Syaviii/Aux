package com.sya.tube.library;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads followed channels out of a Google Takeout export (the zip or its subscriptions.csv), a NewPipe export or an
 * OPML file, and resolves a channel or video address to its channel for following from the phone
 */
public final class SubscriptionImport {
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[\\w-]{22}");

    /** Every channel found in the file, in the file's order */
    public static List<Subscriptions.Channel> read(Context context, Uri file) throws Exception {
        Map<String, Subscriptions.Channel> found = new LinkedHashMap<>();
        try (InputStream raw = context.getContentResolver().openInputStream(file); java.io.BufferedInputStream in = new java.io.BufferedInputStream(raw)) {
            in.mark(4);
            boolean zip = in.read() == 'P' && in.read() == 'K';
            in.reset();
            if (zip) {
                // A Takeout archive can be gigabytes, it is read entry by entry and only subscription files are opened
                ZipInputStream entries = new ZipInputStream(in);
                for (ZipEntry entry; (entry = entries.getNextEntry()) != null; ) {
                    String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
                    if (entry.isDirectory() || !(name.contains("subscri") || name.endsWith(".opml"))) continue;
                    parse(new String(readAll(entries), StandardCharsets.UTF_8), found);
                }
            } else parse(new String(readAll(in), StandardCharsets.UTF_8), found);
        }
        return new ArrayList<>(found.values());
    }

    private static void parse(String text, Map<String, Subscriptions.Channel> found) {
        String trimmed = text.trim();
        if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1);
        try {
            if (trimmed.startsWith("{")) {
                JSONArray subs = new JSONObject(trimmed).optJSONArray("subscriptions");
                if (subs != null) {
                    for (int i = 0; i < subs.length(); i++) {
                        JSONObject o = subs.getJSONObject(i);
                        Matcher id = CHANNEL_ID.matcher(o.optString("url"));
                        if (id.find()) found.putIfAbsent(id.group(), new Subscriptions.Channel(id.group(), o.optString("name"), ""));
                    }
                    return;
                }
            }
        } catch (Exception ignored) { }
        if (trimmed.startsWith("<")) {
            // OPML, one outline per channel with its feed address
            Matcher outline = Pattern.compile("<outline[^>]*>", Pattern.CASE_INSENSITIVE).matcher(trimmed);
            while (outline.find()) {
                String tag = outline.group();
                Matcher id = CHANNEL_ID.matcher(tag);
                if (!id.find()) continue;
                Matcher title = Pattern.compile("(?:title|text)=\"([^\"]*)\"").matcher(tag);
                found.putIfAbsent(id.group(), new Subscriptions.Channel(id.group(), title.find() ? unescape(title.group(1)) : "", ""));
            }
            return;
        }
        // Takeout's CSV: channel id, channel address, channel title, the title can itself contain commas
        for (String line : trimmed.split("\\r?\\n")) {
            Matcher id = CHANNEL_ID.matcher(line);
            if (!id.find() || id.start() > 2) continue;
            String[] parts = line.split(",", 3);
            String title = parts.length == 3 ? parts[2].trim() : "";
            if (title.length() > 1 && title.startsWith("\"") && title.endsWith("\"")) title = title.substring(1, title.length() - 1).replace("\"\"", "\"");
            found.putIfAbsent(id.group(), new Subscriptions.Channel(id.group(), title, ""));
        }
    }

    /** The channel behind a channel page, a handle or a video, looked up with the same extractor the player uses, call off the main thread */
    public static Subscriptions.Channel resolve(String url) throws Exception {
        com.sya.tube.player.YoutubeStreams.ready();
        Uri uri = Uri.parse(url);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String video = "/watch".equals(path) ? uri.getQueryParameter("v") : path.startsWith("/shorts/") ? path.substring(8) : null;
        if (video != null) {
            var stream = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=" + video);
            stream.fetchPage();
            Matcher id = CHANNEL_ID.matcher(stream.getUploaderUrl());
            if (!id.find()) throw new IllegalStateException("No channel for this video");
            String avatar = stream.getUploaderAvatars().isEmpty() ? "" : stream.getUploaderAvatars().get(0).getUrl();
            return new Subscriptions.Channel(id.group(), stream.getUploaderName(), avatar);
        }
        String page = "https://www.youtube.com" + path.replaceAll("/(videos|shorts|streams|playlists|community|featured|about)/?$", "");
        var channel = org.schabi.newpipe.extractor.ServiceList.YouTube.getChannelExtractor(page);
        channel.fetchPage();
        String avatar = channel.getAvatars().isEmpty() ? "" : channel.getAvatars().get(0).getUrl();
        return new Subscriptions.Channel(channel.getId(), channel.getName(), avatar);
    }

    /** True for a page that belongs to a channel or a video, the pages Follow can work from */
    public static boolean canFollowFrom(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost(), path = uri.getPath() == null ? "" : uri.getPath();
        if (host == null || !host.endsWith("youtube.com")) return false;
        return "/watch".equals(path) || path.startsWith("/shorts/") || path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/");
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
            if (out.size() > 20 << 20) throw new java.io.IOException("File too large");
        }
        return out.toByteArray();
    }

    private SubscriptionImport() {}
}
