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


public class MxPackage extends MxTask {
	
	public MxPackage() {
		super();
		setTaskName("mx:package");
	}
	
	public void execute() {
		Build build = getBuild();
		
		// create jar or war
		MxJar task = null;
		if ("war".equals(build.getConfig().getPom().getExtension())) {
			task = new MxWar();
		} else {
			task = new MxJar();
		}
		task.setProject(getProject());
		task.setPackagesources(true);
		task.execute();
		
		// create javadoc
		MxJavadoc javadoc = new MxJavadoc();
		javadoc.setProject(getProject());
		javadoc.setRedirect(true);
		javadoc.execute();
		
		if ("zip".equals(build.getConfig().getPom().getExtension())) {
			// create zip of artifacts
			MxZip zip = new MxZip();
			zip.setProject(getProject());
			zip.createArtifact();
			zip.createArtifact().setClassifier("sources");
			zip.createArtifact().setClassifier("javadoc");
			zip.execute();
		}
	}
}
