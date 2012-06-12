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
import java.io.FileNotFoundException;
import java.io.IOException;

import org.moxie.utils.FileUtils;


public class MoxieCache extends ArtifactCache {

	final ArtifactCache mavenCache;
	final File dataRoot;
	
	public MoxieCache() {
		this(new File(System.getProperty("user.home") + "/.moxie"), new File(System.getProperty("user.home") + "/.m2/repository"));
	}
	
	public MoxieCache(File moxieRoot, File mavenRoot) {
		super(new File(moxieRoot, "repository"));
		mavenCache = new ArtifactCache(mavenRoot);
		dataRoot = new File(moxieRoot, "data");
	}
	
	@Override
	public File getFile(Dependency dep, String ext) {
		String path = Dependency.getMoxiePath(dep, ext, pattern);
	
		File moxieFile = new File(root, path);
		File mavenFile = mavenCache.getFile(dep, ext);
		
		if (!moxieFile.exists() && mavenFile.exists()) {
			// transparently copy from Maven cache to Moxie cache
			try {
				FileUtils.copy(moxieFile.getParentFile(), mavenFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return moxieFile;
	}
	
	@Override
	public File getSolution(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}

		String path = Dependency.getMoxiePath(dep, "maxml", pattern);
		File moxieFile = new File(dataRoot, path);
		return moxieFile;
	}
}
