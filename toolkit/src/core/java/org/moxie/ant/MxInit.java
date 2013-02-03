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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.apache.tools.ant.util.FileUtils;
import org.moxie.Build;
import org.moxie.BuildConfig;
import org.moxie.MoxieException;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.utils.StringUtils;


public class MxInit extends MxTask {

	private String config;

	private File basedir;
	
	public MxInit() {
		super();
		setTaskName("mx:init");
	}
	
	public void setConfig(String config) {
		this.config = config;
	}
	
	public void setBasedir(File dir) {
		this.basedir = dir;
	}


	@SuppressWarnings("unchecked")
	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		if (build != null) {
			// already initialized
			return;
		}

		// load all environment variables into env property
        Map<String, String> osEnv = Execute.getEnvironmentVariables();
        for (Map.Entry<String, String> entry : osEnv.entrySet()) {
            getProject().setProperty("env." + entry.getKey(), entry.getValue());
        }
		
		// push all mx properties from ant into system 
		Map<String,String> antProperties = getProject().getProperties();
		for (Map.Entry<String, String> entry : antProperties.entrySet()) {
			if (entry.getKey().startsWith("mx.")) {
				System.setProperty(entry.getKey(), entry.getValue());
			}
		}
		
		try {
			if (basedir == null) {
				basedir = getProject().getBaseDir();
			}

			File configFile;
			if (StringUtils.isEmpty(config)) {
				// default configuration				
				configFile = new File(basedir, "build.moxie");
			} else {
				// specified configuration
				FileUtils futils = FileUtils.getFileUtils();				
				configFile = futils.resolveFile(basedir, config);
			}
			
			if (!configFile.exists()) {
				throw new MoxieException(MessageFormat.format("Failed to find Moxie descriptor {0}", configFile));
			}
			
			// parse the config files and Moxie settings
			build = new Build(configFile, basedir);
			
			BuildConfig buildConfig = build.getConfig();
			buildConfig.setVerbose(isVerbose());
			
			// set any external properties into the project
			for (Map.Entry<String, String> entry : buildConfig.getExternalProperties().entrySet()) {
				getProject().setProperty(entry.getKey(), entry.getValue());
			}
			
			build.getPom().setAntProperties(antProperties);			

			// add a reference to the full build object
			getProject().addReference(Key.build.refId(), build);			

			// output the build info
			build.describe();
			
			build.setup();
			
			if (isVerbose()) {
				getConsole().separator();
				getConsole().log(getProject().getProperty("ant.version"));
				getConsole().log("Moxie ant properties", getProject().getProperty("ant.version"));
			}

			Pom pom = build.getPom();
			
			// push all pom properties into project 
			Map<String,String> properties = pom.getProperties();
			for (Map.Entry<String, String> entry : properties.entrySet()) {
				getProject().setProperty(entry.getKey(), entry.getValue());
			}

			if (isVerbose()) {
				getConsole().separator();
				getConsole().log("string properties");
			}
			
			setProjectProperty(Key.name, pom.name);
			setProjectProperty(Key.description, pom.description);
			setProjectProperty(Key.groupId, pom.groupId);
			setProjectProperty(Key.artifactId, pom.artifactId);
			setProjectProperty(Key.version, pom.version);
			setProjectProperty(Key.organization, pom.organization);
			setProjectProperty(Key.url, pom.url);
			setProjectProperty(Key.mainclass, buildConfig.getProjectConfig().getMainclass());
			setProjectProperty(Key.releaseVersion, buildConfig.getProjectConfig().getReleaseVersion());
			setProjectProperty(Key.releaseDate, buildConfig.getProjectConfig().getReleaseDate());

			setProperty(Key.outputFolder, buildConfig.getOutputFolder(null).toString());
			setProperty(Key.compile_outputFolder, buildConfig.getOutputFolder(Scope.compile).toString());
			setProperty(Key.test_outputFolder, buildConfig.getOutputFolder(Scope.test).toString());
			setProperty(Key.targetFolder, buildConfig.getTargetFolder().toString());
			setProperty(Key.reportsFolder, buildConfig.getReportsFolder().toString());

			if (isVerbose()) {
				getConsole().separator();
				getConsole().log("path references");
			}
			
			setSourcepath(Key.compile_sourcepath, buildConfig, Scope.compile);
			setSourcepath(Key.test_sourcepath, buildConfig, Scope.test);

			setClasspath(Key.compile_classpath, build, Scope.compile);
			setClasspath(Key.runtime_classpath, build, Scope.runtime);
			setClasspath(Key.test_classpath, build, Scope.test);
			setClasspath(Key.build_classpath, build, Scope.build);

			setDependencypath(Key.compile_dependencypath, build, Scope.compile);
			setDependencypath(Key.runtime_dependencypath, build, Scope.runtime);
			setDependencypath(Key.test_dependencypath, build, Scope.test);
			
			updateExecutionClasspath();			
		} catch (Exception e) {
			throw new MoxieException(e);
		}
	}
	
	protected void setProjectProperty(Key prop, String value) {
		if (!StringUtils.isEmpty(value)) {
			getProject().setProperty(prop.projectId(), value);
			log(prop.projectId(), value, false);
		}
	}

	
	private void setSourcepath(Key key, BuildConfig buildConfig, Scope scope) {
		Set<File> folders = new LinkedHashSet<File>();
		folders.addAll(buildConfig.getSourceFolders(scope));
		folders.addAll(buildConfig.getSourceFolders(Scope.defaultScope));
		
		Path sources = new Path(getProject());
		for (File file : folders) {
			PathElement element = sources.createPathElement();
			element.setLocation(file);
		}
		addReference(key, sources, true);
	}
	
	private void setClasspath(Key key, Build build, Scope scope) {
		List<File> jars = build.getSolver().getClasspath(scope);
		Path cp = new Path(getProject());
		// output folder
		PathElement of = cp.createPathElement();
		of.setLocation(build.getConfig().getOutputFolder(scope));
		if (!scope.isDefault()) {
			of.setLocation(build.getConfig().getOutputFolder(Scope.compile));
		}
		
		// add project dependencies 
		for (File folder : buildDependentProjectsClasspath(build)) {
			PathElement element = cp.createPathElement();
			element.setLocation(folder);
		}
		
		// jars
		for (File jar : jars) {
			PathElement element = cp.createPathElement();
			element.setLocation(jar);
		}

		addReference(key, cp, true);
	}
	
	private void setDependencypath(Key key, Build build, Scope scope) {
		List<File> jars = build.getSolver().getClasspath(scope);
		Path cp = new Path(getProject());
		for (File jar : jars) {
			PathElement element = cp.createPathElement();
			element.setLocation(jar);
		}
		addReference(key, cp, true);
	}
	
	private List<File> buildDependentProjectsClasspath(Build build) {
		List<File> folders = new ArrayList<File>();
		List<Build> libraryProjects = build.getSolver().getLinkedModules();
		for (Build project : libraryProjects) {
			File outputFolder = project.getConfig().getOutputFolder(Scope.compile);
			folders.add(outputFolder);
		}		
		return folders;
	}
}
