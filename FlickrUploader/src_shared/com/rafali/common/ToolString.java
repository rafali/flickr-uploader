package com.rafali.flickruploader;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolString {

	public static final String REGEX_EMAIL_INSIDE = "[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]@[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]\\.[a-zA-Z][a-zA-Z\\.]*[a-zA-Z]";
	public static final String REGEX_EMAIL = "^" + REGEX_EMAIL_INSIDE + "$";
	// public static final String REGEX_PWD = "^[a-zA-Z0-9]([a-zA-Z0-9@#$%^&+=]{5,20})";
	public static final String REGEX_PWD = "^\\S{6,}";
	public static final String REGEX_URL = "(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w\\.-]*)*\\/?";

	public static String replaceLast(String text, String regex, String replacement) {
		return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", replacement);
	}

	public static boolean isNotBlank(String str) {
		return !isBlank(str);
	}

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

	public static boolean isBlank(String str) {
		if (str == null)
			return true;
		return str.trim().length() == 0;
	}

	public static boolean isBlank(CharSequence str) {
		if (str == null)
			return true;
		return str.length() == 0;
	}

	// return true if oldStr = " " and newStr = null
	public static boolean areEqual(String oldStr, String newStr) {
		if (isNotBlank(oldStr)) {
			return oldStr.equals(newStr);
		} else {
			if (isBlank(newStr))
				return true;
			else
				return false;
		}
	}

	public static String formatDuration(long duration) {
		StringBuffer strb = new StringBuffer();
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

	public static String nullToEmpty(String str) {
		if (str == null)
			return "";
		return str;
	}

	public static String ellipsis(String str, int max) {
		if (str == null || str.length() <= max || max <= 3)
			return str;
		return str.substring(0, max - 3) + "...";
	}

	public static String trimExtension(String filename) {
		if (filename != null) {
			int lastIndex = filename.lastIndexOf(".");
			if (lastIndex > 0) {
				int extensionSize = filename.length() - 1 - lastIndex;
				if (extensionSize >= 2 && extensionSize <= 4) {
					return filename.substring(0, lastIndex);
				}
			}
		}
		return filename;
	}

	public static String[] parseFullAppId(String fullAppId) {
		String tmp = fullAppId.substring(fullAppId.indexOf("-") + 1);
		String app = tmp.substring(0, tmp.indexOf("-"));
		String appId = tmp.substring(tmp.indexOf("-") + 1);
		return new String[] { app, appId };
	}

	public static String sanitizeFileName(String filename) {
		return filename.replaceAll("[^\\w]", "_");
	}

	public static String truncate(String str, int maxLength) {
		return new String(str.substring(0, Math.min(str.length(), maxLength)));
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

	public static Boolean hasElementsInCommon(Collection<String> collection, List<String> ids) {
		Boolean inCommon = false;
		for (String string : ids) {
			inCommon = collection.contains(string);
			if (inCommon)
				return inCommon;
		}
		return inCommon;
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
	
	public static String readableFileSize(long size) {
	    if(size <= 0) return "0";
	    final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
	    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
	    return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
}
