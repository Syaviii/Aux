package com.sya.tube.library;

import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One search across YouTube, Twitch and Patreon: the creator's channels on each, their stream when live, then videos,
 * clips and past broadcasts. Each site's public search is asked directly, so no sign-in is needed
 */
final class UnifiedSearch {
    private static final String UA = "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36";
    private static final String TWITCH_CLIENT = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    // The YouTube web version searches are sent as. YouTube refuses one it has retired, then the version its own page uses is read and kept
    private static volatile String youtubeVersion = "2.20251120.00.00";
    // A Patreon page with fewer posts than this is a placeholder or a stranger using the name
    static final int PATREON_MIN_POSTS = 5;
    // A Twitch channel that is only close to the search needs this many followers to count as the creator
    static final long TWITCH_MIN_LOOKALIKE_FOLLOWERS = 1000;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final long KEEP_MS = 3 * 60_000;
    private static final Map<String, Object[]> CACHE = new LinkedHashMap<String, Object[]>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) { return size() > 12; }
    };

    static final class Person { String service, name, sub, picture, href; boolean live; int score; }
    static final class Video { String service, title, meta, picture, href, badge; long durationMs; }

    static final class Results {
        final List<Video> live = new ArrayList<>(), youtube = new ArrayList<>(), clips = new ArrayList<>(), broadcasts = new ArrayList<>();
        final List<Person> people = new ArrayList<>();
        int failed;
    }

    /** The results as page markup, kept for a few minutes so going back to them is instant */
    static String html(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        synchronized (CACHE) {
            Object[] hit = CACHE.get(key);
            if (hit != null && System.currentTimeMillis() - (long) hit[0] < KEEP_MS) return (String) hit[1];
        }
        Results results = search(query.trim());
        String page = render(results);
        if (results.failed == 0) synchronized (CACHE) { CACHE.put(key, new Object[]{System.currentTimeMillis(), page}); }
        return page;
    }

    static Results search(String query) {
        Results out = new Results();
        Person[] youtubeChannel = new Person[1], twitchChannel = new Person[1], patreon = new Person[1];
        List<Video> youtube = new ArrayList<>(), clips = new ArrayList<>(), broadcasts = new ArrayList<>();
        Video[] live = new Video[1];
        String[] corrected = new String[1];
        List<Future<?>> jobs = new ArrayList<>();
        jobs.add(POOL.submit(() -> { youtube(query, youtubeChannel, youtube, corrected); return null; }));
        jobs.add(POOL.submit(() -> { twitch(query, twitchChannel, live, clips, broadcasts); return null; }));
        jobs.add(POOL.submit(() -> { patreon(query, patreon); return null; }));
        out.failed += finish(jobs);
        // A typo YouTube fixed, or the channel it found, is searched again on the sites that found nobody
        String better = corrected[0] != null ? corrected[0] : youtubeChannel[0] != null ? youtubeChannel[0].name : null;
        if (better != null && !norm(better).equals(norm(query))) {
            // Once YouTube knows who was meant, a Twitch or Patreon name that only looked like the typo is someone else
            if (twitchChannel[0] != null && twitchChannel[0].score != 0 && score(twitchChannel[0].name, better) < 0) { twitchChannel[0] = null; live[0] = null; clips.clear(); broadcasts.clear(); }
            if (patreon[0] != null && patreon[0].score != 0 && score(patreon[0].name, better) < 0) patreon[0] = null;
        }
        if (better != null && !norm(better).equals(norm(query)) && (twitchChannel[0] == null || patreon[0] == null)) {
            List<Future<?>> retry = new ArrayList<>();
            if (twitchChannel[0] == null) retry.add(POOL.submit(() -> { twitch(better, twitchChannel, live, clips, broadcasts); return null; }));
            if (patreon[0] == null) retry.add(POOL.submit(() -> { patreon(better, patreon); return null; }));
            finish(retry);
        }
        if (live[0] != null) out.live.add(live[0]);
        for (Person p : new Person[]{twitchChannel[0], youtubeChannel[0], patreon[0]}) if (p != null) out.people.add(p);
        out.youtube.addAll(youtube);
        out.clips.addAll(clips);
        out.broadcasts.addAll(broadcasts);
        return out;
    }

    private static int finish(List<Future<?>> jobs) {
        int failed = 0;
        for (Future<?> job : jobs) {
            try { job.get(12, TimeUnit.SECONDS); } catch (Exception error) { failed++; job.cancel(true); }
        }
        return failed;
    }

    // Each site's own search

    private static void youtube(String query, Person[] channel, List<Video> videos, String[] corrected) throws Exception {
        JSONObject data = youtubeSearch(query);
        List<JSONObject> channels = new ArrayList<>(), found = new ArrayList<>();
        collect(data, "channelRenderer", channels);
        collect(data, "videoRenderer", found);
        List<JSONObject> fixes = new ArrayList<>();
        // Only a spelling YouTube switched to itself, its "Did you mean" guesses also come for nonsense and find strangers
        collect(data, "showingResultsForRenderer", fixes);
        for (JSONObject fix : fixes) {
            String text = text(fix.optJSONObject("correctedQuery"));
            if (!text.isEmpty()) { corrected[0] = text; break; }
        }
        JSONObject bestChannel = null;
        int bestScore = Integer.MAX_VALUE;
        for (JSONObject c : channels) {
            // A long channel name like "Kurzgesagt – In a Nutshell" is still found by its @handle
            String title = text(c.optJSONObject("title")), handle = handle(c);
            int score = best(score(title, query), close(handle, query));
            if (corrected[0] != null) score = best(score, best(score(title, corrected[0]), close(handle, corrected[0])));
            if (score >= 0 && score < bestScore) { bestScore = score; bestChannel = c; }
        }
        if (bestChannel != null) {
            JSONObject c = bestChannel;
            String name = text(c.optJSONObject("title"));
            Person p = new Person();
            p.service = "youtube";
            p.name = name;
            p.picture = picture(c.optJSONObject("thumbnail"));
            String handle = text(c.optJSONObject("subscriberCountText"));
            String subs = text(c.optJSONObject("videoCountText"));
            // YouTube puts the @handle in one field and the subscriber count in the other
            p.sub = join(handle.startsWith("@") ? subs : handle, "YouTube");
            p.href = "https://www.youtube.com/channel/" + c.optString("channelId");
            channel[0] = p;
        }
        for (JSONObject v : found) {
            if (videos.size() >= 12) break;
            String id = v.optString("videoId");
            if (id.isEmpty()) continue;
            Video video = new Video();
            video.service = "youtube";
            video.title = text(v.optJSONObject("title"));
            String owner = text(v.optJSONObject("ownerText")), views = text(v.optJSONObject("shortViewCountText")), when = text(v.optJSONObject("publishedTimeText"));
            video.meta = join(join(owner, views), when);
            video.picture = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
            video.href = "https://www.youtube.com/watch?v=" + id;
            video.durationMs = clockMs(text(v.optJSONObject("lengthText")));
            if (video.durationMs == 0 && hasLiveBadge(v)) video.badge = "LIVE";
            videos.add(video);
        }
    }

    private static JSONObject youtubeSearch(String query) throws Exception {
        String sent = youtubeVersion;
        try {
            JSONObject data = youtubeSearch(query, sent);
            if (data.has("contents")) return data;
        } catch (Exception refused) {
            // Retried below with the version YouTube's own page uses
        }
        String current = currentYoutubeVersion();
        if (current == null || current.equals(sent)) throw new IllegalStateException("YouTube search failed");
        JSONObject data = youtubeSearch(query, current);
        youtubeVersion = current;
        return data;
    }

    private static JSONObject youtubeSearch(String query, String version) throws Exception {
        JSONObject body = new JSONObject().put("query", query).put("context", new JSONObject().put("client",
            new JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", "en").put("gl", "US")));
        return new JSONObject(post("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", body.toString(), null));
    }

    private static String currentYoutubeVersion() {
        try {
            Matcher m = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"")
                .matcher(get("https://www.youtube.com/?hl=en", DESKTOP_UA, "text/html"));
            return m.find() ? m.group(1) : null;
        } catch (Exception offline) {
            return null;
        }
    }

    private static void twitch(String query, Person[] channel, Video[] live, List<Video> clips, List<Video> broadcasts) throws Exception {
        String search = "query($q:String!){searchFor(userQuery:$q,platform:\"web\"){channels{edges{item{... on User{login displayName profileImageURL(width:150) "
            + "followers{totalCount} stream{viewersCount title game{displayName} previewImageURL(width:640,height:360)}}}}}}}";
        JSONObject data = gql(search, new JSONObject().put("q", query));
        JSONArray edges = data.optJSONObject("searchFor") == null || data.getJSONObject("searchFor").optJSONObject("channels") == null ? new JSONArray()
            : data.getJSONObject("searchFor").getJSONObject("channels").optJSONArray("edges");
        JSONObject best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; edges != null && i < edges.length(); i++) {
            JSONObject user = edges.getJSONObject(i).optJSONObject("item");
            if (user == null) continue;
            // The closest name wins, so the creator beats their re-run channel and a typo still finds them
            int score = best(score(user.optString("displayName"), query), score(user.optString("login"), query));
            // A near miss with almost no followers is someone using a lookalike name, not the creator
            JSONObject following = user.optJSONObject("followers");
            if (score > 0 && (following == null ? 0 : following.optLong("totalCount")) < TWITCH_MIN_LOOKALIKE_FOLLOWERS) continue;
            if (score >= 0 && score < bestScore) { bestScore = score; best = user; }
        }
        if (best == null) return;
        int matched = bestScore;
        String login = best.optString("login");
        Person p = new Person();
        p.service = "twitch";
        p.score = matched;
        p.name = best.optString("displayName", login);
        p.picture = best.optString("profileImageURL", null);
        p.href = "https://m.twitch.tv/" + login;
        JSONObject stream = best.optJSONObject("stream");
        p.live = stream != null;
        JSONObject followers = best.optJSONObject("followers");
        p.sub = p.live ? "Live now on Twitch" : join(followers == null ? "" : count(followers.optLong("totalCount"), "follower"), "Twitch");
        channel[0] = p;
        if (stream != null) {
            Video v = new Video();
            v.service = "twitch";
            v.title = stream.optString("title", p.name + " is live");
            JSONObject game = stream.optJSONObject("game");
            v.meta = join(join(p.name, game == null ? "" : game.optString("displayName")), count(stream.optLong("viewersCount"), "viewer"));
            v.picture = stream.optString("previewImageURL", null);
            v.href = p.href;
            v.badge = "LIVE";
            live[0] = v;
        }
        String more = "query($l:String!){user(login:$l){clips(first:8,criteria:{period:LAST_MONTH,sort:VIEWS_DESC}){edges{node{slug title thumbnailURL viewCount durationSeconds createdAt}}}"
            + " videos(first:6,type:ARCHIVE,sort:TIME){edges{node{id title previewThumbnailURL(width:640,height:360) lengthSeconds publishedAt viewCount}}}}}";
        JSONObject user = gql(more, new JSONObject().put("l", login)).optJSONObject("user");
        if (user == null) return;
        for (JSONObject node : nodes(user.optJSONObject("clips"))) {
            Video v = new Video();
            v.service = "twitch";
            v.title = node.optString("title");
            v.meta = join(join(p.name, views(node.optLong("viewCount"))), "Clip");
            v.picture = node.optString("thumbnailURL", null);
            v.href = "https://m.twitch.tv/clip/" + node.optString("slug");
            v.durationMs = node.optLong("durationSeconds") * 1000;
            clips.add(v);
        }
        for (JSONObject node : nodes(user.optJSONObject("videos"))) {
            String thumb = node.optString("previewThumbnailURL", "");
            Video v = new Video();
            v.service = "twitch";
            v.title = node.optString("title");
            v.meta = join(join(p.name, views(node.optLong("viewCount"))), "Past broadcast");
            // A broadcast still being recorded has Twitch's grey placeholder, the live card already covers it
            v.picture = thumb.contains("404_processing") ? null : thumb;
            v.href = "https://m.twitch.tv/videos/" + node.optString("id");
            v.durationMs = node.optLong("lengthSeconds") * 1000;
            broadcasts.add(v);
        }
    }

    private static void patreon(String query, Person[] creator) throws Exception {
        JSONObject data = new JSONObject(get("https://www.patreon.com/api/search?q=" + Uri.encode(query) + "&json-api-version=1.0"));
        JSONArray list = data.optJSONArray("data");
        JSONObject chosen = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject a = list.getJSONObject(i).optJSONObject("attributes");
            if (a == null || a.optBoolean("is_nsfw") || !a.optString("url").startsWith("https://www.patreon.com/")) continue;
            JSONObject posted = a.optJSONObject("post_statistics");
            long posts = posted == null ? PATREON_MIN_POSTS : posted.optLong("total");
            int score = patreonScore(a.optString("name"), a.optString("creator_name"), a.optString("url"), posts, query);
            if (score >= 0 && score < bestScore) { bestScore = score; chosen = a; }
        }
        if (chosen != null) {
            JSONObject a = chosen;
            String name = a.optString("name");
            Person p = new Person();
            p.service = "patreon";
            p.score = bestScore;
            p.name = name;
            p.picture = a.optString("avatar_photo_url", null);
            p.href = a.optString("url");
            JSONObject stats = a.optJSONObject("post_statistics");
            p.sub = join(stats == null ? "" : count(stats.optLong("total"), "post"), "Patreon");
            creator[0] = p;
        }
    }

    // What counts as the creator being searched for

    static String norm(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static boolean exact(String name, String query) {
        return !norm(query).isEmpty() && norm(name).equals(norm(query));
    }

    /** The name is the search, or close to it allowing for typos, so a channel row never shows a stranger */
    static boolean matches(String name, String query) { return score(name, query) >= 0; }

    /** How far a name is from the search, 0 is the same name, -1 is someone else */
    static int score(String name, String query) {
        String n = norm(name), q = norm(query);
        if (n.isEmpty() || q.isEmpty()) return -1;
        if (n.equals(q)) return 0;
        int whole = distance(n, q);
        if (whole <= typos(Math.min(n.length(), q.length()))) return whole * 2;
        String longer = n.length() >= q.length() ? n : q, shorter = longer == n ? q : n;
        // One holds the other, like a channel named after the show plus its network, but not a long unrelated title
        if (shorter.length() < 4 || shorter.length() * 2 < longer.length()) return -1;
        int extra = (longer.length() - shorter.length() + 2) / 3;
        if (longer.contains(shorter)) return 2 + extra;
        // A typo inside a longer name is only forgiven for long searches, short ones find strangers that way
        if (shorter.length() < 9) return -1;
        int closest = Integer.MAX_VALUE;
        for (int size = shorter.length() - 1; size <= shorter.length() + 1; size++)
            for (int at = 0; at + size <= longer.length(); at++) closest = Math.min(closest, distance(longer.substring(at, at + size), shorter));
        return closest <= Math.max(1, typos(shorter.length()) / 2) ? 3 + closest * 2 + extra : -1;
    }

    /**
     * Patreon's search returns loose relatives like fan pages, so only a page named the search or with it as its address
     * counts, never a longer name holding it, and a page with almost nothing posted is skipped
     */
    static int patreonScore(String name, String creator, String url, long posts, String query) {
        if (posts < PATREON_MIN_POSTS) return -1;
        String address = url == null ? "" : url.substring(url.lastIndexOf('/') + 1);
        return best(best(close(name, query), close(creator, query)), close(address, query));
    }

    /** The whole name is the search allowing for typos, with no extra words around it */
    static int close(String name, String query) {
        String n = norm(name), q = norm(query);
        if (n.isEmpty() || q.isEmpty()) return -1;
        int whole = distance(n, q);
        return whole <= typos(Math.min(n.length(), q.length())) ? whole * 2 : -1;
    }

    /** Typos forgiven for a name this long: none for short names, more as they grow */
    private static int typos(int length) { return length < 4 ? 0 : length < 7 ? 1 : length < 12 ? 2 : 3; }

    private static int best(int a, int b) { return a < 0 ? b : b < 0 ? a : Math.min(a, b); }

    /** Letters added, removed, changed or swapped to turn one name into the other */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        return d[a.length()][b.length()];
    }

    // Page markup

    private static String render(Results r) {
        StringBuilder out = new StringBuilder();
        if (r.live.isEmpty() && r.people.isEmpty() && r.youtube.isEmpty() && r.clips.isEmpty() && r.broadcasts.isEmpty()) {
            return r.failed > 0 ? "<div class=empty><b>Couldn't search right now</b><br>Check the phone's connection and try again</div>"
                : "<div class=empty><b>Nothing found</b><br>Try another name or spelling</div>";
        }
        if (!r.people.isEmpty()) {
            out.append("<h2>Channels</h2><div class=people>");
            for (Person p : r.people) {
                out.append("<a class=person href='").append(LibraryPages.esc(p.href)).append("'><span class='av").append(p.live ? " live" : "").append("'>");
                if (p.picture != null) out.append("<img src='").append(LibraryPages.esc(p.picture)).append("' alt='' onerror=\"this.style.display='none'\">");
                out.append("<span class=svc>").append(LibraryPages.mark(p.service)).append("</span></span><span class=pi><b>").append(LibraryPages.esc(p.name))
                    .append("</b><span").append(p.live ? " class=on" : "").append(">").append(LibraryPages.esc(p.sub)).append("</span></span></a>");
            }
            out.append("</div>");
        }
        // A creator live right now leads the videos, their channel card says so too
        List<Video> videos = new ArrayList<>(r.live);
        videos.addAll(r.youtube);
        if (!videos.isEmpty()) section(out, "Videos", videos);
        if (!r.clips.isEmpty()) section(out, "Twitch clips", r.clips);
        if (!r.broadcasts.isEmpty()) section(out, "Past broadcasts", r.broadcasts);
        return out.toString();
    }

    private static void section(StringBuilder out, String title, List<Video> videos) {
        out.append("<h2>").append(LibraryPages.esc(title)).append("</h2><div class=grid>");
        for (Video v : videos) {
            out.append("<a class=card href='").append(LibraryPages.esc(v.href)).append("'><div class=thumb>");
            if (v.picture != null) out.append("<img loading=lazy src='").append(LibraryPages.esc(v.picture)).append("' alt='' onerror=\"this.style.display='none'\">");
            else out.append("<span class=nopic>").append(LibraryPages.mark(v.service)).append("</span>");
            if (v.badge != null) out.append("<span class=livebadge>").append(v.badge).append("</span>");
            else if (v.durationMs > 0) out.append("<span class=dur>").append(clock(v.durationMs)).append("</span>");
            out.append("<span class=svcm>").append(LibraryPages.mark(v.service)).append("</span></div><div class=t>").append(LibraryPages.esc(v.title))
                .append("</div><div class=m>").append(LibraryPages.esc(v.meta)).append("</div></a>");
        }
        out.append("</div>");
    }

    // Small helpers

    private static JSONObject gql(String query, JSONObject variables) throws Exception {
        String body = new JSONObject().put("query", query).put("variables", variables).toString();
        JSONObject reply = new JSONObject(post("https://gql.twitch.tv/gql", body, TWITCH_CLIENT));
        JSONObject data = reply.optJSONObject("data");
        if (data == null) throw new IllegalStateException("Twitch search failed");
        return data;
    }

    private static List<JSONObject> nodes(JSONObject connection) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray edges = connection == null ? null : connection.optJSONArray("edges");
        for (int i = 0; edges != null && i < edges.length(); i++) {
            JSONObject node = edges.optJSONObject(i) == null ? null : edges.optJSONObject(i).optJSONObject("node");
            if (node != null) out.add(node);
        }
        return out;
    }

    private static void collect(Object node, String key, List<JSONObject> out) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = o.opt(k);
                if (k.equals(key) && v instanceof JSONObject) out.add((JSONObject) v);
                else collect(v, key, out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collect(a.opt(i), key, out);
        }
    }

    /** The channel's @handle without the @, YouTube puts it in one field and the subscriber count in the other */
    private static String handle(JSONObject channel) {
        for (String field : new String[]{"subscriberCountText", "videoCountText"}) {
            String value = text(channel.optJSONObject(field));
            if (value.startsWith("@")) return value.substring(1);
        }
        return "";
    }

    private static boolean hasLiveBadge(JSONObject video) {
        String raw = video.toString();
        return raw.contains("BADGE_STYLE_TYPE_LIVE_NOW");
    }

    private static String text(JSONObject value) {
        if (value == null) return "";
        if (value.has("simpleText")) return value.optString("simpleText");
        JSONArray runs = value.optJSONArray("runs");
        StringBuilder out = new StringBuilder();
        for (int i = 0; runs != null && i < runs.length(); i++) out.append(runs.optJSONObject(i) == null ? "" : runs.optJSONObject(i).optString("text"));
        return out.toString();
    }

    private static String picture(JSONObject thumbnail) {
        JSONArray list = thumbnail == null ? null : thumbnail.optJSONArray("thumbnails");
        if (list == null || list.length() == 0) return null;
        String url = list.optJSONObject(list.length() - 1).optString("url");
        return url.startsWith("//") ? "https:" + url : url;
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty()) return a;
        return a + " · " + b;
    }

    static long clockMs(String clock) {
        if (clock == null || !clock.matches("\\d+(:\\d{1,2}){1,2}")) return 0;
        long total = 0;
        for (String part : clock.split(":")) total = total * 60 + Long.parseLong(part);
        return total * 1000;
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static String count(long n, String word) {
        String number = n >= 1_000_000 ? String.format(Locale.US, n >= 10_000_000 ? "%.0fM" : "%.1fM", n / 1e6)
            : n >= 1000 ? String.format(Locale.US, n >= 10_000 ? "%.0fK" : "%.1fK", n / 1e3) : String.valueOf(n);
        return number + " " + word + (n == 1 ? "" : "s");
    }

    private static String views(long n) { return count(n, "view"); }

    private static String get(String url) throws Exception { return get(url, UA, "application/json"); }

    private static String get(String url, String agent, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", agent);
        c.setRequestProperty("Accept", accept);
        return read(c);
    }

    private static String post(String url, String body, String twitchClient) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Content-Type", "application/json");
        if (twitchClient != null) c.setRequestProperty("Client-ID", twitchClient);
        try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        return read(c);
    }

    private static String read(HttpURLConnection c) throws Exception {
        try {
            if (c.getResponseCode() / 100 != 2) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
                return bytes.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    private UnifiedSearch() {}
}
