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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Attribute;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.apache.tools.ant.types.resources.FileResource;
import org.moxie.Build;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MxJar extends GenJar {

	Build build;
	Console console;
	
	ClassSpec mainclass;
	boolean classResolution;
	boolean fatjar;
	boolean includeResources;
	boolean excludePomFiles;
	String includes;
	String excludes;
	boolean packageSources;

	String classifier;
	private boolean configured;
	
	public MxJar() {
		super();
		setTaskName("mx:jar");
	}
	
	/**
	 * Builds a <mainclass> element.
	 * 
	 * @return A <mainclass> element.
	 */
	public ClassSpec createMainclass() {
		if (mainclass == null) {
			ClassSpec cs = new ClassSpec(getProject());
			mainclass = cs;
			jarSpecs.add(cs);
			return cs;
		}
		throw new BuildException("Can only specify one main class");
	}
	
	public boolean getFatjar() {
		return fatjar;
	}

	public void setFatjar(boolean value) {
		this.fatjar = value;
	}
	
	public boolean getExcludepomfiles() {
		return excludePomFiles;
	}

	public void setExcludepomfiles(boolean value) {
		this.excludePomFiles = value;
	}

	public boolean getIncludesresources() {
		return includeResources;
	}
	
	public void setIncluderesources(boolean copy) {
		this.includeResources = copy;
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

	public String getClassifier() {
		return classifier;
	}
	
	public void setClassifier(String classifier) {
		this.classifier = classifier;
	}
	
	public boolean getPackagesources() {
		return packageSources;
	}
	
	public void setPackagesources(boolean sources) {
		this.packageSources = sources;
	}

	
	@Override
	public void setProject(Project project) {
		super.setProject(project);
		Build build = (Build) getProject().getReference(Key.build.refId());
		if (build != null) {
			configure(build);
		}
	}

	private void configure(Build build) {
		configured = true;
		MaxmlMap attributes = build.getConfig().getMxJarAttributes();
		if (attributes == null) {
			build.getConsole().error("mx:Jar attributes are null!");
			return;
		}
		if (attributes.containsKey(Key.excludes.name())) {
			excludes = attributes.getString(Key.excludes.name(), null);
		}
		
		try {
			Map<String, Method> methods = new HashMap<String, Method>();
			for (Class<?> javacClass : new Class<?>[] { Javac.class, MxJar.class }) {
				for (Method method: javacClass.getDeclaredMethods()) {
					if (method.getName().startsWith("set")) {
						methods.put(method.getName().toLowerCase(), method);
					}
				}
			}
			for (String key : attributes.keySet()) {
				// attributes
				Method method = methods.get("set" + key.toLowerCase());
				if (method == null) {					
					build.getConsole().error("unknown mx:Jar attribute {0}", key);
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
			build.getConsole().error(e);
			throw new BuildException("failed to set mx:Jar attributes!", e);
		}
	}

	@Override
	public void execute() throws BuildException {
		build = (Build) getProject().getReference(Key.build.refId());
		console = build.getConsole();
		
		if (!configured) {
			// called from moxie.package
			configure(build);
		}
		
		// automatic manifest entries from Moxie metadata
		configureManifest(mft);

		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			setManifest(mft, "Main-Class", mc);
		}

		// automatic classpath resolution, if not manually specified
		if (classpath == null) {
			Object o = getProject().getReference(Key.compile_classpath.refId());
			if (o != null && o instanceof Path) {
				Path cp = (Path) o;
				if (fatjar) {
					// FatJar generation
					classpath = createClasspath();					
					for (String path : cp.list()) {
						if (path.toLowerCase().endsWith(".jar")) {
							LibrarySpec lib = createLibrary();
							lib.setJar(path);
						} else {
							PathElement element = classpath.createPathElement();
							element.setPath(path);
						}
					}
				} else {
					// standard GenJar class dependency resolution
					classpath = cp;
				}
			}
		}
		
		if (destFile == null) {
			// default output jar if file unspecified
			Pom pom = build.getPom();
			String name = pom.artifactId;
			if (!StringUtils.isEmpty(pom.version)) {
				name += "-" + pom.version;
			}
			if (StringUtils.isEmpty(classifier)) {
				classifier = pom.classifier;
			}
			if (!StringUtils.isEmpty(classifier)) {
				name += "-" + classifier;
			}
			destFile = new File(build.getConfig().getTargetFolder(), name + ".jar");
		}
		
		if (destFile.getParentFile() != null) {
			destFile.getParentFile().mkdirs();
		}
		
		version = build.getPom().version;
		
		// optionally include resources from the outputfolder
		if (includeResources) {
			Resource resources = createResource();			
			FileSet set = resources.createFileset();
			set.setDir(build.getConfig().getOutputFolder(Scope.compile));
			if (includes != null) {
				set.setIncludes(includes);
			}
			if (excludes == null) {
				excludes = Toolkit.DEFAULT_EXCLUDES;
			}
			set.setExcludes(excludes);
		}
		
		console.title(getClass(), destFile.getName());
		
		console.debug("mxjar configuration");

		// display specified mxjar attributes
		MaxmlMap attributes = build.getConfig().getMxJarAttributes();
		if (attributes != null) {
			try {
				Map<String, Method> methods = new HashMap<String, Method>();
				for (Class<?> javacClass : new Class<?>[] { MxJar.class }) {
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
					console.debug(1, "{0} = {1}", attrib, value);
				}			
			} catch (Exception e) {
				console.error(e);
				throw new BuildException("failed to get mx:Jar attributes!", e);
			}
		}
		
		long start = System.currentTimeMillis();
		super.execute();

		console.log(1, destFile.getAbsolutePath());
		console.log(1, "{0} KB, generated in {1} ms", (destFile.length()/1024), System.currentTimeMillis() - start);
		
		/*
		 * Build sources jar
		 */
		if (packageSources) {
			String name = destFile.getName();
			if (!StringUtils.isEmpty(classifier)) {
				// replace the classifier with "sources"
				name = name.replace(classifier, "sources");
			} else {
				// append -sources to the filename before the extension
				name = name.substring(0, name.lastIndexOf('.')) + "-sources" + name.substring(name.lastIndexOf('.'));
			}
			File sourcesFile = new File(destFile.getParentFile(), name);
			if (sourcesFile.exists()) {
				sourcesFile.delete();
			}
			
			Jar jar = new Jar();
			jar.setTaskName(getTaskName());
			jar.setProject(getProject());
			
			// set the destination file
			jar.setDestFile(sourcesFile);
			
			// use the resolved classes to determine included source files
			List<FileResource> sourceFiles = new ArrayList<FileResource>();
			Map<File, Set<String>> packageResources = new HashMap<File, Set<String>>();
			
			if (resolvedLocal.size() == 0) {
				console.warn("mxjar has not resolved any class files local to {0}", build.getPom().getManagementId());
			}
			
			List<File> folders = build.getConfig().getSourceFolders(Scope.compile);
			for (String className : resolvedLocal) {
				String sourceName = className.substring(0, className.length() - ".class".length()).replace('.', '/') + ".java";
				console.debug(sourceName);
				for (File folder : folders) {
					File file = new File(folder, sourceName);
					if (file.exists()) {
						FileResource resource = new FileResource(getProject(), file);
						resource.setBaseDir(folder);
						sourceFiles.add(resource);
						if (!packageResources.containsKey(folder)) {
							// always include default package resources
							packageResources.put(folder, new TreeSet<String>(Arrays.asList( "/*" )));						
						}
						String packagePath = FileUtils.getRelativePath(folder, file.getParentFile());
						packageResources.get(folder).add(packagePath + "/*");
						console.debug(1, file.getAbsolutePath());
						break;
					}
				}
			}
			
			// add the discovered source files for the resolved classes
			jar.add(new FileResourceSet(sourceFiles));
			
			// add the resolved package folders for resource files
			if (includeResources) {
				for (Map.Entry<File, Set<String>> entry : packageResources.entrySet()) {
					FileSet set = new FileSet();				
					set.setDir(entry.getKey());
					set.setExcludes(excludes);
					StringBuilder includes = new StringBuilder();
					for (String packageName : entry.getValue()) {
						includes.append(packageName + ",");
					}
					includes.setLength(includes.length() - 1);
					set.setIncludes(includes.toString());
					console.debug("adding resource fileset {0}", entry.getKey());
					console.debug(1, "includes={0}", includes.toString());
					jar.add(set);
				}
			}
			
			// set the source jar manifest
			try {
				Manifest mft = new Manifest();
				configureManifest(mft);
				jar.addConfiguredManifest(mft);
			} catch (ManifestException e) {
				console.error(e);
			}
			
			start = System.currentTimeMillis();			
			jar.execute();
						
			console.log(1, sourcesFile.getAbsolutePath());
			console.log(1, "{0} KB, generated in {1} ms", (sourcesFile.length()/1024), System.currentTimeMillis() - start);
		}
	}
	
	void configureManifest(Manifest manifest) {
		// set manifest entries from Moxie metadata
		Manifest mft = new Manifest();
		setManifest(mft, "Created-By", "Moxie v" + Toolkit.getVersion());
		setManifest(mft, "Build-Jdk", System.getProperty("java.version"));
		setManifest(mft, "Build-Date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

		setManifest(mft, "Implementation-Title", Key.name);
		setManifest(mft, "Implementation-Vendor", Key.organization);
		setManifest(mft, "Implementation-Vendor-Id", Key.groupId);
		setManifest(mft, "Implementation-Vendor-URL", Key.url);
		setManifest(mft, "Implementation-Version", Key.version);

		setManifest(mft, "Bundle-Name", Key.name);
		setManifest(mft, "Bundle-SymbolicName", Key.artifactId);
		setManifest(mft, "Bundle-Version", Key.version);
		setManifest(mft, "Bundle-Vendor", Key.organization);
		
		setManifest(mft, "Git-Commit", Key.commit);
		
		try {
			manifest.merge(mft, true);
		} catch (ManifestException e) {
			console.error(e, "Failed to configure manifest!");
		}
	}

	void setManifest(Manifest man, String key, Key prop) {
		// try project property
		String value = getProject().getProperty(prop.projectId());
		if (value == null) {
			return;
		}
		if (value.equals(prop.projectId())) {
			// try mxp property
			value = getProject().getProperty(prop.propId());
			if (value.equals(prop.propId())) {
				value = null;
			}
		}
		if (!StringUtils.isEmpty(value)) {
			setManifest(man, key, value);
		}
	}
	
	void setManifest(Manifest man, String key, String value) {
		if (!StringUtils.isEmpty(value)) {
			try {
				man.addConfiguredAttribute(new Attribute(key, value));
			} catch (ManifestException e) {
				console.error(e, "Failed to set manifest attribute!");
			}
		}
	}
	
	/**
	 * Add the Maven META-INF files. 
	 */
	@Override
	protected void writeJarEntries(JarOutputStream jos) {
		if (excludePomFiles) {
			return;
		}
		
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(jos));
		
		Properties properties = new Properties();
		properties.put(Key.groupId.name(), build.getPom().groupId);
		properties.put(Key.artifactId.name(), build.getPom().artifactId);
		properties.put(Key.version.name(), version);
		
		try {
			ZipEntry entry = new ZipEntry(MessageFormat.format("META-INF/maven/{0}/{1}/pom.properties", build.getPom().groupId, build.getPom().artifactId));
			jos.putNextEntry(entry);		
			properties.store(dos, "Generated by Moxie");
			dos.flush();
			jos.closeEntry();
		} catch (IOException e) {
			console.error(e, "failed to write pom.properties!");
		}
		
		try {
			ZipEntry entry = new ZipEntry(MessageFormat.format("META-INF/maven/{0}/{1}/pom.xml", build.getPom().groupId, build.getPom().artifactId));
			jos.putNextEntry(entry);
			dos.write(build.getPom().toXML().getBytes("UTF-8"));
			dos.flush();
			jos.closeEntry();
		} catch (IOException e) {
			console.error(e, "failed to write pom.xml!");
		}
	}

}
