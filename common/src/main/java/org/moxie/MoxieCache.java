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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MoxieCache implements IMavenCache {

	final File moxieRoot;
	final File moxiedataRoot;
	final File localReleasesRoot;
	final File localSnapshotsRoot;
	final File localRoot;
	final File remoteRoot;
	final IMavenCache dotM2Cache;

	public MoxieCache() {
		this(new File(System.getProperty("user.home") + "/.moxie"));
	}
	
	public MoxieCache(File moxieRoot) {
		this.moxieRoot = moxieRoot;
		this.moxiedataRoot = new File(moxieRoot, "data");
		this.localRoot = new File(moxieRoot, Constants.LOCAL);
		this.remoteRoot = new File(moxieRoot, Constants.REMOTE);

		this.localReleasesRoot = new File(localRoot, "releases");
		this.localSnapshotsRoot = new File(localRoot, "snapshots");
		
		this.dotM2Cache = new MavenCache(new File(System.getProperty("user.home") + "/.m2/repository"));
		
		// intial folder creation
		remoteRoot.mkdirs();
		localReleasesRoot.mkdirs();
		localSnapshotsRoot.mkdirs();
	}
	
	public File getMoxieRoot() {
		return moxieRoot;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#getFiles(java.lang.String)
	 */
	@Override
	public List<File> getFiles(String extension) {
		List<File> list = new ArrayList<File>();
		return list;
	}

	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#getArtifact(org.moxie.Dependency, java.lang.String)
	 */
	@Override
	public File getArtifact(Dependency dep, String ext) {
		File baseFolder = localReleasesRoot;
		Dependency dcopy = DeepCopier.copy(dep);
		if (dcopy.isSnapshot()) {
			// in artifact cache we store as a.b-SNAPSHOT
			// not a.b.20120618.134509-5
			dcopy.revision = null;
			baseFolder = localSnapshotsRoot;
		}

		String path = Dependency.getMavenPath(dcopy, ext, Constants.MAVEN2_PATTERN);
	
		File moxieFile = new File(baseFolder, path);

		if (!moxieFile.exists()) {
			// search in downloaded artifact folders
			File downloaded = findDownloadedArtifact(path);
			if (downloaded != null) {
				// found a downloaded file
				moxieFile = downloaded;
			} else {
				// look in .m2/repository
				File mavenFile = dotM2Cache.getArtifact(dep, ext);
				if (mavenFile.exists()) {
					// transparently copy from Maven cache to Moxie cache
					try {
						FileUtils.copy(moxieFile.getParentFile(), mavenFile);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return moxieFile;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#getMetadata(org.moxie.Dependency, java.lang.String)
	 */
	@Override
	public File getMetadata(Dependency dep, String ext) {
		File baseFolder = localReleasesRoot;
		String pattern = Constants.MAVEN2_METADATA_PATTERN;
		if (dep.isSnapshot()) {
			baseFolder = localSnapshotsRoot;
			pattern = Constants.MAVEN2_SNAPSHOT_PATTERN;
		}
		String path = Dependency.getMavenPath(dep,  ext, pattern);
		
		File moxieFile = new File(baseFolder, path);
		
		if (!moxieFile.exists()) {
			// search in downloaded artifact folders
			File downloaded = findDownloadedArtifact(path);
			if (downloaded != null) {
				// found a downloaded file
				moxieFile = downloaded;
			} else {
				// look in .m2/repository
				File mavenFile = dotM2Cache.getMetadata(dep, ext);
				if (mavenFile.exists()) {
					// transparently copy from Maven cache to Moxie cache
					try {
						FileUtils.copy(moxieFile.getParentFile(), mavenFile);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return moxieFile;
	}
	
	// Check the downloaded atifacts for the requested artifact
	protected File findDownloadedArtifact(String path) {
		for (File repository : remoteRoot.listFiles()) {
			if (repository.isDirectory()) {
				File file = new File(repository, path);
				if (file.exists()) {
					return file;
				}
			}
		}
		return null;
	}
	
	protected File getMoxieDataFile(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}
		
		String path = Dependency.getMavenPath(dep, "moxie", Constants.MAVEN2_PATTERN);
		// artifactId-version.moxie
		File moxieFile = new File(moxiedataRoot, path);
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

	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeArtifact(org.moxie.Dependency, java.lang.String, java.lang.String)
	 */
	@Override
	public File writeArtifact(Dependency dep, String ext, String content) {
		byte [] bytes = null;
		try {
			bytes = content.getBytes("UTF-8");			
		} catch (UnsupportedEncodingException e) {
			bytes = content.getBytes();
		}
		return writeArtifact(dep, ext, bytes);
	}

	@Override
	public File writeArtifact(Dependency dep, String ext, byte[] content) {		
		File file;
		if (StringUtils.isEmpty(dep.origin)) {
			// local artifact because origin is undefined
			file = getArtifact(dep, ext);
		} else {
			// downloaded artifact
			String folder = StringUtils.urlToFolder(dep.origin);
			File repositoryRoot = new File(remoteRoot, folder);
			String path = Dependency.getMavenPath(dep, ext, Constants.MAVEN2_PATTERN);
			file = new File(repositoryRoot, path);			
		}
		FileUtils.writeContent(file, content);
		return file;
	}

	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeMetadata(org.moxie.Dependency, java.lang.String, java.lang.String)
	 */
	@Override
	public File writeMetadata(Dependency dep, String ext, String content) {
		byte [] bytes = null;
		try {
			bytes = content.getBytes("UTF-8");			
		} catch (UnsupportedEncodingException e) {
			bytes = content.getBytes();
		}
		return writeMetadata(dep, ext, bytes);
	}

	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#writeMetadata(org.moxie.Dependency, java.lang.String, byte[])
	 */
	@Override
	public File writeMetadata(Dependency dep, String ext, byte[] content) {
		File file;
		if (StringUtils.isEmpty(dep.origin)) {
			// local metadata because origin is undefined
			file = getMetadata(dep, ext);
		} else {
			// downloaded metadata
			String folder = StringUtils.urlToFolder(dep.origin);
			File repositoryRoot = new File(remoteRoot, folder);
			String path = Dependency.getMavenPath(dep, ext, dep.isSnapshot() ? Constants.MAVEN2_SNAPSHOT_PATTERN : Constants.MAVEN2_METADATA_PATTERN);
			file = new File(repositoryRoot, path);			
		}
		FileUtils.writeContent(file, content);
		return file;
	}	
}
