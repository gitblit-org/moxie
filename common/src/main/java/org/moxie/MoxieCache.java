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
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import org.moxie.Constants.MavenCacheStrategy;
import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MoxieCache extends IMavenCache {
	
	final File moxieRoot;
	final File moxiedataRoot;
	final File localReleasesRoot;
	final File localSnapshotsRoot;
	final File localRoot;
	final File remoteRoot;
	final IMavenCache dotM2Cache;
	MavenCacheStrategy m2Strategy;

	public MoxieCache(File moxieRoot) {
		this.moxieRoot = moxieRoot;
		this.moxiedataRoot = new File(moxieRoot, "data");
		this.localRoot = new File(moxieRoot, Constants.LOCAL);
		this.remoteRoot = new File(moxieRoot, Constants.REMOTE);

		this.localReleasesRoot = new File(localRoot, "releases");
		this.localSnapshotsRoot = new File(localRoot, "snapshots");
		
		this.dotM2Cache = new MavenCache(new File(System.getProperty("user.home") + "/.m2/repository"));
		
		// initial folder creation
		moxieRoot.mkdirs();
		moxiedataRoot.mkdirs();
		localRoot.mkdirs();
		remoteRoot.mkdirs();
		localReleasesRoot.mkdirs();
		localSnapshotsRoot.mkdirs();
	}
	
	@Override
	public File getRootFolder() {
		return moxieRoot;
	}
	
	public void setMavenCacheStrategy(MavenCacheStrategy value) {
		m2Strategy = value;
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
		if (dep instanceof SystemDependency) {
			return new File(((SystemDependency) dep).path);
		}
		File baseFolder = localReleasesRoot;
		// clone the original dep object for special checks below
		Dependency original = DeepCopier.copy(dep);
		// resolve dependency version - this updates the shared instance
		resolveRevision(dep);
		if (dep.isSnapshot()) {
			baseFolder = localSnapshotsRoot;
		}

		String path;
		if (dep.isMavenObject()) {
			path = Dependency.getArtifactPath(original, ext, Constants.MAVEN2_ARTIFACT_PATTERN);
		} else {
			path = Dependency.getArtifactPath(original, ext, Constants.FORGE_ARTIFACT_PATTERN);
		}
		File moxieFile = new File(baseFolder, path);
		
		if (!moxieFile.exists() && original.isMetaVersion()) {
			// try fully qualified revision 
			path = Dependency.getArtifactPath(dep, ext, Constants.MAVEN2_ARTIFACT_PATTERN);
			moxieFile = new File(baseFolder, path);
		}
		
		if (!moxieFile.exists()) {
			// search in downloaded artifact folders
			File downloaded = findDownloadedArtifact(path);
			if (downloaded != null) {
				// found a downloaded file
				moxieFile = downloaded;
			} else if (MavenCacheStrategy.IGNORE != m2Strategy) {
				// look in .m2/repository
				File mavenFile = dotM2Cache.getArtifact(dep, ext);
				if (mavenFile.exists()) {
					if (MavenCacheStrategy.COPY == m2Strategy) {
						// transparently copy from Maven cache to Moxie cache
						try {
							FileUtils.copy(moxieFile.getParentFile(), mavenFile);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						// directly use artifact from Maven M2 repository
						moxieFile = mavenFile;
					}

					// update Moxie data with the M2 file:/ url origin
					Date now = new Date();
					MoxieData moxiedata = readMoxieData(dep);
					moxiedata.setOrigin(dotM2Cache.getRootFolder().toURI().toString());
					moxiedata.setLastDownloaded(now);
					moxiedata.setLastChecked(now);
					moxiedata.setLastUpdated(new Date(mavenFile.lastModified()));
					writeMoxieData(dep, moxiedata);
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
		String path = Dependency.getArtifactPath(dep,  ext, pattern);
		
		File moxieFile = new File(baseFolder, path);
		
		if (!moxieFile.exists()) {
			// search in downloaded artifact folders
			File downloaded = findDownloadedArtifact(path);
			if (downloaded != null) {
				// found a downloaded file
				moxieFile = downloaded;
			} else if (MavenCacheStrategy.IGNORE != m2Strategy) {
				// look in .m2/repository
				File mavenFile = dotM2Cache.getMetadata(dep, ext);
				if (mavenFile.exists()) {
					if (MavenCacheStrategy.COPY == m2Strategy) {
						// transparently copy from Maven cache to Moxie cache
						try {
							FileUtils.copy(moxieFile.getParentFile(), mavenFile);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						// directly use the Maven metadata file
						moxieFile = mavenFile;
					}
				}
			}
		}
		return moxieFile;
	}
	
	// Check the downloaded atifacts for the requested artifact
	protected File findDownloadedArtifact(String path) {
		File [] files = remoteRoot.listFiles();
		if (files == null) {
			return null;
		}
		for (File repository : files) {
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
		// Resolve a clone of the dependency so we do not change
		// the original object.  This is to resolve RELEASE and LATEST to a
		// numeric version.  SNAPSHOT revisions are not part of the Moxie
		// data filename and as such they are irrelevant for this lookup.
		Dependency copy = DeepCopier.copy(dep);
		resolveRevision(copy);
		
		String path;
		if (dep.isMavenObject()) {
			path = Dependency.getArtifactPath(copy, "moxie", Constants.MAVEN2_ARTIFACT_PATTERN);
		} else {
			path = Dependency.getArtifactPath(copy, dep.extension, Constants.FORGE_METADATA_PATTERN);
		}
		
		// create a temp file instance so we can get artifact parent folder
		File moxieFile = new File(moxiedataRoot, path);
		
		// metadata.moxie
		return new File(moxieFile.getParentFile(), "metadata.moxie");
	}
	
	public MoxieData readMoxieData(Dependency dep) {
		File moxieFile = getMoxieDataFile(dep);
		MoxieData moxiedata = new MoxieData(moxieFile);
		moxiedata.setArtifact(dep);
		dep.setOrigin(moxiedata.getOrigin());
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
			String path;
			if (dep.isMavenObject()) {
				path = Dependency.getArtifactPath(dep, ext, Constants.MAVEN2_ARTIFACT_PATTERN);
			} else {				
				path = Dependency.getArtifactPath(dep, ext, Constants.FORGE_ARTIFACT_PATTERN);
			}
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
			String path = Dependency.getArtifactPath(dep, ext, dep.isSnapshot() ? Constants.MAVEN2_SNAPSHOT_PATTERN : Constants.MAVEN2_METADATA_PATTERN);
			file = new File(repositoryRoot, path);			
		}
		FileUtils.writeContent(file, content);
		return file;
	}
	

	/**
	 * Creates/updates a prefixes index used by smart maven clients to do
	 * automatic dependency routing.
	 *  
	 * @return the index file
	 */
	@Override
	public File updatePrefixesIndex() {
		// generate a prefix index for each remote cache
		List<File> indexes = new ArrayList<File>();
		File [] remotes = remoteRoot.listFiles();
		if (remotes != null) {
			for (File remote : remotes) {
				if (remote.isDirectory()) {
					File index = updatePrefixesIndex(remote);
					indexes.add(index);
				}
			}
		}
		
		// generate a prefix index for the local releases and snapshots cache
		indexes.add(updatePrefixesIndex(localReleasesRoot));
		indexes.add(updatePrefixesIndex(localSnapshotsRoot));
		
		// merge all prefix indexes together
		Set<String> prefixes = new TreeSet<String>();
		for (File index : indexes) {
			Scanner scanner = null;
			try {
				scanner = new Scanner(index);
				while (scanner.hasNext()) {
					prefixes.add(scanner.next());
				}
			} catch (Exception e) {
			} finally {
				if (scanner != null) {
					scanner.close();
				}
			}
		}
		
		// create flat index content
		StringBuilder sb = new StringBuilder();
		for (String value : prefixes) {
			sb.append(value).append('\n');
		}
		
		File index = new File(moxiedataRoot, "prefixes.txt");
		FileUtils.writeContent(index, sb.toString());
		return index;
	}
}
