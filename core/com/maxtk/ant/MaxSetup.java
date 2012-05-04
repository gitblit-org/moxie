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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Build;
import com.maxtk.Config;
import com.maxtk.Constants.Key;
import com.maxtk.SourceFolder;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.StringUtils;

public class MaxSetup extends MaxTask {

	private String config;
	
	public void setConfig(String config) {
		this.config = config;
	}

	@Override
	public void execute() throws BuildException {
		try {
			Build build;
			if (StringUtils.isEmpty(config)) {
				// default configuration
				build = new Build();
			} else {
				// specified configuration
				build = new Build(config);
			}
			build.setup();
			
			console = build.console;
			Config conf = build.conf;
			
			build.console.separator();
			build.console.log("ant properties");

			// add a reference to the full build object
			addReference(Key.build, build, false);
			setProperty(Key.name, conf.getName());
			setProperty(Key.description, conf.getDescription());
			setProperty(Key.groupId, conf.getGroupId());
			setProperty(Key.artifactId, conf.getArtifactId());
			setProperty(Key.version, conf.getVersion());
			setProperty(Key.vendor, conf.getVendor());
			setProperty(Key.url, conf.getUrl());

			setProperty(Key.outputFolder, conf.getOutputFolder().toString());

			// setup max-sourceFolders reference
			Path sources = new Path(getProject());
			for (SourceFolder sourceFolder : conf.getSourceFolders()) {
				PathElement element = sources.createPathElement();
				element.setLocation(sourceFolder.folder);
			}
			addReference(Key.sourceFolders, sources, true);

			setClasspath(Key.compile_classpath, build, build.getCompileClasspath());
			setClasspath(Key.runtime_classpath, build, build.getRuntimeClasspath());
			setClasspath(Key.test_classpath, build, build.getTestClasspath());


		} catch (MaxmlException e) {
			console.error(e, "Maxilla failed to parse your configuration file!");
			throw new BuildException(e);
		} catch (Exception e) {
			console.error(e, "Maxilla failed to setup your project!");
			throw new BuildException(e);
		}
	}
	
	private void setClasspath(Key key, Build build, List<File> jars) {
		Path cp = new Path(getProject());
		// jars
		for (File jar : jars) {
			PathElement element = cp.createPathElement();
			element.setLocation(jar);
		}

		// output folder
		PathElement of = cp.createPathElement();
		of.setLocation(build.conf.getOutputFolder());
		
		// add project dependencies 
		for (File folder : buildDependentProjectsClasspath(build)) {
			PathElement element = cp.createPathElement();
			element.setLocation(folder);
		}
		addReference(key, cp, true);
	}
	
	private List<File> buildDependentProjectsClasspath(Build build) {
		List<File> folders = new ArrayList<File>();
		File basedir = getProject().getBaseDir();
		String workspace = getProject().getProperty("eclipse.workspace");
		for (String project : build.conf.getProjects()) {
			File projectDir = new File(basedir, "/../" + project);
			if (projectDir.exists()) {
				// project dependency is relative to this project
				File outputFolder = getProjectOutputFolder(build, project, projectDir);
				if (outputFolder != null && outputFolder.exists()) {
					folders.add(outputFolder);
				}
			} else {
				if (StringUtils.isEmpty(workspace)) {
					// workspace is undefined, done looking
					build.console.warn(MessageFormat.format("Failed to find project \"{0}\".  (FYI $'{'eclipse.workspace'}' is not set.)", project));
				} else {
					// check workspace
					File wsDir = new File(workspace);
					projectDir = new File(wsDir, project);
					if (projectDir.exists()) {
						File outputFolder = getProjectOutputFolder(build, project, projectDir);
						if (outputFolder != null && outputFolder.exists()) {
							folders.add(outputFolder);
						}
					} else {
						build.console.error(MessageFormat.format("Failed to find project \"{0}\".", project));
					}
				}
			}
		}
		return folders;
	}
	
	/**
	 * Get the output folder for the dependent project
	 * @param project
	 * @param projectDir
	 * @return
	 */
	private File getProjectOutputFolder(Build build, String project, File projectDir) {
		File projectMax = new File(projectDir, "build.maxml");
		if (projectMax.exists()) {
			// dependent project has a build.maxml descriptor
			try {
				Config projectConfig = Config.load(projectMax);
				File projectOutputFolder = projectConfig.getOutputFolder();
				return projectOutputFolder;
			} catch (Exception e) {
				console.error(e);
			}
		}
		String [] tryThese = { "bin/java", "bin/classes", "bin", "build/classes", "build/java" };
		for (String tryThis : tryThese) {
		File projectOutputFolder = new File(projectDir, tryThis);
			if (projectOutputFolder.exists() && projectOutputFolder.isDirectory()) {
				build.console.warn(MessageFormat.format("Project {0} does not have a build.maxml descriptor but does have a \"{1}\" folder.", project, tryThis));
				return projectOutputFolder;
			}
		}
		build.console.error(MessageFormat.format("Could not find an output folder for project \"{0}\"!", project));
		return null;
	}
}
