package com.rafali.flickruploader.ui.activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.paypal.android.sdk.payments.PayPalService;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrUploader;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.enums.MEDIA_TYPE;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.enums.VIEW_GROUP_TYPE;
import com.rafali.flickruploader.enums.VIEW_SIZE;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.service.UploadService.BasicUploadProgressListener;
import com.rafali.flickruploader.service.UploadService.UploadProgressListener;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.tool.Utils.Callback;
import com.rafali.flickruploader.ui.DrawerContentView;
import com.rafali.flickruploader.ui.DrawerHandleView;
import com.rafali.flickruploader.ui.widget.SlidingDrawer;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView.Header;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView.HeaderAdapter;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.flickr_uploader_activity)
public class FlickrUploaderActivity extends Activity {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploaderActivity.class);

	private static FlickrUploaderActivity instance;
	private FlickrUploaderActivity activity = this;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		LOG.debug("onCreate " + bundle);
		load();
		UploadService.wake();
		if (!FlickrApi.isAuthentified()) {
			Utils.confirmSignIn(activity);
		}
		if (instance != null)
			instance.finish();
		instance = activity;
		Utils.checkPremium(false, new Utils.Callback<Boolean>() {
			@Override
			public void onResult(Boolean result) {
				checkPremium();
			}
		});
		handleIntent(getIntent());
	}

	@UiThread
	void checkPremium() {
		if (!Utils.isPremium() && !Utils.isTrial()) {
			Utils.showPremiumDialog(activity, new Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
				}
			});
		}
	}

	boolean finishOnClose = false;

	@Background
	void handleIntent(Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			String type = intent.getType();
			List<String> paths = null;
			if (Intent.ACTION_SEND.equals(action) && type != null) {
				if (type.startsWith("image/") || type.startsWith("video/")) {
					Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
					String path = Utils.getRealPathFromURI(imageUri);
					if (path != null) {
						paths = Arrays.asList(path);
					}
				}
				finishOnClose = true;
			} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
				if (type.startsWith("image/") || type.startsWith("video/")) {
					List<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
					paths = new ArrayList<String>();
					for (Uri imageUri : imageUris) {
						String path = Utils.getRealPathFromURI(imageUri);
						if (path != null) {
							paths.add(path);
						}
					}
				}
				finishOnClose = true;
			}
			if (paths != null && !paths.isEmpty()) {
				List<Media> medias = Utils.loadMedia(true);
				Iterator<Media> it = medias.iterator();
				while (it.hasNext()) {
					Media media = it.next();
					if (paths.contains(media.getPath())) {
						selectedMedia.add(media);
					}
				}
				if (selectedMedia.isEmpty()) {
					Utils.toast("No media found");
				} else {
					confirmUpload();
				}
			}
		}
	}

	SimpleDateFormat format = new SimpleDateFormat("MMMM, yyyy", Locale.US);

	List<Object> thumbItems;
	List<Media> medias;

	@Background
	void load() {
		medias = Utils.loadMedia(sync);
		sync = false;

		boolean showPhotos = Utils.getShowPhotos();
		boolean showVideos = Utils.getShowVideos();
		boolean showUploaded = Utils.getShowUploaded();
		boolean showNotUploaded = Utils.getShowNotUploaded();
		Iterator<Media> it = medias.iterator();
		while (it.hasNext()) {
			Media media = it.next();
			if (!showPhotos && media.isPhoto() || !showVideos && media.isVideo()) {
				it.remove();
			} else if (!showUploaded && media.isUploaded() || !showNotUploaded && !media.isUploaded()) {
				it.remove();
			}
		}

		if (Utils.getViewGroupType() == VIEW_GROUP_TYPE.date) {
			Collections.sort(medias, Utils.MEDIA_COMPARATOR);
		} else {
			Comparator<Media> folderComparator = new Comparator<Media>() {
				@Override
				public int compare(Media arg0, Media arg1) {
					int compareTo = arg0.getFolderName().compareTo(arg1.getFolderName());
					if (compareTo == 0) {
						compareTo = arg0.getFolderPath().compareTo(arg1.getFolderPath());
						if (compareTo == 0) {
							return Utils.MEDIA_COMPARATOR.compare(arg0, arg1);
						}
					}
					return compareTo;
				}
			};
			Collections.sort(medias, folderComparator);
		}

		computeHeaders(true);

		renderListView();
	}

	@UiThread
	void setLoading(int progress) {
		if (message != null) {
			message.setText("Loading… " + progress + "%");
		}
	}

	@UiThread
	void computeHeaders(boolean clearHeaders) {
		headers = new ArrayList<Header>();
		if (clearHeaders) {
			headerMap = new HashMap<Media, Header>();
			headerIds = new HashMap<String, Header>();
		}
		thumbItems = new ArrayList<Object>();
		computeNbColumn();

		Media[] mediaRow = null;
		int currentIndex = 0;

		for (Media media : medias) {
			Header header;
			if (Utils.getViewGroupType() == VIEW_GROUP_TYPE.date) {
				String id = format.format(new Date(media.getTimestampCreated()));
				header = headerIds.get(id);
				if (header == null) {
					header = new Header(id, id);
					headerIds.put(id, header);
				}
			} else {
				String id = media.getFolderPath();
				header = headerIds.get(id);
				if (header == null) {
					header = new Header(id, media.getFolderName());
					headerIds.put(id, header);
				}
			}

			if (!headers.contains(header)) {
				headers.add(header);
				thumbItems.add(header);
				mediaRow = null;
				header.count = 1;
			} else {
				header.count++;
			}

			if (header.collapsed) {
				continue;
			}

			headerMap.put(media, header);
			if (mediaRow == null || currentIndex >= mediaRow.length) {
				mediaRow = new Media[computed_nb_column];
				currentIndex = 0;
				thumbItems.add(mediaRow);
			}
			mediaRow[currentIndex] = media;
			currentIndex++;
		}
		if (listView != null) {
			renderHeader(listView.getFloatingSectionHeader());
			photoAdapter.notifyDataSetChanged();
		}
	}

	Map<Media, Header> headerMap;
	List<Header> headers;
	Map<String, Header> headerIds;

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance(activity).activityStart(activity);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance(activity).activityStop(activity);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		renderMenu();
		LOG.debug("onNewIntent : " + intent);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		LOG.debug("onConfigurationChanged");
		computeHeaders(false);
	}

	public static FlickrUploaderActivity getInstance() {
		return instance;
	}

	@Override
	protected void onDestroy() {
		LOG.debug("onDestroy");
		super.onDestroy();
		if (instance == this)
			instance = null;
		destroyed = true;
		UploadService.unregister(drawerHandleView);
		UploadService.unregister(drawerContentView);
		UploadService.unregister(uploadProgressListener);
		stopService(new Intent(this, PayPalService.class));
	}

	public boolean destroyed = false;

	PhotoAdapter photoAdapter = new PhotoAdapter();

	@ViewById(R.id.message)
	TextView message;

	@UiThread
	void renderListView() {
		if (listView != null) {
			if (listView.getAdapter() == null) {
				listView.setDividerHeight(0);
				listView.setAdapter(photoAdapter);
				listView.setFastScrollEnabled(true);
			} else {
				photoAdapter.notifyDataSetChanged();
			}
			if (medias != null && medias.isEmpty()) {
				message.setVisibility(View.VISIBLE);
				if (Utils.loadMedia(false).isEmpty()) {
					message.setText("No media found");
				} else {
					message.setText("No media matching your filter");
				}
			} else {
				message.setVisibility(View.GONE);
			}
		}
	}

	@ViewById(R.id.drawer_handle)
	DrawerHandleView drawerHandleView;

	@ViewById(R.id.drawer_content)
	DrawerContentView drawerContentView;

	@ViewById(R.id.slidingDrawer)
	SlidingDrawer slidingDrawer;

	@AfterViews
	void afterViews() {
		UploadService.register(drawerHandleView);
		UploadService.register(drawerContentView);
		UploadService.register(uploadProgressListener);
		getActionBar().setTitle(null);
		getActionBar().setIcon(null);
		getActionBar().setDisplayShowHomeEnabled(false);

		slidingDrawer.setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener() {
			@Override
			public void onDrawerOpened() {
				Utils.setBooleanProperty(STR.show_sliding_demo, false);
				drawerContentView.updateLists();
			}
		});
	}

	UploadProgressListener uploadProgressListener = new BasicUploadProgressListener() {
		@Override
		public void onProcessed(Media media) {
			if (!Utils.getShowUploaded() || !Utils.getShowNotUploaded()) {
				load();
			}
			update(media);
		}
	};

	void computeNbColumn() {
		Point size = new Point();
		WindowManager wm = (WindowManager) FlickrUploader.getAppContext().getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getSize(size);
		int screenWidthPx = size.x;
		VIEW_SIZE view_size = Utils.getViewSize();
		final int req_width;
		final int nb_column;
		if (view_size == VIEW_SIZE.small) {
			req_width = Utils.getScaledSize(64);
			nb_column = Math.max(4, screenWidthPx / req_width);
		} else if (view_size == VIEW_SIZE.large) {
			req_width = Utils.getScaledSize(192);
			nb_column = Math.max(2, screenWidthPx / req_width);
		} else {
			req_width = Utils.getScaledSize(128);
			nb_column = Math.max(3, screenWidthPx / req_width);
		}
		if (view_size == VIEW_SIZE.small) {
			computed_req_height = screenWidthPx / nb_column;
		} else {
			computed_req_height = screenWidthPx / nb_column * 3 / 4;
		}
		computed_req_width = screenWidthPx / nb_column;
		computed_nb_column = nb_column;
		// LOG.info(view_size + ", nb_column : " + nb_column +
		// ", screenWidthPx : " + screenWidthPx);
	}

	int computed_req_height = 100;
	int computed_req_width = 100;
	int computed_nb_column = 2;

	@UiThread
	void confirmUpload() {
		if (FlickrApi.isAuthentified()) {
			new AlertDialog.Builder(activity).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + STR.instantUpload + ")", "One set per folder", "New set…", "Existing set…" }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								enqueue(Lists.newArrayList(selectedMedia), STR.instantUpload);
								clearSelection();
								break;
							case 1:
								Multimap<String, Media> multimap = HashMultimap.create();
								for (Media media : selectedMedia) {
									multimap.put(media.getFolderName(), media);
								}
								for (String folderName : multimap.keySet()) {
									enqueue(multimap.get(folderName), folderName);
								}
								clearSelection();
								break;
							case 2:
								showNewSetDialog(null);
								break;
							case 3:
								showExistingSetDialog();
								break;

							default:
								break;
							}
							LOG.debug("which : " + which);
						}
					}).show();
		} else {
			Utils.confirmSignIn(activity);
		}
	}

	@UiThread
	void clearSelection() {
		selectedMedia.clear();
		for (Header header : headers) {
			header.selected = false;
		}
		refresh();
	}

	@UiThread
	void showExistingSetDialog() {
		Utils.showExistingSetDialog(activity, new Callback<String>() {
			@Override
			public void onResult(String result) {
				enqueue(Lists.newArrayList(selectedMedia), result);
				clearSelection();
			}
		}, null);
	}

	@UiThread
	void showNewSetDialog(final Folder folder) {
		showNewSetDialog(activity, folder == null ? null : folder.getName(), new Callback<String>() {
			@Override
			public void onResult(final String value) {
				if (ToolString.isBlank(value)) {
					Utils.toast("Title cannot be empty");
					showNewSetDialog(folder);
				} else {
					BackgroundExecutor.execute(new Runnable() {
						@Override
						public void run() {
							enqueue(Lists.newArrayList(selectedMedia), value);
							clearSelection();
						}
					});
				}
			}
		});
	}

	ProgressDialog progressDialog;

	@UiThread
	void showLoading(String title, String message) {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(activity);
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

	static void showNewSetDialog(final Activity activity, final String folderTitle, final Callback<String> callback) {
		AlertDialog.Builder alert = new AlertDialog.Builder(activity);

		alert.setTitle("Photo Set Title");

		// Set an EditText view to get user input
		final EditText input = new EditText(activity);
		input.setText(folderTitle);
		alert.setView(input);

		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				LOG.debug("value : " + value);
				callback.onResult(value);
			}

		});

		alert.setNegativeButton("Cancel", null);

		alert.show();
	}

	@Background
	void enqueue(Collection<Media> images, String photoSetTitle) {
		int enqueued = UploadService.enqueue(false, images, photoSetTitle);
		if (slidingDrawer != null && enqueued > 0) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (Utils.getBooleanProperty(STR.show_sliding_demo, true)) {
						slidingDrawer.demo();
					}
					drawerContentView.setCurrentTab(DrawerContentView.TAB_QUEUED_INDEX);
				}
			});
		}
	}

	void renderHeader(View headerView) {
		if (headerView != null && headerView.getTag(Header.class.hashCode()) instanceof Header) {
			Header header = (Header) headerView.getTag(Header.class.hashCode());
			// LOG.debug("rendering : " + header + " on " + headerView);
			TextView count = (TextView) headerView.getTag(R.id.count);
			count.setCompoundDrawablesWithIntrinsicBounds(0, 0, header.selected ? R.drawable.checkbox_on : R.drawable.checkbox_off, 0);
			count.setTextColor(getResources().getColor(header.selected ? R.color.litegray : R.color.gray));
			count.setText("" + header.count);
			// checkbox.setText("" + header.selected);
			TextView title = (TextView) headerView.getTag(R.id.title);
			title.setCompoundDrawablesWithIntrinsicBounds(header.collapsed ? R.drawable.expand_off : R.drawable.expand_on, 0, 0, 0);
			title.setText(header.title);
		}
	}

	Set<Media> selectedMedia = new HashSet<Media>();

	class PhotoAdapter extends BaseAdapter implements HeaderAdapter {

		public PhotoAdapter() {
		}

		@Override
		public int getCount() {
			return thumbItems.size();
		}

		@Override
		public Object getItem(int position) {
			return thumbItems.get(position);
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		static final int VIEW_HEADER = 0;
		static final int VIEW_THUMB = 1;

		@Override
		public int getItemViewType(int position) {
			if (thumbItems.get(position) instanceof Header) {
				return VIEW_HEADER;
			}
			return VIEW_THUMB;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			Object item = getItem(position);
			if (item instanceof Header) {
				return getHeaderView(position, convertView, parent);
			} else {
				final Media[] mediaRow = (Media[]) item;
				LinearLayout linearLayout;
				if (convertView == null || !(convertView instanceof LinearLayout)) {
					linearLayout = new LinearLayout(activity);
					linearLayout.setOrientation(LinearLayout.HORIZONTAL);
				} else {
					linearLayout = (LinearLayout) convertView;
				}
				if (linearLayout.getChildCount() != mediaRow.length) {
					linearLayout.removeAllViews();
					for (int i = 0; i < mediaRow.length; i++) {
						View thumbView = getLayoutInflater().inflate(R.layout.grid_thumb, linearLayout, false);
						thumbView.setTag(R.id.check_image, thumbView.findViewById(R.id.check_image));
						thumbView.setTag(R.id.uploading, thumbView.findViewById(R.id.uploading));
						thumbView.setTag(R.id.image_view, thumbView.findViewById(R.id.image_view));
						thumbView.setTag(R.id.uploaded, thumbView.findViewById(R.id.uploaded));
						thumbView.setTag(R.id.type, thumbView.findViewById(R.id.type));
						linearLayout.addView(thumbView);

						thumbView.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								Media media = (Media) v.getTag();
								Header header = headerMap.get(media);
								if (selectedMedia.contains(media)) {
									v.findViewById(R.id.check_image).setVisibility(View.GONE);
									selectedMedia.remove(media);
									if (header.selected) {
										header.selected = false;
									}
								} else {
									v.findViewById(R.id.check_image).setVisibility(View.VISIBLE);
									selectedMedia.add(media);
									if (!header.selected) {
										List<Media> headerMedias = new ArrayList<Media>();
										Iterator<Entry<Media, Header>> it = headerMap.entrySet().iterator();
										while (it.hasNext()) {
											Map.Entry<Media, Header> entry = it.next();
											if (entry.getValue() == header) {
												headerMedias.add(entry.getKey());
											}
										}
										if (selectedMedia.containsAll(headerMedias)) {
											header.selected = true;
										}
									}
								}
								refresh();
							}
						});
						thumbView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
							@Override
							public void onViewDetachedFromWindow(View v) {
								thumbViews.values().removeAll(Collections.singleton(v));
							}

							@Override
							public void onViewAttachedToWindow(View v) {
								if (v.getTag() instanceof Media) {
									thumbViews.put((Media) v.getTag(), v);
								}
							}
						});
					}
				}
				if (linearLayout.getTag() != mediaRow) {
					linearLayout.setTag(mediaRow);

					for (int i = 0; i < mediaRow.length; i++) {
						View thumbView = linearLayout.getChildAt(i);
						Media media = mediaRow[i];
						if (media == null) {
							thumbViews.remove(thumbView.getTag());
							thumbView.setTag(media);
							renderImageView(thumbView, true);
						} else if (!media.equals(thumbView.getTag())) {
							thumbViews.remove(thumbView.getTag());
							thumbViews.put(media, thumbView);
							thumbView.setTag(media);
							renderImageView(thumbView, true);
						}
					}

				}
				return linearLayout;
			}
		}

		public View getHeaderView(int position, View convertView, ViewGroup arg2) {
			final TextView title;
			final TextView count;
			if (convertView == null) {
				convertView = View.inflate(activity, R.layout.grid_header, null);
				convertView.setTag(Header.class);
				convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScaledSize(48)));
				convertView.findViewById(R.id.count).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Header header = (Header) v.getTag();
						header.selected = !header.selected;
						Iterator<Entry<Media, Header>> it = headerMap.entrySet().iterator();
						while (it.hasNext()) {
							Map.Entry<Media, Header> entry = it.next();
							if (entry.getValue() == header) {
								if (header.selected) {
									selectedMedia.add(entry.getKey());
								} else {
									selectedMedia.remove(entry.getKey());
								}
							}
						}
						refresh();
					}
				});
				title = (TextView) convertView.findViewById(R.id.title);
				convertView.setTag(R.id.title, title);
				count = (TextView) convertView.findViewById(R.id.count);
				convertView.setTag(R.id.count, count);
				convertView.findViewById(R.id.expand).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						final Header header = (Header) title.getTag();
						header.collapsed = !header.collapsed;
						computeHeaders(false);
					}
				});

				convertView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
					@Override
					public void onViewDetachedFromWindow(View v) {
						attachedHeaderViews.remove(v);
					}

					@Override
					public void onViewAttachedToWindow(View v) {
						attachedHeaderViews.add(v);
						renderHeader(v);
					}
				});
			} else {
				title = (TextView) convertView.getTag(R.id.title);
				count = (TextView) convertView.getTag(R.id.count);
			}
			Header header = (Header) thumbItems.get(position);
			convertView.setTag(Header.class.hashCode(), header);
			count.setTag(header);
			title.setTag(header);

			renderHeader(convertView);

			// LOG.debug(header + " : convertView = " + convertView);

			return convertView;
		}

		@Override
		public int getHeaderPosition(int firstVisibleItem) {
			if (thumbItems.size() > firstVisibleItem) {
				Object thumbObject = thumbItems.get(firstVisibleItem);
				if (thumbObject instanceof Header) {
					return firstVisibleItem;
				} else {
					Media[] mediaRow = (Media[]) thumbObject;
					return thumbItems.indexOf(headerMap.get(mediaRow[0]));
				}
			}
			return -1;
		}

	}

	public static void onLoadProgress(int progress) {
		if (instance != null) {
			instance.setLoading(progress);
		}
	}

	public static void updateStatic(Media media) {
		if (instance != null) {
			instance.update(media);
		}
	}

	Map<Media, View> thumbViews = new HashMap<Media, View>();

	private void update(Media media) {
		View thumbView = thumbViews.get(media);
		if (thumbView != null) {
			renderImageView(thumbView, false);
		}
		if (medias != null && !medias.contains(media)) {
			load();
		}
	}

	Set<View> attachedHeaderViews = new HashSet<View>();

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	@UiThread
	void renderImageView(final View convertView, boolean reset) {
		if (convertView.getTag() instanceof Media) {
			convertView.setVisibility(View.VISIBLE);
			convertView.setLayoutParams(new LinearLayout.LayoutParams(computed_req_width, computed_req_height));
			final Media media = (Media) convertView.getTag();
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			imageView.setTag(media);
			final View check_image = (View) convertView.getTag(R.id.check_image);
			final ImageView uploadedImageView = (ImageView) convertView.getTag(R.id.uploaded);
			if (reset) {
				check_image.setVisibility(View.GONE);
				uploadedImageView.setVisibility(View.GONE);
			}

			View type = (View) convertView.getTag(R.id.type);
			type.setVisibility(media.getMediaType() == MEDIA_TYPE.VIDEO ? View.VISIBLE : View.GONE);

			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(media.getPath() + "_" + Utils.getViewSize());
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
			}

			executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						if (media.equals(imageView.getTag())) {
							final boolean uploading = media.isQueued();
							final boolean uploaded = media.isUploaded();
							final int privacyResource;
							if (uploaded) {
								privacyResource = getPrivacyResource(media.getPrivacy());
							} else {
								privacyResource = 0;
							}
							final CacheableBitmapDrawable bitmapDrawable;
							if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
								bitmapDrawable = wrapper;
							} else {
								Bitmap bitmap = Utils.getBitmap(media, Utils.getViewSize());
								if (bitmap != null) {
									bitmapDrawable = Utils.getCache().put(media.getPath() + "_" + Utils.getViewSize(), bitmap);
								} else {
									bitmapDrawable = null;
								}
							}
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (media.equals(imageView.getTag())) {
										check_image.setVisibility(selectedMedia.contains(media) ? View.VISIBLE : View.GONE);
										uploadedImageView.setVisibility(uploaded ? View.VISIBLE : View.GONE);
										((View) convertView.getTag(R.id.uploading)).setVisibility(uploading ? View.VISIBLE : View.GONE);
										if (wrapper != bitmapDrawable) {
											imageView.setImageDrawable(bitmapDrawable);
										}
										if (privacyResource != 0) {
											uploadedImageView.setImageResource(privacyResource);
										}
									}
								}
							});
						}
					} catch (Throwable e) {
						LOG.error("FINAL ERROR\n" + ToolString.stack2string(e));
					}

				}
			});
		} else {
			convertView.setVisibility(View.GONE);
		}
	}

	private Menu menu;

	@ViewById(R.id.list_view)
	StickyHeaderListView listView;

	private boolean paused = false;
	private boolean sync = true;

	@Override
	public void onBackPressed() {
		if (slidingDrawer != null && slidingDrawer.isOpened()) {
			slidingDrawer.animateClose();
		} else {
			if (finishOnClose) {
				finish();
			} else {
				moveTaskToBack(true);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.menu = menu;
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		renderMenu();
		return super.onPrepareOptionsMenu(menu);
	}

	private void renderMenu() {
		if (menu != null) {
			menu.findItem(R.id.trial_info).setVisible(!Utils.isPremium());
			switch (Utils.getViewSize()) {
			case small:
				menu.findItem(R.id.view_size_small).setChecked(true);
				break;
			case medium:
				menu.findItem(R.id.view_size_medium).setChecked(true);
				break;
			case large:
				menu.findItem(R.id.view_size_large).setChecked(true);
				break;
			default:
				break;
			}
			menu.findItem(R.id.filter_photos).setChecked(Utils.getShowPhotos());
			menu.findItem(R.id.filter_videos).setChecked(Utils.getShowVideos());
			menu.findItem(R.id.filter_uploaded).setChecked(Utils.getShowUploaded());
			menu.findItem(R.id.filter_not_uploaded).setChecked(Utils.getShowNotUploaded());
			switch (Utils.getViewGroupType()) {
			case date:
				menu.findItem(R.id.group_by_date).setChecked(true);
				break;
			case folder:
				menu.findItem(R.id.group_by_folder).setChecked(true);
				break;
			default:
				break;

			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.trial_info:
			Utils.showPremiumDialog(activity, new Utils.Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
				}
			});
			break;
		case R.id.preferences:
			startActivity(new Intent(activity, PreferencesActivity.class));
			break;
		case R.id.faq:
			Utils.showHelpDialog(activity);
			break;
		case R.id.view_size_small:
			Utils.setViewSize(VIEW_SIZE.small);
			computeHeaders(false);
			item.setChecked(true);
			break;
		case R.id.view_size_medium:
			Utils.setViewSize(VIEW_SIZE.medium);
			computeHeaders(false);
			item.setChecked(true);
			break;
		case R.id.view_size_large:
			Utils.setViewSize(VIEW_SIZE.large);
			computeHeaders(false);
			item.setChecked(true);
			break;
		case R.id.filter_photos:
			item.setChecked(!item.isChecked());
			Utils.setShowPhotos(item.isChecked());
			load();
			break;
		case R.id.filter_videos:
			item.setChecked(!item.isChecked());
			Utils.setShowVideos(item.isChecked());
			load();
			break;
		case R.id.filter_uploaded:
			item.setChecked(!item.isChecked());
			Utils.setShowUploaded(item.isChecked());
			load();
			break;
		case R.id.filter_not_uploaded:
			item.setChecked(!item.isChecked());
			Utils.setShowNotUploaded(item.isChecked());
			load();
			break;
		case R.id.select_all:
			for (Header header : headers) {
				header.selected = true;
			}
			selectedMedia.addAll(medias);
			refresh();
			break;
		case R.id.select_none:
			for (Header header : headers) {
				header.selected = false;
			}
			selectedMedia.clear();
			refresh();
			break;
		case R.id.group_by_date:
			item.setChecked(true);
			Utils.setViewGroupType(VIEW_GROUP_TYPE.date);
			load();
			break;
		case R.id.group_by_folder:
			item.setChecked(true);
			Utils.setViewGroupType(VIEW_GROUP_TYPE.folder);
			load();
			break;
		case R.id.expand_all:
			for (Header header : headers) {
				header.collapsed = false;
			}
			computeHeaders(false);
			break;
		case R.id.collapse_all:
			for (Header header : headers) {
				header.collapsed = true;
			}
			computeHeaders(false);
			break;
		case R.id.upload_add:
			if (Utils.isPremium() || Utils.isTrial()) {
				if (selectedMedia.isEmpty()) {
					Utils.toast("Select at least one file");
				} else {
					confirmUpload();
				}
			} else {
				checkPremium();
			}
			break;
		case R.id.upload_remove:
			if (selectedMedia.isEmpty()) {
				Utils.toast("Select at least one file");
			} else {
				UploadService.dequeue(selectedMedia);
				clearSelection();
			}
			break;
		}

		return (super.onOptionsItemSelected(item));
	}

	@Override
	protected void onResume() {
		if (paused) {
			sync = true;
			load();
		}
		paused = false;
		super.onResume();
		UploadService.wake();
		drawerHandleView.onActivityResume();
		renderLoop();
	}

	@UiThread(delay = 1000)
	void renderLoop() {
		if (!paused && !destroyed) {
			if (drawerHandleView != null) {
				drawerHandleView.render();
				if (slidingDrawer.isOpened()) {
					drawerContentView.renderCurrentView();
				}
			}
			renderLoop();
		}
	}

	@UiThread
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (Utils.onActivityResult(requestCode, resultCode, data)) {
			return;
		}
		if (resultCode == FlickrWebAuthActivity.RESULT_CODE_AUTH) {
			if (FlickrApi.isAuthentified()) {
				Utils.showAutoUploadDialog(activity);
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@UiThread(delay = 100)
	public void refresh() {
		if (listView != null) {
			renderMenu();
			int childCount = listView.getChildCount();
			for (int i = 0; i < childCount; i++) {
				View convertView = listView.getChildAt(i);
				if (convertView instanceof LinearLayout && convertView.getTag() instanceof Media[]) {
					LinearLayout linearLayout = (LinearLayout) convertView;
					for (int j = 0; j < linearLayout.getChildCount(); j++) {
						renderImageView(linearLayout.getChildAt(j), false);
					}
				}
			}
			for (View headerView : attachedHeaderViews) {
				renderHeader(headerView);
			}
			renderHeader(listView.getFloatingSectionHeader());
		}
		if (selectedMedia.isEmpty()) {
			getActionBar().setTitle(null);
		} else {
			getActionBar().setTitle("" + selectedMedia.size());
		}
	}

	int getPrivacyResource(PRIVACY privacy) {
		if (privacy != null) {
			switch (privacy) {
			case PUBLIC:
				return R.drawable.uploaded_public;
			case FAMILY:
			case FRIENDS:
			case FRIENDS_FAMILY:
				return R.drawable.uploaded_friends;
			default:
				break;
			}
		}
		return R.drawable.uploaded;
	}

	@Override
	protected void onPause() {
		paused = true;
		super.onPause();
	}

	public boolean isPaused() {
		return paused;
	}

	public SlidingDrawer getSlidingDrawer() {
		return slidingDrawer;
	}
}
