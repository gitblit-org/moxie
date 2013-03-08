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
import java.util.Map;
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
import org.moxie.References;
import org.moxie.Regex;
import org.moxie.Scope;
import org.moxie.Substitute;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.utils.FileUtils;
import org.moxie.utils.LessUtils;
import org.moxie.utils.StringUtils;


public class MxDoc extends MxTask {

	Doc doc = new Doc();
	
	String customLessFile;

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
	
	public References createReferences() {
		References references = new References();
		doc.references = references;
		return references;
	}

	public Substitute createReplace() {
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

	public Link createPage() {
		Link page = new Link();		
		page.isPage = true;
		doc.freeformPages.add(page);
		return page;
	}

	public void setName(String name) {
		doc.name = name;
	}

	public void setSourceDir(File folder) {
		doc.sourceDirectory = folder;
	}
	
	public void setTemplateDir(File folder) {
		doc.templateDirectory = folder;
	}

	public void setTodir(File dir) {
		doc.outputDirectory = dir;
	}

	public void setDestdir(File dir) {
		doc.outputDirectory = dir;
	}

	public void setCustomLess(String name) {
		customLessFile = name;
	}

	public void setInjectprettify(boolean value) {
		doc.injectPrettify = value;
	}

	public void setMinify(boolean value) {
		doc.minify = value;
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
	
	public void setRssfeed(String feed) {
		doc.rssFeed = feed;
	}

	public void setAtomfeed(String feed) {
		doc.atomFeed = feed;
	}

	protected void setToken(String token, Object value) {
		if (value == null) {
			value = "${" + token + "}";
		}
		createReplace().set("${" + token + "}", value);
	}

	@Override
	public void execute() throws BuildException {
		Build build = getBuild();
		
		// automatically setup substitution tokens
		setToken(Toolkit.Key.name.projectId(), build.getPom().name);
		setToken(Toolkit.Key.description.projectId(), build.getPom().description);
		setToken(Toolkit.Key.url.projectId(), build.getPom().url);
		setToken(Toolkit.Key.issuesUrl.projectId(), build.getPom().issuesUrl);
		setToken(Toolkit.Key.inceptionYear.projectId(), build.getPom().inceptionYear);
		setToken(Toolkit.Key.organization.projectId(), build.getPom().organization);
		setToken(Toolkit.Key.organizationUrl.projectId(), build.getPom().organizationUrl);
		setToken(Toolkit.Key.forumUrl.projectId(), build.getPom().forumUrl);
		setToken(Toolkit.Key.socialNetworkUrl.projectId(), build.getPom().socialNetworkUrl);
		setToken(Toolkit.Key.blogUrl.projectId(), build.getPom().blogUrl);
		setToken(Toolkit.Key.ciUrl.projectId(), build.getPom().ciUrl);
		setToken(Toolkit.Key.mavenUrl.projectId(), build.getPom().mavenUrl);
		if (build.getPom().scm != null) {
			setToken(Key.scmUrl.projectId(), build.getPom().scm.url);
		}

		setToken(Toolkit.Key.groupId.projectId(), build.getPom().groupId);
		setToken(Toolkit.Key.artifactId.projectId(), build.getPom().artifactId);
		setToken(Toolkit.Key.version.projectId(), build.getPom().version);
		setToken(Toolkit.Key.coordinates.projectId(), build.getPom().getCoordinates());
		
		setToken(Toolkit.Key.buildDate.projectId(), build.getBuildDateString());
		setToken(Toolkit.Key.buildTimestamp.projectId(), build.getBuildTimestamp());
		
		setToken(Toolkit.Key.releaseVersion.projectId(), build.getPom().releaseVersion);
		setToken(Toolkit.Key.releaseDate.projectId(), build.getReleaseDateString());

		setToken(Toolkit.Key.releaseDate.referenceId(), build.getReleaseDate());
		setToken(Toolkit.Key.buildDate.referenceId(), build.getBuildDate());

		for (Map.Entry<String, String> entry : build.getPom().getProperties().entrySet()) {
			setToken(entry.getKey(), entry.getValue());
		}
		
		if (doc.name == null) {
			doc.name = build.getPom().name;
		}
		
		if (doc.sourceDirectory == null) {
			doc.sourceDirectory = build.getConfig().getSiteSourceDirectory();
		}

		if (doc.outputDirectory == null) {
			doc.outputDirectory = build.getConfig().getSiteTargetDirectory();
		}
		
		if (!StringUtils.isEmpty(customLessFile)) {
			File lessfile = new File(customLessFile);
			if (!lessfile.exists()) {
				lessfile = new File(doc.sourceDirectory, customLessFile);
			}
			doc.customLessFile = lessfile;
		}

		
		titleClass(build.getPom().name);
		
		build.getSolver().loadDependency(new Dependency("mx:markdownpapers"));
		build.getSolver().loadDependency(new Dependency("mx:freemarker"));

		if (doc.outputDirectory.exists()) {
			FileUtils.delete(doc.outputDirectory);
		}
		doc.outputDirectory.mkdirs();

		extractHtmlResources(doc.outputDirectory);
		
		if (doc.logo != null) {
			try {
				FileUtils.copy(doc.outputDirectory, doc.logo.getFile());
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy logo file!");
			}
		}
		
		if (doc.favicon != null) {
			try {
				FileUtils.copy(doc.outputDirectory, doc.favicon.getFile());
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy favicon file!");
			}
		}
		
		// setup prev/next pager links
		for (Link menuLink : doc.structure.sublinks) {
			if (menuLink.isMenu && menuLink.showPager) {
				for (int i = 0, maxIndex = menuLink.sublinks.size() - 1; i <= maxIndex; i++) {
					Link pageLink = menuLink.sublinks.get(i);
					if (pageLink.isPage) {							
						// link to previous page
						Link prev = i == 0 ? null : menuLink.sublinks.get(i - 1);
						if (prev != null && prev.isPage) {
							pageLink.prevLink = prev;
						}

						// link to next page
						Link next = i == maxIndex ? null : menuLink.sublinks.get(i + 1);
						if (next != null && next.isPage) {
							pageLink.nextLink = next;
						}

						// show pager is dependent on having at least a prev or next
						pageLink.showPager = pageLink.prevLink != null || pageLink.nextLink != null;
						pageLink.pagerLayout = menuLink.pagerLayout;
						pageLink.pagerPlacement = menuLink.pagerPlacement;
					}
				}
			}

			// pages which are generated from a Freemarker template
			if (menuLink.isPage) { 
				// process navbar items
				prepareTemplatePage(menuLink);
			} else if (menuLink.isMenu) {
				// process menu items
				for (Link sublink : menuLink.sublinks) {
					prepareTemplatePage(sublink);
				}
			}
		}
		
		for (Link page : doc.freeformPages) {
			// process out-of-structure template pages
			prepareTemplatePage(page);
		}
		
		Docs.execute(build, doc, isVerbose());
		
		writeDependenciesAsJson();
		
		// add site resource directories
		for (File dir : build.getConfig().getResourceDirectories(Scope.site)) {
			createResource().createFileset().setDir(dir);
		}

		for (org.moxie.Resource resource : resources) {
			File destdir = doc.outputDirectory;
			if (!StringUtils.isEmpty(resource.prefix)) {
				destdir = new File(doc.outputDirectory, resource.prefix);
			}
			try {
				if (resource.file != null) {
					FileUtils.copy(destdir, resource.file);
				} else {
					for (FileSet fs : resource.filesets) {
						DirectoryScanner ds = fs.getDirectoryScanner(getProject());
						File fromDir = fs.getDir(getProject());

						for (String srcFile : ds.getIncludedFiles()) {
							File dir = destdir;
							if (srcFile.indexOf(File.separatorChar) > -1) {
								dir = new File(dir, srcFile.substring(0, srcFile.lastIndexOf(File.separatorChar)));
							}
							File file = new File(fromDir, srcFile);
							FileUtils.copy(dir, file);
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
		String lessContent = "";
		if (doc.customLessFile != null && doc.customLessFile.exists()) {
			lessContent = FileUtils.readContent(doc.customLessFile, "\n");
		}
		File file = new File(outputFolder, "bootstrap/css/custom.less");
		FileUtils.writeContent(file, lessContent);

		Build build = getBuild();
		build.getSolver().loadDependency(new Dependency("mx:rhino"));

		// compile Bootstrap and custom.less overrides into css
		try {
			LessUtils.compile(new File(outputFolder, "bootstrap/css/bootstrap.less"),
					new File(outputFolder, "bootstrap/css/bootstrap.css"), doc.minify);
		} catch (Exception e) {
			getConsole().error(e,  "Failed to compile LESS!");
		}
		
		// delete temporary resources
		deleteResource(outputFolder, "bootstrap/css/custom.less");
		deleteResource(outputFolder, "bootstrap/css/moxie.less");
		deleteResource(outputFolder, "bootstrap/css/bootstrap.less");
	}
	
	protected void deleteResource(File baseFolder, String file) {
		new File(baseFolder, file).delete();
	}
	
	protected void prepareTemplatePage(Link link) {
		// pages which are generated from a Freemarker template
		if (link.isPage && StringUtils.isEmpty(link.src) && 
				link.templates != null && link.templates.size() == 1) {
			String token = "%-" + link.as + "%";
			link.templates.get(0).setToken(token);
			link.content = token;
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
		File file  = new File(getBuild().getConfig().getSiteTargetDirectory(), "moxie-dependencies.json");
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
