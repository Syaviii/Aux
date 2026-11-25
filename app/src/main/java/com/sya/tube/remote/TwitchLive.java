package com.sya.tube.remote;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Whether a Twitch channel is live, read from its public preview picture: a live channel serves the picture, an
 * offline one redirects to Twitch's placeholder. Network work, called off the main thread
 */
public final class TwitchLive {
    public static boolean isLive(String login) {
        if (login == null || !login.matches("[A-Za-z0-9_]{2,25}")) return false;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://static-cdn.jtvnw.net/previews-ttv/live_user_"
                + login.toLowerCase(java.util.Locale.ROOT) + "-80x45.jpg").openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            return connection.getResponseCode() == 200;
        } catch (Exception unreachable) {
            // Without an answer the pick is treated as a stream, as it was before the check existed
            return true;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private TwitchLive() {}
}
