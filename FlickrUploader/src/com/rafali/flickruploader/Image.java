package com.rafali.flickruploader;

public class Image {

	public int id;

	public String path;

	public String name;

	public int size;

	public long date;

	@Override
	public String toString() {
		return id + " - " + path;
	}

	@Override
	public int hashCode() {
		if (path != null)
			return path.hashCode();
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Image) {
			return ((Image) o).path.equals(path);
		}
		return super.equals(o);
	}
}
