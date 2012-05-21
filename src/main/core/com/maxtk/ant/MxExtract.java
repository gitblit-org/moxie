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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;

import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class MxExtract extends MxTask {

	String srcfile;

	String field;

	String property;

	public void setSrcfile(String file) {
		this.srcfile = file;
	}

	public void setField(String field) {
		this.field = field;
	}

	public void setProperty(String property) {
		this.property = property;
	}

	@Override
	public void execute() throws BuildException {
		if (StringUtils.isEmpty(srcfile)) {
			throw new BuildException("srcfile attribute is unspecified!");
		}
		if (StringUtils.isEmpty(field)) {
			throw new BuildException("field attribute is unspecified!");
		}
		if (StringUtils.isEmpty(property)) {
			throw new BuildException("property attribute is unspecified!");
		}
		File f = new File(srcfile);
		String content = FileUtils.readContent(f, "\n");
		String[] lines = content.split("\n");
		Pattern p = Pattern.compile(field + "\\s*=\\s*\"?(.*?)(\"?\\s*;)");
		for (String line : lines) {
			Matcher m = p.matcher(line);
			if (m.find()) {
				String value = m.group(1);
				getProject().setProperty(property, value);
				break;
			}
		}
	}
}
