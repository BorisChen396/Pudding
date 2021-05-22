package com.azuredragon.puddingplayer.ui;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.azuredragon.puddingplayer.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey);
    }
}
