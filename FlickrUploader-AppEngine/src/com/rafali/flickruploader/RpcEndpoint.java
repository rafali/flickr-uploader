package com.rafali.flickruploader;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;

@Api(name = "rpcendpoint", namespace = @ApiNamespace(ownerDomain = "rafali.com", ownerName = "rafali.com", packagePath = "flickruploader"))
public class RpcEndpoint {

	@ApiMethod(name = "sendMail")
	public void sendMail(@Named("recipient") String recipient, @Named("subject") String subject, @Named("bodyHtml") String bodyHtml, @Named("fromAddress") String fromAddress) {
		System.out.println("sendMail " + recipient + ", " + subject);
		AndroidCrashReport.sendEmailNow(recipient, subject, bodyHtml, fromAddress);
	}

	@ApiMethod(name = "checkPremiumStatus")
	public List<String> checkPremiumStatus(@Named("emails") List<String> emails) {
		System.out.println("checkPremiumStatus " + emails);

		return Arrays.asList("toto", "coco");
	}

}
