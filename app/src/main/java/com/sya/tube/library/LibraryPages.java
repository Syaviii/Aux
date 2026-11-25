package com.sya.tube.library;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import com.sya.tube.remote.RecentVideos;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tube's own pages for followed channels, phone videos and media servers, served to the WebView from inside the app on
 * the address Android reserves for that, so the car and the phone browse them like any other page
 */
public final class LibraryPages {
    public static final String HOST = "appassets.androidplatform.net";
    public static final String BASE = "https://" + HOST + "/tube/";
    public static final String HOME = BASE + "library";
    public static final String CHANNELS = BASE + "channels";
    public static final String PHONE = BASE + "phone";
    public static final String VIDEO_SETTINGS = BASE + "video-settings";
    /** The page Aux opens on, with the places to watch from and what to continue */
    public static final String START = BASE + "start";
    public static final String PATREON_HOME = "https://www.patreon.com/home";
    public static final String TWITCH_HOME = "https://m.twitch.tv/";
    /** One search across YouTube, Twitch and Patreon */
    public static final String SEARCH = BASE + "search";

    public static String searchUrl(String query) { return SEARCH + "?q=" + Uri.encode(query.trim()); }

    public static boolean isSearchPage(String url) {
        return isLibrary(url) && "/tube/search".equals(Uri.parse(url).getPath());
    }

    /** The query of a search results page, null on any other page */
    public static String searchQuery(String url) {
        return isSearchPage(url) ? Uri.parse(url).getQueryParameter("q") : null;
    }

    /** The start page's search bar, each screen opens its own search box */
    public static boolean isSearchLink(String url) {
        return isOpenLink(url) && "search".equals(Uri.parse(url).getQueryParameter("to"));
    }

    /** A start page tile, each screen turns it into its own address with destination */
    public static boolean isOpenLink(String url) {
        return isLibrary(url) && "/tube/open".equals(Uri.parse(url).getPath());
    }

    /** The start page's continue cards, the car opens them at their saved place */
    public static boolean isContinueLink(String url) {
        return isLibrary(url) && "/tube/continue".equals(Uri.parse(url).getPath());
    }

    /** Where a start page tile goes, the car and the phone each use their own YouTube address */
    public static String destination(String link, boolean onPhone, String youtubeHome) {
        String to = Uri.parse(link).getQueryParameter("to");
        if ("patreon".equals(to)) return PATREON_HOME;
        if ("twitch".equals(to)) return TWITCH_HOME;
        if ("library".equals(to)) return HOME;
        return onPhone ? "https://m.youtube.com/" : youtubeHome;
    }

