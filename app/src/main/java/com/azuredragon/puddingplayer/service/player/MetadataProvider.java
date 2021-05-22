package com.azuredragon.puddingplayer.service.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.R;
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
        if(player.getCurrentMediaItem() == null || player.getCurrentMediaItem().playbackProperties == null)
            return new MediaMetadataCompat.Builder().build();
        String videoId = player.getCurrentMediaItem().playbackProperties.uri.getQueryParameter("video_id");
        FileLoader loader = new FileLoader(mContext);
        Uri cachedMetadata = Uri.parse(loader.loadFile(loader.APPLICATION_DATA_DIR + "metadata"));
        if(videoId == null) return new MediaMetadataCompat.Builder().build();
        if(videoId.equals(cachedMetadata.getQueryParameter("videoId"))) {
            return new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, cachedMetadata.getQueryParameter("title"))
                    .putText(MediaMetadataCompat.METADATA_KEY_AUTHOR, cachedMetadata.getQueryParameter("author"))
                    .putText(MediaMetadataCompat.METADATA_KEY_ART_URI, cachedMetadata.getQueryParameter("thumbnail"))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getContentDuration())
                    .build();
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
                    .putText(MediaMetadataCompat.METADATA_KEY_AUTHOR, "YouTube")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getContentDuration())
                    .build();
        }
    }
}
