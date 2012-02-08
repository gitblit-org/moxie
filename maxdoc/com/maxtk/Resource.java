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
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;

public class Resource {

	public List<FileSet> filesets = new ArrayList<FileSet>();

	public File file;

	public void setFile(File f) {
		if (filesets.size() > 0) {
			throw new BuildException("can't add 'file' - fileset already used");
		}
		file = f;
	}

	/**
	 * Gets the name of the resource.
	 * 
	 * @return Thr name
	 */
	public String getName() {
		return file.getName();
	}

	public FileSet createFileset() {
		if (file != null) {
			throw new BuildException("can't add Fileset - file already set");
		}
		FileSet set = new FileSet();
		filesets.add(set);
		return set;
	}
}
