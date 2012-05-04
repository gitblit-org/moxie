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
package com.maxtk.ant;

import java.io.File;

import org.apache.tools.ant.Task;

import com.maxtk.Console;
import com.maxtk.Constants.Key;
import com.maxtk.utils.StringUtils;

public abstract class MaxTask extends Task {

	protected Console console = new Console();

	protected boolean verbose = true;

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	protected void setProperty(Key prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop.maxId(), value);
			log(prop.maxId(), value, false);
		}
	}

	protected void setProperty(String prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop, value);
			log(prop, value, false);
		}
	}

	protected void addReference(Key prop, Object obj, boolean split) {
		getProject().addReference(prop.maxId(), obj);
		log(prop.maxId(), obj.toString(), split);
	}
	
	protected void log(String key, String value, boolean split) {
		if (verbose) {
			int indent = 22;
			if (split) {
				String [] paths = value.split(File.pathSeparator);
				console.key(StringUtils.leftPad(key, indent, ' '), paths[0]);
				for (int i = 1; i < paths.length; i++) {
					console.key(StringUtils.leftPad("", indent, ' '), paths[i]);	
				}
			} else {
				console.key(StringUtils.leftPad(key, indent, ' '), value);
			}
		}
	}
}
