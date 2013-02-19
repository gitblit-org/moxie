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

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Set;
import java.util.TreeSet;

import org.moxie.utils.StringUtils;


/**
 * SourceDirectory represents a scoped source directory.
 */
public class SourceDirectory implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final Scope scope;
	private File sources;
	private File classes;
	public Set<String> tags;

	SourceDirectory(String name, Scope scope) {
		this.name = StringUtils.stripQuotes(name);
		this.scope = scope;
		tags = new TreeSet<String>();
	}
	
	boolean resolve(File projectDirectory, File outputDirectory) {
		sources = new File(projectDirectory, name);
		if (sources.exists()) {
			String dir = null;
			switch (scope) {
			case compile:
				dir = "classes";
				break;
			case test:
				dir = "test-classes";
				break;
			case site:
				dir = "site";
				break;
			default:
				break;
			}
			classes = new File(outputDirectory, dir);
			return true;
		}
		return false;
	}
	
	public File getSources() {
		if (sources == null) {
			throw new RuntimeException(MessageFormat.format("SourceDirectory {0} has not been resolved!", name));
		}
		return sources;
	}
	
	public File getOutputDirectory() {
		if (classes == null) {
			throw new RuntimeException(MessageFormat.format("SourceDirectory {0} has not been resolved!", name));
		}
		return classes;
	}
	
	@Override
	public int hashCode() {
		return getSources().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof SourceDirectory) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
	
	@Override
	public String toString() {
		return getSources() + " (" + scope + ")";
	}		
}