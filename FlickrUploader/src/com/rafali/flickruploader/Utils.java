package com.rafali.flickruploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.co.senab.bitmapcache.BitmapLruCache;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;

import com.google.common.base.Joiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.rafali.flickruploader.FlickrApi.PRIVACY;

public final class Utils {

	private static final String TAG = Utils.class.getSimpleName();
	private static final float textSize = 16.0f;
	private static BitmapLruCache mCache;

	public static void confirmSignIn(final Activity context) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Mixpanel.track("Sign in dialog");
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Sign into Flickr").setMessage("A Flickr account is required to upload photos.")
						.setPositiveButton("Sign in now", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								context.startActivityForResult(WebAuth_.intent(context).get(), 14);
							}
						}).setNegativeButton("Later", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static void setButtonSize(AlertDialog alert) {
		alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
		alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
	}

	public static void confirmCancel(final Activity context, final int nb) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Cancel uploads").setMessage("Do you want to cancel " + nb + " upload" + (nb > 1 ? "s" : ""))
						.setPositiveButton("Cancel uploads", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								UploadService.cancel(true);
							}
						}).setNegativeButton("Continue", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	private static int screenWidthPx = -1;

	public static int getScreenWidthPx() {
		if (screenWidthPx < 0) {
			Point size = new Point();
			WindowManager wm = (WindowManager) FlickrUploader.getAppContext().getSystemService(Context.WINDOW_SERVICE);
			wm.getDefaultDisplay().getSize(size);
			screenWidthPx = Math.min(size.x, size.y);
		}
		return screenWidthPx;
	}

	public static void dialogPrivacy(final Activity context, final PRIVACY privacy, final Callback<PRIVACY> callback) {
		context.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				final PRIVACY[] privacies = FlickrApi.PRIVACY.values();
				String[] items = new String[privacies.length];
				for (int i = 0; i < FlickrApi.PRIVACY.values().length; i++) {
					items[i] = privacies[i].getSimpleName();
				}
				int checked = -1;
				if (privacy != null) {
					checked = privacy.ordinal();
				}
				final PRIVACY[] result = new PRIVACY[1];
				AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle("Choose privacy").setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result[0] = privacies[which];
					}
				}).setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (privacy != result[0])
							callback.onResult(result[0]);
					}
				}).setNegativeButton("Cancel", null).setCancelable(false).show();
				setButtonSize(alertDialog);
			}
		});
	}

	static final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(FlickrUploader.getAppContext());

	public static void setStringProperty(String property, String value) {
		Editor editor = sp.edit();
		editor.putString(property, value);
		editor.apply();
		editor.commit();
	}

	public static String getStringProperty(String property) {
		return sp.getString(property, null);
	}

	public static void clearProperty(String property) {
		Editor editor = sp.edit();
		editor.remove(property);
		editor.apply();
		editor.commit();
	}

	public static void setLongProperty(String property, Long value) {
		Editor editor = sp.edit();
		editor.putLong(property, value);
		editor.apply();
		editor.commit();
	}

	private static String email;

	public static String getEmail() {
		if (email == null) {
			email = getStringProperty(STR.email);
			if (email == null) {
				AccountManager accountManager = AccountManager.get(FlickrUploader.getAppContext());
				final Account[] accounts = accountManager.getAccountsByType("com.google");
				for (Account account : accounts) {
					if (account.name != null) {
						String name = account.name.toLowerCase(Locale.ENGLISH).trim();
						if (account.name.matches(ToolString.REGEX_EMAIL)) {
							email = name;
						}
					}
				}
				if (email == null) {
					email = getDeviceId() + "@fake.com";
				}
				setStringProperty(STR.email, email);
			}
		}
		return email;
	}

	private static String deviceId;

	public static String getDeviceId() {
		if (deviceId == null) {
			deviceId = Secure.getString(FlickrUploader.getAppContext().getContentResolver(), Secure.ANDROID_ID);
			if (deviceId == null) {
				deviceId = getStringProperty("deviceId");
				if (deviceId == null) {
					deviceId = "fake_" + UUID.randomUUID();
					setStringProperty("deviceId", deviceId);
				}
			}
		}
		return deviceId;
	}

	public static long getLongProperty(String property) {
		return sp.getLong(property, 0);
	}

	public static boolean getBooleanProperty(String property, boolean defaultValue) {
		return sp.getBoolean(property, defaultValue);
	}

	public static PRIVACY getDefaultPrivacy() {
		return PRIVACY.valueOf(sp.getString(Preferences.UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString()));
	}

	public static void setBooleanProperty(String property, Boolean value) {
		Editor editor = sp.edit();
		editor.putBoolean(property, value);
		editor.apply();
		editor.commit();
	}

	public static void setImages(String key, Collection<Image> images) {
		String serialized;
		if (images == null || images.isEmpty()) {
			serialized = null;
		} else {
			List<Integer> ids = new ArrayList<Integer>();
			for (Image image : images) {
				ids.add(image.id);
			}
			serialized = Joiner.on(",").join(ids);
		}
		Log.d(TAG, "persisting images " + key + " : " + serialized);
		setStringProperty(key, serialized);
	}

	public static List<Image> getImages(String key) {
		String queueIds = getStringProperty(key);
		if (ToolString.isNotBlank(queueIds)) {
			String filter = MediaStore.Images.Media._ID + " IN (" + queueIds + ")";
			List<Image> images = Utils.loadImages(filter);
			Log.d(TAG, key + " - queueIds : " + queueIds.split(",").length + ", images:" + images.size());
			return images;
		}
		return null;
	}

	public static Image getImage(int id) {
		String filter = MediaStore.Images.Media._ID + " IN (" + id + ")";
		List<Image> images = Utils.loadImages(filter);
		if (!images.isEmpty()) {
			return images.get(0);
		}
		Log.w(TAG, "id " + id + " not found!");
		return null;
	}

	public static void setMapProperty(String property, Map<String, String> map) {
		Editor editor = sp.edit();
		if (map == null || map.isEmpty()) {
			editor.putString(property, null);
		} else {
			StringBuilder strb = new StringBuilder();
			Iterator<String> it = map.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				String value = map.get(key);
				strb.append(key);
				strb.append("|=|");
				strb.append(value);
				if (it.hasNext())
					strb.append("|;|");
			}
			editor.putString(property, strb.toString());
		}
		editor.apply();
		editor.commit();
	}

	public static BitmapLruCache getCache() {
		if (mCache == null) {
			BitmapLruCache.Builder builder = new BitmapLruCache.Builder(FlickrUploader.getAppContext());
			builder.setMemoryCacheEnabled(true).setMemoryCacheMaxSizeUsingHeapSize();
			mCache = builder.build();
		}
		return mCache;
	}

	public static Map<String, String> getMapProperty(String property) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		String str = sp.getString(property, null);
		if (str != null) {
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(split[0], split[1]);
			}
		}
		return map;
	}

	private static String SHA1(Image image) {
		return SHA1(image.path + "_" + new File(image.path).length());
	}

	public static String getSHA1tag(Image image) {
		return "file:sha1sig=" + SHA1(image).toLowerCase(Locale.US);
	}

	private static String SHA1(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] sha1hash = new byte[40];
			md.update(text.getBytes("utf-8"), 0, text.length());
			sha1hash = md.digest();
			return Utils.convertToHex(sha1hash);
		} catch (Exception e) {
			Log.e(TAG, "Error while hashing", e);
		}
		return null;
	}

	static String convertToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
					buf.append((char) ('0' + halfbyte));
				else
					buf.append((char) ('A' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	private static byte[] createChecksum(String filename) {
		try {
			InputStream fis = new FileInputStream(filename);

			byte[] buffer = new byte[1024];
			MessageDigest complete;
			complete = MessageDigest.getInstance("MD5");
			int numRead;

			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();
			return complete.digest();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	static final Map<String, String> md5Sums = new HashMap<String, String>();

	public static final String getMD5Checksum(Image image) {
		String filename = image.path;
		String md5sum = md5Sums.get(filename);
		if (md5sum == null) {
			md5sum = getMD5Checksum(filename);
			md5Sums.put(filename, md5sum);
		}
		return md5sum;
	}

	// see this How-to for a faster way to convert
	// a byte array to a HEX string
	private static String getMD5Checksum(String filename) {
		byte[] b = createChecksum(filename);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	private static ExecutorService queue = Executors.newSingleThreadExecutor();

	public static ExecutorService getQueue() {
		if (queue == null)
			queue = Executors.newSingleThreadExecutor();
		return queue;
	}

	public static void shutdownQueueNow() {
		if (queue != null) {
			queue.shutdownNow();
			queue = null;
		}
	}

	public static List<Image> loadImages(String filter) {
		Cursor cursor = null;
		List<Image> images = new ArrayList<Image>();
		try {
			String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DISPLAY_NAME,
					Media.SIZE };

			// long oneDayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L;
			// String filter = MediaStore.Images.Media.DATE_TAKEN + " > " + oneDayAgo;
			// String filter = MediaStore.Images.Media._ID + " IN (54820, 56342)";

			String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC, " + MediaStore.Images.Media.DATE_ADDED + " DESC";
			cursor = FlickrUploader.getAppContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, filter, null, orderBy);
			int idColumn = cursor.getColumnIndex(Media._ID);
			int dataColumn = cursor.getColumnIndex(Media.DATA);
			int displayNameColumn = cursor.getColumnIndex(Media.DISPLAY_NAME);
			int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
			int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
			int sizeColumn = cursor.getColumnIndex(Media.SIZE);
			cursor.moveToFirst();
			Log.d(TAG, "filter = " + filter + ", count = " + cursor.getCount());
			while (cursor.isAfterLast() == false) {
				Long date;
				String timestampDateTaken = cursor.getString(dateTakenColumn);
				if (ToolString.isBlank(timestampDateTaken)) {
					String timestampDateAdded = cursor.getString(dateAddedColumn);
					if (ToolString.isBlank(timestampDateAdded)) {
						String data = cursor.getString(dataColumn);
						File file = new File(data);
						date = file.lastModified();
					} else {
						if (timestampDateAdded.trim().length() <= 10) {
							date = Long.valueOf(timestampDateAdded) * 1000L;
						} else {
							date = Long.valueOf(timestampDateAdded);
						}
					}
				} else {
					date = Long.valueOf(timestampDateTaken);
				}

				Image item = new Image();
				item.id = cursor.getInt(idColumn);
				item.path = cursor.getString(dataColumn);
				item.name = cursor.getString(displayNameColumn);
				item.size = cursor.getInt(sizeColumn);
				item.date = date;
				images.add(item);
				// Log.d(TAG, item.imageId + " : " + item.imagePath);
				cursor.moveToNext();
			}
		} catch (Throwable e) {
			Log.e(TAG, e.getMessage(), e);
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return images;
	}

	public static List<Folder> getFolders(List<Image> images) {
		final Multimap<String, Image> photoFiles = LinkedHashMultimap.create();
		for (Image image : images) {
			int lastIndexOf = image.path.lastIndexOf("/");
			if (lastIndexOf > 0) {
				photoFiles.put(image.path.substring(0, lastIndexOf), image);
			}
		}
		List<Folder> folders = new ArrayList<Folder>();
		for (String path : photoFiles.keySet()) {
			folders.add(new Folder(path, photoFiles.get(path)));
		}
		return folders;
	}

	public static boolean canConnect() {
		ConnectivityManager manager = (ConnectivityManager) FlickrUploader.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = manager.getActiveNetworkInfo();

		if (activeNetwork == null || !activeNetwork.isConnected()) {
			return false;
		}

		// if wifi is disabled and the user preference only allows wifi abort
		if (sp.getString(Preferences.UPLOAD_NETWORK, "").equals("wifionly") && activeNetwork.getType() != ConnectivityManager.TYPE_WIFI) {
			return false;
		}

		return true;
	}

	public static interface Callback<E> {
		public void onResult(E result);
	}

	public static <T extends Enum<T>> Map<String, T> getMapProperty(String key, Class<T> class1) {
		Map<String, String> map = getMapProperty(key);
		Map<String, T> mapE = new HashMap<String, T>();
		try {
			for (Entry<String, String> entry : map.entrySet()) {
				mapE.put(entry.getKey(), Enum.valueOf(class1, entry.getValue()));
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return mapE;
	}

	public static Bitmap getBitmap(Image image, int thumbLayoutId) {
		Bitmap bitmap = null;
		int retry = 0;
		while (bitmap == null && retry < 3) {
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPurgeable = true;
				options.inInputShareable = true;
				if (thumbLayoutId == R.layout.photo_grid_thumb) {
					bitmap = MediaStore.Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), image.id, MediaStore.Images.Thumbnails.MICRO_KIND, options);
				} else if (thumbLayoutId == R.layout.folder_grid_thumb) {
					bitmap = MediaStore.Images.Thumbnails.getThumbnail(FlickrUploader.getAppContext().getContentResolver(), image.id, MediaStore.Images.Thumbnails.MINI_KIND, options);
				} else {
					// First decode with inJustDecodeBounds=true to check dimensions
					final BitmapFactory.Options opts = new BitmapFactory.Options();
					opts.inJustDecodeBounds = true;
					opts.inPurgeable = true;
					opts.inInputShareable = true;
					BitmapFactory.decodeFile(image.path, opts);
					// BitmapFactory.decodeFileDescriptor(file., null, opts);

					// Calculate inSampleSize
					opts.inJustDecodeBounds = false;
					opts.inSampleSize = calculateInSampleSize(opts, getScreenWidthPx(), getScreenWidthPx()) + retry;
					bitmap = BitmapFactory.decodeFile(image.path, opts);
				}
			} catch (OutOfMemoryError e) {
				Log.w(TAG, "retry : " + retry + ", " + e.getMessage(), e);
			} catch (Throwable e) {
				Log.e(TAG, e.getMessage(), e);
			} finally {
				retry++;
			}
		}
		return bitmap;
	}

	static Set<String> ignoredFolder;

	static boolean isIgnored(Folder folder) {
		if (ignoredFolder == null) {
			ignoredFolder = new HashSet<String>(getStringList("ignoredFolder"));
		}
		return ignoredFolder.contains(folder.path);
	}

	static void setIgnored(Folder folder, boolean ignored) {
		if (ignoredFolder == null) {
			ignoredFolder = new HashSet<String>(getStringList("ignoredFolder"));
		}
		if (ignored) {
			ignoredFolder.add(folder.path);
		} else {
			ignoredFolder.remove(folder.path);
		}
		Mixpanel.track("Ignore Folder", "name", folder.name);
		setStringList("ignoredFolder", ignoredFolder);
	}

	public static List<String> getStringList(String key) {
		String photosSeen = sp.getString(key, null);
		if (photosSeen != null) {
			return Arrays.asList(photosSeen.split("\\|"));
		}
		return new ArrayList<String>();
	}

	public static void setStringList(String key, Collection<String> ids) {
		setStringProperty(key, Joiner.on('|').join(ids));
	}

	/**
	 * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates the
	 * closest inSampleSize that will result in the final decoded bitmap having a width and height equal to or larger than the requested width and height. This implementation does not ensure a power
	 * of 2 is returned for inSampleSize which can be faster when decoding but results in a larger bitmap which isn't as useful for caching purposes.
	 * 
	 * @param options
	 *            An options object with out* params already populated (run through a decode* method with inJustDecodeBounds==true
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @return The value to be used for inSampleSize
	 */
	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).

			final double totalPixels = width * height;

			// Anything more than 2x the requested pixels we'll sample down
			// further.
			final double totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap && inSampleSize < 24) {
				inSampleSize++;
			}
		}
		return inSampleSize;
		// int scale = 1;
		// if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
		// scale = (int) Math.pow(2, (int) Math.round(Math.log(options.outHeight / (double) Math.max(options.outHeight, options.outWidth)) / Math.log(0.5)));
		// }
		// return scale;
	}

	public static <T extends Enum<T>> void setEnumMapProperty(String property, Map<String, T> mapE) {
		Map<String, String> map = new HashMap<String, String>();
		for (Entry<String, T> entry : mapE.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		setMapProperty(property, map);
	}

	public static String getString(int stringId, Object... objects) {
		return FlickrUploader.getAppContext().getResources().getString(stringId, objects);
	}

	public static String getInstantAlbumId() {
		String instantCustomAlbumId = getStringProperty(STR.instantCustomAlbumId);
		if (instantCustomAlbumId != null) {
			return instantCustomAlbumId;
		} else {
			return getStringProperty(STR.instantAlbumId);
		}
	}

	public static String getInstantAlbumTitle() {
		String instantCustomAlbumId = getStringProperty(STR.instantCustomAlbumId);
		if (instantCustomAlbumId != null) {
			return getStringProperty(STR.instantCustomAlbumTitle);
		} else {
			return STR.instantUpload;
		}
	}
}
