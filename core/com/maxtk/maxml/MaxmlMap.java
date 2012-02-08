package com.maxtk.maxml;

import java.util.LinkedHashMap;

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

}