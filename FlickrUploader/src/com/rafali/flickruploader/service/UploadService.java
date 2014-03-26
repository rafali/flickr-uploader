package com.rafali.flickruploader.service;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.OneQuery;
import se.emilsjolander.sprinkles.Query;
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
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.activity.PreferencesActivity;

public class UploadService extends Service {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(UploadService.class);

	private static final Set<UploadProgressListener> uploadProgressListeners = new HashSet<UploadService.UploadProgressListener>();

	public static interface UploadProgressListener {
		void onProgress(int progress, final Media media);

		void onProcessed(final Media media, boolean success);

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
		public void onProgress(int progress, Media media) {
			// FIXME
			// Notifications.notify(progress, media, uploaded.size() + 1, queue.size() + uploaded.size());
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
		public void onProcessed(Media media, boolean success) {

		}
	};

	private static UploadService instance;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
		LOG.debug("Service created ...");
		running = true;
		register(uploadProgressListener);
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
				LOG.error(ToolString.stack2string(e));
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

	public static int enqueue(boolean auto, Collection<Media> medias, String photoSetTitle) {
		int nbQueued = 0;
		int nbAlreadyQueued = 0;
		int nbAlreadyUploaded = 0;
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
				media.setStatus(STATUS.QUEUED);
				mediaPhotosetTitles.put(media.getPath(), photoSetTitle);
			}
		}
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onQueued(nbQueued, nbAlreadyUploaded, nbAlreadyQueued);
		}
		wake(nbQueued > 0);
		return nbQueued;
	}

	public static void enqueueRetry(Collection<Media> medias) {
		int nbQueued = 0;
		for (Media media : medias) {
			if (!media.isQueued() && !FlickrApi.unretryable.contains(media)) {
				nbQueued++;
				media.setStatus(STATUS.QUEUED);
			}
		}
		wake(nbQueued > 0);
	}

	public static void dequeue(Collection<Media> medias) {
		for (Media media : medias) {
			if (media.isQueued()) {
				LOG.debug("dequeueing " + media);
				media.setStatus(STATUS.IMPORTED);
				mediaPhotosetTitles.remove(media.getPath());
			}
		}
		wake();
	}

	private static Map<String, String> mediaPhotosetTitles = Utils.getMapProperty("mediaPhotosetTitles");

	private static boolean paused = true;

	public static boolean isPaused() {
		return paused;
	}

	private static Media mediaCurrentlyUploading;
	private static long lastUpload = 0;

	public static Media getTopQueued() {
		OneQuery<Media> one = Query.one(Media.class, "select * from Media where status=? order by date desc limit 1", STATUS.QUEUED);
		return one.get();
	}

	class UploadRunnable implements Runnable {
		@Override
		public void run() {
			int nbFail = 0;
			while (running) {
				try {
					mediaCurrentlyUploading = getTopQueued();
					LOG.info("mediaCurrentlyUploading : " + mediaCurrentlyUploading);
					CAN_UPLOAD canUploadNow = Utils.canUploadNow();
					if (mediaCurrentlyUploading == null || canUploadNow != CAN_UPLOAD.ok) {
						if (mediaCurrentlyUploading == null) {
						} else {
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onPaused();
							}
						}

						paused = true;
						synchronized (mPauseLock) {
							// LOG.debug("waiting for work");
							if (mediaCurrentlyUploading == null) {
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

					} else {
						paused = false;
						if (FlickrApi.isAuthentified()) {
							long start = System.currentTimeMillis();
							onProgress(mediaCurrentlyUploading, 0);
							String photosetTitle = mediaPhotosetTitles.get(mediaCurrentlyUploading.getPath());
							boolean success = mediaCurrentlyUploading.isUploaded();
							if (!success) {
								LOG.debug("Starting upload : " + mediaCurrentlyUploading);
								success = FlickrApi.upload(mediaCurrentlyUploading, photosetTitle);
							}
							long time = System.currentTimeMillis() - start;

							if (success) {
								lastUpload = System.currentTimeMillis();
								nbFail = 0;
								LOG.debug("Upload success : " + time + "ms " + mediaCurrentlyUploading);
								mediaCurrentlyUploading.setStatus(STATUS.UPLOADED);
								mediaPhotosetTitles.remove(mediaCurrentlyUploading.getPath());
							} else {
								mediaCurrentlyUploading.setStatus(STATUS.FAILED);
								if (FlickrApi.unretryable.contains(mediaCurrentlyUploading)) {
									mediaCurrentlyUploading.setRetries(3);
								} else {
									int retries = mediaCurrentlyUploading.getRetries();
									mediaCurrentlyUploading.setRetries(retries + 1);
									nbFail++;
									LOG.warn("Upload fail : nbFail=" + nbFail + " in " + time + "ms : " + mediaCurrentlyUploading);
									Thread.sleep(Math.min(20000, (long) (Math.pow(2, nbFail) * 2000)));
								}
							}
							for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
								uploadProgressListener.onProcessed(mediaCurrentlyUploading, success);
							}

							FlickrUploaderActivity flickrPhotoUploader = FlickrUploaderActivity.getInstance();
							if (flickrPhotoUploader != null && !flickrPhotoUploader.isPaused())
								flickrPhotoUploader.refresh(false);

						} else {
							Notifications.clear();
						}
					}

					FlickrUploader.cleanLogs();

				} catch (InterruptedException e) {
					LOG.warn("Thread interrupted");
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				} finally {
					if (mediaCurrentlyUploading != null) {
						mediaCurrentlyUploading.save();
						mediaCurrentlyUploading = null;
					}
				}
			}
			stopSelf();
		}
	}

	public static long isRecentlyUploaded(Media media) {
		// FIXME
		// if (media != null) {
		// if (uploaded.containsKey(media)) {
		// return uploaded.get(media);
		// } else if (recentlyUploaded.containsKey(media)) {
		// return recentlyUploaded.get(media);
		// }
		// }
		return -1;
	}

	public static boolean isQueueEmpty() {
		OneQuery<Media> one = Query.one(Media.class, "select * from Media where (status=? or status=?) limit 1", STATUS.QUEUED, STATUS.FAILED);
		return one.get() == null;
	}

	public static void wake() {
		wake(false);
	}

	public static void wake(boolean force) {
		if ((instance == null || instance.destroyed) && (force || Utils.canAutoUploadBool() || !isQueueEmpty())) {
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

	public static boolean isCurrentlyUploading(Media media) {
		if (media.equals(mediaCurrentlyUploading))
			return true;
		return false;
	}

	private static Comparator<Media> recentlyUploadedComparator = new Comparator<Media>() {
		@Override
		public int compare(Media lhs, Media rhs) {
			return (int) (isRecentlyUploaded(lhs) - isRecentlyUploaded(rhs));
		}
	};

	public static void onProgress(Media media, int progress) {
		for (UploadProgressListener uploadProgressListener : uploadProgressListeners) {
			uploadProgressListener.onProgress(progress, media);
		}
	}

	public static void clearQueued() {
		// FIXME
		// queue.clear();
		// persistQueue();
	}

	public static void clearFailed() {
		// FIXME
		// retryDelay.clear();
		// failed.clear();
		// failedCount.clear();
		// persistFailedCount();
	}

	public static void checkNewFiles() {
		try {
			String canAutoUpload = Utils.canAutoUpload();
			if (!"true".equals(canAutoUpload)) {
				LOG.info("canAutoUpload : " + canAutoUpload);
				return;
			}

			long lastNewFilesCheckNotEmpty = Utils.getLongProperty(STR.lastNewFilesCheckNotEmpty);
			List<Media> medias;
			if (lastNewFilesCheckNotEmpty <= 0) {
				// FIXME
				medias = Utils.loadMedia();
			} else {
				medias = new ArrayList<Media>();
				List<Media> all = Utils.loadMedia();
				for (Media media2 : all) {
					if (media2.getDate() >= lastNewFilesCheckNotEmpty) {
						medias.add(media2);
					}
				}
				LOG.debug("found " + medias.size() + " media files since: " + SimpleDateFormat.getDateTimeInstance().format(new Date(lastNewFilesCheckNotEmpty)));
			}

			if (medias == null || medias.isEmpty()) {
				LOG.debug("no media found");
				return;
			}

			long uploadDelayMs = Utils.getUploadDelayMs();
			long newestFileAge = 0;
			List<Media> not_uploaded = new ArrayList<Media>();
			for (Media media : medias) {
				if (media.getMediaType() == MEDIA_TYPE.PHOTO && !Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD, false)) {
					LOG.debug("not uploading " + media + " because photo upload disabled");
					continue;
				} else if (media.getMediaType() == MEDIA_TYPE.VIDEO && !Utils.getBooleanProperty(PreferencesActivity.AUTOUPLOAD_VIDEOS, false)) {
					LOG.debug("not uploading " + media + " because video upload disabled");
					continue;
				} else {
					File file = new File(media.getPath());
					if (file.exists()) {
						boolean uploaded = media.isUploaded();
						LOG.debug("uploaded : " + uploaded + ", " + media);
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
									not_uploaded.add(media);
								}
							}
						}
					} else {
						LOG.debug("Deleted : " + file);
					}
				}
			}
			if (!not_uploaded.isEmpty()) {
				Utils.setLongProperty(STR.lastNewFilesCheckNotEmpty, System.currentTimeMillis());
				LOG.debug("enqueuing " + not_uploaded.size() + " media: " + not_uploaded);
				Map<String, String> foldersSetNames = Utils.getFoldersSetNames();
				for (Media notUploadedMedia : not_uploaded) {
					File file = new File(notUploadedMedia.getPath());
					String uploadSetTitle = foldersSetNames.get(file.getParentFile().getAbsolutePath());
					if (uploadSetTitle == null) {
						uploadSetTitle = STR.instantUpload;
					}
					enqueue(true, Arrays.asList(notUploadedMedia), uploadSetTitle);
				}
				FlickrUploaderActivity.staticRefresh(true);
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}
}