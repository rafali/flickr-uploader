package com.rafali.flickruploader;

import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rafali.common.AndroidRpcInterface;
import com.rafali.common.STR;
import com.rafali.common.ToolString;

public class AndroidRpcImpl implements AndroidRpcInterface {

	private static final Logger logger = LoggerFactory.getLogger(AndroidRpcImpl.class.getPackage().getName());

	@SuppressWarnings("unchecked")
	@Deprecated
	public Object[] checkPremium(List<String> emails) {
		boolean premium = false;
		String sku = null;
		boolean purchased = false;
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(Coupon.class);
			query.setFilter(":param.contains(email)");
			List<Coupon> result = (List<Coupon>) query.execute(emails);
			for (Coupon coupon : result) {
				premium = coupon.isPremium();
				sku = coupon.getSku();
				purchased = coupon.getPurchased();
			}
			logger.debug(emails + " : " + result);
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
		logger.debug("premium:" + premium + ", sku:" + sku);
		return new Object[] { premium, sku, purchased };
	}

	static Date releaseDate = new Date(1395129600000L);

	@SuppressWarnings("unchecked")
	@Override
	public Object[] checkPremiumStatus(AndroidDevice androidDevice) {
		boolean premium = false;
		String sku = null;
		boolean purchased = false;
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(Coupon.class);
			query.setFilter(":param.contains(email)");
			List<Coupon> result = (List<Coupon>) query.execute(androidDevice.getEmails());
			for (Coupon coupon : result) {
				if (coupon.getPurchased() || coupon.getDateCreation().after(releaseDate)) {
					premium = coupon.isPremium();
					sku = coupon.getSku();
					purchased = coupon.getPurchased();
				} else {
					logger.info("cannot use old coupon here");
				}
			}
			logger.debug(androidDevice.getEmails() + " : " + result);
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
		logger.debug("premium:" + premium + ", sku:" + sku);
		return new Object[] { premium, sku, purchased };
	}

	@Override
	public void sendEmail(String recipient, String subject, String bodyHtml, String fromAddress) {
		logger.debug("sendMail " + recipient + ", " + subject);
		ToolMail.sendEmailNow(recipient, subject, bodyHtml, fromAddress);
	}

	@Deprecated
	public void setPremium(boolean premium, List<String> emails) {
		logger.warn("setPremium deprecated call : " + emails + " : premium=" + premium);
	}

	@Override
	public void setPremium(boolean premium, boolean purchased, List<String> emails) {
		logger.debug(emails + " : premium=" + premium);
		try {
			for (String email : emails) {
				MailHandlerServlet.setPremium(email, premium, purchased);
			}
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		}
	}

	@Deprecated
	public void createOrUpdate(AndroidDevice androidDevice) {
		ensureInstall(androidDevice);
	}

	@SuppressWarnings("unchecked")
	@Override
	public AppInstall ensureInstall(AndroidDevice androidDevice) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			AppInstall appInstall;
			Query query = pm.newQuery(AppInstall.class);
			query.setFilter("deviceId == :param");
			List<AppInstall> result = (List<AppInstall>) query.execute(androidDevice.getId());
			if (result.isEmpty()) {
				logger.debug("New install : " + androidDevice);
				String email = androidDevice.getEmails().isEmpty() ? null : androidDevice.getEmails().iterator().next();
				sendEmail(STR.supportEmail, "[FlickrUploader] New install - " + androidDevice.getCountryCode() + " - " + androidDevice.getLanguage() + " - " + androidDevice.getAppVersion() + " - "
						+ email, androidDevice.getEmails() + " - " + androidDevice.getAndroidVersion() + " - " + androidDevice.getAppVersion(), STR.supportEmail);
				appInstall = new AppInstall(androidDevice.getId(), androidDevice, androidDevice.getEmails());
				pm.makePersistent(appInstall);

			} else {
				logger.debug("Updating install : " + androidDevice);
				appInstall = result.get(0);
				appInstall.setAndroidDevice(androidDevice);
			}
			return pm.detachCopy(appInstall);
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
		return null;
	}

	@Override
	public Boolean confirmPaypalPayment(String paypalResultJson) {
		// TODO paypal actual API check
		return true;
	}
}
