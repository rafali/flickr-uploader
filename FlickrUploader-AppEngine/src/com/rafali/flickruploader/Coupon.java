package com.rafali.flickruploader;

import java.io.Serializable;
import java.util.Date;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class Coupon implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum FIELD {
		email, premium, dateCreation, sku
	}

	@PrimaryKey
	@Persistent
	private String email;

	@Persistent
	private Boolean premium;

	@Persistent
	private String sku;

	@Persistent
	private Date dateCreation;

	protected Coupon() {
	}

	protected Coupon(String email) {
		this.email = email;
		this.dateCreation = new Date();
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@Override
	public String toString() {
		return email + "-" + dateCreation + " : premium=" + premium + " : sku=" + getSku();
	}

	public void setDateCreation(Date dateCreation) {
		this.dateCreation = dateCreation;
	}

	public Date getDateCreation() {
		return this.dateCreation;
	}

	public boolean isPremium() {
		if (premium != null)
			return premium;
		return false;
	}

	public void setPremium(boolean premium) {
		this.premium = premium;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}
}