package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.Utils.CAN_UPLOAD;

public class UploadService extends Service {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UploadService.class);

	private static final Set<UploadProgressListener> uploadProgressListeners = new HashSet<UploadService.UploadProgressListener>();

	public static interface UploadProgressListener {
		void onProgress(int progress, final Media image);

		void onProcessed(final Media image, boolean success);

		void onPaused();

		void onFinished(int nbUploaded, int nbErrors);

		void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued);
	}

	public static void register(UploadProgressListener uploadProgressListener) {
		if (uploadProgressListener != null)
			uploadProgressListeners.add(uploadProgressListener);
		else
			LOG.warn("uploadProgressListener is null");
	}

	public static void unregister(UploadProgressListener uploadProgressListener) {
		if (uploadProgressListener != null)
			uploadProgressListeners.remove(uploadProgressListener);
		else
			LOG.warn("uploadProgressListener is null");
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	private static UploadProgressListener uploadProgressListener = new UploadProgressListener() {
		@Override
		public void onProgress(int progress, Media image) {
			Notifications.notify(progress, image, uploaded.size() + 1, queue.size() + uploaded.size());
		}

		@Override
		public void onPaused() {
		}

		@Override
		public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		}

		@Override
		public void onFinished(int nbUploaded, int nbError) {

		}

		@Override
		public void onProcessed(Media image, boolean success) {
			
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.debug("Service created ...");
		running = true;
		try {
			List<Media> images = Utils.getImages(STR.queueIds);
			if (images != null) {
				queue.addAll(images);
			}
			final Map<Integer, Integer> persistedFailedCount = Utils.getMapIntegerProperty(STR.failedCount);
			if (persistedFailedCount != null && !persistedFailedCount.isEmpty()) {
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						Iterator<Entry<Integer, Integer>> it = persistedFailedCount.entrySet().iterator();
						while (it.hasNext()) {
							Map.Entry<Integer, Integer> entry = it.next();
							Integer imageId = entry.getKey();
							Media image = Utils.getImage(imageId);
							if (image != null && new File(image.path).exists()) {
								Integer nbErrors = entry.getValue();
								failedCount.put(imageId, nbErrors);
								retryDelay.put(image, System.currentTimeMillis() + nbErrors * 60 * 1000L);
							}
						}
						persistFailedCount();
					}
				});

			}
		} catch (Throwable e) {
			LOG.error(e.getMessage(), e);
		}
		register(uploadProgressListener);
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
				// LOG.debug("charging : " + charging + ", status : " + status);
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
		unregister(uploadProgressListener);
	}

	static boolean running = false;

	private static List<Media> queue = Collections.synchronizedList(new ArrayList<Media>());
	// private static List<Media> uploaded = Collections.synchronizedList(new ArrayList<Media>());
	private static Map<Media, Long> uploaded = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> recentlyUploaded = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> failed = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> retryDelay = new ConcurrentHashMap<Media, Long>();
	private static Map<Integer, Integer> failedCount = new ConcurrentHashMap<Integer, Integer>();

	public static void enqueue(Collection<Media> images, Folder folder, String photoSetId, String photoSetTitle) {
		int nbQueued = 0;
		int nbAlreadyQueued = 0;
		int nbAlreadyUploaded = 0;
		for (Media image : images) {
			if (queue.contains(image)) {
				nbAlreadyQueued++;
			} else if (FlickrApi.isUploaded(image)) {
				nbAlreadyUploaded++;
			} else {
				nbQueued++;
				LOG.debug("enqueueing " + image);
				queue.add(image);
				if (folder != null) {
					uploadFolders.put(image, folder);
				}
				uploadPhotosetIds.put(image, photoSetId);
				uploadPhotosetTitles.put(image, photoSetTitle);
			}
		}
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onQueued(nbQueued, nbAlreadyUploaded, nbAlreadyQueued);
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
		Utils.setImages(STR.queueIds, queue);
	}

	public static void persistFailedCount() {
		Utils.setMapIntegerProperty(STR.failedCount, failedCount);
	}

	private static boolean paused = true;

	public static boolean isPaused() {
		return paused;
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
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onFinished(uploaded.size(), failed.size());
							}
							for (Media image : failed.keySet()) {
								Integer nbError = failedCount.get(image.id);
								if (nbError == null) {
									nbError = 1;
								}
								if (nbError < 10) {
									// max 3 hours delay to retry
									retryDelay.put(image, System.currentTimeMillis() + Math.min(3 * 60 * 60 * 1000L, (long) (Math.pow(2, nbFail) * 60 * 1000L)));
								}
							}
							recentlyUploaded.putAll(uploaded);
							failed.clear();
							uploaded.clear();
						} else {
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onPaused();
							}
						}

						paused = true;
						synchronized (mPauseLock) {
							// LOG.debug("waiting for work");
							if (queue.isEmpty() && retryDelay.isEmpty()) {
								mPauseLock.wait();
							} else {
								if (FlickrUploaderActivity.getInstance() != null && !FlickrUploaderActivity.getInstance().isPaused()) {
									mPauseLock.wait(1000);
								} else {
									mPauseLock.wait(60000);
								}
							}
						}

						Iterator<Entry<Media, Long>> it = retryDelay.entrySet().iterator();
						while (it.hasNext()) {
							Map.Entry<Media, Long> entry = it.next();
							if (System.currentTimeMillis() > entry.getValue()) {
								queue.add(entry.getKey());
								it.remove();
							}
						}
					} else {
						paused = false;
						if (FlickrApi.isAuthentified()) {
							Collections.sort(queue, Utils.MEDIA_COMPARATOR);
							long start = System.currentTimeMillis();
							final Media image = queue.get(queue.size() - 1);
							Folder folder = uploadFolders.get(image);
							boolean success = folder == null && FlickrApi.isUploaded(image);
							if (!success) {
								LOG.debug("Starting upload : " + image);
								success = FlickrApi.upload(image, uploadPhotosetIds.get(image), uploadPhotosetTitles.get(image), folder, new ProgressListener() {
									@Override
									public void onProgress(int progress) {
										LOG.trace("progress : " + progress);
										for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
											uploadProgressListener.onProgress(progress, image);
										}
									}
								});
							}
							long time = System.currentTimeMillis() - start;
							queue.remove(image);
							persistQueue();

							if (success) {
								nbFail = 0;
								LOG.debug("Upload success : " + time + "ms " + image);
								uploaded.put(image, System.currentTimeMillis());
								if (queue.isEmpty()) {
									if (folder != null) {
										FlickrApi.ensureOrdered(folder);
									} else {
										FlickrApi.ensureOrdered(Utils.getInstantAlbumId());
									}
									Mixpanel.increment("photo_uploaded", uploaded.size());
									Mixpanel.flush();
								}
								failed.remove(image);
								failedCount.remove(image.id);
							} else {
								failed.put(image, System.currentTimeMillis());
								Integer countError = failedCount.get(image);
								if (countError == null) {
									countError = 0;
								}
								nbFail++;
								countError++;
								failedCount.put(image.id, countError);
								LOG.warn("Upload fail : nbFail=" + nbFail + " in " + time + "ms, countError=" + countError + " : " + image);
								Thread.sleep(Math.min(20000, (long) (Math.pow(2, nbFail) * 2000)));
							}
							persistFailedCount();
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onProcessed(image, success);
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

	public static long isRecentlyUploaded(Media image) {
		if (uploaded.containsKey(image)) {
			return uploaded.get(image);
		} else if (recentlyUploaded.containsKey(image)) {
			return recentlyUploaded.get(image);
		} else {
			return -1;
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

	public static int getNbUploaded() {
		return uploaded.size();
	}

	public static int getNbError() {
		return failed.size();
	}

	public static int getTotal() {
		return queue.size() + uploaded.size();
	}

	public static List<Media> getQueueSnapshot() {
		return new ArrayList<Media>(queue);
	}

	public static List<Media> getFailedSnapshot() {
		ArrayList<Media> recent = new ArrayList<Media>(failed.keySet());
		recent.addAll(retryDelay.keySet());
		return recent;
	}

	private static Comparator<Media> recentlyUploadedComparator = new Comparator<Media>() {
		@Override
		public int compare(Media lhs, Media rhs) {
			return (int) (isRecentlyUploaded(lhs) - isRecentlyUploaded(rhs));
		}
	};

	public static List<Media> getRecentlyUploadedSnapshot() {
		ArrayList<Media> recent = new ArrayList<Media>(uploaded.keySet());
		recent.addAll(recentlyUploaded.keySet());
		Collections.sort(recent, recentlyUploadedComparator);
		return recent;
	}
}