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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public abstract class MxTask extends Task {

	private Console console;

	private Boolean verbose;
	
	private boolean requiredGoal;
	
	public MxTask() {
		super();
		requiredGoal = true;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean isVerbose() {
		if (verbose == null) {
			String mxvb = System.getProperty(Toolkit.MX_VERBOSE);
			if (StringUtils.isEmpty(mxvb)) {
				Build build = getBuild();
				if (build == null) {
					return false;
				} else {
					return build.getConfig().isVerbose();
				}
			} else {
				verbose = Boolean.parseBoolean(mxvb);
			}
		}
		return verbose;
	}

	private Boolean showtitle;

	public void setShowtitle(boolean value) {
		this.showtitle = value;
	}
	
	public boolean isShowTitle() {
		return showtitle == null || showtitle;
	}
	
	public boolean isRequiredGoal() {
		return requiredGoal;
	}
	
	public void setRequiredGoal(boolean value) {
		requiredGoal = value;
	}
	
	public void title(String title) {
		if (isShowTitle()) {
			getConsole().title(title);
		}
	}

	public void title(String title, String parameter) {
		if (isShowTitle()) {
			getConsole().title(title, parameter);
		}
	}
	
	public void titleClass() {
		if (isShowTitle()) {
			getConsole().title(getClass());
		}
	}

	public void titleClass(String parameter) {
		if (isShowTitle()) {
			getConsole().title(getClass(), parameter);
		}
	}
	
	/**
	 * Console offset is a one-time correction factor
	 * to improve readability of the output.
	 * @return
	 */
	public int getConsoleOffset() {
		int consoleOffset = 0;
		String offset = getProject().getProperty("console.offset");
		if (!StringUtils.isEmpty(offset)) {
			consoleOffset = Integer.parseInt(offset);
		}
		return consoleOffset;
	}
	
	public void setConsoleOffset(int value) {
		getProject().setProperty("console.offset", "" + value);
	}

	public Build getBuild() {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		return build;
	}

	protected Console getConsole() {
		if (console == null) {
			Build build = getBuild();
			if (build == null) {
				console = new Console();
			} else {
				console = build.getConsole();
			}
		}
		return console;
	}

	protected void setProjectProperty(Key prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop.projectId(), value);
			log(prop.projectId(), value, false);
		}
	}

	protected void setProperty(String prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop, value);
			log(prop, value, false);
		}
	}
	
	protected void setReference(Key prop, Object obj) {
		if (obj == null) {
			return;
		}
		getProject().addReference(prop.referenceId(), obj);
	}

	protected void setPathReference(Key prop, Object obj, boolean split) {
		// set path as both a property and a reference
		// the property is accessible from all Ants
		// the reference is accessible internally from mx tasks
		// and when using Moxie on the Ant classpath (-lib and Moxie+Ant)
		getProject().setProperty(prop.projectId(), obj.toString());
		log(prop.projectId(), obj.toString(), split);
		setReference(prop, obj);
	}
	
	protected void log(String key, String value, boolean split) {
		if (isVerbose()) {
			int indent = 30;
			if (split) {
				String [] paths = value.split(File.pathSeparator);
				getConsole().key(StringUtils.leftPad(key, indent, ' '), paths[0]);
				for (int i = 1; i < paths.length; i++) {
					getConsole().key(StringUtils.leftPad("", indent, ' '), paths[i]);	
				}
			} else {
				getConsole().key(StringUtils.leftPad(key, indent, ' '), value);
			}
		}
	}
	
	protected String readResource(String resource) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			InputStream is = getClass().getResourceAsStream("/" + resource);
			
			byte [] buffer = new byte[32767];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}			
		} catch (Exception e) {
			getConsole().error(e, "Can not extract \"{0}\"!", resource);
		}
		return os.toString();
	}

	protected boolean extractResource(File outputFolder, String resource) {
		return extractResource(outputFolder, resource, resource, true);
	}
	
	protected boolean extractResource(File outputFolder, String resource, String asResource, boolean overwrite) {
		File targetFile = new File(outputFolder, asResource);
		if (targetFile.exists() && !overwrite) {
			return false;
		}
		String content = readResource(resource);
		targetFile.getParentFile().mkdirs();
		FileUtils.writeContent(targetFile, content);
		return true;
	}
	
	protected boolean hasClass(String classname) {
		try {
			return Class.forName(classname) != null;
		} catch (Throwable t) {			
		}
		return false;
	}
	
	protected void updateExecutionClasspath() {
		Build build = getBuild();
		Set<Dependency> executionDependencies = new LinkedHashSet<Dependency>();
		executionDependencies.addAll(build.getSolver().getDependencies(Scope.test));
		executionDependencies.addAll(build.getSolver().getDependencies(Scope.build));
		
		Set<String> cp = new LinkedHashSet<String>();

		ClassLoader loader  = getClass().getClassLoader();
		if (loader instanceof AntClassLoader) {
			// running Moxie using taskdef notation from a script
			AntClassLoader antLoader = (AntClassLoader) loader;
			build.getConsole().debug("updating Moxie classpath via AntClassLoader");
			build.getConsole().debug(antLoader.getClasspath());
			for (Dependency dependency : executionDependencies) {
				File file = build.getSolver().getArtifact(dependency);
				try {					
					cp.add(file.getCanonicalPath());
				} catch (Exception e) {
					cp.add(file.getAbsolutePath());
				}
			}
			
			// add file objects
			for (String path : cp) {	
				antLoader.addPathComponent(new File(path));
				build.getConsole().debug(1, "{0}", path);
			}
		} else if (loader instanceof URLClassLoader) {
			// running Moxie bundled with Ant or with -lib parameter
			build.getConsole().debug("updating Moxie classpath via URLClassLoader");
			URLClassLoader sysloader = (URLClassLoader) loader;
			Class<?> sysclass = URLClassLoader.class;
			Method addURL = null;
			try {
				addURL = sysclass.getDeclaredMethod("addURL", new Class[] { URL.class });
				addURL.setAccessible(true);
			} catch (Throwable t) {
				throw new MoxieException("Error, could not access class loader!", t);
			}

			for (Dependency dependency : executionDependencies) {
				File file = build.getSolver().getArtifact(dependency);
				if (!file.exists()) {
					build.getConsole().warn("excluding {0} from Moxie build classpath because it was not found!", file.getAbsolutePath());
					continue;
				}
				try {
					cp.add(file.getCanonicalPath());
				} catch (Exception e) {
					cp.add(file.getAbsolutePath());
				}
			}
			for (String path : cp) {
				try {
					addURL.invoke(sysloader, new Object[] { new File(path).toURI().toURL() });
					build.getConsole().debug(1, "{0}", path);
				} catch (Throwable t) {
					throw new MoxieException(MessageFormat.format(
						"Error, could not add {0} to classloader", path), t);
				}
			}				
		} else {
			build.getConsole().error("Skipping update classpath. Unexpected class loader {0}", loader.getClass().getName());
		}
	}
	
	public String getProjectTitle() {
		String name = getBuild().getPom().getCoordinates();
		if (!StringUtils.isEmpty(getBuild().getPom().getName())) {
			name = getBuild().getPom().getName() + " (" + name + ")";
		}
		return name;
	}
	
	public void sharePaths(String... paths) {
		getProject().setProperty("mxshared.path", StringUtils.flattenStrings(
				Arrays.asList(paths), File.pathSeparator));
	}
	
	public Path getSharedPaths() {
		Path path = new Path(getProject());
		String paths  = getProject().getProperty("mxshared.path");
		if (!StringUtils.isEmpty(paths)) {
			for (String fp : paths.split(File.pathSeparator)) {
				FileSet fs = new FileSet();
				fs.setProject(getProject());
				File file = new File(fp);
				if (file.isDirectory()) {
					fs.setDir(file);
				} else {
					fs.setFile(file);
				}
				path.add(fs);
			}
		}
		return path;
	}
	
	public Path consumeSharedPaths() {
		Path path = getSharedPaths();
		getProject().setProperty("mxshared.path", "");
		return path;
	}
}
