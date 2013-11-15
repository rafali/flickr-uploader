package com.rafali.flickruploader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class AndroidDevice implements Serializable {
	private static final long serialVersionUID = 6522934875344460973L;

	private String id;
	private String appVersion;
	private boolean appInstalled;
	private String modelInfo;
	private String gcmId;
	private Date dateCreation;
	private String language;
	private List<String> emails;
	private String otherInfos;
	private Integer androidVersion;
	private String countryCode;

	protected AndroidDevice() {
	}

	public AndroidDevice(String id, Collection<String> emails, String language, int androidVersion) {
		this.id = id;
		this.androidVersion = androidVersion;
		this.dateCreation = new Date();
		this.appInstalled = true;
		this.emails = emails == null ? new ArrayList<String>() : new ArrayList<String>(new HashSet<String>(emails));
	}

	public String getId() {
		return id;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

	public String getModelInfo() {
		return modelInfo;
	}

	public void setModelInfo(String modelInfo) {
		this.modelInfo = modelInfo;
	}

	public String getGcmId() {
		return gcmId;
	}

	public String getLanguage() {
		return language;
	}

	public void setGcmId(String gcmId) {
		this.gcmId = gcmId;
	}

	public Date getDateCreation() {
		return dateCreation;
	}

	public boolean isAppInstalled() {
		return appInstalled;
	}

	public void setAppInstalled(boolean appInstalled) {
		this.appInstalled = appInstalled;
	}

	public String getOtherInfos() {
		return otherInfos;
	}

	public void setOtherInfos(String otherInfos) {
		this.otherInfos = otherInfos;
	}

	public List<String> getEmails() {
		return emails;
	}

	public String getInfo(String property) {
		if (otherInfos != null) {
			for (String key_value : otherInfos.split("\n")) {
				if (key_value.contains("=")) {
					int index = key_value.indexOf("=");
					String key = key_value.substring(0, index);
					String value = key_value.substring(index + 1);
					if (property.equals(key)) {
						if (value != null && !value.trim().isEmpty() && !value.equals("null")) {
							return value;
						}
						break;
					}
				}
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return id + " - " + modelInfo + " - " + appVersion + " - installed:" + appInstalled + " - " + gcmId + " - " + dateCreation + " - " + emails;
	}

	public int getAndroidVersion() {
		if (androidVersion == null)
			return -1;
		return androidVersion;
	}

	public void setAndroidVersion(int androidVersion) {
		this.androidVersion = androidVersion;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

}
