package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import android.database.ContentObserver;
import android.os.Handler;

import com.rafali.flickruploader.Utils.MediaType;

public class ImageTableObserver extends ContentObserver {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ImageTableObserver.class);

	public ImageTableObserver() {
		super(new Handler());
	}

	@Override
	public void onChange(boolean change) {
		try {
			if (!Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true) && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
				LOG.debug("autoupload disabled");
				return;
			}
			if (!Utils.isPremium() && !Utils.isTrial()) {
				LOG.debug("no autoupload, trial has ended");
				return;
			}

			if (!FlickrApi.isAuthentified()) {
				LOG.debug("Flickr not authentified yet");
				return;
			}

			List<Media> media = Utils.loadImages(null, 10);
			if (media == null || media.isEmpty()) {
				LOG.debug("no media found");
				return;
			}

			List<Media> not_uploaded = new ArrayList<Media>();
			for (Media image : media.subList(0, Math.min(10, media.size()))) {
				if (image.mediaType == MediaType.photo && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true)) {
					LOG.debug("not uploading " + media + " because photo upload disabled");
					continue;
				} else if (image.mediaType == MediaType.video && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
					LOG.debug("not uploading " + media + " because video upload disabled");
					continue;
				} else {
					boolean uploaded = FlickrApi.isUploaded(image);
					LOG.debug("uploaded : " + uploaded + ", " + image);
					if (!uploaded) {
						File file = new File(image.path);
						if (!Utils.isAutoUpload(new Folder(file.getParent()))) {
							LOG.debug("Ignored : " + file);
						} else {
							int sleep = 0;
							while (file.length() < 100 && sleep < 5) {
								LOG.debug("sleeping a bit");
								sleep++;
								Thread.sleep(1000);
							}
							not_uploaded.add(image);
						}
					}
				}
			}
			if (!not_uploaded.isEmpty()) {
				LOG.debug("enqueuing " + not_uploaded.size() + " media: " + not_uploaded);
				UploadService.enqueue(true, not_uploaded, null, Utils.getInstantAlbumId(), STR.instantUpload);
				FlickrUploaderActivity.staticRefresh(true);
			}
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
