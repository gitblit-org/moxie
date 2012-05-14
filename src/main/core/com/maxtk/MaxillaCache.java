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
import java.io.FileNotFoundException;
import java.io.IOException;

import com.maxtk.utils.FileUtils;

public class MaxillaCache extends ArtifactCache {

	final ArtifactCache mavenCache;
	
	public MaxillaCache() {
		this(new File(System.getProperty("user.home") + "/.maxilla/repository"), new File(System.getProperty("user.home") + "/.m2/repository"));
	}
	
	public MaxillaCache(File maxillaRoot, File mavenRoot) {
		super(maxillaRoot, "${groupId}/${artifactId}/${version}/${artifactId}-${version}${ext}");
		mavenCache = new ArtifactCache(mavenRoot);
	}
	
	public File getFile(Dependency dep, String ext) {
		File mavenFile = mavenCache.getFile(dep, ext);
		File maxillaFile = new File(root, pattern.replace("${groupId}", dep.group.replace('/', '.')).replace("${artifactId}", dep.artifact).replace("${version}", dep.version).replace("${ext}", ext));
		if (!maxillaFile.exists() && mavenFile.exists()) {
			// transparently copy from Maven cache to Maxilla cache
			try {
				FileUtils.copy(maxillaFile.getParentFile(), mavenFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return maxillaFile;
	}
}
