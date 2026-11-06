// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import com.sya.tube.remote.CarRemote;
import com.thekirankumar.youtubeauto2.R;
import com.thekirankumar.youtubeauto2.utils.BroadcastFromPlayer;
import com.thekirankumar.youtubeauto2.utils.SettingsUtils;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MyMediaBrowserService extends MediaBrowserServiceCompat {
    public static final String ACTION_TYPE = "action_type";
    public static final String MEDIA_TITLE = "title";
    public static final String PLAYBACK_STATE = "state";
    public static final String PLAYER_EVENT = "com.sya.tube.player.event";
    public static final String QUERY = "query";
    private static final String TAG = MyMediaBrowserService.class.getName();
    public static final String WEBVIEW_EVENT = "com.sya.tube.webview.event";
    private static final String REWIND = "tube.rewind", FORWARD = "tube.forward", COMMAND = "tube.command";
    private static final String CHANNEL = "car_playback";
    private static final int NOTIFICATION = 7;
    private static MyMediaBrowserService instance;
    private MediaSessionCompat mediaSessionCompat;
    private BroadcastReceiver playbackReceiver;
    private String lastTitle;
    private int lastState = android.media.session.PlaybackState.STATE_STOPPED;
    // What the car plays, kept so the notification is only rebuilt when something visible changes
    private String carTitle;
    private boolean carPlaying, carShown;
    private long carDuration;

    /* JADX INFO: Access modifiers changed from: private */
    public long getAvailableActions() {
        return PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
    }

    @Override // androidx.media.MediaBrowserServiceCompat, android.app.Service
    public void onCreate() {
        super.onCreate();
        MediaSessionCompat mediaSessionCompat = new MediaSessionCompat(this, "youtube");
        this.mediaSessionCompat = mediaSessionCompat;
        mediaSessionCompat.setCallback(new MediaCallBack());
        this.mediaSessionCompat.setFlags(3);
        setSessionToken(this.mediaSessionCompat.getSessionToken());
        instance = this;
        setup();
    }

    private void setup() {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setActions(getAvailableActions());
        builder.setState(1, 0L, 1.0f);
        if (!SettingsUtils.isDisabledNotifications(this)) {
            MediaMetadataCompat.Builder builder2 = new MediaMetadataCompat.Builder();
            builder2.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Open Aux from the Android Auto app list");
            builder2.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Aux provides playback controls only");
            this.mediaSessionCompat.setMetadata(builder2.build());
        }
        this.mediaSessionCompat.setPlaybackState(builder.build());
        playbackReceiver=new BroadcastReceiver() {
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                // While the car screen is up its two second updates carry the position too, these would reset it to zero
                if (CarRemote.car() != null) return;
                String stringExtra;
                if (!SettingsUtils.isDisabledNotifications(context) && (stringExtra = intent.getStringExtra(MyMediaBrowserService.MEDIA_TITLE)) != null && !stringExtra.equals(lastTitle)) {
                    lastTitle = stringExtra;
                    MediaMetadataCompat.Builder builder3 = new MediaMetadataCompat.Builder();
                    builder3.putString(MediaMetadataCompat.METADATA_KEY_TITLE, stringExtra);
                    MyMediaBrowserService.this.mediaSessionCompat.setMetadata(builder3.build());
                }
                PlaybackStateCompat.Builder builder4 = new PlaybackStateCompat.Builder();
                int intExtra = intent.getIntExtra(MyMediaBrowserService.PLAYBACK_STATE, 0);
                if(intExtra == lastState) return;
                lastState = intExtra;
                builder4.setState(intExtra, 0L, 1.0f);
                builder4.setActions(MyMediaBrowserService.this.getAvailableActions());
                MyMediaBrowserService.this.mediaSessionCompat.setPlaybackState(builder4.build());
                if (intExtra == 3) {
                    MyMediaBrowserService.this.mediaSessionCompat.setActive(true);
                } else if (intExtra == 1) {
                    MyMediaBrowserService.this.mediaSessionCompat.setActive(false);
                }
            }
        };
        androidx.core.content.ContextCompat.registerReceiver(this,playbackReceiver,new IntentFilter(WEBVIEW_EVENT),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * The car screen reports what it plays every two seconds, the media session then carries a moving position, a seek bar
     * and 10 second jumps to the phone's notification, its lock screen and the car's own media card, a null title clears them
     */
    public static void publishCar(Context context, String title, boolean playing, long positionMs, long durationMs) {
        MyMediaBrowserService service = instance;
        if (service != null && service.mediaSessionCompat != null) service.showCar(title, playing, positionMs, durationMs);
    }

    private void showCar(String title, boolean playing, long positionMs, long durationMs) {
        if (title == null) {
            if (!carShown) return;
            carShown = false; carTitle = null;
            stopForeground(true);
            NotificationManagerCompat.from(this).cancel(NOTIFICATION);
            mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder().setActions(getAvailableActions()).setState(PlaybackStateCompat.STATE_STOPPED, 0, 0).build());
            return;
        }
        boolean changed = !carShown || !title.equals(carTitle) || durationMs != carDuration || playing != carPlaying;
        if (!title.equals(carTitle) || durationMs != carDuration) {
            MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, title).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Playing on your car");
            if (durationMs > 0) metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
            mediaSessionCompat.setMetadata(metadata.build());
        }
        carTitle = title; carDuration = durationMs; carPlaying = playing; carShown = true;
        mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
            .setActions(getAvailableActions() | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_REWIND | PlaybackStateCompat.ACTION_FAST_FORWARD)
            .addCustomAction(new PlaybackStateCompat.CustomAction.Builder(REWIND, "Back 10 seconds", R.drawable.tube_ic_replay).build())
            .addCustomAction(new PlaybackStateCompat.CustomAction.Builder(FORWARD, "Forward 10 seconds", R.drawable.tube_ic_forward).build())
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, positionMs, playing ? 1f : 0f)
            .build());
        mediaSessionCompat.setActive(true);
        if (changed && !SettingsUtils.isDisabledNotifications(this)) postNotification(title, playing);
    }

    private void postNotification(String title, boolean playing) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Playing on the car", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, com.thekirankumar.youtubeauto2.activity.MainPhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_aux_notification)
            .setContentTitle(title)
            .setContentText("Playing on your car")
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .addAction(R.drawable.tube_ic_replay, "Back 10 seconds", command(REWIND, 1))
            .addAction(playing ? R.drawable.ic_video_pause : R.drawable.ic_video_play, playing ? "Pause" : "Play", command(playing ? "pause" : "play", 2))
            .addAction(R.drawable.tube_ic_forward, "Forward 10 seconds", command(FORWARD, 3))
            .addAction(R.drawable.ic_video_next, "Next video", command("next", 4))
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSessionCompat.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        // Playing makes this a media foreground service so the phone does not stop it behind Maps, paused leaves the notification
        // Android 12 and later refuse a foreground start while the app counts as in the background, the notification still shows then
        android.app.Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (playing) try { androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); } catch (RuntimeException refused) { }
            return;
        }
        try {
            if (playing) androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION, notification, Build.VERSION.SDK_INT >= 29 ? android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
            else { stopForeground(false); NotificationManagerCompat.from(this).notify(NOTIFICATION, notification); }
        } catch (RuntimeException refused) {
            try { NotificationManagerCompat.from(this).notify(NOTIFICATION, notification); } catch (RuntimeException ignored) { }
        }
    }

    private PendingIntent command(String name, int code) {
        Intent intent = new Intent(this, MyMediaBrowserService.class).setAction(COMMAND).putExtra("name", name);
        return PendingIntent.getService(this, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Notification buttons, the media session, the car's media card and the steering wheel all end up here */
    private void runCommand(String name) {
        CarRemote.Car car = CarRemote.car();
        if (car == null) {
            if ("play".equals(name)) BroadcastFromPlayer.broadcastPlayClicked(this);
            else if ("pause".equals(name)) BroadcastFromPlayer.broadcastPauseClicked(this);
            else if ("next".equals(name)) BroadcastFromPlayer.broadcastNextClicked(this);
            else if ("previous".equals(name)) BroadcastFromPlayer.broadcastPreviousClicked(this);
            return;
        }
        switch (name) {
            case "play": car.remoteSetPlaying(true); break;
            case "pause": car.remoteSetPlaying(false); break;
            case "next": car.remoteSkip(true); break;
            case "previous": car.remoteSkip(false); break;
            case REWIND: car.remoteSeek(-10_000); break;
            case FORWARD: car.remoteSeek(10_000); break;
        }
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        NotificationManagerCompat.from(this).cancel(NOTIFICATION);
        if(playbackReceiver!=null) { unregisterReceiver(playbackReceiver); playbackReceiver=null; }
        if(mediaSessionCompat!=null) { mediaSessionCompat.release(); mediaSessionCompat=null; }
        super.onDestroy();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent != null && COMMAND.equals(intent.getAction())) { runCommand(intent.getStringExtra("name")); return START_NOT_STICKY; }
        MediaButtonReceiver.handleIntent(this.mediaSessionCompat, intent);
        return super.onStartCommand(intent, i, i2);
    }

    @Override // androidx.media.MediaBrowserServiceCompat
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(String str, int i, Bundle bundle) {
        return new MediaBrowserServiceCompat.BrowserRoot("youtube", null);
    }

    @Override // androidx.media.MediaBrowserServiceCompat
    public void onLoadChildren(String str, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(Collections.emptyList());
    }

    // Only while the car plays, the phone's own player keeps next and previous as they were
    private boolean skipSeeks() {
        return CarRemote.car() != null && com.sya.tube.settings.TubePrefs.skipSeeks(this);
    }

    public class MediaCallBack extends MediaSessionCompat.Callback {
        public MediaCallBack() {
        }

        // The notification's Next video button calls runCommand directly, so it still changes the video
        @Override public void onSkipToPrevious() { runCommand(skipSeeks() ? REWIND : "previous"); }

        @Override public void onSkipToNext() { runCommand(skipSeeks() ? FORWARD : "next"); }

        @Override public void onPlay() { runCommand("play"); }

        @Override public void onPause() { runCommand("pause"); }

        @Override public void onStop() { runCommand("pause"); }

        @Override public void onRewind() { runCommand(REWIND); }

        @Override public void onFastForward() { runCommand(FORWARD); }

        @Override public void onCustomAction(String action, Bundle extras) { runCommand(action); }

        @Override public void onSeekTo(long position) {
            CarRemote.Car car = CarRemote.car();
            if (car != null) car.remoteSeekTo(position);
        }

        @Override // android.support.v4.media.session.MediaSessionCompat.Callback
        public void onPrepare() {
            super.onPrepare();
        }

        @Override // android.support.v4.media.session.MediaSessionCompat.Callback
        public void onPlayFromSearch(String str, Bundle bundle) {
            super.onPlayFromSearch(str, bundle);
            if (str != null) {
                BroadcastFromPlayer.broadcastVoiceSearch(MyMediaBrowserService.this, str);
            }
        }
    }
}
