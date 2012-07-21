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
package org.moxie;

import static java.text.MessageFormat.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.Base64;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


/**
 * Represents the current build (build.moxie, settings.moxie, and build state)
 */
public class Build {

	private final Set<Proxy> proxies;
	private final Set<Repository> repositories;
	private final Config moxie;
	private final Config project;
	private final Console console;
	private final Solver solver;
	
	private final File configFile;
	private final File projectFolder;
	
	private boolean verbose;
	
	public Build(File configFile, File basedir) throws MaxmlException, IOException {
		this.configFile = configFile;
		if (basedir == null) {
			this.projectFolder = configFile.getAbsoluteFile().getParentFile();
		} else {
			this.projectFolder = basedir;
		}
		
		// allow specifying Moxie root folder
		File moxieRoot = new File(System.getProperty("user.home") + "/.moxie");
		if (System.getProperty(Toolkit.MX_ROOT) != null) {
			String value = System.getProperty(Toolkit.MX_ROOT);
			if (!StringUtils.isEmpty(value)) {
				moxieRoot = new File(value);
			}
		}
		moxieRoot.mkdirs();
		
		this.moxie = new Config(new File(moxieRoot, Toolkit.MOXIE_SETTINGS), projectFolder, Toolkit.MOXIE_SETTINGS);
		this.project = new Config(configFile, projectFolder, Toolkit.MOXIE_DEFAULTS);
		
		this.proxies = new LinkedHashSet<Proxy>();
		this.repositories = new LinkedHashSet<Repository>();
		this.console = new Console(isColor());
		this.console.setDebug(isDebug());

		console.debug("determining proxies and repositories");
		determineProxies();
		determineRepositories();		
		
		this.solver = new Solver(this, moxieRoot);
	}
	
