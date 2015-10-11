package com.rafali.flickruploader.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLHandshakeException;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncBasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;

import android.os.SystemClock;

import com.google.api.client.util.StreamingContent;
import com.google.common.base.Joiner;
import com.rafali.common.AndroidRpcInterface;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.Config;
import com.rafali.flickruploader.FlickrUploader;

public final class RPC {

	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(RPC.class);
	private static final int DEFAULT_MAX_CONNECTIONS = 10;
	private static final int DEFAULT_SOCKET_TIMEOUT = 50 * 1000;
	private static final int DEFAULT_CONNECTION_TIMEOUT = 10 * 1000;
	private static final int DEFAULT_MAX_RETRIES = 5;
	private static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;
	private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	private static final String ENCODING_GZIP = "gzip";

	// private static int maxConnections = DEFAULT_MAX_CONNECTIONS;
	// private static int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

	private static DefaultHttpClient httpClient;
	private static HttpContext rafaliHttpContext;

	static {
		init();
	}

	private static void init() {
		BasicHttpParams httpParams = new BasicHttpParams();

        StreamingContent.class.getName();

		ConnManagerParams.setTimeout(httpParams, DEFAULT_SOCKET_TIMEOUT);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(DEFAULT_MAX_CONNECTIONS));
		ConnManagerParams.setMaxTotalConnections(httpParams, DEFAULT_MAX_CONNECTIONS);

