package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class NetworkHandler {
    Context mContext;
    String mUrl;
    private HttpsURLConnection mConnection;

    public NetworkHandler(String url, Context context) throws IOException {
        mUrl = url;
        mContext = context;
        mConnection = (HttpsURLConnection) new URL(mUrl).openConnection();
    }

    public HttpsURLConnection getConnection() {
        return mConnection;
    }

    public void setHttpURLConnection(HttpsURLConnection connection) {
        mConnection = connection;
    }

    public boolean isNetworkConnected() {
        ConnectivityManager mConnectionManager;
        mConnectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return  mConnectionManager != null &&
                mConnectionManager.getActiveNetworkInfo() != null &&
                mConnectionManager.getActiveNetworkInfo().isConnected();
    }

    @NonNull
    public Bundle getResponse() {
        Bundle result = new Bundle();

        if(!isNetworkConnected()) {
            result.putInt("statusCode", 0);
            result.putString("statusText", "No internet connection");
            result.putBoolean("ok", false);
            return result;
        }

        try {
            result.putInt("statusCode", mConnection.getResponseCode());
            result.putString("statusText", mConnection.getResponseMessage());
        } catch (IOException e) {
            result.putInt("statusCode", 0);
            result.putString("statusText", e.getMessage());
            result.putBoolean("ok", false);
            return result;
        }

        InputStreamReader reader;
        try {
            reader = new InputStreamReader(mConnection.getInputStream());
        } catch (IOException e) {
            result.putInt("statusCode", 0);
            result.putString("statusText", e.getMessage());
            result.putBoolean("ok", false);
            return result;
        }

        StringBuilder response = new StringBuilder();
        while(true) {
            int i;
            try {
                i = reader.read();
            } catch (IOException e) {
                result.putInt("statusCode", 0);
                result.putString("statusText", e.getMessage());
                result.putBoolean("ok", false);
                return result;
            }

            if(i < 0) break;
            response.append((char) i);
        }

        result.putString("response", response.toString());
        result.putBoolean("ok", true);
        return result;
    }
    
    public InputStream getInputStream() throws IOException {
        return mConnection.getInputStream();
    }
}
