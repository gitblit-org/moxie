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
import java.io.IOException;

import com.maxtk.Config;
import com.maxtk.Dependency;
import com.maxtk.Setup;
import com.maxtk.maxml.MaxmlException;

public class MaxGitTask extends MaxTask {

	protected File repositoryFolder;

	public void setRepositoryFolder(String path) {
		this.repositoryFolder = new File(path);
	}

	protected void checkDependencies(Config config) throws IOException,
			MaxmlException {
		try {
			Class.forName("org.eclipse.jgit.api.Git");
		} catch (Throwable t) {
			Dependency jgit = new Dependency("org/eclipse/jgit", "org.eclipse.jgit",
					"1.2.0.201112221803-r");
			Setup.retriveInternalDependency(config, jgit);
		}
	}
}
