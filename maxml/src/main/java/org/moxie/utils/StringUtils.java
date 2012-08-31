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
package org.moxie.utils;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
		return string.replace("\r\n", "<br/>").replace("\r", "<br/>")
				.replace("\n", "<br/>");
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
		StringBuilder retStr = new StringBuilder();
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
	
	public static String createBlank(int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			sb.append(' ');
		}
		return sb.toString();
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
			byte [] bytes = string.getBytes("iso-8859-1");
			return getMD5(bytes);
		} catch (UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		}
	}
	
	/**
	 * Calculates the MD5 of the bytes.
	 * 
	 * @param string
	 * @return md5 of the string
	 */
	public static String getMD5(byte [] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(bytes);
			byte[] digest = md.digest();
			return toHex(digest);
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
	public static String flattenStrings(Collection<String> values,
			String separator) {
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
		if (basePath.equals(fullPath)) {
			return "";
		}
		if (fullPath.startsWith(basePath)) {
			String relativePath = fullPath.substring(basePath.length()).replace(
					'\\', '/');
			if (relativePath.charAt(0) == '/') {
				relativePath = relativePath.substring(1);
			}
			return relativePath;
		} return null;
	}
	
	/**
	 * Strip surrounding quotes from a string.
	 * 
	 * @param value
	 * @return the string without leading or trailing quotes 
	 */
	public static String stripQuotes(String value) {
		if ((value.charAt(0) == '\"') || (value.charAt(0) == '\'')) {
			// strip leading quote
			value = value.substring(1);
		}
		if ((value.charAt(value.length() - 1) == '\"') ||
				(value.charAt(value.length() - 1) == '\'')) {
			// strip trailing quote
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}
	
	/**
	 * Breaks the CSV line into strings.
	 * 
	 * @param value
	 * @return a list of strings
	 */
	public static List<String> breakCSV(String value) {
		List<String> array = new ArrayList<String>();
		// http://www.programmersheaven.com/user/Jonathan/blog/73-Splitting-CSV-with-regex
		for (String field : value
				.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*[\\\"^,]*\\\")*(?![^\\\"]*\\\"))")) {
			array.add(stripQuotes(field.trim()).trim());
		}
		return array;
	}
	
	/**
	 * Creates an XML node for the field, if the value is not null.
	 * 
	 * @param field
	 * @param value
	 * @return
	 */
	public static <K> String toXML(String field, K value) {
		if (value != null) {
			return MessageFormat.format("    <{0}>{1}</{0}>\n", field, value);
		}
		return "";
	}
	
	public static String insertTab(String content) {
		StringBuilder sb = new StringBuilder();
		for (String line : content.split("\n")) {
			sb.append("    ").append(line).append('\n');
		}
		return sb.toString();
	}

	/**
	 * Converts a url into a folder name by elimating the protocol and replacing
	 * forward slashes with underscores.
	 * e.g. http://repo1.apache.org/maven2 = repo1.apache.org_maven2
	 * @param url
	 * @return
	 */
	public static String urlToFolder(String url) {
		String val = url.substring(url.indexOf("://") + 3);
		val = val.replace('/', '_');
		val = val.replace(':', '-');
		return val;
	}
	
	/**
	 * Returns the hostname or ip address portion of a url.
	 * 
	 * @param url
	 * @return a hostname or ip address
	 */
	public static String getHost(String url) {
		try {
			URL u = new URL(url);
			return u.getHost();
		} catch (Exception e) {
		}
		return url;
	}
}