/*
 * Copyright 2013 James Moger
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
package org.moxie.ant;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;
import org.moxie.Dependency;
import org.moxie.IMavenCache;
import org.moxie.MavenCache;
import org.moxie.Metadata;
import org.moxie.MetadataReader;
import org.moxie.MoxieException;
import org.moxie.Pom;
import org.moxie.PurgePolicy;
import org.moxie.Repository;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public abstract class MxRepositoryTask extends MxTask {
	
	protected File baseDir;
	
	protected String repositoryId;
	
	protected String refId;
	
	protected boolean allowSnapshots;
	
	protected boolean calculateChecksums;
	
	protected boolean generatePom;
	
	private Path installedArtifacts;
	
	public MxRepositoryTask() {
		super();		
	}
	
	public void setBaseDir(File dir) {
		this.baseDir = dir;
	}
	
	public File getBaseDir() {
		return baseDir;
	}
	
	public void setAllowsnapshots(boolean value) {
		allowSnapshots = value;
	}
	
	public void setCalculatechecksums(boolean value) {
		calculateChecksums = value;
	}
	
	public void setGeneratePom(boolean value) {
		generatePom = value;
	}
	
	public void setRepositoryId(String value) {
		repositoryId = value;
	}
	
	public void setPathId(String id) {
		installedArtifacts = new Path(getProject());
		getProject().addReference(id, installedArtifacts);
	}
	
	protected IMavenCache getArtifactCache(boolean isSnapshot) {
		IMavenCache cache;
		if (baseDir == null) {
			if (StringUtils.isEmpty(repositoryId)) {
				// return MoxieCache
				cache = getBuild().getSolver().getMoxieCache();
			} else {
				// get repository by identifier
				Repository repository = getBuild().getConfig().getRepository(repositoryId);
				if (repository == null) {
					throw new MoxieException("Failed to find repositoryId: {0}", repositoryId);
				}
				if (isSnapshot && !repository.allowSnapshots()) {
					if (allowSnapshots) {
						getConsole().warn("Repository \"{0}\" prohibits snapshots! Overridden by \"allowSnapshots\" attribute!", repository.toString());
					} else {
						throw new MoxieException("Repository \"{0}\" prohibits installation or deployment of snapshots!",
							repository.toString(), repository.getRepositoryUrl());
					}
				}
				String url = repository.getRepositoryUrl();
				if (url.startsWith("file:/")) {					
					try {
						URI uri = new URI(url);
						baseDir = new File(uri);
						cache = new MavenCache(baseDir);						
					} catch (URISyntaxException e) {
						throw new MoxieException("Failed to parse " + url, e);
					}
				} else {
					throw new MoxieException("Invalid url \"{0}\"! Moxie does not support installing or deploying artifacts to remote repositories!", url);
				}
			}			
		} else {
			// return MavenCache for specified directory
			cache = new MavenCache(baseDir);
		}		
		return cache;
	}
	
	/**
	 * Install release artifacts to the artifact directory.
	 * 
	 * @param pom
	 * @param sourceDir
	 * @param artifactDir
	 * @param optionally update metadata
	 */
	protected void deployRelease(Pom pom, File sourceDir, File artifactDir, boolean updateMetadata) {		
		String artifact = pom.artifactId + "-" + pom.version;
		String pattern = artifact + "*";
		Dependency asDependency = new Dependency(pom.getCoordinates());

		// copy artifacts
		copy(sourceDir, pattern, artifactDir);

		// output pom file
		generatePom(artifactDir, pom, artifact);

		// calculate checksums for each file
		calculateChecksums(artifactDir, pattern);

		// optionally update metadata
		if (updateMetadata) {
			updateArtifactsMetadata(artifactDir, asDependency);
		}
	}

	
	/**
	 * Copies files of a particular pattern from one location to another
	 * 
	 * @param fromDir
	 * @param pattern
	 * @param toDir
	 */
	protected void copy(File fromDir, String pattern, File toDir) {
		FileSet fs = new FileSet();
		fs.setProject(getProject());
		fs.setDir(fromDir);
		fs.setIncludes(pattern);
		
		Copy copy = new Copy();
		copy.setTaskName(getTaskName());
		copy.setProject(getProject());
		copy.setTodir(toDir);
		copy.setVerbose(isVerbose());
		copy.add(fs);
		copy.execute();
		
		// add files to installed artifacts path
		reference(fs);
	}
	
	/**
	 * Copies a single file to a destination file of a different name and/or
	 * location.
	 * 
	 * @param sourceFile
	 * @param targetFile
	 */
	protected void copyFile(File sourceFile, File targetFile) {
		Copy copy = new Copy();
		copy.setTaskName(getTaskName());
		copy.setProject(getProject());
		copy.setFile(sourceFile);
		copy.setTofile(targetFile);
		copy.setVerbose(isVerbose());
		copy.execute();
		
		// add target file to installed artifacts path
		reference(targetFile);
	}
	
	/**
	 * Generates a pom.xml file
	 * 
	 * @param dir
	 * @param pom
	 * @param artifact
	 */
	protected void generatePom(File dir, Pom pom, String artifact) {
		if (generatePom) {
			// output pom file
			getConsole().debug("generating pom");
			File pomFile = new File(dir, artifact + ".pom");
			FileUtils.writeContent(pomFile, pom.toXML(false));
			getConsole().debug(1, "wrote {0}", pomFile);

			// add pom file to installed artifacts path
			reference(pomFile);
		}
	}
	
	/**
	 * Updates the artifacts maven-metadata.xml with the current versions
	 * information.
	 * 
	 * e.g. org/moxie/toolkit/maven-metadata.xml
	 * 
	 * @param artifactDir
	 * @param dependency
	 */
	protected void updateArtifactsMetadata(File artifactDir, Dependency dependency) {
		// create/update ARTIFACTS metadata
		File artifactsDir = artifactDir.getParentFile();
		updateMetadata(artifactsDir, dependency, false);
		calculateChecksums(artifactsDir, "maven-metadata.xml");
	}
	
	/**
	 * Updates the snapshot maven-metadata.xml with the current snapshot revision
	 * and purges obsolete artifacts according to the purge policy.
	 * 
	 * @param artifactDir
	 * @param dependency
	 * @param policy
	 */
	protected void updateSnapshotMetadata(File artifactDir, Dependency dependency, PurgePolicy policy) {
		// create/update SNAPSHOT/maven-metadata.xml			
		updateMetadata(artifactDir, dependency, true);

		// purge old snapshots from SNAPSHOT/maven-metadata.xml		
		getArtifactCache(true).purgeSnapshots(dependency, policy);
		calculateChecksums(artifactDir, "maven-metadata.xml");
	}
	
	private void updateMetadata(File dir, Dependency dependency, boolean isSnapshotMetadata) {
		// create/update maven-metadata.xml
		File metadataFile = new File(dir, "maven-metadata.xml");
		Metadata oldMetadata = null;
		if (metadataFile.exists()) {
			oldMetadata = MetadataReader.readMetadata(metadataFile);
		}

		Metadata metadata = new Metadata(dependency, isSnapshotMetadata);
		if (oldMetadata != null) {
			metadata.merge(oldMetadata);
		}

		FileUtils.writeContent(metadataFile, metadata.toXML());
		metadataFile.setLastModified(metadata.lastUpdated.getTime());
		
		// add metadata file to installed artifacts path
		reference(metadataFile);
	}
	
	/**
	 * Calculates SHA1 and MD5 checksums for the files in the directory which
	 * match the specified pattern.
	 * 
	 * @param dir
	 * @param pattern
	 */
	protected void calculateChecksums(File dir, String pattern) {
		if (calculateChecksums) {
			getConsole().debug("calculating checksums for artifacts in {0}", dir);
			FileSet repoSet = new FileSet();
			repoSet.setProject(getProject());
			repoSet.setDir(dir);
			repoSet.setIncludes(pattern);
			repoSet.setExcludes("*.sha1, *.md5, *.sig, *.asc");
			Iterator<?> itr = repoSet.iterator();
			while (itr.hasNext()) {
				FileResource file = (FileResource) itr.next();
				byte [] bytes = FileUtils.readContent(file.getFile());

				// calculate the SHA1 hash of the content and save result
				String sha1 = StringUtils.getSHA1(bytes);
				File sha1File = new File(dir, file.getFile().getName() + ".sha1");
				FileUtils.writeContent(sha1File, sha1);
				getConsole().debug(1, "wrote {0}", sha1File);

				// add sha1 file to installed artifacts path
				reference(sha1File);

				// calculate the MD5 hash of the content and save result
				String md5 = StringUtils.getMD5(bytes);
				File md5File = new File(dir, file.getFile().getName() + ".md5");
				FileUtils.writeContent(md5File, md5);
				getConsole().debug(1, "wrote {0}", md5File);
				
				// add md5 file to installed artifacts path
				reference(md5File);
			}
		}
	}
	
	/**
	 * Add a reference to the installed/deployed file for processing in Ant.
	 * 
	 * @param file
	 */
	private void reference(File file) {
		if (installedArtifacts != null) {
			installedArtifacts.createPathElement().setLocation(file);
		}
	}
	
	/**
	 * Add a reference to the fileset for processing in Ant.
	 * 
	 * @param file
	 */
	private void reference(FileSet fs) {
		if (installedArtifacts != null) {
			installedArtifacts.addFileset(fs);
		}
	}
}
