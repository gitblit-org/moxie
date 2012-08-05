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
import org.apache.tools.ant.Project;
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
			
			// TODO import Moxie targets somehow
		}
		
		// continue normal parsing
		super.parse(project, source);
	}
}
