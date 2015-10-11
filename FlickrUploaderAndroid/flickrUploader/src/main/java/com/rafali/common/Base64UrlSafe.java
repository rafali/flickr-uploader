package com.rafali.common;

import java.io.UnsupportedEncodingException;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Provides Base64 encoding and decoding with URL and filename safe alphabet as defined by RFC 3548, section 4.
 * <p/>
 * This Base64 encoder is modified to meet URL requirements. The changes are: '+' => '*', '/' => '-', and no padding.
 * <p/>
 * This class is taken from the Apache commons-codec, and adjusted to fit the Wicket framework's needs, especially external dependencies have been removed.
 * </p>
 * <p/>
 * This class implements section <cite>4. Base 64 Encoding with URL and Filename Safe Alphabet</cite> from RFC 3548 <cite>The Base16, Base32, and Base64 Data Encodings</cite> by Simon Josefsson.
 * </p>
 * 
 * @author Apache Software Foundation
 * @author Juergen Donnerstag
 * 
 * @since 1.2
 */
public class Base64UrlSafe {
	private static final String UTF_8 = "UTF-8";

	/**
	 * The base length.
	 */
	private static final int BASELENGTH = 255;

	/**
	 * Lookup length.
	 */
	private static final int LOOKUPLENGTH = 64;

	/**
	 * Used to calculate the number of bits in a byte.
	 */
	private static final int EIGHTBIT = 8;

	/**
	 * Used when encoding something which has fewer than 24 bits.
	 */
	private static final int SIXTEENBIT = 16;

	/**
	 * Used to determine how many bits data contains.
	 */
	private static final int TWENTYFOURBITGROUP = 24;

	/**
	 * Used to get the number of Quadruples.
	 */
	private static final int FOURBYTE = 4;

	/**
	 * Used to test the sign of a byte.
	 */
	private static final int SIGN = -128;

	/**
	 * Contains the Base64 values <code>0</code> through <code>63</code> accessed by using character encodings as indices.
	 * <p/>
	 * For example, <code>base64Alphabet['+']</code> returns <code>62</code>.
	 * </p>
	 * <p/>
	 * The value of undefined encodings is <code>-1</code>.
	 * </p>
	 */
	private static byte[] base64Alphabet = new byte[BASELENGTH];

	/**
	 * <p/>
	 * Contains the Base64 encodings <code>A</code> through <code>Z</code>, followed by <code>a</code> through <code>z</code>, followed by <code>0</code> through <code>9</code>, followed by
	 * <code>+</code>, and <code>/</code>.
	 * </p>
	 * <p/>
	 * This array is accessed by using character values as indices.
	 * </p>
	 * <p/>
	 * For example, <code>lookUpBase64Alphabet[62] </code> returns <code>'+'</code>.
	 * </p>
	 */
	private static byte[] lookUpBase64Alphabet = new byte[LOOKUPLENGTH];

	// Populating the lookup and character arrays
	static {
		for (int i = 0; i < BASELENGTH; i++) {
			base64Alphabet[i] = (byte) -1;
		}
		for (int i = 'Z'; i >= 'A'; i--) {
			base64Alphabet[i] = (byte) (i - 'A');
		}
		for (int i = 'z'; i >= 'a'; i--) {
			base64Alphabet[i] = (byte) (i - 'a' + 26);
		}
		for (int i = '9'; i >= '0'; i--) {
			base64Alphabet[i] = (byte) (i - '0' + 52);
		}

		base64Alphabet['*'] = 62;
		base64Alphabet['-'] = 63;

		for (int i = 0; i <= 25; i++) {
			lookUpBase64Alphabet[i] = (byte) ('A' + i);
		}

		for (int i = 26, j = 0; i <= 51; i++, j++) {
			lookUpBase64Alphabet[i] = (byte) ('a' + j);
		}

		for (int i = 52, j = 0; i <= 61; i++, j++) {
			lookUpBase64Alphabet[i] = (byte) ('0' + j);
		}

		lookUpBase64Alphabet[62] = (byte) '*';
		lookUpBase64Alphabet[63] = (byte) '-';
	}

