package com.sya.tube.player;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import okhttp3.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.stream.*;

/** Public-stream resolution without reading browser credentials or cookies. */
public final class YoutubeStreams {
    public static final String USER_AGENT="Mozilla/5.0";
    public static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    // Stream URLs stay valid for hours, lifetime() still caps each entry at its own expiry
    private static final long CACHE_TTL_MS=20*60_000;
    private static final Map<String,Entry> CACHE=new LinkedHashMap<>(4,.75f,true);
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build();
    static {
        NewPipe.init(new Downloader() {
            @Override public org.schabi.newpipe.extractor.downloader.Response execute(org.schabi.newpipe.extractor.downloader.Request request) throws IOException {
                Request.Builder b=new Request.Builder().url(request.url()).header("User-Agent",USER_AGENT);
                request.headers().forEach((k,values)->{ b.removeHeader(k); for(String value:values)b.addHeader(k,value); });
                byte[] data=request.dataToSend();
                RequestBody body=data==null?null:RequestBody.create(data,null);
                if(body==null&&!request.httpMethod().equals("GET")&&!request.httpMethod().equals("HEAD"))body=RequestBody.create(new byte[0],null);
                b.method(request.httpMethod(),body);
                try(Response r=HTTP.newCall(b.build()).execute()) {
                    return new org.schabi.newpipe.extractor.downloader.Response(r.code(),r.message(),r.headers().toMultimap(),r.body()==null?"":r.body().string(),r.request().url().toString());
                }
            }
        });
    }
    /** Sets up the extractor for code that uses it directly, the first call runs the setup above */
    public static void ready() {}
    public static final class Chapter {
        public final long startMs; public final String title;
        Chapter(long startMs,String title) { this.startMs=startMs;this.title=title; }
    }
    public static final class Captions {
        public final String url,language; public final boolean automatic;
        Captions(String url,String language,boolean automatic) { this.url=url;this.language=language;this.automatic=automatic; }
    }
    public static final class Result {
        public final String title,video,audio; public final int fps,itag,height;
        public final java.util.List<Chapter> chapters; public final java.util.List<Captions> captions;
        Result(String title,VideoStream v,AudioStream a,java.util.List<Chapter> chapters,java.util.List<Captions> captions) {
            this.title=title;video=v.getContent();audio=a.getContent();fps=v.getFps();itag=v.getItag();height=v.getHeight();this.chapters=chapters;this.captions=captions;
        }
        /** The track for a language YouTube's own player showed, a written track is preferred over an automatic one */
        public Captions captionsFor(String language,boolean automatic) {
            if(language==null)return null;
            Captions loose=null;
            for(Captions c:captions){
                if(!c.language.equalsIgnoreCase(language)&&!c.language.split("-")[0].equalsIgnoreCase(language.split("-")[0]))continue;
                if(c.automatic==automatic)return c;
                if(loose==null)loose=c;
            }
            return loose;
        }
    }

