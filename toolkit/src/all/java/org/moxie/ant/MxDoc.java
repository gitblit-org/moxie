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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import org.moxie.Scope;
import org.moxie.Substitute;
import org.moxie.utils.FileUtils;
import org.moxie.utils.LessUtils;
import org.moxie.utils.StringUtils;


public class MxDoc extends MxTask {

	Doc doc = new Doc();

	List<org.moxie.Resource> resources = new ArrayList<org.moxie.Resource>();
	
	public MxDoc() {
		super();
		setTaskName("mx:doc");
	}

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
	
	public Logo createFavicon() {
		Logo logo = new Logo();
		doc.favicon = logo;
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

	public void setGoogleAnalyticsid(String value) {
		doc.googleAnalyticsId = value;
	}

	public void setGooglePlusid(String value) {
		doc.googlePlusId = value;
	}

	public void setGooglePlusOne(boolean value) {
		doc.googlePlusOne = value;
	}
	
	public void setResponsiveLayout(boolean value) {
		doc.isResponsiveLayout = value;
	}

	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		
		if (doc.sourceFolder == null) {
			doc.sourceFolder = build.getConfig().getSiteSourceFolder();
		}

		if (doc.outputFolder == null) {
			doc.outputFolder = build.getConfig().getSiteOutputFolder();
		}
		
		getConsole().title(getClass(), build.getPom().name);
		
		build.getSolver().loadDependency(new Dependency("mx:markdownpapers"));

		if (doc.outputFolder.exists()) {
			FileUtils.delete(doc.outputFolder);
		}
		doc.outputFolder.mkdirs();

		extractHtmlResources(doc.outputFolder);
		
		if (doc.logo != null) {
			try {
				FileUtils.copy(doc.outputFolder, doc.logo.getFile());
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy logo file!");
			}
		}
		
		if (doc.favicon != null) {
			try {
				FileUtils.copy(doc.outputFolder, doc.favicon.getFile());
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy favicon file!");
			}
		}
		
		Docs.execute(build, doc, isVerbose());
		
		writeDependenciesAsJson();

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
	
	protected void extractHtmlResources(File outputFolder) {

		// extract resources
		extractResource(outputFolder, "bootstrap/css/moxie.less");
		extractResource(outputFolder, "bootstrap/css/bootstrap.less");
		extractResource(outputFolder, "bootstrap/css/bootstrap-responsive.min.css");
		extractResource(outputFolder, "bootstrap/js/bootstrap.min.js");
		extractResource(outputFolder, "bootstrap/js/jquery.js");
		extractResource(outputFolder, "bootstrap/img/glyphicons-halflings.png");
		extractResource(outputFolder, "bootstrap/img/glyphicons-halflings-white.png");
		extractResource(outputFolder, "d3/d3.js");
		extractResource(outputFolder, "d3/rings.css");
		extractResource(outputFolder, "d3/rings.js");

		// write build's LESS as custom.less
		Build build = getBuild();
		String less = build.getCustomLess();
		File file = new File(outputFolder, "bootstrap/css/custom.less");
		FileUtils.writeContent(file, less);

		build.getSolver().loadDependency(new Dependency("mx:rhino"));

		// compile Bootstrap and custom.less overrides into css
		try {
			LessUtils.compile(new File(outputFolder, "bootstrap/css/bootstrap.less"),
					new File(outputFolder, "bootstrap/css/bootstrap.css"), false);
		} catch (Exception e) {
			getConsole().error(e,  "Failed to compile LESS!");
		}
	}
	
	void writeDependenciesAsJson() {
		DepNode root = new DepNode(new Dependency(getBuild().getPom().getCoordinates()));
		Set<Dependency> dependencies = getBuild().getSolver().getDependencies(Scope.test);
		DepNode currRoot = root;
		for (Dependency dep : dependencies) {
			if (currRoot.dep.ring == dep.ring) {
				// dep is at same ring as curr root, add to parent
				currRoot = currRoot.parent.add(dep);
			} else if (dep.ring > currRoot.dep.ring) {
				// dep is one ring lower then curr root, add to curr root
				// and reset curr root
				currRoot = currRoot.add(dep);
			} else if (dep.ring < currRoot.dep.ring) {
				// find the parent node for this dep
				currRoot = currRoot.parentAt(dep.ring - 1);
				// add dep to the parent node
				currRoot = currRoot.add(dep);
			}
		}
		String json = root.asJSON();
		File file  = new File(getBuild().getConfig().getSiteOutputFolder(), "moxie-dependencies.json");
		FileUtils.writeContent(file, json);
	}
	
	private class DepNode {
		DepNode parent;
		Dependency dep;
		
		
		List<DepNode> children;
		
		DepNode(Dependency dep) {
			this(dep, null);
		}
		DepNode(Dependency dep, DepNode parent) {
			this.dep = dep;
			this.parent = parent;
			children = new ArrayList<DepNode>();
		}
		
		DepNode add(Dependency dep) {
			DepNode node = new DepNode(dep, this);
			children.add(node);
			return node;
		}
		
		DepNode parentAt(int ring) {
			DepNode node = this;
			while (node != null && node.dep.ring >= ring) {
				if (node.parent == null) {
					break;
				}
				node = node.parent;
			}
			return node;
		}
		
		String asJSON() {
			StringBuilder sb = new StringBuilder("{\n");
			sb.append(MessageFormat.format("    \"ring\" : \"{0}\",\n", dep.ring));
			sb.append(MessageFormat.format("    \"name\" : \"{0} {1}\"", dep.artifactId, dep.version));
			if (children.size() == 0) {
				//sb.append(MessageFormat.format(",\n    \"colour\" : \"{0}\"", "#"));
			} else if (children.size() > 0) {
				sb.append(",\n    \"children\" : [\n");
				for (DepNode node : children) {
					sb.append(StringUtils.insertSoftTab(node.asJSON()));
					sb.append(",\n");
				}
				// trim trailing comma,newline
				sb.setLength(sb.length() - 2);
				sb.append("\n    ]\n");
			}
			sb.append("\n}\n");
			return sb.toString();
		}
	}
}
