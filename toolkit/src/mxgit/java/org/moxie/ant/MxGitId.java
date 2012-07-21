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
import org.moxie.Toolkit.Key;
import org.moxie.utils.JGitUtils;
import org.moxie.utils.StringUtils;


public class MxGitId extends MxGitTask {

	private String property;

	public void setProperty(String property) {
		this.property = property;
	}

	@Override
	public void execute() throws org.apache.tools.ant.BuildException {
		Build build = (Build) getProject().getReference(Key.build.refId());
		loadDependency(build);

		if (repositoryFolder == null || !repositoryFolder.exists()) {
			repositoryFolder = new File(getProject().getProperty("basedir"));			
		}
		String hashid = JGitUtils.getCommitId(repositoryFolder);

		getConsole().title(getClass(), repositoryFolder.getAbsolutePath());

		setVerbose(false);
		if (StringUtils.isEmpty(property)) {
			setProperty(Key.commit, hashid);
			getConsole().key(Key.commit.propId(), hashid);
		} else {
			setProperty(property, hashid);
			getConsole().key(property, hashid);
		}		
	}
}