    public static boolean isLibrary(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return HOST.equals(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/tube/");
    }

    public static boolean isPlayLink(String url) {
        return isLibrary(url) && "/tube/play".equals(Uri.parse(url).getPath());
    }

    /** The videos a play link starts, looked up off the main thread */
    public static MediaServers.Queue resolve(Context context, String link) throws Exception {
        Uri uri = Uri.parse(link);
        if ("phone".equals(uri.getQueryParameter("src"))) return PhoneVideos.queue(context, Long.parseLong(uri.getQueryParameter("id")));
        MediaServers.Server server = MediaServers.get(context, uri.getQueryParameter("s"));
        if (server == null) throw new IllegalStateException("This server was removed");
        return MediaServers.queue(context, server, uri.getQueryParameter("i"));
    }

    /** Answers a request for one of Tube's pages or its pictures, null for every other address */
    public static WebResourceResponse intercept(Context context, String url) {
        return intercept(context, url, false);
    }

    public static WebResourceResponse intercept(Context context, String url, boolean onPhone) {
        if (!isLibrary(url)) return null;
        Uri uri = Uri.parse(url);
        String path = uri.getPath().substring(6);
        try {
            switch (path) {
                case "thumb": return image(PhoneVideos.thumbnail(context, Long.parseLong(uri.getQueryParameter("id"))));
                case "img": {
                    MediaServers.Server server = MediaServers.get(context, uri.getQueryParameter("s"));
                    return image(server == null ? null : MediaServers.image(context, server, uri.getQueryParameter("p")));
                }
                case "version": return text("application/json", "{\"v\":" + Subscriptions.version + ",\"busy\":" + Subscriptions.isRefreshing() + "}");
                case "channels": return html(channels(context, uri.getQueryParameter("c")));
                case "phone": return html(phone(context, uri.getQueryParameter("f"), onPhone));
                case "server": return html(server(context, uri.getQueryParameter("s"), uri.getQueryParameter("p")));
                case "play": return html(page(context, "Aux", "", "<div class=empty>Opening the video</div>"));
                case "start": return html(start(context, onPhone));
                case "search": return html(searchPage(uri.getQueryParameter("q"), onPhone));
                case "search-results": {
                    String q = uri.getQueryParameter("q");
                    return text("text/html", q == null || q.trim().isEmpty() ? "" : UnifiedSearch.html(q));
                }
                case "open": case "continue": return html(page(context, "Aux", "", "<div class=empty>Opening</div>"));
                default: return html(home(context));
            }
        } catch (Exception error) {
            return html(page(context, "Aux", "", "<div class=empty>Couldn't open the library</div>"));
        }
    }

    private static String home(Context context) {
        if (!Subscriptions.list(context).isEmpty()) return channels(context, null);
        if (PhoneVideos.allowed(context) && !PhoneVideos.list(context, null).isEmpty()) return phone(context, null);
        List<MediaServers.Server> servers = MediaServers.list(context);
        if (!servers.isEmpty()) return server(context, servers.get(0).id, null);
        return page(context, "Library", "channels", "<div class=empty><b>Your library is empty</b><br>Add channels, phone videos or media servers in Settings</div>");
    }

    // The start page

    private static final String[][] PLACES = {
        {"youtube", "YouTube",
            "<svg viewBox='0 0 48 48'><rect x='4' y='10' width='40' height='28' rx='9' fill='#f03'/><path d='M20 17v14l12-7z' fill='#fff'/></svg>"},
        {"patreon", "Patreon",
            "<svg viewBox='-40 -20 516 516'><path fill='#fff' d='M436 143c-.084-60.778-47.57-110.591-103.285-128.565C263.528-7.884 172.279-4.649 106.214 26.424 26.142 64.089.988 146.596.051 228.883c-.77 67.653 6.004 245.841 106.83 247.11 74.917.948 86.072-95.279 120.737-141.623 24.662-32.972 56.417-42.285 95.507-51.929C390.309 265.865 436.097 213.011 436 143Z'/></svg>"},
        {"twitch", "Twitch",
            "<svg viewBox='0 0 48 48'><path d='M9 5 6 13v27h9v5h5l5-5h7l10-10V5z' fill='#9146ff'/><path d='M12 9h26v19l-6 6h-8l-5 5v-5h-7z' fill='#fff'/><path d='M22 15h3v9h-3zM30 15h3v9h-3z' fill='#9146ff'/></svg>"},
        {"library", "Library",
            "<svg viewBox='0 0 48 48'><path d='M6 12a4 4 0 0 1 4-4h9l4 5h15a4 4 0 0 1 4 4v19a4 4 0 0 1-4 4H10a4 4 0 0 1-4-4z' fill='#8ab4f8'/><path d='M21 21v11l9-5.5z' fill='#0f0f0f'/></svg>"},
    };

    private static final String TWITCH_MARK = "<svg viewBox='0 0 48 48'><path d='M12 9h26v19l-6 6h-8l-5 5v-5h-7z' fill='#fff'/><path d='M22 15h3v9h-3zM30 15h3v9h-3z' fill='#9146ff'/></svg>";

    private static final String START_CSS = ".ph{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:#9146ff}.ph svg{width:30%;height:auto}"
        + ".start{max-width:1100px;margin:0 auto;padding:18px 16px 32px}"
        + ".places{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:26px}"
        + ".place{display:flex;align-items:center;justify-content:center;height:104px;border-radius:20px;background:#1f1f1f}.place:focus,.place:active{background:#2c2c2c;box-shadow:0 0 0 3px #fff}"
        + ".place svg{flex:none;width:58px;height:58px}"
        + ".start h2{margin:4px 4px 12px}.start .grid{padding:0}";

    private static String start(Context context, boolean onPhone) {
        StringBuilder body = new StringBuilder("<div class=start>").append(onPhone ? "" : searchBar(null)).append("<div class=places>");
        for (String[] place : PLACES)
            body.append("<a class=place aria-label='").append(place[1]).append("' href='").append(BASE).append("open?to=").append(place[0]).append("'>").append(place[2]).append("</a>");
        body.append("</div>");
        List<RecentVideos.Item> recent = RecentVideos.list(context);
        if (!recent.isEmpty()) {
            body.append("<h2>Continue watching</h2><div class=grid>");
            int shown = 0;
            for (RecentVideos.Item item : recent) {
                if (++shown > 8) break;
                String source = com.sya.tube.remote.Media.serviceName(item.id);
                card(body, BASE + "continue?id=" + Uri.encode(item.id), item.picture(),
                    item.title.isEmpty() ? source + " video" : item.title, source + " · " + clock(item.positionMs) + " of " + clock(item.durationMs), 0, item.progress());
            }
            body.append("</div>");
        }
        body.append("</div>");
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Aux</title><style>" + CSS + START_CSS + SEARCH_CSS + "</style>"
            + "</head><body data-tube-library=true>" + body + "</body></html>";
    }

    // Search across every service

    private static final String GLASS = "<svg viewBox='0 0 24 24' width='26' height='26'><path fill='#aaa' d='M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14'/></svg>";

    private static String searchBar(String query) {
        return "<a class=search href='" + BASE + "open?to=search'>" + GLASS + "<span" + (query == null ? ">Search Aux" : " class=q>" + esc(query)) + "</span></a>";
    }

    /** A service's small logo, by the name the start page uses for it */
    static String mark(String service) {
        for (String[] place : PLACES) if (place[0].equals(service)) return place[2];
        return "";
    }

    private static final String SEARCH_CSS = ".search{display:flex;align-items:center;gap:14px;height:60px;padding:0 20px;margin-bottom:14px;border-radius:30px;background:#1f1f1f;color:#aaa;font-size:18px}"
        + ".search:focus,.search:active{box-shadow:0 0 0 3px #fff}.search .q{color:#f1f1f1}.search svg{flex:none}"
        + ".results{max-width:1400px;margin:0 auto;padding:14px 0 28px}.results>.search{margin:0 12px 6px}"
        + ".people{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:12px;padding:0 12px 18px}"
        + ".person{display:flex;align-items:center;gap:14px;padding:12px 14px;border-radius:16px;background:#1f1f1f;min-width:0}.person:focus,.person:active{box-shadow:0 0 0 3px #fff}"
        + ".av{position:relative;flex:none;width:64px;height:64px;margin:0 8px 8px 0;border-radius:50%;background:#333}.av img{width:64px;height:64px;border-radius:50%;object-fit:cover}.av.live{box-shadow:0 0 0 3px #e91916}"
        + ".svc{position:absolute;right:-10px;bottom:-10px;width:38px;height:38px;border-radius:50%;background:#0f0f0f;display:flex;align-items:center;justify-content:center}.svc svg{width:28px;height:28px}"
        + ".pi{min-width:0}.pi b{display:block;font-size:18px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pi span{display:block;color:#aaa;font-size:14px;margin-top:2px}.pi .on{color:#ff4e45;font-weight:600}"
        + ".livebadge{position:absolute;left:8px;top:8px;background:#e91916;color:#fff;font-size:12px;font-weight:700;padding:2px 6px;border-radius:4px}"
        + ".svcm{position:absolute;left:8px;bottom:8px;width:46px;height:46px;border-radius:12px;background:rgba(0,0,0,.82);display:flex;align-items:center;justify-content:center}.svcm svg{width:34px;height:34px}"
        + ".nopic svg{width:56px;height:56px}"
        + ".wait{padding:48px 28px;text-align:center;color:#aaa;font-size:16px}";

    /** The phone has its own search field up top, the car's page carries the bar */
    private static String searchPage(String query, boolean onPhone) {
        String q = query == null ? "" : query.trim();
        String js = "<script>(function(){var box=document.getElementById('found');"
            + "fetch('" + BASE + "search-results?q='+encodeURIComponent(" + jsString(q) + ")).then(function(r){return r.text();})"
            + ".then(function(h){box.innerHTML=h;}).catch(function(){box.innerHTML=\"<div class=empty><b>Couldn't search right now</b><br>Check the phone's connection and try again</div>\";});})();</script>";
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(q.isEmpty() ? "Search" : q) + "</title><style>" + CSS + SEARCH_CSS + "</style>"
            + "</head><body data-tube-library=true><div class=results>" + (onPhone ? "" : searchBar(q.isEmpty() ? null : q))
            + "<div id=found>" + (q.isEmpty() ? "" : "<div class=wait>Searching</div>") + "</div></div>" + (q.isEmpty() ? "" : js) + "</body></html>";
    }

    private static String jsString(String value) {
        StringBuilder out = new StringBuilder("'");
        for (char c : value.toCharArray()) {
            if (c < 0x20 || c == '\\' || c == '\'' || c == '<' || c == '>' || c == '&' || c > 0x7e) out.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append("'").toString();
    }

    // Followed channels

    private static String channels(Context context, String channelId) {
        List<Subscriptions.Channel> followed = Subscriptions.list(context);
        if (followed.isEmpty()) return page(context, "Your channels", "channels", "<div class=empty><b>No channels yet</b><br>Follow a channel from its menu or import subscriptions in Settings</div>");
        StringBuilder body = new StringBuilder("<div class=chips><a class='chip" + (channelId == null ? " on" : "") + "' href='" + CHANNELS + "'>All</a>");
        for (Subscriptions.Channel c : followed) {
            body.append("<a class='chip").append(c.id.equals(channelId) ? " on" : "").append("' href='").append(CHANNELS).append("?c=").append(c.id).append("'>");
            if (!c.avatar.isEmpty()) body.append("<img src='").append(esc(c.avatar)).append("' alt=''>");
            body.append(esc(c.name.isEmpty() ? "Channel" : c.name)).append("</a>");
        }
        body.append("</div>");
        List<Subscriptions.Video> videos;
        if (channelId != null) videos = Subscriptions.channelVideos(channelId);
        else videos = Subscriptions.feed(context);
        Map<String, RecentVideos.Item> recent = new HashMap<>();
        for (RecentVideos.Item item : RecentVideos.list(context)) recent.put(item.id, item);
        StringBuilder grid = new StringBuilder("<div class=grid>");
        int shown = 0;
        for (Subscriptions.Video v : videos) {
            // Shorts are made for a vertical phone screen, they stay out of the car's list
            if (v.shorts) continue;
            if (++shown > 200) break;
            RecentVideos.Item watched = recent.get(v.id);
            String href = watched != null ? watched.url() : "https://m.youtube.com/watch?v=" + v.id;
            grid.append("<a class=card href='").append(esc(href)).append("'><div class=thumb><img loading=lazy src='https://i.ytimg.com/vi/").append(v.id).append("/mqdefault.jpg' alt=''>");
            if (watched != null) grid.append("<div class=track></div><div class=bar style='width:").append(Math.max(3, (int) (watched.progress() * 100))).append("%'></div>");
            grid.append("</div><div class=t>").append(esc(v.title)).append("</div><div class=m>").append(esc(v.channel)).append(v.published > 0 ? " · " + ago(v.published) : "")
                .append(v.views > 0 ? " · " + views(v.views) : "").append("</div></a>");
        }
        grid.append("</div>");
        if (shown == 0) grid = new StringBuilder(Subscriptions.isRefreshing() || channelId != null ? "<div class=empty>Loading the newest videos</div>" : "<div class=empty>No videos found, check your connection</div>");
        // The feed refreshes in the background, an open page picks the new list up without a tap when it is still at the top
        String watch = channelId != null ? "" : "<div id=fresh class=fresh onclick='location.reload()'>New videos</div><script>(()=>{let v=" + Subscriptions.version
            + ";setInterval(()=>fetch('version').then(r=>r.json()).then(s=>{if(s.v!==v){v=s.v;if(scrollY<150)location.reload();else document.getElementById('fresh').style.display='block';}}).catch(()=>{}),2500);})()</script>";
        return page(context, "Your channels", "channels", body + grid.toString() + watch);
    }

    // Phone videos

    private static String phone(Context context, String folder) {
        return phone(context, folder, false);
    }

    private static String phone(Context context, String folder, boolean onPhone) {
        if (!PhoneVideos.allowed(context)) return page(context, "On this phone", "phone", "<div class=empty><b>Phone videos are off</b><br>"
            + (onPhone ? "Allow video access in Settings<br><a class=action href='" + VIDEO_SETTINGS + "'>Open Settings</a>"
                : "Open Aux on your phone, go to Settings and allow Phone videos") + "</div>");
        List<PhoneVideos.Video> all = PhoneVideos.list(context, null);
        if (all.isEmpty()) return page(context, "On this phone", "phone", PhoneVideos.limited(context)
            ? "<div class=empty><b>No videos selected</b><br>" + (onPhone
                ? "Choose videos to add to your library<br><a class=action href='" + VIDEO_SETTINGS + "'>Choose videos</a>"
                : "Choose videos in Aux Settings on your phone") + "</div>"
            : "<div class=empty>No videos on this phone</div>");
        StringBuilder body = new StringBuilder("<div class=chips><a class='chip" + (folder == null ? " on" : "") + "' href='" + BASE + "phone'>All</a>");
        for (Map.Entry<String, Integer> f : PhoneVideos.folders(all).entrySet())
            body.append("<a class='chip").append(f.getKey().equals(folder) ? " on" : "").append("' href='").append(BASE).append("phone?f=").append(Uri.encode(f.getKey())).append("'>").append(esc(f.getKey())).append("</a>");
        body.append("</div><div class=grid>");
        int shown = 0;
        for (PhoneVideos.Video v : all) {
            if (folder != null && !folder.equals(v.folder)) continue;
            if (++shown > 300) break;
            card(body, BASE + "play?src=phone&id=" + v.id, BASE + "thumb?id=" + v.id, v.title, v.folder + " · " + ago(v.addedAt), v.durationMs, MediaPositions.progress(context, v.key()));
        }
        body.append("</div>");
        return page(context, "On this phone", "phone", body.toString());
    }

    // Jellyfin and Plex

    private static String server(Context context, String serverId, String parent) {
        MediaServers.Server server = MediaServers.get(context, serverId);
        if (server == null) return home(context);
        StringBuilder body = new StringBuilder();
        try {
            if (parent == null) {
                List<MediaServers.Entry> resume = MediaServers.resume(context, server);
                if (!resume.isEmpty()) { body.append("<h2>Continue watching</h2><div class=grid>"); entries(context, body, server, resume); body.append("</div>"); }
                body.append("<h2>Libraries</h2><div class=grid>");
                entries(context, body, server, MediaServers.libraries(context, server));
                body.append("</div>");
            } else {
                String title = MediaServers.title(context, server, parent);
                body.append("<h2><a class=back href='javascript:history.back()'>‹</a>").append(esc(title)).append("</h2><div class=grid>");
                List<MediaServers.Entry> children = MediaServers.children(context, server, parent);
                entries(context, body, server, children);
                body.append("</div>");
                if (children.isEmpty()) body.append("<div class=empty>Nothing here</div>");
            }
        } catch (SecurityException signedOut) {
            body.append("<div class=empty><b>").append(esc(server.name)).append(" requires sign-in</b><br>Sign in again under Settings &gt; Media servers</div>");
        } catch (Exception error) {
            body.append("<div class=empty><b>Can't reach ").append(esc(server.name)).append("</b><br>Check your connection and server</div>");
        }
        return page(context, server.name, "server:" + server.id, body.toString());
    }

    private static void entries(Context context, StringBuilder out, MediaServers.Server server, List<MediaServers.Entry> entries) {
        for (MediaServers.Entry e : entries) {
            String image = e.image.isEmpty() ? null : BASE + "img?s=" + server.id + "&p=" + Uri.encode(e.image);
            if (e.folder) {
                out.append("<a class='card folder' href='").append(BASE).append("server?s=").append(server.id).append("&p=").append(Uri.encode(e.id)).append("'><div class=thumb>")
                    .append(FOLDER).append(image == null ? "" : "<img loading=lazy src='" + esc(image) + "' alt='' onerror=\"this.style.display='none'\">")
                    .append("</div><div class=t>").append(esc(e.title)).append("</div>").append(e.subtitle.isEmpty() ? "" : "<div class=m>" + esc(e.subtitle) + "</div>").append("</a>");
            } else {
                float progress = e.durationMs > 0 && e.positionMs > 0 ? e.positionMs / (float) e.durationMs : MediaPositions.progress(context, (MediaServers.PLEX.equals(server.type) ? "plex:" : "jf:") + server.id + ":" + e.id.replace("meta:", ""));
                card(out, BASE + "play?src=server&s=" + server.id + "&i=" + Uri.encode(e.id), image, e.title, e.subtitle, e.durationMs, progress);
            }
        }
    }

    private static void card(StringBuilder out, String href, String image, String title, String meta, long durationMs, float progress) {
        out.append("<a class=card href='").append(esc(href)).append("'><div class=thumb>");
        if (image != null) out.append("<img loading=lazy src='").append(esc(image)).append("' alt='' onerror=\"this.style.display='none'\">");
        // A Twitch broadcast or clip before a frame was kept from it shows Twitch's mark instead of an empty box
        else if (href.contains("continue?id=twitch")) out.append("<div class=ph>").append(TWITCH_MARK).append("</div>");
        if (durationMs > 0) out.append("<span class=dur>").append(clock(durationMs)).append("</span>");
        if (progress > 0.01f) out.append("<div class=track></div><div class=bar style='width:").append(Math.max(3, (int) (progress * 100))).append("%'></div>");
        out.append("</div><div class=t>").append(esc(title)).append("</div>");
        if (meta != null && !meta.isEmpty()) out.append("<div class=m>").append(esc(meta)).append("</div>");
        out.append("</a>");
    }

    // The page around every view, its tabs switch between the library's sources

    private static final String FOLDER = "<svg viewBox='0 0 24 24' width='44' height='44' fill='#aaa'><path d='M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>";

    private static final String CSS = ":root{color-scheme:dark}*{box-sizing:border-box}html{background:#0f0f0f;min-height:100%}body{margin:0;min-height:100vh;background:#0f0f0f;color:#f1f1f1;font-family:Roboto,'Segoe UI',system-ui,sans-serif;-webkit-tap-highlight-color:transparent}"
        + "a{color:inherit;text-decoration:none;outline:none}"
        + ".tabs{position:sticky;top:0;z-index:3;background:#0f0f0f;display:flex;gap:8px;padding:10px 12px 8px;overflow-x:auto;scrollbar-width:none}.tabs::-webkit-scrollbar,.chips::-webkit-scrollbar{display:none}"
        + ".tab{flex:none;padding:9px 18px;border-radius:20px;background:#272727;font-size:15px;font-weight:500;white-space:nowrap}.tab.on{background:#f1f1f1;color:#0f0f0f}"
        + ".chips{display:flex;gap:8px;padding:2px 12px 12px;overflow-x:auto;scrollbar-width:none}"
        + ".chip{flex:none;display:flex;align-items:center;gap:8px;padding:6px 14px;border-radius:16px;background:#1f1f1f;color:#ddd;font-size:14px;white-space:nowrap;max-width:240px;overflow:hidden;text-overflow:ellipsis}"
        + ".chip img{width:22px;height:22px;border-radius:50%}.chip.on{background:#f1f1f1;color:#0f0f0f}"
        + ".tab:focus,.chip:focus,.back:focus{box-shadow:0 0 0 3px #fff}"
        + "h2{font-size:18px;margin:12px 12px 10px;display:flex;align-items:center;gap:10px}.back{font-size:28px;line-height:1;padding:0 10px 4px;border-radius:12px;background:#272727}"
        + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:16px 12px;padding:0 12px 28px}"
        + ".card{display:block;border-radius:12px;content-visibility:auto;contain-intrinsic-size:auto 230px}.card:focus .thumb{box-shadow:0 0 0 3px #fff}"
        + ".thumb{position:relative;aspect-ratio:16/9;background:#222;border-radius:12px;overflow:hidden;display:flex;align-items:center;justify-content:center}"
        + ".thumb img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}"
        + ".dur{position:absolute;right:6px;bottom:8px;background:rgba(0,0,0,.8);font-size:12px;font-weight:500;padding:1px 5px;border-radius:4px}"
        + ".track{position:absolute;left:0;right:0;bottom:0;height:4px;background:rgba(255,255,255,.35)}.bar{position:absolute;left:0;bottom:0;height:4px;background:#f03}"
        + ".t{font-size:15px;line-height:1.3;margin-top:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}"
        + ".m{font-size:13px;color:#aaa;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
        + ".empty{padding:48px 28px;text-align:center;color:#aaa;font-size:16px;line-height:1.55;max-width:640px;margin:0 auto}.empty b{color:#f1f1f1;font-size:18px}"
        + ".action{display:inline-flex;align-items:center;justify-content:center;min-height:48px;margin-top:20px;padding:10px 22px;border-radius:24px;background:#f1f1f1;color:#0f0f0f;font-weight:500}"
        + ".fresh{display:none;position:fixed;top:62px;left:50%;transform:translateX(-50%);z-index:4;background:#f1f1f1;color:#0f0f0f;font-weight:500;padding:9px 18px;border-radius:20px}";

    private static String page(Context context, String title, String active, String body) {
        StringBuilder tabs = new StringBuilder("<nav class=tabs>");
        tabs.append(tab("Your channels", CHANNELS, "channels".equals(active)));
        tabs.append(tab("On this phone", BASE + "phone", "phone".equals(active)));
        for (MediaServers.Server s : MediaServers.list(context)) tabs.append(tab(s.name, BASE + "server?s=" + s.id, ("server:" + s.id).equals(active)));
        tabs.append("</nav>");
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(title) + "</title><style>" + CSS + "</style>"
            + "</head><body data-tube-library=true>" + tabs + body + "</body></html>";
    }

    private static String tab(String label, String href, boolean on) {
        return "<a class='tab" + (on ? " on" : "") + "' href='" + esc(href) + "'>" + esc(label) + "</a>";
    }

    private static WebResourceResponse html(String page) { return text("text/html", page); }

    private static WebResourceResponse text(String mime, String value) {
        WebResourceResponse response = new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        response.setResponseHeaders(headers);
        return response;
    }

    private static WebResourceResponse image(byte[] data) {
        if (data == null) return new WebResourceResponse("image/jpeg", null, 404, "Not found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
        WebResourceResponse response = new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(data));
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "max-age=86400");
        response.setResponseHeaders(headers);
        return response;
    }

    static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String ago(long at) {
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
        if (minutes < 60) return minutes <= 1 ? "just now" : minutes + " minutes ago";
        long hours = minutes / 60;
        if (hours < 24) return hours == 1 ? "1 hour ago" : hours + " hours ago";
        long days = hours / 24;
        if (days < 7) return days == 1 ? "1 day ago" : days + " days ago";
        if (days < 30) return days / 7 == 1 ? "1 week ago" : days / 7 + " weeks ago";
        if (days < 365) return days / 30 <= 1 ? "1 month ago" : days / 30 + " months ago";
        return days / 365 == 1 ? "1 year ago" : days / 365 + " years ago";
    }

    private static String views(long views) {
        if (views >= 1_000_000) return String.format(java.util.Locale.US, views >= 10_000_000 ? "%.0fM views" : "%.1fM views", views / 1e6);
        if (views >= 1000) return String.format(java.util.Locale.US, views >= 10_000 ? "%.0fK views" : "%.1fK views", views / 1e3);
        return views == 1 ? "1 view" : views + " views";
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private LibraryPages() {}
}
