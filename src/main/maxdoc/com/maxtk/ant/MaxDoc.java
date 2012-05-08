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
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.Dependency;
import com.maxtk.Doc;
import com.maxtk.Docs;
import com.maxtk.Link;
import com.maxtk.Load;
import com.maxtk.NoMarkdown;
import com.maxtk.Prop;
import com.maxtk.Regex;
import com.maxtk.Substitute;
import com.maxtk.utils.FileUtils;

public class MaxDoc extends Task {

	boolean verbose;

	Doc doc = new Doc();

	List<com.maxtk.Resource> resources = new ArrayList<com.maxtk.Resource>();

	public Link createStructure() {
		Link link = new Link();
		doc.structure = link;
		return link;
	}

	public Substitute createSubstitute() {
		Substitute sub = new Substitute();
		doc.substitutions.add(sub);
		return sub;
	}

	public Load createLoad() {
		Load load = new Load();
		doc.loads.add(load);
		return load;
	}

	public Prop createProperties() {
		Prop prop = new Prop();
		doc.props.add(prop);
		return prop;
	}

	public NoMarkdown createNomarkdown() {
		NoMarkdown nomd = new NoMarkdown();
		doc.nomarkdowns.add(nomd);
		return nomd;
	}

	public Regex createRegex() {
		Regex regex = new Regex();
		doc.regexes.add(regex);
		return regex;
	}

	public com.maxtk.Resource createResource() {
		com.maxtk.Resource rsc = new com.maxtk.Resource();
		resources.add(rsc);
		return rsc;
	}

	public void setName(String name) {
		doc.name = name;
	}

	public void setSourceFolder(File folder) {
		doc.sourceFolder = folder;
	}

	public void setOutputFolder(File folder) {
		doc.outputFolder = folder;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setInjectprettify(boolean value) {
		doc.injectPrettify = value;
	}

	public void setInjectfancybox(boolean value) {
		doc.injectFancybox = value;
	}

	public void setGoogleAnalyticsid(String value) {
		doc.googleAnalyticsId = value;
	}

	public void setGooglePlusid(String value) {
		doc.googlePlusId = value;
	}

	public void setGooglePlusOne(boolean value) {
		doc.googlePlusOne = value;
	}

	@Override
	public void execute() throws BuildException {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		build.loadDependency(new Dependency("org.tautua.markdownpapers:markdownpapers-core:1.2.7"));

		if (doc.sourceFolder == null) {
			doc.sourceFolder = new File(build.getProjectFolder(), "src/site");
		}

		if (doc.outputFolder == null) {
			doc.outputFolder = new File(build.getTargetFolder(), "site");
		}
		
		Docs.execute(build, doc, verbose);

		for (com.maxtk.Resource resource : resources) {
			try {
				if (resource.file != null) {
					FileUtils.copy(doc.outputFolder, resource.file);
				} else {
					for (FileSet fs : resource.filesets) {
						DirectoryScanner ds = fs.getDirectoryScanner(getProject());
						File fromDir = fs.getDir(getProject());

						for (String srcFile : ds.getIncludedFiles()) {
							File file = new File(fromDir, srcFile);
							FileUtils.copy(doc.outputFolder, file);
						}

						for (String srcDir : ds.getIncludedDirectories()) {
							File file = new File(fromDir, srcDir);
							FileUtils.copy(doc.outputFolder, file);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
