package com.rafali.flickruploader.model;

import se.emilsjolander.sprinkles.Model;
import se.emilsjolander.sprinkles.annotations.Cacheable;
import se.emilsjolander.sprinkles.annotations.Column;
import se.emilsjolander.sprinkles.annotations.PrimaryKey;
import se.emilsjolander.sprinkles.annotations.Table;

import com.rafali.common.ToolString;

@Table("Folder")
@Cacheable
public class Folder extends Model {

	@PrimaryKey
	@Column("id")
	private int id;

	@Column("path")
	private String path;

	@Column("flickrSetTitle")
	private String flickrSetTitle;

	private int size;

	private Media media;

	public Folder() {
	}

	@Override
	public String toString() {
		return path + ":" + flickrSetTitle + ":" + size;
	}

	public Folder(String path) {
		this.id = path.hashCode();
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	public String getName() {
		return ToolString.getFileName(this.path);
	}

	public int getSize() {
		return this.size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public Media getMedia() {
		return media;
	}

	public void setMedia(Media media) {
		this.media = media;
	}

	public String getFlickrSetTitle() {
		return flickrSetTitle;
	}

	public void setFlickrSetTitle(String flickrSetTitle) {
		if (ToolString.isBlank(flickrSetTitle)) {
			this.flickrSetTitle = null;
		} else {
			this.flickrSetTitle = flickrSetTitle;
		}
	}

	public boolean isAutoUploaded() {
		return ToolString.isNotBlank(flickrSetTitle);
	}

}
