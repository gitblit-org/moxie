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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * MaxmlMap is a subclass of LinkedHashMap that forces keys to lowercase.
 */
public class MaxmlMap extends LinkedHashMap<String, Object> {

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
	
	public boolean getBoolean(String key, boolean defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof Boolean) {
				return (Boolean) o;
			}
			return Boolean.parseBoolean(o.toString());
		}
		return defaultValue;
	}
	
	public int getInt(String key, int defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof Number) {
				return ((Number) o).intValue();
			}
			return Integer.parseInt(o.toString());
		}
		return defaultValue;
	}

	public String getString(String key, String defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			return o.toString();
		}
		return defaultValue;
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getStrings(String key, List<String> defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof List<?>) {
				return (List<String>) o;
			} else if (o instanceof String) {
				List<String> strings = new ArrayList<String>();
				for (String value : o.toString().split(",")) {
					strings.add(value.trim());
				}
				return strings;
			}
		}
		return defaultValue;
	}

	public Date getDate(String key, Date defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof Date) {
				return (Date) o;
			}
		}
		return defaultValue;
	}
	
	public List<?> getList(String key, List<?> defaultValue) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof List<?>) {
				return (List<?>) o;
			} else if (o instanceof String) {
				return Arrays.asList(o.toString());
			}
		}
		return defaultValue;
	}
	
	public MaxmlMap getMap(String key) {
		if (containsKey(key)) {
			Object o = get(key);
			if (o instanceof MaxmlMap) {
				return (MaxmlMap) o;
			}
		}
		return null;
	}
	
	public String toMaxml() {
		StringBuilder sb = new StringBuilder();
		for (String key : keySet()) {
			Object o = get(key);
			if (o instanceof MaxmlMap) {
				sb.append(escapeKey(key)).append(" : {\n");
				sb.append(((MaxmlMap) o).toMaxml());
				sb.append("\n}\n");
			} else if (o instanceof List) {
				sb.append(escapeKey(key)).append(" :\n");
				for (Object j : ((List<?>) o)) {
					sb.append("- ").append(toMaxml(j)).append('\n');
				}
			} else {
				sb.append(escapeKey(key)).append(" : ").append(toMaxml(o)).append('\n');
			}
		}
		return sb.toString();
	}
	
	String escapeKey(String key) {
		if (key.indexOf(':') > -1) {
			return "\"" + key + "\"";
		}
		return key;
	}
	
	String toMaxml(Object o) {
		StringBuilder sb = new StringBuilder();
		if (o instanceof MaxmlMap) {
			// inline-map
			sb.append(" { ");
			MaxmlMap map = (MaxmlMap) o;
			for (String key : map.keySet()) {
				sb.append(escapeKey(key)).append(':').append(toMaxml(map.get(key))).append(", ");
				sb.append(((MaxmlMap) o).toMaxml());
			}
			// trim trailing comma-space
			sb.setLength(sb.length() - 2);
			sb.append(" }");
		} else if (o instanceof List) {
			// in-line list
			sb.append(" [ ");
			for (Object j : ((List<?>) o)) {
				sb.append("\"").append(toMaxml(j)).append("\", ");
			}
			// trim trailing comma-space
			sb.setLength(sb.length() - 2);
			sb.append(" ]");
		} else if (o instanceof java.sql.Date) {
			// date
			DateFormat date = new SimpleDateFormat("yyyy-MM-dd");
			sb.append(date.format((Date) o)).append('\n');
		} else if (o instanceof Date) {
			// full date
			DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
			sb.append(iso8601.format((Date) o)).append('\n');
		} else {
			// number, boolean, string
			sb.append(o);
		}
		return sb.toString();
	}
}