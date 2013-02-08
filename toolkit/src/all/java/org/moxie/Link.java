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
package org.moxie;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Link implements Serializable {

	private static final long serialVersionUID = 1L;

	String name;
	String src;
	String as;
	List<Link> sublinks;	
	String content;

	boolean isLink;
	boolean isPage;
	boolean isMenu;
	boolean isReport;	
	boolean isDivider;
	boolean showToc;
	boolean showHeaderLinks;
	boolean isFluidLayout;

	public void setName(String name) {
		this.name = name;
	}

	public void setSrc(String src) {
		this.src = src;
	}

	public void setAs(String as) {
		this.as = as;
	}

	public void setOut(String as) {
		this.as = as;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setToc(boolean value) {
		this.showToc = value;
	}

	public void setHeaderlinks(boolean value) {
		this.showHeaderLinks = value;
	}

	public void setFluidlayout(boolean value) {
		this.isFluidLayout = value;
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
	
	public Link createReport() {
		Link link = newLink();
		link.isReport = true;
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
