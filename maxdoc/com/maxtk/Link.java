package com.maxtk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Link implements Serializable {

	private static final long serialVersionUID = 1L;

	String name;
	String src;
	List<Link> sublinks;
	
	boolean isLink;
	boolean isPage;
	boolean isMenu;
	boolean isDivider;

	public void setName(String name) {
		this.name = name;
	}
	
	public void setSrc(String src) {
		this.src = src;
	}
	
	public Link createPage() {
		Link link = newLink();
		link.isPage = true;
		return link;
	}
	
	public Link createMenu() {
		Link link = newLink();
		link.isMenu = true;
		return link;
	}

	public Link createDivider() {
		Link link = newLink();
		link.isDivider = true;
		return link;
	}

	public Link createLink() {
		Link link = newLink();
		link.isLink = true;
		return link;
	}
	
	private Link newLink() {
		Link link = new Link();
		if (sublinks == null) {
			sublinks = new ArrayList<Link>();
		}
		sublinks.add(link);
		return link;
	}
}
