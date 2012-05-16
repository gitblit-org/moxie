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

import com.maxtk.utils.FileUtils;

public class ArtifactCache {

	final File root;
	final String pattern;
	
	public ArtifactCache(File root) {
		this(root, Constants.MAVEN2_PATTERN);
	}
	
	public ArtifactCache(File root, String pattern) {
		this.root = root;
		this.pattern = pattern;
	}
	
	public File getFile(Dependency dep, String ext) {
		String path = Dependency.getMavenPath(dep,  ext, pattern);
		return new File(root, path);
	}
	
	public File writeFile(Dependency dep, String ext, String content) {
		File file = getFile(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	public File writeFile(Dependency dep, String ext, byte [] content) {
		File file = getFile(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}	
}
