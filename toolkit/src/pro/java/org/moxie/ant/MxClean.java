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

import org.moxie.Build;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.utils.FileUtils;


public class MxClean extends MxTask {
	
	Scope scope;
	
	public MxClean() {
		super();
		setTaskName("mx:clean");
	}
	
	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}

	public void execute() {
		if (scope == null) {
			// clean output folder
			File dir = new File(getProject().getProperty(Key.outputDirectory.projectId()));
			getConsole().log("cleaning {0}", dir.getAbsolutePath());
			FileUtils.delete(dir);			

			// clean target folder
			dir = new File(getProject().getProperty(Key.targetDirectory.projectId()));
			getConsole().log("cleaning {0}", dir.getAbsolutePath());
			FileUtils.delete(dir);
			
			if (getProject().getProperty(Key.dependencyDirectory.projectId()) != null) {
				// clean project dependency directory
				dir = new File(getProject().getProperty(Key.dependencyDirectory.projectId()));
				getConsole().log("cleaning {0}", dir.getAbsolutePath());
				FileUtils.delete(dir);
			}
		} else {
			clean(scope);
		}
	}
	
	private void clean(Scope scope) {
		if (!scope.isValidSourceScope()) {
			getConsole().error("Illegal scope for cleaning {0}", scope);
			return;
		}

		Build build = getBuild();
		File dir = build.getConfig().getOutputDirectory(scope);
		getConsole().log("cleaning {0}", dir.getAbsolutePath());
		FileUtils.delete(dir);			
	}
}
