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

    public void getVideoIdByChannelId(String channelId) throws IOException {/*
        Uri.Builder linkBuilder = new Uri.Builder()
                .scheme("https")
                .authority("www.googleapis.com")
                .appendEncodedPath("youtube/v3/search")
                .appendQueryParameter("key", apiKey)
                .appendQueryParameter("channelId", channelId)
                .appendQueryParameter("type", "video")
                .appendQueryParameter("maxResults", "50");

        final FileLoader mFileLoader = new FileLoader(mContext);
        final NetworkHandler mNetworkHandler = new NetworkHandler(linkBuilder.toString(), mContext);
        final String filePath = mFileLoader.APPLICATION_DATA_DIR + "search";
        mNetworkHandler.setHttpURLConnection(setupConnection(mNetworkHandler.getHttpURLConnection()));

        mNetworkHandler.setOnErrorListener(new NetworkHandler.OnErrorListener() {
            @Override
            public void onError(String info) {
                showToast("HttpRequestError: " + info);
            }
        });
        mNetworkHandler.setOnDownloadComplete(new NetworkHandler.OnDownloadComplete() {
            @Override
            public void onDownloadComplete() throws IOException {
                mFileLoader.loadFile(filePath);
            }
        });

        mFileLoader.setOnFileLoadedListener(new FileLoader.OnFileLoadedListener() {
            @Override
            public void onFileLoaded(String fileContent) {
                try {
                    JSONArray items = new JSONObject(fileContent).getJSONArray("items");
                    if(items.length() == 0) {
                        showToast("No results.");
                        return;
                    }
                    final JSONArray result = new JSONArray();
                    for(int i = 0; i < items.length(); i++) {
                        result.put(result.length(), items.getJSONObject(i).getJSONObject("id").getString("videoId"));
                    }
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mOnComplete.onComplete(result);
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                    showToast("Invalid api response.\n" + e.getMessage());
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mNetworkHandler.downloadFile(filePath, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
    }

    public void getVideoIdByUsername(String username) throws IOException {/*
        Uri.Builder linkBuilder = new Uri.Builder()
                .scheme("https")
                .authority("www.googleapis.com")
                .appendEncodedPath("youtube/v3/channels")
                .appendQueryParameter("key", apiKey)
                .appendQueryParameter("forUsername", username);

        final FileLoader mFileLoader = new FileLoader(mContext);
        final NetworkHandler mNetworkHandler = new NetworkHandler(linkBuilder.toString(), mContext);
        final String filePath = mFileLoader.APPLICATION_DATA_DIR + "channels";
        mNetworkHandler.setHttpURLConnection(setupConnection(mNetworkHandler.getHttpURLConnection()));

        mNetworkHandler.setOnErrorListener(new NetworkHandler.OnErrorListener() {
            @Override
            public void onError(String info) {
                showToast("HttpRequestError: " + info);
            }
        });
        mNetworkHandler.setOnDownloadComplete(new NetworkHandler.OnDownloadComplete() {
            @Override
            public void onDownloadComplete() throws IOException {
                mFileLoader.loadFile(filePath);
            }
        });

        mFileLoader.setOnFileLoadedListener(new FileLoader.OnFileLoadedListener() {
            @Override
            public void onFileLoaded(String fileContent) {
                try {
                    JSONArray items = new JSONObject(fileContent).getJSONArray("items");
                    if(items.length() == 0) {
                        showToast("No results.");
                        return;
                    }
                    getVideoIdByChannelId(items.getJSONObject(0).getString("id"));
                } catch (JSONException e) {
                    e.printStackTrace();
                    showToast("Invalid api response.\n" + e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    showToast("An Exception has occurred.\n" + e.getMessage());
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mNetworkHandler.downloadFile(filePath, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
    }

    public void getVideoIdByCustomName(String customName) throws IOException {/*
        Uri.Builder linkBuilder = new Uri.Builder()
                .scheme("https")
                .authority("www.googleapis.com")
                .appendEncodedPath("youtube/v3/search")
                .appendQueryParameter("key", apiKey)
                .appendQueryParameter("q", customName)
                .appendQueryParameter("type", "channel");

        final FileLoader mFileLoader = new FileLoader(mContext);
        final NetworkHandler mNetworkHandler = new NetworkHandler(linkBuilder.toString(), mContext);
        final String filePath = mFileLoader.APPLICATION_DATA_DIR + "channels";
        mNetworkHandler.setHttpURLConnection(setupConnection(mNetworkHandler.getHttpURLConnection()));

        mNetworkHandler.setOnErrorListener(new NetworkHandler.OnErrorListener() {
            @Override
            public void onError(String info) {
                showToast("HttpRequestError: " + info);
            }
        });
        mNetworkHandler.setOnDownloadComplete(new NetworkHandler.OnDownloadComplete() {
            @Override
            public void onDownloadComplete() throws IOException {
                mFileLoader.loadFile(filePath);
            }
        });

        mFileLoader.setOnFileLoadedListener(new FileLoader.OnFileLoadedListener() {
            @Override
            public void onFileLoaded(String fileContent) {
                try {
                    JSONArray items = new JSONObject(fileContent).getJSONArray("items");
                    if(items.length() == 0) {
                        showToast("No results.");
                        return;
                    }
                    getVideoIdByChannelId(items.getJSONObject(0).getJSONObject("id").getString("channelId"));
                } catch (JSONException e) {
                    e.printStackTrace();
                    showToast("Invalid api response.\n" + e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    showToast("An Exception has occurred.\n" + e.getMessage());
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mNetworkHandler.downloadFile(filePath, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
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
