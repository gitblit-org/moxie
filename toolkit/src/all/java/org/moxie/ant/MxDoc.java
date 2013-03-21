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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Doc;
import org.moxie.DocElement;
import org.moxie.DocMenu;
import org.moxie.DocPage;
import org.moxie.DocStructure;
import org.moxie.Docs;
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
	
	Logo logo;
	
	Logo favicon;

	List<org.moxie.Resource> resources = new ArrayList<org.moxie.Resource>();
	
	public MxDoc() {
		super();
		setTaskName("mx:doc");
	}

	public DocStructure createStructure() {
		DocStructure struct = new DocStructure();
		doc.structure = struct;
		return struct;
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
		logo = new Logo();
		return logo;
	}
	
	public Logo createFavicon() {	
		favicon = new Logo();
		return favicon;		
	}

	public DocPage createPage() {
		DocPage page = new DocPage();		
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

	public void setPrettifyTheme(String value) {
		doc.prettifyTheme = value;
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
		
		if (doc.templateDirectory == null) {
			doc.templateDirectory = new File(build.getConfig().getSiteSourceDirectory(), "templates");
		}
		
		doc.customLessFile = findFile(customLessFile);
		if (logo != null) {
			doc.logo = findFile(logo.getFile());
		}
		if (favicon != null) {
			doc.favicon = findFile(favicon.getFile());
		}
		
		titleClass(build.getPom().name);

		loadRuntimeDependencies(build, 
				new Dependency("mx:markdownpapers"),
				new Dependency("mx:freemarker"));

		Dependency bootstrap = new Dependency("mx:bootstrap");
		Dependency jquery = new Dependency("mx:jquery");
		Dependency d3js = new Dependency("mx:d3js");
		Dependency prettify = new Dependency("mx:prettify");
		Dependency less = new Dependency("mx:lesscss-engine");
		
		loadRuntimeDependencies(build, bootstrap, jquery, d3js, prettify, less);

		if (doc.outputDirectory.exists()) {
			FileUtils.delete(doc.outputDirectory);
		}
		doc.outputDirectory.mkdirs();
		
		if (doc.logo != null && doc.logo.exists()) {
			try {
				FileUtils.copy(doc.outputDirectory, doc.logo);
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy logo file!");
			}
		}
		
		if (doc.favicon != null && doc.favicon.exists()) {
			try {
				FileUtils.copy(doc.outputDirectory, doc.favicon);
			} catch (IOException e) {
				getConsole().error(e, "Failed to copy favicon file!");
			}
		}
		
		extractBootstrap(bootstrap, less, doc.outputDirectory);
		extractJQuery(jquery, doc.outputDirectory);
		extractD3js(d3js, doc.outputDirectory);
		extractPrettify(prettify, doc.outputDirectory);
		
		// setup prev/next pager links
		preparePages(doc.structure.elements);
		
		for (DocPage page : doc.freeformPages) {
			// process out-of-structure template pages
			prepareTemplatePage(page);
		}
		
		createNomarkdown().configure("---NOMARKDOWN---", false, false, null);
		createNomarkdown().configure("---CODE---", true, false, null);
		createNomarkdown().configure("---JAVA---", true, true, "java");
		createNomarkdown().configure("---JSON---", true, true, "json");
		createNomarkdown().configure("---CSS---", true, true, "css");
		createNomarkdown().configure("---XML---", true, true, "xml");
		createNomarkdown().configure("---YAML---", true, true, "yaml");
		createNomarkdown().configure("---SQL---", true, true, "sql");

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
	
	protected void preparePages(List<DocElement> elements) {
		for (DocElement element : elements) {
			if (element instanceof DocMenu) {
				DocMenu menu = (DocMenu) element;

				for (int i = 0, maxIndex = menu.elements.size() - 1; i <= maxIndex; i++) {
					DocElement subElement = menu.elements.get(i);
					if (subElement instanceof DocPage) {
						prepareTemplatePage((DocPage) subElement);

						if (menu.showPager) {
							// link to previous page
							DocPage page = (DocPage) subElement;
							DocElement prev = i == 0 ? null : menu.elements.get(i - 1);
							if (prev != null && prev instanceof DocPage) {
								page.prevPage = (DocPage) prev;
							}

							// link to next page
							DocElement next = i == maxIndex ? null : menu.elements.get(i + 1);
							if (next != null && next instanceof DocPage) {
								page.nextPage = (DocPage) next;
							}

							// show pager is dependent on having at least a prev or next
							page.showPager = page.prevPage != null || page.nextPage != null;
							page.pagerLayout = menu.pagerLayout;
							page.pagerPlacement = menu.pagerPlacement;
						}

					} else if (subElement instanceof DocMenu) {
						// process menu/submenu
						preparePages(Arrays.asList(subElement));
					}
				}
			} else if (element instanceof DocPage) {
				// pages which are generated from a Freemarker template
				// process navbar items
				prepareTemplatePage((DocPage) element);
			}
		}
	}

	protected File findFile(String filename) {
		if (!StringUtils.isEmpty(filename)) {
			List<File> dirs = new ArrayList<File>();
			dirs.add(null); // dir specified in filename 
			dirs.add(getBuild().getConfig().getSiteSourceDirectory());
			dirs.addAll(getBuild().getConfig().getResourceDirectories(Scope.site));
			for (File dir : dirs) {
				File aFile = new File(dir, filename);
				if (aFile.exists()) {
					return aFile;
				}
			}
		}
		return null;
	}

	protected void extractBootstrap(Dependency bsDep, Dependency lessDep, File outputFolder) {
		getConsole().debug("injecting Twitter Bootstrap");

		String wj = MessageFormat.format("META-INF/resources/webjars/{0}/{1}/", bsDep.artifactId, bsDep.version);

		extractResource(outputFolder, wj + "js/bootstrap.min.js", "bootstrap/js/bootstrap.min.js", true);
		extractResource(outputFolder, wj + "css/bootstrap-responsive.min.css", "bootstrap/css/bootstrap-responsive.min.css", true);
		extractResource(outputFolder, wj + "img/glyphicons-halflings.png", "bootstrap/img/glyphicons-halflings.png", true);
		extractResource(outputFolder, wj + "img/glyphicons-halflings-white.png", "bootstrap/img/glyphicons-halflings-white.png", true);

		String [] modules = { "accordion", "alerts", "bootstrap", "breadcrumbs", "button-groups", "buttons",
				"carousel", "close", "code", "component-animations", "dropdowns", "forms", "grid", "hero-unit",
				"labels-badges", "layouts", "media", "mixins", "modals", "navbar", "navs", "pager", "pagination",
				"popovers", "progress-bars", "reset", "scaffolding", "sprites", "tables", "thumbnails", "tooltip",
				"type", "utilities", "variables", "wells" };
		
		for (String module : modules) {
			extractResource(outputFolder, wj + "less/" + module + ".less", "bootstrap/less/" + module + ".less", true);
		}

		// extract Moxie's Bootstrap overrides
		extractResource(outputFolder, "moxie.less");
		
		File bsLess = new File(outputFolder, "bootstrap/less/bootstrap.less");
		String content = FileUtils.readContent(bsLess, "\n");
		content += "\n" + FileUtils.readContent(new File(outputFolder, "moxie.less"), "\n");
		if (doc.customLessFile != null && doc.customLessFile.exists()) {
			content += "\n" + FileUtils.readContent(doc.customLessFile, "\n");
		}
		FileUtils.writeContent(bsLess, content);

		Build build = getBuild();		
		loadRuntimeDependencies(build, new Dependency("mx:rhino"));

		// compile Bootstrap and custom.less overrides into css
		try {
			File bsCss = new File(outputFolder, "bootstrap/css/bootstrap.css");
			if (doc.minify) {
				getConsole().log("compiling and minifying {0}...", bsCss.getAbsolutePath());
			} else {
				getConsole().log("compiling {0}...", bsCss.getAbsolutePath());
			}

			long start = System.currentTimeMillis();
			String css = LessUtils.compile(bsLess, doc.minify);
			FileUtils.writeContent(bsCss, css);

			getConsole().log("css generated in {0} msecs", System.currentTimeMillis() - start);
			
		} catch (Exception e) {
			getConsole().error(e,  "Failed to compile LESS!");
		}
		
		// remove less folder
		FileUtils.delete(new File(outputFolder, "less"));
	}

	protected void extractJQuery(Dependency dep, File outputFolder) {
		getConsole().debug("injecting JQuery");

		String wj = MessageFormat.format("META-INF/resources/webjars/{0}/{1}/", dep.artifactId, dep.version);

		extractResource(outputFolder, wj + "jquery.min.js", "bootstrap/js/jquery.js", true);
	}

	protected void extractD3js(Dependency dep, File outputFolder) {
		getConsole().debug("injecting D3");
		String wj = MessageFormat.format("META-INF/resources/webjars/{0}/{1}/", dep.artifactId, dep.version);
		extractResource(outputFolder, wj + "d3.v2.min.js", "d3/d3.js", true);
		
		extractResource(outputFolder, "d3/rings.css");
		extractResource(outputFolder, "d3/rings.js");
	}

	protected void extractPrettify(Dependency dep, File outputFolder) {
		getConsole().debug("injecting GoogleCode Prettify");

		String wj = MessageFormat.format("META-INF/resources/webjars/{0}/{1}/", dep.artifactId, dep.version);

		extractResource(outputFolder, wj + "prettify.js", "prettify/prettify.js", true);
		extractResource(outputFolder, wj + "prettify.css", "prettify/prettify.css", true);
		
		String [] langs = { "apollo", "clj", "css", "go", "hs", "lisp", "lua", "ml", "n", "proto", "scala",
				"sql", "tex", "vb", "vhdl", "wiki", "xq", "yaml" };
		
		for (String lang : langs) {
			extractResource(outputFolder, wj + "lang-" + lang + ".js", "prettify/lang-" + lang + ".js", true);
		}
		
		extractZippedResource("prettify-themes.zip", outputFolder, "prettify");
	}
	
	protected void extractZippedResource(String zipResource, File outputDirectory, String toDir) {
		try {
			ZipInputStream is = new ZipInputStream(getClass().getResourceAsStream("/" + zipResource));
			File destFolder = new File(outputDirectory, toDir);
			destFolder.mkdirs();
			ZipEntry entry = null;
			while ((entry = is.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					File file = new File(destFolder, entry.getName());
					file.mkdirs();
					continue;
				}
				FileOutputStream os = new FileOutputStream(new File(destFolder,	entry.getName()));
				byte[] buffer = new byte[32767];
				int len = 0;
				while ((len = is.read(buffer)) > -1) {
					os.write(buffer, 0, len);
				}
				os.close();
				is.closeEntry();
			}
			is.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	protected void deleteResource(File baseFolder, String file) {
		new File(baseFolder, file).delete();
	}
	
	protected void prepareTemplatePage(DocPage page) {
		// pages which are generated from a Freemarker template
		if (StringUtils.isEmpty(page.src) && 
				page.templates != null && page.templates.size() == 1) {
			String token = "%-" + page.as + "%";
			page.templates.get(0).setToken(token);
			page.content = token;
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
