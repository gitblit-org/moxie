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

import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Reference;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.Dependency.Scope;
import com.maxtk.utils.FileUtils;

public class MaxCompile extends Javac {
	
	Scope scope;
	boolean clean;
	
	public void setClean(boolean clean) {
		this.clean = clean;
	}
	
	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		
		if (scope == null) {
			scope = Scope.defaultScope;
		}
		
		switch (scope) {
		case test:
			// test compile scope
			setDestdir((File) getProject().getReference(Key.test_outputpath.maxId()));
			createSrc().setRefid(new Reference(getProject(), Key.test_sourcepath.maxId()));
			setClasspathRef(new Reference(getProject(), Key.test_classpath.maxId()));
			break;
		default:
			// default compile scope
			setDestdir((File) getProject().getReference(Key.compile_outputpath.maxId()));
			createSrc().setRefid(new Reference(getProject(), Key.compile_sourcepath.maxId()));
			setClasspathRef(new Reference(getProject(), Key.compile_classpath.maxId()));
		}
		
		if (clean) {
			// clean the output folder before compiling
			build.console.log("cleaning {0}", getDestdir());
			FileUtils.delete(getDestdir());			
		}
		
		super.execute();
	}
}
