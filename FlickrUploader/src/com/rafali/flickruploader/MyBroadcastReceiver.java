package com.rafali.flickruploader;

import java.util.Arrays;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.rafali.flickruploader.FlickrApi.PRIVACY;

public class MyBroadcastReceiver extends BroadcastReceiver {
	private static final String TAG = MyBroadcastReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "intent : " + intent);
		if ("com.rafali.intent.CANCEL_UPLOAD".equals(intent.getAction())) {
			Mixpanel.track("Cancel in notification");
			Log.d(TAG, "canceling uploads");
			UploadService.cancel(true);
		} else if ("com.rafali.intent.SHARE_PHOTO".equals(intent.getAction())) {
			Mixpanel.track("Share in notification");
			Log.d(TAG, "share intent : " + intent);
			int imageId = intent.getIntExtra("imageId", -1);
			if (imageId > 0) {
				Media image = Utils.getImage(imageId);
				final String photoId = FlickrApi.getPhotoId(image);
				if (photoId != null) {
					Toast.makeText(context, "Sharing photo", Toast.LENGTH_LONG).show();
					String url = FlickrApi.getShortUrl(photoId);
					Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
					sharingIntent.setType("text/plain");
					sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, url);
					Intent createChooser = Intent.createChooser(sharingIntent, "Share via");
					createChooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(createChooser);
					Log.d(TAG, "url : " + url);
					FlickrApi.setPrivacy(PRIVACY.PUBLIC, Arrays.asList(photoId));
					NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					mNotificationManager.cancelAll();
				}
			}
		} else {
			Log.d(TAG, "action : " + intent.getAction());
			context.startService(new Intent(context, UploadService.class));
			UploadService.wake();
		}
	}
}
