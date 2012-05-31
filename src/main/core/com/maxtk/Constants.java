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

public class Constants {

	public static final String VERSION = "0.3.0";

	public static final String HDR = "=========================================================";

	public static final String SUB = "---------------------------------------------------------";
	
	public static final String SEP = "---------------------------------------------------------";

	public static final String INDENT = "   ";
	
	public static final String MAVEN2_PATTERN = "${groupId}/${artifactId}/${version}/${artifactId}-${version}${classifier}.${ext}";

	public static final String MAVENCENTRAL = "central";

	public static final String MAVENCENTRAL_URL = "http://repo1.maven.org/maven2";
	
	public static final String GOOGLECODE = "googlecode";
	
	public static final String APPLY_ECLIPSE = "eclipse";
	
	public static final String APPLY_POM = "pom";
	
	public static final String APPLY_COLOR = "color";
	
	public static final String APPLY_DEBUG = "debug";
	
	public static final String APPLY_CACHE = "cache";
	
	public static final String DEFAULT_SRC_EXCLUDES = "**/Thumbs.db, **/.svn, **/CVS, **/.gitignore, **/.hgignore, **/.hgtags";

	public static final String DEFAULT_BIN_EXCLUDES = "**/*.java, " + DEFAULT_SRC_EXCLUDES;
		
	public static final String POM = "pom";
	
	public static enum Key {
		build, name, description, url, vendor, scope, groupId, artifactId, version,
		type, classifier, optional, folder, sourceFolder, sourceFolders, compile_sourcepath,
		test_sourcepath, outputFolder, compile_outputpath, test_outputpath, linkedProjects,
		dependencyFolder, repositories, properties, dependencies, apply,
		googleAnalyticsId, googlePlusId, runtime_classpath, compile_classpath, test_classpath,
		compile_dependencypath, runtime_dependencypath, test_dependencypath, commit, targetFolder,
		proxies, parent, exclusions, mxjar, mxjavac, compilerArgs, excludes, includes,
		dependencyManagement, mxreport, outputFile, quiet, build_classpath, reportsFolder,
		dependencyOverrides;
		
		public String propId() {
			return "mxp." + name().replace('_', '.');
		}
		
		public String refId() {
			return "mxr." + name().replace('_', '.');
		}

	}
}
