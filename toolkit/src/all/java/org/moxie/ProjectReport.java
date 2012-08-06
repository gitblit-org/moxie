package org.moxie;

import static java.text.MessageFormat.format;

import org.moxie.utils.StringUtils;

public class ProjectReport implements MoxieReport {

	@Override
	public String report(Build build) {
		Pom pom = build.getPom();
		
		String h2Pattern = "<h2>{0}</h2>\n";
		String kvpPattern = "<tr><th>{0}</th><td>{1}</td></tr>";
		String aPattern = "<a href=\"{1}\" target=\"_blank\">{0}</a>";

		StringBuilder sb = new StringBuilder();

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
		if (build.getConfig().getSourceFolders().size() > 0) {
			sb.append("<p />\n");
			sb.append(format(h2Pattern,"source folders"));
			sb.append("<table class='table'>\n");
			for (SourceFolder sourceFolder : build.getConfig().getSourceFolders()) {
				sb.append(format(kvpPattern, sourceFolder.name, sourceFolder.scope));	
			}
			sb.append("</table>\n");
		}
		return sb.toString();
	}
	
	private void addRow(StringBuilder sb, String pattern, String field, String value) {
		if (!StringUtils.isEmpty(value)) {
			sb.append(format(pattern, field, value));
		}
	}
}
