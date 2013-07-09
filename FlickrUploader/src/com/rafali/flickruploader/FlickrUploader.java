package com.rafali.flickruploader;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APPLICATION_LOG;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.AVAILABLE_MEM_SIZE;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.BUILD;
import static org.acra.ReportField.DEVICE_FEATURES;
import static org.acra.ReportField.ENVIRONMENT;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.SETTINGS_SECURE;
import static org.acra.ReportField.SETTINGS_SYSTEM;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.THREAD_DETAILS;
import static org.acra.ReportField.TOTAL_MEM_SIZE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;

import java.io.File;
import java.util.Date;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.slf4j.LoggerFactory;

import android.app.Application;
import android.content.Context;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import com.googlecode.androidannotations.api.BackgroundExecutor;

@ReportsCrashes(formUri = "http://ra-fa-li.appspot.com/androidCrashReport", formKey = "", mode = ReportingInteractionMode.TOAST, forceCloseDialogAfterToast = false, // optional, default false
resToastText = R.string.crash_toast_text, customReportContent = { REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME, PHONE_MODEL, ANDROID_VERSION, BUILD, BRAND, PRODUCT, TOTAL_MEM_SIZE,
		AVAILABLE_MEM_SIZE, STACK_TRACE, USER_APP_START_DATE, USER_CRASH_DATE, DEVICE_FEATURES, ENVIRONMENT, SETTINGS_SYSTEM, SETTINGS_SECURE, THREAD_DETAILS, APPLICATION_LOG })
public class FlickrUploader extends Application {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploader.class);

	private static Context context;

	@Override
	public void onCreate() {
		super.onCreate();
		FlickrUploader.context = getApplicationContext();
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					initLogs();
					ACRA.init(FlickrUploader.this);
					ACRA.getConfig().setApplicationLogFile(Utils.getLogFile().getAbsolutePath());
					long versionCode = Utils.getLongProperty(STR.versionCode);
					if (Config.VERSION != versionCode) {
						if (versionCode == 0) {
							Mixpanel.track("First install");
						}
						Utils.saveAndroidDevice();
						Utils.setLongProperty(STR.versionCode, (long) Config.VERSION);
					}
					long firstInstallTime = context.getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime;
					LOG.info("firstInstallTime : " + new Date(firstInstallTime));
				} catch (Throwable e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
	}

	public static Context getAppContext() {
		return context;
	}

	private static synchronized void initLogs() {
		try {
			File logFile = Utils.getLogFile();
			ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			RollingFileAppender<ILoggingEvent> appender = (RollingFileAppender<ILoggingEvent>) root.getAppender("file");
			appender.setFile(logFile.getAbsolutePath());
			@SuppressWarnings("unchecked")
			TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = (TimeBasedRollingPolicy<ILoggingEvent>) appender.getRollingPolicy();
			rollingPolicy.setFileNamePattern(logFile.getParent() + "/log/flickruploader.%d{yyyy-MM-dd}.%i.log");
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static void deleteOldLogs() {
		try {
			File logFile = Utils.getLogFile();
			File logDir = new File(logFile.getParent(), "log");
			if (logDir.exists() && logDir.isDirectory()) {
				for (File file : logDir.listFiles()) {
					file.delete();
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
