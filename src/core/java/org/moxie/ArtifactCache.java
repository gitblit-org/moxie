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

import org.moxie.utils.FileUtils;


public class ArtifactCache {

	final File root;
	final String pattern;
	final String metadataPattern;
	
	public ArtifactCache(File root) {
		this(root, Constants.MAVEN2_PATTERN, Constants.MAVEN2_METADATA_PATTERN);
	}
	
	public ArtifactCache(File root, String pattern, String metadataPattern) {
		this.root = root;
		this.pattern = pattern;
		this.metadataPattern = metadataPattern;
	}
	
	public File getArtifact(Dependency dep, String ext) {
		String path = Dependency.getMavenPath(dep,  ext, pattern);
		return new File(root, path);
	}

	public File writeArtifact(Dependency dep, String ext, String content) {
		File file = getArtifact(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	public File writeArtifact(Dependency dep, String ext, byte [] content) {
		File file = getArtifact(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	public File getMetadata(Dependency dep, String ext) {
		String path = Dependency.getMavenPath(dep,  ext, metadataPattern);
		return new File(root, path);
	}

	public File writeMetadata(Dependency dep, String ext, String content) {
		File file = getMetadata(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}

	public File writeMetadata(Dependency dep, String ext, byte [] content) {
		File file = getMetadata(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	public File getSolution(Dependency dep) {
		return null;
	}
	
	public File writeSolution(Dependency dep, String content) {
		File file = getSolution(dep);
		FileUtils.writeContent(file, content);
		return file;
	}
}
