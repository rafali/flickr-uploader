package com.rafali.flickruploader.tool;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity_;
import com.rafali.flickruploader2.R;

public class Notifications {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Notifications.class);

	static final android.app.NotificationManager manager = (android.app.NotificationManager) FlickrUploader.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	private static PendingIntent resultPendingIntent;

	private static Builder builderUploading;
	private static Builder builderUploaded;

	static long lastNotified = 0;

	private static void ensureBuilders() {
		if (resultPendingIntent == null) {
			Intent resultIntent = new Intent(FlickrUploader.getAppContext(), FlickrUploaderActivity_.class);
			resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			resultIntent.setAction(Intent.ACTION_MAIN);
			resultPendingIntent = PendingIntent.getActivity(FlickrUploader.getAppContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		if (builderUploading == null) {
			builderUploading = new NotificationCompat.Builder(FlickrUploader.getAppContext());
			builderUploading.setContentIntent(resultPendingIntent);
			builderUploading.setContentTitle("Uploading to Flickr");
			builderUploading.setPriority(NotificationCompat.PRIORITY_MIN);
			builderUploading.setSmallIcon(R.drawable.ic_launcher);

			builderUploaded = new NotificationCompat.Builder(FlickrUploader.getAppContext());
			builderUploaded.setSmallIcon(R.drawable.ic_launcher);
			builderUploaded.setPriority(NotificationCompat.PRIORITY_MIN);
			builderUploaded.setContentIntent(resultPendingIntent);
			// builderUploaded.setProgress(1000, 1000, false);
			builderUploaded.setTicker("Upload finished");
			builderUploaded.setContentTitle("Upload finished");
			builderUploaded.setAutoCancel(true);

		}
	}

	public static void notify(int progress, final Media media, int currentPosition, int total) {
		try {
			if (!Utils.getBooleanProperty("notification_progress", true)) {
				return;
			}

			ensureBuilders();

			int realProgress = (int) (100 * (currentPosition - 1 + Double.valueOf(progress) / 100) / total);

			Builder builder = builderUploading;
			builder.setProgress(100, realProgress, false);
			builder.setContentText(media.getName());
			builder.setContentInfo(currentPosition + " / " + total);

			CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
			if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap bitmap = Utils.getBitmap(media, VIEW_SIZE.small);
						if (bitmap != null) {
							Utils.getCache().put(media.getPath() + "_" + R.layout.grid_thumb, bitmap);
						}
					}
				});
			} else {
				builder.setLargeIcon(bitmapDrawable.getBitmap());
			}

			builder.setOngoing(realProgress < 95);

			Notification notification = builder.build();
			notification.icon = android.R.drawable.stat_sys_upload_done;
			// notification.iconLevel = progress / 10;
			manager.notify(0, notification);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}

	}

	public static void notifyFinished(int nbUploaded, int nbError) {
		try {
			manager.cancelAll();

			if (!Utils.getBooleanProperty("notification_finished", true)) {
				return;
			}

			ensureBuilders();

			Builder builder = builderUploaded;
			String text = nbUploaded + " media sent to Flickr";
			if (nbError > 0) {
				text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
			}
			builder.setContentText(text);

			Notification notification = builder.build();
			notification.icon = android.R.drawable.stat_sys_upload_done;
			// notification.iconLevel = progress / 10;
			manager.notify(0, notification);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}

	}

	public static void notifyTrialEnded() {
		try {

			if (!Utils.isPremium() && !Utils.isTrial() && Utils.getBooleanProperty(STR.end_of_trial, true)) {
				long lastTrialEndedNotifications = Utils.getLongProperty(STR.lastTrialEndedNotifications);
				if (System.currentTimeMillis() - lastTrialEndedNotifications > 3 * 24 * 3600 * 1000L) {
					ensureBuilders();

					Builder builder = new NotificationCompat.Builder(FlickrUploader.getAppContext());
					builder.setSmallIcon(R.drawable.ic_launcher);
					builder.setContentIntent(resultPendingIntent);
					builder.setTicker("Flickr Uploader trial ended");
					builder.setContentTitle("Flickr Uploader trial ended");
					builder.setAutoCancel(true);
					builder.setContentText("Auto-upload is no longer activated");

					Notification notification = builder.build();
					notification.icon = android.R.drawable.stat_sys_upload_done;
					// notification.iconLevel = progress / 10;
					manager.notify(0, notification);

					Utils.setLongProperty(STR.lastTrialEndedNotifications, System.currentTimeMillis());
				}
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}

	}

	public static void clear() {
		manager.cancelAll();
	}
}
