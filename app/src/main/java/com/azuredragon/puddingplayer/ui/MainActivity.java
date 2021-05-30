package com.azuredragon.puddingplayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.YoutubeAPIv3;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.azuredragon.puddingplayer.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    MediaBrowserCompat browser;
    MediaControllerCompat controllerCompat;
    PlayerFragment playerFragment;
    RecyclerView playlist;

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
    protected void onStop() {
        super.onStop();
        if (browser.isConnected()) browser.disconnect();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
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
            playerFragment = new PlayerFragment(MainActivity.this, controllerCompat);
            playerFragment.behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    refreshPlaylistPadding();
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
            });
            PlaylistItemAdapter playlistItemAdapter = new PlaylistItemAdapter(controllerCompat, MainActivity.this);
            playlist = findViewById(R.id.playlist);
            playlist.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            playlist.setAdapter(playlistItemAdapter);
            refreshPlaylistPadding();
            handleSharedContent();
        }

        void refreshPlaylistPadding() {
            if(playerFragment.behavior.getState() != BottomSheetBehavior.STATE_HIDDEN)
                playlist.setPadding(0, 0, 0, Utils.dp2px(MainActivity.this, 70));
            else
                playlist.setPadding(0, 0, 0, 0);
        }
    };

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
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
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_add:
                View view = getLayoutInflater().inflate(R.layout.dialog_add_item, null);
                TextInputLayout inputLayout = view.findViewById(R.id.text_input_layout);
                EditText linkEditText = view.findViewById(R.id.edit_text_add_item);
                AlertDialog addDialog = new AlertDialog.Builder(MainActivity.this)
                        .setView(view)
                        .setTitle(R.string.dialog_add_item_title)
                        .setNegativeButton(R.string.dialog_button_cancel, (dialog, which) -> {})
                        .setPositiveButton(R.string.dialog_button_ok, (dialog, which) ->
                                onSubmitLink(Utils.decodeYTLink(linkEditText.getText().toString())))
                        .create();
                addDialog.show();
                addDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                linkEditText.setOnEditorActionListener((v, actionId, event) -> {
                    if(actionId == EditorInfo.IME_ACTION_DONE) {
                        if(linkEditText.getText().length() != 0)
                            addDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                        else
                            inputLayout.setError(getString(R.string.edittext_error_required));
                        return true;
                    }
                    return false;
                });
                linkEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        addDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(linkEditText.getText().length() != 0);
                        if(linkEditText.getText().length() == 0)
                            inputLayout.setError(getString(R.string.edittext_error_required));
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

    void handleSharedContent() {
        String action = getIntent().getAction();
        String type = getIntent().getType();
        String data = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if(Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            if(data != null) onSubmitLink(Utils.decodeYTLink(data));
            setIntent(getIntent().putExtra(Intent.EXTRA_TEXT, ""));
        }
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
        dialog.setTitle(R.string.dialog_add_item_title);
        dialog.setMessage(getString(R.string.dialog_content_wait));
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
                    .setTitle(R.string.dialog_add_item_title)
                    .setMessage(String.format(getString(R.string.dialog_add_all_items), linkInfo.getString("listId")))
                    .setPositiveButton(R.string.dialog_button_add_all, (dialog, which) ->
                            addItemByListId(linkInfo.getString("listId")))
                    .setNegativeButton(R.string.dialog_button_only_one, (dialog, which) ->
                            addItemByVideoId(linkInfo.getString("videoId")))
                    .show();
            return;
        }
        if(linkInfo.getString("videoId") != null) addItemByVideoId(linkInfo.getString("videoId"));
        if(linkInfo.getString("listId") != null) addItemByListId(linkInfo.getString("listId"));
    }

    void checkUpdate() {
        boolean autoCheck = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("checkUpdate", true);
        if(!autoCheck) return;

        String currentVersion = getVersionCode();
        String url = "https://raw.githubusercontent.com/BorisChen396/PuddingPlayer/master/VERSION";
        Bundle response;
        try {
            response = new NetworkHandler(url, this).getResponse();
        } catch (IOException e) {
            Log.e("UpdateCheck", e.getMessage());
            return;
        }
        Uri info = Uri.parse("https://example.com/?" + response.getString("response"));
        String version = info.getQueryParameter("version");
        if(!currentVersion.equals(version) && version != null) {
            runOnUiThread(() -> {
                AlertDialog updateDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title_update)
                        .setMessage(String.format(getString(R.string.dialog_content_update), version))
                        .setPositiveButton(R.string.dialog_button_update, (dialog, which) ->
                                downloadApk(info.getQueryParameter("apk-url")))
                        .setNeutralButton(R.string.dialog_button_more_info, (dialog, which) -> {
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(info.getQueryParameter("url")));
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.dialog_button_cancel, (dialog, which) -> {
                        })
                        .setCancelable(true).create();
                updateDialog.show();
            });
        }
    }

    void downloadApk(String url) {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(url));
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Pudding.apk");
        long id = manager.enqueue(request);
        ProgressDialog updateDialog = new ProgressDialog(this);
        updateDialog.setTitle(R.string.dialog_title_update);
        updateDialog.setMessage(getString(R.string.dialog_content_wait));
        updateDialog.setOnCancelListener(dialog -> manager.remove(id));
        updateDialog.create();
        registerReceiver(new DownloadReceiver(manager, id, updateDialog::dismiss),
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        updateDialog.show();
        Toast.makeText(this, R.string.toast_update_start, Toast.LENGTH_LONG).show();
    }

    public String getVersionCode(){
        PackageManager packageManager = getPackageManager();
        PackageInfo packageInfo;
        String versionCode = "";
        try {
            packageInfo = packageManager.getPackageInfo(getPackageName(),0);
            versionCode = String.valueOf(packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    static class DownloadReceiver extends BroadcastReceiver {
        private final long downloadId;
        private DownloadManager mManager;
        private final OnEndedListener onEndedListener;

        DownloadReceiver(DownloadManager manager, long id, OnEndedListener onEnded) {
            downloadId = id;
            mManager = manager;
            onEndedListener = onEnded;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if(downloadId != id) return;
                onEndedListener.onEnded();
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(id);
                Cursor result = mManager.query(q);
                result.moveToFirst();
                int statusIndex = result.getColumnIndex(DownloadManager.COLUMN_STATUS);
                try {
                    if(result.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, R.string.toast_update_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                } catch (CursorIndexOutOfBoundsException e) {
                    Log.e("Update", e.getMessage());
                    Toast.makeText(context, R.string.toast_update_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                int uriIndex = result.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                Uri fileUri = Uri.parse(result.getString(uriIndex));
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri uri = FileProvider.getUriForFile(context,
                            "com.azuredragon.puddingplayer.provider", new File(fileUri.getPath()));
                    intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    intent.setData(uri);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                else {
                    intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
                context.unregisterReceiver(this);
                result.close();
            }
        }

        interface OnEndedListener {
            void onEnded();
        }
    }
}