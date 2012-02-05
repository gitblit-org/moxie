/*
 * Copyright 2012 James Moger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.maxtk.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * Utility class of string functions.
 * 
 * @author James Moger
 * 
 */
public class StringUtils {

	/**
	 * Returns true if the string is null or empty.
	 * 
	 * @param value
	 * @return true if string is null or empty
	 */
	public static boolean isEmpty(String value) {
		return value == null || value.trim().length() == 0;
	}

	/**
	 * Replaces carriage returns and line feeds with html line breaks.
	 * 
	 * @param string
	 * @return plain text with html line breaks
	 */
	public static String breakLinesForHtml(String string) {
		return string.replace("\r\n", "<br/>").replace("\r", "<br/>").replace("\n", "<br/>");
	}

	/**
	 * Prepare text for html presentation. Replace sensitive characters with
	 * html entities.
	 * 
	 * @param inStr
	 * @param changeSpace
	 * @return plain text escaped for html
	 */
	public static String escapeForHtml(String inStr, boolean changeSpace) {
		StringBuffer retStr = new StringBuffer();
		int i = 0;
		while (i < inStr.length()) {
			if (inStr.charAt(i) == '&') {
				retStr.append("&amp;");
			} else if (inStr.charAt(i) == '<') {
				retStr.append("&lt;");
			} else if (inStr.charAt(i) == '>') {
				retStr.append("&gt;");
			} else if (inStr.charAt(i) == '\"') {
				retStr.append("&quot;");
			} else if (changeSpace && inStr.charAt(i) == ' ') {
				retStr.append("&nbsp;");
			} else if (changeSpace && inStr.charAt(i) == '\t') {
				retStr.append(" &nbsp; &nbsp;");
			} else {
				retStr.append(inStr.charAt(i));
			}
			i++;
		}
		return retStr.toString();
	}

	/**
	 * Left pad a string with the specified character, if the string length is
	 * less than the specified length.
	 * 
	 * @param input
	 * @param length
	 * @param pad
	 * @return left-padded string
	 */
	public static String leftPad(String input, int length, char pad) {
		if (input.length() < length) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0, len = length - input.length(); i < len; i++) {
				sb.append(pad);
			}
			sb.append(input);
			return sb.toString();
		}
		return input;
	}
	
	/**
	 * Calculates the SHA1 of the string.
	 * 
	 * @param text
	 * @return sha1 of the string
	 */
	public static String getSHA1(String text) {
		try {
			byte[] bytes = text.getBytes("iso-8859-1");
			return getSHA1(bytes);
		} catch (UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		}
	}

	/**
	 * Calculates the SHA1 of the byte array.
	 * 
	 * @param bytes
	 * @return sha1 of the byte array
	 */
	public static String getSHA1(byte[] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(bytes, 0, bytes.length);
			byte[] digest = md.digest();
			return toHex(digest);
		} catch (NoSuchAlgorithmException t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Calculates the MD5 of the string.
	 * 
	 * @param string
	 * @return md5 of the string
	 */
	public static String getMD5(String string) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(string.getBytes("iso-8859-1"));
			byte[] digest = md.digest();
			return toHex(digest);
		} catch (UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		} catch (NoSuchAlgorithmException t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Returns the hex representation of the byte array.
	 * 
	 * @param bytes
	 * @return byte array as hex string
	 */
	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			if (((int) bytes[i] & 0xff) < 0x10) {
				sb.append('0');
			}
			sb.append(Long.toString((int) bytes[i] & 0xff, 16));
		}
		return sb.toString();
	}

	/**
	 * Flatten the list of strings into a single string with a space separator.
	 * 
	 * @param values
	 * @return flattened list
	 */
	public static String flattenStrings(Collection<String> values) {
		return flattenStrings(values, " ");
	}

	/**
	 * Flatten the list of strings into a single string with the specified
	 * separator.
	 * 
	 * @param values
	 * @param separator
	 * @return flattened list
	 */
	public static String flattenStrings(Collection<String> values, String separator) {
		StringBuilder sb = new StringBuilder();
		for (String value : values) {
			sb.append(value).append(separator);
		}
		if (sb.length() > 0) {
			// truncate trailing separator
			sb.setLength(sb.length() - separator.length());
		}
		return sb.toString().trim();
	}

	/**
	 * Join a base path and a resource into a single well-formed url.
	 * 
	 * @param baseUrl
	 * @param resource
	 * @return a url
	 */
	public static String makeUrl(String baseUrl, String resource) {
		if (baseUrl.charAt(baseUrl.length() - 1) != '/') {
			baseUrl = baseUrl += "/";
		}
		if (resource.charAt(0) == '/') {
			resource = resource.substring(1);
		}
		return baseUrl + resource;
	}
	
	/**
	 * Returns the path remainder after subtracting the basePath from the
	 * fullPath.
	 * 
	 * @param basePath
	 * @param fullPath
	 * @return the relative path
	 */
	public static String getRelativePath(String basePath, String fullPath) {
		String relativePath = fullPath.substring(basePath.length()).replace('\\', '/');
		if (relativePath.charAt(0) == '/') {
			relativePath = relativePath.substring(1);
		}
		return relativePath;
	}

}