		HttpConnectionParams.setSoTimeout(httpParams, DEFAULT_SOCKET_TIMEOUT);
		HttpConnectionParams.setConnectionTimeout(httpParams, DEFAULT_CONNECTION_TIMEOUT);
		HttpConnectionParams.setTcpNoDelay(httpParams, true);
		HttpConnectionParams.setSocketBufferSize(httpParams, DEFAULT_SOCKET_BUFFER_SIZE);

		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setUserAgent(httpParams, "rafali-android-rpc gzip");

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);

		rafaliHttpContext = new SyncBasicHttpContext(new BasicHttpContext());
		httpClient = new DefaultHttpClient(cm, httpParams);
		httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
			public void process(HttpRequest request, HttpContext context) {
				if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
					request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
				}
				request.setHeader("rafali-versionCode", "" + Config.VERSION);
				request.setHeader("rafali-packageName", FlickrUploader.getAppContext().getPackageName());
				request.setHeader(HTTP.CONTENT_TYPE, "application/json;charset=UTF-8");
			}
		});

		httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(HttpResponse response, HttpContext context) {
				final HttpEntity entity = response.getEntity();
				if (entity == null) {
					return;
				}
				final Header encoding = entity.getContentEncoding();
				if (encoding != null) {
					for (HeaderElement element : encoding.getElements()) {
						if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
							response.setEntity(new InflatingEntity(response.getEntity()));
							break;
						}
					}
				}
			}
		});

	}

	static void setCookieSession() {
		BasicCookieStore mCookieStore = new BasicCookieStore();
		mCookieStore.addCookie(createCookie("DEVICEID", Utils.getDeviceId()));
		rafaliHttpContext.setAttribute(ClientContext.COOKIE_STORE, mCookieStore);
	}

	private static BasicClientCookie createCookie(String name, String value) {
		BasicClientCookie cookie = new BasicClientCookie(name, value);
		cookie.setDomain(Config.HOSTNAME);
		cookie.setPath("/");
		return cookie;
	}

	// static List<UserLog> getLastUserlogs() {
	// ArrayList<UserLog> logs = Lists.newArrayList(userLastLogs);
	// List<UserLog> batchUserlogs = Analytics.getBatchUserlogs();
	// if (batchUserlogs != null)
	// logs.addAll(batchUserlogs);
	// return logs;
	// }

	private static String postRpc(Method method, Object[] args) {
		String responseStr = null;
		try {
			HttpPost post = new HttpPost(Config.HTTP_START + "/androidRpc?method=" + method.getName());
			if (args != null) {
				// String encode = ToolStream.encode(args);
				// LOG.debug("encoded string : " + encode.length());
				// LOG.debug("gzipped string : " + ToolStream.encodeGzip(args).length());
				Object[] args_logs = new Object[3];
				args_logs[0] = args;
				post.setEntity(new StringEntity(Streams.encodeGzip(args_logs), HTTP.UTF_8));

			}
			if (Config.isDebug())
				LOG.trace("postRpc " + method.getName() + "(" + Joiner.on(',').useForNull("null").join(args) + ")");

			responseStr = makeRequest(post, true);

		} catch (ConnectException e) {
			LOG.warn("ConnectException on postRpc " + method.getName() + "(" + Joiner.on(',').useForNull("null").join(args) + ")");
		} catch (Throwable e) {
			// Analytics.failedProcessing(userlogs);
			LOG.error(e.getClass().getSimpleName() + " Failed androidRpc : " + method.getName() + " : " + Arrays.toString(args), e);
		}
		return responseStr;
	}

	private static String JSESSIONID;

	public static String makeRequest(HttpUriRequest request, boolean isRpc) throws IOException {
		HttpResponse response;

		boolean retry = true;
		IOException cause = null;
		int executionCount = 0;
		while (retry) {
			try {
				if (isRpc) {
					response = httpClient.execute(request, rafaliHttpContext);
				} else {
					response = httpClient.execute(request);
				}

				String responseStr = null;
				if (isRpc) {
					for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
						if ("JSESSIONID".equals(cookie.getName())) {
							JSESSIONID = cookie.getValue();
							LOG.debug("JSESSIONID : " + JSESSIONID);
							break;
						}
					}
				}

				Header appMinimalSupportedVersionCode = response.getFirstHeader("rafali-appMinimalSupportedVersionCode");
				if (appMinimalSupportedVersionCode == null || Config.VERSION >= Integer.valueOf(appMinimalSupportedVersionCode.getValue())) {

					HttpEntity resEntity = response.getEntity();
					if (resEntity != null) {
						responseStr = EntityUtils.toString(resEntity);
					}
					if (resEntity != null) {
						resEntity.consumeContent();
					}

					Header appCurrentVersionCode = response.getFirstHeader("rafali-appCurrentVersionCode");
					if (appCurrentVersionCode != null && Config.VERSION < Integer.valueOf(appCurrentVersionCode.getValue())) {
						// Dialogs.showUpdateDialog(AbstractActivity.getCurrentActivity());//TODO
					}

					// try {
					// Header appServerVersion = response.getFirstHeader("rafali-appServerVersion");
					// } catch (Throwable e) {
					// LOG.error(ToolString.stack2string(e));
					// }

				} else {
					// TODO
				}
				return responseStr;
			} catch (IOException e) {
				cause = e;
				retry = retryRequest(request, cause, ++executionCount, isRpc ? rafaliHttpContext : null);
			} catch (NullPointerException e) {
				// there's a bug in HttpClient 4.0.x that on some occasions causes
				// DefaultRequestExecutor to throw an NPE, see
				// http://code.google.com/p/android/issues/detail?id=5255
				cause = new IOException("NPE in HttpClient" + e.getMessage());
				retry = retryRequest(request, cause, ++executionCount, isRpc ? rafaliHttpContext : null);
			}
		}

		// no retries left, crap out with exception
		ConnectException ex = new ConnectException();
		ex.initCause(cause);
		throw ex;

	}

	private static final int RETRY_SLEEP_TIME_MILLIS = 2000;
	private static final HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
	private static final HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

	static {
		// Retry if the server dropped connection on us
		exceptionWhitelist.add(NoHttpResponseException.class);
		// retry-this, since it may happens as part of a Wi-Fi to 3G failover
		exceptionWhitelist.add(UnknownHostException.class);
		// retry-this, since it may happens as part of a Wi-Fi to 3G failover
		exceptionWhitelist.add(SocketException.class);

		// never retry timeouts
		exceptionBlacklist.add(InterruptedIOException.class);
		// never retry SSL handshake failures
		exceptionBlacklist.add(SSLHandshakeException.class);
	}

	private static boolean retryRequest(HttpUriRequest request, IOException exception, final int executionCount, HttpContext context) {
		boolean retry = true;
		Boolean b = context == null ? null : (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
		boolean sent = (b != null && b.booleanValue());
		LOG.warn(String.format("Network error : url: %s, sent: %s, executionCount: %s, exception: %s, thread: %s - %s", request.getURI(), sent, executionCount, exception, Thread.currentThread()
				.getName(), Thread.currentThread().getPriority()));

		if (executionCount > DEFAULT_MAX_RETRIES) {
			// Do not retry if over max retry count
			Utils.toast("Network error");
			retry = false;
		} else if (exceptionBlacklist.contains(exception.getClass())) {
			// immediately cancel retry if the error is blacklisted
			retry = false;
		} else if (exceptionWhitelist.contains(exception.getClass())) {
			// immediately retry if error is whitelisted
			retry = true;
		} else if (!sent) {
			// for most other errors, retry only if request hasn't been fully sent yet
			retry = true;
		}

		if (retry) {
			if (executionCount >= 2 && Thread.currentThread().getPriority() >= Thread.NORM_PRIORITY) {
				notifyNetworkError();
			}
			SystemClock.sleep(RETRY_SLEEP_TIME_MILLIS * executionCount);
		}

		return retry;
	}

	private static long lastNotifyNetworkError = 0;

	static void notifyNetworkError() {
		if (System.currentTimeMillis() - lastNotifyNetworkError > 10000) {
			lastNotifyNetworkError = System.currentTimeMillis();
			Utils.toast("Network error, retrying…");
		}
	}

	private static final AndroidRpcInterface rpcService = (AndroidRpcInterface) Proxy.newProxyInstance(RpcHandler.class.getClassLoader(), new Class<?>[] { AndroidRpcInterface.class },
			new RpcHandler());

	public static AndroidRpcInterface getRpcService() {
		return rpcService;
	}

	private static class InflatingEntity extends HttpEntityWrapper {
		public InflatingEntity(HttpEntity wrapped) {
			super(wrapped);
		}

		@Override
		public InputStream getContent() throws IOException {
			return new GZIPInputStream(wrappedEntity.getContent());
		}

		@Override
		public long getContentLength() {
			return -1;
		}
	}

	private static class RpcHandler implements InvocationHandler {

		// HTTP response should stay in cache for 5 seconds
		private static final int HTTP_CACHE_TIMEOUT = 5000;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// LOG.trace("method : " + method);
			final String key = method.getName() + getKey(args);

			Object lock;
			if (locks.containsKey(key)) {
				long start = System.currentTimeMillis();
				lock = locks.get(key);
				synchronized (lock) {
					while (!responses.containsKey(key) && System.currentTimeMillis() - start < 5000) {
						try {
							// LOG.debug("###### pausing in RpcHandler : " + key + " - " + Thread.currentThread().getId());
							lock.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			} else {
				lock = new Object();
				locks.put(key, lock);
			}
			try {
				Object object = null;
				String response;
				if (responses.containsKey(key)) {
					// LOG.debug("###### response still in cache : " + key);
					object = responses.get(key);
				} else {
					int retry = 0;
					while (retry < 3) {
						retry++;
						response = RPC.postRpc(method, args);
						if (ToolString.isBlank(response)) {
							break;
						} else {
							object = Streams.decode(response);
							if (object instanceof Throwable) {
								LOG.warn("Server exception :" + object.getClass().getName() + "," + ((Throwable) object).getMessage());
							} else {
								break;
							}
						}
					}

					if (object != null)
						responses.put(key, object);
					FlickrUploader.getHandler().postDelayed(new Runnable() {
						@Override
						public void run() {
							responses.remove(key);
						}
					}, HTTP_CACHE_TIMEOUT);
				}
				if (object instanceof Throwable) {
					if (object instanceof StreamCorruptedException || object instanceof IOException) {
						LOG.warn(object.getClass().getSimpleName() + " on " + method.getName());
					} else {
						LOG.error("Server error calling " + method.getName(), (Throwable) object);
					}
					return null;
					// throw new Exception(throwable.getClass().getSimpleName() + " : " + throwable.getMessage());
				}
				return object;
			} finally {
				synchronized (lock) {
					lock.notifyAll();
					locks.remove(key);
				}
			}
		}

		private Map<String, Object> responses = new ConcurrentHashMap<String, Object>();
		private Map<String, Object> locks = new ConcurrentHashMap<String, Object>();

		private String getKey(Object[] args) {
			String key = "";
			for (Object object : args) {
				key += object != null ? object.hashCode() : object;
			}
			return key;
		}

	}
}
