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

import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.IMavenCache;
import org.moxie.Pom;
import org.moxie.PurgePolicy;
import org.moxie.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;


public class MxUpdateRepo extends MxRepositoryTask {

	private int revisionRetentionCount = -1;
	private int revisionPurgeAfterDays = -1;
	private boolean generateIndexPage = true;
	private boolean updatePrefixIndices = false;
	private boolean purgeSnapshots = false;
	private List<String> tags;

	public MxUpdateRepo() {
		super();
		setTaskName("mx:repoupdate");
		setGeneratePom(false);
		setCalculatechecksums(false);
		setGenerateIndexPage(true);
		setUpdatePrefixIndices(true);
		setPurgeSnapshots(false);
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

	public void setUpdatePrefixIndices(boolean value) {
		this.updatePrefixIndices = value;
	}

	public void setPurgeSnapshots(boolean value) {
		this.purgeSnapshots = value;
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
		IMavenCache artifactCache = getArtifactCache(purgeSnapshots);
		File cacheRoot = artifactCache.getRootFolder();

		if (purgeSnapshots) {
			Build build = getBuild();

			Pom pom = build.getPom(tags);

			if (!allowSnapshots && pom.isSnapshot()) {
				// do not deploy snapshots into the repository
				return;
			}


			Dependency asDependency = new Dependency(pom.getCoordinates());
			File artifactFile = artifactCache.getArtifact(asDependency, asDependency.extension);
			File artifactDir = artifactFile.getParentFile();
			purgeSnapshots(pom, artifactDir);
		}
		
		// updates the prefixes index
		if (updatePrefixIndices) {
			artifactCache.updatePrefixesIndex();
		}

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
	 * Purge SNAPSHOT artifacts
	 * 
	 * @param pom
	 * @param artifactDir
	 */
	protected void purgeSnapshots(Pom pom, File artifactDir) {
		Dependency asDependency = new Dependency(pom.getCoordinates());

		// update SNAPSHOT metadata and purge obsolete artifacts
		PurgePolicy policy = getPurgePolicy();
		updateSnapshotMetadata(artifactDir, asDependency, policy);
		
		// update ARTIFACTS metadata
		updateArtifactsMetadata(artifactDir, asDependency);
	}	
}
