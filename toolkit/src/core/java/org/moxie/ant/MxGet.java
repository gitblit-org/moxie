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
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.utils.FileUtils;

public class MxGet extends MxTask {

	List<ScopedDependency> deps;
	Scope scope;
	File destinationDirectory;

	public MxGet() {
		super();
		setTaskName("mx:get");
	}

	public ScopedDependency createDependency() {
		if (deps == null) {
			deps = new ArrayList<ScopedDependency>();
		}
		ScopedDependency dep = new ScopedDependency();
		deps.add(dep);
		return dep;
	}

	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}

	public Scope getScope() {
		return scope;
	}

	public File getTodir() {
		return destinationDirectory;
	}

	public void setTodir(File dir) {
		this.destinationDirectory = dir;
	}

	public File getDestdir() {
		return destinationDirectory;
	}

	public void setDestdir(File dir) {
		this.destinationDirectory = dir;
	}

	@Override
	public void execute() throws BuildException {
		if (deps == null || deps.size() == 0) {
			throw new MoxieException("Must specify dependencies!");
		}

		if (scope == null) {
			scope = Scope.defaultScope;
		}

		if (destinationDirectory == null) {
			throw new MoxieException("Destination directory must be set!");
		}

		Build build = getBuild();
		getConsole().title(getClass());

		List<Dependency> dependencies = new ArrayList<Dependency>();
		for (ScopedDependency dep : deps) {
			dependencies.add(dep.dependency);			
		}

		List<File> artifacts = build.getSolver().solve(scope,
				dependencies.toArray(new Dependency[dependencies.size()]));

		if (artifacts.size() > 0) {
			getConsole().log(1, "copying {0} artifacts => {1}", artifacts.size(), destinationDirectory);
			try {
				FileUtils.copy(destinationDirectory, artifacts.toArray(new File[artifacts.size()]));
			} catch (Exception e) {
				throw new MoxieException(e);
			}
		}
	}

	public static class ScopedDependency {
		private Dependency dependency;

		public void setCoordinates(String def) {
			this.dependency = new Dependency(def);
		}
	}
}
