// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.RemoteException;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.thekirankumar.youtubeauto2.activity.MainCarActivity;
import com.thekirankumar.youtubeauto2.R;

/* JADX INFO: loaded from: classes.dex */
public class MediaNotificationManager extends BroadcastReceiver {
    public static final String ACTION_NEXT = "com.sya.tube.next";
    public static final String ACTION_PAUSE = "com.sya.tube.pauseVideo";
    public static final String ACTION_PLAY = "com.sya.tube.playVideo";
    public static final String ACTION_PREV = "com.sya.tube.prev";
    public static final String ACTION_STOP = "com.sya.tube.stop";
    public static final String ACTION_STOP_CASTING = "com.sya.tube.stop_cast";
    private static final String CHANNEL_ID = "com.sya.tube.MUSIC_CHANNEL_ID";
    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;
    private static final String TAG = MediaNotificationManager.class.getName();
    private final Context mContext;
    private MediaControllerCompat mController;
    private MediaMetadataCompat mMetadata;
    private final PendingIntent mNextIntent;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPauseIntent;
    private final PendingIntent mPlayIntent;
    private PlaybackStateCompat mPlaybackState;
    private final PendingIntent mPreviousIntent;
    private MediaSessionCompat.Token mSessionToken;
    private final PendingIntent mStopCastIntent;
    private final PendingIntent mStopIntent;
    private MediaControllerCompat.TransportControls mTransportControls;
    private boolean mStarted = false;
    private final MediaControllerCompat.Callback mCb = new MediaControllerCompat.Callback() {
        @Override // android.support.v4.media.session.MediaControllerCompat.Callback
        public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            MediaNotificationManager.this.mPlaybackState = playbackStateCompat;
            Log.d(MediaNotificationManager.TAG, "Received new playback state" + playbackStateCompat);
            if (playbackStateCompat.getState() != 1 && playbackStateCompat.getState() != 0) {
                Notification notificationCreateNotification = MediaNotificationManager.this.createNotification();
                if (notificationCreateNotification != null) {
                    MediaNotificationManager.this.postNotification(notificationCreateNotification);
                    return;
                }
                return;
            }
            MediaNotificationManager.this.stopNotification();
        }

        @Override // android.support.v4.media.session.MediaControllerCompat.Callback
        public void onMetadataChanged(MediaMetadataCompat mediaMetadataCompat) {
            MediaNotificationManager.this.mMetadata = mediaMetadataCompat;
            Log.d(MediaNotificationManager.TAG, "Received new metadata " + mediaMetadataCompat);
            Notification notificationCreateNotification = MediaNotificationManager.this.createNotification();
            if (notificationCreateNotification != null) {
                MediaNotificationManager.this.postNotification(notificationCreateNotification);
            }
        }

