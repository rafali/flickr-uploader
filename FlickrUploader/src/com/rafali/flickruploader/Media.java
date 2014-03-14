package com.rafali.flickruploader;

import com.rafali.flickruploader.Utils.MediaType;

public class Media {

	public int id;

	public String path;

	public String name;

	public MediaType mediaType;

	public int size;

	public long date;

	@Override
	public String toString() {
		return mediaType + " - " + id + " - " + path;
	}

	@Override
	public int hashCode() {
		if (path != null)
			return path.hashCode();
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Media) {
			return ((Media) o).path.equals(path);
		}
		return super.equals(o);
	}

	public String getFolderPath() {
		int lastIndexOf = path.lastIndexOf("/");
		if (lastIndexOf > 0) {
			return path.substring(0, lastIndexOf);
		}
		return "/";
	}

	public String getFolderName() {
		String folderPath = getFolderPath();
		int lastIndexOf = folderPath.lastIndexOf("/");
		if (lastIndexOf > 0) {
			return folderPath.substring(lastIndexOf);
		}
		return "";
	}

}
