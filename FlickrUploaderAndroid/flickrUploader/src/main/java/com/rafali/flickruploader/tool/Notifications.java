package com.rafali.flickruploader.tool;

import org.androidannotations.api.BackgroundExecutor;
import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.service.UploadService.BasicUploadProgressListener;
import com.rafali.flickruploader.service.UploadService.UploadProgressListener;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity_;
import com.rafali.flickruploader2.R;

public class Notifications {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Notifications.class);

	static final android.app.NotificationManager manager = (android.app.NotificationManager) FlickrUploader.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	private static PendingIntent resultPendingIntent;

	private static Notification.Builder builderUploading;
	private static Notification.Builder builderUploaded;

	static long lastNotified = 0;

	private static UploadProgressListener uploadProgressListener = new BasicUploadProgressListener() {

		@Override
		public void onProgress(Media media) {
			Notifications.notifyProgress(media);
		};

		@Override
		public void onFinished(int nbUploaded, int nbError) {
			Notifications.notifyFinished(nbUploaded, nbError);
		}

	};

	public static void init() {
		UploadService.register(uploadProgressListener);
	}

	private static void ensureBuilders() {
		if (resultPendingIntent == null) {
			Intent resultIntent = new Intent(FlickrUploader.getAppContext(), FlickrUploaderActivity_.class);
			resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			resultIntent.setAction(Intent.ACTION_MAIN);
			resultPendingIntent = PendingIntent.getActivity(FlickrUploader.getAppContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		if (builderUploading == null) {
			builderUploading = new Notification.Builder(FlickrUploader.getAppContext());
			builderUploading.setContentIntent(resultPendingIntent);
			builderUploading.setContentTitle("Uploading to Flickr");
			builderUploading.setPriority(Notification.PRIORITY_MIN);
			builderUploading.setSmallIcon(R.drawable.ic_launcher);

			builderUploaded = new Notification.Builder(FlickrUploader.getAppContext());
			builderUploaded.setSmallIcon(R.drawable.ic_launcher);
			builderUploaded.setPriority(Notification.PRIORITY_MIN);
			builderUploaded.setContentIntent(resultPendingIntent);
			// builderUploaded.setProgress(1000, 1000, false);
			builderUploaded.setTicker("Upload finished");
			builderUploaded.setContentTitle("Upload finished");
			builderUploaded.setAutoCancel(true);

		}
	}

	static long lastUpdate = 0;

	public static void notifyProgress(final Media media) {
		try {
			if (!Utils.getBooleanProperty("notification_progress", true)) {
				return;
			}

			if (System.currentTimeMillis() - lastUpdate < 1000) {
				return;
			}

			lastUpdate = System.currentTimeMillis();

			int currentPosition = UploadService.getRecentlyUploaded().size();
			int total = UploadService.getNbTotal();
			int progress = media.getProgress();

			ensureBuilders();

			Notification.Builder builder = builderUploading;
			builder.setProgress(1000, progress, false);
			builder.setContentText(media.getName());
			builder.setContentInfo(currentPosition + " / " + total);

			CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
			if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap bitmap = Utils.getBitmap(media, VIEW_SIZE.small);
						if (bitmap != null) {
							Utils.getCache().put(media.getPath() + "_" + VIEW_SIZE.small, bitmap);
						}
					}
				});
			} else {
				builder.setLargeIcon(bitmapDrawable.getBitmap());
			}

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
			if (FlickrUploaderActivity.getInstance() == null || FlickrUploaderActivity.getInstance().isPaused()) {

				if (!Utils.getBooleanProperty("notification_finished", true)) {
					return;
				}

				ensureBuilders();

				Notification.Builder builder = builderUploaded;
				String text = nbUploaded + " media recently uploaded to Flickr";
				if (nbError > 0) {
					text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
				}
				builder.setContentText(text);

				Notification notification = builder.build();
				notification.icon = android.R.drawable.stat_sys_upload_done;
				// notification.iconLevel = progress / 10;
				manager.notify(0, notification);
			}
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

					Notification.Builder builder = new Notification.Builder(FlickrUploader.getAppContext());
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
