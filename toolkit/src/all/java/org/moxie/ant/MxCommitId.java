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
import org.moxie.Toolkit.Key;
import org.moxie.utils.JGitUtils;
import org.moxie.utils.StringUtils;


public class MxCommitId extends MxGitTask {

	private String property;
	
	public MxCommitId() {
		super();
		setTaskName("mx:commitid");
	}

	public void setProperty(String property) {
		this.property = property;
	}

	@Override
	public void execute() throws BuildException {
		loadDependencies();

		File dir = getRepositoryDir();
		String hashid = JGitUtils.getCommitId(dir);

		titleClass(dir.getAbsolutePath());

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
