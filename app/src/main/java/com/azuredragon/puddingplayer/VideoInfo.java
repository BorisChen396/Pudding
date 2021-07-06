package com.azuredragon.puddingplayer;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class VideoInfo {
    private final Context mContext;
    private static String TAG = "VideoInfo";

    public VideoInfo(@NonNull Context context) {
        mContext = context;
    }

    public Bundle get(@NonNull String videoId) {
        Bundle playerApiResponse;
        Bundle result = new Bundle();
        try {
            playerApiResponse = getVideoInfo(videoId);
        } catch (JSONException | IOException e) {
            result.putBoolean("ok", false);
            result.putString("reason", e.getMessage());
            return result;
        }
        if(!playerApiResponse.getBoolean("ok")) {
            String msg = "Error " + playerApiResponse.getInt("statusCode") +
                    ": " + playerApiResponse.getString("statusText");
            result.putBoolean("ok", false);
            result.putString("reason", msg);
            return result;
        }
        boolean streaming;
        String response = playerApiResponse.getString("response");
        String uri;
        try {
            JSONObject playerResponse = new JSONObject(response);
            String status = playerResponse.getJSONObject("playabilityStatus").getString("status");
            if(!"OK".equals(status)) {
                switch (status) {
                    case "LOGIN_REQUIRED":
                        status = mContext.getString(R.string.string_youtube_LOGIN_REQUIRED);
                        break;
                    case "UNPLAYABLE":
                        status = mContext.getString(R.string.string_youtube_UNPLAYABLE);
                        break;
                }
                String msg = status + ": " + playerResponse.getJSONObject("playabilityStatus").getString("reason");
                result.putBoolean("ok", false);
                result.putString("reason", msg);
                return result;
            }
            uri = getUri(playerResponse);
            streaming = playerResponse.getJSONObject("playabilityStatus").has("liveStreamability");
        } catch (JSONException e) {
            result.putBoolean("ok", false);
            result.putString("reason", e.getMessage());
            return result;
        }
        result.putBoolean("ok", true);
        result.putBoolean("stream", streaming);
        result.putString("uri", uri);
        return result;
    }

    private Bundle getVideoInfo(@NonNull String videoId) throws IOException, JSONException {
        String link = "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w&alt=json";
        NetworkHandler handler = new NetworkHandler(link, mContext);
        HttpsURLConnection connection = handler.getHttpsURLConnection();
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("X-YouTube-Client-Version", "16.07.34");
        connection.setRequestProperty("Referer", "https://www.youtube.com/");
        connection.setRequestProperty("Content-Type", "application/json");
        handler.setHttpsURLConnection(connection);
        String hl = String.format("%s-%s", Locale.getDefault().getLanguage(), Locale.getDefault().getCountry());
        JSONObject content = new JSONObject("{\"context\": {\"client\": {\"hl\":\"" + hl +
                "\",\"clientName\": \"ANDROID\", \"clientVersion\": \"16.07.34\"}}}");
        content.put("videoId", videoId);
        return handler.post(content.toString());
    }

    private String getUri(JSONObject playerResponse) throws JSONException {
        boolean lowQuality = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("lowQuality", false);
        String[] urls = new String[2];
        JSONArray array = playerResponse.getJSONObject("streamingData").getJSONArray("adaptiveFormats");
        for(int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if(item.getString("mimeType").contains("audio")) {
                if("AUDIO_QUALITY_LOW".equals(item.getString("audioQuality")))
                    urls[0] = item.getString("url");
                if("AUDIO_QUALITY_MEDIUM".equals(item.getString("audioQuality")))
                    urls[1] = item.getString("url");
            }
        }
        if(lowQuality)
            return urls[0] == null ? urls[1] : urls[0];
        else
            return urls[1] == null ? urls[0] : urls[1];
    }
}