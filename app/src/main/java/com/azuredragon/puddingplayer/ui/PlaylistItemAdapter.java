package com.azuredragon.puddingplayer.ui;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class PlaylistItemAdapter extends RecyclerView.Adapter<PlaylistItemAdapter.ViewHolder> {
    private final MediaControllerCompat mController;
    private final List<MediaSessionCompat.QueueItem> mQueue;
    private final Activity mActivity;
    private final ExecutorService executorService;
    private final String TAG = "PlaylistItemAdapter";
    private final List<String> downloadList = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView, MediaControllerCompat controller) {
            super(itemView);
            itemView.setOnClickListener(v ->
                    controller.getTransportControls().skipToQueueItem(getAdapterPosition()));
        }

        TextView getTitle() {
            return itemView.findViewById(R.id.playlist_item_title);
        }

        TextView getAuthor() {
            return itemView.findViewById(R.id.playlist_item_author);
        }

        ImageView getArtwork() {
            return itemView.findViewById(R.id.imageView3);
        }
    }

    public PlaylistItemAdapter(List<MediaSessionCompat.QueueItem> queue,
                               MediaControllerCompat controller,
                               Activity activity) {
        mQueue = queue;
        mController = controller;
        mActivity = activity;

        executorService = Executors.newSingleThreadExecutor();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_playlist_item, parent, false);
        return new ViewHolder(view, mController);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if(mQueue.get(position).getDescription().getExtras() == null) return;
        String videoId = mQueue.get(position).getDescription().getExtras().getString("videoId");
        FileLoader loader = new FileLoader(mActivity);
        String metadataPath = loader.APPLICATION_CACHE_DIR + videoId;
        String thumbnailPath = loader.APPLICATION_CACHE_DIR + videoId + ".jpg";
        if(!loader.checkFileExistance(metadataPath) || !loader.checkFileExistance(thumbnailPath)) {
            //需要下載影片資訊
            if(downloadList.contains(videoId)) return;  //項目已在下載序列中
            downloadList.add(videoId);
            holder.getTitle().setText(videoId);
            holder.setIsRecyclable(false);  //避免於下載時ViewHolder被回收
            executorService.execute(() -> {
                try {
                    String url = new Uri.Builder()
                            .scheme("https")
                            .authority("www.youtube.com")
                            .appendPath("oembed")
                            .appendQueryParameter("url", "youtu.be/" + videoId)
                            .toString();
                    JSONObject response = new JSONObject(loader.downloadFile(url, videoId, true));
                    url = response.optString("thumbnail_url");
                    loader.downloadFile(url, videoId + ".jpg", true);
                    downloadList.remove(videoId);
                    mActivity.runOnUiThread(() -> {
                        holder.setIsRecyclable(true);
                        notifyItemChanged(position);
                    });
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            });
        }
        else {
            //從暫存區取得影片資訊
            if(loader.getFileSize(metadataPath) == 0 || loader.getFileSize(thumbnailPath) == 0) return;
            setViewContent(loader.loadFile(metadataPath), holder, thumbnailPath);
        }
    }

    void setViewContent(String content, ViewHolder holder, String thumbnailPath) {
        JSONObject metadata;
        try {
            metadata = new JSONObject(content);
        } catch (JSONException e) {
            Log.e(TAG, "The Metadata file exists, but it is not downloaded yet.");
            return;
        }
        String title = metadata.optString("title");
        String author = metadata.optString("author_name");
        holder.getTitle().setText(title);
        holder.getAuthor().setText(author);
        holder.getArtwork().setImageBitmap(BitmapFactory.decodeFile(thumbnailPath));
    }

    @Override
    public int getItemCount() {
        return mQueue.size();
    }
}
