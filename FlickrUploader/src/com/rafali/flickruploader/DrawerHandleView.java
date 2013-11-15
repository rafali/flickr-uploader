package com.rafali.flickruploader;

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
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploaderActivity.TAB;
import com.rafali.flickruploader.UploadService.UploadProgressListener;
import com.rafali.flickruploader.Utils.CAN_UPLOAD;

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
	void renderProgress(int progress, final Media image, int currentPosition, int total) {
		if (System.currentTimeMillis() > messageUntil) {
			progressContainer.setVisibility(View.VISIBLE);
			message.setVisibility(View.GONE);
			title.setText(image.name);
			subTitle.setText(progress + "% - " + currentPosition + " / " + total);

			CacheableBitmapDrawable bitmapDrawable = Utils.getCache().getFromMemoryCache(image.path + "_" + R.layout.photo_grid_thumb);
			if (bitmapDrawable == null || bitmapDrawable.getBitmap().isRecycled()) {
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap bitmap = Utils.getBitmap(image, TAB.photo);
						if (bitmap != null) {
							Utils.getCache().put(image.path + "_" + R.layout.photo_grid_thumb, bitmap);
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

	@UiThread(delay = 1000)
	void checkStatus() {
		FlickrUploaderActivity activity = null;
		try {
			activity = (FlickrUploaderActivity) getContext();
			if (activity != null && !activity.isPaused()) {
				long canShow = System.currentTimeMillis() - messageUntil;
				if (canShow > 4000) {
					if (!Utils.isPremium() && !Utils.isTrial()) {
						message.setText("Click on the menu and select 'Trial Info'");
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
							boolean photoAutoUpload = Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true);
							boolean videoAutoUpload = Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true);
							if (photoAutoUpload && videoAutoUpload) {
								text += ", photos/videos auto-upload enabled";
							} else if (photoAutoUpload) {
								text += ", photos auto-upload enabled";
							} else if (videoAutoUpload) {
								text += ", videos auto-upload enabled";
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
		} finally {
			if (activity != null && !activity.destroyed) {
				checkStatus();
			}
		}
	}

	@Override
	@UiThread
	public void onProgress(int progress, Media image) {
		renderProgress(progress, image, UploadService.getNbUploaded() + 1, UploadService.getTotal());
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
	public void onProcessed(Media image, boolean success) {
		if (!success) {
			setMessage("Error uploading " + image.name, 5000);
		}
	}
}
