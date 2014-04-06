package com.rafali.flickruploader.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.Transaction;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.googlecode.flickrjandroid.FlickrException;
import com.googlecode.flickrjandroid.REST;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.broadcast.AlarmBroadcastReceiver;
import com.rafali.flickruploader.enums.CAN_UPLOAD;
import com.rafali.flickruploader.enums.MEDIA_TYPE;
import com.rafali.flickruploader.enums.STATUS;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.tool.Notifications;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.tool.Utils.Callback;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.activity.PreferencesActivity;

public class UploadService extends Service {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UploadService.class);

	private static final Set<UploadProgressListener> uploadProgressListeners = new HashSet<UploadService.UploadProgressListener>();

	public static interface UploadProgressListener {
		void onProgress(final Media media);

		void onProcessed(final Media media);

		void onFinished(final int nbUploaded, final int nbErrors);

		void onQueued(final int nbQueued, final int nbAlreadyUploaded, final int nbAlreadyQueued);

		void onDequeued(final int nbDequeued);
	}

	public static class BasicUploadProgressListener implements UploadProgressListener {
		@Override
		public void onProgress(Media media) {
		}

		@Override
		public void onProcessed(Media media) {
		}

		@Override
		public void onFinished(int nbUploaded, int nbErrors) {
		}

		@Override
		public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		}

		@Override
		public void onDequeued(int nbDequeued) {
		}

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

	private static UploadService instance;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		LOG.debug("Service created…");
		running = true;
		getContentResolver().registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, imageTableObserver);
		getContentResolver().registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, imageTableObserver);

		if (thread == null || !thread.isAlive()) {
			thread = new Thread(new UploadRunnable());
			thread.start();
		}
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(batteryReceiver, filter);
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				checkNewFiles();
			}
		});
		Notifications.init();
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
		LOG.debug("Service destroyed… started " + ToolString.formatDuration(System.currentTimeMillis() - started) + " ago");
		if (instance == this) {
			instance = null;
		}
		running = false;
		unregisterReceiver(batteryReceiver);
		getContentResolver().unregisterContentObserver(imageTableObserver);
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

	public static int enqueue(boolean auto, Collection<Media> medias, String photoSetTitle) {
		int nbQueued = 0;
		int nbAlreadyQueued = 0;
		int nbAlreadyUploaded = 0;
		Transaction t = new Transaction();
		try {
			for (Media media : medias) {
				if (media.isQueued()) {
					nbAlreadyQueued++;
				} else if (media.isUploaded()) {
					nbAlreadyUploaded++;
				} else if (auto && media.getRetries() > 3) {
					LOG.debug("not auto enqueueing file with too many retries : " + media);
				} else {
					nbQueued++;
					LOG.debug("enqueueing " + media);
					media.setFlickrSetTitle(photoSetTitle);
					media.setStatus(STATUS.QUEUED, t);
				}
			}
			t.setSuccessful(true);
		} finally {
			t.finish();
		}
		if (nbQueued > 0) {
			checkQueue();
		}
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onQueued(nbQueued, nbAlreadyUploaded, nbAlreadyQueued);
		}
		wake(nbQueued > 0);
		return nbQueued;
	}

	public static void enqueueRetry(Iterable<Media> medias) {
		int nbQueued = 0;
		Transaction t = new Transaction();
		try {
			for (Media media : medias) {
				if (!media.isQueued() && media.getTimestampRetry() < Long.MAX_VALUE) {
					nbQueued++;
					media.setStatus(STATUS.QUEUED, t);
				}
			}
			t.setSuccessful(true);
		} finally {
			t.finish();
		}
		if (nbQueued > 0) {
			checkQueue();
		}
		wake(nbQueued > 0);
	}

	public static void dequeue(Collection<Media> medias) {
		int nbDequeued = 0;
		Transaction t = new Transaction();
		try {
			for (final Media media : medias) {
				if (media.isQueued()) {
					LOG.debug("dequeueing " + media);
					media.setStatus(STATUS.PAUSED, t);
					nbDequeued++;
					if (media.equals(mediaCurrentlyUploading)) {
						REST.kill(media);
					}
				}
			}
			t.setSuccessful(true);
		} finally {
			t.finish();
		}
		if (nbDequeued > 0) {
			checkQueue();
			for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
				uploadProgressListener.onDequeued(nbDequeued);
			}
		}
		wake();
	}

	private static boolean paused = true;

	public static boolean isPaused() {
		return paused;
	}

	private static Media mediaCurrentlyUploading;
	private static Media mediaPreviouslyUploading;
	private static long lastUpload = 0;

	public static Media getMediaCurrentlyUploading() {
		return mediaCurrentlyUploading;
	}

	static int nbNetworkRetries = 0;

	class UploadRunnable implements Runnable {
		@SuppressWarnings("deprecation")
		@Override
		public void run() {
			while (running) {
				try {
					mediaCurrentlyUploading = checkQueue();

					if (mediaPreviouslyUploading != null) {
						for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
							uploadProgressListener.onProcessed(mediaPreviouslyUploading);
						}
						mediaPreviouslyUploading = null;
						if (mediaCurrentlyUploading == null) {
							onUploadFinished();
							FlickrUploader.cleanLogs();
						}
					}

					CAN_UPLOAD canUploadNow = Utils.canUploadNow();

					if (mediaCurrentlyUploading == null || canUploadNow != CAN_UPLOAD.ok) {
						paused = true;

						synchronized (mPauseLock) {
							// LOG.debug("waiting for work");
							if (mediaCurrentlyUploading == null) {
								if ((FlickrUploaderActivity.getInstance() == null || FlickrUploaderActivity.getInstance().isPaused()) && !Utils.canAutoUploadBool()
										&& System.currentTimeMillis() - lastUpload > 5 * 60 * 1000) {
									running = false;
									LOG.debug("stopping service after waiting for 5 minutes");
									checkForFilesToDelete();
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

					} else {
						paused = false;
						if (FlickrApi.isAuthentified()) {
							long start = System.currentTimeMillis();
							mediaCurrentlyUploading.setProgress(0);
							onUploadProgress(mediaCurrentlyUploading);
							ConnectivityManager cm = (ConnectivityManager) FlickrUploader.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
							if (STR.wifionly.equals(Utils.getStringProperty(PreferencesActivity.UPLOAD_NETWORK))) {
								cm.setNetworkPreference(ConnectivityManager.TYPE_WIFI);
							} else {
								cm.setNetworkPreference(ConnectivityManager.DEFAULT_NETWORK_PREFERENCE);
							}

							while (mediaCurrentlyUploading.getTimestampRetry() < Long.MAX_VALUE && System.currentTimeMillis() < mediaCurrentlyUploading.getTimestampRetry()) {
								synchronized (mPauseLock) {
									long pausems = Math.max(1000, mediaCurrentlyUploading.getTimestampRetry() - System.currentTimeMillis());
									LOG.debug("pausing for " + (pausems / 1000) + "s before uploading");
									mPauseLock.wait(pausems);
									Media topQueued = checkQueue();
									if (topQueued == null || !topQueued.equals(mediaCurrentlyUploading)) {
										LOG.info("topQueued:" + topQueued + ", mediaCurrentlyUploading:" + mediaCurrentlyUploading);
										mediaCurrentlyUploading = null;
										break;
									}
								}
							}

							if (mediaCurrentlyUploading == null) {
								continue;
							}

							if (mediaCurrentlyUploading.getRetries() > 0) {

								boolean networkOk = FlickrApi.isNetworkOk();
								LOG.debug("retry=" + mediaCurrentlyUploading.getRetries() + ", networkOk=" + networkOk);
								if (!networkOk) {
									nbNetworkRetries++;
									long waitingtime = (long) Math.min(3600 * 1000L, Math.max(10000L, Math.pow(2, nbNetworkRetries) * 1000L));
									LOG.warn("network not ready yet, retrying in " + (waitingtime / 1000) + "s, nbNetworkRetries=" + nbNetworkRetries);
									mediaCurrentlyUploading.setTimestampRetry(System.currentTimeMillis() + waitingtime);
									mediaCurrentlyUploading.save();
									continue;
								}
							}

							UploadException exc = null;
							try {
								LOG.debug("Starting upload : " + mediaCurrentlyUploading);
								FlickrApi.upload(mediaCurrentlyUploading);
							} catch (UploadException e) {
								LOG.error(e.toString());
								exc = e;
							}
							long time = System.currentTimeMillis() - start;

							if (exc == null) {
								nbNetworkRetries = 0;
								lastUpload = System.currentTimeMillis();
								LOG.debug("Upload success : " + time + "ms " + mediaCurrentlyUploading);
								mediaCurrentlyUploading.setStatus(STATUS.UPLOADED);
							} else {
								mediaCurrentlyUploading.setErrorMessage(exc.getMessage());
								int newretries = mediaCurrentlyUploading.getRetries() + 1;
								mediaCurrentlyUploading.setRetries(newretries);
								if (exc.isRetryable()) {
									LOG.warn("Upload fail in " + time + "ms : " + mediaCurrentlyUploading + ", newretries=" + newretries);
									if (newretries >= 9) {
										mediaCurrentlyUploading.setStatus(STATUS.FAILED);
									}
								} else {
									mediaCurrentlyUploading.setStatus(STATUS.FAILED);
								}
							}
							mediaCurrentlyUploading.save();

						} else {
							Notifications.clear();
							running = false;
						}
					}

				} catch (InterruptedException e) {
					LOG.warn("Thread interrupted");
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				} finally {
					if (mediaCurrentlyUploading != null) {
						mediaPreviouslyUploading = mediaCurrentlyUploading;
						mediaCurrentlyUploading = null;
					}
				}
			}
			stopSelf();
		}
	}

	public static void wake() {
		wake(false);
	}

	public static class UploadException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private Boolean retryable;

		public UploadException(String message, boolean retryable) {
			super(message);
			this.retryable = retryable;
		}

		@Override
		public String toString() {
			return "UploadException : " + getMessage() + " : " + (getCause() == null ? "" : "cause : " + getCause().getClass().getSimpleName() + " : " + getCause().getMessage() + " : ")
					+ "isRetryable=" + isRetryable() + ", isNetworkError=" + isNetworkError();
		}

		public UploadException(String message, Throwable cause) {
			super(message, cause);
		}

		public boolean isRetryable() {
			if (retryable != null) {
				return retryable;
			} else {
				return isRetryable(getCause());
			}
		}

		private static boolean isRetryable(Throwable e) {
			if (e == null) {
				return false;
			} else if (e instanceof FlickrException) {
				return false;
			} else if (e instanceof FileNotFoundException) {
				return false;
			} else if (e instanceof RuntimeException && e.getCause() != null) {
				return isRetryable(e.getCause());
			}
			return true;
		}

		public boolean isNetworkError() {
			return isNetworkError(getCause());
		}

		private boolean isNetworkError(Throwable e) {
			if (e instanceof UnknownHostException) {
				return true;
			} else if (e instanceof SocketException) {
				return true;
			}
			return false;
		}
	}

	public static void wake(boolean force) {
		if ((instance == null || instance.destroyed) && (force || Utils.canAutoUploadBool() || checkQueue() != null)) {
			Context context = FlickrUploader.getAppContext();
			context.startService(new Intent(context, UploadService.class));
			AlarmBroadcastReceiver.initAlarm();
		}
		checkForFilesToDelete();
		synchronized (mPauseLock) {
			mPauseLock.notifyAll();
		}
	}

	private static final Object mPauseLock = new Object();

	private Thread thread;

	public static synchronized Media checkQueue() {
		List<Media> medias = Utils.loadMedia(false);
		recentlyUploaded.clear();
		failed.clear();
		currentlyQueued.clear();
		Media oldestCreatedMedia = null;

		long yesterday = System.currentTimeMillis() - 24 * 3600 * 1000L;
		for (Media media : medias) {
			if (media.isQueued()) {
				currentlyQueued.add(media);
				if (oldestCreatedMedia == null || oldestCreatedMedia.getTimestampCreated() > media.getTimestampCreated()) {
					oldestCreatedMedia = media;
				}
			}
			if (media.isUploaded() && media.getTimestampQueued() > yesterday) {
				recentlyUploaded.add(media);
			}
			if (media.isFailed()) {
				failed.add(media);
			}
		}
		return oldestCreatedMedia;
	}

	static Set<Media> recentlyUploaded = new HashSet<Media>();
	static Set<Media> failed = new HashSet<Media>();
	static Set<Media> currentlyQueued = new HashSet<Media>();

	public static void onUploadProgress(Media media) {
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onProgress(media);
		}
	}

	public static void onUploadFinished() {
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onFinished(recentlyUploaded.size(), failed.size());
		}
	}

	public static void clear(final int status, final Callback<Void> callback) {
		if (status == STATUS.FAILED || status == STATUS.QUEUED) {
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					int nbModified = 0;
					List<Media> medias = Utils.loadMedia(false);
					Transaction t = new Transaction();
					try {
						for (final Media media : medias) {
							if (media.getStatus() == status) {
								if (media.isQueued() && media.equals(mediaCurrentlyUploading)) {
									REST.kill(media);
								}
								media.setStatus(STATUS.PAUSED, t);
								nbModified++;
							}
						}
						t.setSuccessful(true);
					} finally {
						t.finish();
					}
					if (nbModified > 0) {
						checkQueue();
					}
					if (callback != null)
						callback.onResult(null);
				}
			});
		} else {
			LOG.error("status " + status + " is not supported");
		}
	}

	public static void checkNewFiles() {
		try {
			String canAutoUpload = Utils.canAutoUpload();
			if (!"true".equals(canAutoUpload)) {
				LOG.info("canAutoUpload : " + canAutoUpload);
				return;
			}

			List<Media> medias = Utils.loadMedia(true);

			if (medias == null || medias.isEmpty()) {
				LOG.info("no media found");
				return;
			}

			Map<String, Folder> pathFolders = Utils.getFolders(false);

			if (pathFolders.isEmpty()) {
				LOG.info("no folder monitored");
				return;
			}

			final long uploadDelayMs = Utils.getUploadDelayMs();
			long newestFileAge = 0;
			for (Media media : medias) {
				if (media.isImported()) {
					if (media.getMediaType() == MEDIA_TYPE.PHOTO && !Utils.isAutoUpload(MEDIA_TYPE.PHOTO)) {
						LOG.debug("not uploading " + media + " because photo upload disabled");
						continue;
					} else if (media.getMediaType() == MEDIA_TYPE.VIDEO && !Utils.isAutoUpload(MEDIA_TYPE.VIDEO)) {
						LOG.debug("not uploading " + media + " because video upload disabled");
						continue;
					} else {
						File file = new File(media.getPath());
						if (file.exists()) {
							boolean uploaded = media.isUploaded();
							LOG.debug("uploaded : " + uploaded + ", " + media);
							if (!uploaded) {
								Folder folder = pathFolders.get(media.getFolderPath());
								if (folder == null || !folder.isAutoUploaded()) {
									LOG.debug(media.getFolderPath() + " not monitored : " + file);
								} else {
									int sleep = 0;
									while (file.length() < 100 && sleep < 5) {
										LOG.debug("sleeping a bit");
										sleep++;
										Thread.sleep(1000);
									}
									long fileAge = System.currentTimeMillis() - file.lastModified();
									LOG.debug("uploadDelayMs:" + uploadDelayMs + ", fileAge:" + fileAge + ", newestFileAge:" + newestFileAge);
									if (uploadDelayMs > 0) {
										media.setTimestampRetry(System.currentTimeMillis() + uploadDelayMs);
									}
									enqueue(true, Arrays.asList(media), folder.getFlickrSetTitle());
								}
							}
						} else {
							LOG.debug("Deleted : " + file);
							media.deleteAsync();
						}
					}
				}
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	public static Set<Media> getCurrentlyQueued() {
		return currentlyQueued;
	}

	public static Set<Media> getRecentlyUploaded() {
		return recentlyUploaded;
	}

	public static int getNbTotal() {
		return currentlyQueued.size() + recentlyUploaded.size() + failed.size();
	}

	public static Set<Media> getFailed() {
		return failed;
	}

	static long lastDeleteCheck = Utils.getLongProperty("lastDeleteCheck");

	static void checkForFilesToDelete() {
		if (Utils.isAutoDelete() && System.currentTimeMillis() - lastDeleteCheck > 3600 * 1000L) {
			lastDeleteCheck = System.currentTimeMillis();
			Utils.setLongProperty("lastDeleteCheck", lastDeleteCheck);
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						long firstInstallTime = FlickrUploader.getAppContext().getPackageManager().getPackageInfo(FlickrUploader.getAppContext().getPackageName(), 0).firstInstallTime;
						long yesterday = System.currentTimeMillis() - 24 * 3600 * 1000L;
						List<Media> loadMedia = Utils.loadMedia(false);
						int nbFileDeleted = 0;
						for (Media media : loadMedia) {
							if (media.isUploaded() && media.getTimestampUploaded() > firstInstallTime && media.getTimestampUploaded() < yesterday) {
								boolean stillOnFlickr = FlickrApi.isStillOnFlickr(media);
								LOG.info("poundering deletion of : " + media + " : stillOnFlickr=" + stillOnFlickr);
								if (stillOnFlickr) {
									boolean deleted = new File(media.getPath()).delete();
									LOG.warn("Deleting " + media + " " + ToolString.formatDuration(System.currentTimeMillis() - media.getTimestampUploaded()) + " after upload : deleted=" + deleted);
									media.delete();
									nbFileDeleted++;
								} else if (FlickrApi.isNetworkOk()) {
									media.setFlickrId(null);
									media.save2(null);
								}
							}
						}
						if (nbFileDeleted > 0) {
							LOG.warn(nbFileDeleted + " files deleted");
							Utils.loadMedia(true);
						}
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			});
		}
	}
}