    /** Chapters for a video, from the lookup the native player already makes, empty when the video has none or it fails */
    public static java.util.List<Chapter> chapters(String url,int quality) {
        String id=key(url,0).substring(2);
        synchronized(CACHE) { for(var entry:CACHE.entrySet()) if(entry.getKey().endsWith("\n"+id)) return entry.getValue().result.chapters; }
        try { return resolve(url,quality).chapters; } catch(Exception error) { return java.util.Collections.emptyList(); }
    }
    private static final class Entry {
        final Result result;
        final long expires;
        Entry(Result result,long lifetimeMs) {
            this.result=result;
            expires=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(lifetimeMs);
        }
    }
    private static final Map<String,Future<Result>> INFLIGHT=new java.util.HashMap<>();
    // Most head units project at 30 frames per second, Android Auto reports the real rate once the car screen starts
    public static volatile int carFrameRate=30;
    private static final ExecutorService PREFETCH=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"TubePrefetch");t.setPriority(Thread.MIN_PRIORITY+1);return t;});
    // Keyed by video id, watch URLs for the same video differ in timestamps and tracking parameters
    private static String key(String url,int quality) {
        android.net.Uri uri=android.net.Uri.parse(url);
        String id=uri.getQueryParameter("v");
        if(id==null){String path=uri.getPath();if(path!=null&&path.startsWith("/shorts/"))id=path.substring(8);}
        return quality+"\n"+(id==null?url:id);
    }
    public static void invalidate(String url,int quality) {
        synchronized(CACHE) { CACHE.remove(key(url,quality)); INFLIGHT.remove(key(url,quality)); }
    }
    /** Starts resolving in the background as soon as a video page opens, so native playback can begin without waiting for extraction */
    public static void prefetch(String url,int quality) {
        String cacheKey=key(url,quality);
        synchronized(CACHE) {
            Entry cached=CACHE.get(cacheKey);
            if((cached!=null&&cached.expires-System.nanoTime()>0)||INFLIGHT.containsKey(cacheKey))return;
            debug("prefetch start");
            // The task's cleanup locks CACHE, so it cannot run before self is assigned below
            @SuppressWarnings("unchecked") Future<Result>[] self=new Future[1];
            self[0]=PREFETCH.submit(()->{
                try {
                    Result result=resolveCached(url,quality);
                    warm(result.video);warm(result.audio);
                    return result;
                } finally { synchronized(CACHE){INFLIGHT.remove(cacheKey,self[0]);} }
            });
            INFLIGHT.put(cacheKey,self[0]);
        }
    }
    private static long lifetime(String stream) {
        String expire=android.net.Uri.parse(stream).getQueryParameter("expire");
        if(expire==null)return CACHE_TTL_MS;
        try {
            long seconds=Long.parseLong(expire);
            if(seconds<=0)return 0;
            long remaining=TimeUnit.SECONDS.toMillis(seconds)-System.currentTimeMillis()-30_000;
            return Math.max(0,Math.min(CACHE_TTL_MS,remaining));
        } catch(NumberFormatException ignored) { return 0; }
    }
    public static Result resolve(String url,int quality) throws Exception {
        android.os.Trace.beginSection("Tube.ResolveStreams");
        try {
            String cacheKey=key(url,quality);
            Future<Result> pending;
            java.util.concurrent.FutureTask<Result> own=null;
            synchronized(CACHE) {
                pending=INFLIGHT.get(cacheKey);
                // Published so a prefetch that starts meanwhile waits for this extraction instead of running a second one
                if(pending==null){own=new java.util.concurrent.FutureTask<>(()->resolveCached(url,quality));INFLIGHT.put(cacheKey,own);}
            }
            if(own!=null) {
                try { own.run(); return own.get(); }
                catch(ExecutionException error) { Throwable cause=error.getCause(); if(cause instanceof Exception)throw (Exception)cause; throw error; }
                finally { synchronized(CACHE){INFLIGHT.remove(cacheKey,own);} }
            }
            long waitStart=System.nanoTime();
            try { Result r=pending.get(); debug("resolve waited for prefetch "+(System.nanoTime()-waitStart)/1_000_000+"ms"); return r; }
            catch(ExecutionException ignored) { /* a failed prefetch falls through to a fresh attempt */ }
            return resolveCached(url,quality);
        }
        finally { android.os.Trace.endSection(); }
    }
    private static Result resolveCached(String url,int quality) throws Exception {
        String cacheKey=key(url,quality);
        synchronized(CACHE) {
            long now=System.nanoTime();
            CACHE.entrySet().removeIf(entry->now-entry.getValue().expires>=0);
            Entry cached=CACHE.get(cacheKey);
            if(cached!=null) {
                debug("resolve cache hit");
                android.os.Trace.beginSection("Tube.StreamCacheHit");
                android.os.Trace.endSection();
                return cached.result;
            }
        }
        Result result;
        android.os.Trace.beginSection("Tube.ExtractStreams");
        long extractStart=System.nanoTime();
        try { result=extract(url,quality); debug("extracted in "+(System.nanoTime()-extractStart)/1_000_000+"ms on "+Thread.currentThread().getName()); }
        finally { android.os.Trace.endSection(); }
        long lifetimeMs=Math.min(lifetime(result.video),lifetime(result.audio));
        if(lifetimeMs>0) synchronized(CACHE) {
            CACHE.put(cacheKey,new Entry(result,lifetimeMs));
            while(CACHE.size()>4)CACHE.remove(CACHE.keySet().iterator().next());
        }
        return result;
    }
    private static Result extract(String url,int quality) throws Exception {
        StreamExtractor ex=ServiceList.YouTube.getStreamExtractor(url); ex.fetchPage();
        // Frames faster than the car's projection rate are thrown away unevenly, which reads as judder, so prefer a rate the car shows in full
        int car=carFrameRate;
        VideoStream video=ex.getVideoOnlyStreams().stream().filter(v->v.isUrl()&&v.getHeight()<=quality&&v.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP)
            .max(java.util.Comparator.comparingInt((VideoStream v)->v.getHeight()*1_000_000+(v.getFps()<=car?100+v.getFps():100-v.getFps())*10+(v.getCodec()!=null&&v.getCodec().startsWith("avc1")?1:0))).orElseThrow(()->new IOException("No public stream at selected quality"));
        debug("picked itag="+video.getItag()+" fps="+video.getFps()+" car="+car);
        // Videos with dubbed tracks list every language, the original track comes first, then a dub in the phone's language
        String language=java.util.Locale.getDefault().getLanguage();
        AudioStream audio=ex.getAudioStreams().stream().filter(a->a.isUrl()&&a.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP)
            .max(java.util.Comparator.comparingInt((AudioStream a)->trackRank(a,language)).thenComparingInt(a->a.getFormat()==MediaFormat.M4A?10000+a.getAverageBitrate():a.getAverageBitrate())).orElseThrow(()->new IOException("No audio stream"));
        debug("audio track="+audio.getAudioTrackType()+" locale="+audio.getAudioLocale()+" name="+audio.getAudioTrackName());
        // Chapters and captions come with the same page, a failure in either still leaves the video playable
        java.util.List<Chapter> chapters=new java.util.ArrayList<>();
        try { for(var segment:ex.getStreamSegments()) chapters.add(new Chapter(segment.getStartTimeSeconds()*1000L,segment.getTitle())); }
        catch(Exception error) { debug("chapters unavailable "+error.getClass().getSimpleName()); }
        java.util.List<Captions> captions=new java.util.ArrayList<>();
        try { for(var track:ex.getSubtitles(MediaFormat.VTT)) if(track.isUrl()&&track.getLanguageTag()!=null) captions.add(new Captions(track.getContent(),track.getLanguageTag(),track.isAutoGenerated())); }
        catch(Exception error) { debug("captions unavailable "+error.getClass().getSimpleName()); }
        debug("chapters="+chapters.size()+" captions="+captions.size());
        return new Result(ex.getName(),video,audio,chapters,captions);
    }
    private static int trackRank(AudioStream audio,String language) {
        org.schabi.newpipe.extractor.stream.AudioTrackType type=audio.getAudioTrackType();
        // Videos with a single audio track carry no type at all
        if(type==null||type==org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL)return 3;
        java.util.Locale locale=audio.getAudioLocale();
        if(type==org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED&&locale!=null&&language.equals(locale.getLanguage()))return 2;
        return type==org.schabi.newpipe.extractor.stream.AudioTrackType.DESCRIPTIVE?0:1;
    }
    /** A 1 KB read through the player's own transport leaves a pooled TLS connection, so native playback skips connection setup */
    private static void warm(String stream) {
        long start=System.nanoTime();
        var source=YoutubeDataSource.factory().createDataSource();
        try {
            source.open(new com.google.android.exoplayer2.upstream.DataSpec.Builder().setUri(android.net.Uri.parse(stream)).setPosition(0).setLength(1024).build());
            byte[] buffer=new byte[1024];
            while(source.read(buffer,0,buffer.length)>0) {}
            debug("warmed connection in "+(System.nanoTime()-start)/1_000_000+"ms");
        } catch(Exception error) {
            debug("warm failed "+error.getClass().getSimpleName());
        } finally {
            try { source.close(); } catch(Exception ignored) {}
        }
    }
    static void debug(String message) {
        com.sya.tube.diag.Diag.log("streams",message);
        if(com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics","stage "+message.replace(' ','_')+" elapsed="+android.os.SystemClock.elapsedRealtime());
    }
    private YoutubeStreams() {}
}
