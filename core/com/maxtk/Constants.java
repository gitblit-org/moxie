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

	public static final String VERSION = "0.2.0";

	public static final String HDR = "===========================================";

	public static final String SEP = "-------------------------------------------";

	public static final String INDENT = "   ";

	public static final String MAVENCENTRAL = "central";

	public static final String MAVENCENTRAL_URL = "http://repo1.maven.org/maven2";
	
	public static enum Key {
		build, name, description, url, vendor, scope, groupId, artifactId, version,
		type, classifier, optional, folder, sourceFolder, sourceFolders, outputFolder,
		projects, dependencyFolder, dependencySources, properties, dependencies,
		configureEclipseClasspath, googleAnalyticsId, googlePlusId, runtime_classpath,
		compile_classpath, test_classpath, commit;
		
		public String maxId() {
			return "max." + name().replace('_', '.');
		}
	}
}
