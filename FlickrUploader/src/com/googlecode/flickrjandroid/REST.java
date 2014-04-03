/*
 * Copyright (c) 2005 Aetrion LLC.
 */
package com.googlecode.flickrjandroid;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.googlecode.flickrjandroid.oauth.OAuthUtils;
import com.googlecode.flickrjandroid.uploader.ImageParameter;
import com.googlecode.flickrjandroid.uploader.UploaderResponse;
import com.googlecode.flickrjandroid.util.Base64;
import com.googlecode.flickrjandroid.util.IOUtilities;
import com.googlecode.flickrjandroid.util.StringUtilities;
import com.googlecode.flickrjandroid.util.UrlUtilities;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.Config;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService;

/**
 * Transport implementation using the REST interface.
 * 
 * @author Anthony Eden
 * @version $Id: REST.java,v 1.26 2009/07/01 22:07:08 x-mago Exp $
 */
public class REST extends Transport {
	private static final Logger LOG = LoggerFactory.getLogger(REST.class);

	private static final String UTF8 = "UTF-8";
	public static final String PATH = "/services/rest/";
	private boolean proxyAuth = false;
	private String proxyUser = "";
	private String proxyPassword = "";
	private DocumentBuilder builder;

	/**
	 * Construct a new REST transport instance.
	 * 
	 * @throws ParserConfigurationException
	 */
	public REST() throws ParserConfigurationException {
		setTransportType(REST);
		setHost(Flickr.DEFAULT_HOST);
		setPath(PATH);
		setResponseClass(RESTResponse.class);
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		builder = builderFactory.newDocumentBuilder();
	}

	/**
	 * Construct a new REST transport instance using the specified host endpoint.
	 * 
	 * @param host
	 *            The host endpoint
	 * @throws ParserConfigurationException
	 */
	public REST(String host) throws ParserConfigurationException {
		this();
		setHost(host);
	}

	/**
	 * Construct a new REST transport instance using the specified host and port endpoint.
	 * 
	 * @param host
	 *            The host endpoint
	 * @param port
	 *            The port
	 * @throws ParserConfigurationException
	 */
	public REST(String host, int port) throws ParserConfigurationException {
		this();
		setHost(host);
		setPort(port);
	}

	/**
	 * Set a proxy for REST-requests.
	 * 
	 * @param proxyHost
	 * @param proxyPort
	 */
	public void setProxy(String proxyHost, int proxyPort) {
		System.setProperty("http.proxySet", "true");
		System.setProperty("http.proxyHost", proxyHost);
		System.setProperty("http.proxyPort", "" + proxyPort);
	}

	/**
	 * Set a proxy with authentication for REST-requests.
	 * 
	 * @param proxyHost
	 * @param proxyPort
	 * @param username
	 * @param password
	 */
	public void setProxy(String proxyHost, int proxyPort, String username, String password) {
		setProxy(proxyHost, proxyPort);
		proxyAuth = true;
		proxyUser = username;
		proxyPassword = password;
	}

	/**
	 * Invoke an HTTP GET request on a remote host. You must close the InputStream after you are done with.
	 * 
	 * @param path
	 *            The request path
	 * @param parameters
	 *            The parameters (collection of Parameter objects)
	 * @return The Response
	 * @throws IOException
	 * @throws JSONException
	 */
	public Response get(String path, List<Parameter> parameters) throws IOException, JSONException {
		parameters.add(new Parameter("nojsoncallback", "1"));
		parameters.add(new Parameter("format", "json"));
		String data = getLine(path, parameters);
		return new RESTResponse(data);
	}

	private InputStream getInputStream(String path, List<Parameter> parameters) throws IOException {
		URL url = UrlUtilities.buildUrl(getHost(), getPort(), path, parameters);
		if (Config.isDebug()) {
			LOG.debug("GET URL: {}", url.toString());
		}
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.addRequestProperty("Cache-Control", "no-cache,max-age=0");
		conn.addRequestProperty("Pragma", "no-cache");
		conn.setRequestMethod("GET");
		if (proxyAuth) {
			conn.setRequestProperty("Proxy-Authorization", "Basic " + getProxyCredentials());
		}
		conn.connect();
		if (Config.isDebug()) {
			LOG.debug("response code : " + conn.getResponseCode());
		}
		return conn.getInputStream();
	}

