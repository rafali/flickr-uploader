package com.rafali.flickruploader.enums;

public enum CAN_UPLOAD {
	ok, network, wifi, charging, manually, no_flickr_login("Flickr authentication");

	private String description;

	private CAN_UPLOAD() {
	}

	private CAN_UPLOAD(String description) {
		this.description = description;
	}

	public String getDescription() {
		if (description == null)
			return toString();
		return description;
	}
}