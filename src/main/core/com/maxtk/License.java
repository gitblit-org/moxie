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

import java.io.Serializable;

/**
 * Represents a license.
 */
public class License implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final String url;

	License(String name, String url) {
		this.name = convert(name);
		this.url = url;
	}
	
	/**
	 * Try to identify and shorten license names.
	 * 
	 * @param name
	 * @return a name
	 */
	private String convert(String name) {
		String n = name.toLowerCase();
		String v = "";
		if (n.contains("version")) {
			v = "v" + n.substring(n.indexOf("version") + "version".length()).trim();
		}

		if (n.contains("apache")) {
			name = "Apache ASL";
		} else if (n.contains("eclipse")) {
			if (n.contains("distribution")) {
				name = "Eclipse EDL";
			}
			if (n.contains("public")) {
				name = "Eclipse EPL";
			}
		} else if (n.contains("common public license")) {
			name = "CPL";
		} else if (n.contains("gnu lesser general public license")) {
			name = "GNU LGPL";
		} else if (n.contains("gnu general public license")) {
			name = "GNU GPL";
		} else if (n.contains("gnu affero general public license")) {
			name = "GNU AGPL";
		} else if (n.contains("mit license")) {
			name = "MIT";
		} else {
			v = "";
		}
		return name + v;
	}
	
	@Override
	public String toString() {
		return name + " (" + url + ")";
	}		
}