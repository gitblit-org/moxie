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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.moxie.Build;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MxJavac extends Javac {
	
	Scope scope;
	boolean clean;
	boolean compileLinkedProjects;
	boolean copyResources;
	String includes;
	String excludes;
	Set<Build> builds;
	
	private boolean configured;
	
	public MxJavac() {
		super();
		setTaskName("mx:javac");
	}
	
	private MxJavac(Set<Build> builds) {
		this.builds = builds;
		setTaskName("mx:javac");
	}
	
	public boolean getClean() {
		return clean;
	}
	
	public void setClean(boolean clean) {
		this.clean = clean;
	}
	
	public boolean getCompilelinkedprojects() {
		return compileLinkedProjects;
	}

	public void setCompilelinkedprojects(boolean compileLinkedProjects) {
		this.compileLinkedProjects = compileLinkedProjects;
	}
	
	public String getScope() {
		return scope.name();
	}

	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}
	
	public boolean getCopyresources() {
		return copyResources;
	}

	public void setCopyresources(boolean copy) {
		this.copyResources = copy;
	}
	
	public String getIncludes() {
		return includes;
	}

	public void setIncludes(String includes) {
		this.includes = includes;
	}
	
	public String getExcludes() {
		return excludes;
	}

	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}
	
	@Override
	public void setProject(Project project) {
		super.setProject(project);
		Build build = (Build) getProject().getReference(Key.build.refId());
		if (build != null) {
			configure(build);
		}
	}
	
	/**
	 * Configure the javac task from the mxjavac attributes
	 * @param build
	 */
	private void configure(Build build) {
		configured = true;
		MaxmlMap attributes = build.getConfig().getTaskAttributes(getTaskName());
		if (attributes == null) {
			build.getConsole().error(getTaskName() + " attributes are null!");
			return;
		}
		// clone the original attributes because we remove the compiler args
		attributes = DeepCopier.copy(attributes);
		Object args = attributes.remove(Key.compilerArgs.name());

		AttributeReflector.setAttributes(getProject(), this, attributes);
		
		if (args != null) {
			// set the compiler args, if any
			List<Object> list = new ArrayList<Object>();
			if (args instanceof List) {
				list = (List<Object>) args;
			} else if (args instanceof String) {
				for (String value : StringUtils.breakCSV(args.toString())) {
					if (!StringUtils.isEmpty(value)) {
						list.add(value);
					}
				}
			}
			for (Object o : list) {
				createCompilerArg().setValue(o.toString());
			}
		}
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.refId());
		Console console = build.getConsole();
		
		if (!configured) {
			// called from moxie.compile
			configure(build);
		}
		
		if (scope == null) {
			scope = Scope.defaultScope;
		}
		
		if (builds == null) {
			// top-level mx:javac instantiates the build stack
			builds = new HashSet<Build>();
		}
		
		if (compileLinkedProjects) {
			for (Build linkedProject: build.getSolver().getLinkedModules()) {
				if (builds.contains(linkedProject)) {
					// already built, skip
					console.debug(1, "skipping {0}, already compiled", linkedProject.getPom().getManagementId());
					continue;
				}
				// add the build to the stack so we do not rebuild
				builds.add(linkedProject);
				
				try {
					// compile the linked project
					Project project = new Project();
					project.setBaseDir(linkedProject.getConfig().getProjectFolder());
					project.addReference(Key.build.refId(), linkedProject);

					MxJavac subCompile = new MxJavac(builds);
					subCompile.setProject(project);
					subCompile.perform();
				} catch (Exception e) {
					console.error(e);
					throw new MoxieException(e);
				}
			}
		}

		console.title(getClass(), build.getPom().getCoordinates() + ", " + scope.name());

		console.debug("mxjavac configuration");

		// display specified mxjavac attributes
		MaxmlMap attributes = build.getConfig().getTaskAttributes(getTaskName());
		AttributeReflector.logAttributes(this, attributes, console);
		
		// project folder
		console.debug(1, "projectdir = {0}", build.getConfig().getProjectFolder());

		// create sourcepath
		Path sources = createSrc();
		for (File file : build.getConfig().getSourceFolders(scope)) {
			PathElement element = sources.createPathElement();
			element.setLocation(file);
		}
		console.debug(1, "sources = {0}", sources);

		// set output folder
		setDestdir(build.getConfig().getOutputFolder(scope));
		console.debug(1, "destdir = {0}", getDestdir());
		
		// create classpath
		Path classpath = createClasspath();
		if (Scope.test.equals(scope)) {
			// add the compile output folder
			PathElement element = classpath.createPathElement();
			element.setLocation(build.getConfig().getOutputFolder(Scope.compile));
		}
		for (File file : build.getSolver().getClasspath(scope)) {
			PathElement element = classpath.createPathElement();
			element.setLocation(file);
		}
		for (Build subbuild : build.getSolver().getLinkedModules()) {
			PathElement element = classpath.createPathElement();
			element.setLocation(subbuild.getConfig().getOutputFolder(Scope.compile));
		}
		console.debug(1, "classpath = {0}", classpath);
				
		if (clean) {
			// clean the output folder before compiling
			console.log("Cleaning {0}", getDestdir().getAbsolutePath());
			FileUtils.delete(getDestdir());
		}
		
		getDestdir().mkdirs();
		
		// set the update property name so we know if nothing compiled
		String prop = build.getPom().getCoordinates().replace(':', '.') + ".compiled";
		setUpdatedProperty(prop);
		super.execute();
		if (getProject().getProperty(prop) == null) {
			console.log(1, "compiled classes are up-to-date");
		}
		
		// optionally copy resources from source folders
		if (copyResources) {
			Copy copy = new Copy();
			copy.setTaskName(getTaskName());
			copy.setProject(getProject());
			copy.setTodir(getDestdir());
			copy.setVerbose(getVerbose());

			if (getVerbose()) {
				console.log("copying resources => {0}", getDestdir());
			}

			if (excludes == null) {
				// default exclusions
				excludes = Toolkit.DEFAULT_EXCLUDES;
			}
			
			for (String path : getSrcdir().list()) {
				File file = new File(path);
				if (file.isDirectory()) {
					FileSet set = new FileSet();
					set.setDir(file);
					if (includes != null) {
						set.setIncludes(includes);
					}
					if (excludes != null) {
						set.setExcludes(excludes);
					}
					copy.add(set);
					if (getVerbose()) {
						console.log("adding resource path {0}", path);
					}
				}
			}
			
			copy.execute();
		}
	}
}
