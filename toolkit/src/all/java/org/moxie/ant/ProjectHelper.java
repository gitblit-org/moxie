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

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.ProjectHelper2;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.moxie.utils.StringUtils;

/**
 * Entry point for Ant-classpath-included Moxie (i.e. Moxie + Ant or 
 * ${user.home}/.ant/lib).
 * 
 */
public class ProjectHelper extends ProjectHelper2 {

	public ProjectHelper() {
		super();
	}
	
	@Override
	public void parse(Project project, Object source) throws BuildException {
		if (getImportStack().size() == 0) {
			project.log("configuring moxie tasks", Project.MSG_DEBUG);
			
			// automatically define Moxie tasks
			Taskdef def = new Taskdef();
			def.setProject(project);
			def.setURI("antlib:org.moxie");
			def.setResource("org/moxie/antlib.xml");
			def.execute();
			
			// add Moxie targets
			project.log("adding Moxie phases", Project.MSG_DEBUG);
			newInitPhase(project);
			newCompilePhase(project);
			newTestPhase(project);
			newPackagePhase(project);
			newInstallPhase(project);
			newDeployPhase(project);
			newCleanPhase(project);
			newReportPhase(project);
			newRunPhase(project);
		}		
		
		// continue normal parsing
		super.parse(project, source);
	}
	
	private Target newPhase(Project project, String name, String... depends) {
		String prefix = "phase:";
		Target phase = new Target();
		phase.setName(prefix + name);
		phase.setLocation(new Location(prefix + name));
		project.addTarget(phase);
		if (depends != null && depends.length > 0) {
			List<String> list = new ArrayList<String>();
			for (String depend : depends) {
				list.add(prefix + depend);
			}
			phase.setDepends(StringUtils.flattenStrings(list, ","));
		}
		return phase;
	}
	
	private Target newInitPhase(Project project) {
		Target phase = newPhase(project, "init");
		phase.setDescription("validates project configuration, retrieves dependencies, and configures ANT properties");
		
		MxInit task = new MxInit();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newCompilePhase(Project project) {
		Target phase = newPhase(project, "compile", "init");		
		phase.setDescription("compile the source code of the project");
				
		MxJavac task = new MxJavac();
		task.setProject(project);		
		phase.addTask(task);
		return phase;
	}

	private Target newTestPhase(Project project) {
		Target phase = newPhase(project, "test", "compile");
		phase.setDescription("compile the source code of the project");
		
		MxTest task = new MxTest();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}

	private Target newPackagePhase(Project project) {
		Target phase = newPhase(project, "package", "compile");
		phase.setDescription("take the compiled code and package it in its distributable format, such as a JAR");
		
		MxPackage task = new MxPackage();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newInstallPhase(Project project) {
		Target phase = newPhase(project, "install", "package");
		phase.setDescription("install the package into the local repository, for use as a dependency in other projects locally");

		MxInstall task = new MxInstall();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newDeployPhase(Project project) {
		Target phase = newPhase(project, "deploy", "package");
		phase.setDescription("deploys the generated binaries to an external repository");
		
		MxDeploy task = new MxDeploy();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newCleanPhase(Project project) {		
		Target phase = newPhase(project, "clean", "init");
		phase.setDescription("clean build and target directories");

		MxPackage task = new MxPackage();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newReportPhase(Project project) {
		Target phase = newPhase(project, "report", "init");
		phase.setDescription("generates a dependency report");

		MxReport task = new MxReport();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
	
	private Target newRunPhase(Project project) {
		Target phase = newPhase(project, "run", "compile");
		phase.setDescription("executes a specified main class");

		MxRun task = new MxRun();
		task.setProject(project);
		phase.addTask(task);
		return phase;
	}
}
