/*
 * Copyright 2013 James Moger
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

public class DocPage extends DocElement implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;
	public String src;
	public String as;
	public String content;
	
	public DocPage prevPage;
	public DocPage nextPage;

	public boolean showToc;
	public boolean showHeaderLinks;
	public boolean isFluidLayout;
	
	public boolean showPager;
	public String pagerPlacement;
	public String pagerLayout;

	public boolean showNavbarLink = true;
	public boolean processSubstitutions = true;

	public List<Template> templates;

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

	public void setNavbarLink(boolean value) {
		this.showNavbarLink = value;
	}

	public void setToc(boolean value) {
		this.showToc = value;
	}

	public void setHeaderlinks(boolean value) {
		this.showHeaderLinks = value;
	}

	public void setProcessSubstitutions(boolean value) {
		this.processSubstitutions = value;
	}

	public void setPager(boolean value) {
		this.showPager = value;
	}
	
	public void setPagerplacement(String value) {
		pagerPlacement = value;
	}

	public void setPagerlayout(String value) {
		pagerLayout = value;
	}

	public void setFluidlayout(boolean value) {
		this.isFluidLayout = value;
	}
	
	public Template createTemplate() {
		Template template = new Template();
		if (templates == null) {
			templates = new ArrayList<Template>();
		}
		templates.add(template);
		return template;
	}

}
