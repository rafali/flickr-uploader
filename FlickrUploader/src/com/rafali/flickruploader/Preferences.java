package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.Log;
import com.google.common.collect.Lists;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.billing.IabHelper;
import com.rafali.flickruploader.billing.IabHelper.OnConsumeFinishedListener;
import com.rafali.flickruploader.billing.IabHelper.OnIabPurchaseFinishedListener;
import com.rafali.flickruploader.billing.IabResult;
import com.rafali.flickruploader.billing.Purchase;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Preferences.class);
	private static final String AUTOUPLOAD_PHOTOSET = "autoupload_photoset";
	public static final String UPLOAD_NETWORK = "upload_network";
	public static final String UPLOAD_PRIVACY = "upload_privacy";
	public static final String AUTOUPLOAD = "autoupload";
	public static final String AUTOUPLOAD_VIDEOS = "autouploadvideos";
	public static final String CHARGING_ONLY = "charging_only";

	List<String> donations = Lists.newArrayList("S. Korpinen", "J. R. Whitehead", "J. Harvey", "R. Balanza", "I. A. Mendoza", "F. Jumayao", "O. M. Figueras", "R. Raghavn", "D. Riley");

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		handler = new Handler();
		Collections.shuffle(donations);
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		// add preferences from xml
		addPreferencesFromResource(R.xml.preferences);
		sp.registerOnSharedPreferenceChangeListener(this);
		findPreference("login").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (Utils.getStringProperty(STR.userId) != null) {
					new AlertDialog.Builder(Preferences.this).setIcon(android.R.drawable.ic_dialog_alert).setTitle("Sign out").setMessage("Confirm signing out. Uploads will be disabled.")
							.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Mixpanel.track("Sign out");
									Editor editor = sp.edit();
									editor.remove(STR.userId);
									editor.remove(STR.accessToken);
									editor.remove(STR.accessTokenSecret);
									editor.remove(STR.userDateCreated);
									editor.remove(STR.userName);
									editor.remove(STR.uploadedPhotos);
									editor.remove(STR.instantAlbumId);
									editor.remove(STR.instantCustomAlbumId);
									editor.remove(STR.instantCustomAlbumTitle);
									editor.remove(Preferences.AUTOUPLOAD_PHOTOSET);
									editor.apply();
									editor.commit();
									render();
									FlickrApi.reset();
									UploadService.cancel(false);
								}
							}).setNegativeButton("Cancel", null).show();
				} else {
					WebAuth_.intent(Preferences.this).start();
				}
				return false;
			}
		});
		ListPreference privacyPreference = (ListPreference) findPreference(UPLOAD_PRIVACY);
		PRIVACY[] privacies = FlickrApi.PRIVACY.values();
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
				Preferences.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.rafali.flickruploader")));
				Mixpanel.track("Rate");
				Utils.setBooleanProperty(STR.hasRated, true);
				return false;
			}
		});

		findPreference("notifications").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(Preferences.this, PreferencesNotification.class));
				return false;
			}
		});

		findPreference("donation").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showDonation();
				return false;
			}
		});

		findPreference("userdonations").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showDonation();
				return false;
			}
		});

		findPreference("pictarine").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Mixpanel.track("Pictarine");
				Preferences.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pictarine.android")));
				return false;
			}
		});

		findPreference("feedback").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Mixpanel.track("Feedback");
				showEmailActivity("Feedback on Flickr Instant Upload", "Here are some feedback to improve this app:", true);
				return false;
			}

		});

		findPreference(AUTOUPLOAD_PHOTOSET).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Log.d("newValue : " + newValue);
				if ("custom".equals(newValue)) {
					if (ToolString.isBlank(Utils.getStringProperty(STR.userId))) {
						Toast.makeText(Preferences.this, "You need to login to select your photoset", Toast.LENGTH_LONG).show();
					} else {
						final ProgressDialog dialog = ProgressDialog.show(Preferences.this, "", "Loading photosets", true);
						BackgroundExecutor.execute(new Runnable() {
							@Override
							public void run() {
								final Map<String, String> photosets = FlickrApi.getPhotoSets();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										dialog.cancel();
										if (photosets.isEmpty()) {
											Toast.makeText(Preferences.this, "No photoset found", Toast.LENGTH_LONG).show();
										} else {
											AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
											final List<String> photosetTitles = new ArrayList<String>();
											final List<String> photosetIds = new ArrayList<String>();
											for (String photosetId : photosets.keySet()) {
												photosetIds.add(photosetId);
												photosetTitles.add(photosets.get(photosetId));
											}
											String[] photosetTitlesArray = photosetTitles.toArray(new String[photosetTitles.size()]);
											builder.setItems(photosetTitlesArray, new OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													Log.d("selected : " + photosetIds.get(which) + " - " + photosetTitles.get(which));
													Utils.setStringProperty(STR.instantCustomAlbumId, photosetIds.get(which));
													Utils.setStringProperty(STR.instantCustomAlbumTitle, photosetTitles.get(which));
													Utils.setStringProperty(AUTOUPLOAD_PHOTOSET, "custom");
													((ListPreference) findPreference(AUTOUPLOAD_PHOTOSET)).setValue("custom");
												}
											});
											builder.show();
										}
									}
								});
							}
						});
					}
					return false;
				} else {
					Utils.clearProperty(STR.instantCustomAlbumId);
					Utils.clearProperty(STR.instantCustomAlbumTitle);
				}
				return true;
			}
		});

		render();

		renderDonation();

	}

	void loadDonations() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				List<String> users = Utils.getDonationUsers();
				for (String name : users) {
					if (!donations.contains(name)) {
						donations.add(name);
					}
				}
			}
		});
	}

	private void showEmailActivity(String subject, String message, boolean attachLogs) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/email");
		intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "flickruploader@rafali.com" });
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, message);

		if (attachLogs) {
			File log = Utils.getLogFile();
			if (log.exists()) {
				File publicDownloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
				File publicLog = new File(publicDownloadDirectory, "flickruploader_log.txt");
				Utils.copyFile(log, publicLog);
				Uri uri = Uri.fromFile(publicLog);
				intent.putExtra(Intent.EXTRA_STREAM, uri);
			} else {
				LOG.warn(log + " does not exist");
			}
		}
		final List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, 0);

		ResolveInfo gmailResolveInfo = null;
		for (ResolveInfo resolveInfo : resInfoList) {
			if ("com.google.android.gm".equals(resolveInfo.activityInfo.packageName)) {
				gmailResolveInfo = resolveInfo;
				break;
			}
		}

		if (gmailResolveInfo != null) {
			intent.setClassName(gmailResolveInfo.activityInfo.packageName, gmailResolveInfo.activityInfo.name);
			startActivity(intent);
		} else {
			startActivity(Intent.createChooser(intent, "Send Feedback:"));
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		Mixpanel.track("Preference Change", key, sp.getAll().get(key));
		render();
	}

	SharedPreferences sp;

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
			if (Utils.getStringProperty(STR.instantCustomAlbumId) == null) {
				summary = "Default";
			} else {
				summary = Utils.getStringProperty(STR.instantCustomAlbumTitle);
			}
			findPreference(AUTOUPLOAD_PHOTOSET).setSummary(summary);
		}

		String privacy = sp.getString(UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString());
		findPreference(UPLOAD_PRIVACY).setSummary(PRIVACY.valueOf(privacy).getSimpleName());
		Preference login = findPreference("login");
		if (Utils.getStringProperty(STR.userId) == null) {
			login.setTitle("Sign into Flickr");
			login.setSummary("Account required for upload");
		} else {
			login.setTitle("Sign out");
			login.setSummary(Utils.getStringProperty(STR.userName) + " is currently logged in");
		}
	}

	String currentDonation;

	Handler handler;

	Random random = new Random();

	@SuppressWarnings("deprecation")
	void renderDonation() {
		try {
			String message;
			if (currentDonation == null) {
				currentDonation = donations.get(0);
				message = donationMessage[0];
			} else {
				int index = donations.indexOf(currentDonation);
				if (index + 1 >= donations.size()) {
					currentDonation = donations.get(0);
				} else {
					currentDonation = donations.get(index + 1);
				}
				message = donationMessage[random.nextInt(donationMessage.length)];
			}
			Preference userdonations = findPreference("userdonations");
			userdonations.setTitle("Thanks " + currentDonation);
			userdonations.setSummary(message);
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!destroyed) {
						renderDonation();
					}
				}
			}, 10000);
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}

	String[] donationMessage = new String[] { "for your generous donation!", "for your support!", "for the coffee!", "for the beer!", "for beeing awesome!" };
	private boolean destroyed = false;

	@Override
	protected void onDestroy() {
		sp.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();

		destroyed = true;
	}

	private void showDonation() {
		Mixpanel.track("Donation");

		AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
		String[] choices = new String[] { "a coffee ($2)", "a nicer coffee ($3)", "a beer! ($5)" };
		final String[] choiceValuess = new String[] { "flickruploader.donation.2", "flickruploader.donation.3", "flickruploader.donation.5" };
		builder.setTitle("This app is free and opensource. Thank the developer with").setItems(choices, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// startActivity(new Intent(Preferences.this, DonationsActivity.class));
				final String inappKey = choiceValuess[which];
				final IabHelper mHelper = new IabHelper(Preferences.this, Utils.getString(R.string.google_play_billing_key));
				final OnIabPurchaseFinishedListener mPurchaseFinishedListener = new OnIabPurchaseFinishedListener() {
					@Override
					public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
						try {
							LOG.debug("result : " + result + ", purchase:" + purchase);
							if (result.isFailure()) {
								Toast.makeText(Preferences.this, "Next time maybe ;)", Toast.LENGTH_LONG).show();
								return;
							}
							Mixpanel.track("DonationSuccess");
							thankYou();
							Utils.sendMail("[FlickrUploader] Donation " + inappKey,
									Utils.getDeviceId() + " - " + Utils.getEmail() + " - " + Utils.getStringProperty(STR.userId) + " - " + Utils.getStringProperty(STR.userName));
							mHelper.consumeAsync(purchase, new OnConsumeFinishedListener() {
								@Override
								public void onConsumeFinished(Purchase purchase, IabResult result) {
									LOG.info("Donation success");
								}
							});
						} catch (Throwable e) {
							LOG.error(e.getMessage(), e);
						}
					}
				};
				// enable debug logging (for a production application, you should set this to false).
				mHelper.enableDebugLogging(Config.isDebug());

				// Start setup. This is asynchronous and the specified listener
				// will be called once setup completes.
				LOG.debug("Starting setup.");
				mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
					public void onIabSetupFinished(IabResult result) {
						LOG.debug("Setup finished. : " + result);
						if (result.isSuccess()) {
							mHelper.launchPurchaseFlow(Preferences.this, inappKey, 1231, mPurchaseFinishedListener, "");
						}
					}
				});
			}
		});
		builder.create().show();
	}

	void thankYou() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Mixpanel.track("ThankYou");
				AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
				builder.setMessage("Thank you for your support!\n\nIt feels really good to know you appreciate my work ;)\n\nMaxime");
				builder.setPositiveButton("Reply", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showEmailActivity("Greetings!", "Hey Maxime,\n", false);
					}
				});
				builder.setNegativeButton("OK", null);
				// Create the AlertDialog object and return it
				builder.create().show();
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}
}
