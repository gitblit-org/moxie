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
package org.moxie;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
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

import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.MarkdownUtils;
import org.moxie.utils.StringUtils;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;


/**
 * Builds a web site or deployment documentation from Markdown source files.
 * 
 * @author James Moger
 * 
 */
public class Docs {

	private static PrintStream out = System.out;
	
	public static void execute(Build build, Doc doc, boolean verbose) {		

		List<DocElement> revisedElements = new ArrayList<DocElement>();
		
		// prepare report links
		for (DocElement element : doc.structure.elements) {
			if (element instanceof DocReport) {
				// switch to menu
				DocMenu reportMenu = new DocMenu();
				
				// project report
				DocPage projectReport = reportMenu.createPage();
				projectReport.setName("project data");
				projectReport.setContent(new ProjectReport().report(build));
				projectReport.setSrc("moxie-project.html");
				
				// dependency report
				DocPage depReport = reportMenu.createPage();
				depReport.setName("dependencies");
				depReport.setContent(new DependencyReport().report(build));
				depReport.setSrc("moxie-dependencies.html");

				File testOutput = new File(build.getConfig().getReportsTargetDirectory(), "tests");
				File coverageOutput = new File(build.getConfig().getReportsTargetDirectory(), "coverage");
				
				boolean hasTests = testOutput.exists();
				boolean hasCoverage = coverageOutput.exists();
				
				if (hasTests || hasCoverage) {
					reportMenu.createDivider();

					if (hasTests) {
						// test report
						try {
							FileUtils.copy(new File(doc.outputDirectory, "tests"), testOutput);
						} catch (IOException e) {
							build.getConsole().error(e, "failed to copy test report");
						}
						DocPage testReport = reportMenu.createPage();
						testReport.setName("unit tests");
						testReport.setContent(iframe("tests/index.html"));
						testReport.setSrc("moxie-tests.html");
					}

					if (hasCoverage) {
						// code coverage report
						try {
							FileUtils.copy(new File(doc.outputDirectory, "coverage"), coverageOutput);
						} catch (IOException e) {
							build.getConsole().error(e, "failed to copy coverage report");
						}
						DocPage coverageReport = reportMenu.createPage();
						coverageReport.setName("code coverage");
						coverageReport.setContent(iframe("coverage/index.html"));
						coverageReport.setSrc("moxie-coverage.html");
					}
				}
				
				// add reports menu
				revisedElements.add(reportMenu);
			} else {
				// do not alter
				revisedElements.add(element);
			}
		}
		
		doc.structure.elements = revisedElements;
		
		if (verbose) {
			doc.describe(build.getConsole());
		}
		
		if (verbose) {
			build.getConsole().separator();
		}
		
		generatePages(build, doc, verbose);
	}
	
	private static String iframe(String src) {
		return MessageFormat.format("<iframe src=\"{0}\" style=\"border:1px solid #ccc;\" width=\"100%\" height=\"90%\"></iframe>", src);
	}

