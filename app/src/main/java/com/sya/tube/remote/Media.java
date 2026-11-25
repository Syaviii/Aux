package com.sya.tube.remote;

import android.net.Uri;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web videos Aux plays and remote controls, each named by one key: a bare YouTube id, "patreon:" and a post's
 * number, or a Twitch "twitch:" and a channel name (its live stream), "twitch:video:" and a past broadcast's number, or
 * "twitch:clip:" and a clip's name
 */
public final class Media {
    public static final String PATREON = "patreon:", TWITCH = "twitch:", TWITCH_VIDEO = "twitch:video:", TWITCH_CLIP = "twitch:clip:";
    // Twitch pages under a first path part that is not a channel
    private static final java.util.Set<String> TWITCH_PAGES = new java.util.HashSet<>(java.util.Arrays.asList(
        "directory", "search", "settings", "videos", "downloads", "jobs", "p", "store", "turbo", "login", "signup", "subscriptions",
        "inventory", "wallet", "drops", "friends", "messages", "following", "prime", "bits", "broadcast", "u", "popout", "embed",
        "moderator", "team", "collections", "events", "legal", "privacy", "logout", "passport-callback", "clip", "clips", "activate",
        "user", "creatorcamp", "partner", "safety", "products", "redeem", "giftcard", "account", "notifications", "dashboard"));
    private static final Pattern TWITCH_LOGIN = Pattern.compile("^[A-Za-z0-9_]{2,25}$");
    // patreon.com/posts/123, /posts/title-123 and /creator/posts/title-123 all name post 123
    private static final Pattern PATREON_POST = Pattern.compile("^(?:/[^/]+)?/posts/(?:[^/]*-)?(\\d+)/?$");

    /** The key of the video an address shows, or null when it is not a video Aux knows */
    public static String key(String url) {
        String post = patreonPost(url);
        if (post != null) return PATREON + post;
        String twitch = twitchKey(url);
        if (twitch != null) return twitch;
        String playable = youtubeUrl(url);
        return playable == null ? null : Uri.parse(playable).getQueryParameter("v");
    }

    /** A clean address for the video an address shows, the car loads this one, null for anything else */
    public static String playableUrl(String url) {
        String post = patreonPost(url);
        if (post != null) return patreonUrl(post);
        String twitch = twitchKey(url);
        if (twitch != null) return url(twitch, 0);
        return youtubeUrl(url);
    }

    /** The address that opens a key, a YouTube address also starts at the given time */
    public static String url(String key, long startMs) {
        if (key == null) return null;
        if (isPatreon(key)) return patreonUrl(key.substring(PATREON.length()));
        if (key.startsWith(TWITCH_VIDEO)) return "https://m.twitch.tv/videos/" + key.substring(TWITCH_VIDEO.length()) + (startMs > 5000 ? "?t=" + twitchTime(startMs / 1000) : "");
        if (key.startsWith(TWITCH_CLIP)) return "https://m.twitch.tv/clip/" + key.substring(TWITCH_CLIP.length());
        if (isTwitch(key)) return "https://m.twitch.tv/" + key.substring(TWITCH.length());
        return "https://m.youtube.com/watch?v=" + key + (startMs > 5000 ? "&t=" + startMs / 1000 + "s" : "");
    }

    public static boolean isPatreon(String key) { return key != null && key.startsWith(PATREON); }

    public static boolean isTwitch(String key) { return key != null && key.startsWith(TWITCH); }

    /** A Twitch channel's live stream, it has no length and no place to come back to */
    public static boolean isLive(String key) { return isTwitch(key) && !key.startsWith(TWITCH_VIDEO) && !key.startsWith(TWITCH_CLIP); }

    /** The name of the service a key plays on, for labels */
    public static String serviceName(String key) { return isPatreon(key) ? "Patreon" : isTwitch(key) ? "Twitch" : "YouTube"; }

    /** YouTube's own features (its native player, chapters, SponsorBlock, ads, quality) apply only to these */
    public static boolean isYoutube(String key) { return key != null && !key.contains(":"); }

    /** A picture for the key, the same card picture the post's own page shares */
    public static String thumbnail(String key) {
        if (key == null) return null;
        if (isPatreon(key)) return "https://www.patreon.com/ig/card-teaser-image/post/" + key.substring(PATREON.length()) + "/landscape.png";
        // A channel's preview is its live picture, past broadcasts and clips have no picture without Twitch's own API
        if (isLive(key)) return "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + key.substring(TWITCH.length()).toLowerCase(java.util.Locale.ROOT) + "-320x180.jpg";
        if (isTwitch(key)) return null;
        return "https://i.ytimg.com/vi/" + key + "/mqdefault.jpg";
    }

