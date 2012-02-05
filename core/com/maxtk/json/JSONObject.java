/*
 * Copyright 2006 Fang Yidong<fangyidong@yahoo.com.cn>
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
package com.maxtk.json;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSON object. Key value pairs are unordered. JSONObject supports
 * java.util.Map interface.
 * 
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONObject extends LinkedHashMap<String, Object> implements Map<String, Object>, JSONAware,
		JSONStreamAware {
	private static final long serialVersionUID = -503443796854799292L;

	/**
	 * Encode a map into JSON text and write it to out. If this map is also a
	 * JSONAware or JSONStreamAware, JSONAware or JSONStreamAware specific
	 * behaviours will be ignored at this top level.
	 * 
	 * @see com.maxtk.json.JSONValue#writeJSONString(Object, Writer)
	 * 
	 * @param map
	 * @param out
	 */
	public static void writeJSONString(Map<?, ?> map, Writer out) throws IOException {
		if (map == null) {
			out.write("null");
			return;
		}

		boolean first = true;

		out.write('{');
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (first)
				first = false;
			else
				out.write(',');
			out.write('\"');
			out.write(escape(String.valueOf(entry.getKey())));
			out.write('\"');
			out.write(':');
			JSONValue.writeJSONString(entry.getValue(), out);
		}
		out.write('}');
	}

	public void writeJSONString(Writer out) throws IOException {
		writeJSONString(this, out);
	}

	/**
	 * Convert a map to JSON text. The result is a JSON object. If this map is
	 * also a JSONAware, JSONAware specific behaviours will be omitted at this
	 * top level.
	 * 
	 * @see com.maxtk.json.JSONValue#toJSONString(Object)
	 * 
	 * @param map
	 * @return JSON text, or "null" if map is null.
	 */
	public static String toJSONString(Map<?, ?> map) {
		if (map == null)
			return "null";

		StringBuffer sb = new StringBuffer();
		boolean first = true;

		sb.append('{');
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (first)
				first = false;
			else
				sb.append(',');

			toJSONString(String.valueOf(entry.getKey()), entry.getValue(), sb);
		}
		sb.append('}');
		return sb.toString();
	}

	public String toJSONString() {
		return toJSONString(this);
	}

	private static String toJSONString(String key, Object value, StringBuffer sb) {
		sb.append('\"');
		if (key == null)
			sb.append("null");
		else
			JSONValue.escape(key, sb);
		sb.append('\"').append(':');

		sb.append(JSONValue.toJSONString(value));

		return sb.toString();
	}

	public String toString() {
		return toJSONString();
	}

	public static String toString(String key, Object value) {
		StringBuffer sb = new StringBuffer();
		toJSONString(key, value, sb);
		return sb.toString();
	}

	/**
	 * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters
	 * (U+0000 through U+001F). It's the same as JSONValue.escape() only for
	 * compatibility here.
	 * 
	 * @see com.maxtk.json.JSONValue#escape(String)
	 * 
	 * @param s
	 * @return
	 */
	public static String escape(String s) {
		return JSONValue.escape(s);
	}
}
