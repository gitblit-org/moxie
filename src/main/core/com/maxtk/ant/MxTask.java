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

import com.maxtk.Build;
import com.maxtk.Constants;
import com.maxtk.Constants.Key;
import com.maxtk.console.Console;
import com.maxtk.utils.StringUtils;

public abstract class MxTask extends Task {

	protected Console console = new Console();

	private Boolean verbose;

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public boolean isVerbose() {
		if (verbose == null) {
			String mxvb = System.getProperty(Constants.MX_VERBOSE);
			if (StringUtils.isEmpty(mxvb)) {
				Build build = getBuild();
				if (build == null) {
					return false;
				} else {
					return build.isVerbose();
				}
			} else {
				verbose = Boolean.parseBoolean(mxvb);
			}
		}
		return verbose;
	}
	
	protected Build getBuild() {
		Build build = (Build) getProject().getReference(Key.build.refId());
		return build;
	}

	protected void setProperty(Key prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop.propId(), value);
			log(prop.propId(), value, false);
		}
	}

	protected void setProperty(String prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop, value);
			log(prop, value, false);
		}
	}

	protected void addReference(Key prop, Object obj, boolean split) {
		getProject().addReference(prop.refId(), obj);
		log(prop.refId(), obj.toString(), split);
	}
	
	protected void log(String key, String value, boolean split) {
		if (isVerbose()) {
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
