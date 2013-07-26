package com.rafali.flickruploader;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.base.Joiner;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.Utils.MediaType;
import com.rafali.flickruploader.billing.IabHelper;

@EActivity(R.layout.flickr_uploader_activity)
public class FlickrUploaderActivity extends Activity {

	private static final int MAX_LINK_SHARE = 5;

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploaderActivity.class);

	static enum TAB {
		feed(R.layout.photo_feed, R.layout.photo_feed_thumb, R.string.feed), photo(R.layout.photo_grid, R.layout.photo_grid_thumb, R.string.photos), video(R.layout.video_grid,
				R.layout.video_grid_thumb, R.string.videos), folder(R.layout.folder_grid, R.layout.folder_grid_thumb, R.string.folders);

		private int thumbLayoutId;
		private int layoutId;
		private int title;

		TAB(int layoutId, int thumbLayoutId, int title) {
			this.layoutId = layoutId;
			this.thumbLayoutId = thumbLayoutId;
			this.title = title;
		}
	}

	ActionMode mMode;

	private static FlickrUploaderActivity instance;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		LOG.debug("onCreate " + bundle);
		startService(new Intent(this, UploadService.class));
		if (Utils.getStringProperty(STR.accessToken) == null) {
			Utils.confirmSignIn(FlickrUploaderActivity.this);
		}
		load();
		if (instance != null)
			instance.finish();
		instance = this;
		Utils.checkPremium(this);
	}

	AbsListView[] views = new AbsListView[TAB.values().length];

	class MainTabView extends TabView {

		public MainTabView() {
			super(FlickrUploaderActivity.this, null, TAB.values().length, Arrays.asList(TAB.values()).indexOf(TAB.photo));
		}

		@Override
		protected View createTabViewItem(int position) {
			return views[position];
		}

		@Override
		protected int getTabViewItemTitle(int position) {
			return TAB.values()[position].title;
		}

		@Override
		public void onPageSelected(int position) {
			super.onPageSelected(position);
			AbsListView view = (AbsListView) getCurrentView();
			if (view != null && mainTabView != null) {
				for (int i = 0; i < view.getChildCount(); i++) {
					renderImageView(view.getChildAt(i));
				}
				updateCount();
			}
		}

	}

	private void load() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				photos = Utils.loadImages(null, MediaType.photo);
				videos = Utils.loadImages(null, MediaType.video);

				List<Media> all = new ArrayList<Media>(photos);
				all.addAll(videos);
				folders = Utils.getFolders(all);
				for (Folder folder : folders) {
					foldersMap.put(folder.images.get(0), folder);
				}
				init();
			}

		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	protected void onStop() {
		Mixpanel.flush();
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
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
		refresh(false);
	}

	public static FlickrUploaderActivity getInstance() {
		return instance;
	}

	void testNotification() {
		BackgroundExecutor.execute(new Runnable() {
			Media image = photos.get(0);

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
	}

	boolean destroyed = false;

	@UiThread
	void init() {
		if (mainTabView == null) {
			for (int i = 0; i < TAB.values().length; i++) {
				TAB tab = TAB.values()[i];
				AbsListView absListView = views[i] = (AbsListView) View.inflate(FlickrUploaderActivity.this, tab.layoutId, null);
				absListView.setAdapter(new PhotoAdapter(tab));
				absListView.setTag(tab);
				absListView.setOnItemClickListener(onItemClickListener);
			}
			mainTabView = new MainTabView();
			RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.container);
			relativeLayout.addView(mainTabView, 0);
		} else {
			for (int i = 0; i < TAB.values().length; i++) {
				((PhotoAdapter) views[i].getAdapter()).notifyDataSetChanged();
			}
		}
	}

	@ViewById(R.id.footer)
	TextView footer;

	@AfterViews
	void afterViews() {
		renderPremium();
	}

	@Click(R.id.footer)
	void onFooterClick() {
		Utils.showPremiumDialog(this, new Utils.Callback<Boolean>() {
			@Override
			public void onResult(Boolean result) {
				renderPremium();
			}
		});
	}

	OnItemClickListener onItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> arg0, View convertView, int arg2, long arg3) {
			FlickrApi.isUploaded(isFolderTab() ? foldersMap.get(convertView.getTag()) : convertView.getTag());
			if (getImageSelected(SelectionType.all).contains(convertView.getTag())) {
				convertView.findViewById(R.id.check_image).setVisibility(View.GONE);
				getImageSelected(SelectionType.all).remove(convertView.getTag());
			} else {
				convertView.findViewById(R.id.check_image).setVisibility(View.VISIBLE);
				getImageSelected(SelectionType.all).add((Media) convertView.getTag());
			}
			updateCount();
		}
	};

	void updateCount() {
		int size = 0;
		if (isFolderTab()) {
			for (Media image : getImageSelected(SelectionType.all)) {
				Folder folder = foldersMap.get(image);
				if (folder != null)
					size += folder.size;
			}
		} else {
			size = getImageSelected(SelectionType.all).size();
		}
		if (size > 0) {
			if (mMode == null)
				mMode = startActionMode(mCallback);
			if (mMode != null)
				mMode.setTitle(size + "");
			if (!isFolderTab()) {
				setShareIntent();
			}
		} else if (size == 0 && mMode != null) {
			mMode.finish();
		}
	}

	private MenuItem shareItem;
	private MenuItem enableAutoUpload;
	private MenuItem disableAutoUpload;
	private ShareActionProvider shareActionProvider;

	@UiThread(delay = 200)
	void setShareIntent() {
		if (!getImageSelected(SelectionType.all).isEmpty() && shareItem != null) {
			Map<String, String> shortUrls = new LinkedHashMap<String, String>();
			for (Media image : getImageSelected(SelectionType.all)) {
				String photoId = FlickrApi.getPhotoId(image);
				if (photoId != null) {
					shortUrls.put(photoId, FlickrApi.getShortUrl(photoId));
				}
				if (shortUrls.size() >= MAX_LINK_SHARE)
					break;
			}
			boolean uploadedPhotosSelected = !shortUrls.isEmpty();
			shareItem.setVisible(uploadedPhotosSelected);
			privacyItem.setVisible(uploadedPhotosSelected);
			if (uploadedPhotosSelected) {
				Intent shareIntent = ShareCompat.IntentBuilder.from(FlickrUploaderActivity.this).setType("text/*").setText(Joiner.on(" ").join(shortUrls.values())).getIntent();
				shareIntent.putExtra("photoIds", Joiner.on(",").join(shortUrls.keySet()));
				shareActionProvider.setShareIntent(shareIntent);
			}
		}
	}

	private MenuItem privacyItem;
	final ActionMode.Callback mCallback = new ActionMode.Callback() {

		/** Invoked whenever the action mode is shown. This is invoked immediately after onCreateActionMode */
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		/** Called when user exits action mode */
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mMode = null;
			getImageSelected(SelectionType.all).clear();
			renderSelection();
		}

		/** This is called when the action mode is created. This is called by startActionMode() */
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			getMenuInflater().inflate(R.menu.context_menu, menu);
			shareItem = menu.findItem(R.id.menu_item_share);
			enableAutoUpload = menu.findItem(R.id.menu_item_enable_auto_upload);
			disableAutoUpload = menu.findItem(R.id.menu_item_disable_auto_upload);
			if (isFolderTab()) {
				shareItem.setVisible(false);
			} else {
				enableAutoUpload.setVisible(false);
				disableAutoUpload.setVisible(false);
			}
			privacyItem = menu.findItem(R.id.menu_item_privacy);

			shareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
			shareActionProvider.setOnShareTargetSelectedListener(new ShareActionProvider.OnShareTargetSelectedListener() {
				@Override
				public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
					LOG.debug("intent : " + intent);
					if (intent.hasExtra("photoIds")) {
						List<String> privatePhotoIds = new ArrayList<String>();
						String[] photoIds = intent.getStringExtra("photoIds").split(",");
						for (String photoId : photoIds) {
							if (FlickrApi.getPrivacy(photoId) != PRIVACY.PUBLIC) {
								privatePhotoIds.add(photoId);
							}
						}
						int size = privatePhotoIds.size();
						if (size > 0) {
							toast(privatePhotoIds.size() + " photo" + (size > 1 ? "s" : "") + " will be public");
							FlickrApi.setPrivacy(PRIVACY.PUBLIC, privatePhotoIds);
						}
						Mixpanel.increment("photos_shared", photoIds.length);
					}
					return false;
				}
			});
			return true;
		}

		/** This is called when an item in the context menu is selected */
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			Mixpanel.track("UI actionMode " + getResources().getResourceEntryName(item.getItemId()));
			switch (item.getItemId()) {
			case R.id.menu_item_select_all: {
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_select_all", 0L);
				if (getImageSelected(SelectionType.auto).isEmpty()) {
					Collection<Media> allImages;
					if (isFolderTab()) {
						allImages = foldersMap.keySet();
					} else if (isVideoTab()) {
						allImages = videos;
					} else {
						allImages = photos;
					}
					getImageSelected(SelectionType.auto).addAll(allImages);
					getImageSelected(SelectionType.auto).removeAll(getImageSelected(SelectionType.all));
					getImageSelected(SelectionType.all).clear();
					getImageSelected(SelectionType.all).addAll(allImages);
				} else {
					getImageSelected(SelectionType.all).removeAll(getImageSelected(SelectionType.auto));
					getImageSelected(SelectionType.auto).clear();
				}
				updateCount();
				renderSelection();
			}
				break;
			case R.id.menu_item_privacy: {
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_privacy", 0L);
				Collection<Media> selectedImages;
				if (isFolderTab()) {
					selectedImages = new ArrayList<Media>();
					for (Media image : getImageSelected(SelectionType.all)) {
						selectedImages.addAll(foldersMap.get(image).images);
					}
				} else {
					selectedImages = getImageSelected(SelectionType.all);
				}
				PRIVACY privacy = null;
				for (Media image : selectedImages) {
					if (privacy == null) {
						privacy = FlickrApi.getPrivacy(image);
					} else {
						if (privacy != FlickrApi.getPrivacy(image)) {
							privacy = null;
							break;
						}
					}
				}
				Utils.dialogPrivacy(FlickrUploaderActivity.this, privacy, new Utils.Callback<FlickrApi.PRIVACY>() {
					@Override
					public void onResult(PRIVACY result) {
						if (result != null) {
							List<String> photoIds = new ArrayList<String>();
							for (Media image : getImageSelected(SelectionType.all)) {
								String photoId = FlickrApi.getPhotoId(image);
								if (photoId != null && FlickrApi.getPrivacy(photoId) != result) {
									photoIds.add(photoId);
								}
							}
							if (!photoIds.isEmpty()) {
								FlickrApi.setPrivacy(result, photoIds);
							}
						}
					}
				});
			}
				break;
			case R.id.menu_item_upload: {
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_upload", 0L);
				final List<Media> selection = new ArrayList<Media>(getImageSelected(SelectionType.all));
				if (FlickrApi.isAuthentified()) {
					// foldersMap.clear();
					BackgroundExecutor.execute(new Runnable() {
						@Override
						public void run() {
							if (selection.isEmpty()) {
								toast("Photos already on Flickr");
							} else {
								confirmUpload(selection);
							}
						}
					});
					mMode.finish();
				} else {
					// Notifications.notify(40, selection.get(0), 1, 1);
					Utils.confirmSignIn(FlickrUploaderActivity.this);
				}
			}
				break;
			case R.id.menu_item_dequeue: {
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_dequeue", 0L);
				final List<Media> selection = new ArrayList<Media>(getImageSelected(SelectionType.all));
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						if (isFolderTab()) {
							for (Media image : selection) {
								Folder folder = foldersMap.get(image);
								UploadService.dequeue(folder.images);
							}
						} else {
							UploadService.dequeue(selection);
						}
						refresh(false);
					}
				});
				mMode.finish();
			}
				break;

			case R.id.menu_item_enable_auto_upload:
				for (Media image : getImageSelected(SelectionType.all)) {
					Utils.setSynced(foldersMap.get(image), true);
				}
				refresh(false);
				break;
			case R.id.menu_item_disable_auto_upload:
				for (Media image : getImageSelected(SelectionType.all)) {
					Utils.setSynced(foldersMap.get(image), false);
				}
				refresh(false);
				break;
			}
			return false;
		}

	};

	@UiThread
	void confirmUpload(final List<Media> selection) {
		if (isFolderTab()) {
			new AlertDialog.Builder(this).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + Utils.getInstantAlbumTitle() + ")", "One set per folder", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							int count = 0;
							switch (which) {
							case 0:
								for (Media image : selection) {
									Folder folder = foldersMap.get(image);
									count += folder.images.size();
									UploadService.enqueue(folder.images, Utils.getInstantAlbumId(), STR.instantUpload);
								}
								toast(count + " photos enqueued");
								break;
							case 1:
								for (Media image : selection) {
									Folder folder = foldersMap.get(image);
									count += folder.images.size();
									UploadService.enqueue(folder.images, folder, null, null);
								}
								toast(count + " photos enqueued");
								break;
							case 2:
								showNewSetDialog(selection);
								break;
							case 3:
								showExistingSetDialog(selection);
								break;
							default:
								break;
							}
							LOG.debug("which : " + which);
						}
					}).show();
		} else {
			new AlertDialog.Builder(this).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + Utils.getInstantAlbumTitle() + ")", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								UploadService.enqueue(selection, Utils.getInstantAlbumId(), STR.instantUpload);
								break;
							case 1:
								showNewSetDialog(selection);
								break;
							case 2:
								showExistingSetDialog(selection);
								break;

							default:
								break;
							}
							LOG.debug("which : " + which);
						}
					}).show();
		}
		// Notifications.notify(40, selection.get(0), 1, 1);
		// refresh(false);
	}

	@UiThread
	void showExistingSetDialog(final List<Media> selection) {
		final ProgressDialog dialog = ProgressDialog.show(this, "", "Loading photosets", true);
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				final Map<String, String> photosets = FlickrApi.getPhotoSets();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						dialog.cancel();
						if (photosets.isEmpty()) {
							Toast.makeText(FlickrUploaderActivity.this, "No photoset found", Toast.LENGTH_LONG).show();
						} else {
							AlertDialog.Builder builder = new AlertDialog.Builder(FlickrUploaderActivity.this);
							final List<String> photosetTitles = new ArrayList<String>();
							final List<String> photosetIds = new ArrayList<String>();
							for (String photosetId : photosets.keySet()) {
								photosetIds.add(photosetId);
								photosetTitles.add(photosets.get(photosetId));
							}
							String[] photosetTitlesArray = photosetTitles.toArray(new String[photosetTitles.size()]);
							builder.setItems(photosetTitlesArray, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									LOG.debug("selected : " + photosetIds.get(which) + " - " + photosetTitles.get(which));
									String photoSetId = photosetIds.get(which);
									int count = 0;
									if (isFolderTab()) {
										for (Media image : selection) {
											Folder folder = foldersMap.get(image);
											count += folder.images.size();
											UploadService.enqueue(folder.images, photoSetId, null);
										}
									} else {
										count += selection.size();
										UploadService.enqueue(selection, photoSetId, null);
									}
									toast(count + " photos enqueued");
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
	void showNewSetDialog(final List<Media> selection) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Photo Set Title");

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				LOG.debug("value : " + value);
				if (ToolString.isBlank(value)) {
					showNewSetDialog(selection);
				} else {
					int count = 0;
					if (isFolderTab()) {
						for (Media image : selection) {
							Folder folder = foldersMap.get(image);
							count += folder.images.size();
							UploadService.enqueue(folder.images, null, value);
						}
					} else {
						count += selection.size();
						UploadService.enqueue(selection, null, value);
					}
					toast(count + " photos enqueued");
				}
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Canceled.
			}
		});

		alert.show();
	}

	private boolean isFolderTab() {
		return mainTabView.getCurrentView().getTag() == TAB.folder;
	}

	private boolean isVideoTab() {
		return mainTabView.getCurrentView().getTag() == TAB.video;
	}

	@UiThread
	void toast(String message) {
		Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
	}

	@UiThread
	void renderSelection() {
		ViewGroup grid = (ViewGroup) mainTabView.getCurrentView();
		int childCount = grid.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View view = grid.getChildAt(i);
			view.findViewById(R.id.check_image).setVisibility(getImageSelected(SelectionType.all).contains(view.getTag()) ? View.VISIBLE : View.GONE);
		}
	}

	Map<String, List<Media>> imagesSelectedMap = new HashMap<String, List<Media>>();

	private List<Media> getImageSelected(SelectionType type) {
		String key = mainTabView.getCurrentView().getClass().getSimpleName() + "_" + mainTabView.getCurrentView().getTag() + "_" + type;
		List<Media> imagesSelected = imagesSelectedMap.get(key);
		if (imagesSelected == null) {
			imagesSelected = new ArrayList<Media>();
			imagesSelectedMap.put(key, imagesSelected);
		}
		return imagesSelected;
	}

	enum SelectionType {
		all, auto
	}

	List<Media> photos;
	List<Media> videos;
	List<Folder> folders;
	Map<Media, Folder> foldersMap = new HashMap<Media, Folder>();

	class PhotoAdapter extends BaseAdapter {

		private TAB tab;

		public PhotoAdapter(TAB tab) {
			this.tab = tab;
		}

		@Override
		public int getCount() {
			if (tab == TAB.video) {
				return videos.size();
			} else if (tab == TAB.folder) {
				return folders.size();
			} else {
				return photos.size();
			}
		}

		@Override
		public Object getItem(int position) {
			if (tab == TAB.video) {
				return videos.get(position);
			} else if (tab == TAB.folder) {
				return folders.get(position).images.get(0);
			} else {
				return photos.get(position);
			}
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(tab.thumbLayoutId, parent, false);
				if (tab == TAB.feed) {
					convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScreenWidthPx()));
				} else if (tab == TAB.folder || tab == TAB.video) {
					convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScreenWidthPx() / 2));
				}
				convertView.setTag(TAG_KEY_TAB, tab);
				convertView.setTag(R.id.check_image, convertView.findViewById(R.id.check_image));
				if (tab == TAB.folder) {
					convertView.setTag(R.id.size, convertView.findViewById(R.id.size));
					convertView.setTag(R.id.title, convertView.findViewById(R.id.title));
					convertView.setTag(R.id.synced, convertView.findViewById(R.id.synced));
				}
				convertView.setTag(R.id.uploading, convertView.findViewById(R.id.uploading));
				convertView.setTag(R.id.image_view, convertView.findViewById(R.id.image_view));
				convertView.setTag(R.id.uploaded, convertView.findViewById(R.id.uploaded));
			}
			final Media image;
			if (tab == TAB.video) {
				image = videos.get(position);
			} else if (tab == TAB.folder) {
				image = folders.get(position).images.get(0);
			} else {
				image = photos.get(position);
			}
			if (convertView.getTag() != image) {
				convertView.setTag(image);
				renderImageView(convertView);
			}
			return convertView;
		}

	}

	static final int TAG_KEY_TAB = TAB.class.hashCode();

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private void renderImageView(final View convertView) {
		final TAB tab = (TAB) convertView.getTag(TAG_KEY_TAB);
		final Media image = (Media) convertView.getTag();
		if (image != null) {
			final CacheableImageView imageView = (CacheableImageView) convertView.getTag(R.id.image_view);
			imageView.setTag(image);
			final View check_image = (View) convertView.getTag(R.id.check_image);
			check_image.setVisibility(View.GONE);
			final ImageView uploadedImageView = (ImageView) convertView.getTag(R.id.uploaded);
			uploadedImageView.setVisibility(View.GONE);
			if (tab == TAB.folder) {
				Folder folder = foldersMap.get(image);
				((TextView) convertView.getTag(R.id.size)).setText("" + folder.size);
				((TextView) convertView.getTag(R.id.title)).setText(folder.name);
				((View) convertView.getTag(R.id.synced)).setVisibility(Utils.isSynced(folder) ? View.VISIBLE : View.GONE);
			}

			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(image.path + "_" + tab.thumbLayoutId);
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
			}

			executorService.submit(new Runnable() {
				@Override
				public void run() {
					if (imageView.getTag() == image) {
						final boolean isUploaded;
						final boolean isUploading;
						if (tab == TAB.folder) {
							Folder folder = foldersMap.get(image);
							isUploaded = FlickrApi.isUploaded(folder);
							isUploading = UploadService.isUploading(folder);
						} else {
							isUploaded = FlickrApi.isUploaded(image);
							isUploading = UploadService.isUploading(image);
						}
						final int privacyResource;
						if (isUploaded && tab != TAB.folder) {
							privacyResource = getPrivacyResource(FlickrApi.getPrivacy(image));
						} else {
							privacyResource = 0;
						}
						// LOG.debug(tab + ", isUploaded=" + isUploaded);
						final CacheableBitmapDrawable bitmapDrawable;
						if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
							bitmapDrawable = wrapper;
						} else {
							Bitmap bitmap = Utils.getBitmap(image, tab);
							if (bitmap != null) {
								bitmapDrawable = Utils.getCache().put(image.path + "_" + tab.thumbLayoutId, bitmap);
							} else {
								bitmapDrawable = null;
							}
						}
						final boolean isChecked = getImageSelected(SelectionType.all).contains(image);

						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if (imageView.getTag() == image) {
									check_image.setVisibility(isChecked ? View.VISIBLE : View.GONE);
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
				}
			});
		}
	}

	static SparseArray<String> uploadedPhotos = new SparseArray<String>();

	private Menu menu;

	private MainTabView mainTabView;

	private boolean paused = false;

	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.menu = menu;
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		renderMenu();
		return true;
	}

	private void renderMenu() {
		if (menu != null) {
			menu.findItem(R.id.cancel_uploads).setVisible(UploadService.getNbQueued() > 0);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Mixpanel.track("UI actionBar " + getResources().getResourceEntryName(item.getItemId()));
		switch (item.getItemId()) {
		case R.id.cancel_uploads:
			int nbQueued = UploadService.getNbQueued();
			if (nbQueued > 0)
				Utils.confirmCancel(FlickrUploaderActivity.this, nbQueued);
			break;
		case R.id.preferences:
			startActivity(new Intent(this, Preferences.class));
			break;
		case R.id.faq:
			String url = "https://github.com/rafali/flickr-uploader/wiki/FAQ";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			break;
		}

		return (super.onOptionsItemSelected(item));
	}

	@Override
	protected void onResume() {
		paused = false;
		super.onResume();
		refresh(false);
		UploadService.wake();
		renderPremium();
	}

	@UiThread
	void confirmSync() {
		final CharSequence[] modes = { "Sync current " + photos.size() + " photos and auto-upload new photos", "Auto-upload new photos", "Manually upload photos" };
		AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
		alt_bld.setTitle("Flickr Sync (" + Utils.getStringProperty(STR.userName) + ")");
		alt_bld.setSingleChoiceItems(modes, 0, null);
		alt_bld.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				ListView lw = ((AlertDialog) dialog).getListView();
				switch (lw.getCheckedItemPosition()) {
				case 0:
					UploadService.enqueue(photos, Utils.getInstantAlbumId(), STR.instantUpload);
					Utils.setBooleanProperty(Preferences.AUTOUPLOAD, true);
					break;
				case 1:
					Utils.setBooleanProperty(Preferences.AUTOUPLOAD, true);
					break;
				case 2:
					Utils.setBooleanProperty(Preferences.AUTOUPLOAD, false);
				default:
					break;
				}
			}
		});
		alt_bld.setNegativeButton("Cancel", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD, false);
			}
		});
		alt_bld.setCancelable(false);
		AlertDialog alert = alt_bld.create();
		alert.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (IabHelper.get(false) != null && IabHelper.get(false).handleActivityResult(requestCode, resultCode, data)) {
			return;
		}
		if (resultCode == WebAuth.RESULT_CODE_AUTH) {
			if (FlickrApi.isAuthentified() && getImageSelected(SelectionType.all).isEmpty())
				confirmSync();
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@UiThread(delay = 100)
	public void refresh(boolean reload) {
		if (reload) {
			load();
		} else {
			if (mainTabView != null) {
				renderMenu();
				for (int k = 0; k < mainTabView.getTabCount(); k++) {
					ViewGroup grid = (ViewGroup) mainTabView.getTabView(k);
					int childCount = grid.getChildCount();
					for (int i = 0; i < childCount; i++) {
						View convertView = grid.getChildAt(i);
						Media image = (Media) convertView.getTag();
						if (image != null) {
							renderImageView(convertView);
						}
					}
				}
			}
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

	@UiThread
	public void renderPremium() {
		if (footer != null && !destroyed) {
			if (Utils.isPremium()) {
				footer.setVisibility(View.GONE);
			} else {
				footer.setVisibility(View.VISIBLE);
				if (Utils.isTrial()) {
					footer.setText("You are in the trial period. The premium Auto-Upload feature is available to you in trial until "
							+ SimpleDateFormat.getDateInstance().format(new Date(Utils.trialUntil())) + ". Click here for more info.");
				} else {
					footer.setText("Your trial period has expired. Click here to continue to use the Auto-Upload feature.");
				}
			}
		}
	}

}
