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
package com.maxtk;

import static java.text.MessageFormat.format;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.maxtk.Constants.Key;
import com.maxtk.Dependency.Extension;
import com.maxtk.Dependency.Scope;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

/**
 * Represents the current build (build.maxml, settings.maxml, and all instance
 * state.
 */
public class Build {
	
	public final Settings settings;
	public final Config conf;
	public final ArtifactCache artifactCache;
	public Console console;
	
	private final Map<Scope, Set<Dependency>> solutions;
	
	private final File configFile;
	private final File projectFolder;
	
	public Build() throws MaxmlException, IOException {
		this("build.maxml");
	}
	
	public Build(String filename) throws MaxmlException, IOException {
		this.configFile = new File(filename);
		this.projectFolder = configFile.getParentFile();
		
		this.settings = Settings.load();
		this.conf = Config.load(configFile);
		
		this.artifactCache = new MaxillaCache();
		this.solutions = new HashMap<Scope, Set<Dependency>>();
		this.console = new Console();
	}
	
	public void setup() {
		Repository central = new Repository("MavenCentral", Constants.MAVENCENTRAL_URL);
		for (String mavenUrl : conf.repositoryUrls) {
			if (Constants.MAVENCENTRAL_URL.equalsIgnoreCase(mavenUrl)
					|| Constants.MAVENCENTRAL.equalsIgnoreCase(mavenUrl)) {
				// MavenCentral
				settings.add(central);
			} else if ("gitblit".equalsIgnoreCase(mavenUrl)) {
				// Gitblit Maven Proxy
				String url = getGitblitUrl();
				if (!StringUtils.isEmpty(url)) {
					settings.add(new Repository("Gitblit", url));	
				}
			} else {
				// unidentified repository
				settings.add(new Repository(null, mavenUrl));
			}
		}
		settings.add(central);
		settings.add(new GoogleCode());
		
		// bootstrap
		loadDependency(new Dependency("org.fusesource.jansi:jansi:1.8"));

		// try to replace the console with the JansiConsole
		try {
			Class.forName("org.fusesource.jansi.AnsiConsole");
			Class<?> jansiConsole = Class.forName("com.maxtk.opt.JansiConsole");
			console = (Console) jansiConsole.newInstance();
		} catch (Throwable t) {
		}

		console.header();
		console.log("{0} v{1}", getPom().name, getPom().version);
		console.header();

		describeConfig();
		describeSettings();
		
		retrievePOMs();
		retrieveJARs();
		
		// create/update Eclipse configuration files
		if (conf.configureEclipseClasspath) {
			writeEclipseClasspath();
			console.separator();
			console.log("rebuilt eclipse .classpath");
		}
	}
	
	public Pom getPom() {
		return conf.pom;
	}
	
	public List<SourceFolder> getSourceFolders() {
		return conf.sourceFolders;
	}

	public List<String> getProjects() {
		return conf.projects;
	}
	
