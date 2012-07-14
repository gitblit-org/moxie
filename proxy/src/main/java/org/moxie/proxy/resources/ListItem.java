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
package org.moxie.proxy.resources;

import java.io.Serializable;

public class ListItem implements Serializable, Comparable<ListItem> {

	private static final long serialVersionUID = 1L;

	final String name;
	final String path;
	final boolean isDownload;
	boolean isDirectory;
	String size;
	String date;

	ListItem(String name, String path, boolean isDownload) {
		this.name = name;
		this.path = path;
		this.isDownload = isDownload;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public String getSize() {
		return size;
	}
	
	public String getDate() {
		return date;
	}
	
	public boolean isDownload() {
		return isDownload;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	@Override
	public int compareTo(ListItem arg0) {
		if (isDirectory && arg0.isDirectory) {
			// sort directories by name
			return name.compareTo(arg0.name);
		} else if (isDirectory) {
			// sort directories first
			return 0;
		} else if (arg0.isDirectory) {
			// sort directories first
			return 1;
		}
		// sort filenames
		return name.compareTo(arg0.name);
	}
}