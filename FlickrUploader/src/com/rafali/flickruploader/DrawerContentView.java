package com.rafali.flickruploader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EViewGroup;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.rafali.flickruploader.FlickrUploaderActivity.TAB;
import com.rafali.flickruploader.UploadService.UploadProgressListener;

@EViewGroup(R.layout.drawer_content)
public class DrawerContentView extends RelativeLayout implements UploadProgressListener {

	public DrawerContentView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@ViewById(R.id.pause_btn)
	Button pauseBtn;

	@ViewById(R.id.clear_btn)
	Button clearBtn;

	@ViewById(R.id.container)
	LinearLayout container;

	PhotoAdapter queuedAdapter;
	PhotoAdapter uploadedAdapter;
	PhotoAdapter failedAdapter;

	@Click(R.id.bottom_bar)
	void onBottomBarClick() {
		// do nothing
	}

	@AfterViews
	void afterViews() {
		container.addView(new QueueTabView(getContext()));
		updateLists();
	}

	View createEmptyView(String text) {
		TextView tv = (TextView) View.inflate(getContext(), R.layout.no_data, null);
		tv.setText(text);
		return tv;
	}

	@Background
	public void updateLists() {
		List<Media> queuedMedias = UploadService.getQueueSnapshot();
		List<Media> uploadedMedias = UploadService.getRecentlyUploadedSnapshot();
		List<Media> failedMedias = UploadService.getFailedSnapshot();
		notifyDataSetChanged(queuedAdapter, queuedMedias);
		notifyDataSetChanged(uploadedAdapter, uploadedMedias);
		notifyDataSetChanged(failedAdapter, failedMedias);
	}

	@UiThread
	void notifyDataSetChanged(PhotoAdapter photoAdapter, List<Media> medias) {
		photoAdapter.medias.clear();
		photoAdapter.medias.addAll(medias);
		photoAdapter.notifyDataSetChanged();
	}

	class QueueTabView extends TabView {

		public QueueTabView(Context context) {
			super(context, null, 3, 1);
		}

		@Override
		protected View createTabViewItem(int position) {
			View view = View.inflate(getContext(), R.layout.drawer_list_view, null);
			ListView listView = (ListView) view.findViewById(R.id.list_view);
			TextView emptyView = (TextView) view.findViewById(R.id.empty_list_item);
			listView.setEmptyView(emptyView);
			if (position == 0) {
				uploadedAdapter = new PhotoAdapter(new ArrayList<Media>());
				listView.setAdapter(uploadedAdapter);
				emptyView.setText("No media has been uploaded recently");
			} else if (position == 1) {
				queuedAdapter = new PhotoAdapter(new ArrayList<Media>());
				listView.setAdapter(queuedAdapter);
				emptyView.setText("No media has been queued for upload");
			} else if (position == 2) {
				failedAdapter = new PhotoAdapter(new ArrayList<Media>());
				listView.setAdapter(failedAdapter);
				emptyView.setText("No upload errors");
			}
			return view;
		}

		@Override
		protected int getTabViewItemTitle(int position) {
			if (position == 0) {
				return R.string.uploaded;
			} else if (position == 1) {
				return R.string.queued;
			} else if (position == 2) {
				return R.string.failed;
			}
			return R.string.ellipsis;
		}

		@Override
		public void onPageSelected(int position) {
			super.onPageSelected(position);
		}

	}

	class PhotoAdapter extends BaseAdapter {

		private List<Media> medias;

		public PhotoAdapter(List<Media> medias) {
			this.medias = medias;
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

	private void renderImageView(final View convertView) {
		final Media image = (Media) convertView.getTag();
		if (image != null) {
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			final TextView titleView = (TextView) convertView.getTag(R.id.title);
			final TextView subTitleView = (TextView) convertView.getTag(R.id.sub_title);
			titleView.setText(image.path);
			subTitleView.setText("");
			imageView.setTag(image);
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
	}

	@Override
	public void onFinished(int nbUploaded, int nbErrors) {
		updateLists();
	}

	@Override
	public void onProcessed(Media image, boolean success) {
		updateLists();
	}
}
