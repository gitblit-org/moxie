/*
 * Copyright 2013 James Moger
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
import org.apache.tools.ant.taskdefs.optional.net.RExecTask;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Toolkit.Key;


public class MxFtp extends RExecTask {

	public MxFtp() {
		super();
		setTaskName("mx:ftp");
	}
	
	protected void loadDependencies() {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		build.getSolver().loadDependency(new Dependency("mx:commons-net"));
	}
	
	@Override
	public void execute() throws BuildException {
		loadDependencies();
		
		super.execute();
	}
}
