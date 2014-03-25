package com.rafali.flickruploader.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Folder {
	public Folder(String path) {
		this.path = path;
		this.name = new File(path).getName();
	}
	public Folder(String path, Collection<Media> images) {
		this(path);
		this.medias = new ArrayList<Media>(images);
		this.size = images.size();
	}
	public int size;
	public List<Media> medias;
	public final String path;
	public final String name;
}
