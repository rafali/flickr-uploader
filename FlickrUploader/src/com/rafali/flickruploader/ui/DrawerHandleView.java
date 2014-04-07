package com.rafali.flickruploader.ui;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.EViewGroup;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.enums.CAN_UPLOAD;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.service.UploadService.UploadProgressListener;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.widget.CustomImageView;
import com.rafali.flickruploader2.R;

@EViewGroup(R.layout.drawer_handle_view)
public class DrawerHandleView extends LinearLayout implements UploadProgressListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DrawerHandleView.class);

	public DrawerHandleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOrientation(LinearLayout.VERTICAL);
		activity = (FlickrUploaderActivity) getContext();
	}

	@ViewById(R.id.image)
	CustomImageView imageView;

	@ViewById(R.id.title)
	TextView title;

	@ViewById(R.id.sub_title)
	TextView subTitle;

	@ViewById(R.id.message)
	TextView message;

	@ViewById(R.id.progressContainer)
	View progressContainer;

	@AfterViews
	void afterViews() {
		progressContainer.setVisibility(View.GONE);
		message.setVisibility(View.VISIBLE);
		message.setText("");
		render();
	}

	@Override
	public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		String str = nbQueued + " media queued";
		if (nbAlreadyQueued > 0) {
			str += ", " + nbAlreadyQueued + " already queued";
		}
		if (nbAlreadyUploaded > 0) {
			str += ", " + nbAlreadyUploaded + " already uploaded";
		}
		setMessage(str, 3000);
		render();
	}

	@Override
	public void onDequeued(int nbDequeued) {
		if (nbDequeued > 0) {
			setMessage(nbDequeued + " media removed from queue", 3000);
			render();
		}
	}

	long messageUntil = System.currentTimeMillis();

	@UiThread
	void setMessage(String text, int duration) {
		messageUntil = System.currentTimeMillis() + duration;
		progressContainer.setVisibility(View.GONE);
		message.setVisibility(View.VISIBLE);
		message.setText(text);
		LOG.debug("message:" + text + ", duration:" + duration);
	}

	int nbMonitored = -1;

	public void onActivityResume() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				nbMonitored = Utils.getFoldersMonitoredNb();
				render();
			}
		});
	}

	FlickrUploaderActivity activity = null;

	private void setMessage(String text) {
		progressContainer.setVisibility(View.GONE);
		message.setVisibility(View.VISIBLE);
		message.setText(text);
	}

	@UiThread
	public void render() {
		try {
			if (message != null && activity != null && !activity.isPaused()) {
				if (System.currentTimeMillis() > messageUntil) {

					if (!FlickrApi.isAuthentified()) {
						setMessage("Login into Flickr from the Preferences");
					} else if (!Utils.isPremium() && !Utils.isTrial()) {
						setMessage("Trial version as expired");
					} else if (UploadService.getCurrentlyQueued().size() == 0) {
						String text = "No media queued";
						int nbUploaded = UploadService.getRecentlyUploaded().size();
						if (nbUploaded > 0) {
							text += ", " + nbUploaded + " recently uploaded";
						}
						int nbError = UploadService.getFailed().size();
						if (nbError > 0) {
							text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
						}
						if (nbError + nbUploaded <= 0) {
							if (Utils.isAutoUpload()) {
								if (nbMonitored < 0) {
									nbMonitored = Utils.getFoldersMonitoredNb();
								}
								text += ", " + nbMonitored + " folders monitored";
							}
						}

						setMessage(text);
					} else {
						if (UploadService.isPaused()) {
							CAN_UPLOAD canUploadNow = Utils.canUploadNow();
							if (canUploadNow == CAN_UPLOAD.ok) {
								setMessage("Upload paused, should resume soon");
							} else if (canUploadNow == CAN_UPLOAD.manually) {
								long pausedUntil = Utils.getLongProperty(STR.manuallyPaused);
								if (pausedUntil == Long.MAX_VALUE) {
									setMessage("Upload paused by user");
								} else {
									setMessage("Upload paused by user, will auto resume in " + ToolString.formatDuration(pausedUntil - System.currentTimeMillis()));
								}
							} else {
								setMessage("Upload paused, waiting for " + canUploadNow.getDescription());
							}
						} else {
							final Media media = UploadService.getMediaCurrentlyUploading();
							if (media == null) {
								setMessage("Uploading " + UploadService.getCurrentlyQueued().size() + " media");
							} else {
								int currentPosition = UploadService.getRecentlyUploaded().size();
								int total = UploadService.getNbTotal();
								int progress = media.getProgress();
								progressContainer.setVisibility(View.VISIBLE);
								message.setVisibility(View.GONE);
								title.setText(media.getName());
								long pause = 0;
								if (media.getTimestampRetry() > 0 && media.getTimestampRetry() < Long.MAX_VALUE) {
									pause = Math.max(0, media.getTimestampRetry() - System.currentTimeMillis());
								}
								String progressd = (progress / 10d) + "% - " + ToolString.formatDuration(System.currentTimeMillis() - media.getTimestampUploadStarted()) + " - " + currentPosition
										+ " / " + total;
								if (media.getRetries() > 0) {
									String duration;
									if (pause > 0) {
										duration = " in " + ToolString.formatDuration(pause) + " : ";
									} else {
										duration = " : ";
									}
									subTitle.setText("retry #" + media.getRetries() + duration + progressd);
								} else {
									if (pause > 0) {
										subTitle.setText("uploading in " + ToolString.formatDuration(pause));
									} else {
										subTitle.setText(progressd);
									}
								}

								CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
								if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
									BackgroundExecutor.execute(new Runnable() {
										@Override
										public void run() {
											final Bitmap bitmap = Utils.getBitmap(media, VIEW_SIZE.small);
											if (bitmap != null) {
												Utils.getCache().put(media.getPath() + "_" + VIEW_SIZE.small, bitmap);
											}
										}
									});
									imageView.setImageDrawable(null);
								} else {
									imageView.setImageDrawable(bitmapDrawable);
								}
							}

						}
					}
				}
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	@Override
	public void onProgress(Media media) {
		render();
	}

	@Override
	@UiThread
	public void onFinished(int nbUploaded, int nbError) {
		String text = nbUploaded + " media recently uploaded";
		if (nbError > 0) {
			text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
		}
		setMessage(text, 3000);
	}

	@Override
	@UiThread
	public void onProcessed(Media media) {
		if (media.isFailed()) {
			setMessage("Error uploading " + media.getName(), 3000);
		}
	}
}
