package com.rafali.flickruploader;

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

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.base.Joiner;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.Utils.MediaType;
import com.rafali.flickruploader.billing.IabHelper;

@EActivity(R.layout.flickr_uploader_slider_activity)
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

	private AdView bannerAdView;
	private static final String ADMOD_UNIT_ID = Utils.getString(R.string.admob_unit_id);

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
		handleIntent(getIntent());
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
						confirmUpload(loadImages, false);
					} else {
						toast("No media found for " + imageUri);
					}
				}
			}
		}
	}

	@UiThread
	void toast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

	private static final int AD_FREQ = 5;

	private void load() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				photos = Utils.loadImages(null, MediaType.photo);
				videos = Utils.loadImages(null, MediaType.video);
				if (Utils.isPremium() || Utils.isTrial()) {
					feedPhotos = new ArrayList<Object>(photos);
				} else {
					feedPhotos = new ArrayList<Object>();
					int count = 0;
					int nbAds = 0;
					for (Media media : photos) {
						if (count % AD_FREQ == 1) {
							feedPhotos.add(nbAds % 2 == 0 ? AdSize.BANNER : AdSize.IAB_MRECT);
							nbAds++;
						}
						feedPhotos.add(media);
						count++;
					}
				}

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
		UploadService.unregister(drawerHandleView);
		UploadService.unregister(drawerContentView);
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
		renderPremium();
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
						if (privatePhotoIds.size() > 0) {
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
							confirmUpload(selection, isFolderTab());
						}
					});
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
					Utils.setAutoUploaded(foldersMap.get(image), true);
				}
				refresh(false);
				break;
			case R.id.menu_item_disable_auto_upload:
				for (Media image : getImageSelected(SelectionType.all)) {
					Utils.setAutoUploaded(foldersMap.get(image), false);
				}
				refresh(false);
				break;
			}
			return false;
		}

	};

	@UiThread
	void confirmUpload(final List<Media> selection, boolean isFolderTab) {
		if (isFolderTab) {
			new AlertDialog.Builder(this).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + Utils.getInstantAlbumTitle() + ")", "One set per folder", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								for (Media image : selection) {
									Folder folder = foldersMap.get(image);
									enqueue(false, folder.images, Utils.getInstantAlbumId(), STR.instantUpload);
								}
								clearSelection();
								break;
							case 1:
								for (Media image : selection) {
									Folder folder = foldersMap.get(image);
									enqueue(false, folder.images, folder, null, null);
								}
								clearSelection();
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
								enqueue(false, selection, Utils.getInstantAlbumId(), STR.instantUpload);
								clearSelection();
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
	}

	@UiThread
	void clearSelection() {
		if (mMode != null)
			mMode.finish();
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
									if (isFolderTab()) {
										for (Media image : selection) {
											Folder folder = foldersMap.get(image);
											enqueue(false, folder.images, photoSetId, null);
										}
									} else {
										enqueue(false, selection, photoSetId, null);
									}
									clearSelection();
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
					if (isFolderTab()) {
						for (Media image : selection) {
							Folder folder = foldersMap.get(image);
							enqueue(false, folder.images, null, value);
						}
					} else {
						enqueue(false, selection, null, value);
					}
					clearSelection();
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

	private void enqueue(boolean auto, Collection<Media> images, String photoSetId, String photoSetTitle) {
		enqueue(auto, images, null, photoSetId, photoSetTitle);
	}

	private void enqueue(boolean auto, Collection<Media> images, Folder folder, String photoSetId, String photoSetTitle) {
		int enqueued = UploadService.enqueue(auto, images, folder, photoSetId, photoSetTitle);
		if (slidingDrawer != null && enqueued > 0) {
			slidingDrawer.animateOpen();
			drawerContentView.setCurrentTab(DrawerContentView.TAB_QUEUED_INDEX);
		}
	}

	private boolean isFolderTab() {
		return mainTabView.getCurrentView().getTag() == TAB.folder;
	}

	private boolean isVideoTab() {
		return mainTabView.getCurrentView().getTag() == TAB.video;
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
	List<Object> feedPhotos;
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
				if (tab == TAB.feed) {
					return feedPhotos.size();
				} else {
					return photos.size();
				}
			}
		}

		@Override
		public Object getItem(int position) {
			if (tab == TAB.video) {
				return videos.get(position);
			} else if (tab == TAB.folder) {
				return folders.get(position).images.get(0);
			} else if (tab == TAB.feed) {
				return feedPhotos.get(position);
			} else {
				return photos.get(position);
			}
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		View createAd(AdSize adSize) {
			if (adSize == null) {
				adSize = AdSize.BANNER;
			}
			AdView adView = new AdView(FlickrUploaderActivity.this, adSize, ADMOD_UNIT_ID);
			AdRequest adRequest = new AdRequest();
			adRequest.addTestDevice("DE46A4A314F9E6F59597CD32A63D68C4");
			adView.loadAd(adRequest);
			adView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
			adView.setTag(adSize);
			return adView;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			Object item = getItem(position);
			boolean isAd = (item instanceof AdSize);
			if (convertView == null) {
				if (isAd) {
					convertView = createAd((AdSize) item);
				} else {
					convertView = getLayoutInflater().inflate(tab.thumbLayoutId, parent, false);
					if (tab == TAB.feed) {
						convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, 500));
					} else if (tab == TAB.folder || tab == TAB.video) {
						convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScreenWidthPx() / 2));
					}
					convertView.setTag(TAG_KEY_TAB, tab);
					convertView.setTag(R.id.check_image, convertView.findViewById(R.id.check_image));
					if (tab == TAB.folder) {
						convertView.setTag(R.id.size, convertView.findViewById(R.id.size));
						convertView.setTag(R.id.title, convertView.findViewById(R.id.title));
						convertView.setTag(R.id.synced, convertView.findViewById(R.id.synced));
					} else if (tab == TAB.feed) {
						convertView.setTag(R.id.title, convertView.findViewById(R.id.title));
					}
					convertView.setTag(R.id.uploading, convertView.findViewById(R.id.uploading));
					convertView.setTag(R.id.image_view, convertView.findViewById(R.id.image_view));
					convertView.setTag(R.id.uploaded, convertView.findViewById(R.id.uploaded));
				}
			}
			if (isAd) {
				if (item != convertView.getTag())
					return createAd((AdSize) item);
				return convertView;
			}
			final Media image = (Media) item;
			if (convertView.getTag() != image) {
				convertView.setTag(image);
				renderImageView(convertView);
			}
			return convertView;
		}

		int TYPE_THUMB = 0;
		int TYPE_AD = 1;

		@Override
		public int getItemViewType(int position) {
			if (tab == TAB.feed && !(feedPhotos.get(position) instanceof Media)) {
				return TYPE_AD;
			}
			return TYPE_THUMB;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}
	}

	static final int TAG_KEY_TAB = TAB.class.hashCode();

	ExecutorService executorService = Executors.newSingleThreadExecutor();

	private void renderImageView(final View convertView) {
		final TAB tab = (TAB) convertView.getTag(TAG_KEY_TAB);
		if (convertView.getTag() instanceof Media) {
			final Media image = (Media) convertView.getTag();
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
				((View) convertView.getTag(R.id.synced)).setVisibility(Utils.isAutoUpload(folder) ? View.VISIBLE : View.GONE);
			} else if (tab == TAB.feed) {
				((TextView) convertView.getTag(R.id.title)).setText(image.path);
			}

			final CacheableBitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(image.path + "_" + tab.thumbLayoutId);
			if (wrapper != null && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				imageView.setImageDrawable(wrapper);
				if (tab == TAB.feed) {
					int width = wrapper.getBitmap().getWidth();
					int height = wrapper.getBitmap().getHeight();
					int reqWidth = Utils.getScreenWidthPx();
					int reqHeight = height * reqWidth / width;
					convertView.setLayoutParams(new AbsListView.LayoutParams(reqWidth, reqHeight));
				}
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
										if (tab == TAB.feed) {
											int width = bitmapDrawable.getBitmap().getWidth();
											int height = bitmapDrawable.getBitmap().getHeight();
											int reqWidth = Utils.getScreenWidthPx();
											int reqHeight = height * reqWidth / width;
											convertView.setLayoutParams(new AbsListView.LayoutParams(reqWidth, reqHeight));
										}
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
		inflater.inflate(R.menu.main_menu, menu);
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
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Mixpanel.track("UI actionBar " + getResources().getResourceEntryName(item.getItemId()));
		switch (item.getItemId()) {
		case R.id.trial_info:
			Utils.showPremiumDialog(this, new Utils.Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
					renderPremium();
				}
			});
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

	boolean showFolderAutoUploadDialog = false;

	@UiThread
	void showFolderAutoUploadDialog() {
		if (showFolderAutoUploadDialog) {
			showFolderAutoUploadDialog = false;
			if (Utils.getBooleanProperty(Preferences.AUTOUPLOAD, true) || Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, true)) {
				mainTabView.setCurrentItem(Arrays.asList(TAB.values()).indexOf(TAB.folder));
				AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
				alt_bld.setTitle("Auto-upload folders");
				List<String> foldersName = Utils.getAutoUploadFoldersName();
				int size = foldersName.size();
				String message = "Make sure all the folders you want auto-uploaded are selected (small sync icon on the top right). ";
				if (size > 0) {
					message += size + " folder" + (size > 1 ? "s" : "") + " have already been preselected for you.";
				}
				message += "\n\nIf you have any questions, please check the FAQ first then contact me at flickruploader@rafali.com.";
				alt_bld.setMessage(message);
				alt_bld.setPositiveButton("OK", null);
				AlertDialog alert = alt_bld.create();
				alert.show();
			}
		}
	}

	@UiThread
	void confirmSync() {
		showFolderAutoUploadDialog = true;
		final CharSequence[] modes = { "Auto-upload new photos", "Auto-upload new videos" };
		AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
		alt_bld.setTitle("Auto-upload");
		alt_bld.setMultiChoiceItems(modes, new boolean[] { true, true }, null);
		alt_bld.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
				showFolderAutoUploadDialog();
			}

		});
		alt_bld.setNegativeButton("More options", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
				startActivity(new Intent(FlickrUploaderActivity.this, Preferences.class));
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
			if (FlickrApi.isAuthentified()) {
				confirmSync();
			}
		} else {
			showFolderAutoUploadDialog();
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
		if (!destroyed) {
			boolean showAds = false;
			if (Utils.isPremium()) {
				getActionBar().setTitle("Flickr Uploader");
			} else {
				if (Utils.isTrial()) {
					getActionBar().setTitle("Flickr Uploader (Trial)");
				} else {
					getActionBar().setTitle("Trial Expired");
					showAds = true;
				}
			}
			if (showAds) {
				if (bannerAdView == null) {
					ViewGroup adContainer = (ViewGroup) findViewById(R.id.ad_container);
					adContainer.setVisibility(View.VISIBLE);
					bannerAdView = new AdView(this, AdSize.BANNER, ADMOD_UNIT_ID);
					AdRequest adRequest = new AdRequest();
					adRequest.addTestDevice("DE46A4A314F9E6F59597CD32A63D68C4");
					bannerAdView.loadAd(adRequest);
					// bannerAdView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
					adContainer.addView(bannerAdView);
				}
			} else {
				findViewById(R.id.ad_container).setVisibility(View.GONE);
			}
		}
	}

}