	/**
	 * Send a GET request to the provided URL with the given parameters, then return the response as a String.
	 * 
	 * @param path
	 * @param parameters
	 * @return the data in String
	 * @throws IOException
	 */
	public String getLine(String path, List<Parameter> parameters) throws IOException {
		InputStream in = null;
		BufferedReader rd = null;
		try {
			in = getInputStream(path, parameters);
			rd = new BufferedReader(new InputStreamReader(in, OAuthUtils.ENC));
			final StringBuffer buf = new StringBuffer();
			String line;
			while ((line = rd.readLine()) != null) {
				buf.append(line);
			}

			return buf.toString();
		} catch (IOException e) {
			LOG.error(e.getMessage());
			throw e;
		} finally {
			IOUtilities.close(in);
			IOUtilities.close(rd);
		}
	}

	/**
	 * <p>
	 * A helper method for sending a GET request to the provided URL with the given parameters, then return the response as a Map.
	 * </p>
	 * 
	 * <p>
	 * Please make sure the response data is a Map before calling this method.
	 * </p>
	 * 
	 * @param path
	 * @param parameters
	 * @return the data in Map with key value pairs
	 * @throws IOException
	 */
	public Map<String, String> getMapData(boolean getRequestMethod, String path, List<Parameter> parameters) throws IOException {
		String data = getRequestMethod ? getLine(path, parameters) : sendPost(path, parameters);
		return getDataAsMap(URLDecoder.decode(data, OAuthUtils.ENC));
	}

	public Map<String, String> getDataAsMap(String data) {
		Map<String, String> result = new HashMap<String, String>();
		if (data != null) {
			for (String string : StringUtilities.split(data, "&")) {
				String[] values = StringUtilities.split(string, "=");
				if (values.length == 2) {
					result.put(values[0], values[1]);
				}
			}
		}
		return result;
	}

	@Override
	protected Response sendUpload(String path, List<Parameter> parameters) throws IOException, FlickrException, SAXException {
		return sendUpload(path, parameters, null);
	}

	void reportProgress(Media media, int progress) {
		media.setProgress(progress);
		UploadService.onUploadProgress(media);
	}

	static Map<Media, UploadThread> uploadThreads = new ConcurrentHashMap<Media, UploadThread>();

	public static void kill(Media media) {
		try {
			UploadThread uploadThread = uploadThreads.get(media);
			LOG.warn("killing " + media + ", uploadThread=" + uploadThread);
			if (uploadThread != null) {
				uploadThread.kill();
			}
		} catch (Exception e) {
			LOG.error(ToolString.stack2string(e));
		}
	}

	class UploadThread extends Thread {
		private final Media media;
		private final String path;
		private final List<Parameter> parameters;
		private final Object[] responseContainer;
		HttpURLConnection conn = null;
		DataOutputStream out = null;
		private InputStream in;

		public UploadThread(Media media, String path, List<Parameter> parameters, Object[] responseContainer) {
			this.media = media;
			this.path = path;
			this.parameters = parameters;
			this.responseContainer = responseContainer;
		}

		boolean killed = false;

