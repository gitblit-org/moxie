/*
 * Copyright 2013 James Moger
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
import org.apache.tools.ant.types.DirSet;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.Union;
import org.moxie.MoxieException;
import org.moxie.Toolkit;
import org.moxie.utils.FileUtils;
import org.moxie.utils.JGitUtils;
import org.moxie.utils.StringUtils;

public class MxCommit extends MxGitTask {

	protected Message message;

	protected Tag tag;

	protected Union path;
	
	public MxCommit() {
		super();
		setTaskName("mx:commit");
	}
	
	public Message createMessage() {
		this.message = new Message();
		this.message.setProject(getProject());
		return this.message;
	}
	
	public Tag createTag() {
		tag = new Tag();
		tag.setProject(getProject());
		return tag;
	}

	public void addFileset(FileSet set) {
		getPath().add(set);
	}

	public void addDirset(DirSet set) {
		getPath().add(set);
	}
	
	private synchronized Union getPath() {
		if (path == null) {
			path = new Union();
			path.setProject(getProject());
		}
		return path;
	}
	
	@Override
	public void execute() throws BuildException {
		loadDependencies();
		if (message == null || StringUtils.isEmpty(message.getValue())) {
			throw new MoxieException("The commit must have a message!");
		}
		if (tag != null && StringUtils.isEmpty(tag.getMessage())) {
			// default tag message is the commit message
			tag.createMessage().setValue(message.getValue());
		}		
		if (tag != null && StringUtils.isEmpty(tag.getName())) {
			// default tag name is version
			tag.setName(getProject().getProperty(Toolkit.Key.version.projectId()));
		}

		File dir = getRepositoryDir();
		if (dir == null && !isRequiredGoal()) {
			// do not require git commit
			return;
		}
		File workingDir = dir;
		if (dir.getName().equals(".git")) {
			workingDir = dir.getParentFile();
		}
		titleClass(dir.getAbsolutePath());
		
		// consume path shared by another task
		getPath().add(consumeSharedPaths());

		List<String> files = new ArrayList<String>();
		try {
			int offset = getConsoleOffset();
			setConsoleOffset(0);
			// display log message, fit on 80 column terminal
			getConsole().log(StringUtils.leftPad("", offset, ' ') + StringUtils.trimString(message.getValue(), 67 - offset));
			getConsole().log();
			
			// add all included files relative to the working directory
			for (String file : getPath().list()) {
				String relativePath = FileUtils.getRelativePath(workingDir, new File(file));
				if (relativePath == null) {
					String msg = getConsole().error("Can not add {0} because it is not located relative to {1}",
							file, workingDir.getAbsolutePath());
					throw new MoxieException(msg);
				}
				files.add(relativePath);
				getConsole().log(1, "adding " + relativePath);
			}
			String commitId = JGitUtils.commitFiles(dir, files, message.getValue(),
					tag == null ? null : tag.getName(), tag == null ? null : tag.getMessage());
			
			getConsole().log();
			getConsole().log(1,  "created commit {0}", commitId);
			if (tag != null) {
				getConsole().log(1,  "tagged as ''{0}''", tag.getName());
			}
			
			// update the commit id property
			getProject().setProperty(Toolkit.Key.commitId.projectId(), commitId);
		} catch (Exception e) {
			throw new MoxieException(e);
		}
	}
}
