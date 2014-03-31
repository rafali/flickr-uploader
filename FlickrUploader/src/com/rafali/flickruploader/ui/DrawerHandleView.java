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
		checkStatus();
	}

	@UiThread
	void renderProgress(int progress, final Media media, int currentPosition, int total) {
		if (System.currentTimeMillis() > messageUntil) {
			progressContainer.setVisibility(View.VISIBLE);
			message.setVisibility(View.GONE);
			title.setText(media.getName());
			subTitle.setText(progress + "% - " + currentPosition + " / " + total);

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

	@Override
	public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		String str = nbQueued + " media queued";
		if (nbAlreadyQueued > 0) {
			str += ", " + nbAlreadyQueued + " already queued";
		}
		if (nbAlreadyUploaded > 0) {
			str += ", " + nbAlreadyUploaded + " already uploaded";
		}
		setMessage(str, 4000);
		checkStatus();
	}

	long messageUntil = System.currentTimeMillis();

	@UiThread
	void setMessage(String text, int duration) {
		long canShow = System.currentTimeMillis() - messageUntil;
		LOG.debug(canShow + " : " + text);
		if (canShow > 0) {
			messageUntil = System.currentTimeMillis() + duration;
			progressContainer.setVisibility(View.GONE);
			message.setVisibility(View.VISIBLE);
			message.setText(text);
		}
	}

	int nbMonitored = -1;

	public void onActivityResume() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				nbMonitored = Utils.getFoldersMonitoredNb();
			}
		});
		checkStatus();
	}

	@UiThread
	void checkStatus() {
		FlickrUploaderActivity activity = null;
		try {
			activity = (FlickrUploaderActivity) getContext();
			if (message != null && activity != null && !activity.isPaused()) {
				long canShow = System.currentTimeMillis() - messageUntil;
				if (canShow > 4000) {
					if (!FlickrApi.isAuthentified()) {
						message.setText("Login into Flickr from the Preferences");
					} else if (!Utils.isPremium() && !Utils.isTrial()) {
						message.setText("Trial version as expired");
					} else if (UploadService.getNbQueued() == 0) {
						String text = "No media queued";
						int nbUploaded = UploadService.getNbUploadedTotal();
						if (nbUploaded > 0) {
							text += ", " + nbUploaded + " recently uploaded";
						}
						int nbError = UploadService.getNbError();
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

						message.setText(text);
					} else {
						if (UploadService.isPaused()) {
							progressContainer.setVisibility(View.GONE);
							message.setVisibility(View.VISIBLE);
							CAN_UPLOAD canUploadNow = Utils.canUploadNow();
							if (canUploadNow == CAN_UPLOAD.ok) {
								message.setText("Upload paused, should resume soon");
							} else if (canUploadNow == CAN_UPLOAD.manually) {
								long pausedUntil = Utils.getLongProperty(STR.manuallyPaused);
								if (pausedUntil == Long.MAX_VALUE) {
									message.setText("Upload paused by user");
								} else {
									message.setText("Upload paused by user, will auto resume in " + ToolString.formatDuration(pausedUntil - System.currentTimeMillis()));
								}
							} else {
								message.setText("Upload paused, waiting for " + canUploadNow);
							}
						} else {
							message.setText("Uploading " + UploadService.getNbQueued() + " media");
						}
					}
				}
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	@Override
	public void onProgress(Media media, int mediaProgress, int queueProgress, int queueTotal) {
		renderProgress(mediaProgress, media, queueProgress, queueTotal);
	}

	@Override
	public void onPaused() {
		// TODO Auto-generated method stub

	}

	@Override
	@UiThread
	public void onFinished(int nbUploaded, int nbError) {
		String text = nbUploaded + " media uploaded";
		if (nbError > 0) {
			text += ", " + nbError + " error" + (nbError > 1 ? "s" : "");
		}
		setMessage(text, 5000);
	}

	@Override
	@UiThread
	public void onProcessed(Media media, boolean success) {
		if (!success) {
			setMessage("Error uploading " + media.getName(), 5000);
		}
	}
}
