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

import java.util.Arrays;
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
				return Arrays.asList(o.toString());
			}
		}
		return defaultValue;
	}
}