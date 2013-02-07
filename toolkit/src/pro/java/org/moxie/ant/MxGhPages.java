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
import org.moxie.MoxieException;
import org.moxie.utils.JGitUtils;


public class MxGhPages extends MxGitTask {

	private File sourceDir;

	private File repositoryDir;

	private boolean obliterate;
	
	public MxGhPages() {
		super();
		setTaskName("mx:ghPages");
	}

	public void setSourceDir(String path) {
		this.sourceDir = new File(path);
	}

	public void setRepositoryDir(String path) {
		this.repositoryDir = new File(path);
	}

	public void setObliterate(boolean value) {
		this.obliterate = value;
	}

	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		getConsole().title(getClass());
		loadDependency(build);

		if (sourceDir == null) {
			sourceDir = build.getConfig().getSiteTargetDirectory();
		}

		if (!sourceDir.exists()) {
			throw new MoxieException("Source folder does not exist!");
		}

		if (repositoryDir == null || !repositoryDir.exists()) {
			repositoryDir = new File(getProject().getProperty("basedir"));
		}

		JGitUtils.updateGhPages(repositoryDir, sourceDir, obliterate);
	}
}
