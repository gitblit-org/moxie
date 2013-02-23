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
package org.moxie.ant;

import java.io.File;

import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.IMavenCache;
import org.moxie.Pom;


public class MxInstall extends MxRepositoryTask {
	
	public MxInstall() {
		super();
		setTaskName("mx:install");
		setAllowsnapshots(true);
		setGeneratePom(true);
		setCalculatechecksums(true);
	}
	
	public void execute() {
		Build build = getBuild();
		
		Pom pom = build.getPom();
		if (!allowSnapshots && pom.isSnapshot()) {
			// do not install snapshots into the repository
			return;
		}
		
		Dependency asDependency = new Dependency(pom.getCoordinates());
		IMavenCache cache = getArtifactCache(pom.isSnapshot());
		File artifactFile = cache.getArtifact(asDependency, asDependency.type);
		File artifactDir = artifactFile.getParentFile();
		File sourceDir = build.getConfig().getTargetDirectory();
		
		getConsole().title(getClass(), pom.artifactId + "-" + pom.version);

		deployRelease(pom, sourceDir, artifactDir, false);
	}
}
