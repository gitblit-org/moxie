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
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Build;
import com.maxtk.Constants;
import com.maxtk.Constants.Key;
import com.maxtk.Pom;
import com.maxtk.Scope;
import com.maxtk.ant.Mft.MftAttr;
import com.maxtk.maxml.MaxmlMap;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class MxJar extends GenJar {

	ClassSpec mainclass;
	boolean classResolution;
	boolean fatjar;
	boolean includeResources;
	String includes;
	String excludes;
	
	String classifier;
	
	boolean packageSources;

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
		configure(build);
	}

	private void configure(Build build) {
		MaxmlMap attributes = build.getMxJarAttributes();
		if (attributes == null) {
			build.console.error("mx:Jar attributes are null!");
			return;
		}
		if (attributes.containsKey(Key.excludes.name())) {
			excludes = attributes.getString(Key.excludes.name(), null);
		}
	}

	@Override
	public void execute() throws BuildException {
		Build build = (Build) getProject().getReference(Key.build.refId());

		// automatic manifest entries from Maxilla metadata
		setManifest("Created-By", "Maxilla v" + Constants.VERSION);
		setManifest("Build-Jdk", System.getProperty("java.version"));
		setManifest("Build-Date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

		setManifest("Implementation-Title", Key.name);
		setManifest("Implementation-Vendor", Key.vendor);
		setManifest("Implementation-Vendor-Id", Key.groupId);
		setManifest("Implementation-Vendor-URL", Key.url);
		setManifest("Implementation-Version", Key.version);

		setManifest("Bundle-Name", Key.name);
		setManifest("Bundle-SymbolicName", Key.artifactId);
		setManifest("Bundle-Version", Key.version);
		setManifest("Bundle-Vendor", Key.vendor);
		
		setManifest("Git-Commit", Key.commit);

		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			setManifest("Main-Class", mc);
		}
		// if (splash != null) {
		// setManifest("SplashScreen-Image", splash);
		// }

		// TODO specify import-package? and export-package?
		
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
			destFile = new File(build.getTargetFolder(), name + ".jar");
		}
		
		if (destFile != null) {
			if (destFile.getParentFile() != null) {
				destFile.getParentFile().mkdirs();
			}
		} else if (destDir != null) {
			destDir.mkdirs();
		}
		
		// optionally include resources from the outputfolder
		if (includeResources) {
			Resource resources = createResource();			
			FileSet set = resources.createFileset();
			set.setDir(build.getOutputFolder(Scope.compile));
			if (includes != null) {
				set.setIncludes(includes);
			}
			if (excludes == null) {
				excludes = Constants.DEFAULT_BIN_EXCLUDES;
			}
			set.setExcludes(excludes);
		}
		
		build.console.title(getClass(), destFile.getName());
		
		build.console.debug("mxjar configuration");

		// display specified mxjar attributes
		MaxmlMap attributes = build.getMxJarAttributes();
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
					build.console.debug(1, "{0} = {1}", attrib, value);
				}			
			} catch (Exception e) {
				build.console.error(e);
				throw new BuildException("failed to get mx:Jar attributes!", e);
			}
		}
		
		long start = System.currentTimeMillis();
		super.execute();

		if (destFile != null) {
			build.console.log(1, destFile.getAbsolutePath());
			build.console.log(1, "{0} KB, generated in {1} ms", (destFile.length()/1024), System.currentTimeMillis() - start);
		} else {
			build.console.log(1, "class structure => " + destDir);
			build.console.log(1, "{0} KB, generated in {1} ms", (FileUtils.folderSize(destDir)/1024), System.currentTimeMillis() - start);
		}
		
		if (packageSources && destFile != null) {
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
			
			// add the source folders
			for (File dir : build.getSourceFolders(Scope.compile)) {
				FileSet set = new FileSet();				
				set.setDir(dir);
				set.setExcludes(Constants.DEFAULT_SRC_EXCLUDES);
				jar.add(set);
			}
			
			start = System.currentTimeMillis();			
			jar.execute();
						
			build.console.log(1, sourcesFile.getAbsolutePath());
			build.console.log(1, "{0} KB, generated in {1} ms", (sourcesFile.length()/1024), System.currentTimeMillis() - start);
		}
	}

	void setManifest(String key, String value) {
		// do not override a manual specification
		for (Object obj : mft.attrs) {
			MftAttr attr = (MftAttr) obj;
			if (attr.getName().equals(key)) {
				return;
			}
		}
		MftAttr attr = (MftAttr) mft.createAttribute();
		attr.setName(key);
		attr.setValue(value);
	}

	void setManifest(String key, Key prop) {
		String value = getProject().getProperty(prop.propId());
		if (!StringUtils.isEmpty(value)) {
			setManifest(key, value);
		}
	}
}
