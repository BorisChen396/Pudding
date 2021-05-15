package com.azuredragon.puddingplayer.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.azuredragon.puddingplayer.FileLoader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

class YouTubeMetadata {
    private Context mContext;

    public YouTubeMetadata(@NonNull Context context) {
        mContext = context;
    }

    String getMetadata(String videoId) throws IOException {
        String link = "https://www.youtube.com/oembed?url=youtu.be/" + videoId;
        FileLoader loader = new FileLoader(mContext);
        boolean cached = loader.checkFileExistance(loader.APPLICATION_CACHE_DIR + "metadata_" + videoId);
        String response;
        if(!cached) {
            response = loader.downloadFile(link, "metadata_" + videoId, true);
        }
        else {
            response = loader.loadFile(loader.APPLICATION_CACHE_DIR + "metadata_" + videoId);
        }
        return response;
    }
}
