package com.azuredragon.puddingplayer.service.player;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.azuredragon.puddingplayer.service.VideoInfo;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

public final class URLUpdatingDataSource implements DataSource {
    public static final class Factory implements DataSource.Factory {
        private final DataSource.Factory mFactory;
        private Context mContext;

        public Factory(Context context, DataSource.Factory factory) {
            mFactory = factory;
            mContext = context;
        }

        @Override
        public DataSource createDataSource() {
            return new URLUpdatingDataSource(mFactory.createDataSource(), mContext);
        }
    }

    private final DataSource mDataSource;
    private DataSpec updatedDataSpec;
    private Context mContext;

    private URLUpdatingDataSource(DataSource dataSource, Context context) {
        mDataSource = dataSource;
        mContext = context;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        VideoInfo info = new VideoInfo(mContext, dataSpec.uri.getQueryParameter("videoId"));
        updatedDataSpec = dataSpec;
        try {
            String url = info.getInfo();
            if("".equals(url)) throw new IOException("An error has occurred while getting the video url.");
            updatedDataSpec = updatedDataSpec.buildUpon().setUri(Uri.parse(url)).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
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