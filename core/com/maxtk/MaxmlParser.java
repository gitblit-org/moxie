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
package com.maxtk;

import static java.text.MessageFormat.format;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MaxmlParser is a simple recursive parser that can deserialize an Maxml
 * document. Maxml is based mostly on YAML but borrows ideas from XML and JSON
 * such as space-insensitivity.
 * 
 * @author James Moger
 * 
 */
public class MaxmlParser {

	/**
	 * Parse the Maxml content and reflectively build an object with the
	 * document's data.
	 * 
	 * @param content
	 * @param clazz
	 * @return an object
	 * @throws MaxmlException
	 */
	public static <X> X parse(String content, Class<X> clazz) throws MaxmlException {
		try {
			Map<String, Object> map = parse(content.split("\n"));
			X x = clazz.newInstance();
			Map<String, Field> fields = new HashMap<String, Field>();
			for (Field field : clazz.getFields()) {
				fields.put(field.getName().toLowerCase(), field);
			}
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				String key = entry.getKey();
				Object o = entry.getValue();
				if (fields.containsKey(key)) {
					Field field = fields.get(key);
					field.set(x, o);
				} else {
					throw new MaxmlException(format("Unbound property \"{0}\"",
							key));
				}
			}
			return x;
		} catch (Exception t) {
			throw new MaxmlException(t);
		}
	}

	/**
	 * Parse the content of the Maxml document and return a object map of the
	 * content.
	 * 
	 * @param content
	 * @return an object map
	 */
	public static Map<String, Object> parse(String content) throws MaxmlException {
		try {
			return parse(content.split("\n"));
		} catch (Exception e) {
			throw new MaxmlException(e);
		}
	}

	/**
	 * Recursive method to parse an Maxml document.
	 * 
	 * @param lines
	 * @return an object map
	 */
	private static Map<String, Object> parse(String[] lines) {
		String lastKey = null;
		MaxmlMap map = new MaxmlMap();
		ArrayList<Object> array = null;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.charAt(0) == '#') {
				// ignore comment
				continue;
			} else if (line.equals("...")) {
				// ignore end of document
				continue;
			} else if (line.equals("---")) {
				// ignore new document
				continue;
			} else if (line.charAt(0) == '}') {
				// end this map
				map.put("__linesparsed__", i + 1);
				return map;
			} else if (line.charAt(0) == '-') {
				// array element
				if (array == null) {
					array = new ArrayList<Object>();
					map.put(lastKey, array);
				}
				Object value = parseValue(line.substring(1).trim());
				array.add(value);
			} else {
				// field:value
				int colon = line.indexOf(':');
				String key = line.substring(0, colon).trim();
				String value = line.substring(colon + 1).trim();
				Object o;
				if (value.length() == 0) {
					// empty string
					o = value;
				} else if (value.charAt(0) == '{') {
					// map
					String[] sublines = new String[lines.length - i - 1];
					System.arraycopy(lines, i + 1, sublines, 0, sublines.length);
					Map<String, Object> submap = parse(sublines);
					int linesParsed = (Integer) submap
							.remove("__linesparsed__");
					i += linesParsed;
					o = submap;
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
	public static Object parseValue(String value) {
		if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			// quoted string, strip double quotes
			return value.substring(1, value.length() - 1).trim();
		} else if (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
			// quoted string, strip single quotes
			return value.substring(1, value.length() - 1).trim();
		}
		if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
			// array
			ArrayList<Object> array = new ArrayList<Object>();
			String inside = value.substring(1, value.length() - 1).trim();
			for (String field : inside.split(",")) {
				Object object = parseValue(field);
				array.add(object);
			}
			return array;
		}

		value = value.trim();
		String vlc = value.toLowerCase();
		if (vlc.equals("true") || vlc.equals("yes") || vlc.equals("on")) {
			return Boolean.TRUE;
		} else if (vlc.equals("false") || vlc.equals("no") || vlc.equals("off")) {
			return Boolean.FALSE;
		} else if (value.length() > 1) {
			// try parsing numbers
			char code = vlc.charAt(value.length() - 1);
			String number = value.substring(0, value.length() - 1);
			try {
				switch (code) {
				case 'f':
					return Float.parseFloat(number);
				case 'd':
					return Double.parseDouble(number);
				case 'n':
					return Integer.parseInt(number);
				case 'l':
					return Long.parseLong(number);
				}
			} catch (Throwable t) {
			}

			// date/time parsing
			String canonical = "yyyy-MM-dd'T'HH:mm:ss'Z'";
			String iso8601 = "yyyy-MM-dd'T'HH:mm:ssZ";
			String date = "yyyy-MM-dd";
			String[] patterns = { canonical, iso8601, date };
			for (String pattern : patterns) {
				try {
					SimpleDateFormat df = new SimpleDateFormat(pattern);
					return df.parse(value);
				} catch (Throwable t) {
					// t.printStackTrace();
				}
			}
		}

		if (value.equals("~")) {
			// null
			return null;
		}

		// default to string
		return value;
	}

	/**
	 * MaxmlMap is a subclass of LinkedHashMap that forces keys to lowercase.
	 */
	public static class MaxmlMap extends LinkedHashMap<String, Object> {

		private static final long serialVersionUID = 1L;

		@Override
		public boolean containsKey(Object key) {
			return super.containsKey(key.toString().toLowerCase());
		}

		@Override
		public Object get(Object key) {
			return super.get(key.toString().toLowerCase());
		}

		@Override
		public Object put(String key, Object value) {
			return super.put(key.toString().toLowerCase(), value);
		}

		@Override
		public Object remove(Object key) {
			return super.remove(key.toString().toLowerCase());
		}

	}

	/**
	 * MaxmlException is thrown by the MaxmlParser.
	 * 
	 * @author James Moger
	 * 
	 */
	public static class MaxmlException extends Exception {

		private static final long serialVersionUID = 1L;

		public MaxmlException(String msg) {
			super(msg);
		}

		public MaxmlException(Throwable t) {
			super(t);
		}
	}
}