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
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moxie.utils.StringUtils;

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
	int tabWidth = 4;
	int lineCount;
	MaxmlMap rootMap;

	/**
	 * Recursive method to parse an Maxml document.
	 * 
	 * @param lines
	 * @return an object map
	 */
	public MaxmlMap parse(BufferedReader reader) throws IOException, MaxmlException {
		String lastKey = null;
		MaxmlMap map = new MaxmlMap();
		if (rootMap == null) {
			rootMap = map;
		}
		ArrayList<Object> array = null;
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				lineCount++;
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
				} else if (line.equals("\"\"\"") || line.equals("'''") || line.equals("\"\"") || line.equals("''")) {
					// start text block, offset is 0
					String value = parseTextBlock(reader, 0);
					map.put(lastKey, value);
				} else if (line.charAt(0) == '}') {
					// end this map
					return map;
				} else if ((line.charAt(0) == '-') || (line.charAt(0) == '+')) {
					// array element
					if (array == null) {
						array = new ArrayList<Object>();
						map.put(lastKey, array);
					}
					boolean addAll = line.charAt(0) == '+';
					String rem = line.substring(1).trim();
					Object value;
					if (rem.charAt(0) == '{' && rem.length() == 1) {
						Map<String, Object> submap = parse(reader);
						value = submap;
						array.add(value);
					} else if (rem.startsWith("\"\"\"")) {
						// start text block
						String block = rem.substring(3) + parseTextBlock(reader, 0);
						value = block;
						array.add(block);
					} else if (rem.startsWith("'''")) {
						// start text block
						String block = rem.substring(3) + parseTextBlock(reader, 0);
						value = block;
						array.add(value);
					} else if (rem.startsWith("\"\"")) {
						// start offset text block
						int offset = countWhitespace(untrimmed.substring(0, untrimmed.indexOf("\"\"")));
						String block = rem.substring(2) + parseTextBlock(reader, offset);
						value = block;
						array.add(value);
					} else if (rem.startsWith("''")) {
						// start offset text block
						int offset = countWhitespace(untrimmed.substring(0, untrimmed.indexOf("''")));
						String block = rem.substring(2) + parseTextBlock(reader, offset);
						value = block;
						array.add(value);
					} else {
						value = parseValue(rem);
						if (addAll && value instanceof Collection) {
							Collection<?> c = (Collection<?>) value;
							array.addAll(c);
						} else {
							array.add(value);
						}
					}				
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
						String block = parseTextBlock(reader, 0);
						o = block;
					} else if (value.equals("'''")) {
						// start text block
						String block = parseTextBlock(reader, 0);
						o = block;
					} else if (value.equals("\"\"")) {
						// start text block
						int offset = untrimmed.indexOf("\"\"");
						String block = parseTextBlock(reader, offset);
						o = block;
					} else if (value.equals("''")) {
						// start text block
						int offset = untrimmed.indexOf("''");
						String block = parseTextBlock(reader, offset);
						o = block;
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
		} catch (MaxmlException e) {
			throw e;
		} catch (Exception e) {
			throw new MaxmlException(MessageFormat.format("Parsing failed on line {0,number,0}: {1}", lineCount, line), e);
		}
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

		// object reference
		if (value.charAt(0) == '&') {
			if (value.indexOf(' ') == -1) {
				String v = value.substring(1).trim();
				if (v.indexOf('[') > -1 && v.indexOf("..") > -1 && v.indexOf(']') > -1) {
					
					String name = v.substring(0,  v.indexOf('['));
					int a = Integer.parseInt(v.substring(v.indexOf('[') + 1, v.indexOf("..")));
					int b = Integer.parseInt(v.substring(v.indexOf("..") + 2, v.indexOf(']')));
					
					List<Object> list = new ArrayList<Object>();
					for (int i = a; i <= b; i++) {
						String valName = name + i;
						if (name.endsWith("'") || name.endsWith("\"")) {
							valName = name.substring(0, name.length() - 1) + i + name.charAt(name.length() - 1);
						}
						Object o = getObject(valName, rootMap);
						list.add(o);
					}
					return list;
				}
				return getObject(v, rootMap);
			}
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
	
	protected String parseTextBlock(BufferedReader reader, int offset) throws IOException, MaxmlException {
		String line = null;
		StringBuilder sb = new StringBuilder();
		while ((line = reader.readLine()) != null) {
			lineCount++;
			// text block processing
			if (line.equals("\"\"\"") || line.equals("'''") || line.equals("\"\"") || line.equals("''")) {
				// end block
				line = stripWhitespace(offset, line);
				return sb.toString();
			} else if (line.endsWith("\"\"\"") || line.endsWith("'''")) {
				// end textblock
				line = line.substring(0, line.length() - 3);
				line = stripWhitespace(offset, line);
				sb.append(line);
				return sb.toString();
			} else if (line.endsWith("\"\"") || line.endsWith("''")) {
				// end offset text`block
				line = line.substring(0, line.length() - 2);
				line = stripWhitespace(offset, line);
				sb.append(line);
				return sb.toString();
			} else {
				// append line
				line = stripWhitespace(offset, line);
				sb.append(line);
				sb.append('\n');
			}
		}
		throw new MaxmlException(MessageFormat.format("Failed to parse textblock at line {0,number,0}", lineCount));
	}
	
	protected int countWhitespace(String chunk) {
		int count = 0;
		for (char c : chunk.toCharArray()) {
			switch (c) {
			case ' ':
				count++;
				break;
			case '\t':
				count += tabWidth;
				break;
			default:
				break;
			}
		}
		return count;
	}
	
	protected String stripWhitespace(int offset, String line) throws MaxmlException {
		if (offset > 0) {
			// attempt to eliminate leading whitespace
			if (line.length() >= offset) {
				String leading = line.substring(0, offset);
				int whiteCount = 0;
				boolean stripWhitespace = true;
				for (char c : leading.toCharArray()) {
					boolean ws = c== ' ';
					if (ws) {
						whiteCount++;
					}
					stripWhitespace &= ws;
				}
				if (stripWhitespace) {
					return line.substring(offset);
				} else {
					throw new MaxmlException(MessageFormat.format("Line {0,number,0} in a textblock is expected to have {1,number,0} indentation spaces, found {2,number,0}!", lineCount, offset, whiteCount, line));
				}
			}
		}
		return line;
	}
	
	protected Object getObject(String value, MaxmlMap container) {
		Object o = null;
		if (value.charAt(0) == '.') {
			value = value.substring(1);
		}
		if (value.charAt(0) == '\'') {
			int i = value.indexOf('\'', 1);
			String id = value.substring(1,  i);
			o = container.get(id);
			String remainder = value.substring(i + 1);
			if (StringUtils.isEmpty(remainder)) {
				return o;
			}
			if (o instanceof List) {
				return getObject(remainder, (List<?>) o);
			}
			return getObject(remainder, container);
		} else if (value.charAt(0) == '\"') {
			int i = value.indexOf('\"', 1);
			String id = value.substring(1,  i);
			o = container.get(id);
			String remainder = value.substring(i + 1);
			if (StringUtils.isEmpty(remainder)) {
				return o;
			}
			if (o instanceof List) {
				return getObject(remainder, (List<?>) o);
			}
			return getObject(remainder, container);
		}

		String [] fields = value.split("\\.");
		Pattern p = Pattern.compile("(.*)\\[(\\d+)\\]");
		for (String field : fields) {
			int index = -1;
			Matcher m = p.matcher(field);
			if (m.find()) {
				String i = m.group(2);
				index = Integer.parseInt(i);
				field = field.substring(0, field.indexOf('['));
			}
			o = container.get(field);
			if (o instanceof MaxmlMap) {
				container = (MaxmlMap) o;
			} else if (o instanceof List) {
				// grab indexed element from list
				if (index >= 0) {
					o = ((List<?>) o).get(index);
				}
			}
		}
		return o;
	}
	
	protected Object getObject(String value, List<?> list) {
		Pattern p = Pattern.compile("(.*)\\[(\\d+)\\]");
		int index = -1;
		Matcher m = p.matcher(value);
		if (m.find()) {
			String i = m.group(2);
			index = Integer.parseInt(i);
		}
		if (index > -1) {
			return list.get(index);
		}
		return null;
	}
}