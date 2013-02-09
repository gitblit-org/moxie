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
package org.moxie.maxml;

import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * MaxmlParser is a simple recursive parser that can deserialize an Maxml
 * document. Maxml is based mostly on YAML but borrows ideas from XML and JSON
 * such as space-insensitivity.
 * 
 * @author James Moger
 * 
 */
public class MaxmlParser {

	DateFormat canonical = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	DateFormat date = new SimpleDateFormat("yyyy-MM-dd");
	Pattern datePattern = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}");
	Pattern wholePattern = Pattern.compile("^\\d{1,3}(,\\d{1,3})*$");
	DecimalFormat wholeFormat = new DecimalFormat("#,###,###,###,###,###,###");
	String csvPattern = ",(?=(?:[^\\\"]*\\\"[^\\\"]*[\\\"^,]*\\\")*(?![^\\\"]*\\\"))";

	/**
	 * Recursive method to parse an Maxml document.
	 * 
	 * @param lines
	 * @return an object map
	 */
	public MaxmlMap parse(BufferedReader reader) throws IOException,
			MaxmlException {
		String lastKey = null;
		MaxmlMap map = new MaxmlMap();
		ArrayList<Object> array = null;
		StringBuilder textBlock = new StringBuilder();
		boolean appendTextBuffer = false;
		int textBlockOffset = 0;
		String line = null;
		while ((line = reader.readLine()) != null) {
			// text block processing
			if (appendTextBuffer) {
				if (line.equals("\"\"\"") || line.equals("'''")) {
					// end block
					map.put(lastKey, textBlock.toString());
					textBlock.setLength(0);
					appendTextBuffer = false;
				} else if (line.endsWith("\"\"\"") || line.endsWith("'''")) {
					// end block
					line = parseTextBlock(textBlockOffset, line);
					textBlock.append(line.substring(0, line.length() - 3));
					map.put(lastKey, textBlock.toString());
					textBlock.setLength(0);
					appendTextBuffer = false;
				} else {
					// append line
					line = parseTextBlock(textBlockOffset, line);
					textBlock.append(line);
					textBlock.append('\n');
				}
				continue;
			}

			// trim the line
			String untrimmed = line;
			line = line.trim();
			if (line.length() == 0) {
				// ignore blanks
				continue;
			}
			if (line.charAt(0) == '#') {
				// ignore comment
				continue;
			} else if (line.equals("...")) {
				// ignore end of document
				continue;
			} else if (line.equals("---")) {
				// ignore new document
				continue;
			} else if (line.equals("\"\"\"") || line.equals("'''")) {
				// start text block
				textBlockOffset = 0;
				textBlock.setLength(0);
				appendTextBuffer = true;
			} else if (line.charAt(0) == '}') {
				// end this map
				return map;
			} else if (line.charAt(0) == '-') {
				// array element
				if (array == null) {
					array = new ArrayList<Object>();
					map.put(lastKey, array);
				}
				String rem = line.substring(1).trim();
				Object value;
				if (rem.charAt(0) == '{' && rem.length() == 1) {
					Map<String, Object> submap = parse(reader);
					value = submap;
				} else {
					value = parseValue(rem);
				}				
				array.add(value);
			} else {
				// field:value
				String key;
				String value;
				if (line.charAt(0) == '\"') {
					// "key" : value
					// quoted key because of colons
					int quote = line.indexOf('\"', 1);
					key = line.substring(1, quote).trim();
					
					value = line.substring(quote + 1).trim();
					int colon = value.indexOf(':');
					value = value.substring(colon + 1).trim();
				} else if (line.charAt(0) == '\'') {
					// 'key' : value
					// quoted key because of colons
					int quote = line.indexOf('\'', 1);
					key = line.substring(1, quote).trim();
					
					value = line.substring(quote + 1).trim();
					int colon = value.indexOf(':');
					value = value.substring(colon + 1).trim();

				} else {
					// key : value
					int colon = line.indexOf(':');
					key = line.substring(0, colon).trim();
					value = line.substring(colon + 1).trim();
				}
				Object o;
				if (value.length() == 0) {
					// empty string
					o = value;
				} else if (value.charAt(0) == '{') {
					// map
					Map<String, Object> submap = parse(reader);
					o = submap;
				} else if (value.equals("\"\"\"")) {
					// start text block
					textBlockOffset = untrimmed.indexOf("\"\"\"");
					textBlock.setLength(0);
					appendTextBuffer = true;
					o = "";
				} else if (value.equals("'''")) {
					// start text block
					textBlockOffset = untrimmed.indexOf("'''");
					textBlock.setLength(0);
					appendTextBuffer = true;
					o = "";
				} else {
					// value
					o = parseValue(value);
				}
				// put the value into the map
				map.put(key, o);

				// reset
				lastKey = key;
				array = null;
			}
		}
		return map;
	}

	/**
	 * Parse an Maxml value into an Object.
	 * 
	 * @param value
	 * @return and object
	 */
	public Object parseValue(String value) throws MaxmlException {
		value = value.trim();
		if (value.length() == 0) {
			// empty value
			return value;
		}
		if (value.equals("~")) {
			// null
			return null;
		}

		if (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
			// quoted string, strip single quotes
			return value.substring(1, value.length() - 1).trim();
		}
		if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			// quoted string, strip double quotes
			return value.substring(1, value.length() - 1).trim();
		}
		if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
			// inline list
			ArrayList<Object> array = new ArrayList<Object>();
			String inside = value.substring(1, value.length() - 1).trim();
			// http://www.programmersheaven.com/user/Jonathan/blog/73-Splitting-CSV-with-regex
			for (String field : inside
					.split(csvPattern)) {
				Object object = parseValue(field);
				array.add(object);
			}
			return array;
		}
		if (value.charAt(0) == '{' && value.charAt(value.length() - 1) == '}') {
			// inline map
			MaxmlMap map = new MaxmlMap();
			String inside = value.substring(1, value.length() - 1).trim();
			for (String kvp : inside.split(csvPattern)) {
				int colon = kvp.indexOf(':');
				if (colon < 0) {
					throw new MaxmlException(
							format("Illegal value \"{0}\". Inline map must have key:value pairs!\n{1}",
									kvp, value));
				}
				String[] chunks = kvp.split(":", 2);
				Object o = parseValue(chunks[1].trim());
				map.put(chunks[0].trim(), o);
			}
			return map;
		}

		String vlc = value.toLowerCase();
		if (vlc.equals("true") || vlc.equals("yes") || vlc.equals("on")) {
			return Boolean.TRUE;
		} else if (vlc.equals("false") || vlc.equals("no") || vlc.equals("off")) {
			return Boolean.FALSE;
		} else if (value.length() > 0) {
			// try parsing a whole number
			try {
				long along;
				if (value.charAt(0) == '0') {
					// octal
					along = Long.decode(value);
				} else if (wholePattern.matcher(value).find()) {
					// whole number comma-formatted
					along = wholeFormat.parse(value).longValue();
				} else {
					// hexadecimal, plain number
					along = Long.decode(value);
				}
				if (along <= Integer.MAX_VALUE && along >= Integer.MIN_VALUE) {
					// if it fits in an int, return an int
					return (int) along;
				}
				return along;
			} catch (Exception e) {
			}
			// try parsing a decimal value
			try {
				double adouble = Double.parseDouble(value);
				if (adouble <= Float.MAX_VALUE && adouble >= Float.MIN_VALUE) {
					return (float) adouble;
				}
				return adouble;
			} catch (Throwable t) {
			}

			// date/time parsing
			if (datePattern.matcher(value).find()) {
				DateFormat[] formats = { canonical, iso8601, date };
				for (DateFormat df : formats) {
					try {
						Date aDate = df.parse(value);
						// reset milliseconds to 0
						Calendar cal = Calendar.getInstance();
						cal.setTime(aDate);
						cal.set(Calendar.MILLISECOND, 0);
						return cal.getTime();
					} catch (Throwable t) {
						// t.printStackTrace();
					}
				}
			}
		}

		// default to string
		return value;
	}
	
	protected String parseTextBlock(int textBlockOffset, String line) {
		if (textBlockOffset > 0) {
			// attempt to eliminate leading whitespace
			if (line.length() > textBlockOffset) {
				String leading = line.substring(0, textBlockOffset);
				boolean whitespace = true;
				for (char c : leading.toCharArray()) {
					whitespace &= c == ' ';
				}
				if (whitespace) {
					line = line.substring(textBlockOffset);
				}
			}
		}
		return line;
	}
}