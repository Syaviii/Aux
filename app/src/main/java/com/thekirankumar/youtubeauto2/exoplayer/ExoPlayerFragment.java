// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.exoplayer;
import android.content.*;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.ui.*;
import com.thekirankumar.youtubeauto2.R;
import com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment;
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService;
import com.thekirankumar.youtubeauto2.utils.BroadcastFromUI;
import java.io.File;
public class ExoPlayerFragment extends Fragment implements Player.Listener {
    private ExoPlayer player;
    private static java.lang.ref.WeakReference<ExoPlayerFragment> activeNative=new java.lang.ref.WeakReference<>(null);
    public static void pauseForWebPlayback() { ExoPlayerFragment value=activeNative.get(); if(value!=null&&value.player!=null)value.player.pause(); }
    private PlayerView playerView;
    private TextView title,album;
    private View loading;
    private OnFragmentInteractionListener listener;
    private BroadcastReceiver receiver;
    private long resumePosition;
    private int resumeIndex;
    private boolean playWhenReady=true;
    private java.util.concurrent.Future<?> resolution;
    private boolean remoteVideo;
    private int sourceFps,sourceItag;
    // Handoff mode prepares paused underneath the web fullscreen view, the host swaps once a frame is ready
    private boolean handoff,handoffReadySignalled,handoffSwapPending;
    public interface OnFragmentInteractionListener {
        WebViewCarFragment.AspectRatio getAspectRatio();
        void onNativePlayerControlsVisibilityChange(int visibility);
        default void onNativePlaybackChanged(boolean playing) {}
        default void onNativeVideoFailure() {}
        default void onNativeSkip(boolean next) {}
        default void onNativeHandoffReady() {}
        /** Where a handoff player should start once its streams are known, the web video kept playing while they were looked up */
        default void handoffStartPosition(long requested, java.util.function.LongConsumer start) { start.accept(requested); }
        default void onNativeHandoffPlaying() {}
        default void onSegmentSkipped(String category) {}
        /** A segment set to ask is playing, it can be skipped until it ends */
        default void onSegmentOffer(String category) {}
        default void onSegmentOfferGone() {}
    }
    public static ExoPlayerFragment newInstance(String file) {
        ExoPlayerFragment fragment=new ExoPlayerFragment(); Bundle args=new Bundle(); args.putString("file_path",file); fragment.setArguments(args); return fragment;
    }
    public static ExoPlayerFragment youtube(String url,long position,boolean playing,float speed,int quality) {
        ExoPlayerFragment f=new ExoPlayerFragment(); Bundle args=new Bundle();args.putString("youtube_url",url);args.putLong("position",position);args.putBoolean("playing",playing);args.putFloat("speed",speed);args.putInt("quality",quality);f.setArguments(args);return f;
    }
    public static ExoPlayerFragment handoff(String url,long position,float speed,int quality,String captions,boolean captionsAuto) {
        ExoPlayerFragment f=youtube(url,position,false,speed,quality);f.requireArguments().putBoolean("handoff",true);
        f.requireArguments().putString("captions",captions);f.requireArguments().putBoolean("captionsAuto",captionsAuto);return f;
    }
    /** Phone videos and media server videos, played in order from the picked one, each continuing where it stopped */
    public static ExoPlayerFragment media(java.util.ArrayList<Bundle> items,int index) {
        ExoPlayerFragment f=new ExoPlayerFragment(); Bundle args=new Bundle();args.putParcelableArrayList("media",items);args.putInt("index",index);f.setArguments(args);return f;
    }
    public boolean isMedia() { return mediaMode; }
    public void next() { if(player!=null&&player.hasNextMediaItem())player.seekToNextMediaItem(); }
    public void previous() { if(player==null)return; if(player.getCurrentPosition()>3000||!player.hasPreviousMediaItem())player.seekTo(0);else player.seekToPreviousMediaItem(); }
    public void setSpeed(float speed) { if(player!=null)player.setPlaybackSpeed(speed); }
    public float speed() { return player==null?1f:player.getPlaybackParameters().speed; }
    // SponsorBlock segments of this video, each one is skipped once, a segment the driver seeks back into again stays
    private java.util.List<com.sya.tube.player.SponsorBlock.Segment> segments=java.util.Collections.emptyList();
    private final java.util.Set<Long> skippedSegments=new java.util.HashSet<>();
    private com.sya.tube.player.SponsorBlock.Segment offeredSegment;
    public void setSegments(java.util.List<com.sya.tube.player.SponsorBlock.Segment> value) {
        segments=value;
        if(playerView!=null){playerView.removeCallbacks(segmentWatch);if(!value.isEmpty())playerView.postDelayed(segmentWatch,300);}
    }
    public void skipOfferedSegment() {
        if(player==null||offeredSegment==null)return;
        skippedSegments.add(offeredSegment.startMs);player.seekTo(offeredSegment.endMs);offeredSegment=null;listener.onSegmentOfferGone();
    }
    private final Runnable segmentWatch=new Runnable(){public void run(){
        if(player==null||playerView==null||segments.isEmpty())return;
        playerView.postDelayed(this,300);
        if(!player.isPlaying())return;
        long t=player.getCurrentPosition();
        com.sya.tube.player.SponsorBlock.Segment inside=null;
        for(var segment:segments)if(t>=segment.startMs&&t<segment.endMs-400){inside=segment;break;}
        if(inside!=null&&"skip".equals(inside.mode)&&skippedSegments.add(inside.startMs)){player.seekTo(inside.endMs);listener.onSegmentSkipped(inside.category);return;}
        var offer=inside!=null&&"ask".equals(inside.mode)&&!skippedSegments.contains(inside.startMs)?inside:null;
        if(offer!=offeredSegment){offeredSegment=offer;if(offer!=null)listener.onSegmentOffer(offer.category);else listener.onSegmentOfferGone();}
    }};
    private boolean mediaMode;
    private final java.util.Set<Integer> fellBack=new java.util.HashSet<>();
    private java.util.ArrayList<Bundle> mediaItems() { return requireArguments().getParcelableArrayList("media"); }
    private Bundle currentMedia() {
        if(player==null||!mediaMode)return null;
        int index=player.getCurrentMediaItemIndex();var items=mediaItems();
        return index>=0&&index<items.size()?items.get(index):null;
    }
    /** The place to continue from, the server's own or the one kept on the phone */
    private long startFor(Bundle item) {
        long start=item.getLong("start");
        if(start<=0)start=com.sya.tube.library.MediaPositions.position(requireContext(),item.getString("key"));
        long duration=item.getLong("duration");
        return duration>0&&start>duration*0.95?0:start;
    }
    private MediaItem mediaItem(Bundle item,String uri) {
        MediaItem.Builder builder=new MediaItem.Builder().setUri(Uri.parse(uri)).setMediaId(String.valueOf(item.getString("key")))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(item.getString("title")).setSubtitle(item.getString("subtitle")).build());
        if(uri.contains(".m3u8")||item.getString("mime")!=null&&uri.equals(item.getString("uri")))builder.setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8);
        return builder.build();
    }
    private com.google.android.exoplayer2.source.MediaSource.Factory mediaSources() {
        var http=new com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory().setUserAgent("Aux").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000);
        return new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(new com.google.android.exoplayer2.upstream.DefaultDataSource.Factory(requireContext(),http));
    }
    // Media positions are kept on the phone every few seconds and reported to the server so its own apps continue at the same spot
    private int mediaTick;
    private final Runnable mediaProgress=new Runnable(){public void run(){
        if(player==null||playerView==null)return;
        playerView.postDelayed(this,5000);
        Bundle item=currentMedia();
        if(item==null||!player.isPlaying())return;
        com.sya.tube.library.MediaPositions.save(requireContext(),item.getString("key"),player.getCurrentPosition(),duration());
        if(++mediaTick%2==0)com.sya.tube.library.MediaServers.report(requireContext(),item,"playing",player.getCurrentPosition(),duration());
    }};
    private Bundle reportedMedia;
    private void reportMediaStart() {
        Bundle item=currentMedia();
        if(item==null||item==reportedMedia)return;
        reportedMedia=item;
        com.sya.tube.library.MediaServers.report(requireContext(),item,"start",player.getCurrentPosition(),duration());
    }
    private void reportMediaStop(boolean finished) {
        if(reportedMedia==null||getContext()==null)return;
        long length=Math.max(duration(),1);
        long position=finished?length:player==null?0:player.getCurrentPosition();
        com.sya.tube.library.MediaPositions.save(requireContext(),reportedMedia.getString("key"),position,length);
        com.sya.tube.library.MediaServers.report(requireContext(),reportedMedia,"stop",position,duration());
        reportedMedia=null;
    }
    public long bufferedPosition() { return player==null?0:player.getBufferedPosition(); }
    public boolean isReady() { return player!=null&&player.getPlaybackState()==Player.STATE_READY; }
    public void seekAndPlay(long position,boolean play) {
        if(player==null)return;
        handoffSwapPending=true;
        Player.Listener ready=new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(!play&&state==Player.STATE_READY&&handoffSwapPending){handoffSwapPending=false;player.removeListener(this);listener.onNativeHandoffPlaying();}}};
        if(!play)player.addListener(ready);
        // Seeking to the frame already shown would flush and re-decode, which is exactly the delay the handoff avoids
        if(Math.abs(player.getCurrentPosition()-position)>60)player.seekTo(position);
        player.setPlayWhenReady(play);
        // A paused handoff never reports isPlaying, so it is complete as soon as the seek lands
        if(!play)ready.onPlaybackStateChanged(player.getPlaybackState());
    }
    public long position() { return player==null||player.getPlaybackState()==Player.STATE_IDLE?requireArguments().getLong("position"):player.getCurrentPosition(); }
    public boolean playing() { return player!=null&&player.getPlayWhenReady(); }
    public void toggle() { if(player!=null)player.setPlayWhenReady(!player.getPlayWhenReady()); }
    public void pause() { if(player!=null)player.pause(); }
    public void play() { if(player!=null)player.play(); }
    public void seekBy(long deltaMs) { if(player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()+deltaMs)); }
    public long duration() { return player==null||player.getDuration()==C.TIME_UNSET?0:player.getDuration(); }
    public String title() { return title==null?"":String.valueOf(title.getText()); }
    public void restart() { if(player!=null)player.seekTo(0); }
    public void revealControls() { if(playerView!=null)playerView.showController(); }
    public int state() { return player==null?Player.STATE_IDLE:player.getPlaybackState(); }
    private java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> chapters=java.util.Collections.emptyList();
    private boolean captionsLoaded,captionsOn;
    public java.util.List<com.sya.tube.player.YoutubeStreams.Chapter> chapters() { return chapters; }
    /** True once the video has a caption track loaded next to it, it can then be shown or hidden without reloading */
    public boolean hasCaptions() { return captionsLoaded; }
    public boolean captionsOn() { return captionsLoaded&&captionsOn; }
    public void setCaptionsOn(boolean on) {
        captionsOn=on;
        if(player!=null&&captionsLoaded)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!on).build());
    }
    /** Chapter starts show as marks on the seek bar, the same way YouTube draws them */
    private void showChapterMarks() {
        if(playerView==null||chapters.size()<2)return;
        long[] marks=new long[chapters.size()-1];boolean[] played=new boolean[marks.length];
        for(int i=1;i<chapters.size();i++)marks[i-1]=chapters.get(i).startMs;
        playerView.setExtraAdGroupMarkers(marks,played);
    }
    // The car can keep the sound going while another app is on screen, the picture is not decoded meanwhile
    public static volatile boolean keepPlayingWhenHidden;
    private boolean playingHidden;
    public boolean playingHidden() { return playingHidden; }
    private void setVideoDecoding(boolean on) {
        if(player==null)return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,!on).build());
    }
    @Override public void onAttach(Context context) {
        super.onAttach(context); Object parent=getParentFragment(); if(parent==null) parent=context;
        if(!(parent instanceof OnFragmentInteractionListener)) throw new IllegalStateException("Player host missing");
        listener=(OnFragmentInteractionListener)parent;
    }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle saved) {
        return inflater.inflate(R.layout.fragment_exo_player,container,false);
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        if(saved!=null) { resumePosition=saved.getLong("position");resumeIndex=saved.getInt("index");playWhenReady=saved.getBoolean("playing",false); }
        title=view.findViewById(R.id.player_title); album=view.findViewById(R.id.player_album); playerView=view.findViewById(R.id.exoplayer_view); loading=view.findViewById(R.id.player_loading);
        playerView.setControllerShowTimeoutMs(8000);
        everReady=false;
        playerView.setControllerVisibilityListener((PlayerControlView.VisibilityListener)visibility->{ title.setVisibility(visibility); album.setVisibility(visibility); listener.onNativePlayerControlsVisibilityChange(visibility); });
        DefaultRenderersFactory renderers=new com.sya.tube.player.SyncedRenderersFactory(requireContext());
        // A deeper buffer rides out cellular dips while driving, startup still begins after half a second
        player=new ExoPlayer.Builder(requireContext(),renderers).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(30000,60000,500,3000).setPrioritizeTimeOverSizeThresholds(true).build()).build(); playerView.setPlayer(player); player.addListener(this);
        activeNative=new java.lang.ref.WeakReference<>(this);
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG)player.addAnalyticsListener(new com.google.android.exoplayer2.util.EventLogger());
        player.setAudioAttributes(new com.google.android.exoplayer2.audio.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlaybackSpeed(requireArguments().getFloat("speed",1f));
        remoteVideo=requireArguments().containsKey("youtube_url");
        mediaMode=requireArguments().containsKey("media");
        handoff=requireArguments().getBoolean("handoff",false);
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG)android.util.Log.i("TubeNativeMetrics","stage playerCreated elapsed="+android.os.SystemClock.elapsedRealtime());
        if(remoteVideo) {
            title.setText("Loading video");album.setText("720p");
            if(saved==null)playWhenReady=requireArguments().getBoolean("playing",true);
            long seek=saved==null?requireArguments().getLong("position"):resumePosition;
            resolution=com.sya.tube.player.YoutubeStreams.WORKER.submit(()->{
                try {
                    var streams=com.sya.tube.player.YoutubeStreams.resolve(requireArguments().getString("youtube_url"),requireArguments().getInt("quality",720));
                    if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG)android.util.Log.i("TubeNativeMetrics","stage resolved elapsed="+android.os.SystemClock.elapsedRealtime());
                    view.post(()->{
                        if(player==null||getView()!=view)return;
                        var http=com.sya.tube.player.YoutubeDataSource.factory();
                        var factory=new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(http);
                        java.util.List<com.google.android.exoplayer2.source.MediaSource> sources=new java.util.ArrayList<>();
                        sources.add(factory.createMediaSource(MediaItem.fromUri(streams.video)));sources.add(factory.createMediaSource(MediaItem.fromUri(streams.audio)));
                        // Captions YouTube's own player showed carry over, the track is side loaded next to the video and audio
                        var track=streams.captionsFor(requireArguments().getString("captions"),requireArguments().getBoolean("captionsAuto"));
                        if(track==null)track=streams.captionsFor("en",false);
                        if(track==null&&!streams.captions.isEmpty())track=streams.captions.get(0);
                        if(track!=null){
                            var config=new MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url)).setMimeType(com.google.android.exoplayer2.util.MimeTypes.TEXT_VTT).setLanguage(track.language).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
                            sources.add(new com.google.android.exoplayer2.source.SingleSampleMediaSource.Factory(http).createMediaSource(config,C.TIME_UNSET));
                            captionsOn=requireArguments().getBoolean("captionsOn");
                            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage(track.language).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!captionsOn).build());
                            com.sya.tube.diag.Diag.log("player","captions "+track.language+(track.automatic?" automatic":""));
                        }
                        player.setMediaSource(new com.google.android.exoplayer2.source.MergingMediaSource(true,sources.toArray(new com.google.android.exoplayer2.source.MediaSource[0])));
                        chapters=streams.chapters;captionsLoaded=track!=null;
                        showChapterMarks();
                        title.setText(streams.title);album.setText(streams.height+"p");
                        sourceFps=streams.fps;sourceItag=streams.itag;
                        BroadcastFromUI.broadcastTitle(requireContext(),streams.title);
                        java.util.function.LongConsumer begin=position->{if(player==null||getView()!=view)return;player.seekTo(position);player.prepare();player.setPlayWhenReady(playWhenReady);};
                        if(handoff&&saved==null)listener.handoffStartPosition(seek,begin);else begin.accept(seek);
                    });
                } catch(Exception error) {
                    android.util.Log.w("TubePlayer","Public stream resolution failed: "+error.getClass().getSimpleName());
                    com.sya.tube.diag.Diag.log("player","stream lookup failed "+error.getClass().getSimpleName()+" "+error.getMessage());
                    view.post(()->{if(player!=null&&getView()==view)listener.onNativeVideoFailure();});
                }
            });
            view.postDelayed(()->{if(player!=null&&getView()==view&&!everReady&&player.getPlaybackState()!=Player.STATE_READY&&player.getPlaybackState()!=Player.STATE_ENDED)listener.onNativeVideoFailure();},30000);
            // Debug builds sample every 5 seconds for the benchmark, every build keeps a 10 second sample in the diagnostics log
            Runnable sample=new Runnable(){int tick;public void run(){
                if(player==null||getView()!=view)return;
                var count=player.getVideoDecoderCounters();if(count!=null){count.ensureUpdated();var format=player.getVideoFormat();
                    if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG){
                        View surface=playerView.getVideoSurfaceView();
                        boolean valid=surface instanceof android.view.SurfaceView&&((android.view.SurfaceView)surface).getHolder().getSurface().isValid();
                        android.util.Log.i("TubeNativeMetrics","position="+player.getCurrentPosition()+" rendered="+count.renderedOutputBufferCount+" dropped="+count.droppedBufferCount+" skipped="+count.skippedOutputBufferCount+" state="+player.getPlaybackState()+" playing="+player.isPlaying()+" width="+(format==null?0:format.width)+" height="+(format==null?0:format.height)+" fps="+sourceFps+" itag="+sourceItag+" buffer="+player.getTotalBufferedDuration()+" shown="+view.isShown()+" surfaceValid="+valid+" surfaceShown="+(surface!=null&&surface.isShown())+" surfaceWidth="+(surface==null?0:surface.getWidth())+" windowVisibility="+view.getWindowVisibility());
                    }
                    if(++tick%2==0&&player.isPlaying())com.sya.tube.diag.Diag.log("player","pos="+player.getCurrentPosition()/1000+"s rendered="+count.renderedOutputBufferCount+" dropped="+count.droppedBufferCount+" skipped="+count.skippedOutputBufferCount+" buffer="+player.getTotalBufferedDuration()/1000+"s "+(format==null?0:format.height)+"p"+sourceFps+" itag="+sourceItag+" car="+com.sya.tube.player.YoutubeStreams.carFrameRate+" sync="+com.sya.tube.player.SyncedRenderersFactory.offsetMs(requireContext()));}
                view.postDelayed(this,5000);
            }};view.postDelayed(sample,5000);
        } else if(mediaMode) {
            var items=mediaItems();
            if(items==null||items.isEmpty()){title.setText("Video unavailable");album.setText("Choose another one");return;}
            var factory=mediaSources();
            for(Bundle item:items)player.addMediaSource(factory.createMediaSource(mediaItem(item,item.getString("uri"))));
            int index=Math.max(0,Math.min(items.size()-1,saved==null?requireArguments().getInt("index"):resumeIndex));
            player.setPlaybackSpeed(com.sya.tube.settings.TubePrefs.speed(requireContext()));
            player.seekTo(index,saved==null?startFor(items.get(index)):resumePosition);player.prepare();player.setPlayWhenReady(playWhenReady);
            view.postDelayed(mediaProgress,5000);
        } else {
            String path=requireArguments().getString("file_path");
            PlayerQueue queue=new PlayerQueue(path);
            for(File file:queue.getCurrentQueue()) player.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            if(player.getMediaItemCount()==0) { title.setText("Video unavailable"); album.setText("Choose another file"); return; }
            player.seekTo(saved==null?queue.currentIndex():resumeIndex,resumePosition); player.prepare(); player.setPlayWhenReady(playWhenReady);
        }
        setAspectRatio(listener.getAspectRatio()); updateTitle();
        receiver=new BroadcastReceiver() {
            @Override public void onReceive(Context context,Intent intent) {
                long action=intent.getLongExtra(MyMediaBrowserService.ACTION_TYPE,0);
                if(player==null) return;
                if(action==PlaybackState.ACTION_PLAY) player.play();
                else if(action==PlaybackState.ACTION_PAUSE||action==PlaybackState.ACTION_STOP) player.pause();
                else if(action==PlaybackState.ACTION_SKIP_TO_NEXT) { if(remoteVideo)listener.onNativeSkip(true);else if(player.hasNextMediaItem())player.seekToNextMediaItem(); }
                else if(action==PlaybackState.ACTION_SKIP_TO_PREVIOUS) { if(remoteVideo)listener.onNativeSkip(false);else if(player.hasPreviousMediaItem())player.seekToPreviousMediaItem(); }
            }
        };
        ContextCompat.registerReceiver(requireContext(),receiver,new IntentFilter(MyMediaBrowserService.PLAYER_EVENT),ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    public void setAspectRatio(WebViewCarFragment.AspectRatio ratio) {
        if(playerView==null) return;
        int mode=ratio==WebViewCarFragment.AspectRatio.FILL?AspectRatioFrameLayout.RESIZE_MODE_FILL:ratio==WebViewCarFragment.AspectRatio.COVER?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT;
        playerView.setResizeMode(mode);
    }
    private void updateTitle() {
        if(remoteVideo)return;
        if(mediaMode){
            Bundle item=currentMedia();if(item==null)return;
            title.setText(item.getString("title"));album.setText(item.getString("subtitle"));
            BroadcastFromUI.broadcastTitle(requireContext(),item.getString("title"));
            return;
        }
        MediaItem item=player.getCurrentMediaItem(); if(item==null||item.localConfiguration==null) return;
        File file=new File(item.localConfiguration.uri.getPath()); title.setText(file.getName()); album.setText(file.getParent());
        BroadcastFromUI.broadcastTitle(requireContext(),file.getName());
    }
    @Override public void onMediaItemTransition(MediaItem item,int reason) {
        // The one before ended by itself or the driver skipped it, an automatic move means it was watched to the end
        if(mediaMode){reportMediaStop(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);if(player!=null&&player.isPlaying())reportMediaStart();}
        updateTitle();
    }
    @Override public void onRenderedFirstFrame() {
        if(handoff&&!handoffReadySignalled){handoffReadySignalled=true;listener.onNativeHandoffReady();}
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics","firstFrame elapsed="+android.os.SystemClock.elapsedRealtime());
    }
    @Override public void onIsPlayingChanged(boolean playing) {
        if(getContext()==null) return;
        if(playing&&handoffSwapPending){handoffSwapPending=false;listener.onNativeHandoffPlaying();}
        if(mediaMode&&player!=null){
            if(playing)reportMediaStart();
            else if(reportedMedia!=null){Bundle item=currentMedia();if(item!=null){com.sya.tube.library.MediaPositions.save(requireContext(),item.getString("key"),player.getCurrentPosition(),duration());com.sya.tube.library.MediaServers.report(requireContext(),item,"paused",player.getCurrentPosition(),duration());}}
        }
        if(playing) { com.thekirankumar.youtubeauto2.webview.PlaybackOwner.nativePlaying(); BroadcastFromUI.broadCastPlaying(getContext(),title.getText().toString()); }
        else if(com.thekirankumar.youtubeauto2.webview.PlaybackOwner.isNativeSelected()) BroadcastFromUI.broadCastPaused(getContext(),title.getText().toString());
        listener.onNativePlaybackChanged(playing);
    }
    // A freeze while driving shows up as buffering after playback had started, its length goes to the diagnostics log
    private long stallStartedAt;
    private boolean everReady;
    @Override public void onPlaybackStateChanged(int state) {
        if(state==Player.STATE_READY){
            if(stallStartedAt>0)com.sya.tube.diag.Diag.log("player","stall ended after "+(android.os.SystemClock.elapsedRealtime()-stallStartedAt)+" ms");
            stallStartedAt=0;everReady=true;
        } else if(state==Player.STATE_BUFFERING&&everReady&&player!=null&&player.getPlayWhenReady()&&stallStartedAt==0){
            stallStartedAt=android.os.SystemClock.elapsedRealtime();
            com.sya.tube.diag.Diag.log("player","stall at "+player.getCurrentPosition()/1000+"s buffer="+player.getTotalBufferedDuration()+"ms");
            if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG)android.util.Log.i("TubeNativeMetrics","stage stall elapsed="+android.os.SystemClock.elapsedRealtime());
        }
        // Idle after an error shows the error text instead of a spinner that never ends
        if(loading!=null)loading.setVisibility(state==Player.STATE_READY||state==Player.STATE_ENDED||state==Player.STATE_IDLE&&player!=null&&player.getPlayerError()!=null?View.GONE:View.VISIBLE);
        if(state==Player.STATE_BUFFERING&&getContext()!=null) BroadcastFromUI.broadCastLoading(getContext());
        if(state==Player.STATE_ENDED&&remoteVideo&&getView()!=null)getView().post(()->{if(player!=null&&player.getPlaybackState()==Player.STATE_ENDED)listener.onNativeSkip(true);});
    }
    @Override public void onPlayerError(PlaybackException error) {
        android.util.Log.w("TubePlayer","Playback failed: "+error.getErrorCodeName());
        com.sya.tube.diag.Diag.log("player","error "+error.getErrorCodeName()+" "+(error.getCause()==null?"":error.getCause().getMessage()));
        if(remoteVideo){
            com.sya.tube.player.YoutubeStreams.invalidate(requireArguments().getString("youtube_url"),requireArguments().getInt("quality",720));
            listener.onNativeVideoFailure();return;
        }
        // A file the phone can't decode is asked from the server converted, once
        Bundle item=currentMedia();
        if(item!=null&&item.getString("fallback")!=null&&player!=null&&fellBack.add(player.getCurrentMediaItemIndex())){
            int index=player.getCurrentMediaItemIndex();long position=player.getCurrentPosition();
            com.sya.tube.diag.Diag.log("player","direct play failed, converting on the server");
            item.putString("method","Transcode");
            player.removeMediaItem(index);
            player.addMediaSource(index,mediaSources().createMediaSource(mediaItem(item,item.getString("fallback"))));
            player.seekTo(index,position);player.prepare();player.play();
            return;
        }
        if(loading!=null)loading.setVisibility(View.GONE);
        title.setText("Couldn't play this video"); album.setText(mediaMode?"The server or file isn't available":"Try another file");
    }
    // Switching away from Tube in the car pauses, coming back continues instead of leaving a paused black screen
    private boolean resumeOnReturn;
    @Override public void onPause() {
        if(keepPlayingWhenHidden&&(remoteVideo||mediaMode)&&player!=null&&player.getPlayWhenReady()){playingHidden=true;setVideoDecoding(false);super.onPause();return;}
        resumeOnReturn=player!=null&&player.getPlayWhenReady(); pause(); super.onPause();
    }
    @Override public void onResume() {
        super.onResume();
        if(playingHidden){playingHidden=false;setVideoDecoding(true);}
        if(resumeOnReturn&&player!=null)player.play(); resumeOnReturn=false;
    }
    @Override public void onSaveInstanceState(Bundle saved) {
        super.onSaveInstanceState(saved);saved.putLong("position",position());saved.putInt("index",player==null?resumeIndex:player.getCurrentMediaItemIndex());saved.putBoolean("playing",playing());
    }
    @Override public void onDestroyView() {
        if(resolution!=null){resolution.cancel(true);resolution=null;}
        if(receiver!=null) { requireContext().unregisterReceiver(receiver); receiver=null; }
        if(mediaMode&&player!=null)reportMediaStop(player.getPlaybackState()==Player.STATE_ENDED);
        if(player!=null) { resumePosition=player.getCurrentPosition(); resumeIndex=player.getCurrentMediaItemIndex(); playWhenReady=player.getPlayWhenReady(); player.release(); player=null; }
        if(activeNative.get()==this) { activeNative.clear(); com.thekirankumar.youtubeauto2.webview.PlaybackOwner.nativeClosed(); }
        playerView=null; loading=null; super.onDestroyView();
    }
}