	private static void generatePages(Build build, Doc doc, boolean verbose) {
		String projectName = build.getPom().name;
		if (StringUtils.isEmpty(projectName) && !StringUtils.isEmpty(doc.name)) {
			projectName = doc.name;
		}
		List<DocElement> allElements = new ArrayList<DocElement>();
		for (DocElement element : doc.structure.elements) {
			allElements.add(element);
			if (element instanceof DocMenu) {
				allElements.addAll(((DocMenu) element).elements);
			}
		}

		String header = generateHeader(projectName, build.getConfig().getProjectConfig(), doc);
		String footer = generateFooter(doc);

		if (!doc.structure.elements.isEmpty()) {
			build.getConsole().log("Generating Structured Documentation from source files... ");
		}

		// read references
		if (doc.references == null) {
			doc.references = new References();			
		} else {
			String content = FileUtils.readContent(new File(doc.sourceDirectory, doc.references.src), "\n");
			doc.references.content = "\n\n" + content; 
		}
		
		for (DocElement element : allElements) {
			if (!(element instanceof DocPage)) {
				// nothing to generate
				continue;
			}
			DocPage page = (DocPage) element;
			try {
				String fileName = getHref(page);
				if (StringUtils.isEmpty(page.src)) {
					// template page
					build.getConsole().log(1, "{0} => {1}", page.templates.get(0).src, fileName);
				} else {
					// markdown page
					build.getConsole().log(1, "{0} => {1}", page.src, fileName);
				}
				String content;
				List<Section> sections = new ArrayList<Section>();
				String pager = "";

				if (page.content != null) {
					// generated content
					content = page.content;
					
					processTemplates(doc, page);
					
					for (Substitute sub : doc.substitutions) {
						if (page.processSubstitutions || sub.isTemplate) {
							content = content.replace(sub.token, sub.value.toString());
						}
					}
					for (Regex regex : doc.regexes) {
						content = content.replaceAll(regex.searchPattern, regex.replacePattern);
					}
				} else if (page.src.endsWith(".html") || page.src.endsWith(".htm")) {
					// static html content
					content = FileUtils.readContent(new File(doc.sourceDirectory, page.src), "\n");
				} else {
					// begin markdown
					String markdownContent = FileUtils.readContent(new File(doc.sourceDirectory, page.src), "\n");
					
					// append references, if specified
					if (!StringUtils.isEmpty(doc.references.content)) {
						markdownContent += doc.references.content;
					}

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
									i = endCode;
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

					// prev/next pager links
					if (page.showPager) {
						String prev;
						if (page.prevPage == null) {
							prev = "";
						} else {
							prev = MessageFormat.format("<li class=\"previous\"><a href=\"{0}\">&larr; {1}</a></li>", getHref(page.prevPage), page.prevPage.name);
						}
						String next;
						if (page.nextPage == null) {
							next = "";
						} else {
							next = MessageFormat.format("<li class=\"next\"><a href=\"{0}\">{1} &rarr;</a></li>", getHref(page.nextPage), page.nextPage.name);
						}
						String divClass = "";
						String pagerClass = "";
						if (!StringUtils.isEmpty(page.pagerLayout)) {
							if ("right".equals(page.pagerLayout)) {
								divClass = "class=\"pull-right\"";
								pagerClass = "class=\"pager\"";
							} else if ("justified".equals(page.pagerLayout)) {
								pagerClass = "class=\"pager\"";
							}
						}
						pager = MessageFormat.format("<div {0}><ul {1}>{2} {3}</ul></div>", divClass, pagerClass, prev, next);
					}

					// header links
					AtomicInteger sectionCounter = new AtomicInteger();
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
							String id = "H" + sectionCounter.addAndGet(1);
							sections.add(new Section(id, name));
							if (page.showHeaderLinks) {
								sb.append(MessageFormat.format("<{0} class=\"section\" id=''{2}''><a href=\"#{2}\" class=\"sectionlink\"><i class=\"icon-share-alt\"> </i></a>{1}</{0}>\n", h, name, id));
							} else {
								sb.append(MessageFormat.format("<{0} id=''{2}''>{1}</{0}>\n", h, name, id));
							}
						} else {
							// preserve line
							sb.append(line);
							sb.append('\n');
						}
					}

					// replace markdown content with the navigable content
					markdownContent = sb.toString();

					// transform markdown to html
					content = transformMarkdown(markdownContent.toString());

					// reinsert nomarkdown chunks
					for (Map.Entry<String, String> nomarkdown : nomarkdownMap .entrySet()) {
						content = content.replaceFirst(nomarkdown.getKey(), Matcher.quoteReplacement(nomarkdown.getValue()));
					}
					
					// templates
					processTemplates(doc, page);

					for (Substitute sub : doc.substitutions) {
						if (page.processSubstitutions || sub.isTemplate) {
							content = content.replace(sub.token, sub.value.toString());
						}
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
					// end markdown
				}
				
				// Create the topbar links for this page
				String links = createLinks(page, doc.structure.elements);

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
				if (doc.logo != null) {
					linksHtml = linksHtml.replace("%PROJECTLOGO%", doc.logo.getName());
				}
				links = linksHtml;

				// write final document
				OutputStreamWriter writer = new OutputStreamWriter(
						new FileOutputStream(new File(doc.outputDirectory,
								fileName)), Charset.forName("UTF-8"));
				writer.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">\n<html>\n<head>\n");
				writer.write(header);

				if (doc.injectPrettify) {
					writer.append("\n<script src=\"./prettify/prettify.js\"></script>");
				}

				writer.append("\n<script src=\"./bootstrap/js/jquery.js\"></script>");
				writer.append("\n<script src=\"./bootstrap/js/bootstrap.min.js\"></script>");
				
				writer.write("\n</head>");
				if (doc.injectPrettify) {
					writer.write("\n<body onload='prettyPrint()'>");
				} else {
					writer.write("\n<body>");
				}
				writer.write(links);
				if (page.showToc) {
					if (page.isFluidLayout) {
						writer.write("\n<div class='container-fluid'>");
						writer.write("\n<div class='row-fluid'>");
					} else {
						writer.write("\n<div class='container'>");
						writer.write("\n<div class='row'>");
					}
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
					if (page.isFluidLayout) {
						writer.write("\n<div class='container-fluid'>");
					} else {
						writer.write("\n<div class='container'>");
					}
				}
				
				boolean manualPagerPlacement = false;
				// replace %PAGER%
				if (content.contains("%PAGER%")) {
					manualPagerPlacement = true;
					content = content.replace("%PAGER%", pager);
				}
				
				if (!manualPagerPlacement) {
					if (!StringUtils.isEmpty(page.pagerPlacement) && page.pagerPlacement.contains("top")) {
						// top pager
						writer.write(pager);
					}
				}
				
				writer.write("\n<!-- Begin Markdown -->\n");
				writer.write(content);
				writer.write("\n<!-- End Markdown -->\n");
								
				if (!manualPagerPlacement) {
					if (!StringUtils.isEmpty(page.pagerPlacement) && page.pagerPlacement.contains("bottom")) {
						// bottom pager
						writer.write(pager);
					}
				}

				writer.write("<footer class=\"footer\">");
				writer.write(footer);
				writer.write("\n</footer>\n</div>");
				if (page.showToc) {
					writer.write("\n</div></div>");
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
				build.getConsole().error(t, "Failed to transform " + page.src);
			}
		}
		
