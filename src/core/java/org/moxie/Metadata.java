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
	public String version;
	public Date lastUpdated;

	private final List<String> versions;
	private final List<Snapshot> snapshots;

	public Metadata() {
		lastUpdated = new Date(0);
		versions = new ArrayList<String>();
		snapshots = new ArrayList<Snapshot>();
	}

	public void addVersion(String version) {
		versions.add(version);
	}

	public void addSnapshot(String timestamp, String buildNumber) {
		snapshots.add(new Snapshot(timestamp, buildNumber));
	}

	public void merge(Metadata oldMetadata) {
		// merge versions
		LinkedHashSet<ArtifactVersion> vset = new LinkedHashSet<ArtifactVersion>();
		for (String version : versions) {
			vset.add(new ArtifactVersion(version));
		}
		for (String version : oldMetadata.versions) {
			vset.add(new ArtifactVersion(version));
		}

		// sort them by Maven rules
		List<ArtifactVersion> vlist = new ArrayList<ArtifactVersion>(vset);
		Collections.sort(vlist);

		// convert back to simple strings and determine LATEST and RELEASE
		ArtifactVersion latest = null;
		ArtifactVersion release = null;
		versions.clear();
		for (ArtifactVersion version : vlist) {
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

	public void setLastUpdated(String date) {
		if (StringUtils.isEmpty(date) || "null".equalsIgnoreCase(date)) {
			return;
		}
		try {
			SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
			lastUpdated = df.parse(date);
		} catch (ParseException e) {
			// silently ignore malformed lastUpdate
		}
	}

	public String getManagementId() {
		return groupId + ":" + artifactId;
	}
	
	public String getSnapshotRevision() {
		return snapshots.get(snapshots.size() - 1).getRevision();
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
		sb.append(StringUtils.toXML("version", version));

		// project versioning
		sb.append("\t<!-- project versioning -->\n");
		sb.append("\t<versioning>\n");
		StringBuilder node = new StringBuilder();
		node.append(StringUtils.toXML("latest", latest));
		node.append(StringUtils.toXML("release", release));
		sb.append(StringUtils.insertTab(node.toString()));
		
		// snapshots
		if (snapshots.size() > 0) {
			sb.append("\t\t<!-- snapshots -->\n");
			for (Snapshot snapshot : snapshots) {
				sb.append("\t\t<snapshot>\n");
				sb.append("\t\t").append(StringUtils.toXML("timestamp", snapshot.timestamp));
				sb.append("\t\t").append(StringUtils.toXML("buildNumber", snapshot.buildNumber));
				sb.append("\t\t</snapshot>\n");
			}
		}

		// versions
		if (versions.size() > 0) {
			sb.append("\t\t<!-- versions-->\n");
			sb.append("\t\t<versions>\n");
			StringBuilder sbv = new StringBuilder();
			for (String version : versions) {
				sbv.append(StringUtils.insertTab(StringUtils.toXML("version", version)));
			}
			if (sbv.length() > 0) {
				sb.append(StringUtils.insertTab(sbv.toString()));
			}
			sb.append("\t\t</versions>\n");
		}
		
		// set lastUpdated to now, if it is unset
		if (lastUpdated.getTime() == 0) {
			lastUpdated = new Date();
		}

		if (snapshots.size() > 0) {
			// lastUpdated is most recent snapshot timestamp
			SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd.HHmmss");
			sf.setTimeZone(TimeZone.getTimeZone("UTC"));
			try {
				lastUpdated = sf.parse(snapshots.get(snapshots.size() - 1).timestamp);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		
		// set lastUpdated
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		sb.append("\t").append(StringUtils.toXML("lastUpdated", df.format(lastUpdated)));
		
		sb.append("\t</versioning>\n");

		// close metadata
		sb.append("</metadata>");
		return sb.toString();
	}
	
	class Snapshot {
		final String timestamp;
		final String buildNumber;
		
		public Snapshot(String timestamp, String buildNumber) {
			this.timestamp = timestamp;
			this.buildNumber = buildNumber;
		}
		
		public String getRevision() {
			return version.replace("SNAPSHOT", timestamp + "-" + buildNumber);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof Snapshot) {
				return o.hashCode() == hashCode();
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return 11 + timestamp.hashCode() + buildNumber.hashCode();
		}
	}
}