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

import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Reference;

import com.maxtk.Build;
import com.maxtk.Constants;
import com.maxtk.Constants.Key;
import com.maxtk.Scope;
import com.maxtk.utils.FileUtils;

public class MxJavac extends Javac {
	
	Scope scope;
	boolean clean;
	boolean copyResources;
	String includes;
	String excludes;
	
	public void setClean(boolean clean) {
		this.clean = clean;
	}
	
	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}

	public void setCopyresources(boolean copy) {
		this.copyResources = copy;
	}

	public void setIncludes(String includes) {
		this.includes = includes;
	}

	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		
		if (scope == null) {
			scope = Scope.defaultScope;
		}
		
		switch (scope) {
		case test:
			// test compile scope
			setDestdir(new File(getProject().getProperty(Key.test_outputpath.maxId())));
			createSrc().setRefid(new Reference(getProject(), Key.test_sourcepath.maxId()));
			setClasspathRef(new Reference(getProject(), Key.test_classpath.maxId()));
			break;
		default:
			// default compile scope
			setDestdir(new File(getProject().getProperty(Key.compile_outputpath.maxId())));
			createSrc().setRefid(new Reference(getProject(), Key.compile_sourcepath.maxId()));
			setClasspathRef(new Reference(getProject(), Key.compile_classpath.maxId()));
		}
		
		if (clean) {
			// clean the output folder before compiling
			build.console.log("cleaning {0}", getDestdir().getAbsolutePath());
			FileUtils.delete(getDestdir());
		}
		
		getDestdir().mkdirs();
		
		build.console.title(getClass(), scope.name());

		super.execute();
		
		// optionally copy resources from source folders
		if (copyResources) {
			Copy copy = new Copy();
			copy.setTaskName(getTaskName());
			copy.setProject(getProject());
			copy.setTodir(getDestdir());
			copy.setVerbose(getVerbose());

			if (getVerbose()) {
				build.console.log("copying resources => {0}", getDestdir());
			}

			if (excludes == null) {
				// default exclusions
				excludes = Constants.DEFAULT_EXCLUDES;
			}
			
			for (String path : getSrcdir().list()) {
				File file = new File(path);
				if (file.isDirectory()) {
					FileSet set = new FileSet();
					set.setDir(file);
					if (includes != null) {
						set.setIncludes(includes);
					}
					set.setExcludes(excludes);
					copy.add(set);
					if (getVerbose()) {
						build.console.log("adding resource path {0}", path);
					}
				}
			}
			
			copy.execute();
		}
	}
}
