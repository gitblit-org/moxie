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
package com.maxtk;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;

import com.maxtk.Scope;

/**
 * SourceFolder represents a scoped source folder.
 */
public class SourceFolder implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final Scope scope;
	private File sources;
	private File classes;

	SourceFolder(String name, Scope scope) {
		this.name = name;
		this.scope = scope;
	}
	
	boolean resolve(File projectFolder, File outputFolder) {
		sources = new File(projectFolder, name);
		if (sources.exists()) {
			classes = new File(outputFolder, scope.equals(Scope.compile) ? "classes":"test-classes");
			return true;
		}
		return false;
	}
	
	public File getSources() {
		if (sources == null) {
			throw new RuntimeException(MessageFormat.format("SourceFolder {0} has not been resolved!", name));
		}
		return sources;
	}
	
	public File getOutputFolder() {
		if (classes == null) {
			throw new RuntimeException(MessageFormat.format("SourceFolder {0} has not been resolved!", name));
		}
		return classes;
	}
	
	@Override
	public int hashCode() {
		return getSources().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof SourceFolder) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
	
	@Override
	public String toString() {
		return getSources() + " (" + scope + ")";
	}		
}