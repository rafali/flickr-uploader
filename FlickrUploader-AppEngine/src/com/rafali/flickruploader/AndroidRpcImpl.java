package com.rafali.flickruploader;

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
	@Override
	public Object[] checkPremium(List<String> emails) {
		boolean premium = false;
		String sku = null;
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(Coupon.class);
			query.setFilter(":param.contains(email)");
			List<Coupon> result = (List<Coupon>) query.execute(emails);
			for (Coupon appInstall : result) {
				if (appInstall.isPremium()) {
					premium = true;
				} else if (appInstall.getSku() != null) {
					sku = appInstall.getSku();
				}
			}
			logger.debug(emails + " : " + result);
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
		logger.debug("premium:" + premium + ", sku:" + sku);
		return new Object[] { premium, sku };
	}

	@Override
	public void sendEmail(String recipient, String subject, String bodyHtml, String fromAddress) {
		logger.debug("sendMail " + recipient + ", " + subject);
		AndroidCrashReport.sendEmailNow(recipient, subject, bodyHtml, fromAddress);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setPremium(boolean premium, List<String> emails) {
		logger.debug(emails + " : premium=" + premium);
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			for (String email : emails) {
				Query query = pm.newQuery(AppInstall.class);
				query.setFilter("emails == :param");
				List<AppInstall> result = (List<AppInstall>) query.execute(email);
				for (AppInstall appInstall : result) {
					appInstall.setPremium(premium);
				}
				pm.flush();
			}
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void createOrUpdate(AndroidDevice androidDevice) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(AppInstall.class);
			query.setFilter("deviceId == :param");
			List<AppInstall> result = (List<AppInstall>) query.execute(androidDevice.getId());
			if (result.isEmpty()) {
				logger.debug("New install : " + androidDevice);
				String email = androidDevice.getEmails().isEmpty() ? null : androidDevice.getEmails().iterator().next();
				sendEmail(STR.supportEmail, "[FlickrUploader] New install - " + androidDevice.getCountryCode() + " - " + androidDevice.getLanguage() + " - " + email, androidDevice.getEmails() + " - "
						+ androidDevice.getAndroidVersion() + " - " + androidDevice.getAppVersion(), STR.supportEmail);
				AppInstall appInstall = new AppInstall(androidDevice.getId(), androidDevice, androidDevice.getEmails());
				pm.makePersistent(appInstall);
			} else {
				logger.debug("Updating install : " + androidDevice);
				result.get(0).setAndroidDevice(androidDevice);
			}
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void saveFlickrData(AndroidDevice androidDevice, String flickrUserId, String flickrUserName, String flickrToken, String flickrTokenSecret) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(AppInstall.class);
			query.setFilter("deviceId == :param");
			AppInstall appInstall;
			List<AppInstall> result = (List<AppInstall>) query.execute(androidDevice.getId());
			if (result.isEmpty()) {
				logger.debug("New install : " + androidDevice);
				String email = androidDevice.getEmails().isEmpty() ? null : androidDevice.getEmails().iterator().next();
				sendEmail(STR.supportEmail, "[FlickrUploader] New install - " + androidDevice.getCountryCode() + " - " + androidDevice.getLanguage() + " - " + email, androidDevice.getEmails() + " - "
						+ androidDevice.getAndroidVersion() + " - " + androidDevice.getAppVersion(), STR.supportEmail);
				appInstall = pm.makePersistent(new AppInstall(androidDevice.getId(), androidDevice, androidDevice.getEmails()));
			} else {
				logger.debug("Updating install : " + androidDevice);
				appInstall = result.get(0);
				appInstall.setAndroidDevice(androidDevice);
			}
			logger.debug("setting flickr data for: " + flickrUserId + ", " + flickrUserName);
			appInstall.setFlickrUserId(flickrUserId);
			appInstall.setFlickrUserName(flickrUserName);
			appInstall.setFlickrToken(flickrToken);
			appInstall.setFlickrTokenSecret(flickrTokenSecret);
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
	}

}
