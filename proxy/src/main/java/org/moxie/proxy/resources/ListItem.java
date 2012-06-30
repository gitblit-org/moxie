package org.moxie.proxy.resources;

import java.io.Serializable;

public class ListItem implements Serializable {

	private static final long serialVersionUID = 1L;

	final String name;
	final String path;
	String size;
	String date;

	ListItem(String name, String path) {
		this.name = name;
		this.path = path;
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
}