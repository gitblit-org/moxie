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

public enum Scope {
	compile, provided, runtime, test, system, imprt, assimilate, site, build;
	
	public static final Scope defaultScope = compile;
	
	public boolean isDefault() {
		return compile.equals(this);
	}
	
	// http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope		
	public boolean includeOnClasspath(Scope dependencyScope) {
		if (dependencyScope == null) {
			return false;
		}
		if (site.equals(this)) {
			return false;
		}
		if (build.equals(this)) {
			// build classpath
			return build.equals(dependencyScope);
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
			switch (dependencyScope) {
			case site:
			case build:
				return false;
			}
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
		case build:
			// build dependency
			switch (transitiveDependency) {
			case compile:
				return build;
			}
			break;
		}
		return null;
	}
	
	public static Scope fromString(String str) {
		if ("import".equalsIgnoreCase(str)) {
			return imprt;
		}
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
		case site:
			return true;
		default:
			return false;
		}
	}
	
	public boolean isMavenScope() {
		return !this.equals(assimilate) && !this.equals(site) && !this.equals(build);
	}
}