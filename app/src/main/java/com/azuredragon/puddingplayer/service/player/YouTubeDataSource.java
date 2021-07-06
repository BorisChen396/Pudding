package com.azuredragon.puddingplayer.service.player;


import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.azuredragon.puddingplayer.VideoInfo;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

public final class YouTubeDataSource implements DataSource {
    public static final class Factory implements DataSource.Factory {
        private final DataSource.Factory mFactory;
        private Context mContext;

        public Factory(Context context, DataSource.Factory factory) {
            mFactory = factory;
            mContext = context;
        }

        @Override
        public DataSource createDataSource() {
            return new YouTubeDataSource(mFactory.createDataSource(), mContext);
        }
    }

    private final DataSource mDataSource;
    private DataSpec updatedDataSpec;
    private Context mContext;

    private YouTubeDataSource(DataSource dataSource, Context context) {
        mDataSource = dataSource;
        mContext = context;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if(!NetworkHandler.isNetworkConnected(mContext))
            throw new IOException(PlaybackService.EXCEPTION_NO_INTERNET);
        String videoId = Uri.parse(dataSpec.uri.getQueryParameter("uri")).getQueryParameter("videoId");
        VideoInfo info = new VideoInfo(mContext);
        updatedDataSpec = dataSpec;
        Bundle result = info.get(videoId);
        if(!result.getBoolean("ok", false) || result.getString("uri") == null)
            throw new IOException(result.getString("reason", "Unable to get the video's uri."));
        Uri uri = Uri.parse(result.getString("uri"));
        updatedDataSpec = updatedDataSpec.buildUpon().setUri(uri).build();
        return mDataSource.open(updatedDataSpec);
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {

    }

    @Nullable
    @Override
    public Uri getUri() {
        return updatedDataSpec == null ? null : updatedDataSpec.uri;
    }

    @Override
    public void close() throws IOException {
        mDataSource.close();
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        return mDataSource.read(target, offset, length);
    }
}