    /** A title read from a page, without the site name the page adds after it */
    public static String cleanTitle(String key, String title) {
        if (title == null) return "";
        if (isTwitch(key)) return title.replaceFirst("\\s*-\\s*Twitch\\s*$", "").replaceFirst("^Twitch$", "").trim();
        if (isPatreon(key)) return title.replaceFirst("\\s*\\|[^|]*on Patreon\\s*$", "").replaceFirst("\\s*\\|\\s*Patreon\\s*$", "").trim();
        return title.replaceFirst(" - YouTube$", "").trim();
    }

    public static final String YOUTUBE_SERVICE = "youtube", PATREON_SERVICE = "patreon", TWITCH_SERVICE = "twitch", LIBRARY_SERVICE = "library";

    /** Which service a page belongs to, so Home and Search act on it, null for the start page and other sites */
    public static String service(String url) {
        if (url == null) return null;
        java.net.URI uri;
        try { uri = new java.net.URI(url); } catch (java.net.URISyntaxException bad) { return null; }
        String host = uri.getHost(), path = uri.getPath() == null ? "" : uri.getPath();
        if (host == null) return null;
        if (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be")) return YOUTUBE_SERVICE;
        if (host.equals("patreon.com") || host.endsWith(".patreon.com")) return PATREON_SERVICE;
        if (isTwitchHost(host)) return TWITCH_SERVICE;
        // Aux's own pages: the library is a service, the start page and its links are not
        if (host.equals("appassets.androidplatform.net") && path.startsWith("/tube/")
            && !path.equals("/tube/start") && !path.equals("/tube/search") && !path.equals("/tube/open") && !path.equals("/tube/continue")) return LIBRARY_SERVICE;
        return null;
    }

    private static String patreonUrl(String post) { return "https://www.patreon.com/posts/" + post; }

    private static boolean isTwitchHost(String host) { return host.equals("twitch.tv") || host.endsWith(".twitch.tv"); }

    /** 1h2m3s, the start time a past broadcast's address takes */
    static String twitchTime(long seconds) {
        long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return (h > 0 ? h + "h" : "") + (h > 0 || m > 0 ? m + "m" : "") + s + "s";
    }

    /** The key of a Twitch channel, past broadcast or clip address, or null */
    static String twitchKey(String url) {
        if (url == null) return null;
        java.net.URI uri;
        try { uri = new java.net.URI(url); } catch (java.net.URISyntaxException bad) { return null; }
        String host = uri.getHost();
        if (host == null || !isTwitchHost(host) || uri.getPath() == null) return null;
        String[] parts = uri.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        if (host.equals("clips.twitch.tv")) return parts.length == 1 && !parts[0].equals("embed") ? TWITCH_CLIP + parts[0] : null;
        if (parts.length == 2 && parts[0].equals("videos") && parts[1].matches("\\d+")) return TWITCH_VIDEO + parts[1];
        if (parts.length == 2 && parts[0].equals("clip")) return TWITCH_CLIP + parts[1];
        if (parts.length == 3 && parts[1].equals("clip") && TWITCH_LOGIN.matcher(parts[0]).matches()) return TWITCH_CLIP + parts[2];
        if (parts.length == 1 && TWITCH_LOGIN.matcher(parts[0]).matches() && !TWITCH_PAGES.contains(parts[0].toLowerCase(java.util.Locale.ROOT)))
            return TWITCH + parts[0].toLowerCase(java.util.Locale.ROOT);
        return null;
    }

    /** The post number in a Patreon post address, or null */
    static String patreonPost(String url) {
        if (url == null) return null;
        java.net.URI uri;
        try { uri = new java.net.URI(url); } catch (java.net.URISyntaxException bad) { return null; }
        String host = uri.getHost();
        if (host == null || !(host.equals("patreon.com") || host.endsWith(".patreon.com")) || uri.getPath() == null) return null;
        Matcher post = PATREON_POST.matcher(uri.getPath());
        return post.matches() ? post.group(1) : null;
    }

    /** A watch or shorts address the car can play, or null for anything else */
    private static String youtubeUrl(String url) {
        if (url == null) return null;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be"))) return null;
        String path = uri.getPath() == null ? "" : uri.getPath();
        String id = "/watch".equals(path) ? uri.getQueryParameter("v") : host.equals("youtu.be") && path.length() > 1 ? path.substring(1) : null;
        if (id == null || id.isEmpty()) return null;
        Uri.Builder out = new Uri.Builder().scheme("https").authority("m.youtube.com").path("/watch").appendQueryParameter("v", id);
        String list = uri.getQueryParameter("list");
        if (list != null) out.appendQueryParameter("list", list);
        String start = uri.getQueryParameter("t");
        if (start != null) out.appendQueryParameter("t", start);
        return out.build().toString();
    }

    private Media() {}
}
