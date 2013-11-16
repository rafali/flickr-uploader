package com.rafali.common;

import java.util.List;

import com.rafali.flickruploader.AndroidDevice;

public interface AndroidRpcInterface {
	Object[] checkPremium(List<String> emails);

	void sendEmail(String recipient, String subject, String bodyHtml, String fromAddress);

	void setPremium(boolean premium, List<String> emails);

	void createOrUpdate(AndroidDevice androidDevice);

	void saveFlickrData(AndroidDevice androidDevice, String id, String username, String oauthToken, String oauthTokenSecret);
}
