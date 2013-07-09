package com.rafali.flickruploader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.rafali.flickruploader.Utils.CAN_UPLOAD;

public class UploadService extends Service {

	private static final String QUEUE_IDS = "queueIds";

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UploadService.class);

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.debug("Service created ...");
		running = true;
		List<Media> images = Utils.getImages(QUEUE_IDS);
		if (images != null) {
			queue.addAll(images);
		}
		ImageTableObserver observer = new ImageTableObserver();
		getContentResolver().registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, observer);
		getContentResolver().registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, observer);

		if (thread == null || !thread.isAlive()) {
			thread = new Thread(new UploadRunnable());
			thread.start();
		}
		BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
				Utils.setCharging(charging);
				LOG.debug("charging : " + charging + ", status : " + status);
				if (charging)
					wake();
			}
		};
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(batteryReceiver, filter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LOG.debug("Service destroyed ...");
		running = false;
		wake();
	}

	static boolean running = false;

	private static List<Media> queue = Collections.synchronizedList(new ArrayList<Media>());
	private static List<Media> uploaded = Collections.synchronizedList(new ArrayList<Media>());

	public static void enqueue(Collection<Media> images, Folder folder, String photoSetId, String photoSetTitle) {
		for (Media image : images) {
			if (!queue.contains(image)) {
				LOG.debug("enqueueing " + image);
				queue.add(image);
				if (folder != null) {
					uploadFolders.put(image, folder);
				}
				uploadPhotosetIds.put(image, photoSetId);
				uploadPhotosetTitles.put(image, photoSetTitle);
			}
		}
		persistQueue();
		wake();
	}

	public static void dequeue(Collection<Media> images) {
		for (Media image : images) {
			if (queue.contains(image)) {
				LOG.debug("dequeueing " + image);
				queue.remove(image);
				uploadFolders.remove(image);
				uploadPhotosetIds.remove(image);
				uploadPhotosetTitles.remove(image);
			}
		}
		persistQueue();
		wake();
	}

	private static Map<Media, Folder> uploadFolders = new HashMap<Media, Folder>();
	private static Map<Media, String> uploadPhotosetIds = new HashMap<Media, String>();
	private static Map<Media, String> uploadPhotosetTitles = new HashMap<Media, String>();

	public static void enqueue(Collection<Media> images, String photoSetId, String photoSetTitle) {
		enqueue(images, null, photoSetId, photoSetTitle);
	}

	public static void persistQueue() {
		Utils.setImages(QUEUE_IDS, queue);
	}

	class UploadRunnable implements Runnable {
		@Override
		public void run() {
			int nbFail = 0;
			while (running) {
				try {
					CAN_UPLOAD canUploadNow = Utils.canUploadNow();
					if (queue.isEmpty() || canUploadNow != Utils.CAN_UPLOAD.ok) {
						if (queue.isEmpty()) {
							uploaded.clear();
						} else {
							Notifications.notify(queue.size(), canUploadNow);
						}

						synchronized (mPauseLock) {
							LOG.debug("waiting for work");
							if (queue.isEmpty()) {
								mPauseLock.wait();
							} else {
								mPauseLock.wait(60000);
							}
						}
					} else {
						if (FlickrApi.isAuthentified()) {
							Collections.sort(queue, Utils.MEDIA_COMPARATOR);
							long start = System.currentTimeMillis();
							final Media image = queue.get(0);
							Folder folder = uploadFolders.get(image);
							boolean success = folder == null && FlickrApi.isUploaded(image);
							if (!success) {
								LOG.debug("Starting upload : " + image);
								success = FlickrApi.upload(image, uploadPhotosetIds.get(image), uploadPhotosetTitles.get(image), folder, new ProgressListener() {
									@Override
									public void onProgress(int progress) {
										LOG.trace("progress : " + progress);
										Notifications.notify(progress, image, uploaded.size() + 1, queue.size() + uploaded.size());
									}
								});
							}
							long time = System.currentTimeMillis() - start;
							queue.remove(image);
							persistQueue();
							if (success) {
								LOG.debug("Upload success : " + time + "ms " + image);
								uploaded.add(image);
								nbFail = 0;
								if (queue.isEmpty()) {
									if (folder != null) {
										FlickrApi.ensureOrdered(folder);
									} else {
										FlickrApi.ensureOrdered(Utils.getInstantAlbumId());
									}
									Mixpanel.increment("photo_uploaded", uploaded.size());
									Mixpanel.flush();
								}
							} else {
								nbFail++;
								LOG.warn("Upload fail : nbFail=" + nbFail + " in " + time + "ms " + image);
								Thread.sleep((long) (Math.pow(2, nbFail) * 2000));
							}
						} else {
							queue.clear();
							persistQueue();
							Notifications.clear();
						}
					}

					FlickrUploader.deleteOldLogs();

				} catch (InterruptedException e) {
					LOG.warn("Thread interrupted");
				} catch (Throwable e) {
					LOG.error(e.getMessage(), e);
				} finally {
					FlickrUploaderActivity flickrPhotoUploader = FlickrUploaderActivity.getInstance();
					if (flickrPhotoUploader != null)
						flickrPhotoUploader.refresh(false);
				}
			}
		}
	}

	public static boolean isUploading(Media image) {
		return queue.contains(image);
	}

	public static boolean isUploading(Folder folder) {
		return !Collections.disjoint(queue, folder.images);
	}

	public static void wake() {
		synchronized (mPauseLock) {
			mPauseLock.notifyAll();
		}
	}

	private static final Object mPauseLock = new Object();

	static private Thread thread;

	public static void cancel(boolean force) {
		LOG.warn("Canceling : queue = " + queue.size() + ", force = " + force);
		queue.clear();
		uploadFolders.clear();
		persistQueue();
		if (thread != null)
			thread.interrupt();
		Notifications.clear();
		if (force) {
			System.exit(0);
		} else {
			FlickrUploaderActivity flickrPhotoUploader = FlickrUploaderActivity.getInstance();
			if (flickrPhotoUploader != null)
				flickrPhotoUploader.refresh(false);
		}
	}

	public static int getNbQueued() {
		return queue.size();
	}

}