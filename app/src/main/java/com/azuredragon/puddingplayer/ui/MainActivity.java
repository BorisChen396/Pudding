package com.azuredragon.puddingplayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.YoutubeAPIv3;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.azuredragon.puddingplayer.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    MediaBrowserCompat browser;
    MediaControllerCompat controllerCompat;
    PlayerFragment playerFragment;
    long currentVersion = 20210515;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        browser = new MediaBrowserCompat(this,
                new ComponentName(this, PlaybackService.class),
                connectionCallback,
                null);
        new Thread(this::checkUpdate).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!browser.isConnected()) browser.connect();
    }

    @Override
    public void onBackPressed() {
        if(playerFragment != null &&
                playerFragment.behavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
            playerFragment.behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        else super.onBackPressed();
    }

    MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            super.onConnected();
            controllerCompat = new MediaControllerCompat(MainActivity.this, browser.getSessionToken());
            controllerCompat.registerCallback(controllerCallback);
            refreshPlaylist(controllerCompat.getQueue());
            playerFragment = new PlayerFragment(MainActivity.this, controllerCompat);
        }
    };

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
            refreshPlaylist(queue);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main_actionbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                AlertDialog aboutDialog = new AlertDialog.Builder(MainActivity.this)
                        .setView(getLayoutInflater().inflate(R.layout.dialog_about, null))
                        .setTitle("About")
                        .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                        .create();
                aboutDialog.show();
                return true;
            case R.id.action_add:
                View view = getLayoutInflater().inflate(R.layout.dialog_add_item, null);
                TextInputLayout inputLayout = view.findViewById(R.id.text_input_layout);
                EditText linkEditText = view.findViewById(R.id.edit_text_add_item);
                AlertDialog addDialog = new AlertDialog.Builder(MainActivity.this)
                        .setView(view)
                        .setTitle("Add Item")
                        .setNegativeButton("Cancel", (dialog, which) -> {})
                        .setPositiveButton("Add", (dialog, which) ->
                                onSubmitLink(Utils.decodeYTLink(linkEditText.getText().toString())))
                        .create();
                addDialog.show();
                addDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                linkEditText.setOnEditorActionListener((v, actionId, event) -> {
                    if(actionId == EditorInfo.IME_ACTION_DONE) {
                        if(linkEditText.getText().length() != 0)
                            addDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                        else
                            inputLayout.setError("Required");
                        return true;
                    }
                    return false;
                });
                linkEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        addDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(linkEditText.getText().length() != 0);
                        if(linkEditText.getText().length() == 0) inputLayout.setError("Required");
                        else inputLayout.setError(null);
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void refreshPlaylist(List<MediaSessionCompat.QueueItem> queue) {
        RecyclerView playlist = findViewById(R.id.playlist);
        playlist.setLayoutManager(new LinearLayoutManager(this));
        playlist.setAdapter(new PlaylistItemAdapter(queue, controllerCompat, MainActivity.this));
        Log.i("", queue.size() + "");
    }

    void addItemByVideoId(String videoId) {
        Bundle bundle = new Bundle();
        bundle.putString("videoId", videoId);
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setExtras(bundle)
                .build();
        controllerCompat.addQueueItem(description);
    }

    void addItemByListId(String listId) {
        ProgressDialog dialog = new ProgressDialog(MainActivity.this);
        dialog.setTitle("Adding Playlist");
        dialog.setMessage("Please wait...");
        dialog.setCancelable(false);
        dialog.show();
        new Thread(() -> {
            YoutubeAPIv3 api = new YoutubeAPIv3(MainActivity.this);
            String[] items = api.getVideoIdByPlaylist(listId);
            for (String item : items) {
                addItemByVideoId(item);
            }
            dialog.dismiss();
        }).start();
    }

    void onSubmitLink(Bundle linkInfo) {
        if(linkInfo.getString("videoId") != null && linkInfo.getString("listId") != null) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirm")
                    .setMessage(String.format(getString(R.string.dialog_add_all_items), linkInfo.getString("listId")))
                    .setPositiveButton("Add All", (dialog, which) ->
                            addItemByListId(linkInfo.getString("listId")))
                    .setNegativeButton("Only the video", (dialog, which) ->
                            addItemByVideoId(linkInfo.getString("videoId")))
                    .show();
            return;
        }
        if(linkInfo.getString("videoId") != null) addItemByVideoId(linkInfo.getString("videoId"));
        if(linkInfo.getString("listId") != null) addItemByListId(linkInfo.getString("listId"));
    }

    void checkUpdate() {
        String url = "https://raw.githubusercontent.com/BorisChen396/PuddingPlayer/master/VERSION";
        Bundle response;
        try {
            response = new NetworkHandler(url, this).getResponse();
        } catch (IOException e) {
            Log.e("UpdateCheck", e.getMessage());
            return;
        }
        Uri info = Uri.parse(response.getString("response"));
        long version = Long.parseLong(info.getQueryParameter("version"));
        if(currentVersion < version) {
            runOnUiThread(() -> {
                AlertDialog updateDialog = new AlertDialog.Builder(this)
                        .setTitle("New version available")
                        .setMessage("A newer version is released.  (" + version + ")")
                        .setPositiveButton("Update", (dialog, which) -> {
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(info.getQueryParameter("url")));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                        })
                        .setCancelable(true).create();
                updateDialog.show();
            });
        }
    }
}