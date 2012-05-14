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

	public static enum Scope {
		compile, provided, runtime, test, system;
		
		public static final Scope defaultScope = compile;
		
		public boolean isDefault() {
			return compile.equals(this);
		}
		
		// http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope		
		public boolean includeOnClasspath(Scope dependencyScope) {
			if (dependencyScope == null) {
				return false;
			}
			if (compile.equals(dependencyScope)) {
				// compile dependency is on all classpaths
				return true;
			} else if (system.equals(dependencyScope)) {
				// system dependency is on all classpaths
				return true;
			}
			switch(this) {
			case compile:
				// compile classpath
				switch(dependencyScope) {
					case provided:
						return true;
				}
				break;
			case provided:
				// provided classpath
				switch (dependencyScope) {
					case provided:
						return true;
				}
				break;
			case runtime:
				// runtime classpath
				switch (dependencyScope) {
					case runtime:
						return true;
				}
				break;
			case test:
				// test classpath
				return true;
			}
			return false;
		}
		
		// http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope
		public Scope getTransitiveScope(Scope transitiveDependency) {
			// left-column in table
			switch(this) {
			case compile:
				// compile dependency
				switch (transitiveDependency) {
				case compile:
					return compile;
				case runtime:
					return runtime;
				}
				break;
			case provided:
				// provided dependency
				switch (transitiveDependency) {
				case compile:
				case runtime:
					return provided;
				}
				break;
			case runtime:
				// runtime dependency
				switch (transitiveDependency) {
				case compile:
				case runtime:
					return runtime;
				}
				break;
			case test:
				// test dependency
				switch (transitiveDependency) {
				case compile:
				case runtime:
					return test;
				}
				break;
			}
			return null;
		}
		
		public static Scope fromString(String str) {
			for (Scope value : Scope.values()) {
				if (value.name().equalsIgnoreCase(str)) {
					return value;
				}
			}
			return null;
		}
		
		public boolean isValidSourceScope() {
			switch(this) {
			case compile:
			case test:
				return true;
			default:
				return false;
			}
		}
	}
	
	public static String EXT_JAR = ".jar";
	public static String EXT_POM = ".pom";
	public static String EXT_SRC = "-sources";

	public String group;
	public String artifact;
	public String version;
	public String ext;
	public boolean optional;	
	public boolean resolveDependencies;
	public Set<String> exclusions;

	public int ring;

	public Dependency() {
		ext = EXT_JAR;
		resolveDependencies = true;
		exclusions = new TreeSet<String>();
	}
	
	public Dependency(String def) {
		String [] principals = def.trim().split(" ");
		
		String coordinates = StringUtils.stripQuotes(principals[0]);
		if (coordinates.indexOf('@') > -1) {
			// strip @ext
			ext = "." + coordinates.substring(coordinates.indexOf('@') + 1);			
			coordinates = coordinates.substring(0, coordinates.indexOf('@'));
			resolveDependencies = false;
		} else {
			ext = EXT_JAR;
			resolveDependencies = true;
		}
		
		// determine primary Maven coordinates
		String [] fields = coordinates.split(":");
		this.group = fields[0].replace('/', '.');
		this.artifact = fields[1];
		this.version = fields[2];
		
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
		return group.charAt(0) != '<';
	}
	
	public String getProjectId() {
		return group + ":" + artifact;
	}

	public String getCoordinates() {
		return group + ":" + artifact + ":" + version;
	}

	@Override
	public int hashCode() {
		return (group + artifact + (version == null ? "":version)).hashCode();
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
		return (group.replace('/', '.') + ":" + artifact + ":" + version) + " (" + (optional ? ", optional":"") + (resolveDependencies ? "":", @jar") + ")";
	}
	
	public String toXML(Scope scope) {
		StringBuilder sb = new StringBuilder();
		sb.append("<dependency>\n");
		sb.append(StringUtils.toXML("groupId", group));
		sb.append(StringUtils.toXML("artifactId", artifact));
		sb.append(StringUtils.toXML("version", version));
		sb.append(StringUtils.toXML("scope", scope));
		if (optional) {
			sb.append(StringUtils.toXML("optional", true));
		}
		sb.append(StringUtils.toXML("type", ext.substring(1)));
		// TODO type/classifier
		sb.append("</dependency>\n");
		return sb.toString();
	}
}