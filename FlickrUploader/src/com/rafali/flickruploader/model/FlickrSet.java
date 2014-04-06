package com.rafali.flickruploader.model;

import se.emilsjolander.sprinkles.Model;
import se.emilsjolander.sprinkles.annotations.Column;
import se.emilsjolander.sprinkles.annotations.PrimaryKey;
import se.emilsjolander.sprinkles.annotations.Table;

@Table("FlickrSet")
public class FlickrSet extends Model {

	@PrimaryKey
	@Column("id")
	private String id;

	@Column("name")
	private String name;

	@Column("size")
	private int size;

	public FlickrSet() {
	}

	public FlickrSet(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof FlickrSet) {
			return id.equals(((FlickrSet) o).id);
		}
		return super.equals(o);
	}

	@Override
	public String toString() {
		return id + " : " + name;
	}
}
