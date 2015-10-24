package com.rafali.flickruploader.ui.activity;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import com.rafali.common.STR;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

public class PreferencesNotificationActivity extends AbstractPreferenceActivity implements OnSharedPreferenceChangeListener {

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setIcon(R.drawable.preferences);
        }
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        addPreferencesFromResource(R.xml.preferences_notification);
        if (Utils.isPremium()) {
            Preference end_of_trial = findPreference(STR.end_of_trial);
            getPreferenceScreen().removePreference(end_of_trial);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }
}
