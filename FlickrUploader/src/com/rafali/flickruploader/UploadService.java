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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.Utils.CAN_UPLOAD;
import com.rafali.flickruploader.Utils.MediaType;

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
	public IBinder onBind(Intent intent) {
		return null;
	}

	private UploadProgressListener uploadProgressListener = new UploadProgressListener() {
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
			Notifications.notifyFinished(nbUploaded, nbError);
		}

		@Override
		public void onProcessed(Media image, boolean success) {

		}
	};

	private static UploadService instance;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		LOG.debug("Service created ...");
		running = true;
		try {
			List<Media> images = Utils.getImages(STR.queueIds);
			if (images != null) {
				for (Media media : images) {
					if (!queue.contains(media)) {
						queue.add(media);
					}
				}
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
						if (!retryDelay.isEmpty()) {
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onPaused();
							}
						}
						persistFailedCount();
					}
				});

			}
		} catch (Throwable e) {
			LOG.error(Utils.stack2string(e));
		}
		register(uploadProgressListener);
		getContentResolver().registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, imageTableObserver);
		getContentResolver().registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, imageTableObserver);

		if (thread == null || !thread.isAlive()) {
			thread = new Thread(new UploadRunnable());
			thread.start();
		}
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(batteryReceiver, filter);
	}

	ContentObserver imageTableObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean change) {
			UploadService.checkNewFiles();
		}
	};

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

	private long started = System.currentTimeMillis();
	private boolean destroyed = false;

	@Override
	public void onDestroy() {
		super.onDestroy();
		destroyed = true;
		LOG.debug("Service destroyed ... started " + ToolString.formatDuration(System.currentTimeMillis() - started) + " ago");
		if (instance == this) {
			instance = null;
		}
		running = false;
		unregister(uploadProgressListener);
		unregisterReceiver(batteryReceiver);
		getContentResolver().unregisterContentObserver(imageTableObserver);
	}

	static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
	static CheckNewFilesTask checkNewFilesTask;

	static class CheckNewFilesTask implements Runnable {

		ScheduledFuture<?> future;

		@Override
		public void run() {
			checkNewFiles();
		}

		void cancel(boolean mayInterruptIfRunning) {
			try {
				future.cancel(mayInterruptIfRunning);
			} catch (Throwable e) {
				LOG.error(Utils.stack2string(e));
			}
		}

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (Utils.canAutoUploadBool()) {
			// We want this service to continue running until it is explicitly
			// stopped, so return sticky.
			return START_STICKY;
		} else {
			return super.onStartCommand(intent, flags, startId);
		}
	}

	boolean running = false;

	private static List<Media> queue = Collections.synchronizedList(new ArrayList<Media>());
	// private static List<Media> uploaded = Collections.synchronizedList(new ArrayList<Media>());
	private static Map<Media, Long> uploaded = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> recentlyUploaded = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> failed = new ConcurrentHashMap<Media, Long>();
	private static Map<Media, Long> retryDelay = new ConcurrentHashMap<Media, Long>();
	private static Map<Integer, Integer> failedCount = new ConcurrentHashMap<Integer, Integer>();

	public static int enqueue(boolean auto, Collection<Media> images, Folder folder, String photoSetId, String photoSetTitle) {
		int nbQueued = 0;
		int nbAlreadyQueued = 0;
		int nbAlreadyUploaded = 0;
		for (Media image : images) {
			if (queue.contains(image)) {
				nbAlreadyQueued++;
			} else if (FlickrApi.isUploaded(image)) {
				nbAlreadyUploaded++;
			} else if (auto && getNbError(image) > 10) {
				LOG.debug("not auto enqueueing file with too many retries : " + image);
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
		return nbQueued;
	}

	public static void enqueueRetry(Collection<Media> medias) {
		for (Media media : medias) {
			if (!queue.contains(media) && !FlickrApi.unretryable.contains(media)) {
				queue.add(media);
				retryDelay.remove(media);
				failed.remove(media);
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

	private static Media mediaCurrentlyUploading;
	private static long lastUpload = 0;

	class UploadRunnable implements Runnable {
		@Override
		public void run() {
			int nbFail = 0;
			while (running) {
				try {
					CAN_UPLOAD canUploadNow = Utils.canUploadNow();
					if (queue.isEmpty() || canUploadNow != Utils.CAN_UPLOAD.ok) {
						if (queue.isEmpty()) {
							for (Media image : failed.keySet()) {
								Integer nbError = failedCount.get(image.id);
								if (nbError == null) {
									nbError = 1;
									failedCount.put(image.id, nbError);
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
								if ((FlickrUploaderActivity.getInstance() == null || FlickrUploaderActivity.getInstance().isPaused()) && !Utils.canAutoUploadBool()
										&& System.currentTimeMillis() - lastUpload > 5 * 60 * 1000) {
									running = false;
									LOG.debug("stopping service after waiting for 5 minutes");
								} else {
									if (Utils.canAutoUploadBool()) {
										mPauseLock.wait();
									} else {
										LOG.debug("will stop the service if no more upload " + ToolString.formatDuration(System.currentTimeMillis() - started));
										mPauseLock.wait(60000);
									}
								}
							} else {
								if (FlickrUploaderActivity.getInstance() != null && !FlickrUploaderActivity.getInstance().isPaused()) {
									mPauseLock.wait(2000);
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
							mediaCurrentlyUploading = queue.get(queue.size() - 1);
							onProgress(mediaCurrentlyUploading, 0);
							Folder folder = uploadFolders.get(mediaCurrentlyUploading);
							boolean success = folder == null && FlickrApi.isUploaded(mediaCurrentlyUploading);
							if (!success) {
								LOG.debug("Starting upload : " + mediaCurrentlyUploading);
								success = FlickrApi.upload(mediaCurrentlyUploading, uploadPhotosetIds.get(mediaCurrentlyUploading), uploadPhotosetTitles.get(mediaCurrentlyUploading), folder);
							}
							long time = System.currentTimeMillis() - start;
							queue.remove(mediaCurrentlyUploading);
							persistQueue();

							if (success) {
								lastUpload = System.currentTimeMillis();
								nbFail = 0;
								LOG.debug("Upload success : " + time + "ms " + mediaCurrentlyUploading);
								uploaded.put(mediaCurrentlyUploading, System.currentTimeMillis());
								if (queue.isEmpty()) {
									if (folder != null) {
										FlickrApi.ensureOrdered(folder);
									} else {
										FlickrApi.ensureOrdered(Utils.getInstantAlbumId());
									}
									Mixpanel.increment("photo_uploaded", uploaded.size());
									Mixpanel.flush();
								}
								failed.remove(mediaCurrentlyUploading);
								failedCount.remove(mediaCurrentlyUploading.id);
							} else {
								if (FlickrApi.unretryable.contains(mediaCurrentlyUploading)) {
									failed.remove(mediaCurrentlyUploading);
									failedCount.remove(mediaCurrentlyUploading.id);
								} else {
									failed.put(mediaCurrentlyUploading, System.currentTimeMillis());
									Integer countError = failedCount.get(mediaCurrentlyUploading.id);
									if (countError == null) {
										countError = 0;
									}
									nbFail++;
									failedCount.put(mediaCurrentlyUploading.id, countError + 1);
									LOG.warn("Upload fail : nbFail=" + nbFail + " in " + time + "ms, countError=" + countError + " : " + mediaCurrentlyUploading);
									Thread.sleep(Math.min(20000, (long) (Math.pow(2, nbFail) * 2000)));
								}
							}
							persistFailedCount();
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onProcessed(mediaCurrentlyUploading, success);
							}

							if (queue.isEmpty()) {
								for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
									if (uploaded.size() + failed.size() > 0) {
										uploadProgressListener.onFinished(uploaded.size(), failed.size());
									}
								}
							}

							FlickrUploaderActivity flickrPhotoUploader = FlickrUploaderActivity.getInstance();
							if (flickrPhotoUploader != null && !flickrPhotoUploader.isPaused())
								flickrPhotoUploader.refresh(false);

						} else {
							queue.clear();
							persistQueue();
							Notifications.clear();
						}
					}

					FlickrUploader.cleanLogs();

				} catch (InterruptedException e) {
					LOG.warn("Thread interrupted");
				} catch (Throwable e) {
					LOG.error(Utils.stack2string(e));
				} finally {
					mediaCurrentlyUploading = null;
				}
			}
			stopSelf();
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

	public static int getNbError(Media image) {
		if (failedCount.containsKey(image.id)) {
			return failedCount.get(image.id) * FlickrApi.NB_RETRY;
		} else {
			return 0;
		}
	}

	public static boolean isUploading(Media image) {
		return queue.contains(image);
	}

	public static boolean isUploading(Folder folder) {
		return !Collections.disjoint(queue, folder.images);
	}

	public static void wake() {
		if ((instance == null || instance.destroyed) && (!queue.isEmpty() || Utils.canAutoUploadBool())) {
			Context context = FlickrUploader.getAppContext();
			context.startService(new Intent(context, UploadService.class));
			AlarmBroadcastReceiver.initAlarm();
		}
		synchronized (mPauseLock) {
			mPauseLock.notifyAll();
		}
	}

	private static final Object mPauseLock = new Object();

	private Thread thread;

	public static int getNbQueued() {
		return queue.size();
	}

	public static int getNbUploadedTotal() {
		return uploaded.size() + recentlyUploaded.size();
	}

	public static int getNbUploaded() {
		return uploaded.size();
	}

	public static int getNbError() {
		return failed.size() + retryDelay.size();
	}

	public static int getTotal() {
		return queue.size() + uploaded.size();
	}

	public static boolean isCurrentlyUploading(Media media) {
		if (media.equals(mediaCurrentlyUploading))
			return true;
		return false;
	}

	public static List<Media> getQueueSnapshot() {
		return new ArrayList<Media>(queue);
	}

	public static List<Media> getFailedSnapshot() {
		ArrayList<Media> recent = new ArrayList<Media>(failed.keySet());
		for (Media media : retryDelay.keySet()) {
			if (!recent.contains(media)) {
				recent.add(media);
			}
		}
		for (Media media : FlickrApi.unretryable) {
			if (!recent.contains(media)) {
				recent.add(media);
			}
		}
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
		for (Media media : recentlyUploaded.keySet()) {
			if (!recent.contains(media)) {
				recent.add(media);
			}
		}
		Collections.sort(recent, recentlyUploadedComparator);
		return recent;
	}

	public static void onProgress(Media media, int progress) {
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onProgress(progress, media);
		}
	}

	public static void clearUploaded() {
		recentlyUploaded.clear();
		uploaded.clear();
	}

	public static void clearQueued() {
		queue.clear();
		persistQueue();
	}

	public static long getRetryDelay(Media image) {
		Long delay = retryDelay.get(image);
		if (delay == null || delay < System.currentTimeMillis()) {
			delay = 0L;
			retryDelay.put(image, delay);
		}
		return delay;
	}

	public static void clearFailed() {
		retryDelay.clear();
		failed.clear();
		failedCount.clear();
		persistFailedCount();
	}

	public static void checkNewFiles() {
		try {
			String canAutoUpload = Utils.canAutoUpload();
			if ("true".equals(canAutoUpload)) {
				LOG.info("not autouploading : " + canAutoUpload);
				return;
			}

			List<Media> media = Utils.loadImages(null, 10);
			if (media == null || media.isEmpty()) {
				LOG.debug("no media found");
				return;
			}

			long uploadDelayMs = Utils.getUploadDelayMs();
			long newestFileAge = 0;
			List<Media> not_uploaded = new ArrayList<Media>();
			for (Media image : media.subList(0, Math.min(10, media.size()))) {
				if (image.mediaType == MediaType.photo && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true)) {
					LOG.debug("not uploading " + media + " because photo upload disabled");
					continue;
				} else if (image.mediaType == MediaType.video && !Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
					LOG.debug("not uploading " + media + " because video upload disabled");
					continue;
				} else {
					File file = new File(image.path);
					if (file.exists()) {
						boolean uploaded = FlickrApi.isUploaded(image);
						LOG.debug("uploaded : " + uploaded + ", " + image);
						if (!uploaded) {
							if (!Utils.isAutoUpload(new Folder(file.getParent()))) {
								LOG.debug("Ignored : " + file);
							} else {
								int sleep = 0;
								while (file.length() < 100 && sleep < 5) {
									LOG.debug("sleeping a bit");
									sleep++;
									Thread.sleep(1000);
								}
								long fileAge = System.currentTimeMillis() - file.lastModified();
								LOG.debug("uploadDelayMs:" + uploadDelayMs + ", fileAge:" + fileAge + ", newestFileAge:" + newestFileAge);
								if (uploadDelayMs > 0 && fileAge < uploadDelayMs) {
									if (newestFileAge < fileAge) {
										newestFileAge = fileAge;
										long delay = Math.max(1000, uploadDelayMs - newestFileAge);
										LOG.debug("waiting " + ToolString.formatDuration(delay) + " for the " + ToolString.formatDuration(uploadDelayMs) + " delay");
										if (checkNewFilesTask != null) {
											checkNewFilesTask.cancel(false);
										}
										checkNewFilesTask = new CheckNewFilesTask();
										checkNewFilesTask.future = scheduledThreadPoolExecutor.schedule(checkNewFilesTask, delay, TimeUnit.MILLISECONDS);
									}
								} else {
									not_uploaded.add(image);
								}
							}
						}
					} else {
						LOG.debug("Deleted : " + file);
					}
				}
			}
			if (!not_uploaded.isEmpty()) {
				LOG.debug("enqueuing " + not_uploaded.size() + " media: " + not_uploaded);
				enqueue(true, not_uploaded, null, Utils.getInstantAlbumId(), STR.instantUpload);
				FlickrUploaderActivity.staticRefresh(true);
			}
		} catch (Throwable e) {
			LOG.error(Utils.stack2string(e));
		}
	}
}