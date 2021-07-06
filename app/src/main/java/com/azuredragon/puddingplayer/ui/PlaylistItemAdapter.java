package com.azuredragon.puddingplayer.ui;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class PlaylistItemAdapter extends RecyclerView.Adapter<PlaylistItemAdapter.ViewHolder> {
    private MediaControllerCompat mController;
    private List<MediaSessionCompat.QueueItem> mQueue;
    private final Activity mActivity;
    private final ExecutorService executorService;
    private final String TAG = "PlaylistItemAdapter";
    private final List<String> downloadList = new ArrayList<>();
    private int currentIndex = -1;
    ItemTouchHelper touchHelper;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
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

        void setIsCurrent(boolean isCurrent) {
            View playingMask = itemView.findViewById(R.id.current_item_mask);
            playingMask.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        }
    }

    public PlaylistItemAdapter(Activity activity) {
        mActivity = activity;
        executorService = Executors.newSingleThreadExecutor();
        touchHelper = new ItemTouchHelper(touchCallback);
    }

    void initializeController(MediaControllerCompat controller) {
        mQueue = controller.getQueue();
        mController = controller;
        controller.registerCallback(controllerCallback);
        mController.sendCommand(PlaybackService.ACTION_GET_CURRENT_INDEX, null, resultReceiver);
        notifyDataSetChanged();
    }

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
            mQueue = queue;
            notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            mController.sendCommand(PlaybackService.ACTION_GET_CURRENT_INDEX, null, resultReceiver);
        }
    };

    ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            currentIndex = resultCode;
            notifyDataSetChanged();
        }
    };

    ItemTouchHelper.Callback touchCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP|ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT) {
        int fromPos = -1;
        int toPos = -1;

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if(actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null)
                fromPos = viewHolder.getAdapterPosition();
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if(fromPos >= 0 && toPos >= 0) {
                Bundle params = new Bundle();
                params.putInt(TimelineQueueEditor.EXTRA_FROM_INDEX, fromPos);
                params.putInt(TimelineQueueEditor.EXTRA_TO_INDEX, toPos);
                mController.sendCommand(TimelineQueueEditor.COMMAND_MOVE_QUEUE_ITEM, params, null);
                fromPos = toPos = -1;
            }
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            toPos = target.getAdapterPosition();
            notifyItemMoved(from, toPos);
            return true;
        }

        Snackbar itemRemovedSnackBar;
        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int pos = viewHolder.getAdapterPosition();
            MediaDescriptionCompat description = mQueue.get(pos).getDescription();
            notifyItemRemoved(pos);
            mController.removeQueueItem(mQueue.get(pos).getDescription());

            if(itemRemovedSnackBar == null || itemRemovedSnackBar.isShown())
                itemRemovedSnackBar = Snackbar.make(mActivity.findViewById(R.id.container_bottomsheet),
                        R.string.snackbar_item_removed, Snackbar.LENGTH_LONG);
            itemRemovedSnackBar.setAnchorView(R.id.bottomsheet_player);
            itemRemovedSnackBar.setAction(R.string.snackbar_action_undo, v ->
                    mController.addQueueItem(description, pos));
            itemRemovedSnackBar.show();
        }
    };

    View mPlayerView;
    void setPlayerView(View playerView) {
        mPlayerView = playerView;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        touchHelper.attachToRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_playlist_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (mQueue.get(position).getDescription().getExtras() == null) return;
        String type = mQueue.get(position).getDescription().getExtras().getString("type");
        switch (type) {
            case PlaybackService.TYPE_LOCAL:
                bindViewHolderByLocalFile(holder, position);
                break;
            case PlaybackService.TYPE_YOUTUBE:
                bindViewHolderByVideoId(holder, position);
                break;
        }
        holder.itemView.setOnClickListener(v -> {
            long id = mController.getQueue().get(holder.getAdapterPosition()).getQueueId();
            mController.getTransportControls().skipToQueueItem(id);
        });
        holder.setIsCurrent(mQueue.get(position).getQueueId() == currentIndex);
    }

    void bindViewHolderByLocalFile(@NonNull ViewHolder holder, int position) {
        Uri uri = mQueue.get(position).getDescription().getMediaUri();
        if(uri == null) return;
        String filename = DocumentFile.fromSingleUri(mActivity, uri).getName();
        filename = Utils.getFilenameWithoutExtension(filename, mActivity);
        boolean isRetrieverSupported = true;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mActivity, uri);
        } catch (Exception e) {
            Log.e("MetadataProvider", "File not supported by retriever.");
            isRetrieverSupported = false;
        }
        String title = isRetrieverSupported ? retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) : null;
        String artist = isRetrieverSupported ? retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) : null;
        String author = isRetrieverSupported ? retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR) : null;
        byte[] artwork = isRetrieverSupported ? retriever.getEmbeddedPicture() : null;
        holder.getTitle().setText(title == null ? filename : title);
        holder.getAuthor().setText(artist == null ?
                (author == null ? mActivity.getString(R.string.metadata_unknown_author) : author) : artist);

        if(artwork == null) {
            holder.getArtwork().setImageDrawable(
                    ContextCompat.getDrawable(mActivity, R.drawable.ic_baseline_music_note_24));
        }
        else {
            holder.getArtwork().setImageBitmap(BitmapFactory.decodeByteArray(artwork, 0, artwork.length));
        }
    }

    void bindViewHolderByVideoId(@NonNull ViewHolder holder, int position) {
        String videoId = mQueue.get(position).getDescription().getMediaUri().getQueryParameter("videoId");
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
            setYtViewContent(loader.loadFile(metadataPath), holder, thumbnailPath);
        }
    }

    void setYtViewContent(String content, ViewHolder holder, String thumbnailPath) {
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
        if(mQueue == null) return 0;
        return mQueue.size();
    }
}
