package com.rafali.flickruploader;

import java.util.ArrayList;
import java.util.Collections;
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
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploaderActivity.TAB;
import com.rafali.flickruploader.UploadService.UploadProgressListener;

@EViewGroup(R.layout.drawer_content)
public class DrawerContentView extends RelativeLayout implements UploadProgressListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DrawerContentView.class);

	public DrawerContentView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

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
	void render() {
		if (queueTabView.getCurrentItem() == TAB_UPLOADED_INDEX) {
			pauseBtn.setVisibility(View.GONE);
		} else if (queueTabView.getCurrentItem() == TAB_QUEUED_INDEX) {
			pauseBtn.setVisibility(View.VISIBLE);
			if (isUploadManuallyPaused()) {
				pauseBtn.setText("Resume");
			} else {
				pauseBtn.setText("Pause");
			}
		} else if (queueTabView.getCurrentItem() == TAB_FAILED_INDEX) {
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
				render();
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
						render();
					}
				});
				dialog.show();
			}
		} else if (queueTabView.getCurrentItem() == TAB_FAILED_INDEX) {
			List<Media> failedMedias = UploadService.getFailedSnapshot();
			if (!failedMedias.isEmpty()) {
				UploadService.enqueueRetry(failedMedias);
			}
		}
	}

	public static final int TAB_UPLOADED_INDEX = 0;
	public static final int TAB_QUEUED_INDEX = 1;
	public static final int TAB_FAILED_INDEX = 2;

	@Click(R.id.clear_btn)
	void onClearClick() {
		Notifications.clear();
		if (queueTabView.getCurrentItem() == TAB_UPLOADED_INDEX) {
			UploadService.clearUploaded();
		} else if (queueTabView.getCurrentItem() == TAB_QUEUED_INDEX) {
			UploadService.clearQueued();
		} else if (queueTabView.getCurrentItem() == TAB_FAILED_INDEX) {
			UploadService.clearFailed();
			FlickrApi.unretryable.clear();
		}
		updateLists();
	}

	@AfterViews
	void afterViews() {
		queueTabView = new QueueTabView(getContext());
		container.addView(queueTabView);
		render();
		updateLists();
		checkStatus();
	}

	View createEmptyView(String text) {
		TextView tv = (TextView) View.inflate(getContext(), R.layout.no_data, null);
		tv.setText(text);
		return tv;
	}

	public void updateLists() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					List<Media> queuedMedias = UploadService.getQueueSnapshot();
					Collections.reverse(queuedMedias);
					List<Media> uploadedMedias = UploadService.getRecentlyUploadedSnapshot();
					List<Media> failedMedias = UploadService.getFailedSnapshot();
					notifyDataSetChanged(queuedAdapter, queuedMedias);
					notifyDataSetChanged(uploadedAdapter, uploadedMedias);
					notifyDataSetChanged(failedAdapter, failedMedias);
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			}
		});
	}

	@UiThread
	void notifyDataSetChanged(PhotoAdapter photoAdapter, List<Media> medias) {
		photoAdapter.medias.clear();
		photoAdapter.medias.addAll(medias);
		photoAdapter.notifyDataSetChanged();
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
				emptyView.setText("No media has been uploaded recently");
			} else if (position == TAB_QUEUED_INDEX) {
				queuedAdapter = new PhotoAdapter(new ArrayList<Media>(), getTabViewItemTitle(position));
				listView.setAdapter(queuedAdapter);
				emptyView.setText("No media has been queued for upload");
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
			render();
		}

		private void renderView(int position) {
			View view = gridViewsArray[position];
			if (view != null) {
				ListView listView = (ListView) view.findViewById(R.id.list_view);
				for (int i = 0; i < listView.getChildCount(); i++) {
					renderImageView(listView.getChildAt(i));
				}
			}
		}

		void renderCurrentView() {
			renderView(getCurrentItem());
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
				convertView.setTag(R.id.sub_title, convertView.findViewById(R.id.sub_title));
				convertView.setTag(R.id.list_view, titleRes);
			}
			final Media image = medias.get(position);
			if (convertView.getTag() != image) {
				convertView.setTag(image);
				renderImageView(convertView);
			}
			return convertView;
		}

	}

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private QueueTabView queueTabView;

	private void renderImageView(final View convertView) {
		final Media image = (Media) convertView.getTag();
		if (image != null) {
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			final TextView titleView = (TextView) convertView.getTag(R.id.title);
			final TextView subTitleView = (TextView) convertView.getTag(R.id.sub_title);
			int titleRes = (Integer) convertView.getTag(R.id.list_view);
			titleView.setText(image.path);
			subTitleView.setText("");
			imageView.setTag(image);
			int nbError = UploadService.getNbError(image);
			if (titleRes == R.string.queued) {
				if (nbError > 0) {
					subTitleView.setText("retry #" + (nbError + 1));
				} else {
					subTitleView.setText("");
				}
			} else if (titleRes == R.string.failed) {
				String text = "";
				if (FlickrApi.unretryable.contains(image)) {
					text = "unretryable error";
				} else {
					long delay = UploadService.getRetryDelay(image);
					if (nbError > 0) {
						text = nbError + " error" + (nbError > 1 ? "s" : "");
					}
					if (delay > 0) {
						if (!text.isEmpty()) {
							text += ", ";
						}
						text += "retrying in " + ToolString.formatDuration(delay - System.currentTimeMillis());
					}
				}
				Throwable lastException = FlickrApi.getLastException(image);
				if (lastException != null) {
					text += "\n" + lastException.getClass().getSimpleName() + " : " + lastException.getMessage();
				}
				subTitleView.setText(text);
			}
			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(image.path + "_" + R.layout.photo_grid_thumb);
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
				executorService.submit(new Runnable() {
					@Override
					public void run() {
						if (imageView.getTag() == image) {
							final CacheableBitmapDrawable bitmapDrawable;
							if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
								bitmapDrawable = wrapper;
							} else {
								Bitmap bitmap = Utils.getBitmap(image, TAB.photo);
								if (bitmap != null) {
									bitmapDrawable = Utils.getCache().put(image.path + "_" + R.layout.photo_grid_thumb, bitmap);
								} else {
									bitmapDrawable = null;
								}
							}

							((Activity) getContext()).runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (imageView.getTag() == image) {
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
	public void onProgress(int progress, Media image) {
	}

	@Override
	public void onQueued(int nbQueued, int nbAlreadyUploaded, int nbAlreadyQueued) {
		updateLists();
	}

	@Override
	public void onPaused() {
		updateLists();
	}

	@Override
	public void onFinished(int nbUploaded, int nbErrors) {
		updateLists();
	}

	@Override
	public void onProcessed(Media image, boolean success) {
		updateLists();
	}

	@UiThread(delay = 1000)
	void checkStatus() {
		FlickrUploaderActivity activity = null;
		try {
			activity = (FlickrUploaderActivity) getContext();
			if (activity != null && !activity.isPaused()) {
				queueTabView.renderCurrentView();
			}
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		} finally {
			if (activity != null && !activity.destroyed) {
				checkStatus();
			}
		}
	}
}
