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
import com.maxtk.Dependency;
import com.maxtk.Pom;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class MxInstall extends MxTask {
	
	public void execute() {
		Build build = getBuild();
		
		File sourceFolder = build.getTargetFolder();
		
		Pom pom = build.getPom();
		String artifact = pom.artifactId + "-" + pom.version;
		String pattern = artifact + "*";
		
		Dependency asDependency = new Dependency(pom.getCoordinates());
		File maxillaFile = build.getArtifactCache().getFile(asDependency, asDependency.type);
		File destinationFolder = maxillaFile.getParentFile();
		
		build.console.title(getClass(), artifact);

		FileSet sourceFileset = new FileSet();
		sourceFileset.setProject(getProject());
		sourceFileset.setDir(sourceFolder);
		sourceFileset.setIncludes(pattern);
		
		// copy artifacts
		Copy copy = new Copy();
		copy.setTaskName(getTaskName());
		copy.setProject(getProject());
		copy.setTodir(destinationFolder);
		copy.setVerbose(isVerbose());
		copy.add(sourceFileset);
		copy.execute();

		// output pom file
		build.console.log("generating pom for {0}", artifact);
		File pomFile = new File(destinationFolder, artifact + ".pom");
		FileUtils.writeContent(pomFile, pom.toXML());
		build.console.debug(1, "wrote {0}", pomFile);
		
		// calculate checksums for each file
		build.console.log("calculating checksums for installed artifacts");
		FileSet repoSet = new FileSet();
		repoSet.setProject(getProject());
		repoSet.setDir(destinationFolder);
		repoSet.setIncludes(pattern);
		repoSet.setExcludes("*.sha1, *.md5, *.sig, *.asc");
		Iterator<?> itr = repoSet.iterator();
		while (itr.hasNext()) {
			FileResource file = (FileResource) itr.next();
			byte [] bytes = FileUtils.readContent(file.getFile());
			
			// calculate the SHA1 hash of the content and save result
			String sha1 = StringUtils.getSHA1(bytes);
			File sha1File = new File(destinationFolder, file.getFile().getName() + ".sha1");
			FileUtils.writeContent(sha1File, sha1);
			build.console.debug(1, "wrote {0}", sha1File);

			// calculate the MD5 hash of the content and save result
			String md5 = StringUtils.getMD5(bytes);
			File md5File = new File(destinationFolder, file.getFile().getName() + ".md5");
			FileUtils.writeContent(md5File, md5);
			build.console.debug(1, "wrote {0}", md5File);
		}
	}
}
