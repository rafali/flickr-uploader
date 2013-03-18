package com.rafali.flickruploader;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;

import com.googlecode.androidannotations.api.BackgroundExecutor;

public class Notifications {

	static final android.app.NotificationManager manager = (android.app.NotificationManager) FlickrUploader.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
	private static PendingIntent resultPendingIntent;
	private static PendingIntent cancelIntent;

	private static Builder builderUploading;
	private static Builder builderUploaded;

	public static void notify(int progress, final Image image, int currentPosition, int total) {
		if (resultPendingIntent == null) {
			Intent resultIntent = new Intent(FlickrUploader.getAppContext(), FlickrUploaderActivity_.class);
			resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			resultIntent.setAction(Intent.ACTION_MAIN);
			resultPendingIntent = PendingIntent.getActivity(FlickrUploader.getAppContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			cancelIntent = PendingIntent.getBroadcast(FlickrUploader.getAppContext(), 0, new Intent("com.rafali.intent.CANCEL_UPLOAD"), 0);
		}

		if (builderUploading == null) {
			builderUploading = new NotificationCompat.Builder(FlickrUploader.getAppContext());
			builderUploading.setContentIntent(resultPendingIntent);
			builderUploading.setTicker("Uploading");
			builderUploading.setContentTitle("Uploading to Flickr");
			builderUploading.addAction(R.drawable.navigation_cancel, "Cancel", cancelIntent);
			builderUploading.setOngoing(true);
			builderUploading.setPriority(NotificationCompat.PRIORITY_MIN);
			builderUploading.setSmallIcon(R.drawable.ic_launcher);

			builderUploaded = new NotificationCompat.Builder(FlickrUploader.getAppContext());
			builderUploaded.setSmallIcon(R.drawable.ic_launcher);
			builderUploaded.setPriority(NotificationCompat.PRIORITY_MIN);
			builderUploaded.setContentIntent(resultPendingIntent);
			builderUploaded.setProgress(1000, 1000, false);
			builderUploaded.setTicker("Upload finished");
			builderUploaded.setContentTitle("Upload finished");
			builderUploaded.setAutoCancel(true);
		}
		// Log.d("Notifications", "realProgress : " + realProgress + ", progress:" + progress + ", currentPosition:" + currentPosition + ", total:" + total);

		int realProgress = (int) (100 * (currentPosition - 1 + Double.valueOf(progress) / 100) / total);
		boolean uploading = realProgress < 100;

		Builder builder;
		if (uploading) {
			builder = builderUploading;
			builder.setProgress(100, realProgress, false);
			builder.setContentText(image.name);
			builder.setContentInfo(currentPosition + " / " + total);
		} else {
			builder = builderUploaded;
			builder.setContentText(total + " photo" + (total > 1 ? "s" : "") + " sent to Flickr");
		}

		CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(image.path + "_" + R.layout.photo_grid_thumb);
		if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					final Bitmap bitmap = Utils.getBitmap(image, R.layout.photo_grid_thumb);
					if (bitmap != null) {
						Utils.getCache().put(image.path + "_" + R.layout.photo_grid_thumb, bitmap);
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

	}

	public static void clear() {
		manager.cancelAll();
	}
}