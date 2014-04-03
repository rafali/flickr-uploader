package com.rafali.flickruploader.ui.activity;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.ManyQuery;
import se.emilsjolander.sprinkles.ModelList;
import se.emilsjolander.sprinkles.Query;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import com.google.analytics.tracking.android.EasyTracker;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.Config;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.enums.STATUS;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PreferencesActivity.class);
	public static final String UPLOAD_NETWORK = "upload_network";
	public static final String UPLOAD_PRIVACY = "upload_privacy";
	public static final String AUTOUPLOAD = "autoupload";
	public static final String AUTOUPLOAD_VIDEOS = "autouploadvideos";
	public static final String CHARGING_ONLY = "charging_only";

	PreferencesActivity activity = this;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		handler = new Handler();
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setIcon(R.drawable.preferences);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		// add preferences from xml
		addPreferencesFromResource(R.xml.preferences);
		sp.registerOnSharedPreferenceChangeListener(this);
		findPreference("login").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (Utils.getStringProperty(STR.userId) != null) {
					new AlertDialog.Builder(activity).setIcon(android.R.drawable.ic_dialog_alert).setTitle("Sign out").setMessage("Confirm signing out. Uploads will be disabled.")
							.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Editor editor = sp.edit();
									editor.remove(STR.userId);
									editor.remove(STR.accessToken);
									editor.remove(STR.accessTokenSecret);
									editor.remove(STR.userName);
									editor.apply();
									editor.commit();
									ManyQuery<Media> query = Query.all(Media.class);
									ModelList.from(query.get()).deleteAllAsync();
									render();
									FlickrApi.reset();
									UploadService.clear(STATUS.QUEUED, null);
									UploadService.clear(STATUS.FAILED, null);
								}
							}).setNegativeButton("Cancel", null).show();
				} else {
					FlickrWebAuthActivity_.intent(activity).start();
				}
				return false;
			}
		});
		ListPreference privacyPreference = (ListPreference) findPreference(UPLOAD_PRIVACY);
		PRIVACY[] privacies = PRIVACY.values();
		int length = privacies.length;
		String[] entries = new String[length];
		String[] values = new String[length];
		for (int i = 0; i < length; i++) {
			entries[i] = privacies[i].getSimpleName();
			values[i] = privacies[i].toString();
		}
		privacyPreference.setEntries(entries);
		privacyPreference.setEntryValues(values);

		findPreference("rate").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.rafali.flickruploader2")));
				Utils.setBooleanProperty(STR.hasRated, true);
				return false;
			}
		});

		findPreference("notifications").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(activity, PreferencesNotificationActivity.class));
				return false;
			}
		});
		findPreference("advancedPreferences").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(activity, PreferencesAdvancedActivity.class));
				return false;
			}
		});

		findPreference("faq").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rafali/flickr-uploader/wiki/FAQ2")));
				return false;
			}
		});

		findPreference("feedback").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Utils.showEmailActivity(activity, "Feedback on Flickr Uploader " + Config.VERSION_NAME, "Here are some feedback to improve this app:", true);
				return false;
			}

		});

		findPreference("github").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rafali/flickr-uploader")));
				return false;
			}
		});

		findPreference("autoupload_folder_settings").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AutoUploadFoldersActivity_.intent(activity).start();
				return false;
			}
		});

		findPreference(UPLOAD_NETWORK).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object arg1) {
				if (STR.wifionly.equals(arg1)) {
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setTitle("WARNING")
							.setMessage("This feature is not guaranteed. If you really want to make sure this app does not use your data plan, enforce it at the OS level as explained in the FAQ.")
							.setNegativeButton("Later", null).setPositiveButton("See the FAQ", new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									String url = "https://github.com/rafali/flickr-uploader/wiki/FAQ#how-to-make-sure-it-wont-use-my-precious-data-plan";
									Intent i = new Intent(Intent.ACTION_VIEW);
									i.setData(Uri.parse(url));
									startActivity(i);
								}
							});

					builder.create().show();
				}
				return true;
			}
		});

		render();
	}

	@Override
	protected void onResume() {
		loadNbSynced();
		super.onResume();
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		render();
	}

	SharedPreferences sp;

	int nbSynced = 0;

	void loadNbSynced() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					nbSynced = Utils.getFoldersMonitoredNb();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							render();
						}
					});
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			}

		});
	}

	@SuppressWarnings("deprecation")
	void render() {
		{
			List<String> values = Arrays.asList(getResources().getStringArray(R.array.network_values));
			String[] entries = getResources().getStringArray(R.array.network_entries);
			String value = sp.getString(UPLOAD_NETWORK, values.get(0));
			findPreference(UPLOAD_NETWORK).setSummary(entries[values.indexOf(value)]);
		}
		{
			String summary;
			if (Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD, false) || Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, false)) {
				if (nbSynced <= 0) {
					summary = "No folder monitored";
				} else {
					summary = nbSynced + " folder" + (nbSynced > 1 ? "s" : "") + " monitored";
				}
			} else {
				summary = "No folder monitored, auto-upload disabled";
			}

			findPreference("autoupload_folder_settings").setSummary(summary);
		}

		String privacy = sp.getString(UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString());
		findPreference(UPLOAD_PRIVACY).setSummary(PRIVACY.valueOf(privacy).getSimpleName());
		Preference login = findPreference("login");
		if (Utils.getStringProperty(STR.userId) == null) {
			login.setTitle("Sign into Flickr");
			login.setSummary("Account required for upload");
		} else {
			login.setTitle("Signed into Flickr");
			login.setSummary("Click here to sign out " + Utils.getStringProperty(STR.userName));
		}
		Preference premium = findPreference(STR.premium);
		if (Utils.isPremium()) {
			premium.setTitle("You are using the PRO version");
			premium.setSummary("Thank you!");
		} else {
			if (Utils.isTrial()) {
				premium.setTitle("PRO version usable for free");
				premium.setSummary("until " + SimpleDateFormat.getDateInstance().format(new Date(Utils.trialUntil())));
			} else {
				premium.setTitle("Your trial of the app has ended");
				premium.setSummary("Click here to buy the PRO version");
				((CheckBoxPreference) findPreference(AUTOUPLOAD)).setChecked(false);
				((CheckBoxPreference) findPreference(AUTOUPLOAD_VIDEOS)).setChecked(false);
				OnPreferenceClickListener onPreferenceClickListener = new OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						Utils.showPremiumDialog(activity, new Utils.Callback<Boolean>() {
							@Override
							public void onResult(Boolean result) {
								render();
							}
						});
						return false;
					}
				};
				findPreference(AUTOUPLOAD).setOnPreferenceClickListener(onPreferenceClickListener);
				findPreference(AUTOUPLOAD_VIDEOS).setOnPreferenceClickListener(onPreferenceClickListener);
			}
			premium.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Utils.showPremiumDialog(activity, new Utils.Callback<Boolean>() {
						@Override
						public void onResult(Boolean result) {
							render();
						}
					});
					return false;
				}
			});
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (Utils.onActivityResult(requestCode, resultCode, data)) {
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	String currentDonation;

	Handler handler;

	@Override
	protected void onDestroy() {
		sp.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}
}
