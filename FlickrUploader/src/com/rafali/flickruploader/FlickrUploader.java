package com.rafali.flickruploader;

import static org.acra.ReportField.ANDROID_VERSION;
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
import java.nio.charset.Charset;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.Migration;
import se.emilsjolander.sprinkles.Sprinkles;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

@ReportsCrashes(formUri = "http://ra-fa-li.appspot.com/androidCrashReport", formKey = "", mode = ReportingInteractionMode.TOAST, forceCloseDialogAfterToast = false, resToastText = R.string.crash_toast_text, customReportContent = {
		REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME, PHONE_MODEL, ANDROID_VERSION, BUILD, BRAND, PRODUCT, TOTAL_MEM_SIZE, AVAILABLE_MEM_SIZE, STACK_TRACE, USER_APP_START_DATE, USER_CRASH_DATE,
		DEVICE_FEATURES, ENVIRONMENT, SETTINGS_SYSTEM, SETTINGS_SECURE, THREAD_DETAILS })
public class FlickrUploader extends Application {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploader.class);

	private static Context context;

	@Override
	public void onCreate() {
		super.onCreate();
		FlickrUploader.context = getApplicationContext();
		getHandler();

		try {
			Sprinkles sprinkles = Sprinkles.init(getApplicationContext());
			Migration initialMigration = new Migration();
			initialMigration.createTable(Media.class);
			sprinkles.addMigration(initialMigration);
		} catch (Throwable e) {
			Log.e("Flickr Uploader", e.getMessage(), e);
		}

		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					initLogs();
					ACRA.init(FlickrUploader.this);
					long versionCode = Utils.getLongProperty(STR.versionCode);
					if (Config.VERSION != versionCode) {
						if (versionCode == 0) {
							Utils.setLongProperty(STR.lastNewFilesCheckNotEmpty, System.currentTimeMillis());

						} else {
						}
						Utils.saveAndroidDevice();
						Utils.setLongProperty(STR.versionCode, (long) Config.VERSION);
					}
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			}
		});
	}

	public static Context getAppContext() {
		return context;
	}

	private static Handler handler;

	public static Handler getHandler() {
		if (handler == null) {
			handler = new Handler();
		}
		return handler;
	}

	public static String getLogFilePath() {
		return context.getFilesDir().getPath() + "/logs/flickruploader.log";
	}

	private static void initLogs() {
		Logger logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		LoggerContext lc = logbackLogger.getLoggerContext();

		Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.detachAndStopAllAppenders();

		TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
		rollingPolicy.setMaxHistory(3);
		SizeAndTimeBasedFNATP<ILoggingEvent> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<ILoggingEvent>();
		sizeAndTimeBasedFNATP.setMaxFileSize("2MB");
		rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(sizeAndTimeBasedFNATP);
		rollingPolicy.setFileNamePattern(context.getFilesDir().getPath() + "/logs/old/flickruploader.%d{yyyy-MM-dd}.%i.log");
		rollingPolicy.setContext(lc);

		RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
		fileAppender.setContext(lc);
		fileAppender.setFile(getLogFilePath());
		fileAppender.setRollingPolicy(rollingPolicy);
		fileAppender.setTriggeringPolicy(rollingPolicy);
		rollingPolicy.setParent(fileAppender);

		PatternLayoutEncoder pl = new PatternLayoutEncoder();
		pl.setContext(lc);
		pl.setCharset(Charset.defaultCharset());
		pl.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %class{0}.%method:%L > %msg%n");
		pl.setImmediateFlush(false);
		pl.start();

		fileAppender.setEncoder(pl);
		fileAppender.setName("file");

		rollingPolicy.start();
		fileAppender.start();

		if (Config.isDebug()) {
			final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
			logcatTagPattern.setContext(lc);
			logcatTagPattern.setPattern("%class{0}");
			logcatTagPattern.start();

			final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
			logcatPattern.setContext(lc);
			logcatPattern.setPattern("[%thread] %method:%L > %msg%n");
			logcatPattern.start();

			final LogcatAppender logcatAppender = new LogcatAppender();
			logcatAppender.setContext(lc);
			logcatAppender.setTagEncoder(logcatTagPattern);
			logcatAppender.setEncoder(logcatPattern);
			logcatAppender.start();

			rootLogger.addAppender(logcatAppender);
		}

		rootLogger.addAppender(fileAppender);

	}

	public static void flushLogs() {
		try {
			Logger logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			LoggerContext lc = logbackLogger.getLoggerContext();
			Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
			Appender<ILoggingEvent> appender = rootLogger.getAppender("file");
			appender.stop();
			appender.start();
		} catch (Throwable e) {
			Log.e("Flickr Uploader", e.getMessage(), e);
		}
	}

	public static void cleanLogs() {
		try {
			String path = context.getFilesDir().getPath() + "/logs/old";
			File folder = new File(path);
			if (folder.exists() && folder.isDirectory()) {
				File[] listFiles = folder.listFiles();
				if (listFiles != null) {
					for (File file : listFiles) {
						try {
							if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L)
								file.delete();
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
				}
			}
		} catch (Throwable e) {
			Log.e("Flickr Uploader", e.getMessage(), e);
		}
	}

	public static long getLogSize() {
		flushLogs();
		return Utils.getFileSize(new File(context.getFilesDir().getPath() + "/logs/"));
	}

	public static void deleteAllLogs() {
		Utils.deleteFiles(new File(context.getFilesDir().getPath() + "/logs/"));
		initLogs();
	}

}
