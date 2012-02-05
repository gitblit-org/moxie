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

import com.maxtk.Config;
import com.maxtk.utils.JGitUtils;
import com.maxtk.utils.StringUtils;

public class MaxCommitId extends MaxGitTask {

	private String property;

	public void setProperty(String property) {
		this.property = property;
	}

	@Override
	public void execute() throws org.apache.tools.ant.BuildException {
		Config conf = (Config) getProject()
				.getReference(Property.max_conf.id());
		checkDependencies(conf);

		if (repositoryFolder == null || !repositoryFolder.exists()) {
			repositoryFolder = new File(getProject().getProperty("basedir"));
			log("Repository folder unspecified, trying " + repositoryFolder);
		}
		String hashid = JGitUtils.getCommitId(repositoryFolder);

		if (StringUtils.isEmpty(property)) {
			setProperty(Property.max_commit, hashid);
		} else {
			setProperty(property, hashid);
		}
	}
}
