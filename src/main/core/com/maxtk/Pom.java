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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.maxtk.Dependency.Scope;
import com.maxtk.utils.StringUtils;

public class Pom {
	
	public static void main(String [] args) {
		MaxillaCache cache = new MaxillaCache();
		Dependency dep = new Dependency("org.eclipse.jgit:org.eclipse.jgit:1.3.0.201202151440-r");
		Pom pom = PomReader.readPom(cache, dep);
		System.out.println(pom);
		for (Dependency d : pom.getDependencies(Scope.runtime)) {
			System.out.println(d);
		}
		
	}
	
	public String name;
	public String description;
	public String url;
	public String vendor;
	
	public String groupId;
	public String artifactId;
	public String version;
		
	public String parentGroupId;
	public String parentArtifactId;
	public String parentVersion;

	private final Map<String, String> properties;
	private final Map<Scope, List<Dependency>> dependencies;
	private final Map<String, String> managedVersions;
	private final Map<String, Scope> managedScopes;
	
	public Pom() {
		managedVersions = new TreeMap<String, String>();
		managedScopes = new TreeMap<String, Scope>();
		properties = new TreeMap<String, String>();
		dependencies = new LinkedHashMap<Scope, List<Dependency>>();
		
		// Support all Java system properties (case-insensitive)
		for (String property : System.getProperties().stringPropertyNames()) {
			setProperty(property, System.getProperty(property));
		}
		// Support all environment variables (case-insensitive)
		for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
			setProperty(entry.getKey(), entry.getValue());
		}
	}
	
	public void setProperty(String key, String value) {
		if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
			return;
		}
		properties.put("${" + key.trim().toUpperCase() + "}", value.trim());
	}
	
	public String getProperty(String key) {
		return get(key, properties);
	}
	
	private String get(String key, Map<String, String> propertyMap) {
		if (key == null) {
			// can happen on dependency version of child pom which relies on
			// a dependencyManagement definition in a parent pom
			return null;
		}
		if (propertyMap.containsKey(key.toUpperCase())) {
			return propertyMap.get(key.toUpperCase());
		}
		return key;
	}
	
	public void addManagedDependency(Dependency dep, Scope scope) {
		dep.version = getProperty(dep.version);		
		managedVersions.put(dep.getProjectId().toUpperCase(), dep.version);
		if (scope != null) {
			managedScopes.put(dep.getProjectId().toUpperCase(), scope);
		}
	}
	
	public String getManagedVersion(Dependency dep) {
		if (managedVersions.containsKey(dep.getProjectId().toUpperCase())) {
			return managedVersions.get(dep.getProjectId().toUpperCase());
		}
		return dep.version;
	}
	
	public Scope getManagedScope(Dependency dep) {
		if (managedScopes.containsKey(dep.getProjectId().toUpperCase())) {
			return managedScopes.get(dep.getProjectId().toUpperCase());
		}
		return null;
	}
	
	public List<Scope> getScopes() {
		return new ArrayList<Scope>(dependencies.keySet());
	}
	
	public boolean hasDependencies() {
		return dependencies.size() > 0;
	}
	
	public void addDependency(Dependency dep, Scope scope) {
		if (!StringUtils.isEmpty(dep.version)) {
			// property substitution
			// managed dependencies are blank
			dep.version = getProperty(dep.version);
		}
		dep.group = getProperty(dep.group);
		
		if (!dependencies.containsKey(scope)) {
			dependencies.put(scope, new ArrayList<Dependency>());
		}		
		dependencies.get(scope).add(dep);
	}
	
	public List<Dependency> getDependencies(Scope scope) {
		return getDependencies(scope, 0);
	}
	
	public List<Dependency> getDependencies(Scope scope, int ring) {
		Set<Dependency> set = new LinkedHashSet<Dependency>();		
		for (Scope dependencyScope : dependencies.keySet()) {
			boolean includeScope = false;
			if (ring == 0) {
				// project-specified dependency
				includeScope = scope.includeOnClasspath(dependencyScope);
			} else if (ring > 0) {
				// transitive dependencies
				Scope transitiveScope = scope.getTransitiveScope(dependencyScope);
				includeScope = scope.includeOnClasspath(transitiveScope);
			}
			
			if (includeScope) {
				List<Dependency> list = dependencies.get(dependencyScope);
				for (Dependency dependency : list) {
					if (ring > 0 && dependency.optional) {
						// skip optional transitive dependencies
						continue;
					}
					dependency.ring = ring;
					set.add(dependency);
				}
			}
		}
		return new ArrayList<Dependency>(set);
	}
	
	public boolean hasParentDependency() {
		return !StringUtils.isEmpty(parentArtifactId);
	}
	
	public Dependency getParentDependency() {
		return new Dependency(parentGroupId, parentArtifactId, parentVersion);
	}
	
	public void inherit(Pom pom) {
		nonDestructiveCopy(pom.managedVersions, managedVersions);
		nonDestructiveCopy(pom.managedScopes, managedScopes);
		nonDestructiveCopy(pom.properties, properties);
	}
	
	/**
	 * Copies values from sourceMap into destinationMap without overriding keys
	 * already in destinationMap.
	 * 
	 * @param sourceMap
	 * @param destinationMap
	 */
	private <K> void nonDestructiveCopy(Map<String, K> sourceMap, Map<String, K> destinationMap) {
		Set<String> sourceKeys = new HashSet<String>(sourceMap.keySet());
		sourceKeys.removeAll(destinationMap.keySet());
		for (String key : sourceKeys) {
			destinationMap.put(key, sourceMap.get(key));
		}
	}
	
	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version;
	}
}