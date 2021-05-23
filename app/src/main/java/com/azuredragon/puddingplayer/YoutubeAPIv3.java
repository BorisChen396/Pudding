package com.azuredragon.puddingplayer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.common.io.BaseEncoding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;

public class YoutubeAPIv3 {
    private Context mContext;
    private String apiKey = "AIzaSyCyCWb8ZgcoGyItt4JiyMZjxKD7ed4UWME";
    private String TAG = "YoutubeAPIv3";

    public YoutubeAPIv3(Context context) {
        mContext = context;
    }

    public String[] getVideoIdByPlaylist(String playlistId) {
        final Uri.Builder uri = new Uri.Builder()
                .scheme("https")
                .authority("www.googleapis.com")
                .appendEncodedPath("youtube/v3/playlistItems")
                .appendQueryParameter("key", apiKey)
                .appendQueryParameter("playlistId", playlistId)
                .appendQueryParameter("part", "contentDetails")
                .appendQueryParameter("maxResults", "50");

        Uri mUri = uri.build();
        String[] itemsId = new String[0];
        boolean hasNextPage = true;
        while (hasNextPage) {
            NetworkHandler request;
            try {
                request = new NetworkHandler(mUri.toString(), mContext);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return new String[0];
            }
            request.setHttpURLConnection(setupConnection(request.getConnection()));
            Bundle response = request.getResponse();
            if(!response.getBoolean("ok") || response.getString("response") == null) {
                Log.e(TAG, String.format(mContext.getString(R.string.error_http_error), response.getInt("statusCode")));
                return new String[0];
            }
            JSONObject content;
            try {
                content = new JSONObject(response.getString("response"));
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
                return new String[0];
            }
            String nextPageToken = content.optString("nextPageToken");
            JSONArray items = content.optJSONArray("items");
            if(items == null) {
                Log.e(TAG, "Object should not be null.");
                return new String[0];
            }
            itemsId = new String[items.length()];
            for(int i = 0; i < items.length(); i++) {
                itemsId[i] = items.optJSONObject(i).optJSONObject("contentDetails").optString("videoId");
            }
            if(!nextPageToken.equals("")) {
                mUri = uri.appendQueryParameter("pageToken", nextPageToken).build();
            }
            else hasNextPage = false;
        }
        return itemsId;
    }

    private HttpsURLConnection setupConnection(HttpsURLConnection connection) {
        connection.setRequestProperty("X-Android-Package", mContext.getPackageName());
        connection.setRequestProperty("X-Android-Cert", getSignature());
        return connection;
    }

    private String getSignature() {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo info = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_SIGNATURES);
            if(info == null ||
                    info.signatures == null ||
                    info.signatures.length == 0 ||
                    info.signatures[0] == null) return null;
            Signature sig = info.signatures[0];
            MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
            byte[] digest = messageDigest.digest(sig.toByteArray());
            return BaseEncoding.base16().lowerCase().encode(digest);
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
