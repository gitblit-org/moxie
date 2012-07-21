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

import org.apache.tools.ant.BuildException;
import org.moxie.Build;
import org.moxie.utils.JGitUtils;


public class MxGhPages extends MxGitTask {

	private File sourceFolder;

	private File repositoryFolder;

	private boolean obliterate;

	public void setSourceFolder(String path) {
		this.sourceFolder = new File(path);
	}

	public void setRepositoryFolder(String path) {
		this.repositoryFolder = new File(path);
	}

	public void setObliterate(boolean value) {
		this.obliterate = value;
	}

	@Override
	public void execute() throws org.apache.tools.ant.BuildException {
		Build build = getBuild();
		build.getConsole().title(getClass());
		loadDependency(build);

		if (sourceFolder == null) {
			throw new BuildException("You did not specify a sourceFolder!");
		}

		if (!sourceFolder.exists()) {
			throw new BuildException("Source folder does not exist!");
		}

		if (repositoryFolder == null || !repositoryFolder.exists()) {
			repositoryFolder = new File(getProject().getProperty("basedir"));
		}

		JGitUtils.updateGhPages(repositoryFolder, sourceFolder, obliterate);
	}
}
