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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Config;
import com.maxtk.Constants;
import com.maxtk.Dependency;
import com.maxtk.Setup;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.StringUtils;

public class MaxSetup extends MaxTask {

	private String config;

	public void setConfig(String config) {
		this.config = config;
	}

	@Override
	public void execute() throws BuildException {
		checkDependencies();
		
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
			for (File file : conf.getClasspath()) {
				PathElement element = classpath.createPathElement();
				element.setLocation(file);
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
	
	protected void checkDependencies() {
		try {
			Class.forName("argo.jdom.JdomParser");
		} catch (Throwable t) {
			Dependency argo = new Dependency("argo", "2.23", "net/sourceforge/argo");			
//			Setup.retriveInternalDependency(config, argo);
		}
	}

}
