package com.rafali.flickruploader.broadcast;

import org.slf4j.LoggerFactory;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.tool.Utils;

public class MyBroadcastReceiver extends BroadcastReceiver {
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MyBroadcastReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		LOG.info("intent : " + intent);
		if ("com.rafali.intent.SHARE_PHOTO".equals(intent.getAction())) {
//			LOG.debug("share intent : " + intent);
//			int imageId = intent.getIntExtra("imageId", -1);
//			if (imageId > 0) {
//				Media media = Utils.getImage(imageId);
//				final String photoId = media.getFlickrId();
//				if (photoId != null) {
//					Utils.toast("Sharing photo");
//					String url = FlickrApi.getShortUrl(photoId);
//					Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
//					sharingIntent.setType("text/plain");
//					sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, url);
//					Intent createChooser = Intent.createChooser(sharingIntent, "Share via");
//					createChooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//					context.startActivity(createChooser);
//					LOG.debug("url : " + url);
//					media.setPrivacy(PRIVACY.PUBLIC);
//					media.save();
//					NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//					mNotificationManager.cancelAll();
//				}
//			}
		} else {
			LOG.debug("action : " + intent.getAction());
			UploadService.wake();
		}
	}
}