		void kill() {
			killed = true;
			new Thread(new Runnable() {
				@Override
				public void run() {
					if (out != null) {
						try {
							out.close();
							LOG.warn("DataOutputStream closed");
						} catch (Throwable e) {
							LOG.error(ToolString.stack2string(e));
						}
					} else {
						LOG.warn("DataOutputStream is null");
					}
					if (in != null) {
						try {
							in.close();
							LOG.warn("InputStream closed");
						} catch (Throwable e) {
							LOG.error(ToolString.stack2string(e));
						}
					} else {
						LOG.warn("InputStream is null");
					}
					if (conn != null) {
						try {
							conn.setConnectTimeout(50);
							conn.setReadTimeout(50);
							conn.disconnect();
						} catch (Throwable e) {
							LOG.error(ToolString.stack2string(e));
						}
					} else {
						LOG.warn("HttpURLConnection is null");
					}
					try {
						UploadThread.this.interrupt();
						LOG.warn(this + " is interrupted : " + UploadThread.this.isInterrupted());
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
					onFinish();
				}
			}).start();
			;
		}

		@Override
		public void run() {
			// String data = null;
			int progress = 0;
			reportProgress(media, 0);
			try {
				URL url = UrlUtilities.buildPostUrl(getHost(), getPort(), path);

				if (Config.isDebug()) {
					LOG.debug("Post URL: {}", url.toString());
				}
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");

				String boundary = "---------------------------7d273f7a0d3";
				conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
				conn.setRequestProperty("Host", "api.flickr.com");
				conn.setDoInput(true);
				conn.setDoOutput(true);

				boundary = "--" + boundary;

				boolean shouldStream = false;
				int contentLength = 0;
				contentLength += boundary.getBytes("UTF-8").length;
				for (Parameter parameter : parameters) {
					contentLength += "\r\n".getBytes("UTF-8").length;
					if (parameter.getValue() instanceof String) {
						contentLength += ("Content-Disposition: form-data; name=\"" + parameter.getName() + "\"\r\n").getBytes("UTF-8").length;
						contentLength += ("Content-Type: text/plain; charset=UTF-8\r\n\r\n").getBytes("UTF-8").length;
						contentLength += ((String) parameter.getValue()).getBytes("UTF-8").length;
					} else if (parameter instanceof ImageParameter && parameter.getValue() instanceof File) {
						ImageParameter imageParam = (ImageParameter) parameter;
						File file = (File) parameter.getValue();
						if (file.length() > 4 * 1024 * 1024L) {
							shouldStream = true;
						}
						contentLength += String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n", parameter.getName(), imageParam.getImageName())
								.getBytes("UTF-8").length;
						contentLength += String.format(Locale.US, "Content-Type: image/%s\r\n\r\n", imageParam.getImageType()).getBytes("UTF-8").length;

						LOG.debug("set to upload " + file + " : " + file.length() + " bytes");
						contentLength += file.length();
						break;
					}
					contentLength += "\r\n".getBytes("UTF-8").length;
					contentLength += boundary.getBytes("UTF-8").length;
				}
				contentLength += "--\r\n\r\n".getBytes("UTF-8").length;

				contentLength += 213;// dirty hack to account for missing param somewhere
				LOG.debug("contentLength : " + contentLength);

				if (shouldStream) {// may be buggy due to the aforementioned dirty hack so only on big files
					conn.setRequestProperty("Content-Length", "" + contentLength);
					conn.setFixedLengthStreamingMode(contentLength);
				}

				conn.connect();
				progress = 1;
				reportProgress(media, progress);
				out = new DataOutputStream(conn.getOutputStream());
				out.writeBytes(boundary);
				progress = 2;
				reportProgress(media, progress);

				for (Parameter parameter : parameters) {
					progress = writeParam(progress, parameter, out, boundary, media);
				}

				out.writeBytes("--\r\n\r\n");
				out.flush();

				LOG.debug("out.size() : " + out.size());

				out.close();

				progress = 51;
				reportProgress(media, progress);
				int responseCode = -1;
				final int[] progressArray = new int[] { progress };
				try {
					new Thread(new Runnable() {
						@Override
						public void run() {
							int progress = progressArray[0];
							while (UploadThread.this.isAlive() && !UploadThread.this.isInterrupted() && progressArray[0] <= 51 && progress < 98) {
								progress = Math.min(98, progress + 1);
								reportProgress(media, progress);
								try {
									Thread.sleep(Math.max(1000, (progress - 65) * 700));
								} catch (InterruptedException ignore) {
								}
							}
						}
					}).start();
					responseCode = conn.getResponseCode();
				} catch (IOException e) {
					LOG.error("Failed to get the POST response code\n" + ToolString.stack2string(e));
					if (conn.getErrorStream() != null) {
						responseCode = conn.getResponseCode();
					}
					responseContainer[0] = e;
				} finally {
					progress = 99;
					progressArray[0] = progress;
					reportProgress(media, progress);
				}
				if (responseCode < 0) {
					LOG.error("some error occured : " + responseCode);
				} else if ((responseCode != HttpURLConnection.HTTP_OK)) {
					String errorMessage = readFromStream(conn.getErrorStream());
					String detailMessage = "Connection Failed. Response Code: " + responseCode + ", Response Message: " + conn.getResponseMessage() + ", Error: " + errorMessage;
					LOG.error("detailMessage : " + detailMessage);
					throw new IOException(detailMessage);
				}
				if (killed) {
					LOG.warn("thread was killed");
					if (responseContainer[0] == null) {
						responseContainer[0] = new UploadService.UploadException("upload cancelled by user", false);
					}
				} else {
					UploaderResponse response = new UploaderResponse();
					in = conn.getInputStream();
					Document document = builder.parse(in);
					response.parse(document);
					responseContainer[0] = response;
				}
			} catch (Throwable t) {
				responseContainer[0] = t;
			} finally {
				try {
					progress = 100;
					reportProgress(media, progress);
					IOUtilities.close(out);
					if (conn != null)
						conn.disconnect();
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
				onFinish();
			}
		}

		private void onFinish() {
			try {
				LOG.debug("finishing thread : " + responseContainer[0]);
				uploadThreads.remove(media);
				synchronized (responseContainer) {
					responseContainer.notifyAll();
				}
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gmail.yuyang226.flickr.Transport#sendUpload(java.lang.String, java.util.List)
	 */
	public Response sendUpload(final String path, final List<Parameter> parameters, final Media media) throws IOException, FlickrException, SAXException {
		if (Config.isDebug()) {
			LOG.debug("Send Upload Input Params: path '{}'; parameters {}", path, parameters);
		}

		final Object[] responseContainer = new Object[1];

		UploadThread uploadThread = new UploadThread(media, path, parameters, responseContainer);
		uploadThreads.put(media, uploadThread);
		uploadThread.start();

		synchronized (responseContainer) {
			try {
				responseContainer.wait();
			} catch (InterruptedException e) {
			}
		}

		if (responseContainer[0] == null) {
			LOG.debug("response is null, waiting a bit more in case of thread interruption");
			synchronized (responseContainer) {
				try {
					responseContainer.wait(1000);
				} catch (InterruptedException e) {
				}
			}
		}

		LOG.debug("response : " + responseContainer[0]);

		if (responseContainer[0] instanceof Response) {
			return (Response) responseContainer[0];
		} else if (responseContainer[0] instanceof IOException) {
			throw (IOException) responseContainer[0];
		} else if (responseContainer[0] instanceof FlickrException) {
			throw (FlickrException) responseContainer[0];
		} else if (responseContainer[0] instanceof SAXException) {
			throw (SAXException) responseContainer[0];
		} else if (responseContainer[0] instanceof Throwable) {
			Throwable throwable = (Throwable) responseContainer[0];
			throw new UploadService.UploadException(throwable.getMessage(), throwable);
		}
		return null;

	}

	public String sendPost(String path, List<Parameter> parameters) throws IOException {
		String method = null;
		int timeout = 0;
		for (Parameter parameter : parameters) {
			if (parameter.getName().equalsIgnoreCase("method")) {
				method = (String) parameter.getValue();
			} else if (parameter.getName().equalsIgnoreCase("machine_tags") && ((String) parameter.getValue()).contains("file:md5sum")) {
				timeout = 10000;
			}
		}
		if (Config.isDebug()) {
			LOG.debug("API " + method + ", timeout=" + timeout);
			LOG.trace("Send Post Input Params: path '{}'; parameters {}", path, parameters);
		}
		HttpURLConnection conn = null;
		DataOutputStream out = null;
		String data = null;
		try {
			URL url = UrlUtilities.buildPostUrl(getHost(), getPort(), path);
			if (Config.isDebug()) {
				LOG.trace("Post URL: {}", url.toString());
			}
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			String postParam = encodeParameters(parameters);
			byte[] bytes = postParam.getBytes(UTF8);
			conn.setRequestProperty("Content-Length", Integer.toString(bytes.length));
			conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.addRequestProperty("Cache-Control", "no-cache,max-age=0");
			conn.addRequestProperty("Pragma", "no-cache");
			conn.setUseCaches(false);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			if (timeout > 0) {
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(timeout);
			}
			conn.connect();
			out = new DataOutputStream(conn.getOutputStream());
			out.write(bytes);
			out.flush();
			out.close();

			int responseCode = HttpURLConnection.HTTP_OK;
			try {
				responseCode = conn.getResponseCode();
			} catch (IOException e) {
				LOG.error("Failed to get the POST response code\n" + ToolString.stack2string(e));
				if (conn.getErrorStream() != null) {
					responseCode = conn.getResponseCode();
				}
			}
			if ((responseCode != HttpURLConnection.HTTP_OK)) {
				String errorMessage = readFromStream(conn.getErrorStream());
				throw new IOException("Connection Failed. Response Code: " + responseCode + ", Response Message: " + conn.getResponseMessage() + ", Error: " + errorMessage);
			}

			String result = readFromStream(conn.getInputStream());
			data = result.trim();
			return data;
		} finally {
			IOUtilities.close(out);
			if (conn != null)
				conn.disconnect();
			if (Config.isDebug()) {
				LOG.trace("Send Post Result: {}", data);
			}
		}
	}

	private String readFromStream(InputStream input) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(input));
			StringBuffer buffer = new StringBuffer();
			String line = null;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
			}
			return buffer.toString();
		} finally {
			IOUtilities.close(input);
			IOUtilities.close(reader);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gmail.yuyang226.flickr.Transport#post(java.lang.String, java.util.List, boolean)
	 */
	@Override
	public Response post(String path, List<Parameter> parameters) throws IOException, JSONException {
		String data = sendPost(path, parameters);
		return new RESTResponse(data);
	}

	public boolean isProxyAuth() {
		return proxyAuth;
	}

	/**
	 * Generates Base64-encoded credentials from locally stored username and password.
	 * 
	 * @return credentials
	 */
	public String getProxyCredentials() {
		return new String(Base64.encode((proxyUser + ":" + proxyPassword).getBytes()));
	}

	public static String encodeParameters(List<Parameter> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return "";
		}
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < parameters.size(); i++) {
			if (i != 0) {
				buf.append("&");
			}
			Parameter param = parameters.get(i);
			buf.append(UrlUtilities.encode(param.getName())).append("=").append(UrlUtilities.encode(String.valueOf(param.getValue())));
		}
		return buf.toString();
	}

