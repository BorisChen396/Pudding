package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class NetworkHandler {
    Context mContext;
    private HttpsURLConnection httpsURLConnection;

    public NetworkHandler(String url, Context context) throws IOException {
        mContext = context;
        httpsURLConnection = (HttpsURLConnection) new URL(url).openConnection();
    }

    public void setHttpsURLConnection(HttpsURLConnection connection) {
        httpsURLConnection = connection;
    }

    public HttpsURLConnection getHttpsURLConnection() {
        return httpsURLConnection;
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager mConnectionManager;
        mConnectionManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return  mConnectionManager != null &&
                mConnectionManager.getActiveNetworkInfo() != null &&
                mConnectionManager.getActiveNetworkInfo().isConnected();
    }

    @NonNull
    public Bundle getResponse() {
        Bundle result = new Bundle();

        if(!isNetworkConnected(mContext)) {
            result.putInt("statusCode", 0);
            result.putString("statusText", "No internet connection");
            result.putBoolean("ok", false);
            return result;
        }

        try {
            result.putInt("statusCode", httpsURLConnection.getResponseCode());
            result.putString("statusText", httpsURLConnection.getResponseMessage());
        } catch (IOException e) {
            result.putInt("statusCode", 0);
            result.putString("statusText", e.getMessage());
            result.putBoolean("ok", false);
            return result;
        }

        InputStreamReader reader;
        try {
            reader = new InputStreamReader(httpsURLConnection.getInputStream());
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

    @NonNull
    public Bundle post(String content) throws ProtocolException {
        httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(content.length()));
        httpsURLConnection.setRequestMethod("POST");
        httpsURLConnection.setDoInput(true);
        httpsURLConnection.setDoOutput(true);
        Bundle result = new Bundle();
        OutputStream os;
        try {
            os = httpsURLConnection.getOutputStream();
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e) {
            result.putInt("statusCode", 0);
            result.putString("statusText", e.getMessage());
            result.putBoolean("ok", false);
            return result;
        }
        return getResponse();
    }
    
    public InputStream getInputStream() throws IOException {
        return httpsURLConnection.getInputStream();
    }
}
