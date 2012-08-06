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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.helper.ProjectHelper2;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.moxie.Toolkit;

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
			System.out.println();
			System.out.println("Moxie+Ant v" + Toolkit.getVersion());			
			System.out.println();
			// TODO display useful details here
			
			// automatically define Moxie tasks
			Taskdef def = new Taskdef();
			def.setProject(project);
			def.setURI("antlib:org.moxie");
			def.setResource("tasks.properties");
			def.execute();
			
			// add Moxie targets
			project.addTarget(newInitTarget());
			project.addTarget(newCompileTarget());
			project.addTarget(newTestTarget());
			project.addTarget(newPackageTarget());
			project.addTarget(newInstallTarget());
			project.addTarget(newDeployTarget());
			project.addTarget(newCleanTarget());
			project.addTarget(newReportTarget());
		}		
		
		// continue normal parsing
		super.parse(project, source);
	}
	
	private Target newTarget(String name) {
		Target target = new Target();
		target.setName(name);
		target.setLocation(new Location(name));
		return target;
	}
	
	private Target newInitTarget() {
		Target target = newTarget("moxie.init");
		target.setDescription("validates project configuration, retrieves dependencies, and configures ANT properties");
		
		MxInit task = new MxInit();
		target.addTask(task);
		return target;
	}
	
	private Target newCompileTarget() {
		Target target = newTarget("moxie.compile");
		target.setDepends("moxie.init");
		target.setDescription("compile the source code of the project");
		
		
		MxJavac task = new MxJavac();
		target.addTask(task);
		return target;
	}

	private Target newTestTarget() {
		Target target = newTarget("moxie.test");
		target.setDepends("moxie.compile");
		target.setDescription("compile the source code of the project");
		
		// TODO implement test target
		return target;
	}

	private Target newPackageTarget() {
		Target target = newTarget("moxie.package");
		target.setDepends("moxie.test");
		target.setDescription("take the compiled code and package it in its distributable format, such as a JAR");
		
		// TODO WAR packaging
		MxJar task = new MxJar();
		task.setIncluderesources(true);
		task.setPackagesources(true);
		target.addTask(task);
		return target;
	}
	
	private Target newInstallTarget() {
		Target target = newTarget("moxie.install");
		
		target.setDepends("moxie.package");
		target.setDescription("install the package into the local repository, for use as a dependency in other projects locally");

		MxInstall task = new MxInstall();		

		target.addTask(task);
		return target;
	}
	
	private Target newDeployTarget() {
		Target target = newTarget("moxie.deploy");
		target.setDepends("moxie.install");
		target.setDescription("deploys the generated binaries to an external repository");
		// TODO implement deploy
		return target;
	}
	
	private Target newCleanTarget() {		
		Target target = newTarget("moxie.clean");
		target.setDepends("moxie.init");
		target.setDescription("clean build and target folders");

		MxClean task = new MxClean();
		target.addTask(task);
		return target;
	}
	
	private Target newReportTarget() {
		Target target = newTarget("moxie.report");
		target.setDepends("moxie.init");
		target.setDescription("generates a dependency report");

		MxReport task = new MxReport();
		target.addTask(task);
		return target;
	}
}