	/**
	 * Attempts to return a Gitblit Maven proxy url.  This requires that the
	 * project be version-controlled with Git and that the "origin" is an http
	 * or https uri which "looks" like a Gitblit repository url.
	 * 
	 * @return
	 */
	private String getGitblitUrl() {
		File folder = new File("").getAbsoluteFile();
		File configFile = null;
		if (hasGitRepo(folder)) {
			configFile = new File(folder, ".git/config");
		} else if (hasGitRepo(folder.getParentFile())) {
			configFile = new File(folder.getParentFile(), ".git/config");
		}
		if (configFile == null) {
			return null;
		}
		String content = FileUtils.readContent(configFile,"\n");
		String [] lines = content.split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].startsWith("[remote \"origin\"")) {
				for (int j = i + 1; j < lines.length; j++) {
					if (lines[j].contains("[remote \"")) {
						// didn't find url :(
						break;
					} else if (lines[j].trim().startsWith("url")) {
						String url = lines[j].substring(lines[j].indexOf('=') + 1).trim();
						if (url.toLowerCase().startsWith("http://") 
								|| url.toLowerCase().startsWith("https://")) {
							if (url.indexOf("/git/") > -1) {
								// this looks like a Gitblit url
								url = url.substring(0, url.indexOf("/git/"));
								return url += "/maven2";
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	private boolean hasGitRepo(File folder) {
		return new File(folder, ".git/config").exists();
	}
	
	public ArtifactCache getArtifactCache() {
		return artifactCache;
	}
	
	public Collection<Repository> getRepositories() {
		return settings.repositories;
	}
	
	public void retrievePOMs() {
		// retrieve POMs for all dependencies in all scopes
		for (Scope scope : conf.pom.getScopes()) {
			for (Dependency dependency : conf.pom.getDependencies(scope, 0)) {
				retrievePOM(dependency);
			}
		}
	}
	
	public void retrieveJARs() {
		// solve dependencies for compile, runtime, and test scopes
		for (Scope scope : new Scope [] { Scope.compile, Scope.runtime, Scope.test }) {
			console.separator();
			console.scope(scope);
			console.separator();
			Set<Dependency> solution = solve(scope);
			if (solution.size() == 0) {
				console.log(" none");
			} else {
				for (Dependency dependency : solution) {
					console.dependency(dependency);
					retrieveArtifact(dependency, true);
				}
			}
		}
	}
	
	public Set<Dependency> solve(Scope solutionScope) {
		if (solutions.containsKey(solutionScope)) {
			return solutions.get(solutionScope);
		}
		Set<Dependency> solution = new LinkedHashSet<Dependency>();
		for (Dependency dependency : conf.pom.getDependencies(solutionScope, 0)) {
			solution.add(dependency);
			solution.addAll(solve(solutionScope, dependency));
		}
		
		// TODO version conflicts, nearness resolution (ring)
		solutions.put(solutionScope, solution);
		return solution;
	}
	
	private List<Dependency> solve(Scope scope, Dependency dependency) {
		List<Dependency> resolved = new ArrayList<Dependency>();
		if (!dependency.resolveDependencies) {
			return resolved;
		}
		File pomFile = retrievePOM(dependency);
		if (pomFile == null || !pomFile.exists()) {
			return resolved;
		}

		Pom pom = PomReader.readPom(artifactCache, dependency);
		List<Dependency> dependencies = pom.getDependencies(scope, dependency.ring + 1);
		if (dependencies.size() > 0) {			
			for (Dependency dep : dependencies) {
				resolved.add(dep);
				resolved.addAll(solve(scope, dep));
			}
		}
		return resolved;
	}
	
	private File retrievePOM(Dependency dependency) {
		if (!dependency.isMavenObject()) {
			return null;
		}
		if (StringUtils.isEmpty(dependency.version)) {
			return null;
		}
		File pomFile = artifactCache.getFile(dependency,  Extension.POM);
		if (!pomFile.exists()) {
			// download the POM
			for (Repository repository : settings.repositories) {
				if (!repository.isMavenSource()) {
					// skip non-Maven repositories
					continue;
				}
				File retrievedFile = repository.download(this, dependency, Extension.POM);
				if (retrievedFile != null && retrievedFile.exists()) {
					pomFile = retrievedFile;
					break;
				}
			}
		}

		// Read POM
		if (pomFile.exists()) {
			try {
				Pom pom = PomReader.readPom(artifactCache, dependency);
				// retrieve parent POM
				if (pom.hasParentDependency()) {			
					Dependency parent = pom.getParentDependency();
					parent.ring = dependency.ring;
					retrievePOM(parent);
				}
				
				// retrieve all dependent POMs
				for (Scope scope : pom.getScopes()) {
					for (Dependency dep : pom.getDependencies(scope, dependency.ring + 1)) {
						retrievePOM(dep);
					}
				}
			} catch (Exception e) {
				console.error(e);
			}
			return pomFile;
		}		
		return null;
	}
	
	/**
	 * Download an artifact from a local or remote artifact repository.
	 * 
	 * @param dependency
	 *            the dependency to download
	 * @param forProject
	 *            true if this is a project dependency, false if this is a
	 *            Maxilla dependency
	 * @return
	 */
	private void retrieveArtifact(Dependency dependency, boolean forProject) {
		for (Repository repository : settings.repositories) {
			if (!repository.isSource(dependency)) {
				// dependency incompatible with repository
				continue;
			}
			Extension[] jarTypes = { Extension.LIB, Extension.SRC };
			for (Extension fileType : jarTypes) {
				// check to see if we already have the artifact
				File cachedFile = artifactCache.getFile(dependency, fileType);
				if (!cachedFile.exists()) {
					cachedFile = repository.download(this, dependency, fileType);
				}

				if (cachedFile != null && cachedFile.exists()) {
					// optionally copy artifact to project-specified folder
					if (forProject && conf.dependencyFolder != null) {
						File projectFile = new File(conf.dependencyFolder, cachedFile.getName());
						if (!projectFile.exists()) {
							try {
								projectFile.getParentFile().mkdirs();
								FileUtils.copy(projectFile.getParentFile(), cachedFile);
							} catch (IOException e) {
								throw new RuntimeException("Error writing to file " + projectFile, e);
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Downloads an internal dependency needed for runtime operation of Maxilla.
	 * This dependency is automatically loaded by the classloader.
	 * 
	 * @param dependencies
	 */
	public void loadDependency(Dependency... dependencies) {
		// solve the classpath solution for the Maxilla runtime dependencies
		Pom pom = new Pom();
		for (Dependency dependency : dependencies) {
			retrievePOM(dependency);
			pom.addDependency(dependency, Scope.runtime);
		}
		Set<Dependency> solution = new LinkedHashSet<Dependency>();
		for (Dependency dependency : pom.getDependencies(Scope.runtime, 0)) {
			solution.add(dependency);
			solution.addAll(solve(Scope.runtime, dependency));
		}		
		for (Dependency dependency : solution) {
			retrieveArtifact(dependency, false);
		}

		// load dependency onto executing classpath from Maxilla cache
		Class<?>[] PARAMETERS = new Class[] { URL.class };
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		for (Dependency dependency : dependencies) {
			File file = artifactCache.getFile(dependency, Extension.LIB);
			if (file.exists()) {
				try {
					URL u = file.toURI().toURL();
					Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
					method.setAccessible(true);
					method.invoke(sysloader, new Object[] { u });
				} catch (Throwable t) {
					console.error(t, "Error, could not add {0} to system classloader", file.getPath());					
				}
			}
		}
	}
	
	public List<File> getClasspath(Scope scope) {
		File projectFolder = null;
		if (conf.dependencyFolder != null && conf.dependencyFolder.exists()) {
			projectFolder = conf.dependencyFolder;
		}
		
		Set<Dependency> dependencies = solve(scope);
		List<File> jars = new ArrayList<File>();
		for (Dependency dependency : dependencies) {
			File jar = artifactCache.getFile(dependency, Extension.LIB); 
			if (projectFolder != null) {
				File pJar = new File(projectFolder, jar.getName());
				if (pJar.exists()) {
					jar = pJar;
				}
			}
			jars.add(jar);
		}
		return jars;
	}
	
	public File getOutputFolder(Scope scope) {
		if (scope == null) {
			return conf.outputFolder;
		}
		switch (scope) {
		case test:
			return new File(conf.outputFolder, "tests");
		default:
			return new File(conf.outputFolder, "classes");
		}
	}
	
	private File getEclipseOutputFolder(Scope scope) {
		File baseFolder = new File(projectFolder, "bin");
		if (scope == null) {
			return baseFolder;
		}
		switch (scope) {
		case test:
			return new File(baseFolder, "tests");
		default:
			return new File(baseFolder, "classes");
		}
	}
	
	public File getTargetFolder() {
		return conf.targetFolder;
	}
	
	public File getProjectFolder() {
		return projectFolder;
	}
	
	public File getSiteSourceFolder() {
		return new File(projectFolder, "src/site");
	}

	public File getSiteOutputFolder() {
		return new File(getTargetFolder(), "site");
	}

	private void writeEclipseClasspath() {
		List<File> jars = getClasspath(Scope.test);
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<classpath>\n");
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n");
		for (SourceFolder sourceFolder : conf.sourceFolders) {
			if (sourceFolder.scope.isDefault()) {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\"/>\n", sourceFolder.folder));
			} else {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" output=\"{1}\"/>\n", sourceFolder.folder, getEclipseOutputFolder(sourceFolder.scope)));
			}
		}
		for (File jar : jars) {			
			File srcJar = new File(jar.getParentFile(), jar.getName().substring(0, jar.getName().lastIndexOf('.')) + "-sources.jar");
			if (srcJar.exists()) {
				// have sources
				sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" sourcepath=\"{1}\" />\n", jar.getAbsolutePath(), srcJar.getAbsolutePath()));
			} else {
				// no sources
				sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" />\n", jar.getAbsolutePath()));
			}
		}
		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\"/>\n", getEclipseOutputFolder(Scope.compile)));
				
		for (String project : conf.projects) {
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\"/>\n", project));
		}
		sb.append("</classpath>");
		
		FileUtils.writeContent(new File(".classpath"), sb.toString());
	}
	
	void describeConfig() {
		Pom pom = conf.pom;
		console.log("project metadata");
		describe(Key.name, pom.name);
		describe(Key.description, pom.description);
		describe(Key.groupId, pom.groupId);
		describe(Key.artifactId, pom.artifactId);
		describe(Key.version, pom.version);
		describe(Key.vendor, pom.vendor);
		describe(Key.url, pom.url);
		console.separator();

		console.log("source folders");
		for (SourceFolder folder : conf.sourceFolders) {
			console.sourceFolder(folder);
		}
		console.separator();

		console.log("output folder");
		console.log(1, conf.outputFolder.toString());
		console.separator();
	}
	
	void describeSettings() {
		console.log("dependency sources");
		for (Repository repository : settings.repositories) {
			console.log(1, repository.toString());
			console.download(repository.getArtifactUrl());
			console.log();
		}

		List<Proxy> actives = settings.getActiveProxies();
		if (actives.size() > 0) {
			console.log("proxy settings");
			for (Proxy proxy : actives) {
				if (proxy.active) {
					describe("proxy", proxy.host + ":" + proxy.port);
				}
			}
			console.separator();
		}
	}

	void describe(Enum<?> key, String value) {
		describe(key.name(), value);
	}
	
	void describe(String key, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		console.key(StringUtils.leftPad(key, 12, ' '), value);
	}
	
	@Override
	public String toString() {
		return "Build (" + conf.pom.toString() + ")";
	}
}
