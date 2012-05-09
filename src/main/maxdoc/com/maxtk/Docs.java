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
package com.maxtk;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.maxtk.utils.FileUtils;
import com.maxtk.utils.MarkdownUtils;
import com.maxtk.utils.StringUtils;

/**
 * Builds a web site or deployment documentation from Markdown source files.
 * 
 * @author James Moger
 * 
 */
public class Docs {

	private static PrintStream out = System.out;
	
	public static void execute(Build build, Doc doc, boolean verbose) {
		build.console.header();
		build.console.log("MaxDoc for {0}", build.getPom().name);
		build.console.header();
		if (verbose) {
			doc.describe(build.console);
		}
		if (doc.outputFolder.exists()) {
			FileUtils.delete(doc.outputFolder);
		}
		doc.outputFolder.mkdirs();
		
		injectBootstrap(doc, true, verbose);
		injectPrettify(doc, doc.injectPrettify, verbose);
		injectFancybox(doc, doc.injectFancybox, verbose);
		build.console.separator();
		
		generatePages(build, doc, verbose);
	}

	private static void generatePages(Build build, Doc doc, boolean verbose) {
		String projectName = build.getPom().name;
		if (StringUtils.isEmpty(projectName) && !StringUtils.isEmpty(doc.name)) {
			projectName = doc.name;
		}
		List<Link> allLinks = new ArrayList<Link>();
		for (Link link : doc.structure.sublinks) {
			allLinks.add(link);
			if (link.sublinks != null) {
				allLinks.addAll(link.sublinks);
			}
		}

		String header = generateHeader(projectName, build.project, doc);
		String footer = generateFooter(doc);

		build.console.log("Generating HTML from Markdown files in {0} ", doc.sourceFolder.getAbsolutePath());

		for (Link link : allLinks) {
			if (link.isMenu || link.isDivider || link.isLink) {
				// nothing to generate
				continue;
			}
			try {
				String fileName = getHref(link);
				build.console.log(1, "{0} => {1}", link.src, fileName);
				String markdownContent = FileUtils.readContent(new File(doc.sourceFolder, link.src), "\n");

				Map<String, String> nomarkdownMap = new HashMap<String, String>();

				// extract sections marked as no-markdown
				int nmd = 0;
				for (NoMarkdown nomarkdown : doc.nomarkdowns) {
					List<String> lines = Arrays.asList(markdownContent.split("\n"));
					StringBuilder strippedContent = new StringBuilder();
					for (int i = 0; i < lines.size(); i++) {
						String line = lines.get(i);

						if (line.trim().startsWith(nomarkdown.startToken)) {
							// found start token, look for end token
							int beginCode = i + 1;
							int endCode = beginCode;
							for (int j = beginCode; j < lines.size(); j++) {
								String endLine = lines.get(j);
								if (endLine.trim().startsWith(nomarkdown.endToken)) {
									endCode = j;
									break;
								}
							}

							if (endCode > beginCode) {
								// append a placeholder for extracted content
								String nomarkdownKey = "%NOMARKDOWN" + nmd + "%";
								strippedContent.append(nomarkdownKey).append( '\n');
								nmd++;

								// build the hunk from lines
								StringBuilder sb = new StringBuilder();
								for (String nl : lines.subList(beginCode, endCode)) {
									sb.append(nl).append('\n');
								}
								String hunk = sb.toString();

								// put the nomarkdown hunk in a hashmap and
								// optionally escape it for html
								if (nomarkdown.prettify) {
									// wrap the hunk with a Prettify pre tag
									StringBuilder ppclass = new StringBuilder();
									ppclass.append("prettyprint");
									if (nomarkdown.linenums) {
										ppclass.append(" linenums");
									}
									if (!StringUtils.isEmpty(nomarkdown.lang)) {
										ppclass.append(" ");
										ppclass.append(nomarkdown.lang);
									}
									StringBuilder code = new StringBuilder();
									code.append(MessageFormat.format("<pre class=''{0}''>\n", ppclass.toString()));
									code.append(StringUtils.escapeForHtml(hunk, false));
									code.append("</pre>");
									nomarkdownMap.put(nomarkdownKey, code.toString());
								} else if (nomarkdown.escape) {
									// escape the hunk
									nomarkdownMap.put(nomarkdownKey, StringUtils.escapeForHtml(hunk, false));
								} else {
									// leave the hunk as-is
									nomarkdownMap.put(nomarkdownKey, hunk);
								}

								// advance the i counter to endCode so we do not
								// include the lines within the hunk
								i = endCode + 1;
							} else {
								// could not find closing token
								strippedContent.append(line);
								strippedContent.append('\n');
							}
						} else {
							// regular line
							strippedContent.append(line);
							strippedContent.append('\n');
						}
					}

					// replace markdown content with the stripped content
					markdownContent = strippedContent.toString();
				}

				// page navigation
				List<Section> sections = new ArrayList<Section>();
				if (link.sidebar) {
					Map<String, AtomicInteger> counts = new HashMap<String, AtomicInteger>();
					StringBuilder sb = new StringBuilder();
					for (String line : Arrays.asList(markdownContent.split("\n"))) {
						if (line.length() == 0) {
							sb.append('\n');
							continue;
						}
						if (line.charAt(0) == '#') {
							String section = line.substring(0, line.indexOf(' '));
							String name = line.substring(line.indexOf(' ') + 1) .trim();
							if (name.endsWith(section)) {
								name = name.substring(0, name.indexOf(section)) .trim();
							}
							String h = "h" + section.length();
							if (!counts.containsKey(h)) {
								counts.put(h,  new AtomicInteger());
							}
							String id = section.length() + "." + counts.get(h).addAndGet(1);
							sections.add(new Section(id, name));							
							sb.append(MessageFormat.format("<{0} id=''{2}''>{1}</{0}>\n", h, name, id));
						} else {
							// preserve line
							sb.append(line);
							sb.append('\n');
						}
					}
					
					// replace markdown content with the navigable content
					markdownContent = sb.toString();
				}

				// transform markdown to html
				String content = transformMarkdown(markdownContent.toString());

				// reinsert nomarkdown chunks
				for (Map.Entry<String, String> nomarkdown : nomarkdownMap .entrySet()) {
					content = content.replaceFirst(nomarkdown.getKey(), Matcher.quoteReplacement(nomarkdown.getValue()));
				}

				for (Substitute sub : doc.substitutions) {
					content = content.replace(sub.token, sub.value);
				}
				for (Regex regex : doc.regexes) {
					content = content.replaceAll(regex.searchPattern, regex.replacePattern);
				}
				for (Prop prop : doc.props) {
					String loadedContent = generatePropertiesContent(prop);
					content = content.replace(prop.token, loadedContent);
				}
				for (Load load : doc.loads) {
					String loadedContent = FileUtils.readContent(new File( load.file), "\n");
					loadedContent = StringUtils.escapeForHtml(loadedContent, false);
					loadedContent = StringUtils.breakLinesForHtml(loadedContent);
					content = content.replace(load.token, loadedContent);
				}

				// Create the topbar links for this page
				String links = createLinks(link, doc.structure.sublinks, false);

				if (!StringUtils.isEmpty(doc.googlePlusId)) {
					links += "<li><a href='https://plus.google.com/"
							+ doc.googlePlusId
							+ "?prsrc=3' class='gpluspage'><img src='https://ssl.gstatic.com/images/icons/gplus-16.png' width='16' height='16 style='order: 0;'/></a></li>";
				}

				// add Google+1 link
				if (doc.googlePlusOne && !StringUtils.isEmpty(build.getPom().url)) {
					links += "<li><div class='gplusone'><g:plusone size='small' href='"
							+ build.getPom().url + "'></g:plusone></div></li>";
				}
				String linksHtml = readResource(doc, "links.html");
				linksHtml = linksHtml.replace("%PROJECTNAME%", projectName);
				linksHtml = linksHtml.replace("%PROJECTLINKS%", links);
				links = linksHtml;

				// write final document
				OutputStreamWriter writer = new OutputStreamWriter(
						new FileOutputStream(new File(doc.outputFolder,
								fileName)), Charset.forName("UTF-8"));
				writer.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">\n<html>\n<head>\n");
				writer.write(header);
				writer.write("\n</head>");
				if (doc.injectPrettify) {
					writer.write("\n<body onload='prettyPrint()'>");
				} else {
					writer.write("\n<body>");
				}
				writer.write(links);
				if (link.sidebar) {
					writer.write("\n<div class='container-fluid'>");
					writer.write("\n<div class='row-fluid'>");
					writer.write("\n<!-- sidebar -->");
					writer.write("\n<div class='span3'>");
					writer.write("\n<div class='well sidebar-nav'>");
					writer.write("\n<ul class='nav nav-list'>");
					writer.write("\n<li class='nav-header'>Table of Contents</li>");
					for (Section section : sections) {
						writer.write(MessageFormat.format("\n<li><a href=''#{0}''>{1}</a></li>\n", section.id, section.name));	
					}
					writer.write("\n</ul>");
					writer.write("\n</div>");
					writer.write("\n</div>");
					writer.write("\n<div class='span9'>");
				} else {
					writer.write("\n<div class='container'>");
				}
				writer.write("\n<!-- Begin Markdown -->\n");
				writer.write(content);
				writer.write("\n<!-- End Markdown -->\n");
				writer.write("<footer class=\"footer\">");
				writer.write(footer);
				writer.write("\n</footer>\n</div>");
				if (link.sidebar) {
					writer.write("\n</div></div>");
				}
				writer.write("\n\n<!-- Include scripts at end for faster page loading -->");
				if (doc.injectPrettify) {
					writer.append("\n<script src=\"./prettify/prettify.js\"></script>");
				}

				writer.append("\n<script src=\"./bootstrap/js/jquery.js\"></script>");
				writer.append("\n<script src=\"./bootstrap/js/bootstrap.js\"></script>");

				if (doc.injectFancybox) {
					String fancybox = readResource(doc, "fancybox.html");
					writer.append('\n');
					writer.append(fancybox);
					writer.append('\n');
				}

				if (!StringUtils.isEmpty(doc.googleAnalyticsId)) {
					String analytics = readResource(doc, "analytics.html");
					analytics = analytics.replace("%ANALYTICSID%", doc.googleAnalyticsId);
					writer.append('\n');
					writer.append(analytics);
					writer.append('\n');
				}

				writer.write("\n</body>");
				writer.write("\n</html>");
				writer.close();
			} catch (Throwable t) {
				build.console.error(t, "Failed to transform " + link.src);
			}
		}
	}

	static void injectBootstrap(Doc doc, boolean inject, boolean verbose) {
		if (!inject) {
			return;
		}
		if (verbose) {
			out.println("injecting Twitter Bootstrap");
		}
		extractZippedResource(doc, null, "bootstrap.zip");
	}

	static void injectPrettify(Doc doc, boolean inject, boolean verbose) {
		if (!inject) {
			return;
		}
		if (verbose) {
			out.println("injecting GoogleCode Prettify");
		}
		extractZippedResource(doc, null, "prettify.zip");
	}

	static void injectFancybox(Doc doc, boolean inject, boolean verbose) {
		if (!inject) {
			return;
		}
		if (verbose) {
			out.println("injecting Fancybox");
		}
		extractZippedResource(doc, "fancybox", "fancybox.zip");
	}

	static void extractZippedResource(Doc doc, String folder, String resource) {
		try {
			ZipInputStream is = new ZipInputStream(doc.getClass()
					.getResourceAsStream("/" + resource));
			File destFolder;
			if (StringUtils.isEmpty(folder)) {
				destFolder = doc.outputFolder;
			} else {
				destFolder = new File(doc.outputFolder, folder);
			}
			destFolder.mkdirs();
			ZipEntry entry = null;
			while ((entry = is.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					File file = new File(destFolder, entry.getName());
					file.mkdirs();
					continue;
				}
				FileOutputStream os = new FileOutputStream(new File(destFolder,
						entry.getName()));
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

	static String extractResource(Doc doc, String folder, String resource) {
		String content = "";
		try {
			InputStream is = doc.getClass().getResourceAsStream("/" + resource);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			byte[] buffer = new byte[32767];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}
			content = os.toString("UTF-8");
			os.close();
			is.close();
			File outputFile;
			if (StringUtils.isEmpty(folder)) {
				outputFile = new File(doc.outputFolder, resource);
			} else {
				outputFile = new File(new File(doc.outputFolder, folder),
						resource);
			}
			FileUtils.writeContent(outputFile, content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return content;
	}

	static String readResource(Doc doc, String resource) {
		String content = "";
		try {
			InputStream is = doc.getClass().getResourceAsStream("/" + resource);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			byte[] buffer = new byte[32767];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}
			content = os.toString("UTF-8");
			os.close();
			is.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return content;
	}

	static String readContent(File file) {
		String content = "";
		if (file.exists()) {
			content = FileUtils.readContent(file, "\n");
		}
		return content;
	}

	private static String createLinks(Link currentLink, List<Link> links, boolean isMenu) {
		String linkPattern = "<li><a href=''{0}''>{1}</a></li>\n";
		String currentLinkPattern = "<li class=''active''><a href=''{0}''>{1}</a></li>\n";

		StringBuilder sb = new StringBuilder();
		for (Link link : links) {
			boolean active = currentLink.equals(link);
			if (link.sublinks == null) {
				String href = getHref(link);
				if (link.isDivider) {
					if (isMenu) {
						// menu divider
						sb.append("<li class='divider'></li>\n");
					} else {
						// nav bar divider
						sb.append("<li class='divider-vertical'></li>\n");
					}
				} else if (active) {
					// current page
					sb.append(MessageFormat.format(currentLinkPattern, href,
							link.name));
				} else {
					// page link
					sb.append(MessageFormat
							.format(linkPattern, href, link.name));
				}
			} else {
				// drop down menu
				sb.append("<li class='dropdown'> <!-- Menu -->\n");
				sb.append(MessageFormat
						.format("<a class=''dropdown-toggle'' href=''#'' data-toggle=''dropdown''>{0}<b class=''caret''></b></a>\n",
								link.name));
				sb.append("<ul class='dropdown-menu'>\n");
				sb.append(createLinks(link, link.sublinks, true));
				sb.append("</ul></li> <!-- End Menu -->\n");
			}
		}
		sb.trimToSize();
		return sb.toString();
	}

	private static String getHref(Link link) {
		if (link.isLink) {
			// external link
			return link.src;
		} else if (link.isPage) {
			// page link
			String html = link.src.substring(0, link.src.lastIndexOf('.'))
					+ ".html";
			return html;
		}
		return null;
	}

	private static String transformMarkdown(String comment) throws ParseException {
		String md = MarkdownUtils.transformMarkdown(comment);
		if (md.startsWith("<p>")) {
			md = md.substring(3);
		}
		if (md.endsWith("</p>")) {
			md = md.substring(0, md.length() - 4);
		}
		return md;
	}

	private static String generateHeader(String projectName, Config conf, Doc doc) {
		out.println("Generating HTML header...");
		StringBuilder sb = new StringBuilder();
		String header = readResource(doc, "header.html");
		header = header.replace("%PROJECTNAME%", projectName);
		sb.append(header);

		if (doc.keywords != null && doc.keywords.size() > 0) {
			String keywords = StringUtils.flattenStrings(doc.keywords);
			sb.append(MessageFormat.format(
					"<meta name=\"keywords\" content=\"{0}\" />\n", keywords));
		}

		if (!StringUtils.isEmpty(doc.favicon)) {
			sb.append("\n<link rel='shortcut icon' type='image/png' href='./"
					+ doc.favicon + "' />");
		}

		if (doc.injectPrettify) {
			sb.append("\n<link rel=\"stylesheet\" href=\"./prettify/prettify.css\" />");
		}

		if (!StringUtils.isEmpty(doc.googlePlusId)) {
			String content = readResource(doc, "pluspage.html");
			content = content.replace("%PLUSID%", doc.googlePlusId);
			sb.append('\n');
			sb.append(content);
			sb.append('\n');
		}

		if (doc.googlePlusOne && !StringUtils.isEmpty(conf.pom.url)) {
			String content = readResource(doc, "plusone.html");
			content = content.replace("%URL%", conf.pom.url);
			sb.append('\n');
			sb.append(content);
			sb.append('\n');
		}

		if (doc.header != null && doc.header.exists()) {
			String content = FileUtils.readContent(doc.header, "\n");
			if (!StringUtils.isEmpty(content)) {
				sb.append("\n<!-- Header Contribution -->\n");
				sb.append(content);
				sb.append('\n');
			}
		}
		return sb.toString();
	}

	private static String generateFooter(Doc doc) {
		out.println("Generating HTML footer...");
		final String date = new SimpleDateFormat("yyyy-MM-dd")
				.format(new Date());
		String footer = readResource(doc, "footer.html");
		if (doc.footer != null && doc.footer.exists()) {
			String custom = FileUtils.readContent(doc.footer, "\n");
			if (!StringUtils.isEmpty(custom)) {
				footer = custom;
			}
		}
		footer = footer.replace("%PAGEDATE%", date);
		footer = footer
				.replace(
						"%PAGELICENSE%",
						"The content of this page is licensed under the <a href=\"http://creativecommons.org/licenses/by/3.0\">Creative Commons Attribution 3.0 License</a>.");
		return footer;
	}

	private static String generatePropertiesContent(Prop prop) throws Exception {
		BufferedReader propertiesReader = new BufferedReader(new FileReader(
				new File(prop.file)));

		Vector<Setting> settings = new Vector<Setting>();
		List<String> comments = new ArrayList<String>();
		String line = null;
		while ((line = propertiesReader.readLine()) != null) {
			if (line.length() == 0) {
				Setting s = new Setting("", "", comments);
				settings.add(s);
				comments.clear();
			} else {
				if (line.charAt(0) == '#') {
					comments.add(line.substring(1).trim());
				} else {
					String[] kvp = line.split("=", 2);
					String key = kvp[0].trim();
					Setting s = new Setting(key, kvp[1].trim(), comments);
					settings.add(s);
					comments.clear();
				}
			}
		}
		propertiesReader.close();

		StringBuilder sb = new StringBuilder();
		for (Setting setting : settings) {
			for (String comment : setting.comments) {
				if (prop.containsKeyword(comment)) {
					sb.append(MessageFormat
							.format("<span style=\"color:#004000;\"># <i>{0}</i></span>",
									transformMarkdown(comment)));
				} else {
					sb.append(MessageFormat.format(
							"<span style=\"color:#004000;\"># {0}</span>",
							transformMarkdown(comment)));
				}
				sb.append("<br/>\n");
			}
			if (!StringUtils.isEmpty(setting.name)) {
				sb.append(MessageFormat
						.format("<span style=\"color:#000080;\">{0}</span> = <span style=\"color:#800000;\">{1}</span>",
								setting.name,
								StringUtils.escapeForHtml(setting.value, false)));
			}
			sb.append("<br/>\n");
		}

		return sb.toString();
	}

	/**
	 * Setting represents a setting with its comments from the properties file.
	 */
	private static class Setting {
		final String name;
		final String value;
		final List<String> comments;

		Setting(String name, String value, List<String> comments) {
			this.name = name;
			this.value = value;
			this.comments = new ArrayList<String>(comments);
		}
	}
	
	private static class Section {
		String name;
		String id;
		
		Section(String id, String name) {
			this.id = id;
			this.name = name;
		}
	}
}