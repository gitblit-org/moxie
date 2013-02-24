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
import org.moxie.Toolkit.Key;
import org.moxie.utils.JGitUtils;
import org.moxie.utils.StringUtils;


public class MxGitId extends MxGitTask {

	private String property;
	
	public MxGitId() {
		super();
		setTaskName("mx:gitid");
	}

	public void setProperty(String property) {
		this.property = property;
	}

	@Override
	public void execute() throws BuildException {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		loadDependency(build);

		if (repositoryDirectory == null || !repositoryDirectory.exists()) {
			repositoryDirectory = new File(getProject().getProperty("basedir"));			
		}
		String hashid = JGitUtils.getCommitId(repositoryDirectory);

		getConsole().title(getClass(), repositoryDirectory.getAbsolutePath());

		setVerbose(false);
		if (StringUtils.isEmpty(property)) {
			setProjectProperty(Key.commitId, hashid);
			getConsole().key(Key.commitId.projectId(), hashid);
		} else {
			setProperty(property, hashid);
			getConsole().key(property, hashid);
		}		
	}
}
