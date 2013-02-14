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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
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
import org.moxie.MxLauncher;
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

	LauncherSpec launcher;
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
	
	/**
	 * Builds a <launcher> element.
	 * 
	 * @return A <launcher> element.
	 */
	public LauncherSpec createLauncher() {
		if (launcher == null) {
			LauncherSpec cs = new LauncherSpec(getProject());
			launcher = cs;			
			return cs;
		}
		throw new MoxieException("Can only specify one launcher class");
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

		if (mainclass == null) {
			String mc = build.getConfig().getProjectConfig().getMainclass();
			if (!StringUtils.isEmpty(mc)) {
				ClassSpec cs = new ClassSpec(getProject());
				mainclass = cs;
				mainclass.setName(mc);
			}
		}
		
		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			if (launcher == null) {
				// use specified mainclass
				setManifest(manifest, "Main-Class", mc);
			} else {
				// inject Moxie Launcher class
				String mx = launcher.getName().replace('/', '.');
				if (mx.endsWith(".class")) {
					mx = mx.substring(0, mx.length() - ".class".length());
				}
				setManifest(manifest, "Main-Class", mx);
				setManifest(manifest, "mxMain-Class", mc);
				String paths = launcher.getPaths();
				if (!StringUtils.isEmpty(paths)) {
					setManifest(manifest, "mxMain-Paths", paths);	
				}
			}
		}
		try {
			addConfiguredManifest(manifest);
		} catch (ManifestException e1) {
			console.error(e1, "Failed to configure manifest!");
			throw new MoxieException(e1);
		}
		
		if (fatjar) {
			// FatJar generation (merging reference dependencies)
			Object o = getProject().getReference(Key.compileClasspath.refId());
			if (o != null && o instanceof Path) {
				Path cp = (Path) o;
				for (String path : cp.list()) {
					if (path.toLowerCase().endsWith(".jar")) {
						ZipFileSet zip = new ZipFileSet();
						zip.setProject(getProject());
						zip.setSrc(new File(path));
						zip.setExcludes("about.html, META-INF/*.DSA, META-INF/*.SF, META-INF/*.RSA, META-INF/LICENSE*, META-INF/NOTICE*, META-INF/ASL2.0, META-INF/eclipse.inf");
						addZipfileset(zip);
					}
				}
			}
		}
		
		if (excludes == null) {
			excludes = Toolkit.DEFAULT_EXCLUDES;
		}

		// compiled output of this project
		File outputFolder = build.getConfig().getOutputDirectory(Scope.compile);
		FileSet outputSet = new FileSet();
		outputSet.setProject(getProject());
		outputSet.setDir(outputFolder);
		if (includes != null) {
			outputSet.setIncludes(includes);
		}
		outputSet.setExcludes(excludes);		
		addFileset(outputSet);
		
		// add the output folders of linked projects
		for (Build linkedProject : build.getSolver().getLinkedModules()) {
			FileSet projectOutputSet = new FileSet();
			projectOutputSet.setProject(getProject());
			File dir = linkedProject.getConfig().getOutputDirectory(Scope.compile);
			projectOutputSet.setDir(dir);
			if (includes != null) {
				projectOutputSet.setIncludes(includes);
			}
			projectOutputSet.setExcludes(excludes);		
			addFileset(projectOutputSet);
		}
		
		if (getDestFile() == null) {
			setDestFile(build.getBuildArtifact(classifier));
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

		// optionally inject MxLauncher utility
		if (launcher != null) {
			if (launcher.getName().equals(MxLauncher.class.getName().replace('.', '/') + ".class")) {
				// inject MxLauncher into the output folder of the project
				for (String cn : Arrays.asList(MxLauncher.class.getName(), MxLauncher.class.getName() + "$1")) {						
					try {
						String fn = cn.replace('.', '/') + ".class";
						InputStream is = MxLauncher.class.getResourceAsStream("/" + fn);
						if (is == null) {
							continue;
						}
						build.getConsole().log("Injecting {0} into output folder", cn);
						File file = new File(outputFolder, fn.replace('/', File.separatorChar));
						if (file.exists()) {
							file.delete();
						}
						file.getParentFile().mkdirs();
						FileOutputStream os = new FileOutputStream(file, false);
						byte [] buffer = new byte[4096];
						int len = 0;
						while ((len = is.read(buffer)) > 0) {
							os.write(buffer,  0,  len);
						}
						is.close();
						os.flush();
						os.close();
					} catch (Exception e) {
						build.getConsole().error(e, "Failed to inject {0} into {1}",
								launcher.getName(), outputFolder);
					}
				}
			}
		}
		
		long start = System.currentTimeMillis();
		super.execute();

		if (fatjar) {
			// try to merge duplicate META-INF/services files
			JarUtils.mergeMetaInfServices(console, destFile);
		}
		
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
			
			List<File> folders = build.getConfig().getSourceDirectories(Scope.compile);
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
		
		setManifest(mft, "Commit-Id", Key.commitId);
		
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

			File tmpFile = new File(build.getConfig().getOutputDirectory(null), "pom.properties");			
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
			File tmpFile = new File(build.getConfig().getOutputDirectory(null), "pom.xml");
			FileUtils.writeContent(tmpFile, build.getPom().toXML(false));

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
