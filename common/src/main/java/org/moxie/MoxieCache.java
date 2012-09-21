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
	
	protected Dependency resolveRevision(Dependency dependency) {
		if ((dependency.isSnapshot() && StringUtils.isEmpty(dependency.revision))
				|| dependency.version.equalsIgnoreCase(Constants.RELEASE)
				|| dependency.version.equalsIgnoreCase(Constants.LATEST)) {
			// Support SNAPSHOT, RELEASE and LATEST versions
			File metadataFile = getMetadata(dependency, Constants.XML);
			
			// read SNAPSHOT, LATEST, or RELEASE from metadata
			if (metadataFile != null && metadataFile.exists()) {
				Metadata metadata = MetadataReader.readMetadata(metadataFile);
				String version;
				String revision;
				if (Constants.RELEASE.equalsIgnoreCase(dependency.version)) {
					version = metadata.release;
					revision = version;
				} else if (Constants.LATEST.equalsIgnoreCase(dependency.version)) {
					version = metadata.latest;
					revision = version;
				} else {
					// SNAPSHOT
					version = dependency.version;
					revision = metadata.getSnapshotRevision();
				}
				
				dependency.version = version;
				dependency.revision = revision;
			}
		}

		// standard release
		return dependency;
	}

	/*
	 * (non-Javadoc)
	 * @see org.moxie.IMavenCache#getArtifact(org.moxie.Dependency, java.lang.String)
	 */
	@Override
	public File getArtifact(Dependency dep, String ext) {
		File baseFolder = localReleasesRoot;
		// clone the original dep object for special checks below
		Dependency original = DeepCopier.copy(dep);
		// resolve dependency version - this updates the shared instance
		resolveRevision(dep);
		if (dep.isSnapshot()) {
			baseFolder = localSnapshotsRoot;
		}

		String path = Dependency.getMavenPath(original, ext, Constants.MAVEN2_PATTERN);
		File moxieFile = new File(baseFolder, path);
		
		if (!moxieFile.exists() && original.isMetaVersion()) {
			// try fully qualified revision 
			path = Dependency.getMavenPath(dep, ext, Constants.MAVEN2_PATTERN);
			moxieFile = new File(baseFolder, path);
		}
		
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
		// Resolve a clone of the dependency so we do not change
		// the original object.  This is to resolve RELEASE and LATEST to a
		// numeric version.  SNAPSHOT revisions are not part of the Moxie
		// data filename and as such they are irrelevant for this lookup.
		Dependency copy = DeepCopier.copy(dep);
		resolveRevision(copy);		
		String path = Dependency.getMavenPath(copy, "moxie", Constants.MAVEN2_PATTERN);
		
		// create a teamp file instance so we can get artifact parent folder
		File moxieFile = new File(moxiedataRoot, path);
		
		// metadata.moxie
		return new File(moxieFile.getParentFile(), "metadata.moxie");
	}
	
	public MoxieData readMoxieData(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}

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
	
	public void purgeSnapshots(Dependency dep, PurgePolicy policy) {
		if (!dep.isSnapshot()) {
			return;
		}
		File metadataFile = getMetadata(dep, Constants.XML);
		if (metadataFile == null || !metadataFile.exists()) {
			return;
		}
		Metadata metadata = MetadataReader.readMetadata(metadataFile);
		List<String> purgedRevisions = metadata.purgeSnapshots(policy);
		if (purgedRevisions.size() > 0) {
			System.out.println("purging old snapshots " + dep.getCoordinates());
			for (String revision : purgedRevisions) {
				Dependency old = DeepCopier.copy(dep);
				old.revision = revision;
				purgeArtifacts(old, false);
			}
			// write purged metadata
			FileUtils.writeContent(metadataFile, metadata.toXML());

			// if this dependency has a parent, purge that too
			File pomFile = getArtifact(dep, Constants.POM);
			Pom pom = PomReader.readPom(this, pomFile);
			if (pom.hasParentDependency()) {
				Dependency parent = pom.getParentDependency();
				parent.setOrigin(dep.getOrigin());
				purgeSnapshots(parent, policy);
			}
		}
	}
	
	public void purgeArtifacts(Dependency dep, boolean includeDependencies) {
		String identifier = dep.version;
		if (dep.isSnapshot()) {
			identifier = dep.revision;
		}
		File artifact = getArtifact(dep, dep.type);
		File folder = artifact.getParentFile();
		if (folder == null || !folder.exists()) {
			System.out.println("   ! skipping non existent folder " + folder);
			return;
		}
		
		for (File file : folder.listFiles()) {
			if (file.isFile() && file.getName().contains(identifier)) {
				System.out.println("   - " + file.getName());
				file.delete();
			}
		}
	}
}
