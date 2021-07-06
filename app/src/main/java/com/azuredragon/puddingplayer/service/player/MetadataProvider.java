package com.azuredragon.puddingplayer.service.player;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class MetadataProvider implements MediaSessionConnector.MediaMetadataProvider {
    private Context mContext;
    private MediaSessionConnector mConnector;

    public MetadataProvider(Context context, MediaSessionConnector connector) {
        mContext = context;
        mConnector = connector;
    }

    @Override
    public MediaMetadataCompat getMetadata(Player player) {
        if(player.getCurrentMediaItem() == null ||
           player.getCurrentMediaItem().playbackProperties == null ||
           player.getCurrentMediaItem().playbackProperties.uri.getQueryParameter("uri") == null)
            return new MediaMetadataCompat.Builder().build();
        MediaItem.PlaybackProperties properties = player.getCurrentMediaItem().playbackProperties;
        String type = properties.uri.getQueryParameter("type");
        Uri uri = Uri.parse(properties.uri.getQueryParameter("uri"));
        switch (type) {
            case PlaybackService.TYPE_YOUTUBE:
                String videoId = uri.getQueryParameter("videoId");
                return getMetadataByVideoId(videoId)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration())
                        .build();
            case PlaybackService.TYPE_LOCAL:
                return getMetadataByLocalFile(uri)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration())
                        .build();
            default:
                return new MediaMetadataCompat.Builder().build();
        }
    }

    MediaMetadataCompat.Builder getMetadataByLocalFile(@NonNull Uri uri) {
        String filename = DocumentFile.fromSingleUri(mContext, uri).getName();
        filename = Utils.getFilenameWithoutExtension(filename, mContext);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mContext, uri);
        } catch (Exception e) {
            Log.e("MetadataProvider", "File not supported by retriever.");
            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, filename);
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mContext.getString(R.string.metadata_unknown_author));
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
            return builder;
        }
        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
        byte[] artwork = retriever.getEmbeddedPicture();

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title == null ? filename : title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                artist == null ? (author == null ? mContext.getString(R.string.metadata_unknown_author) : author) : artist);
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                artwork == null ? null : BitmapFactory.decodeByteArray(artwork, 0, artwork.length));
        return builder;
    }

    MediaMetadataCompat.Builder getMetadataByVideoId(String videoId) {
        FileLoader loader = new FileLoader(mContext);
        Uri cachedMetadata = Uri.parse(loader.loadFile(loader.APPLICATION_DATA_DIR + "metadata"));
        if(videoId == null) return new MediaMetadataCompat.Builder();
        if(videoId.equals(cachedMetadata.getQueryParameter("videoId"))) {
            byte[] artwork = loader.loadFileToByteArray(loader.APPLICATION_DATA_DIR + "thumbnail.jpg");
            return new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, cachedMetadata.getQueryParameter("title"))
                    .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, cachedMetadata.getQueryParameter("author"))
                    .putText(MediaMetadataCompat.METADATA_KEY_ART_URI, cachedMetadata.getQueryParameter("thumbnail"))
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeByteArray(artwork, 0, artwork.length));
        }
        else {
            new Thread(() -> {
                try {
                    NetworkHandler downloadMetadata = new NetworkHandler("https://www.youtube.com/oembed?url=youtu.be/" + videoId, mContext);
                    Bundle response = downloadMetadata.getResponse();
                    if(!response.getBoolean("ok") || response.getString("response") == null) {
                        Log.e("MetadataProvider",
                                String.format(mContext.getString(R.string.error_http_error), response.getInt("statusCode")));
                        return;
                    }
                    JSONObject newMetadata = new JSONObject(response.getString("response"));
                    Uri saveMetadata = new Uri.Builder()
                            .appendQueryParameter("videoId", videoId)
                            .appendQueryParameter("title", newMetadata.optString("title"))
                            .appendQueryParameter("author", newMetadata.optString("author_name"))
                            .appendQueryParameter("thumbnail", newMetadata.optString("thumbnail_url"))
                            .build();
                    loader.downloadFile(newMetadata.optString("thumbnail_url"), "thumbnail.jpg");
                    loader.saveFile(saveMetadata.toString(), "metadata", true);
                    new Handler(Looper.getMainLooper()).post(() ->
                            mConnector.invalidateMediaSessionMetadata());
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }).start();
            return new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, videoId)
                    .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube");
        }
    }
}
