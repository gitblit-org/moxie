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

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;


public class MoxieCache extends MavenCache {

	final File dataRoot;
	final File snapshotsRoot;
	final MavenCache mavenCache;

	public MoxieCache() {
		this(new File(System.getProperty("user.home") + "/.moxie"));
	}
	
	public MoxieCache(File moxieRoot) {
		super(new File(moxieRoot, "releases"));
		dataRoot = new File(moxieRoot, "data");
		snapshotsRoot = new File(moxieRoot, "snapshots");
		
		mavenCache = new MavenCache(new File(System.getProperty("user.home") + "/.m2/repository"));
	}
	
	@Override
	public File getArtifact(Dependency dep, String ext) {
		File baseFolder = root;
		Dependency dcopy = DeepCopier.copy(dep);
		if (dcopy.isSnapshot()) {
			// in artifact cache we store as a.b-SNAPSHOT
			// not a.b.20120618.134509-5
			dcopy.revision = null;
			baseFolder = snapshotsRoot;
		}

		String path = Dependency.getMavenPath(dcopy, ext, pattern);
	
		File moxieFile = new File(baseFolder, path);
		File mavenFile = mavenCache.getArtifact(dep, ext);
		
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
	public File getMetadata(Dependency dep, String ext) {
		File baseFolder = root;
		String pattern = metadataPattern;
		if (dep.isSnapshot()) {
			baseFolder = snapshotsRoot;
			pattern = snapshotPattern;
		}
		String path = Dependency.getMavenPath(dep,  ext, pattern);
		
		File moxieFile = new File(baseFolder, path);
		File mavenFile = mavenCache.getMetadata(dep, ext);
		
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
	
	protected File getMoxieDataFile(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}
		
		String path = Dependency.getMavenPath(dep, "moxie", pattern);
		// artifactId-version.moxie
		File moxieFile = new File(dataRoot, path);
		// metadata.moxie
		moxieFile = new File(moxieFile.getParentFile(), "metadata.moxie");
		return moxieFile;
	}
	
	public MoxieData readMoxieData(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}

		File moxieFile = getMoxieDataFile(dep);
		MoxieData moxiedata = new MoxieData(moxieFile);
		moxiedata.setArtifact(dep);
		return moxiedata;
	}
	
	public File writeMoxieData(Dependency dep, MoxieData moxiedata) {
		File file = getMoxieDataFile(dep);
		FileUtils.writeContent(file, moxiedata.toMaxML());
		return file;
	}	
}
