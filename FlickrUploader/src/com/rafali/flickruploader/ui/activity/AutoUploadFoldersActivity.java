package com.rafali.flickruploader.ui.activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.auto_upload_folders_activity)
public class AutoUploadFoldersActivity extends Activity implements OnItemClickListener {

	private static final String DEFAULT_SET = "Default set (" + STR.instantUpload + ")";
	private static final String PREVIOUS_SET = "Existing set: ";
	private static final String EXISTING_SET = "Existing set...";
	private static final String NEW_SET = "New set...";
	private static final String DISABLE_AUTO_UPLOAD = "Disable auto-upload";
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploaderActivity.class);
	private static final int item_layout = R.layout.folder_list_thumb;

	AutoUploadFoldersActivity activity = this;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		load();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}

	@ViewById(R.id.list_view)
	ListView listView;

	private Map<String, String> cachedPhotoSets = Utils.getMapProperty("cachedPhotoSets");

	@Background
	void load() {
		List<Media> media = Utils.loadMedia();
		if (media != null) {
			folders = Utils.getFolders(media);
			for (Folder folder : folders) {
				foldersMap.put(folder.medias.get(0), folder);
			}
			render();
		}
		Map<String, String> photoSets = FlickrApi.getPhotoSets(true);
		if (photoSets != null && !photoSets.isEmpty()) {
			Utils.setMapProperty("cachedPhotoSets", photoSets);
			cachedPhotoSets = photoSets;
		}
		refresh();
	}

	@UiThread
	void refresh() {
		for (int i = 0; i < listView.getChildCount(); i++) {
			View view = listView.getChildAt(i);
			renderImageView(view);
		}
	}

	@UiThread
	void render() {
		if (folders != null && listView != null) {
			listView.setAdapter(new PhotoAdapter());
			listView.setOnItemClickListener(this);
		}
	}

	List<Folder> folders;
	Map<Media, Folder> foldersMap = new HashMap<Media, Folder>();

	class PhotoAdapter extends BaseAdapter {

		public PhotoAdapter() {
		}

		@Override
		public int getCount() {
			return folders.size();
		}

		@Override
		public Object getItem(int position) {
			return folders.get(position).medias.get(0);
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			Object item = getItem(position);
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(item_layout, parent, false);
				convertView.setTag(R.id.image_view, convertView.findViewById(R.id.image_view));
				convertView.setTag(R.id.title, convertView.findViewById(R.id.title));
				convertView.setTag(R.id.sub_title, convertView.findViewById(R.id.sub_title));
			}
			final Media media = (Media) item;
			if (convertView.getTag() != media) {
				convertView.setTag(media);
				renderImageView(convertView);
			}
			return convertView;
		}
	}

	String[] previousSet;

	@Override
	public void onItemClick(final AdapterView<?> arg0, final View convertView, final int arg2, final long arg3) {
		if (Utils.isPremium() || Utils.isTrial()) {
			if (convertView.getTag() instanceof Media) {
				final Folder folder = foldersMap.get(convertView.getTag());
				String title = "Auto-upload " + folder.name + " to";
				ArrayList<String> itemsList = Lists.newArrayList(DEFAULT_SET, NEW_SET, EXISTING_SET);
				if (previousSet != null) {
					itemsList.add(PREVIOUS_SET + previousSet[1]);
				}
				if (Utils.isAutoUpload(folder)) {
					itemsList.add(DISABLE_AUTO_UPLOAD);
				}
				final String[] items = itemsList.toArray(new String[0]);
				new AlertDialog.Builder(this).setTitle(title).setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true).setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String item = items[which];
						LOG.debug(item);
						if (which <= 2) {
							if (item.equals(DEFAULT_SET)) {
								setAutoUploaded(folder, true, STR.instantUpload);
							} else if (item.equals(NEW_SET)) {
								FlickrUploaderActivity.showNewSetDialog(activity, folder.name, new Utils.Callback<String>() {
									@Override
									public void onResult(String result) {
										LOG.debug(result);
										if (ToolString.isNotBlank(result)) {
											createSetForFolder(folder, result);
										}
									}
								});
							} else if (item.equals(EXISTING_SET)) {
								FlickrUploaderActivity.showExistingSetDialog(activity, new Utils.Callback<String[]>() {
									@Override
									public void onResult(String[] result) {
										previousSet = result;
										LOG.debug(Arrays.toString(result));
										setAutoUploaded(folder, true, result[1]);
									}
								}, cachedPhotoSets);
							} else {
								LOG.error("unknown item:" + item);
							}
						} else {
							if (item.equals(DISABLE_AUTO_UPLOAD)) {
								setAutoUploaded(folder, false, null);
							} else if (previousSet != null && item.contains(PREVIOUS_SET) && item.contains(previousSet[1])) {
								setAutoUploaded(folder, true, previousSet[1]);
							} else {
								LOG.error("unknown item:" + item);
							}
						}
					}

				}).show();
			}
		} else {
			Utils.showPremiumDialog(activity, new Utils.Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
					if (result) {
						onItemClick(arg0, convertView, arg2, arg3);
					}
				}
			});
		}
	}

	@Background
	void setAutoUploaded(Folder folder, boolean synced, String setTitle) {
		Utils.setAutoUploaded(folder, synced, setTitle);
		refresh();
	}

	ProgressDialog progressDialog;

	@UiThread
	void showLoading(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(this);
		}
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setIndeterminate(true);
		progressDialog.show();
	}

	@UiThread
	void hideLoading() {
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}

	@Background
	void createSetForFolder(Folder folder, String photoSetTitle) {
		setAutoUploaded(folder, true, photoSetTitle);
	}

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private void renderImageView(final View convertView) {
		if (convertView.getTag() instanceof Media) {
			final Media media = (Media) convertView.getTag();
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			imageView.setTag(media);
			Folder folder = foldersMap.get(media);
			((TextView) convertView.getTag(R.id.title)).setText(folder.name + " (" + folder.size + ")");
			boolean autoUpload = Utils.isAutoUpload(folder);
			String summary;
			TextView subTitle = (TextView) convertView.getTag(R.id.sub_title);
			if (autoUpload) {
				String photoSetTitle = Utils.getFoldersSetNames().get(folder.path);
				if (photoSetTitle == null) {
					photoSetTitle = STR.instantUpload;
				}
				summary = "⇒ Flickr (" + photoSetTitle + ")";
				subTitle.setTextColor(Color.WHITE);
			} else {
				summary = "not monitored";
				subTitle.setTextColor(getResources().getColor(R.color.midgray));
			}
			subTitle.setText(summary);

			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + VIEW_SIZE.small);
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
			}

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

						runOnUiThread(new Runnable() {
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
