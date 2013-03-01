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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
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
	private final Date buildDate;
	
	public Build(File configFile, File basedir) throws MaxmlException, IOException {
		this.config = new BuildConfig(configFile, basedir);
		
		this.console = new Console(config.isColor());
		this.console.setDebug(config.isDebug());

		this.solver = new Solver(console, config);
		this.buildDate = new Date();
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
	
	public Date getDate() {
		return buildDate;
	}
	
	public String getBuildDate() {
		return new SimpleDateFormat("yyyy-MM-dd").format(buildDate);
	}

	public String getBuildTimestamp() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(buildDate);
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
	
	public File getBuildArtifact(String classifier) {
		String name = config.getPom().artifactId;
		if (!StringUtils.isEmpty(config.getPom().version)) {
			name += "-" + config.getPom().version;
		}
		if (StringUtils.isEmpty(classifier)) {
			classifier = config.getPom().classifier;
		}
		if (!StringUtils.isEmpty(classifier)) {
			name += "-" + classifier;
		}
		return new File(getConfig().getTargetDirectory(), name + ".jar");
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
					|| project.apply(Toolkit.APPLY_ECLIPSE_VAR))) {
				writeEclipseClasspath();
				writeEclipseProject();
				console.notice(1, "rebuilt Eclipse configuration");
				applied = true;
			}

            // create/update IntelliJ IDEA configuration files
            if (solutionBuilt && (project.apply(Toolkit.APPLY_INTELLIJ)
                    || project.apply(Toolkit.APPLY_INTELLIJ_VAR))) {
           		writeIntelliJProject();
           		writeIntelliJAnt();
                writeIntelliJClasspath();
                console.notice(1, "rebuilt IntelliJ IDEA configuration");
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
	
	private File getIDEOutputFolder(Scope scope) {
		File baseFolder = new File(config.getProjectDirectory(), "bin");
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
		if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no classpath to write
    		return;
    	}

		File projectFolder = config.getProjectDirectory();
		
		List<SourceDirectory> sourceDirs = new ArrayList<SourceDirectory>();
		sourceDirs.addAll(config.getProjectConfig().getSourceDirectories());
		sourceDirs.addAll(config.getProjectConfig().getResourceDirectories());
		StringBuilder sb = new StringBuilder();
		for (SourceDirectory sourceFolder : sourceDirs) {
			if (Scope.site.equals(sourceFolder.scope)) {
				continue;
			}
			if (sourceFolder.scope.isDefault()) {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" />\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources())));
			} else {
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" output=\"{1}\" />\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources()), FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(sourceFolder.scope))));
			}
		}
		
		// determine how to output dependencies (fixed-path or variable-relative)
		String kind = getConfig().getProjectConfig().apply(Toolkit.APPLY_ECLIPSE_VAR) ? "var" : "lib";
		boolean extRelative = getConfig().getProjectConfig().dependencyDirectory != null && getConfig().getProjectConfig().dependencyDirectory.exists();
		
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
						File baseFolder = config.getProjectConfig().getDependencyDirectory();
						jar = new File(baseFolder, jar.getName());
						
						// relative to project dependency source folder
						baseFolder = config.getProjectConfig().getDependencySourceDirectory();
						srcJar = new File(baseFolder, srcJar.getName());
						
						jarPath = FileUtils.getRelativePath(projectFolder, jar);
						srcPath = FileUtils.getRelativePath(projectFolder, srcJar);
					} else {
						// absolute, hard-coded path to Moxie root
						jarPath = jar.getAbsolutePath();
						srcPath = srcJar.getAbsolutePath();
					}
				}
				if (!jar.exists()) {
					console.error("Excluding {0} from Eclipse classpath because artifact does not exist!", dependency.getCoordinates());
					continue;
				}
				if (srcJar.exists() && srcJar.length() > 1024) {
					// has non-placeholder sources jar
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" sourcepath=\"{2}\" />\n", kind, jarPath, srcPath));
				} else {
					// no sources
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" />\n", kind, jarPath));
				}
			}
		}
		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.compile))));
				
		for (Build linkedProject : solver.getLinkedModules()) {
			String projectName = null;
			File dotProject = new File(linkedProject.config.getProjectDirectory(), ".project");
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
					scanner.close();
				} catch (FileNotFoundException e) {
				}
			} else {
				// use folder name
				projectName = linkedProject.config.getProjectDirectory().getName();
			}
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\" />\n", projectName));
		}
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\" />\n");
		
		StringBuilder file = new StringBuilder();
		file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		file.append("<classpath>\n");
		file.append(StringUtils.insertHardTab(sb.toString()));
		file.append("</classpath>");
		
		FileUtils.writeContent(new File(projectFolder, ".classpath"), file.toString());
	}
	
	private void writeEclipseProject() {
    	if (!config.getModules().isEmpty()) {
    		// do not write project file for a parent descriptor
    		return;
    	}
		File dotProject = new File(config.getProjectDirectory(), ".project");
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
		if (config.getSourceDirectories().size() > 0) {
			sb.append("\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n");
			sb.append("\t\t\t<arguments>\n");
			sb.append("\t\t\t</arguments>\n");
		}
		sb.append("\t\t</buildCommand>\n");
		sb.append("\t</buildSpec>\n");
		sb.append("\t<natures>\n");
		if (config.getSourceDirectories().size() > 0) {
			sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n");
		}
		sb.append("\t</natures>\n");
		sb.append("</projectDescription>\n\n");
		
		FileUtils.writeContent(dotProject, sb.toString());
	}
	
	private void writeIntelliJProject() {
    	if (config.getModules().isEmpty()) {
    		// no modules to write project files
    		return;
    	}
    	
		ToolkitConfig project = config.getProjectConfig();
		
		File dotIdea = new File(project.baseDirectory, ".idea");
		dotIdea.mkdirs();
		
		// Group name prefers name attribute, but will use groupId if required
		String groupName = project.pom.getGroupId();
		if (!project.pom.getArtifactId().equals(project.pom.getName())) {
			groupName = project.pom.getName();
		}
		
		List<Module> modules = new ArrayList<Module>(config.getModules());
		Collections.sort(modules);
		
        StringBuilder sb = new StringBuilder();
		for (Module module : modules) {
			File moduleFolder = new File(project.baseDirectory, module.folder);
			File configFile = new File(moduleFolder, module.descriptor);
			if (!configFile.exists()) {
				continue;
			}
			ToolkitConfig moduleConfig;
			try {
				moduleConfig = new ToolkitConfig(configFile, moduleFolder, Toolkit.MOXIE_DEFAULTS);
				if (StringUtils.isEmpty(moduleConfig.getPom().artifactId)) {
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it has no artifactId!", module.folder);
					continue;
				}
				if (moduleConfig.getPom().isPOM()) {
					// skip pom modules
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it is a POM module!", module.folder);
					continue;
				}
				if (moduleConfig.getSourceDirectories().isEmpty()) {
					// skip modules without source folders
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it has no source directories!", module.folder);
					continue;
				}
				sb.append(format("<module fileurl=\"file://$PROJECT_DIR$/{0}/{1}.iml\" filepath=\"$PROJECT_DIR$/{0}/{1}.iml\" group=\"{2}\" />\n",
						module.folder, moduleConfig.getPom().artifactId, groupName));	
			} catch (Exception e) {
				console.error(e, "Failed to parse {0} for module {1}!", module.descriptor, module.folder);
			}
		}

        StringBuilder modulesStr = new StringBuilder();
        modulesStr.append("<modules>\n");
        modulesStr.append(StringUtils.insertHalfTab(sb.toString()));
        modulesStr.append("</modules>");

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"ProjectModuleManager\">\n");
        component.append(StringUtils.insertHalfTab(modulesStr.toString()));
        component.append("</component>");
		
        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<project version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</project>\n\n");
        FileUtils.writeContent(new File(dotIdea, "modules.xml"), file.toString());
	}
	
	private void writeIntelliJAnt() {
    	if (config.getModules().isEmpty()) {
    		// no modules to write project files
    		return;
    	}
    	
		ToolkitConfig project = config.getProjectConfig();
		
		File dotIdea = new File(project.baseDirectory, ".idea");
		dotIdea.mkdirs();
    	File antFile = new File(dotIdea, "ant.xml");
    	if (antFile.exists()) {
    		// do not attempt to update this file
    		return;
    	}
		
        StringBuilder sb = new StringBuilder();

        File rootAnt = new File(project.baseDirectory, "build.xml");
        if (rootAnt.exists()) {
        	sb.append(format("<buildFile url=\"file://$PROJECT_DIR$/{0}\" />\n", rootAnt.getName()));
        }

		for (Module module : project.modules) {
			File moduleFolder = new File(project.baseDirectory, module.folder);
			File scriptFile = new File(moduleFolder, module.script);
			if (!scriptFile.exists()) {
				continue;
			}
			sb.append(format("<buildFile url=\"file://$PROJECT_DIR$/{0}/{1}\" />\n", module.folder, module.script));
		}

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"AntConfiguration\">\n");
        component.append(StringUtils.insertHalfTab(sb.toString()));
        component.append("</component>");
		
        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<project version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</project>\n\n");
        FileUtils.writeContent(antFile, file.toString());
	}

    private void writeIntelliJClasspath() {
    	if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no classpath to write
    		return;
    	}

        File projectFolder = config.getProjectDirectory();

        StringBuilder sb = new StringBuilder();
        sb.append(format("<output url=\"file://$MODULE_DIR$/{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.compile))));
        sb.append(format("<output-test url=\"file://$MODULE_DIR$/{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.test))));
        sb.append("<exclude-output />\n");
        sb.append("<content url=\"file://$MODULE_DIR$\">\n");
		List<SourceDirectory> sourceDirs = new ArrayList<SourceDirectory>();
		sourceDirs.addAll(config.getProjectConfig().getSourceDirectories());
		sourceDirs.addAll(config.getProjectConfig().getResourceDirectories());
        StringBuilder sf = new StringBuilder();
        for (SourceDirectory sourceFolder : sourceDirs) {
            if (Scope.site.equals(sourceFolder.scope)) {
                continue;
            }
            sf.append(format("<sourceFolder url=\"file://$MODULE_DIR$/{0}\" isTestSource=\"{1}\" />\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources()), Scope.test.equals(sourceFolder.scope)));
        }
        sb.append(StringUtils.insertHalfTab(sf.toString()));
        sb.append("</content>\n");
        sb.append("<orderEntry type=\"sourceFolder\" forTests=\"false\" />\n");

        // determine how to output dependencies (fixed-path or variable-relative)
        boolean variableRelative = false;
        boolean extRelative = getConfig().getProjectConfig().dependencyDirectory != null && getConfig().getProjectConfig().dependencyDirectory.exists();

        // always link classpath against Moxie artifact cache
        Set<Dependency> dependencies = new LinkedHashSet<Dependency>();
        dependencies.addAll(solver.solve(Scope.compile));
        // add unique test classpath items
        dependencies.addAll(solver.solve(Scope.test));
        
        for (Dependency dependency : dependencies) {
            Scope scope = null;
            File jar = null;
            File srcJar = null;
            String jarPath = null;
            String srcPath = null;

            if (dependency instanceof SystemDependency) {
                SystemDependency sys = (SystemDependency) dependency;
                jar = new File(sys.path);
                jarPath = format("jar://{0}!/", jar.getAbsolutePath());
            } else {
            	if (dependency.definedScope == null) {
            		getConsole().error("{0} is missing a definedScope!", dependency.getCoordinates());
            	}
                // COMPILE scope is always implied and unnecessary in iml file
                if (!dependency.definedScope.isDefault()) {
                    scope = dependency.definedScope;
                }

                jar = solver.getMoxieCache().getArtifact(dependency, dependency.type);
                Dependency sources = dependency.getSourcesArtifact();
                srcJar = solver.getMoxieCache().getArtifact(sources, sources.type);

                if (variableRelative) {
                    // relative to MOXIE_HOME
                    jarPath = format("jar://$" + Toolkit.MOXIE_HOME + "$/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), jar));
                    srcPath = format("jar://$" + Toolkit.MOXIE_HOME + "$/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), srcJar));
                } else {
                    // filesystem path
                    if (extRelative) {
                        // relative to project dependency folder
                        File baseFolder = config.getProjectConfig().getDependencyDirectory();
                        jar = new File(baseFolder, jar.getName());

                        // relative to project dependency source folder
                        baseFolder = config.getProjectConfig().getDependencySourceDirectory();
                        srcJar = new File(baseFolder, srcJar.getName());

                        jarPath = format("jar://$MODULE_DIR$/{0}!/", FileUtils.getRelativePath(projectFolder, jar));
                        srcPath = format("jar://$MODULE_DIR$/{0}!/", FileUtils.getRelativePath(projectFolder, srcJar));
                    } else {
                        // relative to USER_HOME
                        jarPath = format("jar://$USER_HOME$/.moxie/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), jar));
                        srcPath = format("jar://$USER_HOME$/.moxie/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), srcJar));
                    }
                }
            }

			if (!jar.exists()) {
				console.error("Excluding {0} from IntelliJ IDEA classpath because artifact does not exist!", dependency.getCoordinates());
				continue;
			}

            if (scope == null) {
                sb.append("<orderEntry type=\"module-library\">\n");
            } else {
                sb.append(format("<orderEntry type=\"module-library\" scope=\"{0}\">\n", scope.name().toUpperCase()));
            }
            StringBuilder lib = new StringBuilder();
            lib.append(format("<library name=\"{0}\">\n", jar.getName()));
            StringBuilder CLASSES = new StringBuilder();
            CLASSES.append("<CLASSES>\n");
            CLASSES.append(StringUtils.insertHalfTab(format("<root url=\"{0}\" />\n", jarPath)));
            CLASSES.append("</CLASSES>\n");
            lib.append(StringUtils.insertHalfTab(CLASSES.toString()));
            lib.append(StringUtils.insertHalfTab("<JAVADOC />\n"));
            if (srcJar != null && srcJar.exists() && srcJar.length() > 1024) {
                StringBuilder SOURCES = new StringBuilder();
                SOURCES.append("<SOURCES>\n");
                SOURCES.append(StringUtils.insertHalfTab(format("<root url=\"{0}\" />\n", srcPath)));
                SOURCES.append("</SOURCES>\n");
                lib.append(StringUtils.insertHalfTab(SOURCES.toString()));
            } else {
                lib.append(StringUtils.insertHalfTab("<SOURCES />\n"));
            }
            lib.append("</library>\n");
            sb.append(StringUtils.insertHalfTab(lib.toString()));
            sb.append("</orderEntry>\n");
        }

        for (Build linkedProject : solver.getLinkedModules()) {
            String artifactId = linkedProject.getPom().getArtifactId();
            sb.append(format("<orderEntry type=\"module\" module-name=\"{0}\" />\n", artifactId));
        }
        sb.append("<orderEntry type=\"inheritedJdk\" />\n");

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"NewModuleRootManager\" inherit-compiler-output=\"false\">\n");
        component.append(StringUtils.insertHalfTab(sb.toString()));
        component.append("</component>");

        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</module>\n\n");
        
        String name = config.getPom().getArtifactId();
        FileUtils.writeContent(new File(projectFolder, name + ".iml"), file.toString());
    }
	
	private void writePOM() {
		if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no POM to write
    		return;
    	}
		StringBuilder sb = new StringBuilder();
		sb.append("<!-- This file is automatically generated by Moxie. DO NOT HAND EDIT! -->\n");
		sb.append(getPom().toXML(false));
		FileUtils.writeContent(new File(config.getProjectDirectory(), "pom.xml"), sb.toString());
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
			console.log("source directories");
			for (SourceDirectory directory : config.getSourceDirectories()) {
				console.sourceDirectory(directory);
			}
			console.separator();
			console.log("resource directories");
			for (SourceDirectory directory : config.getResourceDirectories()) {
				console.sourceDirectory(directory);
			}
			console.separator();

			console.log("output directory");
			console.log(1, config.getOutputDirectory(null).toString());
			console.separator();
		}
	}
	
	void describeSettings() {
		if (config.isVerbose()) {
			console.log("Moxie parameters");
			describe(Toolkit.MX_ROOT, solver.getMoxieCache().getRootFolder().getAbsolutePath());
			describe(Toolkit.MX_ONLINE, "" + solver.isOnline());
			describe(Toolkit.MX_UPDATEMETADATA, "" + solver.isUpdateMetadata());
			describe(Toolkit.MX_DEBUG, "" + config.isDebug());
			describe(Toolkit.MX_VERBOSE, "" + config.isVerbose());
			describe(Toolkit.Key.mavenCacheStrategy, config.getMavenCacheStrategy().name());
			
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
