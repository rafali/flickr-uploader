package com.rafali.flickruploader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;
import uk.co.senab.bitmapcache.CacheableImageView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.util.Log;
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
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.base.Joiner;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.flickruploader.FlickrApi.PRIVACY;

@EActivity
public class FlickrUploaderActivity extends Activity {

	private static final int MAX_LINK_SHARE = 5;

	static final String TAG = FlickrUploaderActivity.class.getSimpleName();

	GridView photoGrid;
	GridView folderGrid;
	ListView photoFeed;

	ActionMode mMode;

	private static FlickrUploaderActivity instance;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		Log.d(TAG, "onCreate " + bundle);
		startService(new Intent(this, UploadService.class));
		mainTabView = new MainTabView();
		setContentView(mainTabView);
		if (Utils.getStringProperty(STR.accessToken) == null) {
			Utils.confirmSignIn(FlickrUploaderActivity.this);
		}
		load();
		if (instance != null)
			instance.finish();
		instance = this;

	}

	private static final int FEED_TAB_INDEX = 0;
	private static final int PHOTO_TAB_INDEX = 1;
	private static final int FOLDER_TAB_INDEX = 2;

	class MainTabView extends TabView {

		public MainTabView() {
			super(FlickrUploaderActivity.this, null, 3, PHOTO_TAB_INDEX);
		}

		@Override
		protected View createTabViewItem(int position) {
			View view = null;
			if (position == PHOTO_TAB_INDEX) {
				view = photoGrid = (GridView) View.inflate(FlickrUploaderActivity.this, R.layout.photo_grid, null);
				view.setTag(R.layout.photo_grid_thumb);
			} else if (position == FOLDER_TAB_INDEX) {
				view = folderGrid = (GridView) View.inflate(FlickrUploaderActivity.this, R.layout.folder_grid, null);
				view.setTag(R.layout.folder_grid_thumb);
			} else if (position == FEED_TAB_INDEX) {
				view = photoFeed = (ListView) View.inflate(FlickrUploaderActivity.this, R.layout.photo_feed, null);
				view.setTag(R.layout.photo_feed_thumb);
			}
			return view;
		}

		@Override
		protected int getTabViewItemTitle(int position) {
			if (position == PHOTO_TAB_INDEX) {
				return R.string.photos;
			} else if (position == FOLDER_TAB_INDEX) {
				return R.string.folders;
			} else if (position == FEED_TAB_INDEX) {
				return R.string.feed;
			}
			return R.string.folders;
		}

		@Override
		public void onPageSelected(int position) {
			super.onPageSelected(position);
			ViewGroup view = (ViewGroup) getCurrentView();
			if (view != null && mainTabView != null) {
				if (position == PHOTO_TAB_INDEX) {
					view = photoGrid;
				} else if (position == FOLDER_TAB_INDEX) {
					view = folderGrid;
				} else if (position == FEED_TAB_INDEX) {
					view = photoFeed;
				}
				if (view != null) {
					for (int i = 0; i < view.getChildCount(); i++) {
						renderImageView(view.getChildAt(i), (Integer) view.getTag());
					}
				}
				updateCount();
			}
		}

	}

	private void load() {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				images = Utils.loadImages(null);
				folders = Utils.getFolders(images);
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
		Log.d(TAG, "onNewIntent : " + intent);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d(TAG, "onConfigurationChanged");
		refresh(false);
	}

	public static FlickrUploaderActivity getInstance() {
		return instance;
	}

	void testNotification() {
		BackgroundExecutor.execute(new Runnable() {
			Image image = images.get(0);
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
		Log.d(TAG, "onDestroy");
		super.onDestroy();
		if (instance == this)
			instance = null;
	}

	@UiThread
	void init() {
		photoGrid.setAdapter(new PhotoAdapter(R.layout.photo_grid_thumb));
		photoGrid.setOnItemClickListener(onItemClickListener);

		folderGrid.setAdapter(new PhotoAdapter(R.layout.folder_grid_thumb));
		folderGrid.setOnItemClickListener(onItemClickListener);
		folderGrid.setOnItemLongClickListener(onItemLongClickListener);

		photoFeed.setAdapter(new PhotoAdapter(R.layout.photo_feed_thumb));
		photoFeed.setOnItemClickListener(onItemClickListener);
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
				getImageSelected(SelectionType.all).add((Image) convertView.getTag());
			}
			updateCount();
		}
	};

	OnItemLongClickListener onItemLongClickListener = new OnItemLongClickListener() {
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
			Image image = (Image) v.getTag();
			final Folder selectedFolder = foldersMap.get(image);
			Builder builder = new AlertDialog.Builder(FlickrUploaderActivity.this).setTitle("Auto upload");
			if (Utils.isIgnored(selectedFolder)) {
				builder.setMessage("Enable auto upload for " + selectedFolder.name + "?");
				builder.setPositiveButton("Enable", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Utils.setIgnored(selectedFolder, false);
						refresh(false);
					}
				});
			} else {
				builder.setMessage("Disable auto upload for " + selectedFolder.name + "?");
				builder.setPositiveButton("Disable", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Utils.setIgnored(selectedFolder, true);
						refresh(false);
					}
				});
			}
			builder.setNegativeButton("Cancel", null);
			builder.show();
			return false;
		}
	};

	void updateCount() {
		int size = 0;
		if (mainTabView.getCurrentView() == folderGrid) {
			for (Image image : getImageSelected(SelectionType.all)) {
				Folder folder = foldersMap.get(image);
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
			if (mainTabView.getCurrentView() != folderGrid) {
				setShareIntent();
			}
		} else if (size == 0 && mMode != null) {
			mMode.finish();
		}
	}
	private MenuItem shareItem;
	private ShareActionProvider shareActionProvider;

	@UiThread(delay = 200)
	void setShareIntent() {
		if (!getImageSelected(SelectionType.all).isEmpty() && shareItem != null) {
			Map<String, String> shortUrls = new LinkedHashMap<String, String>();
			for (Image image : getImageSelected(SelectionType.all)) {
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
			if (isFolderTab()) {
				shareItem.setVisible(false);
			}
			privacyItem = menu.findItem(R.id.menu_item_privacy);

			shareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
			shareActionProvider.setOnShareTargetSelectedListener(new ShareActionProvider.OnShareTargetSelectedListener() {
				@Override
				public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
					Log.d(TAG, "intent : " + intent);
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
			case R.id.menu_item_select_all:
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_select_all", 0L);
				if (getImageSelected(SelectionType.auto).isEmpty()) {
					Collection<Image> allImages;
					if (isFolderTab()) {
						allImages = foldersMap.keySet();
					} else {
						allImages = images;
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
				break;
			case R.id.menu_item_privacy:
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_privacy", 0L);
				Collection<Image> selectedImages;
				if (isFolderTab()) {
					selectedImages = new ArrayList<Image>();
					for (Image image : getImageSelected(SelectionType.all)) {
						selectedImages.addAll(foldersMap.get(image).images);
					}
				} else {
					selectedImages = getImageSelected(SelectionType.all);
				}
				PRIVACY privacy = null;
				for (Image image : selectedImages) {
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
							for (Image image : getImageSelected(SelectionType.all)) {
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
				break;
			case R.id.menu_item_upload:
				EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_upload", 0L);
				final List<Image> selection = new ArrayList<Image>(getImageSelected(SelectionType.all));
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

				break;
			}
			return false;
		}

	};

	@UiThread
	void confirmUpload(final List<Image> selection) {
		if (isFolderTab()) {
			new AlertDialog.Builder(this).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + Utils.getInstantAlbumTitle() + ")", "One set per folder", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							int count = 0;
							switch (which) {
							case 0:
								for (Image image : selection) {
									Folder folder = foldersMap.get(image);
									count += folder.images.size();
									UploadService.enqueue(folder.images, Utils.getInstantAlbumId(), STR.instantUpload);
								}
								toast(count + " photos enqueued");
								break;
							case 1:
								for (Image image : selection) {
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
							Log.d(TAG, "which : " + which);
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
							Log.d(TAG, "which : " + which);
						}
					}).show();
		}
		// Notifications.notify(40, selection.get(0), 1, 1);
		// refresh(false);
	}

	@UiThread
	void showExistingSetDialog(final List<Image> selection) {
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
									Log.d(TAG, "selected : " + photosetIds.get(which) + " - " + photosetTitles.get(which));
									String photoSetId = photosetIds.get(which);
									int count = 0;
									if (isFolderTab()) {
										for (Image image : selection) {
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
	void showNewSetDialog(final List<Image> selection) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Photo Set Title");

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				Log.d(TAG, "value : " + value);
				if (ToolString.isBlank(value)) {
					showNewSetDialog(selection);
				} else {
					int count = 0;
					if (isFolderTab()) {
						for (Image image : selection) {
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
		return mainTabView.getCurrentView() == folderGrid;
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

	Map<String, List<Image>> imagesSelectedMap = new HashMap<String, List<Image>>();

	private List<Image> getImageSelected(SelectionType type) {
		String key = mainTabView.getCurrentView().getClass().getSimpleName() + "_" + mainTabView.getCurrentView().getTag() + "_" + type;
		List<Image> imagesSelected = imagesSelectedMap.get(key);
		if (imagesSelected == null) {
			imagesSelected = new ArrayList<Image>();
			imagesSelectedMap.put(key, imagesSelected);
		}
		return imagesSelected;
	}

	enum SelectionType {
		all, auto
	}

	List<Image> images;
	List<Folder> folders;
	Map<Image, Folder> foldersMap = new HashMap<Image, Folder>();

	class PhotoAdapter extends BaseAdapter {

		private int thumbLayoutId;

		public PhotoAdapter(int thumbLayoutId) {
			this.thumbLayoutId = thumbLayoutId;
		}

		@Override
		public int getCount() {
			if (thumbLayoutId == R.layout.folder_grid_thumb) {
				return folders.size();
			} else {
				return images.size();
			}
		}

		@Override
		public Object getItem(int position) {
			if (thumbLayoutId == R.layout.folder_grid_thumb) {
				return folders.get(position).images.get(0);
			} else {
				return images.get(position);
			}
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(thumbLayoutId, parent, false);
				if (thumbLayoutId == R.layout.photo_feed_thumb) {
					convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScreenWidthPx()));
				} else if (thumbLayoutId == R.layout.folder_grid_thumb) {
					convertView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Utils.getScreenWidthPx() / 2));
				}
			}
			final Image image;
			if (thumbLayoutId == R.layout.folder_grid_thumb) {
				image = folders.get(position).images.get(0);
			} else {
				image = images.get(position);
			}
			if (convertView.getTag() != image) {
				convertView.setTag(image);
				renderImageView(convertView, thumbLayoutId);
			}
			return convertView;
		}

	}

	private void renderImageView(final View convertView, final int thumbLayoutId) {
		final Image image = (Image) convertView.getTag();
		if (image == null) {
			convertView.setVisibility(View.INVISIBLE);
		} else {
			convertView.setVisibility(View.VISIBLE);
			convertView.findViewById(R.id.check_image).setVisibility(getImageSelected(SelectionType.all).contains(image) ? View.VISIBLE : View.GONE);
			if (thumbLayoutId == R.layout.folder_grid_thumb) {
				Folder folder = foldersMap.get(image);
				((TextView) convertView.findViewById(R.id.size)).setText("" + folder.size);
				((TextView) convertView.findViewById(R.id.title)).setText(folder.name);
				convertView.findViewById(R.id.uploading).setVisibility(UploadService.isUploading(folder) ? View.VISIBLE : View.GONE);
				convertView.findViewById(R.id.ignore).setVisibility(Utils.isIgnored(folder) ? View.VISIBLE : View.GONE);
			} else {
				convertView.findViewById(R.id.uploading).setVisibility(UploadService.isUploading(image) ? View.VISIBLE : View.GONE);
			}

			final CacheableImageView imageView = (CacheableImageView) convertView.findViewById(R.id.image_view);
			final ImageView uploadedImageView = (ImageView) convertView.findViewById(R.id.uploaded);
			imageView.setTag(image);
			BitmapDrawable wrapper = Utils.getCache().getFromMemoryCache(image.path + "_" + thumbLayoutId);
			if (null != wrapper && !wrapper.getBitmap().isRecycled()) {
				// The cache has it, so just display it
				boolean isUploaded;
				if (thumbLayoutId == R.layout.folder_grid_thumb) {
					isUploaded = FlickrApi.isUploaded(foldersMap.get(image));
				} else {
					isUploaded = FlickrApi.isUploaded(image);
				}
				uploadedImageView.setVisibility(isUploaded ? View.VISIBLE : View.GONE);
				if (isUploaded && thumbLayoutId != R.layout.folder_grid_thumb) {
					uploadedImageView.setImageResource(getPrivacyResource(FlickrApi.getPrivacy(image)));
				}
				imageView.setImageDrawable(wrapper);
			} else {
				imageView.setImageDrawable(null);
				BackgroundExecutor.execute(new Runnable() {
					@Override
					public void run() {
						final boolean isUploaded;
						if (thumbLayoutId == R.layout.folder_grid_thumb) {
							isUploaded = FlickrApi.isUploaded(foldersMap.get(image));
						} else {
							isUploaded = FlickrApi.isUploaded(image);
						}
						final Bitmap bitmap = Utils.getBitmap(image, thumbLayoutId);
						if (bitmap != null) {
							final CacheableBitmapDrawable bitmapDrawable = Utils.getCache().put(image.path + "_" + thumbLayoutId, bitmap);
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (imageView.getTag() == image) {
										uploadedImageView.setVisibility(isUploaded ? View.VISIBLE : View.GONE);
										if (isUploaded && thumbLayoutId != R.layout.folder_grid_thumb) {
											uploadedImageView.setImageResource(getPrivacyResource(FlickrApi.getPrivacy(image)));
										}
										imageView.setImageDrawable(bitmapDrawable);
									}
								}
							});
						}
					}
				});
			}
		}
	}
	static SparseArray<String> uploadedPhotos = new SparseArray<String>();

	private Menu menu;

	private MainTabView mainTabView;

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
		}

		return (super.onOptionsItemSelected(item));
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
	}

	@UiThread
	void confirmSync() {
		final CharSequence[] modes = { "Sync current " + images.size() + " photos and auto-upload new photos", "Auto-upload new photos", "Manually upload photos" };
		AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
		alt_bld.setTitle("Flickr Sync (" + Utils.getStringProperty(STR.userName) + ")");
		alt_bld.setSingleChoiceItems(modes, 0, null);
		alt_bld.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				ListView lw = ((AlertDialog) dialog).getListView();
				switch (lw.getCheckedItemPosition()) {
				case 0:
					UploadService.enqueue(images, Utils.getInstantAlbumId(), STR.instantUpload);
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
			renderMenu();
			for (int k = 0; k < mainTabView.getTabCount(); k++) {
				ViewGroup grid = (ViewGroup) mainTabView.getTabView(k);
				int childCount = grid.getChildCount();
				for (int i = 0; i < childCount; i++) {
					View convertView = grid.getChildAt(i);
					Image image = (Image) convertView.getTag();
					if (image != null) {
						renderImageView(convertView, (Integer) grid.getTag());
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
}
