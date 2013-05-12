package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Folder {
	public Folder(String path) {
		this.path = path;
		this.name = new File(path).getName();
	}
	public Folder(String path, Collection<Image> images) {
		this(path);
		this.images = new ArrayList<Image>(images);
		this.size = images.size();
	}
	public int size;
	public List<Image> images;
	public final String path;
	public final String name;
}