	private int writeParam(int progress, Parameter param, DataOutputStream out, String boundary, Media media) throws IOException {
		String name = param.getName();
		out.writeBytes("\r\n");
		if (param instanceof ImageParameter) {
			ImageParameter imageParam = (ImageParameter) param;
			Object value = param.getValue();
			out.writeBytes(String.format(Locale.US, "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\";\r\n", name, imageParam.getImageName()));
			out.writeBytes(String.format(Locale.US, "Content-Type: image/%s\r\n\r\n", imageParam.getImageType()));
			if (value instanceof File) {
				File file = (File) value;
				InputStream in = new FileInputStream(file);
				try {
					long start = System.currentTimeMillis();
					byte[] buf = new byte[512];
					int res = -1;
					int currentProgress = progress;
					while ((res = in.read(buf)) != -1) {
						out.write(buf, 0, res);
						int tmpProgress = (int) Math.min(49, progress + (48 - progress) * Double.valueOf(out.size()) / file.length());
						if (currentProgress != tmpProgress) {
							currentProgress = tmpProgress;
							// if (currentProgress % 5 == 0)
							// out.flush();
							LOG.trace("out.size() : " + out.size() + ", " + file.length());
							reportProgress(media, currentProgress);
						}
					}
					LOG.debug("output in " + (System.currentTimeMillis() - start) + " ms");
					progress = currentProgress;
				} finally {
					if (in != null) {
						in.close();
					}
				}
			} else if (value instanceof byte[]) {
				out.write((byte[]) value);
			}
		} else {
			out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
			out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
			out.write(((String) param.getValue()).getBytes("UTF-8"));
		}
		out.writeBytes("\r\n");
		out.writeBytes(boundary);
		progress = Math.min(50, progress + 1);
		reportProgress(media, progress);
		return progress;
	}
}
