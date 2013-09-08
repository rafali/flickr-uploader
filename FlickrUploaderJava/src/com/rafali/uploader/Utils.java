package com.rafali.uploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import com.rafali.uploader.FlickrApi.PRIVACY;
import com.rafali.flickruploader.Media;
import com.rafali.flickruploader.STR;

public class Utils {
	private static final String UPLOAD_PRIVACY = "UPLOAD_PRIVACY";
	private static final String TAG = Utils.class.getSimpleName();

	public static String getString(String key) {
		ResourceBundle config = ResourceBundle.getBundle("config");
		return config.getString(key);
	}

	static final Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
	
	public static String getStringProperty(String key) {
		Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
		return prefs.get(key, null);
	}

	public static void setMapProperty(String property, Map<String, String> map) {
		try {
			Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
			if (map == null || map.isEmpty()) {
				prefs.put(property, null);
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
				prefs.put(property, strb.toString());
			}
			prefs.flush();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public static Map<String, String> getMapProperty(String property) {
		Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
		Map<String, String> map = new LinkedHashMap<String, String>();
		String str = prefs.get(property, null);
		if (str != null) {
			String[] entries = str.split("\\|;\\|");
			for (String entry : entries) {
				String[] split = entry.split("\\|=\\|");
				map.put(split[0], split[1]);
			}
		}
		return map;
	}
	
	public static <T extends Enum<T>> Map<String, T> getMapProperty(String key, Class<T> class1) {
		Map<String, String> map = getMapProperty(key);
		Map<String, T> mapE = new HashMap<String, T>();
		try {
			for (Entry<String, String> entry : map.entrySet()) {
				mapE.put(entry.getKey(), Enum.valueOf(class1, entry.getValue()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mapE;
	}
	
	public static <T extends Enum<T>> void setEnumMapProperty(String property, Map<String, T> mapE) {
		Map<String, String> map = new HashMap<String, String>();
		for (Entry<String, T> entry : mapE.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		setMapProperty(property, map);
	}
	
	private static String SHA1(Media image) {
		return SHA1(image.path + "_" + new File(image.path).length());
	}

	public static String getSHA1tag(Media image) {
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
	
	public static String getInstantAlbumId() {
		String instantCustomAlbumId = getStringProperty(STR.instantCustomAlbumId);
		if (instantCustomAlbumId != null) {
			return instantCustomAlbumId;
		} else {
			return getStringProperty(STR.instantAlbumId);
		}
	}
	
	public static void setStringProperty(String property, String value) {
		try {
			Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
			prefs.put(property, value);
			prefs.flush();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public static PRIVACY getDefaultPrivacy() {
		Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
		return PRIVACY.valueOf(prefs.get(UPLOAD_PRIVACY, PRIVACY.PRIVATE.toString()));
	}
	
	public static void clearProperty(String property) {
		try {
			Preferences prefs = Preferences.userRoot().node(Utils.class.getName());
			prefs.remove(property);
			prefs.flush();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	static final Map<String, String> md5Sums = new HashMap<String, String>();

	public static final String getMD5Checksum(Media image) {
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

	public static byte[] createChecksum(String filename) {
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

	public static List<Media> loadImages(Object object) {
		// TODO Auto-generated method stub
		return null;
	}

}
