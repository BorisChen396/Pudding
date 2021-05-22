package com.azuredragon.puddingplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.appcompat.widget.SwitchCompat;

import com.azuredragon.puddingplayer.R;

public class SettingsDialog extends AlertDialog {
    private Activity mActivity;
    private View view;

    private SwitchCompat highQuality;

    public SettingsDialog(Activity activity) {
        super(activity);
        mActivity = activity;
        view = mActivity.getLayoutInflater().inflate(R.layout.dialog_settings, null);
        setTitle("Settings");
        setView(view);
        setButton(BUTTON_POSITIVE, "Save", (dialog, which) -> saveSettings());
        setButton(BUTTON_NEGATIVE, "Cancel", (dialog, which) -> {});
        initializeView();
        loadCurrentSettings();
    }

    private void initializeView() {
        highQuality = view.findViewById(R.id.option_highQuality);
    }

    private void loadCurrentSettings() {
        SharedPreferences settings = mActivity.getSharedPreferences("settings", Context.MODE_PRIVATE);
        highQuality.setChecked(settings.getBoolean("highQuality", true));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = mActivity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit();
        editor.putBoolean("highQuality", highQuality.isChecked());
        editor.commit();
    }
}
