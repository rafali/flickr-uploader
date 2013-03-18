package com.rafali.flickruploader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Folder {
	public Folder(String path, Collection<Image> images) {
		this.images = new ArrayList<Image>(images);
		this.path = path;
		this.name = new File(path).getName();
		this.size = images.size();
	}
	public final int size;
	public final List<Image> images;
	public final String path;
	public final String name;
}
