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
import org.moxie.Dependency;


public class MxGitTask extends MxTask {

	protected File repositoryDirectory;

	public void setRepositoryDir(String path) {
		this.repositoryDirectory = new File(path);
	}

	protected void loadDependency(Build build) {
		build.getSolver().loadDependency(new Dependency("mx:jgit"));
	}
}
