package com.rafali.flickruploader.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EViewGroup;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.enums.STATUS;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.service.UploadService.UploadProgressListener;
import com.rafali.flickruploader.tool.Notifications;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;
import com.rafali.flickruploader.ui.widget.TabView;
import com.rafali.flickruploader2.R;

@EViewGroup(R.layout.drawer_content)
public class DrawerContentView extends RelativeLayout implements UploadProgressListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DrawerContentView.class);

	public DrawerContentView(Context context, AttributeSet attrs) {
		super(context, attrs);
		activity = (FlickrUploaderActivity) getContext();
	}

	FlickrUploaderActivity activity = null;

	@ViewById(R.id.pause_btn)
	TextView pauseBtn;

	@ViewById(R.id.clear_btn)
	TextView clearBtn;

	@ViewById(R.id.container)
	LinearLayout container;

	PhotoAdapter queuedAdapter;
	PhotoAdapter uploadedAdapter;
	PhotoAdapter failedAdapter;

	@Click(R.id.bottom_bar)
	void onBottomBarClick() {
		// do nothing
	}

	@UiThread
	void renderButtons() {
		if (queueTabView.getCurrentItem() == TAB_UPLOADED_INDEX) {
			pauseBtn.setVisibility(View.GONE);
			clearBtn.setVisibility(View.GONE);
		} else if (queueTabView.getCurrentItem() == TAB_QUEUED_INDEX) {
			clearBtn.setVisibility(View.VISIBLE);
			pauseBtn.setVisibility(View.VISIBLE);
			if (isUploadManuallyPaused()) {
				pauseBtn.setText("Resume");
			} else {
				pauseBtn.setText("Pause");
			}
		} else if (queueTabView.getCurrentItem() == TAB_FAILED_INDEX) {
			clearBtn.setVisibility(View.VISIBLE);
			pauseBtn.setVisibility(View.VISIBLE);
			pauseBtn.setText("Retry all");
		}
	}

	boolean isUploadManuallyPaused() {
		return System.currentTimeMillis() < Utils.getLongProperty(STR.manuallyPaused);
	}

	public void setCurrentTab(int tabIndex) {
		queueTabView.setCurrentItem(tabIndex);
	}

	@Click(R.id.pause_btn)
	void onPauseClick() {
		if (queueTabView.getCurrentItem() == TAB_QUEUED_INDEX) {
			if (isUploadManuallyPaused()) {
				Utils.setLongProperty(STR.manuallyPaused, 0L);
				renderButtons();
				UploadService.wake();
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
				builder.setTitle("Pause uploads").setItems(new String[] { "for 30 minutes", "for 2 hours", "for 12 hours", "indefinitely" }, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						long delay = 0;
						if (which == 0) {
							delay = 30 * 60 * 1000L;
						} else if (which == 1) {
							delay = 2 * 60 * 60 * 1000L;
						} else if (which == 2) {
							delay = 12 * 60 * 60 * 1000L;
						} else if (which == 3) {
							delay = Long.MAX_VALUE;
						}
						Utils.setLongProperty(STR.manuallyPaused, delay == Long.MAX_VALUE ? delay : System.currentTimeMillis() + delay);
					}
				});
				AlertDialog dialog = builder.create();
				dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						renderButtons();
					}
				});
				dialog.show();
			}
		} else if (queueTabView.getCurrentItem() == TAB_FAILED_INDEX) {
			List<Media> medias = Utils.loadMedia(false);
			Iterator<Media> it = medias.iterator();
			while (it.hasNext()) {
				Media media = it.next();
				if (!media.isFailed()) {
					it.remove();
				}
			}
			if (!medias.isEmpty()) {
				UploadService.enqueueRetry(medias);
			}
		}
	}

	public static final int TAB_UPLOADED_INDEX = 0;
	public static final int TAB_QUEUED_INDEX = 1;
	public static final int TAB_FAILED_INDEX = 2;

	@Click(R.id.clear_btn)
	void onClearClick() {
		final int tab = queueTabView.getCurrentItem();
		if (tab == TAB_QUEUED_INDEX && !UploadService.getCurrentlyQueued().isEmpty()) {
			Utils.showConfirmCancel(activity, "Cancel uploads", "Do you confirm aborting the current upload and clear the upload queue?", new Utils.Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
					if (result) {
						clearTab(tab);
					}
				}
			});
		} else {
			clearTab(tab);
		}
	}

	private void clearTab(int tab) {
		Notifications.clear();
		UploadService.clear(tab == TAB_QUEUED_INDEX ? STATUS.QUEUED : STATUS.FAILED, new Utils.Callback<Void>() {
			@Override
			public void onResult(Void result) {
				updateLists();
			}
		});
	}

	@AfterViews
	void afterViews() {
		queueTabView = new QueueTabView(getContext());
		container.addView(queueTabView);
		renderButtons();
	}

	View createEmptyView(String text) {
		TextView tv = (TextView) View.inflate(getContext(), R.layout.no_data, null);
		tv.setText(text);
		return tv;
	}

	public void updateLists() {
		if (activity != null && !activity.destroyed && !activity.isPaused() && activity.getSlidingDrawer() != null && activity.getSlidingDrawer().isOpened()) {
			BackgroundExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						List<Media> currentlyQueued = new ArrayList<Media>(UploadService.getCurrentlyQueued());
						Collections.sort(currentlyQueued, Utils.MEDIA_COMPARATOR);
						Collections.reverse(currentlyQueued);
						notifyDataSetChanged(queuedAdapter, currentlyQueued);
						List<Media> recentlyUploaded = new ArrayList<Media>(UploadService.getRecentlyUploaded());
						Collections.sort(recentlyUploaded, Utils.MEDIA_COMPARATOR_UPLOAD);
						notifyDataSetChanged(uploadedAdapter, recentlyUploaded);
						List<Media> failed = new ArrayList<Media>(UploadService.getFailed());
						Collections.sort(failed, Utils.MEDIA_COMPARATOR);
						notifyDataSetChanged(failedAdapter, failed);
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			});
		}
	}

	@UiThread
	void notifyDataSetChanged(PhotoAdapter photoAdapter, List<Media> medias) {
		if (!medias.equals(photoAdapter.medias)) {
			photoAdapter.medias.clear();
			photoAdapter.medias.addAll(medias);
			photoAdapter.notifyDataSetChanged();
		}
	}

	class QueueTabView extends TabView {

		public QueueTabView(Context context) {
			super(context, null, 3, TAB_QUEUED_INDEX);
		}

		@Override
		protected View createTabViewItem(int position) {
			View view = View.inflate(getContext(), R.layout.drawer_list_view, null);
			ListView listView = (ListView) view.findViewById(R.id.list_view);
			TextView emptyView = (TextView) view.findViewById(R.id.empty_list_item);
			listView.setEmptyView(emptyView);
			if (position == TAB_UPLOADED_INDEX) {
				uploadedAdapter = new PhotoAdapter(new ArrayList<Media>(), getTabViewItemTitle(position));
				listView.setAdapter(uploadedAdapter);
				emptyView.setText("No media recently uploaded");
			} else if (position == TAB_QUEUED_INDEX) {
				queuedAdapter = new PhotoAdapter(new ArrayList<Media>(), getTabViewItemTitle(position));
				listView.setAdapter(queuedAdapter);
				listView.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						if (view.getTag() instanceof Media) {
							final Media media = (Media) view.getTag();
							Utils.showConfirmCancel(activity, "Stop uploading", "Abort uploading " + media.getPath() + " - " + ToolString.readableFileSize(media.getSize()) + " ?",
									new Utils.Callback<Boolean>() {
										@Override
										public void onResult(Boolean result) {
											if (result) {
												UploadService.dequeue(Arrays.asList(media));
											}
										}
									});
						}
					}
				});
				emptyView.setText("No media queued for upload");
			} else if (position == TAB_FAILED_INDEX) {
				failedAdapter = new PhotoAdapter(new ArrayList<Media>(), getTabViewItemTitle(position));
				listView.setAdapter(failedAdapter);
				emptyView.setText("No upload errors");
			}
			return view;
		}

		@Override
		protected int getTabViewItemTitle(int position) {
			if (position == TAB_UPLOADED_INDEX) {
				return R.string.uploaded;
			} else if (position == TAB_QUEUED_INDEX) {
				return R.string.queued;
			} else if (position == TAB_FAILED_INDEX) {
				return R.string.failed;
			}
			return R.string.ellipsis;
		}

		@Override
		public void onPageSelected(int position) {
			super.onPageSelected(position);
			renderView(position);
			renderButtons();
		}

		private void renderView(int position) {
			View view = gridViewsArray[position];
			if (view != null) {
				ListView listView = (ListView) view.findViewById(R.id.list_view);
				for (int i = 0; i < listView.getChildCount(); i++) {
					renderThumbView(listView.getChildAt(i));
				}
			}
		}

	}

	class PhotoAdapter extends BaseAdapter {

		private List<Media> medias;
		private int titleRes;

		public PhotoAdapter(List<Media> medias, int titleRes) {
			this.medias = medias;
			this.titleRes = titleRes;
		}

		@Override
		public int getCount() {
			return medias.size();
		}

		@Override
		public Object getItem(int position) {
			return medias.get(position);
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = View.inflate(getContext(), R.layout.upload_status_thumb, null);
				convertView.setTag(R.id.image_view, convertView.findViewById(R.id.image_view));
				convertView.setTag(R.id.title, convertView.findViewById(R.id.title));
				convertView.setTag(R.id.list_view, titleRes);
			}
			final Media media = medias.get(position);
			if (convertView.getTag() != media) {
				convertView.setTag(media);
				renderThumbView(convertView);
			}
			return convertView;
		}

	}

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private QueueTabView queueTabView;

	private void renderThumbView(final View convertView) {
		final Media media = (Media) convertView.getTag();
		if (media != null) {
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			final TextView titleView = (TextView) convertView.getTag(R.id.title);
			int titleRes = (Integer) convertView.getTag(R.id.list_view);
			String text = media.getPath() + " - " + ToolString.readableFileSize(media.getSize());
			imageView.setTag(media);
			int nbError = media.getRetries();
			if (titleRes == R.string.queued) {
				long since = System.currentTimeMillis() - media.getTimestampCreated();
				text += "\ncreated " + ToolString.formatDuration(since) + " ago";
				if (nbError > 0) {
					text += "\nretry #" + nbError;
					if (ToolString.isNotBlank(media.getErrorMessage())) {
						text += "\n" + media.getErrorMessage();
					}
				}
				if (media.getTimestampRetry() < Long.MAX_VALUE && media.getTimestampRetry() > 0 && System.currentTimeMillis() < media.getTimestampRetry()) {
					text += "\nuploading in " + ToolString.formatDuration(media.getTimestampRetry() - System.currentTimeMillis());
				}
			} else if (titleRes == R.string.failed) {
				long since;
				if (media.getTimestampRetry() > 0 && media.getTimestampRetry() < Long.MAX_VALUE) {
					since = System.currentTimeMillis() - media.getTimestampRetry();
				} else {
					since = System.currentTimeMillis() - media.getTimestampQueued();
				}
				text += "\nfailed " + ToolString.formatDuration(since) + " ago";
				if (media.getRetries() > 0) {
					text += "\nafter " + media.getRetries() + " retr" + (media.getRetries() > 1 ? "ies" : "y");
				}
			} else if (titleRes == R.string.uploaded) {
				long since = System.currentTimeMillis() - media.getTimestampUploaded();
				if (since > 0) {
					text += "\nuploaded " + ToolString.formatDuration(since) + " ago";
					if (media.getRetries() > 0) {
						text += "\nafter " + media.getRetries() + " retr" + (media.getRetries() > 1 ? "ies" : "y");
					}
				}
			}
			titleView.setText(text);
			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
				executorService.submit(new Runnable() {
					@Override
					public void run() {
						if (imageView.getTag() == media) {
							final CacheableBitmapDrawable bitmapDrawable;
							if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
								bitmapDrawable = wrapper;
							} else {
								Bitmap bitmap = Utils.getBitmap(media, VIEW_SIZE.small);
								if (bitmap != null) {
									bitmapDrawable = Utils.getCache().put(media.getPath() + "_" + VIEW_SIZE.small, bitmap);
								} else {
									bitmapDrawable = null;
								}
							}

							((Activity) getContext()).runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (imageView.getTag() == media) {
										if (wrapper != bitmapDrawable) {
											imageView.setImageDrawable(bitmapDrawable);
										}
									}
								}
							});
						}
					}
				});
			}

		}
	}

	@Override
	public void onProgress(Media media) {

	}

	@Override
	public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		updateLists();
	}

	@Override
	public void onDequeued(int nbDequeued) {
		updateLists();
	}

	@Override
	public void onFinished(int nbUploaded, int nbErrors) {
		updateLists();
	}

	@Override
	public void onProcessed(Media media) {
		updateLists();
	}

	@UiThread
	public void renderCurrentView() {
		if (activity != null && !activity.isPaused()) {
			queueTabView.renderView(queueTabView.getCurrentItem());
		}
	}

}
