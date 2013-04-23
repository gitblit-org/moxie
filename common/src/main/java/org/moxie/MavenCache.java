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
import java.util.ArrayList;
import java.util.List;

import org.moxie.utils.FileUtils;


public class MavenCache extends IMavenCache {

	final File root;
	
	public MavenCache(File root) {
		this.root = root;
	}
	
	public File getRootFolder() {
		return root;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#getFiles(java.lang.String)
	 */
	@Override
	public List<File> getFiles(String extension) {
		List<File> list = new ArrayList<File>();
		list.addAll(getFiles(root, extension));
		return list;
	}
	
	private List<File> getFiles(File folder, String extension) {
		List<File> matches = new ArrayList<File>();
		File [] files = folder.listFiles();
		if (files == null) {
			return matches;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				matches.addAll(getFiles(file, extension));
			} else if (file.getName().endsWith(extension)) {
				matches.add(file);
			}
		}
		return matches;
	}
	
	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#getArtifact(org.moxie.Dependency, java.lang.String)
	 */
	@Override
	public File getArtifact(Dependency dep, String ext) {
		if (dep instanceof SystemDependency) {
			return new File(((SystemDependency) dep).path);
		}
		resolveRevision(dep);
		String path = Dependency.getArtifactPath(dep,  ext, Constants.MAVEN2_ARTIFACT_PATTERN);
		return new File(root, path);
	}

	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeArtifact(org.moxie.Dependency, java.lang.String, java.lang.String)
	 */
	@Override
	public File writeArtifact(Dependency dep, String ext, String content) {
		File file = getArtifact(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeArtifact(org.moxie.Dependency, java.lang.String, byte[])
	 */
	@Override
	public File writeArtifact(Dependency dep, String ext, byte [] content) {
		File file = getArtifact(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}
	
	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#getMetadata(org.moxie.Dependency, java.lang.String)
	 */
	@Override
	public File getMetadata(Dependency dep, String ext) {
		String path = Dependency.getArtifactPath(dep,  ext, dep.isSnapshot() ? Constants.MAVEN2_SNAPSHOT_PATTERN : Constants.MAVEN2_METADATA_PATTERN);
		return new File(root, path);
	}

	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeMetadata(org.moxie.Dependency, java.lang.String, java.lang.String)
	 */
	@Override
	public File writeMetadata(Dependency dep, String ext, String content) {
		File file = getMetadata(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}

	/* (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeMetadata(org.moxie.Dependency, java.lang.String, byte[])
	 */
	@Override
	public File writeMetadata(Dependency dep, String ext, byte [] content) {
		File file = getMetadata(dep, ext);
		FileUtils.writeContent(file, content);
		return file;
	}	
}
