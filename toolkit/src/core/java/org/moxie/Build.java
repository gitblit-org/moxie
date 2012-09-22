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
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlException;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


/**
 * Build is a container class for the effective build configuration, the console,
 * and the solver.
 */
public class Build {

	private final BuildConfig config;
	private final Console console;
	private final Solver solver;
	
	public Build(File configFile, File basedir) throws MaxmlException, IOException {
		this.config = new BuildConfig(configFile, basedir);
		
		this.console = new Console(config.isColor());
		this.console.setDebug(config.isDebug());

		this.solver = new Solver(console, config);
	}
	
	@Override
	public int hashCode() {
		return config.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Build) {
			return config.getProjectConfig().file.equals(((Build) o).getConfig().getProjectConfig().file);
		}
		return false;
	}
	
	public Solver getSolver() {
		return solver;
	}
	
	public BuildConfig getConfig() {
		return config;
	}
	
	public Console getConsole() {
		return console;
	}
	
	public Pom getPom() {
		return config.getPom();
	}
	
	public void setup() {
		if (config.getRepositories().isEmpty()) {
			console.warn("No dependency repositories have been defined!");
		}

		boolean solutionBuilt = solver.solve();
		ToolkitConfig project = config.getProjectConfig();
		if (project.apply.size() > 0) {
			console.separator();
			console.log("apply");
			boolean applied = false;
			
			// create/update Eclipse configuration files
			if (solutionBuilt && (project.apply(Toolkit.APPLY_ECLIPSE)
					|| project.apply(Toolkit.APPLY_ECLIPSE_VAR)
					|| project.apply(Toolkit.APPLY_ECLIPSE_EXT))) {
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
	
	private File getEclipseOutputFolder(Scope scope) {
		File baseFolder = new File(config.getProjectFolder(), "bin");
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
	
	private void writeEclipseClasspath() {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<classpath>\n");
		File projectFolder = config.getProjectFolder();
		for (SourceFolder sourceFolder : config.getProjectConfig().getSourceFolders()) {
			if (Scope.site.equals(sourceFolder.scope)) {
				continue;
			}
			if (sourceFolder.scope.isDefault()) {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\"/>\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources())));
			} else {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" output=\"{1}\"/>\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources()), FileUtils.getRelativePath(projectFolder, getEclipseOutputFolder(sourceFolder.scope))));
			}
		}
		
		// determine how to output dependencies (fixed-path or variable-relative)
		String kind = getConfig().getProjectConfig().apply(Toolkit.APPLY_ECLIPSE_VAR) ? "var" : "lib";
		boolean extRelative = getConfig().getProjectConfig().apply(Toolkit.APPLY_ECLIPSE_EXT);
		
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
				String jarPath;
				String srcPath;
				if ("var".equals(kind)) {
					// relative to MOXIE_HOME
					jarPath = Toolkit.MOXIE_HOME + "/" + FileUtils.getRelativePath(config.getMoxieRoot(), jar);
					srcPath = Toolkit.MOXIE_HOME + "/" + FileUtils.getRelativePath(config.getMoxieRoot(), srcJar);
				} else {
					// filesystem path
					if (extRelative) {
						// relative to project dependency folder
						File baseFolder = config.getProjectConfig().getDependencyFolder();
						jar = new File(baseFolder, jar.getName());
						
						// relative to project dependency source folder
						baseFolder = config.getProjectConfig().getDependencySourceFolder();
						srcJar = new File(baseFolder, srcJar.getName());
						
						jarPath = FileUtils.getRelativePath(projectFolder, jar);
						srcPath = FileUtils.getRelativePath(projectFolder, srcJar);
					} else {
						// absolute, hard-coded path to Moxie root
						jarPath = jar.getAbsolutePath();
						srcPath = srcJar.getAbsolutePath();
					}
				}
				if (srcJar.exists()) {
					// have sources
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" sourcepath=\"{2}\" />\n", kind, jarPath, srcPath));
				} else {
					// no sources
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" />\n", kind, jarPath));
				}
			}
		}
		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\"/>\n", FileUtils.getRelativePath(projectFolder, getEclipseOutputFolder(Scope.compile))));
				
		for (Build linkedProject : solver.getLinkedProjects()) {
			String projectName = null;
			File dotProject = new File(linkedProject.config.getProjectFolder(), ".project");
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
				projectName = linkedProject.config.getProjectFolder().getName();
			}
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\"/>\n", projectName));
		}
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n");
		sb.append("</classpath>");
		
		FileUtils.writeContent(new File(projectFolder, ".classpath"), sb.toString());
	}
	
	private void writeEclipseProject() {
		File dotProject = new File(config.getProjectFolder(), ".project");
		if (dotProject.exists()) {
			// update name and description
			try {
				StringBuilder sb = new StringBuilder();
				Pattern namePattern = Pattern.compile("\\s*?<name>(.+)</name>");
				Pattern descriptionPattern = Pattern.compile("\\s*?<comment>(.+)</comment>");
				
				boolean replacedName = false;
				boolean replacedDescription = false;
				
				Scanner scanner = new Scanner(dotProject);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					
					// replace name
					if (!replacedName) {
						Matcher m = namePattern.matcher(line);
						if (m.matches()) {
							int start = m.start(1);
							int end = m.end(1);
							//console.error("s=" + start + " e=" + end + " l=" + line);
							line = line.substring(0,  start)
									+ config.getPom().getName() + line.substring(end);
							replacedName = true;
						}
					}
					
					// replace description
					if (!replacedDescription) {
						Matcher m = descriptionPattern.matcher(line);
						if (m.matches()) {
							int start = m.start(1);
							int end = m.end(1);
							//console.error("s=" + start + " e=" + end + " l=" + line);
							line = line.substring(0,  start)
									+ (config.getPom().getDescription() == null ? "" : config.getPom().getDescription())
									+ line.substring(end);
							replacedDescription = true;
						}
					}
					
					sb.append(line).append('\n');
				}
				scanner.close();
				
				FileUtils.writeContent(dotProject, sb.toString());
			} catch (FileNotFoundException e) {
			}
			return;
		}
		
		// create file
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<projectDescription>\n");
		sb.append(MessageFormat.format("\t<name>{0}</name>\n", getPom().name));
		sb.append(MessageFormat.format("\t<comment>{0}</comment>\n", getPom().description == null ? "" : getPom().description));
		sb.append("\t<projects>\n");
		sb.append("\t</projects>\n");
		sb.append("\t<buildSpec>\n");
		sb.append("\t\t<buildCommand>\n");
		if (config.getSourceFolders().size() > 0) {
			sb.append("\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n");
			sb.append("\t\t\t<arguments>\n");
			sb.append("\t\t\t</arguments>\n");
		}
		sb.append("\t\t</buildCommand>\n");
		sb.append("\t</buildSpec>\n");
		sb.append("\t<natures>\n");
		if (config.getSourceFolders().size() > 0) {
			sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n");
		}
		sb.append("\t</natures>\n");
		sb.append("</projectDescription>\n");
		
		FileUtils.writeContent(dotProject, sb.toString());
	}
	
	private void writePOM() {
		StringBuilder sb = new StringBuilder();
		sb.append("<!-- This file is automatically generated by Moxie. DO NOT HAND EDIT! -->\n");
		sb.append(getPom().toXML(false, false));
		FileUtils.writeContent(new File(config.getProjectFolder(), "pom.xml"), sb.toString());
	}
	
	public String getCustomLess() {
		File configFile = config.getProjectConfig().file;
		String lessName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.')) + ".less";
		// prefer config-relative LESS
		File less = new File(configFile.getParentFile(), lessName);
		
		// try projectFolder-relative LESS
		if (!less.exists()) {
			less = new File(config.getProjectFolder(), lessName);
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
		Pom pom = getPom();
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

		if (config.isVerbose()) {
			console.separator();
			console.log("source folders");
			for (SourceFolder folder : config.getSourceFolders()) {
				console.sourceFolder(folder);
			}
			console.separator();

			console.log("output folder");
			console.log(1, config.getOutputFolder(null).toString());
			console.separator();
		}
	}
	
	void describeSettings() {
		if (config.isVerbose()) {
			console.log("Moxie parameters");
			describe(Toolkit.MX_ROOT, solver.getMoxieCache().getMoxieRoot().getAbsolutePath());
			describe(Toolkit.MX_ONLINE, "" + solver.isOnline());
			describe(Toolkit.MX_UPDATEMETADATA, "" + solver.isUpdateMetadata());
			describe(Toolkit.MX_DEBUG, "" + config.isDebug());
			describe(Toolkit.MX_VERBOSE, "" + config.isVerbose());
			
			console.log("dependency sources");
			if (config.getRepositories().size() == 0) {
				console.error("no dependency sources defined!");
			}
			for (Repository repository : config.getRepositories()) {
				console.log(1, repository.toString());
				console.download(repository.repositoryUrl);
				console.log();
			}

			List<Proxy> actives = config.getMoxieConfig().getActiveProxies();
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
		return "Build (" + getPom().toString() + ")";
	}
}
