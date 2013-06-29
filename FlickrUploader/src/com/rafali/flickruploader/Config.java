package com.rafali.flickruploader;

import android.content.pm.ApplicationInfo;

public class Config {
	private static final String TAG = Config.class.getSimpleName();

	public static final int VERSION = getVersion();
	public static final String VERSION_NAME = getVersionName();
	public static final String FULL_VERSION_NAME = VERSION_NAME + "-" + VERSION;

	private static Boolean DEBUG = null;

	public static boolean isDebug() {
		if (DEBUG == null) {
			DEBUG = (FlickrUploader.getAppContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		}
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
