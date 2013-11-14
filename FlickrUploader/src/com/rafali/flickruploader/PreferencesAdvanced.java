package com.rafali.flickruploader;

import com.googlecode.androidannotations.api.BackgroundExecutor;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

@SuppressWarnings("deprecation")
public class PreferencesAdvanced extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		addPreferencesFromResource(R.xml.preferences_advanced);
		findPreference("clear_logs").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Utils.showConfirmCancel(PreferencesAdvanced.this, "Delete logs", "Do you confirm deleting all log files?", new Utils.Callback<Boolean>() {
					@Override
					public void onResult(Boolean result) {
						if (result) {
							BackgroundExecutor.execute(new Runnable() {
								@Override
								public void run() {
									FlickrUploader.deleteAllLogs();
									render();
								}
							});
						}
					}
				});
				return false;
			}
		});
		render();
	}

	private void render() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				final long size = FlickrUploader.getLogSize();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						findPreference("clear_logs").setSummary("Currently using " + Utils.formatFileSize(size));
					}
				});
			}
		});
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		Mixpanel.track("Preference Change", key, sp.getAll().get(key));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}

}