		// process pages which are not part of the normal structure
		if (!doc.freeformPages.isEmpty()) {
			build.getConsole().log("Generating content from Freemarker templates...");
			for (DocPage page : doc.freeformPages) {
				try {
					// template pages
					String fileName = getHref(page);
					build.getConsole().log(1, "{0} => {1}", page.templates.get(0).src, fileName);

					String content = page.content;
					processTemplates(doc, page);
					for (Substitute sub : doc.substitutions) {
						if (page.processSubstitutions || sub.isTemplate) {
							content = content.replace(sub.token, sub.value.toString());
						}
					}
					for (Regex regex : doc.regexes) {
						content = content.replaceAll(regex.searchPattern, regex.replacePattern);
					}

					File output = new File(doc.outputDirectory, page.as);
					FileUtils.writeContent(output, content);
				} catch (Throwable t) {
					build.getConsole().error(t, "Failed to transform " + page.src);
				}
			}
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
				outputFile = new File(doc.outputDirectory, resource);
			} else {
				outputFile = new File(new File(doc.outputDirectory, folder),
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

	private static String createLinks(DocElement currentElement, List<DocElement> elements) {
		String linkPattern = "<li><a href=''{0}''>{1}</a></li>\n";
		String currentLinkPattern = "<li class=''active''><a href=''{0}''>{1}</a></li>\n";

		StringBuilder sb = new StringBuilder();
		for (DocElement element : elements) {
			if (element instanceof DocPage) {
				DocPage page = (DocPage) element;
				if (!page.showNavbarLink) {
					continue;
				}
				String href = getHref(page);
				boolean active = currentElement.equals(page);
				if (active) {
					// current page
					sb.append(MessageFormat.format(currentLinkPattern, href, page.name));
				} else {
					// page link
					sb.append(MessageFormat.format(linkPattern, href, page.name));
				}
			} else if (element instanceof DocLink) {
				// ext link
				DocLink link = (DocLink) element;
				String href = getHref(link);
				sb.append(MessageFormat.format(linkPattern, href, link.name));
			} else if (element instanceof DocDivider) {
				if (currentElement instanceof DocMenu) {
					// menu divider
					sb.append("<li class='divider'></li>\n");
				} else {
					// nav bar divider
					sb.append("<li class='divider-vertical'></li>\n");
				}
			} else if (element instanceof DocMenu) {
				// drop down menu
				DocMenu menu = (DocMenu) element;
				sb.append("<li class='dropdown'> <!-- Menu -->\n");
				sb.append(MessageFormat
						.format("<a class=''dropdown-toggle'' href=''#'' data-toggle=''dropdown''>{0}<b class=''caret''></b></a>\n",
								menu.name));
				sb.append("<ul class='dropdown-menu'>\n");
				sb.append(createLinks(menu, menu.elements));
				sb.append("</ul></li> <!-- End Menu -->\n");
			}
		}
		sb.trimToSize();
		return sb.toString();
	}

	private static String getHref(DocElement element) {
		if (element instanceof DocLink) {
			// external link
			return ((DocLink) element).src;
		} else if (element instanceof DocPage) {
			// page link
			DocPage page = (DocPage) element;
			if (StringUtils.isEmpty(page.as)) {
				String html = page.src.substring(0, page.src.lastIndexOf('.'))
					+ ".html";
				return html;
			}
			return page.as;
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

	private static String generateHeader(String projectName, ToolkitConfig conf, Doc doc) {
		out.println("Generating HTML header...");
		StringBuilder sb = new StringBuilder();
		String header = readResource(doc, "header.html");
		header = header.replace("%PROJECTNAME%", projectName);
		sb.append(header);
		
		if (doc.isResponsiveLayout) {
			sb.append("<!-- Responsive CSS must be included after the above body css! -->\n");
			sb.append("<link rel='stylesheet' href='./bootstrap/css/bootstrap-responsive.min.css'>\n");
		}

		if (doc.keywords != null && doc.keywords.size() > 0) {
			String keywords = StringUtils.flattenStrings(doc.keywords);
			sb.append(MessageFormat.format(
					"<meta name=\"keywords\" content=\"{0}\" />\n", keywords));
		}

		if (doc.favicon != null) {
			sb.append("\n<link rel='shortcut icon' type='image/png' href='./"
					+ doc.favicon.getName() + "' />");
		}

		if (doc.injectPrettify) {
			if (StringUtils.isEmpty(doc.prettifyTheme)) {
				// default theme
				sb.append("\n<link rel=\"stylesheet\" href=\"./prettify/prettify.css\" />");
			} else {
				// custom theme
				sb.append(MessageFormat.format("\n<link rel=\"stylesheet\" href=\"./prettify/{0}\" />", 
						doc.prettifyTheme + (doc.prettifyTheme.toLowerCase().endsWith(".css") ? "" : ".css")));
			}
		}
		
		if (!StringUtils.isEmpty(doc.rssFeed)) {
			sb.append(MessageFormat.format(
					"<link rel=\"alternate\" type=\"application/rss+xml\" title=\"{0} RSS 2.0\" href=\"./{1}\" />\n", doc.name, doc.rssFeed));
		}

		if (!StringUtils.isEmpty(doc.atomFeed)) {
			sb.append(MessageFormat.format(
					"<link rel=\"alternate\" type=\"application/atom+xml\" title=\"{0} Atom\" href=\"./{1}\" />\n", doc.name, doc.atomFeed));
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
	
	protected static void processTemplates(Doc doc, DocPage page) throws TemplateException, MaxmlException, IOException {
		// templates
		if (page.templates != null && !page.templates.isEmpty()) {
			// Freemarker engine
			Configuration fm = new Configuration();
			fm.setObjectWrapper(new DefaultObjectWrapper());
			if (doc.templateDirectory != null && doc.templateDirectory.exists()) {
				fm.setDirectoryForTemplateLoading(doc.templateDirectory);
			} else {
				fm.setDirectoryForTemplateLoading(doc.sourceDirectory);
			}
			
			for (Template template : page.templates) {
				File data = new File(template.data);
				if (!data.exists()) {
					data = new File(doc.sourceDirectory, template.data);
				}
				MaxmlMap dataMap = Maxml.parse(data);
				
				// populate map with build properties by splitting them into maps
				for (Substitute sub : doc.substitutions) {
					if (sub.isProperty()) {
						String prop = sub.getPropertyName();
						MaxmlMap keyMap = dataMap;
						// recursively create/find the destination map
						while (prop.indexOf('.') > -1) {
							String m = prop.substring(0, prop.indexOf('.'));
							if (!keyMap.containsKey(m)) {
								keyMap.put(m, new MaxmlMap());
							}
							keyMap = keyMap.getMap(m);
							prop = prop.substring(m.length() + 1);
						}
						
						// inject property into map
						keyMap.put(prop, sub.value);
					}
				}
				
				// load and process the Freemarker template
				freemarker.template.Template ftl = fm.getTemplate(template.src);
				StringWriter writer = new StringWriter();
				ftl.process(dataMap, writer);
				
				// create a substitution token
				Substitute sub = new Substitute();
				sub.isTemplate = true;
				sub.token = template.token;
				sub.value = writer.toString();
				doc.substitutions.add(sub);
			}
		}
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