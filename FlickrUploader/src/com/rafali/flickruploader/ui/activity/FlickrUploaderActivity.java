package com.rafali.flickruploader.ui.activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.base.Functions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
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
import com.rafali.flickruploader.api.FlickrApi.PRIVACY;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;
import com.rafali.flickruploader.tool.Notifications;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.tool.Utils.Callback;
import com.rafali.flickruploader.tool.Utils.MediaType;
import com.rafali.flickruploader.tool.Utils.VIEW_GROUP_TYPE;
import com.rafali.flickruploader.tool.Utils.VIEW_SIZE;
import com.rafali.flickruploader.ui.DrawerContentView;
import com.rafali.flickruploader.ui.DrawerHandleView;
import com.rafali.flickruploader.ui.widget.SlidingDrawer;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView.Header;
import com.rafali.flickruploader.ui.widget.StickyHeaderListView.HeaderAdapter;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.flickr_uploader_slider_activity)
public class FlickrUploaderActivity extends Activity {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploaderActivity.class);

	private static FlickrUploaderActivity instance;
	private FlickrUploaderActivity activity = this;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		load();
		LOG.debug("onCreate " + bundle);
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
		} else {
		}
	}

	@Background
	void handleIntent(Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			String type = intent.getType();
			if (Intent.ACTION_SEND.equals(action) && type != null) {
				if (type.startsWith("image/") || type.startsWith("video/")) {
					Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
					List<Media> loadImages = Utils.loadImages(imageUri.toString(), type.startsWith("image/") ? MediaType.photo : MediaType.video, 1);
					LOG.debug("imageUri : " + imageUri + ", loadImages : " + loadImages);
					if (!loadImages.isEmpty()) {
						selectedMedia.clear();
						selectedMedia.addAll(loadImages);
						confirmUpload();
					} else {
						Utils.toast("No media found for " + imageUri);
					}
				}
			} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
				if (type.startsWith("image/") || type.startsWith("video/")) {
					ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
					List<Media> loadImages = new ArrayList<Media>();
					if (imageUris != null) {
						for (Uri imageUri : imageUris) {
							List<Media> tmpImages = Utils.loadImages(imageUri.toString(), type.startsWith("image/") ? MediaType.photo : MediaType.video, 1);
							LOG.debug("imageUri : " + imageUri + ", loadImages : " + loadImages);
							loadImages.addAll(tmpImages);
						}
						if (!loadImages.isEmpty()) {
							selectedMedia.clear();
							selectedMedia.addAll(loadImages);
							confirmUpload();
						} else {
							Utils.toast("No media found");
						}
					}
				}
			}
		}
	}

	SimpleDateFormat format = new SimpleDateFormat("MMMM, yyyy", Locale.US);

	List<Object> thumbItems;
	List<Media> medias;

	@Background
	void load() {
		medias = new ArrayList<Media>();
		if (Utils.getShowPhotos()) {
			medias.addAll(Utils.loadImages(null, MediaType.photo));
		}
		if (Utils.getShowVideos()) {
			medias.addAll(Utils.loadImages(null, MediaType.video));
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

		init();
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
				String id = format.format(new Date(media.date));
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

	void testNotification() {
		BackgroundExecutor.execute(new Runnable() {
			Media image = medias.get(0);

			@Override
			public void run() {
				for (int i = 0; i <= 100; i++) {
					Notifications.notify(i, image, 1, 1);
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
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
		stopService(new Intent(this, PayPalService.class));
	}

	public boolean destroyed = false;

	PhotoAdapter photoAdapter = new PhotoAdapter();

	@UiThread
	void init() {
		if (listView == null) {
			final RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.container);
			listView = (StickyHeaderListView) View.inflate(activity, R.layout.grid, null);
			listView.setDividerHeight(0);
			listView.setAdapter(photoAdapter);
			listView.setFastScrollEnabled(true);
			listView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
			relativeLayout.addView(listView, 0);
		} else {
			photoAdapter.notifyDataSetChanged();
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
		getActionBar().setTitle(null);
		getActionBar().setIcon(null);
		getActionBar().setDisplayShowHomeEnabled(false);
	}

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
					.setItems(new String[] { "Default set (" + STR.instantUpload + ")", "One set per folder", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								enqueue(selectedMedia, STR.instantUpload);
								clearSelection();
								break;
							case 1:
								Multimap<String, Media> multimap = HashMultimap.create();
								for (Media image : selectedMedia) {
									multimap.put(image.getFolderName(), image);
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
		refresh(false);
	}

	@UiThread
	void showExistingSetDialog() {
		showExistingSetDialog(activity, new Callback<String[]>() {
			@Override
			public void onResult(String[] result) {
				String photoSetTitle = result[1];
				enqueue(selectedMedia, photoSetTitle);
				clearSelection();
			}
		}, null);
	}

	static void showExistingSetDialog(final Activity activity, final Callback<String[]> callback, final Map<String, String> cachedPhotosets) {
		final ProgressDialog dialog = ProgressDialog.show(activity, "", "Loading photosets", true);
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				final Map<String, String> photosets = cachedPhotosets == null ? FlickrApi.getPhotoSets(true) : cachedPhotosets;
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dialog.cancel();
						if (photosets.isEmpty()) {
							Utils.toast("No photoset found");
						} else {
							AlertDialog.Builder builder = new AlertDialog.Builder(activity);
							final List<String> photosetTitles = new ArrayList<String>();
							final List<String> photosetIds = new ArrayList<String>(photosets.keySet());
							Map<String, String> lowerCasePhotosets = new HashMap<String, String>();
							Iterator<String> it = photosetIds.iterator();
							while (it.hasNext()) {
								String photosetId = it.next();
								String photoSetTitle = photosets.get(photosetId);
								if (ToolString.isNotBlank(photoSetTitle)) {
									lowerCasePhotosets.put(photosetId, photoSetTitle.toLowerCase(Locale.US));
								} else {
									it.remove();
								}
							}
							Ordering<String> valueComparator = Ordering.natural().onResultOf(Functions.forMap(lowerCasePhotosets));
							Collections.sort(photosetIds, valueComparator);
							for (String photosetId : photosetIds) {
								photosetTitles.add(photosets.get(photosetId));
							}
							String[] photosetTitlesArray = photosetTitles.toArray(new String[photosetTitles.size()]);
							builder.setItems(photosetTitlesArray, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									LOG.debug("selected : " + photosetIds.get(which) + " - " + photosetTitles.get(which));
									String photoSetId = photosetIds.get(which);
									String photoSetTitle = photosetTitles.get(which);
									callback.onResult(new String[] { photoSetId, photoSetTitle });
								}
							});
							builder.show();
						}
					}
				});
			}
		});
	}

	@UiThread
	void showNewSetDialog(final Folder folder) {
		showNewSetDialog(activity, folder == null ? null : folder.name, new Callback<String>() {
			@Override
			public void onResult(final String value) {
				if (ToolString.isBlank(value)) {
					Utils.toast("Title cannot be empty");
					showNewSetDialog(folder);
				} else {
					BackgroundExecutor.execute(new Runnable() {
						@Override
						public void run() {
							enqueue(selectedMedia, value);
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

	@UiThread
	void enqueue(Collection<Media> images, String photoSetTitle) {
		int enqueued = UploadService.enqueue(false, images, photoSetTitle);
		if (slidingDrawer != null && enqueued > 0) {
			slidingDrawer.animateOpen();
			drawerContentView.setCurrentTab(DrawerContentView.TAB_QUEUED_INDEX);
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
								refresh(false);
							}
						});
					}
				}
				if (linearLayout.getTag() != mediaRow) {
					linearLayout.setTag(mediaRow);

					for (int i = 0; i < mediaRow.length; i++) {
						View thumbView = linearLayout.getChildAt(i);
						thumbView.setTag(mediaRow[i]);
						renderImageView(thumbView, true);
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
						refresh(false);
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
			Object thumbObject = thumbItems.get(firstVisibleItem);
			if (thumbObject instanceof Header) {
				return firstVisibleItem;
			} else {
				Media[] mediaRow = (Media[]) thumbObject;
				return thumbItems.indexOf(headerMap.get(mediaRow[0]));
			}
		}

	}

	Set<View> attachedHeaderViews = new HashSet<View>();

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private void renderImageView(final View convertView, boolean reset) {
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
			type.setVisibility(media.mediaType == MediaType.video ? View.VISIBLE : View.GONE);

			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(media.path + "_" + Utils.getViewSize());
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
						if (imageView.getTag() == media) {
							final boolean isUploaded;
							final boolean isUploading;
							isUploaded = FlickrApi.isUploaded(media);
							isUploading = UploadService.isUploading(media);
							final int privacyResource;
							if (isUploaded) {
								privacyResource = getPrivacyResource(FlickrApi.getPrivacy(media));
							} else {
								privacyResource = 0;
							}
							// LOG.debug(tab + ", isUploaded=" + isUploaded);
							final CacheableBitmapDrawable bitmapDrawable;
							if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
								bitmapDrawable = wrapper;
							} else {
								Bitmap bitmap = Utils.getBitmap(media, Utils.getViewSize());
								if (bitmap != null) {
									bitmapDrawable = Utils.getCache().put(media.path + "_" + Utils.getViewSize(), bitmap);
								} else {
									bitmapDrawable = null;
								}
							}
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (imageView.getTag() == media) {
										check_image.setVisibility(selectedMedia.contains(media) ? View.VISIBLE : View.GONE);
										uploadedImageView.setVisibility(isUploaded ? View.VISIBLE : View.GONE);
										((View) convertView.getTag(R.id.uploading)).setVisibility(isUploading ? View.VISIBLE : View.GONE);
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

	static SparseArray<String> uploadedPhotos = new SparseArray<String>();

	private Menu menu;

	private StickyHeaderListView listView;

	private boolean paused = false;

	@Override
	public void onBackPressed() {
		if (slidingDrawer != null && slidingDrawer.isOpened()) {
			slidingDrawer.animateClose();
		} else {
			moveTaskToBack(true);
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
		case R.id.select_all:
			for (Header header : headers) {
				header.selected = true;
			}
			selectedMedia.addAll(medias);
			refresh(false);
			break;
		case R.id.select_none:
			for (Header header : headers) {
				header.selected = false;
			}
			selectedMedia.clear();
			refresh(false);
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
			refresh(true);
		}
		paused = false;
		super.onResume();
		UploadService.wake();
		drawerHandleView.onResume();
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
	public void refresh(boolean reload) {
		if (reload) {
			load();
		} else {
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

	public static void staticRefresh(boolean reload) {
		if (instance != null) {
			instance.refresh(reload);
		}
	}

	@Override
	protected void onPause() {
		paused = true;
		super.onPause();
	}

	public boolean isPaused() {
		return paused;
	}

}
