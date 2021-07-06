package com.azuredragon.puddingplayer.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

import com.azuredragon.puddingplayer.Playlist;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.service.player.LocalDataSource;
import com.azuredragon.puddingplayer.service.player.MetadataProvider;
import com.azuredragon.puddingplayer.service.player.YouTubeDataSource;
import com.azuredragon.puddingplayer.ui.MainActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.xml.sax.SAXException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class PlaybackService extends MediaBrowserServiceCompat {
    private PuddingSession session;
    private MediaSessionConnector connector;
    private String TAG = "PlaybackService";
    private SimpleExoPlayer mPlayer;
    private PlayerNotificationManager notificationManager;
    private SharedPreferences settings;

    private DefaultMediaSourceFactory defaultMediaSourceFactory;
    private DefaultMediaSourceFactory youtubeMediaSourceFactory;

    private boolean isPlayerReleased = true;

    public static final int QUEUE_MAXIMUM = 1024;

    public static final String ACTION_RESET_PLAYER = "reset_player";
    public static final String ACTION_GET_CURRENT_INDEX = "get_index";

    public static final String TYPE_LOCAL = "type_local";
    public static final String TYPE_YOUTUBE = "type_youtube";

    public static final String EXCEPTION_NO_INTERNET = "exception_no_internet";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service started.");
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(new LocalDataSource.Factory(
                this, new DefaultDataSourceFactory(this)));
        youtubeMediaSourceFactory = new DefaultMediaSourceFactory(new YouTubeDataSource.Factory(
                this, new DefaultDataSourceFactory(this)));

        session = new PuddingSession(this, TAG);
        session.setActive(true);
        session.setQueue(new ArrayList<>());

        settings.registerOnSharedPreferenceChangeListener(onSettingsChanged);

        connector = new MediaSessionConnector(session);

        mPlayer = new SimpleExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(settings.getBoolean("handleAudioBecomingNoisy", true))
                .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(C.CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_MEDIA)
                                .build(),
                        settings.getBoolean("handleAudioFocus", true))
                .build();
        mPlayer.setPlayWhenReady(true);
        mPlayer.addListener(playerListener);
        isPlayerReleased = false;

        notificationManager = getNotificationManager();
        notificationManager.setMediaSessionToken(session.getSessionToken());
        notificationManager.setUsePreviousActionInCompactView(true);
        notificationManager.setUseNextActionInCompactView(true);
        session.setNotificationManager(notificationManager);

        connector.setQueueEditor(queueEditor);
        connector.setQueueNavigator(getQueueNavigator());
        connector.setMediaMetadataProvider(new MetadataProvider(this, connector));
        connector.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchStop(Player player, boolean reset) {
                try {
                    Playlist playlist = new Playlist(PlaybackService.this, Playlist.PREVIOUS_PLAYLIST_ID);
                    playlist.clear();
                    for(int i = 0; i < mPlayer.getMediaItemCount(); i++) {
                        MediaItem.PlaybackProperties properties = mPlayer.getMediaItemAt(i).playbackProperties;
                        if(properties == null) continue;
                        playlist.add(properties.uri);
                    }
                    playlist.save();
                } catch (ParserConfigurationException | IOException | TransformerException e) {
                    e.printStackTrace();
                    return !isPlayerReleased && super.dispatchStop(player, false);
                }
                return !isPlayerReleased && super.dispatchStop(player, false);
            }
        });
        connector.setPlayer(mPlayer);

        setSessionToken(session.getSessionToken());

        Playlist playlist = new Playlist(this, Playlist.PREVIOUS_PLAYLIST_ID);
        for(int i = 0; i < playlist.size(); i++) {
            Uri uri = playlist.get(i);
            String type = uri.getQueryParameter("type");
            Uri mUri = Uri.parse(uri.getQueryParameter("uri"));
            addItem(type, mUri, i);
        }
    }

    SharedPreferences.OnSharedPreferenceChangeListener onSettingsChanged = (sharedPreferences, key) -> {
        if(key.equals("handleAudioFocus"))
            mPlayer.setAudioAttributes(mPlayer.getAudioAttributes(), sharedPreferences.getBoolean(key, true));
        if(key.equals("handleAudioBecomingNoisy"))
            mPlayer.setHandleAudioBecomingNoisy(sharedPreferences.getBoolean(key, true));
    };

    Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            switch (error.type) {
                case ExoPlaybackException.TYPE_REMOTE:
                    Log.i(TAG, "TYPE_REMOTE");
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    Log.i(TAG, "TYPE_RENDERER");
                    break;
                case ExoPlaybackException.TYPE_SOURCE:
                    if(error.getSourceException() instanceof UnrecognizedInputFormatException)
                        Toast.makeText(PlaybackService.this, R.string.toast_format_not_supported,
                                Toast.LENGTH_LONG).show();
                    else if(error.getSourceException() instanceof HttpDataSource.InvalidResponseCodeException)
                        Toast.makeText(PlaybackService.this, R.string.toast_decipher_failed,
                                Toast.LENGTH_LONG).show();
                    else if(error.getSourceException().getCause() instanceof FileNotFoundException)
                        Toast.makeText(PlaybackService.this, R.string.toast_load_file_failed,
                                Toast.LENGTH_LONG).show();
                    else if(error.getSourceException().getCause() instanceof SecurityException)
                        Toast.makeText(PlaybackService.this, R.string.toast_permission_denied,
                                Toast.LENGTH_LONG).show();
                    else if(error.getSourceException().getMessage() != null) {
                        if(error.getSourceException().getMessage().contains(EXCEPTION_NO_INTERNET))
                            Toast.makeText(PlaybackService.this, R.string.toast_no_internet,
                                    Toast.LENGTH_LONG).show();
                        else {
                            Toast.makeText(PlaybackService.this, error.getSourceException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    Log.i(TAG, "TYPE_UNEXPECTED");
                    break;
            }
            mPlayer.removeMediaItem(mPlayer.getCurrentWindowIndex());
            mPlayer.prepare();
        }
    };

    void addItem(String type, Uri uri, int index) {
        Uri mUri = new Uri.Builder()
                .appendQueryParameter("uri", uri.toString())
                .appendQueryParameter("type", type)
                .build();
        MediaItem item = MediaItem.fromUri(mUri);
        MediaSource source;
        switch (type) {
            case TYPE_LOCAL:
                source = defaultMediaSourceFactory.createMediaSource(item);
                break;
            case TYPE_YOUTUBE:
                source = youtubeMediaSourceFactory.createMediaSource(item);
                break;
            default:
                Log.e(TAG, "Item has no type.");
                return;
        }
        mPlayer.addMediaSource(index, source);
    }

    MediaSessionConnector.QueueEditor queueEditor = new MediaSessionConnector.QueueEditor() {
        @Override
        public void onAddQueueItem(Player player, MediaDescriptionCompat description) {
            onAddQueueItem(player, description, player.getMediaItemCount());
        }

        @Override
        public void onAddQueueItem(Player player, MediaDescriptionCompat description, int index) {
            if(description.getExtras() == null || description.getMediaUri() == null) return;
            String type = description.getExtras().getString("type");
            if(type == null) return;
            addItem(type, description.getMediaUri(), index);
        }

        @Override
        public void onRemoveQueueItem(Player player, MediaDescriptionCompat description) {
            if(description.getExtras() == null || description.getMediaUri() == null) return;
            String type = description.getExtras().getString("type");
            String uri = description.getMediaUri().toString();
            for(int i = 0; i < player.getMediaItemCount(); i++) {
                MediaItem.PlaybackProperties properties = player.getMediaItemAt(i).playbackProperties;
                if(properties == null) break;
                String _type = properties.uri.getQueryParameter("type");
                String _uri = properties.uri.getQueryParameter("uri");
                if(_type.equals(type) && uri.equals(_uri)) {
                    player.removeMediaItem(i);
                    break;
                }
            }
        }

        @Override
        public boolean onCommand(Player player,
                                 ControlDispatcher controlDispatcher,
                                 String command,
                                 @Nullable Bundle extras,
                                 @Nullable ResultReceiver cb) {
            switch (command) {
                case TimelineQueueEditor.COMMAND_MOVE_QUEUE_ITEM:
                    int from = extras.getInt(TimelineQueueEditor.EXTRA_FROM_INDEX);
                    int to = extras.getInt(TimelineQueueEditor.EXTRA_TO_INDEX);
                    player.moveMediaItem(from, to);
                    connector.invalidateMediaSessionQueue();
                    return true;
                case ACTION_RESET_PLAYER:
                    mPlayer.stop(true);
                    return true;
                case ACTION_GET_CURRENT_INDEX:
                    if(cb != null) cb.send(mPlayer.getCurrentWindowIndex(), null);
                    return true;
            }
            return false;
        }
    };

    MediaSessionConnector.QueueNavigator getQueueNavigator() {
        return new TimelineQueueNavigator(session, QUEUE_MAXIMUM) {
            @Override
            public void onSkipToQueueItem(Player player, ControlDispatcher controlDispatcher, long id) {
                super.onSkipToQueueItem(player, controlDispatcher, id);
                notificationManager.setPlayer(player);
                player.prepare();
            }

            @Override
            public MediaDescriptionCompat getMediaDescription(Player player, int windowIndex) {
                MediaItem.PlaybackProperties properties = player.getMediaItemAt(windowIndex).playbackProperties;
                if(properties == null) return new MediaDescriptionCompat.Builder().build();
                String type = properties.uri.getQueryParameter("type");
                if(type == null) return new MediaDescriptionCompat.Builder().build();
                Bundle extras = new Bundle();
                extras.putString("type", type);
                String uri = properties.uri.getQueryParameter("uri");
                if(uri == null) return new MediaDescriptionCompat.Builder().build();
                MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder();
                builder.setMediaUri(Uri.parse(uri));
                builder.setExtras(extras);
                return builder.build();
            }

            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return super.getSupportedQueueNavigatorActions(player) | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
            }
        };
    }

    PlayerNotificationManager getNotificationManager() {
        String channelId = "PUDDING_IS_DELICIOUS";
        PlayerNotificationManager.Builder builder =
                new PlayerNotificationManager.Builder(this, Math.abs(channelId.hashCode()), channelId,
                        new PlayerNotificationManager.MediaDescriptionAdapter() {
                            @Override
                            public CharSequence getCurrentContentTitle(Player player) {
                                return session.getController().getMetadata().getDescription().getTitle();
                            }

                            @Nullable
                            @Override
                            public PendingIntent createCurrentContentIntent(Player player) {
                                Intent intent = new Intent(PlaybackService.this, MainActivity.class);
                                return PendingIntent.getActivity(PlaybackService.this, 0, intent, 0);
                            }

                            @Nullable
                            @Override
                            public CharSequence getCurrentContentText(Player player) {
                                return session.getController().getMetadata().getDescription().getSubtitle();
                            }

                            @Nullable
                            @Override
                            public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                                MediaMetadataCompat metadata = session.getController().getMetadata();
                                Bitmap bm = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                                return bm == null ? Utils.createDefaultLargeIcon(PlaybackService.this) : bm;
                            }
                        });
        builder.setNotificationListener(new PlayerNotificationManager.NotificationListener() {
            @Override
            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                stopForeground(true);
                stopSelf();
            }

            @Override
            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                if(!ongoing) {
                    stopForeground(false);
                }
                else {
                    Intent intent = new Intent(PlaybackService.this, PlaybackService.class);
                    ContextCompat.startForegroundService(PlaybackService.this, intent);
                    startForeground(notificationId, notification);
                }
            }
        });
        builder.setChannelNameResourceId(R.string.notifiyChannel);
        builder.setChannelDescriptionResourceId(R.string.notifyDes);
        return builder.build();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service stopped.");
        session.getController().getTransportControls().stop();
        session.setActive(false);
        mPlayer.release();
        isPlayerReleased = true;
        super.onDestroy();
    }

    static class PuddingSession extends MediaSessionCompat {
        private PlayerNotificationManager mManager;

        public PuddingSession(@NonNull Context context, @NonNull String tag) {
            super(context, tag);
        }

        void setNotificationManager(PlayerNotificationManager manager) {
            mManager = manager;
        }

        @Override
        public void setMetadata(MediaMetadataCompat metadata) {
            super.setMetadata(metadata);
            if(mManager != null) mManager.invalidate();
        }
    }
}
