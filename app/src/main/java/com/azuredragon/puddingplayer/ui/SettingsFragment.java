package com.azuredragon.puddingplayer.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.azuredragon.puddingplayer.NetworkHandler;
import com.azuredragon.puddingplayer.R;

import java.io.IOException;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey);
        Preference share = findPreference("shareApplication");
        if(share != null) share.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT,
                    "https://borischen396.github.io/download?req=android-pudding");
            intent.setType("text/plain");
            startActivity(Intent.createChooser(intent, null));
            return true;
        });
        Preference version = findPreference("version");
        if(version != null) version.setOnPreferenceClickListener(preference -> {
            AlertDialog dialog = new AlertDialog.Builder(getContext()).create();
            dialog.setTitle(R.string.preference_about);
            dialog.setMessage("v" + getString(R.string.versionName));
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                    getString(R.string.dialog_button_more_info),
                    (dialog1, which) -> new Thread(() -> {
                        try {
                            String url = "https://raw.githubusercontent.com/BorisChen396/PuddingPlayer/master/VERSION";
                            NetworkHandler handler = new NetworkHandler(url, getContext());
                            Bundle response = handler.getResponse();
                            if(!response.getBoolean("ok", false))
                                return;
                            Uri uri = Uri.parse("https://example.com/?" + response.getString("response", ""));
                            url = uri.getQueryParameter("url");
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));
                            startActivity(intent);
                        } catch (IOException e) {
                            Log.e("", e.getMessage());
                        }
                    }).start());
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.dialog_button_close),
                    (dialog1, which) -> {});
            dialog.setCancelable(false);
            dialog.show();
            return true;
        });
    }
}