package com.rafali.flickruploader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.support.v4.app.ShareCompat;
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
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Ordering;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.Utils.Callback;
import com.rafali.flickruploader.Utils.MediaType;
import com.rafali.flickruploader.Utils.VIEW_SIZE;
import com.rafali.flickruploader.billing.IabHelper;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.flickr_uploader_slider_activity)
public class FlickrUploaderActivity extends Activity {

	private static final int MAX_LINK_SHARE = 5;

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploaderActivity.class);

	private static FlickrUploaderActivity instance;
	private FlickrUploaderActivity activity;

	@Override
	public void onCreate(Bundle bundle) {
		activity = this;
		super.onCreate(bundle);
		load();
		LOG.debug("onCreate " + bundle);
		UploadService.wake();
		if (Utils.getStringProperty(STR.accessToken) == null) {
			Utils.confirmSignIn(activity);
		}
		if (instance != null)
			instance.finish();
		instance = activity;
		Utils.checkPremium(false, new Utils.Callback<Boolean>() {
			@Override
			public void onResult(Boolean result) {
				renderPremium();
			}
		});
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
							confirmUpload(loadImages, false);
						} else {
							toast("No media found");
						}
					}
				}
			}
		}
	}

	@UiThread
	void toast(String message) {
		Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
	}

	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

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

		Collections.sort(medias, Utils.MEDIA_COMPARATOR);

		computeHeaders();

		init();
		// test();
	}

	@Background
	void test() {
		int size = medias.size();
		for (int i = 0; i < size; i++) {
			Media media = medias.get(i);

			Bitmap bitmap = null;
			// try {
			// ExifInterface exif = new ExifInterface(media.path);
			// if (exif.hasThumbnail()) {
			// byte[] thumbnail = exif.getThumbnail();
			// bitmap = BitmapFactory.decodeByteArray(thumbnail, 0,
			// thumbnail.length);
			// LOG.info(i + "/" + size + " EXIF : " + bitmap.getWidth() + "x" +
			// bitmap.getHeight());
			// }
			// } catch (Throwable e) {
			// LOG.error(ToolString.stack2string(e));
			// }

			if (bitmap == null) {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1;
				options.inPurgeable = true;
				options.inInputShareable = true;
				bitmap = Images.Thumbnails.getThumbnail(getContentResolver(), media.id, Images.Thumbnails.MINI_KIND, options);
				LOG.info(i + "/" + size + " MINI_KIND : " + bitmap.getWidth() + "x" + bitmap.getHeight());
			}

			// final BitmapFactory.Options opts = new BitmapFactory.Options();
			// opts.inJustDecodeBounds = true;
			// opts.inPurgeable = true;
			// opts.inInputShareable = true;
			// BitmapFactory.decodeFile(media.path, opts);
			// // BitmapFactory.decodeFileDescriptor(file., null,
			// // opts);
			//
			// // Calculate inSampleSize
			// opts.inJustDecodeBounds = false;
			// opts.inSampleSize = Utils.calculateInSampleSize(opts, 800, 600);
			// Bitmap bitmap = BitmapFactory.decodeFile(media.path, opts);

		}
	}

	@UiThread
	void computeHeaders() {
		headers = new ArrayList<Header>();
		headerMap = new HashMap<Media, Header>();
		headerIds = new HashMap<String, Header>();
		thumbItems = new ArrayList<Object>();
		computeNbColumn();

		Media[] mediaRow = null;
		int currentIndex = 0;

		for (Media media : medias) {
			String id = format.format(new Date(media.date));
			Header header = headerIds.get(id);
			if (header == null) {
				header = new Header(id, id);
				headerIds.put(id, header);
				headers.add(header);
				thumbItems.add(header);
				mediaRow = null;
			} else {
				header.count++;
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

		photoAdapter.notifyDataSetChanged();
	}

	Map<Media, Header> headerMap;
	List<Header> headers;
	Map<String, Header> headerIds;

	class Header {
		String id;
		String title;
		int count = 1;
		boolean collapsed = false;
		boolean selected = false;

		Header(String id, String title) {
			this.id = id;
			this.title = title;
		}

		@Override
		public String toString() {
			return id + ":" + count + ":" + title + ":collapsed=" + collapsed + ":selected=" + selected;
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(activity);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(activity);
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
		computeHeaders();
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
	}

	boolean destroyed = false;

	PhotoAdapter photoAdapter = new PhotoAdapter();

	@UiThread
	void init() {
		if (listView == null) {
			final RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.container);
			listView = (ListView) View.inflate(activity, R.layout.grid, null);
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
		renderPremium();
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
		LOG.info(view_size + ", nb_column : " + nb_column + ", screenWidthPx : " + screenWidthPx);
	}

	int computed_req_height = 100;
	int computed_req_width = 100;
	int computed_nb_column = 2;

	private MenuItem shareItem;
	private ShareActionProvider shareActionProvider;

	@UiThread(delay = 200)
	void setShareIntent() {
		if (!selectedMedia.isEmpty() && shareItem != null) {
			Map<String, String> shortUrls = new LinkedHashMap<String, String>();
			for (Media image : selectedMedia) {
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
				Intent shareIntent = ShareCompat.IntentBuilder.from(activity).setType("text/*").setText(Joiner.on(" ").join(shortUrls.values())).getIntent();
				shareIntent.putExtra("photoIds", Joiner.on(",").join(shortUrls.keySet()));
				shareActionProvider.setShareIntent(shareIntent);
			}
		}
	}

	private MenuItem privacyItem;

	// final ActionMode.Callback mCallback = new ActionMode.Callback() {
	//
	// /**
	// * Invoked whenever the action mode is shown. This is invoked
	// * immediately after onCreateActionMode
	// */
	// @Override
	// public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	// return false;
	// }
	//
	// /** Called when user exits action mode */
	// @Override
	// public void onDestroyActionMode(ActionMode mode) {
	// mMode = null;
	// selectedMedia.clear();
	// for (Header header : headers) {
	// header.selected = false;
	// }
	// renderSelection();
	// }
	//
	// /**
	// * This is called when the action mode is created. This is called by
	// * startActionMode()
	// */
	// @Override
	// public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	// getMenuInflater().inflate(R.menu.context_menu, menu);
	// shareItem = menu.findItem(R.id.menu_item_share);
	// // shareItem.setVisible(!isFolderTab());
	// privacyItem = menu.findItem(R.id.menu_item_privacy);
	//
	// shareActionProvider = (ShareActionProvider)
	// shareItem.getActionProvider();
	// shareActionProvider.setOnShareTargetSelectedListener(new
	// ShareActionProvider.OnShareTargetSelectedListener() {
	// @Override
	// public boolean onShareTargetSelected(ShareActionProvider
	// shareActionProvider, Intent intent) {
	// LOG.debug("intent : " + intent);
	// if (intent.hasExtra("photoIds")) {
	// List<String> privatePhotoIds = new ArrayList<String>();
	// String[] photoIds = intent.getStringExtra("photoIds").split(",");
	// for (String photoId : photoIds) {
	// if (FlickrApi.getPrivacy(photoId) != PRIVACY.PUBLIC) {
	// privatePhotoIds.add(photoId);
	// }
	// }
	// if (privatePhotoIds.size() > 0) {
	// FlickrApi.setPrivacy(PRIVACY.PUBLIC, privatePhotoIds);
	// }
	// Mixpanel.increment("photos_shared", photoIds.length);
	// }
	// return false;
	// }
	// });
	// return true;
	// }
	//
	// /** This is called when an item in the context menu is selected */
	// @Override
	// public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	// Mixpanel.track("UI actionMode " + getItemName(item));
	// switch (item.getItemId()) {
	// case R.id.menu_item_select_all: {
	// EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_select_all",
	// 0L);
	// selectedMedia.clear();
	// selectedMedia.addAll(photos);
	//
	// renderSelection();
	// }
	// break;
	// case R.id.menu_item_privacy: {
	// EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_privacy",
	// 0L);
	// Collection<Media> selectedImages;
	// selectedImages = selectedMedia;
	// PRIVACY privacy = null;
	// for (Media image : selectedImages) {
	// if (privacy == null) {
	// privacy = FlickrApi.getPrivacy(image);
	// } else {
	// if (privacy != FlickrApi.getPrivacy(image)) {
	// privacy = null;
	// break;
	// }
	// }
	// }
	// Utils.dialogPrivacy(activity, privacy, new
	// Utils.Callback<FlickrApi.PRIVACY>() {
	// @Override
	// public void onResult(PRIVACY result) {
	// if (result != null) {
	// List<String> photoIds = new ArrayList<String>();
	// for (Media image : selectedMedia) {
	// String photoId = FlickrApi.getPhotoId(image);
	// if (photoId != null && FlickrApi.getPrivacy(photoId) != result) {
	// photoIds.add(photoId);
	// }
	// }
	// if (!photoIds.isEmpty()) {
	// FlickrApi.setPrivacy(result, photoIds);
	// }
	// }
	// }
	// });
	// }
	// break;
	// case R.id.menu_item_upload: {
	// EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_upload",
	// 0L);
	// final List<Media> selection = new ArrayList<Media>(selectedMedia);
	// if (FlickrApi.isAuthentified()) {
	// // foldersMap.clear();
	// BackgroundExecutor.execute(new Runnable() {
	// @Override
	// public void run() {
	// confirmUpload(selection, false);
	// }
	// });
	// } else {
	// // Notifications.notify(40, selection.get(0), 1, 1);
	// Utils.confirmSignIn(activity);
	// }
	// }
	// break;
	// case R.id.menu_item_dequeue: {
	// EasyTracker.getTracker().sendEvent("ui", "click", "menu_item_dequeue",
	// 0L);
	// final List<Media> selection = new ArrayList<Media>(selectedMedia);
	// BackgroundExecutor.execute(new Runnable() {
	// @Override
	// public void run() {
	// // if (isFolderTab()) {
	// // for (Media image : selection) {
	// // Folder folder = foldersMap.get(image);
	// // UploadService.dequeue(folder.images);
	// // }
	// // } else {
	// UploadService.dequeue(selection);
	// // }
	// refresh(false);
	// }
	// });
	// mMode.finish();
	// }
	// break;
	//
	// }
	// return false;
	// }
	//
	// };

	@UiThread
	void confirmUpload(final List<Media> selection, boolean isFolderTab) {
		if (isFolderTab) {
			// new
			// AlertDialog.Builder(activity).setTitle("Upload to").setPositiveButton(null,
			// null).setNegativeButton(null, null).setCancelable(true)
			// .setItems(new String[] { "Default set (" + STR.instantUpload +
			// ")", "One set per folder", "New set...", "Existing set..." }, new
			// DialogInterface.OnClickListener() {
			// @Override
			// public void onClick(DialogInterface dialog, int which) {
			// switch (which) {
			// case 0:
			// for (Media image : selection) {
			// Folder folder = foldersMap.get(image);
			// enqueue(folder.images, STR.instantUpload);
			// }
			// clearSelection();
			// break;
			// case 1: {
			// List<Folder> folders = new ArrayList<Folder>();
			// for (Media image : selection) {
			// Folder folder = foldersMap.get(image);
			// folders.add(folder);
			// }
			// createOneSetPerFolder(folders);
			// }
			// break;
			// case 2: {
			// List<Folder> folders = new ArrayList<Folder>();
			// for (Media image : selection) {
			// Folder folder = foldersMap.get(image);
			// folders.add(folder);
			// }
			// if (folders.size() == 1) {
			// showNewSetDialog(folders.get(0), selection);
			// } else {
			// showNewSetDialog(null, selection);
			// }
			// }
			// break;
			// case 3:
			// showExistingSetDialog(selection);
			// break;
			// default:
			// break;
			// }
			// LOG.debug("which : " + which);
			// }
			//
			// }).show();
		} else {
			new AlertDialog.Builder(activity).setTitle("Upload to").setPositiveButton(null, null).setNegativeButton(null, null).setCancelable(true)
					.setItems(new String[] { "Default set (" + STR.instantUpload + ")", "New set...", "Existing set..." }, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
							case 0:
								enqueue(selection, STR.instantUpload);
								clearSelection();
								break;
							case 1:
								showNewSetDialog(null, selection);
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
		selectedMedia.clear();
		for (Header header : headers) {
			header.selected = false;
		}
		refresh(false);
	}

	@UiThread
	void showExistingSetDialog(final List<Media> selection) {
		showExistingSetDialog(activity, new Callback<String[]>() {
			@Override
			public void onResult(String[] result) {
				String photoSetTitle = result[1];
				// if (isFolderTab()) {
				// for (Media image : selection) {
				// Folder folder = foldersMap.get(image);
				// enqueue(folder.images, photoSetTitle);
				// }
				// } else {
				enqueue(selection, photoSetTitle);
				// }
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
							Toast.makeText(activity, "No photoset found", Toast.LENGTH_LONG).show();
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
	void showNewSetDialog(final Folder folder, final List<Media> selection) {
		showNewSetDialog(activity, folder == null ? null : folder.name, new Callback<String>() {
			@Override
			public void onResult(final String value) {
				if (ToolString.isBlank(value)) {
					toast("Title cannot be empty");
					showNewSetDialog(folder, selection);
				} else {
					BackgroundExecutor.execute(new Runnable() {
						@Override
						public void run() {
							enqueue(selection, value);
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

	@Background
	void createOneSetPerFolder(List<Folder> folders) {
		for (Folder folder : folders) {
			try {
				enqueue(folder.images, folder.name);
				clearSelection();
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			}
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

	// @UiThread
	// void renderThumbs() {
	// int childCount = mainTabView.getChildCount();
	// for (int i = 0; i < childCount; i++) {
	// View view = mainTabView.getChildAt(i);
	// View check_image = view.findViewById(R.id.check_image);
	// if (check_image != null) {
	// view.setLayoutParams(new
	// GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT,
	// computed_req_height));
	// check_image.setVisibility(selectedMedia.contains(view.getTag()) ?
	// View.VISIBLE : View.GONE);
	// }
	// }
	// for (View headerView : attachedHeaderViews) {
	// renderHeaderSelection(headerView);
	// }
	// mainTabView.requestLayout();
	// }

	void renderHeaderSelection(View headerView) {
		if (headerView != null && headerView.getTag() instanceof Header) {
			Header header = (Header) headerView.getTag();
			// LOG.debug("rendering : " + header + " on " + headerView);
			TextView count = (TextView) headerView.getTag(R.id.count);
			count.setCompoundDrawablesWithIntrinsicBounds(0, 0, header.selected ? R.drawable.checkbox_on : R.drawable.checkbox_off, 0);
			count.setTextColor(getResources().getColor(header.selected ? R.color.litegray : R.color.gray));
			// checkbox.setText("" + header.selected);
		}
	}

	Set<Media> selectedMedia = new HashSet<Media>();

	class PhotoAdapter extends BaseAdapter {

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
				if (convertView == null) {
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
						// final Header header = (Header) title.getTag();
						// header.collapsed = !header.collapsed;
						// photos = new ArrayList<Media>(medias);
						// Iterator<Media> it = photos.iterator();
						// while (it.hasNext()) {
						// Media media = it.next();
						// if (headerMap.get(media).collapsed) {
						// it.remove();
						// }
						// }
						// photoAdapter.notifyDataSetChanged();
						//
						// if (header.collapsed) {
						// // hack to make sure the collapsed do not disappear
						// mainTabView.postDelayed(new Runnable() {
						// @Override
						// public void run() {
						// int realIndex = getRealIndex(header);
						// if (mainTabView.getFirstVisiblePosition() >
						// realIndex) {
						// mainTabView.setSelection(realIndex);
						// }
						// }
						// }, 100);
						// }
					}
				});
				title.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Header header = (Header) v.getTag();
						listView.setSelection(getRealIndex(header));
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
						renderHeaderSelection(v);
					}
				});
			} else {
				title = (TextView) convertView.getTag(R.id.title);
				count = (TextView) convertView.getTag(R.id.count);
			}
			Header header = (Header) thumbItems.get(position);
			convertView.setTag(header);
			count.setTag(header);
			title.setTag(header);

			title.setText(header.title);
			count.setText("" + header.count);
			title.setCompoundDrawablesWithIntrinsicBounds(header.collapsed ? R.drawable.expand_off : R.drawable.expand_on, 0, 0, 0);
			renderHeaderSelection(convertView);

			// LOG.debug(header + " : convertView = " + convertView);

			return convertView;
		}

		public int getCountForHeader(int headerPosition) {
			Header header = headers.get(headerPosition);
			if (header.collapsed)
				return 0;
			return header.count;
		}

		public int getNumHeaders() {
			return headers.size();
		}
	}

	int getRealIndex(Header header) {
		int nbColumn = 1;// mainTabView.getNumColumns();
		int realIndex = 0;
		for (Header currentHeader : headers) {
			if (currentHeader == header) {
				break;
			} else {
				realIndex += (int) ((Math.ceil(Double.valueOf(currentHeader.collapsed ? 0 : currentHeader.count) / nbColumn) + 1) * nbColumn);
			}
		}
		return realIndex;
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

	private ListView listView;

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
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.trial_info:
			Utils.showPremiumDialog(activity, new Utils.Callback<Boolean>() {
				@Override
				public void onResult(Boolean result) {
					renderPremium();
				}
			});
			break;
		case R.id.preferences:
			startActivity(new Intent(activity, Preferences.class));
			break;
		case R.id.faq:
			String url = "https://github.com/rafali/flickr-uploader/wiki/FAQ";
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			break;
		case R.id.view_size_small:
			Utils.setViewSize(VIEW_SIZE.small);
			computeHeaders();
			item.setChecked(true);
			break;
		case R.id.view_size_medium:
			Utils.setViewSize(VIEW_SIZE.medium);
			computeHeaders();
			item.setChecked(true);
			break;
		case R.id.view_size_large:
			Utils.setViewSize(VIEW_SIZE.large);
			computeHeaders();
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

		}

		return (super.onOptionsItemSelected(item));
	}

	private String getItemName(MenuItem item) {
		try {
			return getResources().getResourceEntryName(item.getItemId());
		} catch (Throwable e) {
		}
		return "unknown";
	}

	@UiThread
	void showSortDialog() {
		final CharSequence[] modes = { "Recent to old", "Old to recent" };
		AlertDialog.Builder alt_bld = new AlertDialog.Builder(activity);
		alt_bld.setTitle("Sort");
		final int sort_type = (int) Utils.getLongProperty("sort_type");
		alt_bld.setSingleChoiceItems(modes, sort_type, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				if (sort_type != which) {
					LOG.debug("clicked : " + modes[which]);
					Utils.setLongProperty("sort_type", (long) which);
					load();
				}
			}
		});
		AlertDialog alert = alt_bld.create();
		alert.show();
	}

	@Override
	protected void onResume() {
		paused = false;
		super.onResume();
		refresh(false);
		UploadService.wake();
		renderPremium();
		drawerHandleView.onResume();
	}

	@UiThread
	void confirmSync() {
		final CharSequence[] modes = { "Auto-upload new photos", "Auto-upload new videos" };
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Auto-upload (7-days Trial)");
		builder.setMultiChoiceItems(modes, new boolean[] { true, true }, null);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
			}

		});
		builder.setNegativeButton("More options", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ListView lw = ((AlertDialog) dialog).getListView();
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD, lw.isItemChecked(0));
				Utils.setBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, lw.isItemChecked(1));
				startActivity(new Intent(activity, Preferences.class));
			}
		});
		builder.setCancelable(false);
		builder.create().show();
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
					if (convertView.getTag() instanceof Media) {
						renderImageView(convertView, false);
					}
				}
				// mainTabView.postDelayed(new Runnable() {
				// @Override
				// public void run() {
				// mainTabView.requestLayout();
				// }
				// }, 1000);
				for (View headerView : attachedHeaderViews) {
					renderHeaderSelection(headerView);
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
	void renderPremium() {
		if (!destroyed) {
			getActionBar().setSubtitle(null);
			getActionBar().setTitle(null);
			getActionBar().setIcon(null);
			getActionBar().setDisplayShowHomeEnabled(false);
			// if (Utils.isPremium()) {
			// getActionBar().setSubtitle(null);
			// } else {
			// if (Utils.getBooleanProperty(Preferences.AUTOUPLOAD, false) ||
			// Utils.getBooleanProperty(Preferences.AUTOUPLOAD_VIDEOS, false)) {
			// if (Utils.isTrial()) {
			// getActionBar().setSubtitle("Auto-Upload Trial");
			// } else {
			// getActionBar().setSubtitle("Trial Expired");
			// }
			// } else {
			// getActionBar().setSubtitle(null);
			// if (!Utils.isTrial()) {
			// // FIXME
			// }
			// }
			// }
		}
	}

}