	/**
	 * Returns whether or not the <code>octet</code> is in the base 64 alphabet.
	 * 
	 * @param octet
	 *            The value to test
	 * @return <code>true</code> if the value is defined in the the base 64 alphabet, <code>false</code> otherwise.
	 */
	private static boolean isBase64(byte octet) {
		if (octet < 0 || base64Alphabet[octet] == -1) {
			return false;
		} else {
			return true;
		}
	}

	public static String encodeServer(String str) {
		try {
			return new String(encodeBase64(str.getBytes(UTF_8)),UTF_8);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String encodeServer(byte[] bytes) {
		try {
			return new String(encodeBase64(bytes),UTF_8);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String decodeServer(String str) {
		try {
			return new String(decodeBase64(str.getBytes(UTF_8)),UTF_8);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String encodeGWT(String str) {
		return new String(encodeBase64(str.getBytes()));
	}
	
	public static String decodeGWT(String str) {
		return new String(decodeBase64(str.getBytes()));
	}
	

	/**
	 * Encodes binary data using the base64 algorithm.
	 * 
	 * @param binaryData
	 *            Array containing binary data to encode.
	 * @return Base64-encoded data.
	 */
	public static byte[] encodeBase64(byte[] binaryData) {
		int lengthDataBits = binaryData.length * EIGHTBIT;
		int fewerThan24bits = lengthDataBits % TWENTYFOURBITGROUP;
		int numberTriplets = lengthDataBits / TWENTYFOURBITGROUP;
		byte encodedData[] = null;
		int encodedDataLength = 0;

		if (fewerThan24bits != 0) {
			// data not divisible by 24 bit
			encodedDataLength = (numberTriplets + 1) * 4;
		} else {
			// 16 or 8 bit
			encodedDataLength = numberTriplets * 4;
		}

		if (fewerThan24bits == EIGHTBIT) {
			encodedDataLength -= 2;
		} else if (fewerThan24bits == SIXTEENBIT) {
			encodedDataLength -= 1;
		}

		encodedData = new byte[encodedDataLength];

		byte k = 0, l = 0, b1 = 0, b2 = 0, b3 = 0;

		int encodedIndex = 0;
		int dataIndex = 0;
		int i = 0;

		// log.debug("number of triplets = " + numberTriplets);
		for (i = 0; i < numberTriplets; i++) {
			dataIndex = i * 3;
			b1 = binaryData[dataIndex];
			b2 = binaryData[dataIndex + 1];
			b3 = binaryData[dataIndex + 2];

			// log.debug("b1= " + b1 +", b2= " + b2 + ", b3= " + b3);

			l = (byte) (b2 & 0x0f);
			k = (byte) (b1 & 0x03);

			byte val1 = ((b1 & SIGN) == 0) ? (byte) (b1 >> 2) : (byte) ((b1) >> 2 ^ 0xc0);
			byte val2 = ((b2 & SIGN) == 0) ? (byte) (b2 >> 4) : (byte) ((b2) >> 4 ^ 0xf0);
			byte val3 = ((b3 & SIGN) == 0) ? (byte) (b3 >> 6) : (byte) ((b3) >> 6 ^ 0xfc);

			encodedData[encodedIndex] = lookUpBase64Alphabet[val1];
			// log.debug( "val2 = " + val2 );
			// log.debug( "k4 = " + (k<<4) );
			// log.debug( "vak = " + (val2 | (k<<4)) );
			encodedData[encodedIndex + 1] = lookUpBase64Alphabet[val2 | (k << 4)];
			encodedData[encodedIndex + 2] = lookUpBase64Alphabet[(l << 2) | val3];
			encodedData[encodedIndex + 3] = lookUpBase64Alphabet[b3 & 0x3f];

			encodedIndex += 4;
		}

		// form integral number of 6-bit groups
		dataIndex = i * 3;

		if (fewerThan24bits == EIGHTBIT) {
			b1 = binaryData[dataIndex];
			k = (byte) (b1 & 0x03);
			// log.debug("b1=" + b1);
			// log.debug("b1<<2 = " + (b1>>2) );
			byte val1 = ((b1 & SIGN) == 0) ? (byte) (b1 >> 2) : (byte) ((b1) >> 2 ^ 0xc0);
			encodedData[encodedIndex] = lookUpBase64Alphabet[val1];
			encodedData[encodedIndex + 1] = lookUpBase64Alphabet[k << 4];
		} else if (fewerThan24bits == SIXTEENBIT) {
			b1 = binaryData[dataIndex];
			b2 = binaryData[dataIndex + 1];
			l = (byte) (b2 & 0x0f);
			k = (byte) (b1 & 0x03);

			byte val1 = ((b1 & SIGN) == 0) ? (byte) (b1 >> 2) : (byte) ((b1) >> 2 ^ 0xc0);
			byte val2 = ((b2 & SIGN) == 0) ? (byte) (b2 >> 4) : (byte) ((b2) >> 4 ^ 0xf0);

			encodedData[encodedIndex] = lookUpBase64Alphabet[val1];
			encodedData[encodedIndex + 1] = lookUpBase64Alphabet[val2 | (k << 4)];
			encodedData[encodedIndex + 2] = lookUpBase64Alphabet[l << 2];
		}

		return encodedData;
	}

	/**
	 * Decodes Base64 data into octets
	 * 
	 * @param base64Data
	 *            Byte array containing Base64 data
	 * @return Array containing decoded data.
	 */
	public static byte[] decodeBase64(byte[] base64Data) {
		// RFC 2045 requires that we discard ALL non-Base64 characters
		base64Data = discardNonBase64(base64Data);

		// handle the edge case, so we don't have to worry about it later
		if (base64Data.length == 0) {
			return new byte[0];
		}

		int numberQuadruple = (base64Data.length + 3) / FOURBYTE;
		byte decodedData[] = new byte[base64Data.length - numberQuadruple];
		byte b1 = 0, b2 = 0, b3 = 0, b4 = 0;

		// Throw away anything not in base64Data
		int encodedIndex = 0;
		int dataIndex = 0;

		for (int i = 0; i < numberQuadruple; i++) {
			dataIndex = i * 4;

			b1 = base64Alphabet[base64Data[dataIndex]];
			b2 = base64Alphabet[base64Data[dataIndex + 1]];

			if ((dataIndex + 3) < base64Data.length) {
				// No PAD e.g 3cQl
				b3 = base64Alphabet[base64Data[dataIndex + 2]];
				b4 = base64Alphabet[base64Data[dataIndex + 3]];

				decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
				decodedData[encodedIndex + 1] = (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
				decodedData[encodedIndex + 2] = (byte) (b3 << 6 | b4);
			} else if ((dataIndex + 2) < base64Data.length) {
				// One PAD e.g. 3cQ[Pad]
				b3 = base64Alphabet[base64Data[dataIndex + 2]];

				decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
				decodedData[encodedIndex + 1] = (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
			} else if ((dataIndex + 1) < base64Data.length) {
				// Two PAD e.g. 3c[Pad][Pad]
				decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
			}
			encodedIndex += 3;
		}
		return decodedData;
	}


	/**
	 * Discards any characters outside of the base64 alphabet, per the requirements on page 25 of RFC 2045 - "Any characters outside of the base64 alphabet are to be ignored in base64 encoded data."
	 * 
	 * @param data
	 *            The base-64 encoded data to groom
	 * @return The data, less non-base64 characters (see RFC 2045).
	 */
	private static byte[] discardNonBase64(byte[] data) {
		byte groomedData[] = new byte[data.length];
		int bytesCopied = 0;

		for (int i = 0; i < data.length; i++) {
			if (isBase64(data[i])) {
				groomedData[bytesCopied++] = data[i];
			}
		}

		byte packedData[] = new byte[bytesCopied];

		System.arraycopy(groomedData, 0, packedData, 0, bytesCopied);

		return packedData;
	}

	// Implementation of the Encoder Interface

}
