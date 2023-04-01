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
import java.util.Collections;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileList;
import org.moxie.Build;
import org.moxie.MoxieException;
import org.moxie.utils.JGitUtils;


public class MxGhPages extends MxGitTask {

	private File sourceDir;

	private boolean obliterate;

	private Keep keep;
	
	public MxGhPages() {
		super();
		setTaskName("mx:ghPages");
	}

	public void setSourceDir(String path) {
		this.sourceDir = new File(path);
	}

	public void setObliterate(boolean value) {
		this.obliterate = value;
	}

	public Keep createKeep()
	{
		if (this.keep == null) this.keep = new Keep(getProject());
		return keep;
	}


	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		titleClass();
		loadDependencies();

		if (sourceDir == null) {
			sourceDir = build.getConfig().getSiteTargetDirectory();
		}

		if (!sourceDir.exists()) {
			throw new MoxieException("Source folder does not exist!");
		}

		File dir = getRepositoryDir();
		JGitUtils.updateGhPages(dir, sourceDir, obliterate, (keep != null) ? keep.fileList() : Collections.emptyList());
	}


	public static class Keep
	{
		Project project;
		List<FileList.FileName> files = new ArrayList<>();
		List<FileList> filelists;


		Keep(Project p)
		{
			this.project = p;
		}

		public void addFile(FileList.FileName file)
		{
			files.add(file);
		}

		public void addFilelist(FileList list)
		{
			if (filelists == null) filelists = new ArrayList<>();
			filelists.add(list);
		}

		List<String> fileList()
		{
			List<String> list = new ArrayList<>();
			for (FileList.FileName fn : files) {
				String name = fn.getName();
				if (name != null && !name.isEmpty()) {
					list.add(name);
				}
			}

			if (filelists == null) return list;

			for (FileList fl : filelists) {
				if (fl.size() <= 0) continue;
				String dir = fl.getDir(project).getName();
				for (String file : fl.getFiles(project)) {
					list.add(dir + "/" + file);
				}
			}
			return list;
		}
	}
}
