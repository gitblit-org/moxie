/*
 * Copyright 2006 FangYidong<fangyidong@yahoo.com.cn>
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A JSON array. JSONObject supports java.util.List interface.
 * 
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONArray extends ArrayList<Object> implements List<Object>, JSONAware, JSONStreamAware {
	private static final long serialVersionUID = 3957988303675231981L;

	/**
	 * Encode a list into JSON text and write it to out. If this list is also a
	 * JSONStreamAware or a JSONAware, JSONStreamAware and JSONAware specific
	 * behaviours will be ignored at this top level.
	 * 
	 * @see com.maxtk.json.JSONValue#writeJSONString(Object, Writer)
	 * 
	 * @param list
	 * @param out
	 */
	public static void writeJSONString(List<?> list, Writer out) throws IOException {
		if (list == null) {
			out.write("null");
			return;
		}

		boolean first = true;

		out.write('[');
		for (Object value : list) {
			if (first)
				first = false;
			else
				out.write(',');

			if (value == null) {
				out.write("null");
				continue;
			}

			JSONValue.writeJSONString(value, out);
		}
		out.write(']');
	}

	public void writeJSONString(Writer out) throws IOException {
		writeJSONString(this, out);
	}

	/**
	 * Convert a list to JSON text. The result is a JSON array. If this list is
	 * also a JSONAware, JSONAware specific behaviours will be omitted at this
	 * top level.
	 * 
	 * @see com.maxtk.json.JSONValue#toJSONString(Object)
	 * 
	 * @param list
	 * @return JSON text, or "null" if list is null.
	 */
	public static String toJSONString(List<?> list) {
		if (list == null)
			return "null";

		boolean first = true;
		StringBuffer sb = new StringBuffer();
		Iterator<?> iter = list.iterator();

		sb.append('[');
		while (iter.hasNext()) {
			if (first)
				first = false;
			else
				sb.append(',');

			Object value = iter.next();
			if (value == null) {
				sb.append("null");
				continue;
			}
			sb.append(JSONValue.toJSONString(value));
		}
		sb.append(']');
		return sb.toString();
	}

	public String toJSONString() {
		return toJSONString(this);
	}

	public String toString() {
		return toJSONString();
	}

}
