package com.rafali.flickruploader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rafali.common.STR;
import com.rafali.common.ToolString;

public class MailHandlerServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(MailHandlerServlet.class.getPackage().getName());

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}

	static final String premiumSkuCoupon50 = "premium.2.5";
	static final String premiumSkuCoupon75 = "premium.1.25";
	static final String premiumSku = "premium.5";

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		logger.debug(req.toString());
		try {
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			MimeMessage message = new MimeMessage(session, req.getInputStream());
			print(message);
			Multipart mp = (Multipart) message.getContent();
			logger.debug("############## count:" + mp.getCount());
			String email = "not_found_email";
			String content = null;
			if (FlickrRemoteApi.adminEmail.equals(message.getSender().toString())) {
				List<BodyPart> bodyparts = new ArrayList<BodyPart>();
				for (int i = 0; i < mp.getCount(); i++) {
					MimeBodyPart bodyPart = (MimeBodyPart) mp.getBodyPart(i);
					bodyparts.add(bodyPart);
					if (bodyPart.getContent() instanceof MimeMultipart) {
						MimeMultipart mimeMultipart = (MimeMultipart) bodyPart.getContent();
						for (int j = 0; j < mimeMultipart.getCount(); j++) {
							bodyparts.add(mimeMultipart.getBodyPart(j));
						}
					}
				}
				logger.debug("bodyparts : " + bodyparts.size() + " : " + bodyparts);
				for (BodyPart bodyPart : bodyparts) {
					if (bodyPart.getContentType().contains("text/plain") && bodyPart.getContent() instanceof CharSequence) {
						Pattern fromPattern = Pattern.compile(".*From:.*<(" + ToolString.REGEX_EMAIL_INSIDE + ")>.*");
						Matcher matcher = fromPattern.matcher((CharSequence) bodyPart.getContent());
						boolean matchFound = matcher.find();
						if (matchFound) {
							email = matcher.group(1).toLowerCase();
							logger.debug("processing : " + email);
							logger.debug("content : " + content);
							String recipient = message.getAllRecipients()[0].toString();
							if (recipient.contains("coupon100@")) {
								setPremium(email, true, false);
								content = "Awesome! I've just upgraded your account (" + email + ") to Premium.\n";
							} else if (recipient.contains("coupon50@")) {
								setDiscount(email, premiumSkuCoupon50);
								content = "Awesome! I've just applied a 50% discount to your account (" + email + ").\n";
							} else if (recipient.contains("coupon75@")) {
								setDiscount(email, premiumSkuCoupon75);
								content = "Awesome! I've just applied a 75% discount to your account (" + email + ").\n";
							}

							if (content != null) {
								content += "To refresh your status go to Preferences > Advanced Preferences and click on ‘Check Premium Status’.";
								content += "\n\nMaxime\n\n";
								content += bodyPart.getContent();
							}
							break;
						}
					}
				}
			} else {
				content = message.getSender() + " is not allowed";
			}

			if (content != null) {
				ToolMail.sendEmailNow(email, message.getSubject().replaceFirst("Fwd:", "Re:"), content.replaceAll("\n", "<br/>\n"), STR.supportEmail, STR.supportEmail);
			} else {
				ToolMail.sendEmailNow(STR.supportEmail, "[failed] - " + message.getSubject(), content, STR.supportEmail);
			}
		} catch (Exception e) {
			logger.error(ToolString.stack2string(e));
			ToolMail.sendEmailNow(STR.supportEmail, "[error] exception while processing mail", req.toString(), STR.supportEmail);
		}
	}

	static void setPremium(String email, boolean premium, boolean purchased) {
		logger.debug("email:" + email + ", premium:" + premium);
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			javax.jdo.Query query = pm.newQuery(Coupon.class);
			query.setFilter("email == :param");
			@SuppressWarnings("unchecked")
			List<Coupon> results = (List<Coupon>) query.execute(email);
			if (results.isEmpty()) {
				logger.debug("no coupon with email = " + email + ", creating one");
				Coupon coupon = new Coupon(email);
				coupon.setPremium(premium);
				coupon.setPurchased(purchased);
				pm.makePersistent(coupon);
			} else {
				for (Coupon coupon : results) {
					coupon.setPremium(premium);
					coupon.setPurchased(purchased);
					coupon.setDateUpdated(new Date());
					logger.debug("updated : " + coupon);
				}
			}
		} finally {
			pm.close();
		}
		updateUser(email, premium);
	}

	static void updateUser(String email, boolean premium) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			javax.jdo.Query query = pm.newQuery(AppInstall.class);
			query.setFilter("emails == :param");
			@SuppressWarnings("unchecked")
			List<AppInstall> results = (List<AppInstall>) query.execute(email);
			if (results.isEmpty()) {
				logger.debug("no device with email = " + email);
			} else {
				for (AppInstall appInstall : results) {
					appInstall.setPremium(premium);
					logger.debug("updated : " + appInstall);
				}
			}
		} finally {
			pm.close();
		}
	}

	static void setDiscount(String email, String sku) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			javax.jdo.Query query = pm.newQuery(Coupon.class);
			query.setFilter("email == :param");
			@SuppressWarnings("unchecked")
			List<Coupon> results = (List<Coupon>) query.execute(email);
			if (results.isEmpty()) {
				logger.debug("no coupon with email = " + email + ", creating one");
				Coupon coupon = new Coupon(email);
				coupon.setSku(sku);
				pm.makePersistent(coupon);
			} else {
				for (Coupon coupon : results) {
					coupon.setSku(sku);
					logger.debug("updated : " + coupon);
				}
			}
		} finally {
			pm.close();
		}
		updateUserCoupon(email, sku);
	}

	static void updateUserCoupon(String email, String customSku) {
		PersistenceManager pm = PMF.get().getPersistenceManager();
		try {
			javax.jdo.Query query = pm.newQuery(AppInstall.class);
			query.setFilter("emails == :param");
			@SuppressWarnings("unchecked")
			List<AppInstall> results = (List<AppInstall>) query.execute(email);
			if (results.isEmpty()) {
				logger.debug("no device with email = " + email);
			} else {
				for (AppInstall appInstall : results) {
					appInstall.setCustomSku(customSku);
					logger.debug("updated : " + appInstall);
				}
			}
		} finally {
			pm.close();
		}
	}

	private void print(MimeMessage message) throws MessagingException, IOException {
		logger.debug("subject:" + message.getSubject());
		logger.debug("description:" + message.getDescription());
		logger.debug("contentType:" + message.getContentType());
		logger.debug("sender:" + message.getSender());
		logger.debug("from:" + Arrays.toString(message.getFrom()));
		logger.debug("recipient:" + Arrays.toString(message.getAllRecipients()));
		// logger.debug("description:"+message.);
		// Enumeration allHeaderLines = message.getAllHeaderLines();
		// while (allHeaderLines.hasMoreElements()) {
		// logger.debug("allHeaderLines:"+allHeaderLines.nextElement());
		// }
		logger.debug("content:" + message.getContent());
		logger.debug("content:" + message.getContent().getClass());
		Multipart mp = (Multipart) message.getContent();
		logger.debug("count:" + mp.getCount());
		for (int i = 0; i < mp.getCount(); i++) {
			MimeBodyPart bodyPart = (MimeBodyPart) mp.getBodyPart(i);
			logger.debug("bodyPart content type:" + bodyPart.getContentType());
			logger.debug("bodyPart content:" + bodyPart.getContent().getClass());
			logger.debug("bodyPart content:" + bodyPart.getContent());
			if (bodyPart.getContent() instanceof MimeMultipart) {
				MimeMultipart mimeMultipart = (MimeMultipart) bodyPart.getContent();
				for (int j = 0; j < mimeMultipart.getCount(); j++) {
					BodyPart part = mimeMultipart.getBodyPart(j);
					logger.debug(i + " - bodyPart content type:" + part.getContentType());
					logger.debug(i + " - bodyPart content:" + part.getContent().getClass());
					logger.debug(i + " - bodyPart content:" + part.getContent());
				}
			}
		}
	}
}
