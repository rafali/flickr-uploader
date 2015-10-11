/**
 *  @author emerix
 *  @file	ToolStream.java
 *  @datecreation Oct 28, 2009
 */
package com.rafali.flickruploader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rafali.common.Base64UrlSafe;
import com.rafali.common.ToolString;

/**
 *
 */
public class ToolStream {
	private static final Logger logger = LoggerFactory.getLogger(ToolStream.class.getPackage().getName());

	public static byte[] readBytes(InputStream in) {
		ByteBuffer buff = ByteBuffer.allocate(1 << 20);
		byte[] tmp = new byte[1 << 14];
		int size = 0;
		while (true) {
			int r;
			try {
				r = in.read(tmp);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			if (r == -1)
				break;
			buff.put(tmp, 0, r);
			size += r;
		}
		return Arrays.copyOfRange(buff.array(), 0, size);
	}

	static byte[] toBytes(Object object) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(object);
			return bos.toByteArray();
		} catch (IOException e) {
			logger.error("IOException : " + ToolString.stack2string(e));
		}
		return null;
	}

	public static String encode(Object object) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(object);
			out.close();
			byte[] buf = bos.toByteArray();
			String base64 = new String(Base64UrlSafe.encodeBase64(buf));
			return base64;
		} catch (IOException e) {
			logger.error("IOException : " + e.getMessage());
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T decodeGzip(String response) {
		try {
			byte[] bytes = Base64UrlSafe.decodeBase64(response.getBytes());
			ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)));
			T readObject = (T) in.readObject();
			in.close();
			return readObject;
		} catch (IOException e) {
			logger.error("IOException : " + e.getMessage());
		} catch (ClassNotFoundException e) {
			logger.error("ClassNotFoundException : " + e.getMessage());
		}
		return null;
	}

	public static String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}
}
