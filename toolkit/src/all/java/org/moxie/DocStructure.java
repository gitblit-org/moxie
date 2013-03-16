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

public class DocStructure extends DocElement implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;
	public List<DocElement> elements;	
	
	public DocStructure() {
		elements = new ArrayList<DocElement>();
	}

	public void setName(String name) {
		this.name = name;
	}

	public DocPage createPage() {
		DocPage page = new DocPage();
		elements.add(page);
		return page;
	}

	public DocMenu createMenu() {
		DocMenu menu = new DocMenu();
		elements.add(menu);
		return menu;
	}
	
	public DocReport createReport() {
		DocReport report = new DocReport();
		elements.add(report);
		return report;
	}

	public DocDivider createDivider() {
		DocDivider div = new DocDivider();
		elements.add(div);
		return div;
	}

	public DocLink createLink() {
		DocLink link = new DocLink();
		elements.add(link);
		return link;
	}
}
