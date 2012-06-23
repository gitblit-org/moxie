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

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;


public class MavenCache {

	final File root;
	final String pattern;
	final String metadataPattern;
	final String snapshotPattern;
	
	public MavenCache(File root) {
		this.root = root;
		this.pattern = Constants.MAVEN2_PATTERN;
		this.metadataPattern = Constants.MAVEN2_METADATA_PATTERN;
		this.snapshotPattern = Constants.MAVEN2_SNAPSHOT_PATTERN;
	}
	
	public File getArtifact(Dependency dep, String ext) {
		Dependency dcopy = DeepCopier.copy(dep);
		if (dcopy.isSnapshot()) {
			// in artifact cache we store as a.b-SNAPSHOT
			// not a.b.20120618.134509-5
			dcopy.revision = null;
		}
		String path = Dependency.getMavenPath(dcopy,  ext, pattern);
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
		String path = Dependency.getMavenPath(dep,  ext, dep.isSnapshot() ? snapshotPattern : metadataPattern);
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
}
