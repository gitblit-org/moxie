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

public class Constants {
	
	public static final String MAVEN2_PATTERN = "${groupId}/${artifactId}/${version}/${artifactId}-${revision}${classifier}.${ext}";

	public static final String MAVEN2_METADATA_PATTERN = "${groupId}/${artifactId}/maven-metadata.${ext}";
	
	public static final String MAVEN2_SNAPSHOT_PATTERN = "${groupId}/${artifactId}/${version}/maven-metadata.${ext}";
	
	public static final String RELEASE = "RELEASE";
	
	public static final String LATEST = "LATEST";
	
	public static final String POM = "pom";
	
	public static final String XML = "xml";
	
	public static final String LOCAL = "local";
	
	public static final String REMOTE = "remote";
	
	public static final int RING1 = 1;
	
	public static enum Key {
		name, description, url, organization, scope, groupId, artifactId, version,
		type, classifier, optional, dependencies, lastChecked, lastUpdated, lastSolved,
		lastDownloaded, origin, release, latest, revision, packaging, solutionVersion,
		id, email, organizationUrl, connection, developerConnection, tag;
	}
}
