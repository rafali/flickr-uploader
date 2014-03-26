package com.rafali.flickruploader.enums;

import java.util.Locale;

public enum PRIVACY {
	PRIVATE, FRIENDS, FAMILY, FRIENDS_FAMILY("Friends and family"), PUBLIC;

	private String simpleName;

	private PRIVACY() {
	}

	private PRIVACY(String simpleName) {
		this.simpleName = simpleName;
	}

	public String getSimpleName() {
		if (simpleName != null) {
			return simpleName;
		}
		return toString().substring(0, 1).toUpperCase(Locale.US) + toString().substring(1).toLowerCase(Locale.US);
	}
}