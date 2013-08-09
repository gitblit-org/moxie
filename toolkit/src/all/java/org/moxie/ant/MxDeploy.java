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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.tools.ant.types.FileSet;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.IMavenCache;
import org.moxie.Metadata;
import org.moxie.MetadataReader;
import org.moxie.Pom;
import org.moxie.PurgePolicy;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MxDeploy extends MxRepositoryTask {
	
	private int revisionRetentionCount = -1;
	private int revisionPurgeAfterDays = -1;
	private boolean generateIndexPage;
	private List<String> tags;
	private String artifactId;
	private String name;
	private String description;
	
	public MxDeploy() {
		super();
		setTaskName("mx:deploy");
		setAllowsnapshots(false);
		setGeneratePom(true);
		setCalculatechecksums(true);
		setGenerateIndexPage(true);
	}
	
	public void setRevisionRetentionCount(int value) {
		this.revisionRetentionCount = value;
	}

	public void setRevisionPurgeAfterDays(int value) {
		this.revisionPurgeAfterDays = value;
	}

	public void setGenerateIndexPage(boolean value) {
		this.generateIndexPage = value;
	}
	
	public void setTags(String tags) {
		if (tags != null && tags.length() > 0) {
			Set<String> set = new LinkedHashSet<String>();
			StringTokenizer tok = new StringTokenizer(tags, ", ", false);
			while (tok.hasMoreTokens()) {
				set.add(tok.nextToken().toLowerCase());
			}
			this.tags = new ArrayList<String>(set);
		}		
	}
	
	public void setArtifactid(String value) {
		this.artifactId = value;
	}
	
	public void setName(String value) {
		this.name = value;
	}

	public void setDescription(String value) {
		this.description = value;
	}

	protected PurgePolicy getPurgePolicy() {
		PurgePolicy policy;
		if (baseDir == null) {
			// deploy to local repository
			policy = getBuild().getConfig().getPurgePolicy();
		} else {
			// deploy to specified repository
			policy = new PurgePolicy();
			policy.retentionCount = 1;
		}

		// override purge policy
		if (revisionRetentionCount > -1) {
			policy.retentionCount = revisionRetentionCount;
		}

		if (revisionPurgeAfterDays > -1) {
			policy.purgeAfterDays = revisionPurgeAfterDays;
		}
		return policy;
	}

	public void execute() {
		Build build = getBuild();
		
		Pom pom = build.getPom(tags);
		
		if (!StringUtils.isEmpty(name)) {
			pom.name = name;
		}
		if (!StringUtils.isEmpty(description)) {
			pom.description = description;
		}
		if (!StringUtils.isEmpty(artifactId)) {
			pom.artifactId = artifactId;
		}
		
		if (!allowSnapshots && pom.isSnapshot()) {
			// do not deploy snapshots into the repository
			return;
		}
		
		Dependency asDependency = new Dependency(pom.getCoordinates());
		IMavenCache artifactCache = getArtifactCache(pom.isSnapshot());
		File cacheRoot = artifactCache.getRootFolder();
		File artifactFile = artifactCache.getArtifact(asDependency, asDependency.extension);
		File artifactDir = artifactFile.getParentFile();
		File sourceDir = build.getConfig().getTargetDirectory();
		
		titleClass(pom.artifactId + "-" + pom.version);

		if (asDependency.isSnapshot()) {
			deploySnapshot(pom, sourceDir, artifactDir, artifactCache);
		} else {
			deployRelease(pom, sourceDir, artifactDir, true);
		}
		
		// updates the prefixes index
		artifactCache.updatePrefixesIndex();

		if (generateIndexPage) {
			boolean extracted = extractResource(cacheRoot, "maven/index.html", "index.html", false);			
			extractResource(cacheRoot, "maven/favicon.png", "favicon.png", false);
			
			if (extracted) {
				// create JSON repository metadata
				String template = readResourceAsString("maven/repository.json").trim();
				template = getProject().replaceProperties(template);
				FileUtils.writeContent(new File(cacheRoot, "repository.json"), template);
			}
			
			// create/update JSON artifact index
			String template = readResourceAsString("maven/artifact.json").trim();
			StringBuilder sb = new StringBuilder("[\n\n");
			String index = artifactCache.generatePomIndex(template, ",\n");
			sb.append(index);
			sb.append("\n]\n");
			FileUtils.writeContent(new File(cacheRoot, "artifacts.json"), sb.toString());
		}		
	}
	
	/**
	 * Deploy SNAPSHOT artifacts
	 * 
	 * @param pom
	 * @param sourceDir
	 * @param artifactDir
	 * @param artifactCache
	 */
	protected void deploySnapshot(Pom pom, File sourceDir, File artifactDir, IMavenCache artifactCache) {
		Dependency asDependency = new Dependency(pom.getCoordinates());

		// setup REVISION
		String timestamp = new SimpleDateFormat(Metadata.snapshotTimestamp).format(new Date());
		int buildNumber = 1;

		File metadataFile = new File(artifactDir, "maven-metadata.xml");			
		if (metadataFile.exists()) {
			Metadata metadata = MetadataReader.readMetadata(metadataFile);
			buildNumber = metadata.getLastBuildNumber() + 1;
		}

		String rev = timestamp + "-" + buildNumber;
		// revision is x.y.z-TIMESTAMP-BUILDNUMBER
		asDependency.revision = pom.version.replace("SNAPSHOT", rev);

		String revisionArtifact = pom.artifactId + "-" + asDependency.revision;
		getConsole().log("deploying SNAPSHOT as {0}", revisionArtifact);

		// copy & rename -SNAPSHOT to -TIMESTAMP-BUILD
		FileSet fs = new FileSet();
		fs.setProject(getProject());
		fs.setDir(sourceDir);
		fs.setIncludes(pom.artifactId + "-" + pom.version + "*");

		for (String name : fs.getDirectoryScanner().getIncludedFiles()) {
			String targetName = name.replace("SNAPSHOT", rev);
			copyFile(new File(sourceDir, name), new File(artifactDir, targetName));
		}

		// output pom file
		generatePom(artifactDir, pom, revisionArtifact);

		// calculate checksums
		calculateChecksums(artifactDir, revisionArtifact + "*");

		// update SNAPSHOT metadata and purge obsolete artifacts
		PurgePolicy policy = getPurgePolicy();
		updateSnapshotMetadata(artifactDir, asDependency, policy);
		
		// update ARTIFACTS metadata
		updateArtifactsMetadata(artifactDir, asDependency);
	}	
}
