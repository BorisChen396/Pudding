package com.azuredragon.puddingplayer.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.R;
import com.eclipsesource.v8.V8;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class VideoInfo {
    private final Context mContext;
    private final String mVideoId;

    private JSONObject playerResponse;
    private static String TAG = "VideoInfo";
    private String decipherUrl = "https://raw.githubusercontent.com/BorisChen396/PuddingPlayer/master/DECIPHER";
    private Uri decipher;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public VideoInfo(@NonNull Context context, @NonNull String videoId) {
        mContext = context;
        mVideoId = videoId;
    }

    public String getInfo() {
        Bundle response;
        try {
            response = new NetworkHandler(decipherUrl, mContext).getResponse();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return "";
        }
        if(!response.getBoolean("ok") || response.getString("response") == null) {
            Log.e(TAG, String.format(mContext.getString(R.string.error_http_error), response.getInt("statusCode")));
            return "";
        }
        decipher = Uri.parse("?" + response.getString("response"));
        String sts = decipher.getQueryParameter("sts");
        String link = new Uri.Builder()
                .scheme("https")
                .authority("www.youtube.com")
                .appendPath("get_video_info")
                .appendQueryParameter("html5", "1")
                .appendQueryParameter("eurl", "https://youtube.googleapis.com")
                .appendQueryParameter("sts", sts)
                .appendQueryParameter("video_id", mVideoId)
                .build().toString();
        return getVideoInfo(link);
    }

    private String getVideoInfo(String link) {
        Bundle response;
        try {
            response = new NetworkHandler(link, mContext).getResponse();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return "";
        }
        if(!response.getBoolean("ok") || response.getString("response") == null) {
            Log.e(TAG, String.format(mContext.getString(R.string.error_http_error), response.getInt("statusCode")));
            return "";
        }
        Uri videoInfo = Uri.parse("?" + response.getString("response"));
        if("fail".equals(videoInfo.getQueryParameter("status"))) {
            Log.e(TAG, videoInfo.getQueryParameter("reason"));
            return "";
        }
        try {
            playerResponse = new JSONObject(videoInfo.getQueryParameter("player_response"));
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return "";
        }
        if(!playerResponse.optJSONObject("playabilityStatus").optString("status").equals("OK")) {
            Log.e(TAG, playerResponse.optJSONObject("playabilityStatus").optString("reason"));
            return "";
        }
        return getAudioLink(playerResponse.optJSONObject("streamingData").optJSONArray("adaptiveFormats"));
    }

    private String getAudioLink(@Nullable JSONArray adaptiveFormats) {
        final JSONObject audios = new JSONObject();
        if(adaptiveFormats == null) {
            Log.e(TAG, "Object should not be null.");
            return "";
        }

        try {
            for(int i = 0; i < adaptiveFormats.length(); i++) {
                JSONObject item = adaptiveFormats.optJSONObject(i);
                if(item.has("audioQuality")) {
                    String quality = item.optString("audioQuality");
                    if(quality.equals("AUDIO_QUALITY_MEDIUM"))
                        audios.put("MEDIUM", item);
                    if(quality.equals("AUDIO_QUALITY_LOW"))
                        audios.put("LOW", item);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return "";
        }

        JSONObject selected;
        boolean lowQuality = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean("lowQuality", false);
        if(lowQuality && audios.has("LOW")) selected = audios.optJSONObject("LOW");
        else selected = audios.optJSONObject("MEDIUM");

        final Bundle bundle = new Bundle();
        bundle.putLong("duration", Long.parseLong(playerResponse.optJSONObject("videoDetails").optString("lengthSeconds")));

        if(selected == null) {
            Log.e(TAG, "Selected item should not be null.");
            return "";
        }
        if(selected.has("signatureCipher")) {
            Uri signatureCipher = Uri.parse("http://example.com/?" + selected.optString("signatureCipher"));
            V8 runtime = V8.createV8Runtime();
            runtime.executeScript(decipher.getQueryParameter("decipher"));
            String sig = runtime.executeStringScript("getDecipher(\"" + signatureCipher.getQueryParameter("s") + "\")");
            Uri url = Uri.parse(signatureCipher.getQueryParameter("url")).buildUpon()
                    .appendQueryParameter(signatureCipher.getQueryParameter("sp"), sig)
                    .appendQueryParameter("video_id", mVideoId).build();
            runtime.release(false);
            return url.toString();
        }
        else {
            return selected.optString("url") + "&video_id=" + mVideoId;
        }
    }
}