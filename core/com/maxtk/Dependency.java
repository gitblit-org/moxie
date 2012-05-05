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

import com.maxtk.utils.StringUtils;

/**
 * Dependency represents a retrievable artifact.
 */
public class Dependency implements Serializable {

	private static final long serialVersionUID = 1L;

	public static enum Scope {
		compile, provided, runtime, test, system;
		
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
			return Scope.compile;
		}
	}
	
	public static enum Extension {
		POM(".pom"), POM_SHA1(".pom.sha1"), LIB(".jar"), LIB_SHA1(".jar.sha1"), SRC("-sources.jar"), SRC_SHA1("-sources.jar.sha1");
		
		final String ext;
		
		Extension(String ext) {
			this.ext = ext;
		}
		
		public Extension sha1() {
			if (toString().endsWith(".sha1")) {
				return this;
			}
			String wanted = toString() + ".sha1";
			for (Extension ext : Extension.values()) {
				if (ext.toString().equals(wanted)) {
					return ext;
				}
			}
			return null;
		}
		
		@Override
		public String toString() {
			return ext;
		}
	}

	public String group;
	public String artifact;
	public String version;
	public boolean optional;	
	public boolean resolveDependencies;

	public int ring;

	public Dependency() {
		resolveDependencies = true;
	}
	
	public Dependency(String def) {
		this(def.split(":"));
	}
	
	private Dependency(String [] def) {
		this(def[0], def[1], def[2]);
	}
	
	public Dependency(String group, String artifact, String version) {
		this.group = group.replace('/', '.');
		this.artifact = artifact;
		this.version = stripFetch(version);
		this.resolveDependencies = true;
		if (!StringUtils.isEmpty(version) && (version.indexOf('@') > -1)) {
			// trailing @ on the version disables transitive resolution
			this.resolveDependencies = false;
		}
	}
	
	private String stripFetch(String def) {
		if (!StringUtils.isEmpty(def)) {			
			if (def.indexOf('@') > -1) {
				return def.substring(0, def.indexOf('@'));
			}
			return def;
		} else {
			return "";
		}
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
}