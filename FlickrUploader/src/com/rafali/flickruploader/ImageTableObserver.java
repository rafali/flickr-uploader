package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.provider.MediaStore;

import com.rafali.flickruploader.FlickrUploaderActivity.TAB;
import com.rafali.flickruploader.Utils.MediaType;

public class ImageTableObserver extends ContentObserver {

	private static final String LAST_CHANGE = "lastChange";
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ImageTableObserver.class);

	public ImageTableObserver() {
		super(new Handler());
		lastChange = Utils.getLongProperty(LAST_CHANGE);
		if (lastChange == 0)
			lastChange = System.currentTimeMillis();
	}

	long lastChange = 0;

	@Override
	public void onChange(boolean change) {
		try {
			if (!Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true) && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
				LOG.debug("autoupload disabled");
				return;
			}
			if (!FlickrApi.isAuthentified()) {
				LOG.debug("Flickr not authentified yet");
				return;
			}

			String filter = MediaStore.Images.Media.DATE_ADDED + " > " + (lastChange / 1000);
			List<Media> media = Utils.loadImages(filter);
			lastChange = System.currentTimeMillis();
			Utils.setLongProperty(LAST_CHANGE, lastChange);
			if (media == null || media.isEmpty()) {
				LOG.debug("no new image since " + filter);
				return;
			}

			List<Media> not_uploaded = new ArrayList<Media>();
			for (Media image : media) {
				if (image.mediaType == MediaType.photo && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true)) {
					continue;
				} else if (image.mediaType == MediaType.video && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
					continue;
				} else {
					boolean uploaded = FlickrApi.isUploaded(image);
					LOG.debug("uploaded : " + uploaded + ", " + image);
					if (!uploaded) {
						File file = new File(image.path);
						if (!Utils.isSynced(new Folder(file.getParent()))) {
							LOG.debug("Ignored : " + file);
						} else {
							int sleep = 0;
							while (file.length() < 100 && sleep < 5) {
								LOG.debug("sleeping a bit");
								sleep++;
								Thread.sleep(1000);
							}
							not_uploaded.add(image);
							final Bitmap bitmap = Utils.getBitmap(image, TAB.photo);
							if (bitmap != null) {
								Utils.getCache().put(image.path + "_" + R.layout.photo_grid_thumb, bitmap);
							}
						}
					}
				}
				if (!not_uploaded.isEmpty()) {
					FlickrUploaderActivity.staticRefresh(true);
					UploadService.enqueue(not_uploaded, Utils.getInstantAlbumId(), STR.instantUpload);
				}
			}
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
