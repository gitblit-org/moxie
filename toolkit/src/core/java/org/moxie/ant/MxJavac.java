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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.moxie.Build;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;


public class MxJavac extends Javac {
	
	Scope scope;
	boolean clean;
	boolean compileLinkedProjects;
	boolean copyResources;
	String includes;
	String excludes;
	Set<Build> builds;
	
	public MxJavac() {
		super();
	}
	
	private MxJavac(Set<Build> builds) {
		this.builds = builds;
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
		configure(build);
	}
	
	/**
	 * Configure the javac task from the mxjavac attributes
	 * @param build
	 */
	private void configure(Build build) {
		MaxmlMap attributes = build.getMxJavacAttributes();
		if (attributes == null) {
			build.console.error("mx:Javac attributes are null!");
			return;
		}
		if (attributes.containsKey(Key.excludes.name())) {
			excludes = attributes.getString(Key.excludes.name(), null);
		}
		if (attributes.containsKey(Key.includes.name())) {
			includes = attributes.getString(Key.includes.name(), null);
		}
		try {
			Map<String, Method> methods = new HashMap<String, Method>();
			for (Class<?> javacClass : new Class<?>[] { Javac.class, MxJavac.class }) {
				for (Method method: javacClass.getDeclaredMethods()) {
					if (method.getName().startsWith("set")) {
						methods.put(method.getName().toLowerCase(), method);
					}
				}
			}
			for (String key : attributes.keySet()) {
				if (key.equalsIgnoreCase(Key.compilerArgs.name())) {
					// compiler args are special
					List<String> args = (List<String>) attributes.getStrings(key,new ArrayList<String>());
					for (String arg : args) {
						createCompilerArg().setValue(arg);
					}
					continue;
				}
				// attributes
				Method method = methods.get("set" + key.toLowerCase());
				if (method == null) {					
					build.console.error("unknown mx:Javac attribute {0}", key);
					continue;
				}
				method.setAccessible(true);
				Object value = null;
				Class<?> parameterClass = method.getParameterTypes()[0];
				if (String.class.isAssignableFrom(parameterClass)) {
					value = attributes.getString(key, "");
				} else if (boolean.class.isAssignableFrom(parameterClass)
						|| Boolean.class.isAssignableFrom(parameterClass)) {
					value = attributes.getBoolean(key, false);
				} else if (int.class.isAssignableFrom(parameterClass)
						|| Integer.class.isAssignableFrom(parameterClass)) {
					value = attributes.getInt(key, 0);
				}
				method.invoke(this, value);
			}			
		} catch (Exception e) {
			build.console.error(e);
			throw new BuildException("failed to set mx:Javac attributes!", e);
		}
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.refId());
				
		if (scope == null) {
			scope = Scope.defaultScope;
		}
		
		if (builds == null) {
			// top-level mx:javac instantiates the build stack
			builds = new HashSet<Build>();
		}
		
		if (compileLinkedProjects) {
			for (Build linkedProject: build.getLinkedProjects()) {
				if (builds.contains(linkedProject)) {
					// already built, skip
					build.console.debug(1, "skipping {0}, already compiled", linkedProject.getPom().getManagementId());
					continue;
				}
				// add the build to the stack so we do not rebuild
				builds.add(linkedProject);
				
				try {
					// compile the linked project
					Project project = new Project();
					project.setBaseDir(linkedProject.getProjectFolder());
					project.addReference(Key.build.refId(), linkedProject);

					MxJavac subCompile = new MxJavac(builds);
					subCompile.setProject(project);
					subCompile.perform();
				} catch (Exception e) {
					build.console.error(e);
					throw new BuildException(e);
				}
			}
		}

		build.console.title(getClass(), build.getPom().getCoordinates() + ", " + scope.name());

		build.console.debug("mxjavac configuration");

		// display specified mxjavac attributes
		MaxmlMap attributes = build.getMxJavacAttributes();
		if (attributes != null) {
			try {
				Map<String, Method> methods = new HashMap<String, Method>();
				for (Class<?> javacClass : new Class<?>[] { Javac.class, MxJavac.class }) {
					for (Method method: javacClass.getDeclaredMethods()) {
						if (method.getName().startsWith("get")) {
							methods.put(method.getName().toLowerCase(), method);
						}
					}
				}
				for (String attrib : attributes.keySet()) {
					Method method = methods.get("get" + attrib.toLowerCase());
					if (method == null) {
						continue;
					}
					method.setAccessible(true);
					Object value = method.invoke(this, (Object[]) null);
					build.console.debug(1, "{0} = {1}", attrib, value);
				}			
			} catch (Exception e) {
				build.console.error(e);
				throw new BuildException("failed to get mx:Javac attributes!", e);
			}
		}
		
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
		if (Scope.test.equals(scope)) {
			// add the compile output folder
			PathElement element = classpath.createPathElement();
			element.setLocation(build.getOutputFolder(Scope.compile));
		}
		for (File file : build.getClasspath(scope)) {
			PathElement element = classpath.createPathElement();
			element.setLocation(file);
		}
		for (Build subbuild : build.getLinkedProjects()) {
			PathElement element = classpath.createPathElement();
			element.setLocation(subbuild.getOutputFolder(Scope.compile));
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
						build.console.log("adding resource path {0}", path);
					}
				}
			}
			
			copy.execute();
		}
	}
}
