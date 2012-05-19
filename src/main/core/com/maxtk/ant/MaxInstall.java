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
package com.maxtk.ant;

import java.io.File;
import java.util.Iterator;

import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.Dependency;
import com.maxtk.Pom;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class MaxInstall extends MaxTask {
	
	Boolean verbose;
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		
		if (verbose == null) {
			verbose = build.isVerbose();
		}
		
		File sourceFolder = build.getTargetFolder();
		
		Pom pom = build.getPom();
		String artifact = pom.artifactId + "-" + pom.version;
		String pattern = artifact + "*";
		
		Dependency asDependency = new Dependency(pom.getCoordinates());
		File maxillaFile = build.getArtifactCache().getFile(asDependency, asDependency.type);
		File destinationFolder = maxillaFile.getParentFile();
		
		build.console.title(getClass().getSimpleName(), artifact);

		FileSet sourceFileset = new FileSet();
		sourceFileset.setProject(getProject());
		sourceFileset.setDir(sourceFolder);
		sourceFileset.setIncludes(pattern);
		
		// copy artifacts
		Copy copy = new Copy();
		copy.setTaskName(getTaskName());
		copy.setProject(getProject());
		copy.setTodir(destinationFolder);
		copy.setVerbose(verbose);
		copy.add(sourceFileset);
		copy.execute();

		// output pom file
		build.console.log("generating pom for {0}", artifact);
		File pomFile = new File(destinationFolder, artifact + ".pom");
		FileUtils.writeContent(pomFile, pom.toXML());
		build.console.debug(1, "wrote {0}", pomFile);
		
		// calculate SHA1 values for each file
		build.console.log("calculating SHA1 hashes for installed artifacts");
		FileSet repoSet = new FileSet();
		repoSet.setProject(getProject());
		repoSet.setDir(destinationFolder);
		repoSet.setIncludes(pattern);
		Iterator<?> itr = repoSet.iterator();
		while (itr.hasNext()) {
			FileResource file = (FileResource) itr.next();
			byte [] bytes = FileUtils.readContent(file.getFile());
			String sha1 = StringUtils.getSHA1(bytes);
			File hashFile = new File(destinationFolder, file.getFile().getName() + ".sha1");
			FileUtils.writeContent(hashFile, sha1);
			build.console.debug(1, "wrote {0}", hashFile);
		}
	}
}
