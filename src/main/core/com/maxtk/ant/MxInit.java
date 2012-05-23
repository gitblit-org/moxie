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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.Pom;
import com.maxtk.Scope;
import com.maxtk.utils.StringUtils;

public class MxInit extends MxTask {

	private String config;
	
	public void setConfig(String config) {
		this.config = config;
	}

	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		if (build != null) {
			// already initialized
			return;
		}
		try {
			if (StringUtils.isEmpty(config)) {
				// default configuration
				build = new Build(new File("build.maxml"));
			} else {
				// specified configuration
				build = new Build(new File(config));
			}
			Map<String,String> antProperties = getProject().getProperties();
			build.getPom().setAntProperties(antProperties);			

			// add a reference to the full build object
			addReference(Key.build, build, false);
			
			//setProperty("project.name", build.getPom().name);
			
			// output the build info
			build.describe();
			
			build.setup(verbose);
			
			console = build.console;
			if (verbose) {
				build.console.separator();
				build.console.log(getProject().getProperty("ant.version"));
				build.console.log("Maxilla ant properties", getProject().getProperty("ant.version"));
			}

			Pom pom = build.getPom();
			
			if (verbose) {
				build.console.separator();
				build.console.log("string properties");
			}
			
			setProperty(Key.name, pom.name);
			setProperty(Key.description, pom.description);
			setProperty(Key.groupId, pom.groupId);
			setProperty(Key.artifactId, pom.artifactId);
			setProperty(Key.version, pom.version);
			setProperty(Key.vendor, pom.vendor);
			setProperty(Key.url, pom.url);

			setProperty(Key.targetFolder, build.getTargetFolder().toString());
			setProperty(Key.outputFolder, build.getOutputFolder(null).toString());
			setProperty(Key.compile_outputpath, build.getOutputFolder(Scope.compile).toString());
			setProperty(Key.test_outputpath, build.getOutputFolder(Scope.test).toString());

			if (verbose) {
				build.console.separator();
				build.console.log("path references");
			}
			
			setSourcepath(Key.compile_sourcepath, build, Scope.compile);
			setSourcepath(Key.test_sourcepath, build, Scope.test);

			setClasspath(Key.compile_classpath, build, Scope.compile);
			setClasspath(Key.runtime_classpath, build, Scope.runtime);
			setClasspath(Key.test_classpath, build, Scope.test);

			setDependencypath(Key.compile_dependencypath, build, Scope.compile);
			setDependencypath(Key.runtime_dependencypath, build, Scope.runtime);
			setDependencypath(Key.test_dependencypath, build, Scope.test);	
		} catch (Exception e) {
			throw new BuildException(e);
		}		
	}
	
	private void setSourcepath(Key key, Build build, Scope scope) {
		Path sources = new Path(getProject());
		for (File file : build.getSourceFolders(scope)) {
			PathElement element = sources.createPathElement();
			element.setLocation(file);
		}
		addReference(key, sources, true);
	}
	
	private void setClasspath(Key key, Build build, Scope scope) {
		List<File> jars = build.getClasspath(scope);
		Path cp = new Path(getProject());
		// output folder
		PathElement of = cp.createPathElement();
		of.setLocation(build.getOutputFolder(scope));
		if (!scope.isDefault()) {
			of.setLocation(build.getOutputFolder(Scope.compile));
		}
		
		// add project dependencies 
		for (File folder : buildDependentProjectsClasspath(build)) {
			PathElement element = cp.createPathElement();
			element.setLocation(folder);
		}
		
		// jars
		for (File jar : jars) {
			PathElement element = cp.createPathElement();
			element.setLocation(jar);
		}

		addReference(key, cp, true);
	}
	
	private void setDependencypath(Key key, Build build, Scope scope) {
		List<File> jars = build.getClasspath(scope);
		Path cp = new Path(getProject());
		for (File jar : jars) {
			PathElement element = cp.createPathElement();
			element.setLocation(jar);
		}
		addReference(key, cp, true);
	}
	
	private List<File> buildDependentProjectsClasspath(Build build) {
		List<File> folders = new ArrayList<File>();
		List<Build> libraryProjects = build.getLinkedProjects();
		for (Build project : libraryProjects) {
			File outputFolder = project.getOutputFolder(Scope.compile);
			folders.add(outputFolder);
		}		
		return folders;
	}
}
