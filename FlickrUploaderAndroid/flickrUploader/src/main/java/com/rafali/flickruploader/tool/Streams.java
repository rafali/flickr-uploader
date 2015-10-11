/**
 *  @author emerix
 *  @file	ToolStream.java
 *  @datecreation Oct 28, 2009
 */
package com.rafali.flickruploader.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.util.zip.GZIPOutputStream;

import org.slf4j.LoggerFactory;

import com.rafali.common.Base64UrlSafe;
import com.rafali.common.ToolString;

public class Streams {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Streams.class);

	public static String encode(Object object) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(object);
			out.close();
			String base64 = new String(Base64UrlSafe.encodeBase64(bos.toByteArray()), "UTF-8");
			return base64;
		} catch (Exception e) {
			LOG.error(ToolString.stack2string(e));
		}
		return null;
	}

	public static String encodeGzip(Object object) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			GZIPOutputStream gzip = new GZIPOutputStream(bos);
			ObjectOutput out = new ObjectOutputStream(gzip);
			out.writeObject(object);
			out.close();
			String base64 = new String(Base64UrlSafe.encodeBase64(bos.toByteArray()), "UTF-8");
			return base64;
		} catch (Exception e) {
			LOG.error(ToolString.stack2string(e));
		}
		return null;
	}

	public static Object decode(String response) {
		// LOG.trace("decode(%s)", response == null ? null : response.length());
		Object readObject = null;
		ObjectInputStream in = null;
		try {
			byte[] bytes = Base64UrlSafe.decodeBase64(response.getBytes("UTF-8"));
			// LOG.debug("bytes: " + bytes.length);
			in = new ObjectInputStream(new ByteArrayInputStream(bytes));
			// LOG.debug("in null? " + (in == null));
			if (in != null) {
				readObject = in.readObject();
			}
		} catch (StreamCorruptedException e) {
			LOG.warn("StreamCorruptedException : " + e.getMessage());
			readObject = e;
		} catch (IOException e) {
			LOG.error(ToolString.stack2string(e));
		} catch (ClassNotFoundException e) {
			LOG.error(ToolString.stack2string(e));
		} catch (NullPointerException e) {
			LOG.error(ToolString.stack2string(e));
			throw e;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					LOG.warn(e.getClass().getSimpleName() + " : " + e.getMessage());
				}
			}
		}
		return readObject;
	}

	private static boolean copyToFile(InputStream inputStream, File destFile) {
		try {
			OutputStream out = new FileOutputStream(destFile);
			try {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) >= 0) {
					out.write(buffer, 0, bytesRead);
				}
			} finally {
				out.close();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// copy a file from srcFile to destFile, return true if succeed, return
	// false if fail
	public static boolean copyFile(File srcFile, File destFile) {
		boolean result = false;
		try {
			InputStream in = new FileInputStream(srcFile);
			try {
				result = copyToFile(in, destFile);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			result = false;
		}
		return result;
	}

	// from libcore.io.IoUtils
	/**
	 * Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null.
	 */
	public static void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (final RuntimeException rethrown) {
				throw rethrown;
			} catch (final Exception ignored) {
			}
		}
	}

}
