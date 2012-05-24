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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Build;
import com.maxtk.Constants;
import com.maxtk.Constants.Key;
import com.maxtk.Scope;
import com.maxtk.utils.FileUtils;

public class MxJavac extends Javac {
	
	Scope scope;
	boolean clean;
	boolean compileLinkedProjects;
	boolean copyResources;
	String includes;
	String excludes;
	
	public void setClean(boolean clean) {
		this.clean = clean;
	}

	public void setCompilelinkedprojects(boolean compileLinkedProjects) {
		this.compileLinkedProjects = compileLinkedProjects;
	}

	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}

	public void setCopyresources(boolean copy) {
		this.copyResources = copy;
	}

	public void setIncludes(String includes) {
		this.includes = includes;
	}

	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.refId());
		
		if (scope == null) {
			scope = Scope.defaultScope;
		}
		
		if (compileLinkedProjects) {
			for (Build linkedProject: build.getLinkedProjects()) {			
				try {
					// clone this MxJavac instance to preserve settings
					MxJavac subCompile = (MxJavac) this.clone();
					subCompile.setFork(false);

					// override the Maxilla project reference
					Project project = new Project();
					project.setBaseDir(linkedProject.getProjectFolder());
					project.addReference(Key.build.refId(), linkedProject);
					subCompile.setProject(project);

					// compile
					subCompile.perform();
				} catch (Exception e) {
					build.console.error(e);
					throw new BuildException(e);
				}
			}
		}

		build.console.title(getClass(), build.getPom().getManagementId() + ", " + scope.name());
		
		// project folder
		build.console.debug(1, "projectdir = {0}", build.getProjectFolder());

		// create sourcepath
		Path sources = createSrc();
		for (File file : build.getSourceFolders(scope)) {
			PathElement element = sources.createPathElement();
			element.setLocation(file);
		}
		build.console.debug(1, "sources = {0}", sources);

		// set output folder
		setDestdir(build.getOutputFolder(scope));
		build.console.debug(1, "destdir = {0}", getDestdir());
		
		// create classpath
		Path classpath = createClasspath();
		for (File file : build.getClasspath(scope)) {
			PathElement element = classpath.createPathElement();
			element.setLocation(file);
		}
		for (Build subbuild : build.getLinkedProjects()) {
			PathElement element = classpath.createPathElement();
			element.setLocation(subbuild.getOutputFolder(scope));
		}
		build.console.debug(1, "classpath = {0}", classpath);
				
		if (clean) {
			// clean the output folder before compiling
			build.console.log("Cleaning {0}", getDestdir().getAbsolutePath());
			FileUtils.delete(getDestdir());
		}
		
		getDestdir().mkdirs();
		
		// set the update property name so we know if nothing compiled
		String prop = build.getPom().getCoordinates().replace(':', '.') + ".compiled";
		setUpdatedProperty(prop);
		super.execute();
		if (getProject().getProperty(prop) == null) {
			build.console.log(1, "compiled classes are up-to-date");
		}
		
		// optionally copy resources from source folders
		if (copyResources) {
			Copy copy = new Copy();
			copy.setTaskName(getTaskName());
			copy.setProject(getProject());
			copy.setTodir(getDestdir());
			copy.setVerbose(getVerbose());

			if (getVerbose()) {
				build.console.log("copying resources => {0}", getDestdir());
			}

			if (excludes == null) {
				// default exclusions
				excludes = Constants.DEFAULT_EXCLUDES;
			}
			
			for (String path : getSrcdir().list()) {
				File file = new File(path);
				if (file.isDirectory()) {
					FileSet set = new FileSet();
					set.setDir(file);
					if (includes != null) {
						set.setIncludes(includes);
					}
					set.setExcludes(excludes);
					copy.add(set);
					if (getVerbose()) {
						build.console.log("adding resource path {0}", path);
					}
				}
			}
			
			copy.execute();
		}
	}
}
