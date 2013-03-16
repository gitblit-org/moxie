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

public class DocMenu extends DocStructure implements Serializable {

	private static final long serialVersionUID = 1L;

	public boolean showPager;
	public String pagerPlacement;
	public String pagerLayout;
	
	public DocMenu() {
		super();
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
}
