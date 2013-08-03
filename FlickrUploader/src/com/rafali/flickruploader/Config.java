package com.rafali.flickruploader;

import org.slf4j.LoggerFactory;

import android.content.pm.ApplicationInfo;

public class Config {
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Config.class);

	public static final int VERSION = getVersion();
	public static final long MAX_FILE_SIZE = 200 * 1024 * 1024L;
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
			LOG.error(e.getMessage(), e);
		}
		return 0;
	}

	private static String getVersionName() {
		try {
			return "" + FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).versionName;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return "0";
	}
}
