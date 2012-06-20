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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TimeZone;

import org.moxie.utils.StringUtils;

public class Metadata {

	public String groupId;
	public String artifactId;
	public String latest;
	public String release;
	public Date lastUpdated;

	private final List<String> versions;

	public Metadata() {
		versions = new ArrayList<String>();
	}

	public void addVersion(String version) {
		versions.add(version);
	}

	public void merge(Metadata oldMetadata) {
		// merge versions
		LinkedHashSet<ArtifactVersion> set = new LinkedHashSet<ArtifactVersion>();
		for (String version : versions) {
			set.add(new ArtifactVersion(version));
		}		
		for (String version : oldMetadata.versions) {
			set.add(new ArtifactVersion(version));
		}
		
		// sort them by Maven rules
		List<ArtifactVersion> list = new ArrayList<ArtifactVersion>(set);				
		Collections.sort(list);
		
		// convert back to simple strings and determine latest and release
		ArtifactVersion latest = null;
		ArtifactVersion release = null;
		versions.clear();
		for (ArtifactVersion version : list) {
			versions.add(version.toString());
			if (StringUtils.isEmpty(version.getQualifier())) {
				if (release == null || release.compareTo(version) == -1) {
					release = version;
				}
			}			
			if (latest == null || latest.compareTo(version) == -1) {
				latest = version;
			}
		}
		
		if (release != null) {
			this.release = release.toString();
		}

		if (latest != null) {
			this.latest = latest.toString();
		}

		if (oldMetadata.lastUpdated.after(lastUpdated)) {
			lastUpdated = oldMetadata.lastUpdated;
		}
	}

	public List<String> getVersions() {
		return versions;
	}

	public void setLastUpdated(String date) throws ParseException {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		lastUpdated = df.parse(date);
	}

	public String getManagementId() {
		return groupId + ":" + artifactId;
	}

	@Override
	public String toString() {
		return "maven-metadata.xml (" + getManagementId() + ")";
	}

	public String toXML() {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<metadata>\n");

		// project metadata
		sb.append("\t<!-- project metadata -->\n");
		sb.append(StringUtils.toXML("groupId", groupId));
		sb.append(StringUtils.toXML("artifactId", artifactId));

		// project versioning
		sb.append("\t<!-- project versioning -->\n");
		sb.append("\t<versioning>\n");
		StringBuilder node = new StringBuilder();
		node.append(StringUtils.toXML("latest", latest));
		node.append(StringUtils.toXML("release", release));
		sb.append(StringUtils.insertTab(node.toString()));

		sb.append("\t\t<versions>\n");
		StringBuilder sbv = new StringBuilder();
		for (String version : versions) {
			sbv.append(StringUtils.insertTab(StringUtils.toXML("version", version)));
		}
		if (sbv.length() > 0) {
			sb.append(StringUtils.insertTab(sbv.toString()));
		}
		sb.append("\t\t</versions>\n");

		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		sb.append("\t").append(StringUtils.toXML("lastUpdated", df.format(new Date())));

		sb.append("\t</versioning>\n");

		// close metadata
		sb.append("</metadata>");
		return sb.toString();
	}
}