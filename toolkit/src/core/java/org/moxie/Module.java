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

import org.moxie.utils.StringUtils;


public class Module implements Comparable<Module> {

	final String folder;
	final String descriptor;
	final String script;
	final String target;
	
	public Module(String def) {
		if (def.indexOf('@') > -1) {
			// specifies descriptor
			this.folder = StringUtils.stripQuotes(def.substring(0, def.lastIndexOf('@')).trim());
			this.descriptor = def.substring(def.lastIndexOf('@') + 1);
		} else {
			// default descriptor
			this.folder = StringUtils.stripQuotes(def.trim());
			this.descriptor = "build.moxie";
		}
		this.script = "build.xml";
		this.target = null;
	}
	
	@Override
	public int compareTo(Module module) {
		return folder.toLowerCase().compareTo(module.folder.toLowerCase());
	}
}
