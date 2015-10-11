package com.rafali.flickruploader;

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

import com.rafali.common.STR;
import com.rafali.common.ToolString;

public class ToolMail {

	static final Logger logger = Logger.getLogger(ToolMail.class.getName());

	public static synchronized void sendEmailNow(String recipient, String subject, String bodyHtml, String fromAddress) {
		sendEmailNow(recipient, subject, bodyHtml, fromAddress, null);
	}

	public static synchronized void sendEmailNow(String recipient, String subject, String bodyHtml, String fromAddress, String bcc) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			Message msg = new MimeMessage(session);

			msg.setFrom(new InternetAddress(STR.robotEmail, "Flickr Uploader Dev"));
			if (fromAddress != null) {
				msg.setReplyTo(new InternetAddress[] { new InternetAddress(fromAddress) });
			}
			msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			if (ToolString.isNotBlank(bcc)) {
				msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
			}
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
