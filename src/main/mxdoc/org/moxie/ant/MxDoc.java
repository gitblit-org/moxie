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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Doc;
import org.moxie.Docs;
import org.moxie.Link;
import org.moxie.Load;
import org.moxie.Logo;
import org.moxie.NoMarkdown;
import org.moxie.Prop;
import org.moxie.Regex;
import org.moxie.Substitute;
import org.moxie.utils.FileUtils;


public class MxDoc extends MxTask {

	Doc doc = new Doc();

	List<org.moxie.Resource> resources = new ArrayList<org.moxie.Resource>();

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

	public org.moxie.Resource createResource() {
		org.moxie.Resource rsc = new org.moxie.Resource();
		resources.add(rsc);
		return rsc;
	}
	
	public Logo createLogo() {
		Logo logo = new Logo();
		doc.logo = logo;
		return logo;
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
		Build build = getBuild();
		
		if (doc.sourceFolder == null) {
			doc.sourceFolder = build.getSiteSourceFolder();
		}

		if (doc.outputFolder == null) {
			doc.outputFolder = build.getSiteOutputFolder();
		}
		
		build.console.title(getClass(), build.getPom().name);
		
		build.loadDependency(new Dependency("mxreport:freemarker"));
		build.loadDependency(new Dependency("mxdoc:markdownpapers"));

		if (doc.outputFolder.exists()) {
			FileUtils.delete(doc.outputFolder);
		}
		doc.outputFolder.mkdirs();

		extractHtmlResources(doc.outputFolder);
		
		if (doc.logo != null) {
			try {
				FileUtils.copy(doc.outputFolder, doc.logo.getFile());
			} catch (IOException e) {
				build.console.error(e, "Failed to copy logo file!");
			}
		}
		
		Docs.execute(build, doc, isVerbose());

		for (org.moxie.Resource resource : resources) {
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
