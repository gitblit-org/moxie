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

import static java.text.MessageFormat.format;

import java.io.File;
import java.util.Set;

import org.apache.tools.ant.Project;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.Dependency;
import com.maxtk.License;
import com.maxtk.Pom;
import com.maxtk.Scope;
import com.maxtk.SourceFolder;
import com.maxtk.maxml.MaxmlMap;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;


public class MxReport extends MxTask {
	
	File outputFile;
	
	@Override
	public void setProject(Project project) {
		super.setProject(project);
		Build build = getBuild();
		configure(build);
	}
	
	private void configure(Build build) {
		MaxmlMap attributes = build.getMxReportAttributes();
		if (attributes == null) {
			return;
		}
		if (attributes.containsKey(Key.outputFile.name())) {
			outputFile = new File(attributes.getString(Key.outputFile.name(), null));
		}
		if (attributes.containsKey(Key.verbose.name())) {
			setVerbose(attributes.getBoolean(Key.verbose.name(), false));
		}
	}
	
	public File getOutputfile() {
		return outputFile;
	}
	
	public void setOutputfile(File file) {
		this.outputFile = file;
	}
	
	public void execute() {
		Build build = getBuild();		
		build.console.title(getClass(), build.getPom().getCoordinates());

		boolean verbose = isVerbose();
		
		Pom pom = build.getPom();
		
		String h2Pattern = "<h2>{0}</h2>\n";
		String kvpPattern = "<tr><th>{0}</th><td>{1}</td></tr>";
		String aPattern = "<a href=\"{1}\" target=\"_blank\">{0}</a>";
		
		StringBuilder sb = new StringBuilder("<html>\n<head>\n<link rel=\"stylesheet/less\" type=\"text/css\" href=\"./bootstrap/css/bootstrap.less\">\n");
		sb.append("<script src=\"./bootstrap/js/less-1.3.0.min.js\"></script>");
		sb.append(format("<title>{0} ({1})</title>\n", pom.name, pom.getCoordinates()));
		sb.append("</head><body>\n");
		sb.append("<div class='container-fluid'>\n");
		sb.append("<div class='row-fluid'>\n");
		sb.append("<div class='span2'>\n");
		sb.append("<ul class=\"nav nav-list\"><li class=\"nav-header\">Reports</li><li class=\"active\"><a href=\"#\">Project Data</a></li><li><a href=\"#\">Dependencies</a></li><li><a href=\"./tests/index.html\">Unit Tests</a></li><li><a href=\"./coverage/index.html\">Code Coverage</a></li></ul>");
		sb.append("</div>\n");
		sb.append("<div class='span10'>\n");
		sb.append(format("<h1>{0} <small>{1}</small></h1>\n", pom.name, pom.description));
		
		// project metadata
		sb.append(format(h2Pattern,"project metadata"));
		sb.append("<table class='table'>\n");
		addRow(sb, kvpPattern, "name", pom.name);
		addRow(sb, kvpPattern, "description", pom.description);
		if (!StringUtils.isEmpty(pom.url)) {
			addRow(sb, kvpPattern, "url", format(aPattern, pom.url, pom.url));
		}
		addRow(sb, kvpPattern, "organization", pom.organization);
		addRow(sb, kvpPattern, "groupId", pom.groupId);
		addRow(sb, kvpPattern, "artifactId", pom.artifactId);
		addRow(sb, kvpPattern, "version", pom.version);
		sb.append("</table>\n");

		// source folders
		sb.append("<p />\n");
		sb.append(format(h2Pattern,"source folders"));
		sb.append("<table class='table'>\n");
		for (SourceFolder sourceFolder : build.getSourceFolders()) {
			sb.append(format(kvpPattern, sourceFolder.name, sourceFolder.scope));	
		}
		sb.append("</table>\n");
		
		// dependencies
		sb.append("<p />\n");
		sb.append(format(h2Pattern,"dependency report"));
		for (Scope scope : new Scope[] { Scope.compile, Scope.runtime, Scope.test, Scope.build }) {
			Set<Dependency> dependencies = build.getDependencies(scope);
			if (dependencies.size() == 0) {
				continue;
			}
			sb.append(format("<h3>{0} dependencies</h3>\n", scope));
			if (verbose) {
				// log to console
				build.console.scope(scope, dependencies.size());
			}
			sb.append("<table class=\"table table-striped table-bordered table-condensed\">\n");
			sb.append(format("<thead><tr><th>{0}</th><th>{1}</th><th>{2}</th><th>{3}</th><th>{4}</th></tr></thead>\n", "ring", "artifact", "size", "links", "license"));
			sb.append("<tbody>\n");
			for (Dependency dep : dependencies) {
				File file = build.getArtifact(dep);
				String size = file.length()/1024L + " kB";
				Pom depPom = build.getPom(dep);
				String badge;
				switch (dep.ring){
					case 0:
						badge = "badge-success";
						break;
					case 1:
						badge = "badge-info";
						break;
					case 2:
						badge = "badge-inverse";
						break;
					default:
						badge = "badge";						
				}
				sb.append(format("<tr><td><span class=\"badge {1}\">{0,number,0}</span></td>", dep.ring, badge));
				if (!dep.isMavenObject()) {
					// not a Maven artifact
					sb.append(format("<td>{0}</td><td>{1}</td><td></td>", depPom.getCoordinates(), size));
				} else {
					// Maven artifact
					String mvnrepository = format("http://mvnrepository.com/artifact/{0}/{1}/{2}", depPom.groupId, depPom.artifactId, depPom.version);
					String mvnLink = format(aPattern, depPom.getCoordinates(), mvnrepository);
					String siteLink = "";
					if (!StringUtils.isEmpty(depPom.url)) {
						siteLink = format(aPattern, "site", depPom.url);
					}
					String issuesLink = "";
					if (!StringUtils.isEmpty(depPom.issuesUrl)) {
						issuesLink = format(aPattern, "issues", depPom.issuesUrl);
					}
					sb.append("<td>").append(mvnLink).append(format("</td><td>{0}</td><td>{1}</td>", size, siteLink + "&nbsp;" + issuesLink));
				}
				sb.append("<td>");
				if (depPom.getLicenses().size() == 0) {
					// no license specified
					sb.append("<span class=\"label label-warning\">Unknown!</span>");
				} else {
					// license(s)
					for (License license : depPom.getLicenses()) {					
						if (StringUtils.isEmpty(license.url)) {
							sb.append(format("{0}<br/>", license.name));
						} else {
							sb.append(format(aPattern, license.name, license.url)).append("<br/>");
						}
					}
				}
				sb.append("</td></tr>\n");
				if (verbose) {
					// log to console
					build.console.license(dep, depPom);
				}
			}
			sb.append("</tbody></table>\n");
		}
		
		sb.append("</div></div></body></html>");
		// write report
		if (outputFile == null) {
			outputFile = new File(build.getReportsFolder(), "index.html");
		}
		FileUtils.writeContent(outputFile, sb.toString());
		
		// extract resources
		File outputFolder = outputFile.getParentFile();
		extractHtmlResources(outputFolder);

		build.console.log("report {0} generated", outputFile.getAbsolutePath());
	}
	
	private void addRow(StringBuilder sb, String pattern, String field, String value) {
		if (!StringUtils.isEmpty(value)) {
			sb.append(format(pattern, field, value));
		}
	}
}
