package com.azuredragon.puddingplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.preference.PreferenceManager;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.service.player.MetadataProvider;
import com.azuredragon.puddingplayer.service.player.URLUpdatingDataSource;
import com.azuredragon.puddingplayer.ui.MainActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends MediaBrowserServiceCompat {
    private PuddingSession mSession;
    private MediaSessionConnector connector;
    private PlayerNotificationManager notifyManager;
    private String TAG = "PlaybackService";
    private SimpleExoPlayer player;
    private SharedPreferences settings;
    private List<MediaDescriptionCompat> descriptions = new ArrayList<>();

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("", new Bundle());
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {}

    @Override
    public void onCreate() {
        super.onCreate();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(settingsListener);

        notifyManager = getNotificationManager();

        mSession = new PuddingSession(this, notifyManager);
        mSession.setQueue(new ArrayList<>());
        mSession.setActive(true);
        setSessionToken(mSession.getSessionToken());

        connector = new MediaSessionConnector(mSession);
        connector.setMediaMetadataProvider(new MetadataProvider(PlaybackService.this, connector));
        connector.setQueueNavigator(new TimelineQueueNavigator(mSession, 1024) {
            @Override
            public void onSkipToQueueItem(final Player mPlayer, ControlDispatcher controlDispatcher, long id) {
                super.onSkipToQueueItem(player, controlDispatcher, id);
                player.seekToDefaultPosition((int) id);
                player.prepare();
                notifyManager.setPlayer(player);
                notifyManager.setMediaSessionToken(mSession.getSessionToken());
            }

            @Override
            public MediaDescriptionCompat getMediaDescription(Player mPlayer, int windowIndex) {
                MediaDescriptionCompat des;
                try {
                    des = descriptions.get(windowIndex);
                }
                catch (IndexOutOfBoundsException e) {
                    des = null;
                }
                return des;
            }

            @Override
            public long getSupportedQueueNavigatorActions(Player mPlayer) {
                return super.getSupportedQueueNavigatorActions(player) |
                        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
            }
        });
        connector.setQueueEditor(new MediaSessionConnector.QueueEditor() {
            @Override
            public void onAddQueueItem(Player mPlayer, MediaDescriptionCompat description) {
                if(description.getExtras() == null) return;
                this.onAddQueueItem(player, description, player.getMediaItemCount());
            }

            @Override
            public void onAddQueueItem(Player mPlayer, MediaDescriptionCompat description, int index) {
                if(description.getExtras() == null) return;
                if(mSession.getController().getQueue().size() >= 1024) {
                    Toast.makeText(PlaybackService.this,
                            "The maximum of the playlist items is 1024.", Toast.LENGTH_LONG).show();
                    return;
                }
                String videoId = description.getExtras().getString("videoId");
                player.addMediaItem(new MediaItem.Builder()
                                .setUri(new Uri.Builder().appendQueryParameter("video_id", videoId).build())
                                .build());
                descriptions.add(index, description);
            }

            @Override
            public void onRemoveQueueItem(Player mPlayer, MediaDescriptionCompat description) {}

            @Override
            public boolean onCommand(Player mPlayer, ControlDispatcher controlDispatcher,
                                     String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
                switch (command) {
                    default:
                        return false;
                    case TimelineQueueEditor.COMMAND_MOVE_QUEUE_ITEM:
                        int from = extras.getInt(TimelineQueueEditor.EXTRA_FROM_INDEX);
                        int to = extras.getInt(TimelineQueueEditor.EXTRA_TO_INDEX);
                        player.moveMediaItem(from, to);
                        MediaDescriptionCompat des = descriptions.get(to);
                        descriptions.set(to, descriptions.get(from));
                        descriptions.set(from, des);
                        return true;
                }
            }
        });
        new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetShuffleModeEnabled(Player player, boolean shuffleModeEnabled) {
                return super.dispatchSetShuffleModeEnabled(player, shuffleModeEnabled);
            }
        };
        player = createPlayer();
        connector.setPlayer(player);
    }

    SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (sharedPreferences, key) -> {
        if(player == null) return;
        if(key.equals("handleAudioFocus"))
            player.setAudioAttributes(player.getAudioAttributes(), sharedPreferences.getBoolean(key, true));
        if(key.equals("handleAudioBecomingNoisy"))
            player.setHandleAudioBecomingNoisy(sharedPreferences.getBoolean(key, true));
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(notifyManager != null) notifyManager.setPlayer(null);
        if(mSession.getController().getPlaybackState().getState() != PlaybackStateCompat.STATE_NONE)
            mSession.getController().getTransportControls().stop();

        settings.unregisterOnSharedPreferenceChangeListener(settingsListener);
        mSession.setActive(false);
        Log.i("", "Service stopped.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    MediaDescriptionCompat getDescriptionByQueueId(long queueId) {
        return descriptions.get((int) queueId);
    }

    SimpleExoPlayer createPlayer() {
        SimpleExoPlayer player = new SimpleExoPlayer.Builder(PlaybackService.this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(C.CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(), settings.getBoolean("handleAudioFocus", true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(
                        new URLUpdatingDataSource.Factory(
                                PlaybackService.this,
                                new DefaultDataSourceFactory(PlaybackService.this)
                        )))
                .setHandleAudioBecomingNoisy(settings.getBoolean("handleAudioBecomingNoisy", true))
                .build();
        player.setPlayWhenReady(true);
        return player;
    }



    int MEDIA_NOTIFICATION_ID = "PUDDING_IS_GOOD".length();
    String channelId = "playerNotification";
    PlayerNotificationManager getNotificationManager() {
        return PlayerNotificationManager.createWithNotificationChannel(
                this,
                channelId,
                R.string.notifiyChannel,
                R.string.notifyDes,
                MEDIA_NOTIFICATION_ID,
                new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @Override
                    public CharSequence getCurrentContentTitle(Player mPlayer) {
                        return mSession.getController().getMetadata().getDescription().getTitle();
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(Player mPlayer) {
                        Intent intent = new Intent(PlaybackService.this, MainActivity.class);
                        return PendingIntent.getActivity(PlaybackService.this, 0, intent, 0);
                    }

                    @Nullable
                    @Override
                    public CharSequence getCurrentContentText(Player mPlayer) {
                        return mSession.getController().getMetadata().getDescription().getSubtitle();
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(Player mPlayer, PlayerNotificationManager.BitmapCallback callback) {
                        return BitmapFactory.decodeFile(
                                new FileLoader(PlaybackService.this).APPLICATION_DATA_DIR + "thumbnail.jpg");
                    }
                },
                new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        stopForeground(true);
                        stopSelf();
                    }

                    @Override
                    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                        ContextCompat.startForegroundService(PlaybackService.this,
                                new Intent(PlaybackService.this, PlaybackService.this.getClass()));
                        if (!ongoing) {
                            stopForeground(false);
                        } else {
                            startForeground(notificationId, notification);
                        }
                    }
                }
        );
    }

    static class PuddingSession extends MediaSessionCompat {
        PlayerNotificationManager mNotificationManager;
        public PuddingSession(@NonNull Context context, PlayerNotificationManager notificationManager) {
            super(context, "PuddingPlayerPlayback");
            mNotificationManager = notificationManager;
        }

        @Override
        public void setMetadata(MediaMetadataCompat metadata) {
            super.setMetadata(metadata);
            mNotificationManager.invalidate();
        }
    }
}
