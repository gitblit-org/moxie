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

import com.maxtk.Dependency.Scope;

/**
 * SourceFolder represents a scoped source folder.
 */
public class SourceFolder implements Serializable {

	private static final long serialVersionUID = 1L;

	public final File folder;
	public final Scope scope;

	public SourceFolder(File folder) {
		this(folder, Scope.compile);
	}
	
	public SourceFolder(File folder, Scope scope) {
		this.folder = folder;
		this.scope = scope;
	}
	
	public File getOutputFolder(File baseFolder) {
		return new File(baseFolder, scope.equals(Scope.compile) ? "classes":"test");
	}
	
	@Override
	public int hashCode() {
		return folder.hashCode();
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
		return folder + " (" + scope + ")";
	}		
}