package com.rafali.flickruploader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.rafali.common.STR;

@SuppressWarnings("serial")
public class AndroidCrashReport extends HttpServlet {

	private static final Logger logger = Logger.getLogger(AndroidCrashReport.class.getName());

	private static enum CRASH_PARAM {
		REPORT_ID, APP_VERSION_CODE, APP_VERSION_NAME, PHONE_MODEL, ANDROID_VERSION, BUILD, BRAND, PRODUCT, TOTAL_MEM_SIZE, AVAILABLE_MEM_SIZE, USER_APP_START_DATE, USER_CRASH_DATE, DEVICE_ID, DEVICE_FEATURES, ENVIRONMENT, SETTINGS_SYSTEM, SETTINGS_SECURE, THREAD_DETAILS
	};

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {
		StringBuffer strb = new StringBuffer();

		String stackTrace = request.getParameter("STACK_TRACE");

		String appVersionCode = request.getParameter(CRASH_PARAM.APP_VERSION_CODE.toString());
		String versionId = request.getParameter(CRASH_PARAM.APP_VERSION_NAME.toString()) + "-" + appVersionCode;
		for (CRASH_PARAM param : CRASH_PARAM.values()) {
			strb.append("######## " + param + " : \n");
			strb.append(request.getParameter(param.toString()) + "\n");
		}

		sendEmailNow("rafalax@gmail.com", "FLICKR-CRASH - " + versionId, "<pre>" + stackTrace + "\n\n" + strb + "</pre>", "rafalax@gmail.com");
	}

	static final String robotEmail = "script@rafali.com";

	public static synchronized void sendEmailNow(String recipient, String subject, String bodyHtml, String fromAddress) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			Message msg = new MimeMessage(session);

			msg.setFrom(new InternetAddress(robotEmail, "Robot Flickr Uploader"));
			if (fromAddress != null) {
				msg.setReplyTo(new InternetAddress[] { new InternetAddress(fromAddress) });
			}
			msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			msg.setSubject(subject);

			Multipart mp = new MimeMultipart("alternative");

			// html part
			if (bodyHtml != null) {
				BodyPart htmlPart = new MimeBodyPart();
				htmlPart.setContent(bodyHtml, "text/html;charset=\"utf-8\"");

				mp.addBodyPart(htmlPart);
				// BUG Appengine : can't sent an image embedded in an email
				// http://code.google.com/p/googleappengine/issues/detail?id=965
			}
			msg.setContent(mp);
			logger.info("Mail //" + "subject : " + msg.getSubject() + ", recipient : " + Arrays.toString(msg.getAllRecipients()) + ", from : " + Arrays.toString(msg.getFrom()) + "\n bodyHtml:"
					+ bodyHtml);

			Transport.send(msg);

		} catch (AddressException e) {
			logger.severe("AddressException : " + recipient + "," + subject + "," + fromAddress + "/n" + e);
		} catch (MessagingException e) {
			if (e instanceof NoSuchProviderException) {
				logger.severe("rethrowing NoSuchProviderException : " + recipient + "," + subject + "," + fromAddress + "/n" + e.getMessage());
				throw new RuntimeException(e);
			} else {
				logger.severe("MessagingException : " + recipient + "," + subject + "," + fromAddress + "/n" + e);
			}
		} catch (UnsupportedEncodingException e) {
			logger.severe("UnsupportedEncodingException : " + recipient + "," + subject + "," + fromAddress + "/n" + e);
		}
	}
}
