package org.moxie;

import static java.text.MessageFormat.format;

import java.io.File;
import java.text.MessageFormat;
import java.util.Set;

import org.moxie.utils.StringUtils;

public class DependencyReport implements MoxieReport {

	@Override
	public String report(Build build) {

		String h2Pattern = "<h2>{0}</h2>\n";
		String aPattern = "<a href=\"{1}\" target=\"_blank\">{0}</a>";

		StringBuilder sb = new StringBuilder();
		sb.append(format(h2Pattern,"dependencies"));

		sb.append("<div class=\"tabbable\">\n<ul class=\"nav nav-tabs\">\n");
		// define tab titles
		boolean first = true;
		for (Scope scope : new Scope[] { Scope.compile, Scope.runtime, Scope.test, Scope.build }) {
			Set<Dependency> dependencies = build.getDependencies(scope);
			if (dependencies.size() == 0) {
				continue;
			}
			if (first) {
				first = false;
				sb.append(MessageFormat.format("<li class=\"active\"><a href=\"#{0}\" data-toggle=\"tab\">{0} </a></li>\n", scope.name()));
			} else {
				sb.append(MessageFormat.format("<li><a href=\"#{0}\" data-toggle=\"tab\">{0} </a></li>\n", scope.name()));
			}
		}
		
		// rings tab
		sb.append(MessageFormat.format("<li><a href=\"#{0}\" data-toggle=\"tab\">{0} </a></li>\n", "rings"));
		sb.append("</ul><div class=\"tab-content\">\n");
		
		// define tab content
		first = true;
		for (Scope scope : new Scope[] { Scope.compile, Scope.runtime, Scope.test, Scope.build }) {
			Set<Dependency> dependencies = build.getDependencies(scope);
			if (dependencies.size() == 0) {
				continue;
			}

			sb.append(MessageFormat.format("<div class=\"tab-pane{1}\" id=\"{0}\">\n", scope.name(), first ? " active":""));
			first = false;
			sb.append("<div class=\"row-fluid\">\n");
			sb.append("<table class=\"table table-striped table-bordered table-condensed\">\n");
			sb.append(format("<thead><tr><th>{0}</th><th>{1}</th><th>{2}</th><th>{3}</th><th>{4}</th></tr></thead>\n", "ring", "artifact", "size", "links", "license"));
			sb.append("<tbody>\n");
			for (Dependency dep : dependencies) {
				File file = build.getArtifact(dep);
				String size = file.length()/1024L + " kB";
				Pom depPom = build.getPom(dep);
				String badge;
				switch (dep.ring){
					case 1:
						badge = "badge-success";
						break;
					case 2:
						badge = "badge-info";
						break;
					case 3:
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
			}
			sb.append("</tbody></table></div></div>\n");
		}
		
		// rings tab
		sb.append("<div class=\"tab-pane\" id=\"rings\">\n");
		sb.append("<center><div id='chart'></div></center>\n");
		sb.append("<script src='./d3/d3.js' type='text/javascript'></script>\n");
		sb.append("<link href='./d3/rings.css' rel='stylesheet' type='text/css' />\n");
		sb.append("<script src='./d3/rings.js' type='text/javascript'></script>\n");
		sb.append("</div>\n");
		sb.append("</div>\n");
		
		return sb.toString();
	}
}
