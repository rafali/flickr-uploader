package com.rafali.flickruploader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.common.base.Joiner;
import com.rafali.common.Base64UrlSafe;

public class HttpClientGAE {
	public static final String POSTPROXY_PHP = "http://log.pictarine.com/postproxy.php";
	private static final Logger logger = LoggerFactory.getLogger(HttpClientGAE.class.getPackage().getName());
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private HttpClientGAE() {

	}

	public static String getResponseDELETE(String url, Map<String, String> params, Map<String, String> headers) {
		int retry = 0;
		while (retry < 3) {
			long start = System.currentTimeMillis();
			try {
				URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
				String urlStr = ToolString.toUrl(url.trim(), params);
				logger.debug("DELETE : " + urlStr);
				HTTPRequest httpRequest = new HTTPRequest(new URL(urlStr), HTTPMethod.DELETE, FetchOptions.Builder.withDeadline(deadline));
				HTTPResponse response = fetcher.fetch(httpRequest);
				return processResponse(response);
			} catch (Throwable e) {
				retry++;
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				} else if (retry < 3) {
					logger.warn("retrying after " + (System.currentTimeMillis() - start) + " and " + retry + " retries\n" + e.getClass() + e.getMessage());
				} else {
					logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
				}
			}
		}
		return null;
	}

	public static String getResponsePUT(String url, Map<String, String> params, String json, Map<String, String> headers) {
		int retry = 0;
		while (retry < 3) {
			long start = System.currentTimeMillis();
			try {
				URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
				String urlStr = ToolString.toUrl(url.trim(), params);
				logger.debug("PUT : " + urlStr);
				HTTPRequest httpRequest = new HTTPRequest(new URL(urlStr), HTTPMethod.PUT, FetchOptions.Builder.withDeadline(deadline));
				if (headers != null) {
					for (String header : headers.keySet()) {
						httpRequest.addHeader(new HTTPHeader(header, headers.get(header)));
					}
				}
				httpRequest.setPayload(json.getBytes());
				HTTPResponse response = fetcher.fetch(httpRequest);
				return processResponse(response);
			} catch (Throwable e) {
				retry++;
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				} else if (retry < 3) {
					logger.warn("retrying after " + (System.currentTimeMillis() - start) + " and " + retry + " retries\n" + e.getClass() + e.getMessage());
				} else {
					logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
				}
			}
		}
		return null;
	}

	public static String getResponseGET(String url) {
		return getResponseGET(url, null, null);
	}

	public static String getResponseGET(String url, Map<String, String> params) {
		return getResponseGET(url, params, null);
	}

	public static String getResponseGET(String url, Map<String, String> params, Map<String, String> headers) {
		int retry = 0;
		while (retry < 3) {
			long start = System.currentTimeMillis();
			try {
				URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
				String urlStr = ToolString.toUrl(url.trim(), params);
				HTTPRequest httpRequest = new HTTPRequest(new URL(urlStr), HTTPMethod.GET, FetchOptions.Builder.withDeadline(deadline));
				if (headers != null) {
					for (String name : headers.keySet()) {
						httpRequest.addHeader(new HTTPHeader(name, headers.get(name)));
					}
				}
				return processResponse(fetcher.fetch(httpRequest));
			} catch (Throwable e) {
				retry++;
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				} else if (retry < 3) {
					logger.warn("retrying after " + (System.currentTimeMillis() - start) + " and " + retry + " retries\n" + e.getClass() + e.getMessage());
				} else {
					logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
				}
			}
		}
		return null;
	}

	public static String processResponse(HTTPResponse response) {
		String content = new String(response.getContent(), UTF8);
		if (response.getResponseCode() >= 400) {
			throw new RuntimeException("HttpError:" + response.getResponseCode() + "\n" + content);
		}
		return content;
	}

	public static String getResponseProxyPOST(URL url) throws MalformedURLException, UnsupportedEncodingException, IOException {
		URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
		String base64payload = "base64url=" + Base64UrlSafe.encodeServer(url.toString());
		String urlStr = url.toString();
		if (urlStr.contains("?")) {
			base64payload = "base64url=" + Base64UrlSafe.encodeServer(urlStr.substring(0, urlStr.indexOf("?")));
			base64payload += "&base64content=" + Base64UrlSafe.encodeServer(urlStr.substring(urlStr.indexOf("?") + 1));
		} else {
			base64payload = "base64url=" + Base64UrlSafe.encodeServer(urlStr);
		}
		HTTPRequest httpRequest = new HTTPRequest(new URL(HttpClientGAE.POSTPROXY_PHP), HTTPMethod.POST, FetchOptions.Builder.withDeadline(30d).doNotValidateCertificate());
		httpRequest.setPayload(base64payload.getBytes(UTF8));
		HTTPResponse response = fetcher.fetch(httpRequest);
		String processResponse = HttpClientGAE.processResponse(response);
		logger.info("proxying " + url + "\nprocessResponse:" + processResponse);
		return processResponse;
	}

	public static String getResponsePOST(String url, Map<String, String> params) {
		return getResponsePOST(url, null, params, null);
	}

	public static double deadline = 50d;

	public static String getResponsePOST(String url, Map<String, String> headers, Map<String, String> params, String content) {
		return getResponsePOST(url, headers, params, content, deadline);
	}

	public static String getResponsePOST(String url, Map<String, String> headers, Map<String, String> params, String content, double timeoutSeconds) {
		int retry = 0;
		Throwable previousException = null;
		while (retry < 3) {
			long start = System.currentTimeMillis();
			try {
				URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
				HTTPRequest httpRequest;
				String urlStr;
				if (content == null) {
					urlStr = url;
				} else {
					urlStr = ToolString.toUrl(url.trim(), params);
				}
				String base64payload = null;
				if (previousException instanceof SocketTimeoutException) {
					base64payload = "base64url=" + Base64UrlSafe.encodeServer(urlStr);
					urlStr = POSTPROXY_PHP;
					if (content != null) {
						base64payload += "&base64content=" + Base64UrlSafe.encodeServer(content);
					}
					logger.info("proxy call : " + urlStr + "\n" + base64payload);
				}
				httpRequest = new HTTPRequest(new URL(urlStr), HTTPMethod.POST, FetchOptions.Builder.withDeadline(timeoutSeconds).doNotValidateCertificate());
				if (base64payload != null) {
					httpRequest.setPayload(base64payload.getBytes(UTF8));
				} else if (content == null) {
					if (params != null) {
						String paramStr = Joiner.on("&").withKeyValueSeparator("=").useForNull("null").join(params);
						httpRequest.setPayload(paramStr.getBytes(UTF8));
						logger.debug(paramStr);
					}
				} else {
					httpRequest.setPayload(content.getBytes(UTF8));
				}
				if (headers != null) {
					for (String header : headers.keySet()) {
						httpRequest.addHeader(new HTTPHeader(header, headers.get(header)));
					}
				}
				HTTPResponse response = fetcher.fetch(httpRequest);
				return processResponse(response);
			} catch (Throwable e) {
				retry++;
				previousException = e;
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				} else if (retry < 3) {
					logger.warn("retrying after " + (System.currentTimeMillis() - start) + " and " + retry + " retries\n" + e.getClass() + " : " + e.getMessage());
				} else {
					logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
				}
			}
		}
		return null;
	}

	// public String getResponsePOST(String url, Map<String, String> params, String json, Map<String, String> headers) {
	// int retry = 0;
	// while (retry < 3) {
	// long start = System.currentTimeMillis();
	// try {
	// URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
	// String urlStr = ToolString.toUrl(url.trim(), params);
	// logger.debug("POST : " + urlStr);
	// HTTPRequest httpRequest = new HTTPRequest(new URL(urlStr), HTTPMethod.POST, FetchOptions.Builder.withDeadline(50d));
	// if (headers != null) {
	// for (String header : headers.keySet()) {
	// httpRequest.addHeader(new HTTPHeader(header, headers.get(header)));
	// }
	// }
	// httpRequest.setPayload(json.getBytes());
	// HTTPResponse response = fetcher.fetch(httpRequest);
	// return processResponse(response);
	// } catch (Throwable e) {
	// retry++;
	// if (e instanceof RuntimeException) {
	// throw (RuntimeException) e;
	// } else if (retry < 3) {
	// logger.warn("retrying after " + (System.currentTimeMillis() - start) + " and " + retry + " retries\n" + e.getClass() + e.getMessage());
	// } else {
	// logger.error(e.getClass() + "\n" + ToolException.stack2string(e));
	// }
	// }
	// }
	// return null;
	// }

	public static Map<String, String> getResponses(Collection<String> urls) {
		Map<String, String> responses = new HashMap<String, String>();
		URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
		Map<String, Future<HTTPResponse>> futures = new HashMap<String, Future<HTTPResponse>>();
		try {
			for (String url : urls) {
				HTTPRequest httpRequest = new HTTPRequest(new URL(url), HTTPMethod.GET, FetchOptions.Builder.withDeadline(deadline));
				Future<HTTPResponse> future = fetcher.fetchAsync(httpRequest);
				futures.put(url, future);
			}
		} catch (MalformedURLException e) {
			logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
		}

		for (String url : urls) {
			Future<HTTPResponse> future = futures.get(url);
			try {
				HTTPResponse httpResponse = future.get();
				responses.put(url, new String(httpResponse.getContent(), UTF8));
			} catch (Exception e) {
				logger.error(e.getClass() + "\n" + ToolString.stack2string(e));
			}
		}

		return responses;
	}

	public static String getResponseProxyGET(URL url) {
		String urlproxy;
		String urlStr = url.toString();
		if (urlStr.contains("?")) {
			urlproxy = "http://log.pictarine.com/getproxy.php?base64url=" + Base64UrlSafe.encodeServer(urlStr.substring(0, urlStr.indexOf("?"))) + "&base64query="
					+ Base64UrlSafe.encodeServer(urlStr.substring(urlStr.indexOf("?") + 1));
		} else {
			urlproxy = "http://log.pictarine.com/getproxy.php?base64url=" + Base64UrlSafe.encodeServer(urlStr);
		}
		String responseGET = getResponseGET(urlproxy);
		logger.info("urlproxy : " + urlproxy + "\nresponseGET : " + responseGET);
		return responseGET;
	}

}
