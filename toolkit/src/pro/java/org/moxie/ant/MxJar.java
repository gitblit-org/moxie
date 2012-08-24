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
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Attribute;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.ZipFileSet;
import org.moxie.Build;
import org.moxie.MoxieException;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class MxJar extends Jar {

	Build build;
	Console console;

	ClassSpec mainclass;
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
			return cs;
		}
		throw new MoxieException("Can only specify one main class");
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
		MaxmlMap attributes = build.getConfig().getTaskAttributes(getTaskName());
		if (attributes == null) {
			build.getConsole().error(getTaskName() + " attributes are null!");
			return;
		}
		
		AttributeReflector.setAttributes(getProject(), this, attributes);
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
		Manifest manifest = new Manifest();
		configureManifest(manifest);

		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			setManifest(manifest, "Main-Class", mc);
		}
		try {
			addConfiguredManifest(manifest);
		} catch (ManifestException e1) {
			console.error(e1, "Failed to configure manifest!");
			throw new MoxieException(e1);
		}
		
		if (fatjar) {
			// FatJar generation (merging reference dependencies)
			Object o = getProject().getReference(Key.compile_classpath.refId());
			if (o != null && o instanceof Path) {
				Path cp = (Path) o;
				for (String path : cp.list()) {
					if (path.toLowerCase().endsWith(".jar")) {
						ZipFileSet zip = new ZipFileSet();
						zip.setProject(getProject());
						zip.setSrc(new File(path));
						addZipfileset(zip);
					}
				}
			}
		}
		
		if (excludes == null) {
			excludes = Toolkit.DEFAULT_EXCLUDES;
		}

		// compiled output
		File outputFolder = build.getConfig().getOutputFolder(Scope.compile);
		FileSet outputSet = new FileSet();
		outputSet.setProject(getProject());
		outputSet.setDir(outputFolder);
		if (includes != null) {
			outputSet.setIncludes(includes);
		}
		outputSet.setExcludes(excludes);		
		addFileset(outputSet);

		if (getDestFile() == null) {
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
			setDestFile(new File(build.getConfig().getTargetFolder(), name + ".jar"));
		}
		
		File destFile = getDestFile();
		
		if (destFile.getParentFile() != null) {
			destFile.getParentFile().mkdirs();
		}
		
		addMavenEntries();
		
		console.title(getClass(), destFile.getName());
		
		console.debug(getTaskName() + " configuration");

		// display specified mx:jar attributes
		MaxmlMap attributes = build.getConfig().getTaskAttributes(getTaskName());
		AttributeReflector.logAttributes(this, attributes, console);

		long start = System.currentTimeMillis();
		super.execute();

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
			
			List<File> folders = build.getConfig().getSourceFolders(Scope.compile);
			for (File folder : folders) {
				FileSet srcSet = new FileSet();
				srcSet.setProject(getProject());
				srcSet.setDir(folder);				
				srcSet.setIncludes("**/*.java");				
				jar.addFileset(srcSet);

				if (includeResources) {
					FileSet resSet = new FileSet();
					resSet.setProject(getProject());
					resSet.setDir(folder);				
					resSet.setExcludes(excludes);
					jar.addFileset(resSet);
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
				console.error(e, "Failed to set manifest attribute \"{0}\"!", key);
			}
		}
	}
	
	/**
	 * Add the Maven META-INF files. 
	 */
	private void addMavenEntries() {
		if (excludePomFiles) {
			return;
		}
		
		try {
			Properties properties = new Properties();
			properties.put(Key.groupId.name(), build.getPom().groupId);
			properties.put(Key.artifactId.name(), build.getPom().artifactId);
			properties.put(Key.version.name(), build.getPom().version);

			File tmpFile = new File(build.getConfig().getOutputFolder(null), "pom.properties");			
			FileWriter writer = new FileWriter(tmpFile);
			properties.store(writer, "Generated by Moxie");
			writer.flush();
			writer.close();
			
			ZipFileSet set = new ZipFileSet();
			set.setProject(getProject());
			set.setFile(tmpFile);
			set.setPrefix(MessageFormat.format("META-INF/maven/{0}/{1}", build.getPom().groupId, build.getPom().artifactId));
			addFileset(set);
		} catch (IOException e) {
			console.error(e, "failed to write pom.properties!");
		}
		
		try {
			File tmpFile = new File(build.getConfig().getOutputFolder(null), "pom.xml");
			FileUtils.writeContent(tmpFile, build.getPom().toXML());

			ZipFileSet set = new ZipFileSet();
			set.setProject(getProject());
			set.setFile(tmpFile);
			set.setPrefix(MessageFormat.format("META-INF/maven/{0}/{1}", build.getPom().groupId, build.getPom().artifactId));
			addFileset(set);
		} catch (Exception e) {
			console.error(e, "failed to write pom.xml!");
		}
	}
}
