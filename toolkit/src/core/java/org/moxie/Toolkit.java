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

import java.io.File;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.moxie.utils.StringUtils;

public class Toolkit {
	
	public static final String DEPENDENCY_FILENAME_PATTERN = "[artifactId]-[version](-[classifier]).[ext]";
	
	public static final String APPLY_ECLIPSE = "eclipse";
	
    public static final String APPLY_INTELLIJ = "intellij";

    public static final String APPLY_POM = "pom";
	
	public static final String APPLY_CACHE = "cache";
	
	public static final String DEFAULT_EXCLUDES = "**/*.java, **/package.html, **/Thumbs.db, **/.svn, **/CVS, **/.gitignore, **/.hgignore, **/.hgtags";
	
	public static final String MX_DEBUG = "mx.debug";

	public static final String MX_VERBOSE = "mx.verbose";
	
	public static final String MX_COLOR = "mx.color";
	
	public static final String MX_ONLINE = "mx.online";
	
	public static final String MX_UPDATEMETADATA = "mx.updateMetadata";
	
	public static final String MX_ROOT = "mx.root";
	
	public static final String MX_ENFORCECHECKSUMS = "mx.enforceChecksums";
	
	public static final String MOXIE_SETTINGS = "settings.moxie";
	
	public static final String MOXIE_DEFAULTS = "defaults.moxie";
	
	public static final String MOXIE_ROOT = "MX_ROOT";
	
	public static final int MAX_REVISIONS = 100;
	
	public static final int MAX_PURGE_AFTER_DAYS = 1000;
	
	public static enum Key {
		build, name, description, url, organization, scope, groupId, artifactId, version,
		type, classifier, optional, dir, sourceDirectory, sourceDirectories, compileSourcePath,
		testSourcePath, outputDirectory, compileOutputDirectory, testOutputDirectory, linkedProjects,
		dependencyDirectory, repositories, properties, dependencies, apply,
		googleAnalyticsId, googlePlusId, runtimeClasspath, compileClasspath, testClasspath,
		compileDependencypath, runtimeDependencypath, testDependencypath, commitId, targetDirectory,
		proxies, parent, exclusions, mxjar, mxjavac, compilerArgs, excludes, includes,
		dependencyManagement, mxreport, outputFile, verbose, buildClasspath, reportTargetDirectory,
		dependencyOverrides, dependencyAliases, updatePolicy, lastChecked, lastUpdated, lastSolved,
		lastDownloaded, origin, release, latest, revision, packaging, registeredRepositories,
		revisionRetentionCount, revisionPurgeAfterDays, inceptionYear, organizationUrl, developers,
		contributors, id, email, roles, scm, connection, developerConnection, tag, requires, licenses,
		parentPom, mainclass, modules, mavenCacheStrategy, coordinates, releaseVersion, releaseDate,
		buildDate, buildTimestamp, issuesUrl, forumUrl, socialNetworkUrl, blogUrl, scmUrl, ciUrl,
		siteSourceDirectory, siteTargetDirectory, failFastOnArtifactResolution, mavenUrl, 
		resourceDirectories, parallelDownloads, dependencyNamePattern, javadocTargetDirectory,
		connectTimeout, readTimeout, username, password;

		public String projectId() {
			return "project." + name().replace('_', '.');
		}

		public String referenceId() {
			return "reference." + name().replace('_', '.');
		}

	}
	
	public static File getMxRoot() {
		File root = new File(System.getProperty("user.home"), ".moxie");
		if (System.getProperty(Toolkit.MX_ROOT) != null) {
			String value = System.getProperty(Toolkit.MX_ROOT);
			if (!StringUtils.isEmpty(value)) {
				root = new File(value);
			}
		}
		return root;
	}
	
	public static String getVersion() {
		String v = Toolkit.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "0.0.0-SNAPSHOT";
		}
		return v;
	}
	
	public static String getBuildDate() {
		return getManifestValue("build-date", "PENDING");
	}

	public static String getMavenUrl() {
		return getManifestValue("Maven-Url", "");
	}

	public static String getCommitId() {
		return getManifestValue("Commit-Id", "");
	}

	private static String getManifestValue(String attrib, String defaultValue) {
		Class<?> clazz = Constants.class;
		String className = clazz.getSimpleName() + ".class";
		String classPath = clazz.getResource(className).toString();
		if (!classPath.startsWith("jar")) {
			// Class not from JAR
			return defaultValue;
		}
		try {
			String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
			Manifest manifest = new Manifest(new URL(manifestPath).openStream());
			Attributes attr = manifest.getMainAttributes();
			String value = attr.getValue(attrib);
			return value;
		} catch (Exception e) {
		}
		return defaultValue;
	}
}
