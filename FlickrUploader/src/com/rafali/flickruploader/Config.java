package com.rafali.flickruploader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;

public class Config {
	private static final String TAG = Config.class.getSimpleName();

	public static final int VERSION = getVersion();
	public static final String VERSION_NAME = getVersionName();
	public static final String FULL_VERSION_NAME = VERSION_NAME + "-" + VERSION;

	private static Properties properties;

	public static String getProperty(String property) {
		ensureProperties();
		String value = properties.getProperty(property);
		if (value == null)
			Logger.w(TAG, "no property found for " + property);
		return value;
	}

	private static void ensureProperties() {
		if (properties == null) {
			AssetManager assetManager = FlickrUploader.getAppContext().getResources().getAssets();

			// Read from the /assets directory
			try {
				String configFile = "config.properties";
				InputStream inputStream = assetManager.open(configFile);
				properties = new Properties();
				properties.load(inputStream);
				Logger.d(TAG, "The properties are now loaded : " + configFile);
				Logger.d(TAG, "properties: " + properties);
			} catch (IOException e) {
				Logger.e(TAG, e, "failed to load config.properties");
			}
		}
	}

	private static Boolean DEBUG = null;

	public static boolean isDebug() {
		if (DEBUG == null) {
			DEBUG = (FlickrUploader.getAppContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		}
		if (DEBUG == null)
			DEBUG = false;
		return DEBUG;
	}

	private static int getVersion() {
		try {
			return FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).versionCode;
		} catch (Exception e) {
			Logger.e(TAG, e);
		}
		return 0;
	}

	private static String getVersionName() {
		try {
			return "" + FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).versionName;
		} catch (Exception e) {
			Logger.e(TAG, e);
		}
		return "0";
	}
}
