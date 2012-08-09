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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public abstract class MxTask extends Task {

	private Console console;

	private Boolean verbose;

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
	
	public Build getBuild() {
		Build build = (Build) getProject().getReference(Key.build.refId());
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

	protected void setProperty(Key prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop.propId(), value);
			log(prop.propId(), value, false);
		}
	}

	protected void setProperty(String prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop, value);
			log(prop, value, false);
		}
	}

	protected void addReference(Key prop, Object obj, boolean split) {
		getProject().addReference(prop.refId(), obj);
		log(prop.refId(), obj.toString(), split);
	}
	
	protected void log(String key, String value, boolean split) {
		if (isVerbose()) {
			int indent = 26;
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
	
	protected void extractResource(File outputFolder, String resource) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			InputStream is = getClass().getResourceAsStream("/" + resource);
			
			byte [] buffer = new byte[32767];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}			
		} catch (Exception e) {
			getConsole().error(e, "Can't extract {0}!", resource);
		}
		File file = new File(outputFolder, resource);
		file.getParentFile().mkdirs();
		FileUtils.writeContent(file, os.toByteArray());
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
//		cp.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));

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
				throw new BuildException("Error, could not access class loader!", t);
			}

			for (Dependency dependency : executionDependencies) {
				File file = build.getSolver().getArtifact(dependency);
				if (!file.exists()) {
					build.getConsole().warn("excluding {0} from build classpath because it was not found!", file.getAbsolutePath());
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
					throw new BuildException(MessageFormat.format(
						"Error, could not add {0} to classloader", path), t);
				}
			}				
		} else {
			build.getConsole().error("Skipping update classpath. Unexpected class loader {0}", loader.getClass().getName());
		}
	}
}
