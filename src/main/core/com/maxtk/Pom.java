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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maxtk.Scope;
import com.maxtk.utils.StringUtils;

public class Pom {

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
	private final Set<String> exclusions;
	
	public Pom() {
		version = "0.0.0-SNAPSHOT";
		managedVersions = new TreeMap<String, String>();
		managedScopes = new TreeMap<String, Scope>();
		properties = new TreeMap<String, String>();
		dependencies = new LinkedHashMap<Scope, List<Dependency>>();
		exclusions = new TreeSet<String>();
	}
	
	public void setProperty(String key, String value) {
		if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
			return;
		}
		properties.put(key.trim(), value);
	}
	
	public String getProperty(String key) {
		key = stripKeyFormat(key.trim());
		String value = get(key, properties);
		if (StringUtils.isEmpty(value)) {
			// Support all Java system properties
			value = System.getProperty(key);
		}
		if (StringUtils.isEmpty(value)) {
			// Support all environment variables
			value = System.getenv().get(key);
		}
		if (StringUtils.isEmpty(value)) {
			return key;
		}
		return value;
	}
	
	private String stripKeyFormat(String key) {
		if (key.startsWith("${")) {
			key = key.substring(2);
		}
		if (key.charAt(key.length() - 1) == '}') {
			key = key.substring(0,  key.length() - 1);
		}
		return key;
	}
	
	private String get(String key, Map<String, String> propertyMap) {
		if (key == null) {
			// can happen on dependency version of child pom which relies on
			// a dependencyManagement definition in a parent pom
			return null;
		}
		if (propertyMap.containsKey(key)) {
			return propertyMap.get(key);
		}
		return null;
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
	
	public void removeScope(Scope scope) {
		dependencies.remove(scope);
	}
	
	public boolean hasDependencies() {
		return dependencies.size() > 0;
	}
	
	public void addDependency(Dependency dep, Scope scope) {
		if ((dep instanceof SystemDependency)) {
			// System Dependency
			SystemDependency sys = (SystemDependency) dep;
			String path = resolveProperties(sys.path);
			dep = new SystemDependency(path);
		} else {
			// Retrievable dependency
			if (!StringUtils.isEmpty(dep.version)) {
				// property substitution
				// managed dependencies are blank
				dep.version = resolveProperties(dep.version);
			}
			dep.group = resolveProperties(dep.group);
		}
		
		if (!dependencies.containsKey(scope)) {
			dependencies.put(scope, new ArrayList<Dependency>());
		}
		
		dependencies.get(scope).add(dep);
	}
	
	private String resolveProperties(String string) {
		Pattern p = Pattern.compile("\\$\\{[a-zA_Z0-9-\\.]+\\}");			
		StringBuffer sb = new StringBuffer(string);
		while (true) {
			Matcher m = p.matcher(sb.toString());
			if (m.find()) {
				String prop = m.group();
				String value = getProperty(prop);
				sb.replace(m.start(), m.end(), value);
			} else {
				return sb.toString();
			}
		}
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
		return new Dependency(parentGroupId + ":" + parentArtifactId + ":" + parentVersion);
	}
	
	public boolean hasDependency(Dependency dependency) {
		String id = dependency.getMediationId();
		for (Map.Entry<Scope, List<Dependency>> entry : dependencies.entrySet()) {
			for (Dependency dep : entry.getValue()) {
				if (dep.getMediationId().equals(id)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Maven POMs do not have a notion of a pom-level exclusion list. In Maven,
	 * exclusions must be set within the dependency declaration.  Because Maxilla
	 * supports direct dependency importing, Maxilla also supports pom-level
	 * exclusion.  This method only makes sense for Maxilla POMs.
	 * 
	 * @param dependency
	 * @return true of the dependency is excluded
	 */
	public boolean excludesDependency(Dependency dependency) {
		return exclusions.contains(dependency.getMediationId()) 
				|| exclusions.contains(dependency.getProjectId())
				|| exclusions.contains(dependency.group);
	}

	/**
	 * Maven POMs do not have a notion of a pom-level exclusion list. In Maven,
	 * exclusions must be set within the dependency declaration.  Because Maxilla
	 * supports direct dependency importing, Maxilla also supports pom-level
	 * exclusion.  This method only makes sense for Maxilla POMs.
	 * 
	 * @param exclusions
	 */
	public void addExclusions(Collection<String> exclusions) {
		exclusions.addAll(exclusions);
	}

	public void inherit(Pom pom) {
		nonDestructiveCopy(pom.managedVersions, managedVersions);
		nonDestructiveCopy(pom.managedScopes, managedScopes);
		nonDestructiveCopy(pom.properties, properties);
	}
	
	public void importManagedDependencies(Pom pom) {
		nonDestructiveCopy(pom.managedVersions, managedVersions);
		nonDestructiveCopy(pom.managedScopes, managedScopes);		
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
	
	public String toXML() {
		String pomVersion = "4.0.0";
		StringBuilder sb = new StringBuilder();
		sb.append(MessageFormat.format("<project xmlns=\"http://maven.apache.org/POM/{0}\" ", pomVersion));
		sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
		sb.append(MessageFormat.format("xsi:schemaLocation=\"http://maven.apache.org/POM/{0} ", pomVersion));
		sb.append(MessageFormat.format("http://maven.apache.org/maven-v{0}.xsd\">\n", pomVersion.replace('.', '_')));
		
		sb.append('\n');
		sb.append(StringUtils.toXML("modelVersion", pomVersion));
		sb.append('\n');
		
		// parent metadata
		if (hasParentDependency()) {
			StringBuilder node = new StringBuilder();
			node.append("<parent>\n");
			node.append(StringUtils.toXML("groupId", parentGroupId));
			node.append(StringUtils.toXML("artifactId", parentArtifactId));
			node.append(StringUtils.toXML("version", parentVersion));
			node.append("</parent>\n");
			sb.append(StringUtils.insertTab(node.toString()));
			sb.append('\n');
		}

		// project metadata
		sb.append("\t<!-- project metadata-->\n");
		sb.append(StringUtils.toXML("groupId", groupId));
		sb.append(StringUtils.toXML("artifactId", artifactId));
		sb.append(StringUtils.toXML("version", version));
		sb.append(StringUtils.toXML("name", name));
		sb.append(StringUtils.toXML("description", description));
		sb.append(StringUtils.toXML("vendor", vendor));
		sb.append(StringUtils.toXML("url", url));
		sb.append('\n');
		
		// properties
		if (properties.size() > 0) {
			Map<String, String> filtered = new LinkedHashMap<String, String>();
			for (Map.Entry<String, String> entry : properties.entrySet()) {
				String key = entry.getKey();
				// strip curly brace notation
				if (key.startsWith("${")) {
					key = key.substring(2);
				}
				if (key.endsWith("}")) {
					key = key.substring(0, key.length() - 1);
				}
				// skip project.* keys
				if (!key.toLowerCase().startsWith("project.")) {
					filtered.put(key, entry.getValue());
				}
			}
			
			// only output filtered properties
			if (filtered.size() > 0) {
				StringBuilder node = new StringBuilder();
				node.append("<properties>\n");
				for (Map.Entry<String, String> entry : filtered.entrySet()) {
					node.append(StringUtils.toXML(entry.getKey(), entry.getValue()));
				}
				node.append("</properties>\n");
				sb.append(StringUtils.insertTab(node.toString()));
				sb.append('\n');
			}
		}

		// managed versions
		if (managedVersions.size() > 0) {
			StringBuilder node = new StringBuilder();
			node.append("<dependencyManagement>\n");
			node.append("<dependencies>\n");
			for (Map.Entry<String, String> entry : managedVersions.entrySet()) {
				String key = entry.getKey();
				String version = entry.getValue();
				Scope scope = managedScopes.get(key);
				Dependency dep = new Dependency(key + ":" + version);
				node.append(dep.toXML(scope));
			}
			node.append("</dependencies>\n");
			node.append("</dependencyManagement>\n");
			sb.append(StringUtils.insertTab(node.toString()));
			sb.append('\n');
		}
		
		// dependencies
		if (dependencies.size() > 0) {
			StringBuilder node = new StringBuilder();
			node.append("<dependencies>\n");
			for (Map.Entry<Scope, List<Dependency>> entry : dependencies.entrySet()) {
				node.append(MessageFormat.format("\t<!-- {0} dependencies -->\n", entry.getKey().name()));
				for (Dependency dependency : entry.getValue()) {
					StringBuilder depNode = new StringBuilder();
					depNode.append(dependency.toXML(entry.getKey()));
					node.append(StringUtils.insertTab(depNode.toString()));
				}
			}
			node.append("</dependencies>\n");
			sb.append(StringUtils.insertTab(node.toString()));
			sb.append('\n');
		}
		
		// close project
		sb.append("</project>");
		return sb.toString();
	}
}