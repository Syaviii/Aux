package com.sya.tube.player;

import android.content.Context;
import android.os.Handler;
import android.preference.PreferenceManager;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Android Auto plays audio through the car with more delay than the projected picture, and the phone cannot measure it.
 * Video frames are released against a clock shifted by the chosen offset, so a positive offset holds the picture back to meet the sound
 */
public final class SyncedRenderersFactory extends com.google.android.exoplayer2.DefaultRenderersFactory {
    public static final String PREF_OFFSET = "pref_av_offset_ms";
    public static final int DEFAULT_OFFSET_MS = 250;
    public static final int STEP_MS = 50;
    public static final int LIMIT_MS = 1000;
    private static volatile long offsetUs = DEFAULT_OFFSET_MS * 1000L;

    public SyncedRenderersFactory(Context context) {
        super(context);
        offsetUs = offsetMs(context) * 1000L;
    }

    public static int offsetMs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_OFFSET, DEFAULT_OFFSET_MS);
    }

    /** Applies at once to the playing video and is remembered for the next one */
    public static int adjust(Context context, int deltaMs) {
        int value = Math.max(-LIMIT_MS, Math.min(LIMIT_MS, offsetMs(context) + deltaMs));
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_OFFSET, value).apply();
        offsetUs = value * 1000L;
        com.sya.tube.diag.Diag.log("sync", "offset " + value + " ms");
        return value;
    }

    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector selector, boolean enableDecoderFallback,
                                       Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        out.add(new OffsetVideoRenderer(context, getCodecAdapterFactory(), selector, allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener));
    }

    private static final class OffsetVideoRenderer extends MediaCodecVideoRenderer {
        OffsetVideoRenderer(Context context, MediaCodecAdapter.Factory adapters, MediaCodecSelector selector, long joiningMs, boolean fallback,
                            Handler handler, VideoRendererEventListener listener) {
            super(context, adapters, selector, joiningMs, fallback, handler, listener, 50);
        }

        private long lastKeptUs = com.google.android.exoplayer2.C.TIME_UNSET;

        @Override
        protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
            super.onPositionReset(positionUs, joining);
            lastKeptUs = com.google.android.exoplayer2.C.TIME_UNSET;
        }

        @Override
        protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodecAdapter codec, ByteBuffer buffer, int bufferIndex, int bufferFlags,
                                              int sampleCount, long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer, boolean isLastBuffer, Format format) throws ExoPlaybackException {
            // Android Auto captures the screen at the car's rate, frames faster than that get picked unevenly and look like stutter,
            // so a 60 fps video on a 30 fps car shows every second frame on an even beat instead
            int car = YoutubeStreams.carFrameRate;
            if (!isDecodeOnlyBuffer && !isLastBuffer && car > 0 && lastKeptUs != com.google.android.exoplayer2.C.TIME_UNSET) {
                long gap = bufferPresentationTimeUs - lastKeptUs;
                if (gap >= 0 && gap < 750_000L / car) {
                    skipOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs);
                    return true;
                }
            }
            // Before playback starts the first frame has to be shown at its own time, held back it never appears and the player
            // waits for it forever, which is what a video starting at zero did
            long shift = getState() == STATE_STARTED ? offsetUs : 0;
            boolean done = super.processOutputBuffer(positionUs - shift, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
            if (done && !isDecodeOnlyBuffer) lastKeptUs = bufferPresentationTimeUs;
            return done;
        }
    }
}
