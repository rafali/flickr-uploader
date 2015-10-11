package com.rafali.flickruploader;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class AndroidCrashReport extends HttpServlet {

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

		ToolMail.sendEmailNow("rafalax@gmail.com", "FLICKR-CRASH - " + versionId, "<pre>" + stackTrace + "\n\n" + strb + "</pre>", "rafalax@gmail.com");
	}

}
