package com.rafali.flickruploader;

import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rafali.common.AndroidRpcInterface;
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

	static String admin = "flickruploader@rafali.com";

	@SuppressWarnings("unchecked")
	@Override
	public void createOrUpdate(AndroidDevice androidDevice) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			Query query = pm.newQuery(AppInstall.class);
			query.setFilter("deviceId == :param");
			List<AppInstall> result = (List<AppInstall>) query.execute(androidDevice.getId());
			if (result.isEmpty()) {
				String email = androidDevice.getEmails().isEmpty() ? null : androidDevice.getEmails().iterator().next();
				sendEmail(admin, "[FlickrUploader] New install - " + androidDevice.getCountryCode() + " - " + androidDevice.getLanguage() + " - " + email, androidDevice.getEmails() + " - "
						+ androidDevice.getAndroidVersion() + " - " + androidDevice.getAppVersion(), admin);
				AppInstall appInstall = new AppInstall(androidDevice.getId(), androidDevice, androidDevice.getEmails());
				pm.makePersistent(appInstall);
			} else {
				result.get(0).setAndroidDevice(androidDevice);
			}
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
		} finally {
			pm.close();
		}
	}

}
