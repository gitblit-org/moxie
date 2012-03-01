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

import static java.text.MessageFormat.format;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Config;
import com.maxtk.Constants;
import com.maxtk.Setup;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class MaxSetup extends MaxTask {

	private String config;

	public void setConfig(String config) {
		this.config = config;
	}

	@Override
	public void execute() throws BuildException {
		try {
			Config conf;
			if (StringUtils.isEmpty(config)) {
				// default configuration
				conf = Setup.execute(null, verbose);
			} else {
				// specified configuration
				File file = new File(config);
				conf = Setup.execute(file.getAbsolutePath(), verbose);
			}

			// create/update Eclipse configuration files
			if (conf.configureEclipseClasspath()) {
				if (verbose) {
					log(Constants.SEP);
				}
				log("rebuilding eclipse .classpath");
				writeEclipseClasspath(conf);
				log("done. refresh your project.");
			}
			
			if (verbose) {
				log(Constants.SEP);
				log("ant properties");
			}
			setProperty(Property.max_name, conf.getName());
			setProperty(Property.max_description, conf.getDescription());
			setProperty(Property.max_version, conf.getVersion());
			setProperty(Property.max_artifactId, conf.getArtifactId());
			setProperty(Property.max_url, conf.getUrl());
			setProperty(Property.max_vendor, conf.getVendor());

			// setup max-sourceFolders reference
			Path sources = new Path(getProject());
			for (File file : conf.getSourceFolders()) {
				PathElement element = sources.createPathElement();
				element.setLocation(file);
			}
			addReference(Property.max_sourceFolders, sources);

			// setup max-classpath reference
			Path classpath = new Path(getProject());
			for (File file : conf.getArtifactClasspath()) {
				PathElement element = classpath.createPathElement();
				element.setLocation(file);
			}

			// output folder
			PathElement of = classpath.createPathElement();
			of.setLocation(conf.getOutputFolder());
			
			// add project dependencies 
			for (File folder : buildDependentProjectsClasspath(conf)) {
				PathElement element = classpath.createPathElement();
				element.setLocation(folder);
			}

			addReference(Property.max_classpath, classpath);

			setProperty(Property.max_outputFolder, conf.getOutputFolder()
					.toString());

			// add a reference to the full conf object
			addReference(Property.max_conf, conf);
		} catch (MaxmlException e) {
			log("Maxilla failed to parse your configuration file!", e,
					Project.MSG_ERR);
			e.printStackTrace();
			throw new BuildException(e);
		} catch (Exception e) {
			log("Maxilla failed to setup your project!", e, Project.MSG_ERR);
			e.printStackTrace();
			throw new BuildException(e);
		}
	}
	
	private List<File> buildDependentProjectsClasspath(Config conf) {
		List<File> folders = new ArrayList<File>();
		File basedir = getProject().getBaseDir();
		String workspace = getProject().getProperty("eclipse.workspace");
		for (String project : conf.getProjects()) {
			File projectDir = new File(basedir, "/../" + project);
			if (projectDir.exists()) {
				// project dependency is relative to this project
				File outputFolder = getProjectOutputFolder(project, projectDir);
				if (outputFolder != null && outputFolder.exists()) {
					folders.add(outputFolder);
				}
			} else {
				if (StringUtils.isEmpty(workspace)) {
					// workspace is undefined, done looking
					log(MessageFormat.format("Failed to find project \"{0}\".  (FYI $'{'eclipse.workspace'}' is not set.)", project), Project.MSG_ERR);
				} else {
					// check workspace
					File wsDir = new File(workspace);
					projectDir = new File(wsDir, project);
					if (projectDir.exists()) {
						File outputFolder = getProjectOutputFolder(project, projectDir);
						if (outputFolder != null && outputFolder.exists()) {
							folders.add(outputFolder);
						}
					} else {
						log(MessageFormat.format("Failed to find project \"{0}\".", project), Project.MSG_ERR);
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
	private File getProjectOutputFolder(String project, File projectDir) {
		File projectMax = new File(projectDir, "build.maxml");
		if (projectMax.exists()) {
			// dependent project has a build.maxml descriptor
			try {
				Config projectConfig = Config.load(projectMax);
				File projectOutputFolder = projectConfig.getOutputFolder();
				return projectOutputFolder;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		String [] tryThese = { "bin/java", "bin/classes", "bin", "build/classes", "build/java" };
		for (String tryThis : tryThese) {
		File projectOutputFolder = new File(projectDir, tryThis);
			if (projectOutputFolder.exists() && projectOutputFolder.isDirectory()) {
				log(MessageFormat.format("Project {0} does not have a build.maxml descriptor but does have a \"{1}\" folder.", project, tryThis), Project.MSG_WARN);
				return projectOutputFolder;
			}
		}
		log(MessageFormat.format("Could not find an output folder for project \"{0}\"!", project), Project.MSG_ERR);
		return null;
	}
	
	void writeEclipseClasspath(Config conf) {
		List<File> jars = conf.getArtifactClasspath();
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<classpath>\n");
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n");
		for (File folder : conf.getSourceFolders()) {
			sb.append(format("<classpathentry kind=\"src\" path=\"{0}\"/>\n",
					folder));
		}
		for (File jar : jars) {			
			File srcJar = new File(jar.getParentFile(), jar.getName()
					.substring(0, jar.getName().lastIndexOf('.'))
					+ "-sources.jar");
			if (srcJar.exists()) {
				// have sources
				sb.append(format(
						"<classpathentry kind=\"lib\" path=\"{0}\" sourcepath=\"{1}\" />\n",
						jar.getAbsolutePath(), srcJar.getAbsolutePath()));
			} else {
				// no sources
				sb.append(format(
						"<classpathentry kind=\"lib\" path=\"{0}\" />\n",
						jar.getAbsolutePath()));
			}
		}
		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\"/>\n",
				conf.getOutputFolder()));
				
		for (String project : conf.getProjects()) {
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\"/>\n", project));
		}
		sb.append("</classpath>");
		FileUtils.writeContent(new File(".classpath"), sb.toString());
	}
}