        @Override // android.support.v4.media.session.MediaControllerCompat.Callback
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            Log.d(MediaNotificationManager.TAG, "Session was destroyed, resetting to the new session token");
            try {
                MediaNotificationManager.this.updateSessionToken();
            } catch (RemoteException e) {
                Log.e(MediaNotificationManager.TAG, e + "could not connect media controller");
            }
        }
    };

    public MediaNotificationManager(Context context, MediaSessionCompat.Token token) throws RemoteException {
        this.mContext = context;
        this.mSessionToken = token;
        updateSessionToken();
        this.mNotificationManager = NotificationManagerCompat.from(context);
        String packageName = context.getPackageName();
        this.mPauseIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_PAUSE).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mPlayIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_PLAY).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mPreviousIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_PREV).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mNextIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_NEXT).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mStopIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_STOP).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mStopCastIntent = PendingIntent.getBroadcast(context, 100, new Intent(ACTION_STOP_CASTING).setPackage(packageName), 268435456 | PendingIntent.FLAG_IMMUTABLE);
        this.mNotificationManager.cancelAll();
    }

    public void startNotification(Service service) {
        if (this.mStarted) {
            return;
        }
        this.mMetadata = this.mController.getMetadata();
        this.mPlaybackState = this.mController.getPlaybackState();
        Notification notificationCreateNotification = createNotification();
        if (notificationCreateNotification != null) {
            this.mController.registerCallback(this.mCb);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_NEXT);
            intentFilter.addAction(ACTION_PAUSE);
            intentFilter.addAction(ACTION_PLAY);
            intentFilter.addAction(ACTION_PREV);
            intentFilter.addAction(ACTION_STOP_CASTING);
            androidx.core.content.ContextCompat.registerReceiver(this.mContext, this, intentFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            service.startForeground(NOTIFICATION_ID, notificationCreateNotification);
            this.mStarted = true;
        }
    }

    public void stopNotification() {
        if (this.mStarted) {
            this.mStarted = false;
            this.mController.unregisterCallback(this.mCb);
            try {
                this.mNotificationManager.cancel(NOTIFICATION_ID);
                this.mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException unused) {
            }
            this.mNotificationManager.cancelAll();
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:17:0x0037  */
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        byte b;
        String action = intent.getAction();
        switch (action) {
            case "com.sya.tube.next":
                b = 2;
                break;
            case "com.sya.tube.prev":
                b = 3;
                break;
            case "com.sya.tube.pauseVideo":
                b = 0;
                break;
            case "com.sya.tube.playVideo":
                b = 1;
                break;
            default:
                b = -1;
                break;
        }
        if (b == 0) {
            this.mTransportControls.pause();
            return;
        }
        if (b == 1) {
            this.mTransportControls.play();
            return;
        }
        if (b == 2) {
            this.mTransportControls.skipToNext();
            return;
        }
        if (b == 3) {
            this.mTransportControls.skipToPrevious();
            return;
        }
        Log.w(TAG, "Unknown intent ignored. Action=" + action);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateSessionToken() throws RemoteException {
        MediaSessionCompat.Token token = this.mSessionToken;
        MediaSessionCompat.Token token2 = this.mSessionToken;
        if (token2 == null || token2.equals(null)) {
            return;
        }
        MediaControllerCompat mediaControllerCompat = this.mController;
        if (mediaControllerCompat != null) {
            mediaControllerCompat.unregisterCallback(this.mCb);
        }
        MediaSessionCompat.Token token3 = this.mSessionToken;
        if (token3 != null) {
            MediaControllerCompat mediaControllerCompat2 = new MediaControllerCompat(this.mContext, token3);
            this.mController = mediaControllerCompat2;
            this.mTransportControls = mediaControllerCompat2.getTransportControls();
            if (this.mStarted) {
                this.mController.registerCallback(this.mCb);
            }
        }
    }

    private PendingIntent createContentIntent(MediaDescriptionCompat mediaDescriptionCompat) {
        Intent intent = new Intent(this.mContext, (Class<?>) MainCarActivity.class);
        intent.setFlags(536870912);
        return PendingIntent.getActivity(this.mContext, 100, intent, 268435456 | PendingIntent.FLAG_IMMUTABLE);
    }

    private void postNotification(Notification notification) {
        if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(mContext,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        mNotificationManager.notify(NOTIFICATION_ID,notification);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Notification createNotification() {
        Log.d(TAG, "updateNotificationMetadata. mMetadata=" + this.mMetadata);
        if (this.mMetadata == null || this.mPlaybackState == null || SettingsUtils.isDisabledNotifications(this.mContext)) {
            return null;
        }
        MediaDescriptionCompat description = this.mMetadata.getDescription();
        if (description.getIconUri() != null) {
            description.getIconUri().toString();
        }
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel();
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.mContext, CHANNEL_ID);
        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(addActions(builder)).setShowCancelButton(true).setCancelButtonIntent(this.mStopIntent).setMediaSession(this.mSessionToken)).setDeleteIntent(this.mStopIntent).setSmallIcon(R.mipmap.ic_launcher).setVisibility(1).setOnlyAlertOnce(true).setContentIntent(createContentIntent(description)).setContentTitle(description.getTitle()).setContentText(description.getSubtitle()).setLargeIcon((android.graphics.Bitmap)null);
        setNotificationPlaybackState(builder);
        return builder.build();
    }

    private int addActions(NotificationCompat.Builder builder) {
        int i;
        int i2;
        PendingIntent pendingIntent;
        String str;
        Log.d(TAG, "updatePlayPauseAction");
        if ((this.mPlaybackState.getActions() & 16) != 0) {
            builder.addAction(R.drawable.ic_prev, "Previous", this.mPreviousIntent);
            i = 1;
        } else {
            i = 0;
        }
        if (this.mPlaybackState.getState() == 3) {
            i2 = R.drawable.ic_pause;
            pendingIntent = this.mPauseIntent;
            str = "Pause";
        } else {
            i2 = R.drawable.ic_play;
            pendingIntent = this.mPlayIntent;
            str = "Play";
        }
        builder.addAction(new NotificationCompat.Action(i2, str, pendingIntent));
        if ((this.mPlaybackState.getActions() & 32) != 0) {
            builder.addAction(R.drawable.ic_next, "Next", this.mNextIntent);
        }
        return i;
    }

    private void setNotificationPlaybackState(NotificationCompat.Builder builder) {
        Log.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + this.mPlaybackState);
        PlaybackStateCompat playbackStateCompat = this.mPlaybackState;
        if (playbackStateCompat == null || !this.mStarted) {
            Log.d(TAG, "updateNotificationPlaybackState. cancelling notification!");
            this.mNotificationManager.cancelAll();
        } else {
            builder.setOngoing(playbackStateCompat.getState() == 3);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, this.mContext.getString(R.string.channel_name), 4);
            notificationChannel.setDescription("Plays youtube audio");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.parseColor("#5B3C88"));
            notificationChannel.enableVibration(true);
            ((NotificationManager) this.mContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).createNotificationChannel(notificationChannel);
        }
    }
}
