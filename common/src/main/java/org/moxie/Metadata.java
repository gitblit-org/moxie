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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TimeZone;

import org.moxie.utils.StringUtils;

public class Metadata {

	public static final String snapshotTimestamp = "yyyyMMdd.HHmmss";
	public static final String versionTimestamp = "yyyyMMddHHmmss";

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
	
	public Metadata(Dependency dep, boolean isSnapshotMetadata) {
		this();
		
		// create metadata from dependency
		groupId = dep.groupId;
		artifactId = dep.artifactId;
		
		if (isSnapshotMetadata && dep.isSnapshot()) {
			// SNAPSHOT metadata
			version = dep.version;
			
			// revision is x.y.z-DATE.TIME-BUILDNUMBER
			String [] values = dep.revision.split("-");
			String timestamp = values[1];
			String buildNumber = values[2];
			addSnapshot(timestamp, buildNumber);
			try {
				lastUpdated = new SimpleDateFormat(snapshotTimestamp).parse(timestamp);
			} catch (ParseException e) {
				lastUpdated = new Date();
			}
		} else {
			// ARTIFACT metadata
			latest = dep.version;
			versions.add(dep.version);
			if (!dep.isSnapshot()) {
				// RELEASE
				release = dep.version;
			}
			lastUpdated = new Date();
		}
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
		
		// merge snapshots
		LinkedHashSet<Snapshot> sset = new LinkedHashSet<Snapshot>();
		sset.addAll(snapshots);
		sset.addAll(oldMetadata.snapshots);
		List<Snapshot> slist = new ArrayList<Snapshot>(sset);
		Collections.sort(slist);		
		snapshots.clear();
		snapshots.addAll(slist);

		if (oldMetadata.lastUpdated.after(lastUpdated)) {
			lastUpdated = oldMetadata.lastUpdated;
		}
	}
	
	public List<String> purgeSnapshots(PurgePolicy policy) {
		List<String> removed = new ArrayList<String>();
		if (snapshots.size() > policy.retentionCount) {
			Collections.sort(snapshots);
			
			// keep last RetentionCount snapshots
			List<Snapshot> kept = new ArrayList<Snapshot>();
			kept.addAll(snapshots.subList(snapshots.size() - policy.retentionCount, snapshots.size()));
			
			if (policy.purgeAfterDays > 0) {
				// determine which of the remaining snapshots should be purged
				// or kept based on purgeAfterDays

				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DATE, -1 * policy.purgeAfterDays);
				cal.set(Calendar.HOUR, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				Date threshold = cal.getTime();

				SimpleDateFormat df = new SimpleDateFormat(snapshotTimestamp);
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				
				List<Snapshot> candidates = snapshots.subList(0, snapshots.size() - policy.retentionCount);				
				for (Snapshot snapshot : candidates) {
					try {
						Date sDate = df.parse(snapshot.timestamp);
						if (sDate.before(threshold)) {
							removed.add(snapshot.getRevision());
						} else {
							kept.add(snapshot);
						}
					} catch (ParseException e) {
					}
				}
			} else {
				// just keep retentionCount revisions
				for (Snapshot snapshot : snapshots.subList(0, snapshots.size() - policy.retentionCount)) {
					removed.add(snapshot.getRevision());
				}
			}
			
			Collections.sort(kept);
			snapshots.clear();
			snapshots.addAll(kept);
		}
		return removed;
	}

	public void setLastUpdated(String date) {
		if (StringUtils.isEmpty(date) || "null".equalsIgnoreCase(date)) {
			return;
		}
		try {
			SimpleDateFormat df = new SimpleDateFormat(versionTimestamp);
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
	
	public int getLastBuildNumber() {
		if (snapshots.isEmpty()) {
			return 0;
		}
		int lastBuildNumber = 0;
		for (Snapshot snapshot : snapshots) {
			if (!StringUtils.isEmpty(snapshot.buildNumber)) {
				try {
					int bn = Integer.parseInt(snapshot.buildNumber);
					if (bn > lastBuildNumber) {
						lastBuildNumber = bn;
					}
				} catch (Throwable t) {
				}
			}
		}
		return lastBuildNumber;
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
		sb.append(StringUtils.insertHalfTab("<!-- project metadata -->\n"));
		sb.append(StringUtils.toXML("groupId", groupId));
		sb.append(StringUtils.toXML("artifactId", artifactId));
		sb.append(StringUtils.toXML("version", version));

		// project versioning
		sb.append(StringUtils.insertHalfTab("<!-- project versioning -->\n"));
		sb.append(StringUtils.insertHalfTab("<versioning>\n"));
		
		if (!StringUtils.isEmpty(latest) ||  !StringUtils.isEmpty(release)) {
			StringBuilder node = new StringBuilder();
			node.append(StringUtils.toXML("latest", latest));
			node.append(StringUtils.toXML("release", release));
			sb.append(StringUtils.insertHalfTab(node.toString()));
		}
		
		// snapshots
		if (snapshots.size() > 0) {
			sb.append(StringUtils.insertSoftTab("<!-- snapshots -->\n"));
			for (Snapshot snapshot : snapshots) {
				sb.append(StringUtils.insertSoftTab("<snapshot>\n"));
				sb.append(StringUtils.insertSoftTab(StringUtils.toXML("timestamp", snapshot.timestamp)));
				sb.append(StringUtils.insertSoftTab(StringUtils.toXML("buildNumber", snapshot.buildNumber)));
				sb.append(StringUtils.insertSoftTab("</snapshot>\n"));
			}
		}

		// versions
		if (versions.size() > 0) {
			sb.append(StringUtils.insertSoftTab("<!-- versions-->\n"));
			sb.append(StringUtils.insertSoftTab("<versions>\n"));
			StringBuilder sbv = new StringBuilder();
			for (String version : versions) {
				sbv.append(StringUtils.insertHalfTab(StringUtils.toXML("version", version)));
			}
			if (sbv.length() > 0) {
				sb.append(StringUtils.insertHalfTab(sbv.toString()));
			}
			sb.append(StringUtils.insertSoftTab("</versions>\n"));
		}
		
		// set lastUpdated to now, if it is unset
		if (lastUpdated.getTime() == 0) {
			lastUpdated = new Date();
		}

		if (snapshots.size() > 0) {
			// lastUpdated is most recent snapshot timestamp
			SimpleDateFormat sf = new SimpleDateFormat(snapshotTimestamp);
			sf.setTimeZone(TimeZone.getTimeZone("UTC"));
			try {
				lastUpdated = sf.parse(snapshots.get(snapshots.size() - 1).timestamp);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		
		// set lastUpdated
		SimpleDateFormat df = new SimpleDateFormat(versionTimestamp);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		sb.append(StringUtils.insertHalfTab(StringUtils.toXML("lastUpdated", df.format(lastUpdated))));
		
		sb.append(StringUtils.insertHalfTab("</versioning>\n"));

		// close metadata
		sb.append("</metadata>");
		return sb.toString();
	}
	
	private class Snapshot implements Comparable<Snapshot> {
		final String timestamp;
		final String buildNumber;
		
		Snapshot(String timestamp, String buildNumber) {
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

		@Override
		public int compareTo(Snapshot o) {
			return getRevision().compareTo(o.getRevision());
		}
		
		@Override
		public String toString() {
			return getRevision();
		}
	}
}