	@Override
	public int hashCode() {
		return 11 + configFile.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Build) {
			return configFile.equals(((Build) o).configFile);
		}
		return false;
	}
	
	public boolean isColor() {
		String mxColor = System.getProperty(Toolkit.MX_COLOR, null);
		if (StringUtils.isEmpty(mxColor)) {
			// use Moxie apply setting
			return moxie.apply(Toolkit.APPLY_COLOR) || project.apply(Toolkit.APPLY_COLOR);
		} else {
			// use system property to determine color
			return Boolean.parseBoolean(mxColor);
		}
	}
	
	public boolean isDebug() {
		String mxDebug = System.getProperty(Toolkit.MX_DEBUG, null);
		if (StringUtils.isEmpty(mxDebug)) {
			// use Moxie apply setting
			return moxie.apply(Toolkit.APPLY_DEBUG) || project.apply(Toolkit.APPLY_DEBUG);
		} else {
			// use system property to determine debug
			return Boolean.parseBoolean(mxDebug);
		}
	}

	public boolean isVerbose() {
		return verbose;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public Solver getSolver() {
		return solver;
	}
	
	public Console getConsole() {
		return console;
	}
	
	public void setup() {
		boolean solutionBuilt = solver.solve();
		
		if (project.apply.size() > 0) {
			console.separator();
			console.log("apply");
			boolean applied = false;
			
			// create/update Eclipse configuration files
			if (solutionBuilt && project.apply(Toolkit.APPLY_ECLIPSE)) {
				writeEclipseClasspath();
				writeEclipseProject();
				console.notice(1, "rebuilt Eclipse configuration");
				applied = true;
			}
		
			// create/update Maven POM
			if (solutionBuilt && project.apply(Toolkit.APPLY_POM)) {
				writePOM();
				console.notice(1, "rebuilt pom.xml");
				applied = true;
			}
			
			if (!applied) {
				console.log(1, "nothing applied");
			}
		}
	}
	
	private void determineProxies() {
		proxies.addAll(project.getActiveProxies());
		proxies.addAll(moxie.getActiveProxies());
	}
	
	private void determineRepositories() {
		List<RemoteRepository> registrations = new ArrayList<RemoteRepository>();
		registrations.addAll(project.registeredRepositories);
		registrations.addAll(moxie.registeredRepositories);
		
		for (String url : project.repositories) {
			if (url.equalsIgnoreCase("googlecode")) {
				// GoogleCode-sourced artifact
				repositories.add(new GoogleCode());
				continue;
			}
			for (RemoteRepository definition : registrations) {
				if (definition.url.equalsIgnoreCase(url) || definition.id.equalsIgnoreCase(url)) {
					repositories.add(new Repository(definition.id, definition.url));
					break;
				}	
			}
		}

		if (repositories.size() == 0) {
			console.warn("No dependency repositories have been defined!");
		}
	}
	
	public Config getMoxieConfig() {
		return moxie;
	}

	public Config getProjectConfig() {
		return project;
	}

	public Pom getPom() {
		return project.pom;
	}
	
	public MaxmlMap getMxJavacAttributes() {
		return project.mxjavac;
	}

	public MaxmlMap getMxJarAttributes() {
		return project.mxjar;
	}

	public MaxmlMap getMxReportAttributes() {
		return project.mxreport;
	}
	
	public Map<String, String> getExternalProperties() {
		return project.externalProperties;
	}
	
	public List<SourceFolder> getSourceFolders() {
		return project.sourceFolders;
	}

	public List<File> getSourceFolders(Scope scope) {
		List<File> folders = new ArrayList<File>();
		for (SourceFolder sourceFolder : project.sourceFolders) {
			if (scope == null || sourceFolder.scope.equals(scope)) {				
				folders.add(sourceFolder.getSources());
			}
		}
		return folders;
	}
	
	public Collection<Repository> getRepositories() {
		return repositories;
	}
	
	public java.net.Proxy getProxy(String repositoryId, String url) {
		if (proxies.size() == 0) {
			return java.net.Proxy.NO_PROXY;
		}
		for (Proxy proxy : proxies) {
			if (proxy.active && proxy.matches(repositoryId, url)) {
				return new java.net.Proxy(java.net.Proxy.Type.HTTP, proxy.getSocketAddress());
			}
		}
		return java.net.Proxy.NO_PROXY;
	}
	
	public String getProxyAuthorization(String repositoryId, String url) {
		for (Proxy proxy : proxies) {
			if (proxy.active && proxy.matches(repositoryId, url)) {
				return "Basic " + Base64.encodeBytes((proxy.username + ":" + proxy.password).getBytes());
			}
		}
		return "";
	}
	
	public File getOutputFolder(Scope scope) {
		if (scope == null) {
			return project.outputFolder;
		}
		switch (scope) {
		case test:
			return new File(project.outputFolder, "test-classes");
		default:
			return new File(project.outputFolder, "classes");
		}
	}
	
	private File getEclipseOutputFolder(Scope scope) {
		File baseFolder = new File(projectFolder, "bin");
		if (scope == null) {
			return baseFolder;
		}
		switch (scope) {
		case test:
			return new File(baseFolder, "test-classes");
		default:
			return new File(baseFolder, "classes");
		}
	}
	
	public File getTargetFile() {
		Pom pom = project.pom;
		String name = pom.groupId + "/" + pom.artifactId + "/" + pom.version + (pom.classifier == null ? "" : ("-" + pom.classifier));
		return new File(project.targetFolder, name + ".jar");
	}

	public File getReportsFolder() {
		return new File(project.targetFolder, "reports");
	}

	public File getTargetFolder() {
		return project.targetFolder;
	}
	
	public File getProjectFolder() {
		return projectFolder;
	}
	
	public File getSiteSourceFolder() {
		for (SourceFolder sourceFolder : project.sourceFolders) {
			if (Scope.site.equals(sourceFolder.scope)) {
				return sourceFolder.getSources();
			}
		}
		// default site sources folder
		return new File(projectFolder, "src/site");
	}

	public File getSiteOutputFolder() {
		for (SourceFolder sourceFolder : project.sourceFolders) {
			if (Scope.site.equals(sourceFolder.scope)) {
				return sourceFolder.getOutputFolder();
			}
		}
		// default site output folder
		return new File(getTargetFolder(), "site");
	}
	
	private void writeEclipseClasspath() {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<classpath>\n");
		for (SourceFolder sourceFolder : project.sourceFolders) {
			if (Scope.site.equals(sourceFolder.scope)) {
				continue;
			}
			if (sourceFolder.scope.isDefault()) {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\"/>\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources())));
			} else {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" output=\"{1}\"/>\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources()), FileUtils.getRelativePath(projectFolder, getEclipseOutputFolder(sourceFolder.scope))));
			}
		}
		
		// always link classpath against Moxie artifact cache
		Set<Dependency> dependencies = solver.solve(Scope.test);
		for (Dependency dependency : dependencies) {
			if (dependency instanceof SystemDependency) {
				SystemDependency sys = (SystemDependency) dependency;
				sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" />\n", FileUtils.getRelativePath(projectFolder, new File(sys.path))));
			} else {				
				File jar = solver.getMoxieCache().getArtifact(dependency, dependency.type);
				Dependency sources = dependency.getSourcesArtifact();
				File srcJar = solver.getMoxieCache().getArtifact(sources, sources.type);
				if (srcJar.exists()) {
					// have sources
					sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" sourcepath=\"{1}\" />\n", jar.getAbsolutePath(), srcJar.getAbsolutePath()));
				} else {
					// no sources
					sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" />\n", jar.getAbsolutePath()));
				}
			}
		}
		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\"/>\n", FileUtils.getRelativePath(projectFolder, getEclipseOutputFolder(Scope.compile))));
				
		for (Build linkedProject : solver.getLinkedProjects()) {
			String projectName = null;
			File dotProject = new File(linkedProject.projectFolder, ".project");
			if (dotProject.exists()) {
				// extract Eclipse project name
				console.debug("extracting project name from {0}", dotProject.getAbsolutePath());
				Pattern p = Pattern.compile("(<name>)(.+)(</name>)");
				try {
					Scanner scanner = new Scanner(dotProject);
					while (scanner.hasNextLine()) {
						scanner.nextLine();
						projectName = scanner.findInLine(p);
						if (!StringUtils.isEmpty(projectName)) {
							Matcher m = p.matcher(projectName);
							m.find();
							projectName = m.group(2).trim();
							console.debug(1, projectName);
							break;
						}
					}
				} catch (FileNotFoundException e) {
				}
			} else {
				// use folder name
				projectName = linkedProject.projectFolder.getName();
			}
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\"/>\n", projectName));
		}
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n");
		sb.append("</classpath>");
		
		FileUtils.writeContent(new File(projectFolder, ".classpath"), sb.toString());
	}
	
	private void writeEclipseProject() {
		File dotProject = new File(projectFolder, ".project");
		if (dotProject.exists()) {
			// don't recreate the project file
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<projectDescription>\n");
		sb.append(MessageFormat.format("\t<name>{0}</name>\n", project.pom.name));
		sb.append(MessageFormat.format("\t<comment>{0}</comment>\n", project.pom.description == null ? "" : project.pom.description));
		sb.append("\t<projects>\n");
		sb.append("\t</projects>\n");
		sb.append("\t<buildSpec>\n");
		sb.append("\t\t<buildCommand>\n");
		sb.append("\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n");
		sb.append("\t\t\t<arguments>\n");
		sb.append("\t\t\t</arguments>\n");
		sb.append("\t\t</buildCommand>\n");
		sb.append("\t</buildSpec>\n");
		sb.append("\t<natures>\n");
		sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n");
		sb.append("\t</natures>\n");
		sb.append("</projectDescription>\n");
		
		FileUtils.writeContent(dotProject, sb.toString());
	}
	
	private void writePOM() {
		StringBuilder sb = new StringBuilder();
		sb.append("<!-- This file is automatically generated by Moxie. DO NOT HAND EDIT! -->\n");
		sb.append(project.pom.toXML());
		FileUtils.writeContent(new File(projectFolder, "pom.xml"), sb.toString());
	}
	
	public String getCustomLess() {
		String lessName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.')) + ".less";
		// prefer config-relative LESS
		File less = new File(configFile.getParentFile(), lessName);
		
		// try projectFolder-relative LESS
		if (!less.exists()) {
			less = new File(projectFolder, lessName);
		}
		
		if (less.exists()) {
			return FileUtils.readContent(less, "\n");
		}
		
		// default CSS
		return "";
	}
	
	public void describe() {
		console.title(getPom().name, getPom().version);

		describeConfig();
		describeSettings();
	}
	
	void describeConfig() {
		Pom pom = project.pom;
		console.log("project metadata");
		describe(Key.name, pom.name);
		describe(Key.description, pom.description);
		describe(Key.groupId, pom.groupId);
		describe(Key.artifactId, pom.artifactId);
		describe(Key.version, pom.version);
		describe(Key.organization, pom.organization);
		describe(Key.url, pom.url);
		
		if (!solver.isOnline()) {
			console.separator();
			console.warn("Moxie is running offline. Network functions disabled.");
		}

		if (verbose) {
			console.separator();
			console.log("source folders");
			for (SourceFolder folder : project.sourceFolders) {
				console.sourceFolder(folder);
			}
			console.separator();

			console.log("output folder");
			console.log(1, project.outputFolder.toString());
			console.separator();
		}
	}
	
	void describeSettings() {
		if (verbose) {
			console.log("Moxie parameters");
			describe(Toolkit.MX_ROOT, solver.getMoxieCache().getMoxieRoot().getAbsolutePath());
			describe(Toolkit.MX_ONLINE, "" + solver.isOnline());
			describe(Toolkit.MX_UPDATEMETADATA, "" + solver.isUpdateMetadata());
			describe(Toolkit.MX_DEBUG, "" + isDebug());
			describe(Toolkit.MX_VERBOSE, "" + isVerbose());
			
			console.log("dependency sources");
			if (repositories.size() == 0) {
				console.error("no dependency sources defined!");
			}
			for (Repository repository : repositories) {
				console.log(1, repository.toString());
				console.download(repository.getArtifactUrl());
				console.log();
			}

			List<Proxy> actives = moxie.getActiveProxies();
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
		return "Build (" + project.pom.toString() + ")";
	}
}
