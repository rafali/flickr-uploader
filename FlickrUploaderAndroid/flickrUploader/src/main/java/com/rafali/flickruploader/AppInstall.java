package com.rafali.flickruploader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.rafali.flickruploader.AndroidDevice;


@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class AppInstall implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum FIELD {
		deviceId, androidDevice, emails, ownerIds, dateCreation
	}

	@PrimaryKey
	@Persistent
	private String deviceId;

	@Persistent(serialized = "true", defaultFetchGroup = "true")
	private AndroidDevice androidDevice;

	@Persistent
	private List<String> emails;

	@Persistent
	private List<String> ownerIds;

	@Persistent
	private Boolean premium;

	@Persistent
	private Date dateCreation;

	@Persistent
	private String customSku;

	@Persistent
	private String flickrUserId;

	@Persistent
	private String flickrUserName;

	@Persistent
	private String flickrToken;

	@Persistent
	private String flickrTokenSecret;

	public AppInstall(String deviceId, AndroidDevice androidDevice, Collection<String> emails) {
		this.deviceId = deviceId;
		this.androidDevice = androidDevice;
		this.emails = emails == null ? new ArrayList<String>() : new ArrayList<String>(new HashSet<String>(emails));
		this.dateCreation = new Date();
	}

	public void setAndroidDevice(AndroidDevice androidDevice) {
		this.androidDevice = androidDevice;
	}

	public AndroidDevice getAndroidDevice() {
		return androidDevice;
	}

	public List<String> getEmails() {
		return emails;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public List<String> getOwnerIds() {
		return Collections.unmodifiableList(ownerIds == null ? new ArrayList<String>() : ownerIds);
	}

	@Override
	public String toString() {
		return androidDevice + "-" + dateCreation + " : " + ownerIds + " : " + emails + ", premium:" + premium + ", sku:" + customSku;
	}

	public void setDateCreation(Date dateCreation) {
		this.dateCreation = dateCreation;
	}

	public Date getDateCreation() {
		return this.dateCreation;
	}

	public void addOwnerId(String userId) {
		if (ownerIds == null)
			ownerIds = new ArrayList<String>();
		if (!ownerIds.contains(userId))
			ownerIds.add(userId);
	}

	public boolean isPremium() {
		if (premium == null)
			return false;
		return premium;
	}

	public void setPremium(boolean premium) {
		this.premium = premium;
	}

	@Override
	public int hashCode() {
		return deviceId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AppInstall) {
			return deviceId.equals(((AppInstall) obj).deviceId);
		}
		return false;
	}

	public String getCustomSku() {
		return customSku;
	}

	public void setCustomSku(String customSku) {
		this.customSku = customSku;
	}

	public String getFlickrUserId() {
		return flickrUserId;
	}

	public void setFlickrUserId(String flickrUserId) {
		this.flickrUserId = flickrUserId;
	}

	public String getFlickrUserName() {
		return flickrUserName;
	}

	public void setFlickrUserName(String flickrUserName) {
		this.flickrUserName = flickrUserName;
	}

	public String getFlickrToken() {
		return flickrToken;
	}

	public void setFlickrToken(String flickrToken) {
		this.flickrToken = flickrToken;
	}

	public String getFlickrTokenSecret() {
		return flickrTokenSecret;
	}

	public void setFlickrTokenSecret(String flickrTokenSecret) {
		this.flickrTokenSecret = flickrTokenSecret;
	}
}