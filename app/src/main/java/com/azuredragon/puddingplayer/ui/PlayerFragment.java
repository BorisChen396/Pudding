package com.azuredragon.puddingplayer.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.Utils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

class PlayerFragment {
    private AppCompatActivity mActivity;
    private MediaControllerCompat controller;
    BottomSheetBehavior<View> behavior;
    private String TAG = "PlayerFragment";
    private View view;
    private View smallPlayer;
    private View largePlayer;

    PlayerFragment(AppCompatActivity activity, MediaControllerCompat controllerCompat) {
        mActivity = activity;
        controller = controllerCompat;
        controller.registerCallback(controllerCallback);
        view = activity.findViewById(R.id.bottomsheet_player);
        smallPlayer = view.findViewById(R.id.fragment_small_player);
        largePlayer = view.findViewById(R.id.fragment_large_player);
        behavior = BottomSheetBehavior.from(view);
        behavior.addBottomSheetCallback(bottomSheetCallback);
        refreshViewByState(controllerCompat.getPlaybackState());
        refreshViewByMetadata(controllerCompat.getMetadata());
        smallPlayer.setOnClickListener((v) -> {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        initializeTransportControls();
    }

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            refreshViewByState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            refreshViewByMetadata(metadata);
        }
    };

    boolean isUpdating = false;
    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            isUpdating = true;
            ProgressBar smallProgressBar = smallPlayer.findViewById(R.id.player_small_progress);
            TextView smallPosition = smallPlayer.findViewById(R.id.player_small_current);
            SeekBar largeProgressBar = largePlayer.findViewById(R.id.player_large_progress);
            TextView largePosition = largePlayer.findViewById(R.id.player_large_current);

            smallProgressBar.setProgress((int) controller.getPlaybackState().getPosition());
            if(!isTracking) largeProgressBar.setProgress((int) controller.getPlaybackState().getPosition());
            largeProgressBar.setSecondaryProgress((int) controller.getPlaybackState().getBufferedPosition());
            smallPosition.setText(Utils.secondToString(controller.getPlaybackState().getPosition() / 1000));
            if(!isTracking) largePosition.setText(Utils.secondToString(controller.getPlaybackState().getPosition() / 1000));
            if(controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
                new Handler(Looper.getMainLooper()).postDelayed(this, 500);
            else isUpdating = false;
        }
    };

    boolean isTracking = false;
    private void initializeTransportControls() {
        ImageButton smallPlayButton = smallPlayer.findViewById(R.id.player_small_playPause);
        ImageButton largePlayButton = largePlayer.findViewById(R.id.player_large_playPause);
        ImageButton largePrevButton = largePlayer.findViewById(R.id.player_large_prev);
        ImageButton largeNextButton = largePlayer.findViewById(R.id.player_large_next);
        SeekBar largeSeekBar = largePlayer.findViewById(R.id.player_large_progress);
        TextView largePosition = largePlayer.findViewById(R.id.player_large_current);

        smallPlayButton.setOnClickListener((v) -> {
            if(controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
                controller.getTransportControls().pause();
            else
                controller.getTransportControls().play();
        });
        largePlayButton.setOnClickListener((v) -> {
            if(controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
                controller.getTransportControls().pause();
            else
                controller.getTransportControls().play();
        });
        largePrevButton.setOnClickListener((v) -> controller.getTransportControls().skipToPrevious());
        largeNextButton.setOnClickListener((v) -> controller.getTransportControls().skipToNext());
        largeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                largePosition.setText(Utils.secondToString(progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking = false;
                controller.getTransportControls().seekTo(seekBar.getProgress());
            }
        });
    }

    private void refreshViewByMetadata(MediaMetadataCompat metadata) {
        TextView smallTitle = smallPlayer.findViewById(R.id.player_small_title);
        TextView largeTitle = largePlayer.findViewById(R.id.player_large_title);
        TextView largeAuthor = largePlayer.findViewById(R.id.player_large_author);
        TextView largeDuration = largePlayer.findViewById(R.id.player_large_duration);
        ProgressBar smallProgressBar = smallPlayer.findViewById(R.id.player_small_progress);
        ProgressBar largeProgressBar = largePlayer.findViewById(R.id.player_large_progress);

        largeAuthor.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_AUTHOR));
        largeDuration.setText(Utils.secondToString(metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000));
        smallTitle.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        largeTitle.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        smallProgressBar.setMax((int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
        largeProgressBar.setMax((int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
    }


    private void refreshViewByState(PlaybackStateCompat state) {
        ImageView largeArtwork = largePlayer.findViewById(R.id.player_large_artwork);
        ImageButton smallPlayButton = smallPlayer.findViewById(R.id.player_small_playPause);
        ImageButton largePlayButton = largePlayer.findViewById(R.id.player_large_playPause);
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_BUFFERING:
                if(behavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
                if(behavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                break;
            case PlaybackStateCompat.STATE_ERROR:
                break;
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
                break;
            case PlaybackStateCompat.STATE_NONE:
                behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                if(behavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                smallPlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_play));
                largePlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_play));
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                if(behavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                smallPlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_pause));
                largePlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_pause));
                if(!isUpdating) new Handler(Looper.getMainLooper()).post(updateProgress);
                break;
            case PlaybackStateCompat.STATE_REWINDING:
                break;
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                break;
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                break;
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                break;
            case PlaybackStateCompat.STATE_STOPPED:
                behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
            default:
                smallPlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_play));
                largePlayButton.setImageDrawable(mActivity.getDrawable(R.drawable.btn_play));
                break;
        }
        Bitmap bm = BitmapFactory.decodeFile(new FileLoader(mActivity).APPLICATION_DATA_DIR + "thumbnail.jpg");
        largeArtwork.setImageBitmap(bm);
    }

    BottomSheetBehavior.BottomSheetCallback bottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
                case BottomSheetBehavior.STATE_COLLAPSED:
                    smallPlayer.setVisibility(View.VISIBLE);
                    largePlayer.setVisibility(View.GONE);
                    break;
                case BottomSheetBehavior.STATE_DRAGGING:
                case BottomSheetBehavior.STATE_SETTLING:
                    smallPlayer.setVisibility(View.VISIBLE);
                    largePlayer.setVisibility(View.VISIBLE);
                    break;
                case BottomSheetBehavior.STATE_EXPANDED:
                    smallPlayer.setVisibility(View.GONE);
                    largePlayer.setVisibility(View.VISIBLE);
                    break;
                case BottomSheetBehavior.STATE_HALF_EXPANDED:
                    break;
                case BottomSheetBehavior.STATE_HIDDEN:
                    controller.getTransportControls().stop();
                    break;
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            smallPlayer.setAlpha(1 - slideOffset);
            largePlayer.setAlpha(slideOffset);
        }
    };
}
