package com.rafali.flickruploader;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;

public class ToolString {

	public static final String REGEX_EMAIL_INSIDE = "[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]@[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]\\.[a-zA-Z][a-zA-Z\\.]*[a-zA-Z]";
	public static final String REGEX_EMAIL = "^" + REGEX_EMAIL_INSIDE + "$";
	// public static final String REGEX_PWD = "^[a-zA-Z0-9]([a-zA-Z0-9@#$%^&+=]{5,20})";
	public static final String REGEX_PWD = "^\\S{6,}";
	public static final String REGEX_URL = "(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w\\.-]*)*\\/?";

	public static boolean isNotBlank(CharSequence str) {
		return !isBlank(str);
	}

	public static String replaceAccents(String source) {
		String chaine = source.replaceAll("è|é|ê|ë", "e");
		chaine = chaine.replaceAll("û|ü|ù", "u");
		chaine = chaine.replaceAll("î|ï", "i");
		chaine = chaine.replaceAll("à|ä|â", "a");
		chaine = chaine.replaceAll("ô|ö", "o");
		chaine = chaine.replaceAll("ç", "c");
		return chaine;
	}

	public static boolean isBlank(CharSequence str) {
		if (str == null)
			return true;
		return str.length() == 0;
	}

	public static String nullToEmpty(String str) {
		if (str == null)
			return "";
		return str;
	}


	public static String sanitizeFileName(String filename) {
		return filename.replaceAll("[^\\w]", "_");
	}

	public static String substringTo(String str, char character) {
		if (str != null) {
			int indexOf = str.indexOf(character);
			return str.substring(0, Math.max(0, indexOf < 0 ? str.length() : indexOf));
		} else
			return null;
	}

	public static String toSimpleName(Class<?> javaClass) {
		String name = javaClass.getName();
		return name.substring(name.lastIndexOf(".") + 1, name.length());
	}

	public static String emailToUsername(String email) {
		if (isNotBlank(email) && email.matches(REGEX_EMAIL)) {
			return email.substring(0, email.indexOf("@")).replaceAll("[^A-Za-z0-9]", " ");
		}
		return email;
	}

	public static String toUrl(String url, Map<String, String> params) {
		if (params != null && !params.isEmpty())
			return url + (url.contains("?") ? "&" : "?") + Joiner.on("&").withKeyValueSeparator("=").join(params);
		else
			return url;
	}


	public static String formatDuration(long duration) {
		StringBuffer strb = new StringBuffer("+");
		long diffInSeconds = duration / 1000L;
		long sec, min, hours, days = 0;
		sec = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
		min = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
		hours = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
		days = (diffInSeconds = (diffInSeconds / 24));
		if (days > 0) {
			strb.append(days + "d");
			if (hours > 0) {
				strb.append(" " + hours + "h");
			}
		} else if (hours > 0) {
			strb.append(hours + "h");
			if (min > 0) {
				strb.append(" " + min + "m");
			}
		} else if (min > 0) {
			strb.append(min + "m");
			if (sec > 0) {
				strb.append(" " + sec + "s");
			}
		} else {
			strb.append(sec + "s");
		}
		return strb.toString();
	}

	public static Map<String, String> getQueryMap(String query) {
		String[] params = query.split("&");
		Map<String, String> map = new HashMap<String, String>();
		for (String param : params) {
			if (param.contains("=")) {
				String[] split = param.split("=");
				map.put(split[0], split[1]);
			}
		}
		return map;
	}

	final protected static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
