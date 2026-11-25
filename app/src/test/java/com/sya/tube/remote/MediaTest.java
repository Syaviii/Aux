package com.sya.tube.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaTest {
    @Test public void patreonPostAddressesShareOneKey() {
        assertEquals("patreon:170474323", Media.key("https://www.patreon.com/posts/170474323"));
        assertEquals("patreon:170474323", Media.key("https://www.patreon.com/posts/bonus-video-self-170474323"));
        assertEquals("patreon:170474323", Media.key("https://www.patreon.com/danandphil/posts/bonus-video-self-170474323?collection=2280258"));
        assertEquals("patreon:170474323", Media.key("https://patreon.com/posts/170474323/"));
    }

    @Test public void patreonPagesThatAreNotPostsHaveNoKey() {
        assertNull(Media.patreonPost("https://www.patreon.com/home"));
        assertNull(Media.patreonPost("https://www.patreon.com/danandphil/posts"));
        assertNull(Media.patreonPost("https://www.patreon.com/collection/2280258"));
        assertNull(Media.patreonPost("https://www.patreon.com/posts/no-number-here"));
        assertNull(Media.patreonPost("https://notpatreon.com/posts/170474323"));
        assertNull(Media.patreonPost(null));
    }

    @Test public void patreonPostsPlayFromTheirShortAddress() {
        assertEquals("https://www.patreon.com/posts/170474323", Media.playableUrl("https://www.patreon.com/danandphil/posts/bonus-170474323"));
        assertEquals("https://www.patreon.com/posts/170474323", Media.url("patreon:170474323", 90_000));
    }

    @Test public void youtubeAddressesKeepTheirStartTime() {
        assertEquals("https://m.youtube.com/watch?v=abc123&t=90s", Media.url("abc123", 90_000));
        assertEquals("https://m.youtube.com/watch?v=abc123", Media.url("abc123", 3000));
    }

    @Test public void onlyYoutubeKeysGetYoutubeFeatures() {
        assertTrue(Media.isYoutube("abc123"));
        assertFalse(Media.isYoutube("patreon:170474323"));
        assertTrue(Media.isPatreon("patreon:170474323"));
        assertFalse(Media.isYoutube(null));
    }

    @Test public void titlesLoseTheSiteName() {
        assertEquals("BONUS VIDEO: The Fanfic Trope Self-Audit", Media.cleanTitle("patreon:1", "BONUS VIDEO: The Fanfic Trope Self-Audit | Dan and Phil on Patreon"));
        assertEquals("A talk", Media.cleanTitle("abc", "A talk - YouTube"));
    }

    @Test public void pagesBelongToTheirService() {
        assertEquals(Media.YOUTUBE_SERVICE, Media.service("https://m.youtube.com/results?search_query=x"));
        assertEquals(Media.PATREON_SERVICE, Media.service("https://www.patreon.com/home"));
        assertEquals(Media.LIBRARY_SERVICE, Media.service("https://appassets.androidplatform.net/tube/channels"));
        assertNull(Media.service("https://appassets.androidplatform.net/tube/start"));
        assertNull(Media.service("https://appassets.androidplatform.net/tube/open?to=patreon"));
        assertNull(Media.service("https://www.google.com/search?q=x"));
    }

    @Test public void thumbnailsComeFromEachSite() {
        assertEquals("https://www.patreon.com/ig/card-teaser-image/post/42/landscape.png", Media.thumbnail("patreon:42"));
        assertEquals("https://i.ytimg.com/vi/abc/mqdefault.jpg", Media.thumbnail("abc"));
    }
    @Test public void twitchAddressesGetKeys() {
        assertEquals("twitch:xqc", Media.key("https://www.twitch.tv/xQc"));
        assertEquals("twitch:xqc", Media.key("https://m.twitch.tv/xqc/"));
        assertEquals("twitch:video:2283412345", Media.key("https://www.twitch.tv/videos/2283412345?t=1h2m3s"));
        assertEquals("twitch:clip:FunnyClipSlug-abc", Media.key("https://www.twitch.tv/xqc/clip/FunnyClipSlug-abc"));
        assertEquals("twitch:clip:FunnyClipSlug-abc", Media.key("https://clips.twitch.tv/FunnyClipSlug-abc"));
    }

    @Test public void twitchPagesThatAreNotStreamsHaveNoKey() {
        assertNull(Media.twitchKey("https://m.twitch.tv/"));
        assertNull(Media.twitchKey("https://www.twitch.tv/directory/category/just-chatting"));
        assertNull(Media.twitchKey("https://m.twitch.tv/search?term=chess"));
        assertNull(Media.twitchKey("https://www.twitch.tv/settings"));
        assertNull(Media.twitchKey("https://www.twitch.tv/xqc/videos"));
        assertNull(Media.twitchKey("https://nottwitch.tv/xqc"));
    }

    @Test public void twitchKeysOpenOnTheMobileSite() {
        assertEquals("https://m.twitch.tv/xqc", Media.url("twitch:xqc", 90_000));
        assertEquals("https://m.twitch.tv/videos/22?t=1h1m5s", Media.url("twitch:video:22", 3_665_000));
        assertEquals("https://m.twitch.tv/videos/22?t=1m30s", Media.url("twitch:video:22", 90_000));
        assertEquals("https://m.twitch.tv/clip/Slug", Media.playableUrl("https://clips.twitch.tv/Slug"));
    }

    @Test public void onlyTwitchChannelsAreLive() {
        assertTrue(Media.isLive("twitch:xqc"));
        assertFalse(Media.isLive("twitch:video:22"));
        assertFalse(Media.isLive("twitch:clip:Slug"));
        assertFalse(Media.isYoutube("twitch:xqc"));
        assertEquals("Twitch", Media.service("https://m.twitch.tv/directory").equals(Media.TWITCH_SERVICE) ? "Twitch" : "");
        assertEquals("Chess night", Media.cleanTitle("twitch:video:22", "Chess night - Twitch"));
    }
}
