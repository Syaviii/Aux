package com.sya.tube.player;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import java.util.HashMap;

/** Applies the stream client's transport, without browser cookies or credentials.
 * Protocol reference: TeamNewPipe/NewPipe player/datasource/YoutubeHttpDataSource.java.
 */
public final class YoutubeDataSource {
    public static DataSource.Factory factory() {
        var http = new DefaultHttpDataSource.Factory().setUserAgent(YoutubeStreams.USER_AGENT)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(15000).setKeepPostFor302Redirects(true);
        return new ResolvingDataSource.Factory(http, spec -> {
            String url=spec.uri.toString();
            var headers=new HashMap<>(spec.httpRequestHeaders);
            headers.put("TE", "trailers");
            headers.put("User-Agent", YoutubeParsingHelper.isVisionOsStreamingUrl(url)
                ? YoutubeParsingHelper.getVisionOsUserAgent(null) : YoutubeStreams.USER_AGENT);
            if(YoutubeParsingHelper.isWebStreamingUrl(url)) {
                headers.put("Origin", "https://www.youtube.com");
                headers.put("Referer", "https://www.youtube.com");
                headers.put("Sec-Fetch-Dest", "empty");
                headers.put("Sec-Fetch-Mode", "cors");
                headers.put("Sec-Fetch-Site", "cross-site");
            }
            return spec.buildUpon().setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .setHttpBody(new byte[]{0x78,0}).setHttpRequestHeaders(headers).build();
        });
    }
    private YoutubeDataSource() {}
}
