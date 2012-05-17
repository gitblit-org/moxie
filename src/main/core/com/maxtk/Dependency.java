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

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

import com.maxtk.utils.StringUtils;

/**
 * Dependency represents a retrievable artifact.
 */
public class Dependency implements Serializable {

	private static final long serialVersionUID = 1L;

	public String groupId;
	public String artifactId;
	public String version;
	public String ext;
	public String classifier;
	public boolean optional;	
	public boolean resolveDependencies;
	public Set<String> exclusions;

	public int ring;
	
	private String mediationId;
	private String coordinates;

	public Dependency() {
		ext = "jar";
		resolveDependencies = true;
		exclusions = new TreeSet<String>();
	}
	
	public Dependency(String def) {
		String [] principals = def.trim().split(" ");
		
		String coordinates = StringUtils.stripQuotes(principals[0]);
		if (coordinates.indexOf('@') > -1) {
			// strip @ext
			ext = coordinates.substring(coordinates.indexOf('@') + 1);			
			coordinates = coordinates.substring(0, coordinates.indexOf('@'));
			resolveDependencies = false;
		} else {
			ext = "jar";
			resolveDependencies = true;
		}

		// determine Maven artifact coordinates
		String [] fields = { groupId, artifactId, version, classifier, ext };
		
		// append trailing colon for custom splitting algorithm
		coordinates = coordinates + ":";
		
		// custom string split for performance, blanks are considered null
		StringBuilder sb = new StringBuilder();
		int field = 0;
		for (int i = 0, len = coordinates.length(); i < len; i++) {
			char c = coordinates.charAt(i);
			switch(c) {
			case ' ':
				break;
			case ':':
				fields[field] = sb.toString().trim();
				if (fields[field].length() == 0) {
					fields[field] = null;
				}
				sb.setLength(0);
				field++;
				break;
			default:
				sb.append(c);
				break;
			}
		}

		this.groupId = fields[0].replace('/', '.');
		this.artifactId = fields[1];
		this.version = fields[2];
		this.classifier = fields[3];
		this.ext = fields[4];

		// determine dependency options and transitive dependency exclusions
		exclusions = new TreeSet<String>();
		Set<String> options = new TreeSet<String>();
		for (String option : principals) {
			if (option.charAt(0) == '-') {
				// exclusion
				exclusions.add(option.substring(1));
			} else {
				// option
				options.add(option.toLowerCase());
			}
		}
		optional = options.contains("optional");
	}
	
	public boolean isMavenObject() {
		return groupId.charAt(0) != '<';
	}
	
	public String getExtension() {
		return "." + ext;
	}
	
	public String getSourceExtension() {
		return "-sources." + ext;
	}

	public String getMediationId() {
		if (mediationId == null) {
			mediationId = groupId + ":" + artifactId + ":" + (classifier == null ? "" : (":" + classifier)) + ":" + ext;
		}
		return mediationId;
	}

	public String getManagementId() {
		return groupId + ":" + artifactId;
	}

	public String getCoordinates() {
		if (coordinates == null) {
			coordinates = groupId + ":" + artifactId + ":" + (version == null ? "" : version) + (classifier == null ? "":(":" + classifier)) + ":" + ext;
		}
		return coordinates;
	}
	
	public boolean excludes(Dependency dependency) {
		return exclusions.contains(dependency.getMediationId()) 
				|| exclusions.contains(dependency.getManagementId())
				|| exclusions.contains(dependency.groupId);
	}

	@Override
	public int hashCode() {
		return getCoordinates().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Dependency) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
	
	@Override
	public String toString() {
		return getCoordinates() + (resolveDependencies ? " transitive":"") + (optional ? " optional":"");
	}
	
	public String toXML(Scope scope) {
		StringBuilder sb = new StringBuilder();
		sb.append("<dependency>\n");
		sb.append(StringUtils.toXML("groupId", groupId));
		sb.append(StringUtils.toXML("artifactId", artifactId));
		sb.append(StringUtils.toXML("version", version));
		sb.append(StringUtils.toXML("type", ext));
		if (!StringUtils.isEmpty(classifier)) {
			sb.append(StringUtils.toXML("classifier", classifier));
		}
		sb.append(StringUtils.toXML("scope", scope));
		if (optional) {
			sb.append(StringUtils.toXML("optional", true));
		}
		sb.append("</dependency>\n");
		return sb.toString();
	}
	
	public static String getMavenPath(Dependency dep, String ext, String pattern) {
		return getPath(dep,  ext, pattern, '.', '/');
	}

	public static String getMaxillaPath(Dependency dep, String ext, String pattern) {
		return getPath(dep,  ext, pattern, '/', '.');
	}

	private static String getPath(Dependency dep, String ext, String pattern, char a, char b) {
		String url = pattern;
		url = url.replace("${groupId}", dep.groupId.replace(a, b)).replace("${artifactId}", dep.artifactId).replace("${version}", dep.version).replace("${ext}", ext);		
		url = url.replace("${classifier}", dep.classifier == null ? "":("-" + dep.classifier));
		return url;
	}
}