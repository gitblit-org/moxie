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

import java.lang.reflect.Field;
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

import org.moxie.utils.StringUtils;


public class Pom {

	public String name;
	public String description;
	public String url;
	public String issuesUrl;
	public String organization;
	public String organizationUrl;
	public String inceptionYear;
	
	public String groupId;
	public String artifactId;
	public String version;
	public String classifier;
	public String packaging;
		
	public String parentGroupId;
	public String parentArtifactId;
	public String parentVersion;
	
	public SCM scm;

	private final Map<String, String> properties;
	private final Map<Scope, List<Dependency>> dependencies;
	private final Map<String, String> managedVersions;
	private final Map<String, Scope> managedScopes;
	private final Set<String> exclusions;	
	private final Map<String, String> antProperties;
	private final List<License> licenses;
	private final List<Person> developers;
	private final List<Person> contributors;
	
	public Pom() {
		version = "";
		managedVersions = new TreeMap<String, String>();
		managedScopes = new TreeMap<String, Scope>();
		properties = new TreeMap<String, String>();
		dependencies = new LinkedHashMap<Scope, List<Dependency>>();
		exclusions = new TreeSet<String>();
		antProperties = new TreeMap<String, String>();
		licenses = new ArrayList<License>();
		developers = new ArrayList<Person>();
		contributors = new ArrayList<Person>();
		scm = new SCM();
		packaging = "jar";
	}
	
	public void setAntProperties(Map<String, String> antProperties) {
		this.antProperties.putAll(antProperties);
	}
	
	public void setProperty(String key, String value) {
		if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
			return;
		}
		properties.put(key.trim(), value);
	}
	
	public String getAntProperty(String key) {
		if (antProperties.containsKey(key)) {
			return antProperties.get(key);
		}
		return null;
	}
	
	public Map<String, String> getProperties() {
		return properties;
	}
	
	private String getProperty(String key) {
		String value = null;
		if (properties.containsKey(key)) {
			value = properties.get(key);
		}
		if (StringUtils.isEmpty(value)) {
			if (key.startsWith("project.")) {
				// try reflection on project fields 
				String fieldName = key.substring(key.indexOf('.') + 1);
				value = getFieldValue(fieldName);
			} else if (key.startsWith("parent.")) {
				// try reflection on project fields 
				String fieldName = key.substring(key.indexOf('.') + 1);
				value = getFieldValue("parent" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
			} else if (key.startsWith("env.")) {
				// Support all environment variables
				String env = key.substring(4);
				value = System.getenv().get(env);
			}
		}
		if (StringUtils.isEmpty(value)) {
			// Support all Ant properties
			if (antProperties.containsKey(key)) {
				value = antProperties.get(key);
			}
		}
		if (StringUtils.isEmpty(value)) {
			// Support all Java system properties
			value = System.getProperty(key);
		}
		if (StringUtils.isEmpty(value)) {
			System.out.println(MessageFormat.format("WARNING: property \"{0}\" not found for {1}", key, getCoordinates()));
			return key;
		}
		return value;
	}
	
	private String getFieldValue(String fieldName) {
		try {
			Field field = getClass().getField(fieldName);
			if (field == null) {
				return null;
			}
			field.setAccessible(true);
			Object o = field.get(this);
			if (o != null) {
				return o.toString();
			}
		} catch (Exception e) {					
		}
		return null;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getUrl() {
		return url;
	}
	
	public String getIssuesUrl() {
		return issuesUrl;
	}
	
	public SCM getScm() {
		return scm;
	}

	public String getOrganization() {
		return organization;
	}
	
	public void addLicense(License license) {
		licenses.add(license);
	}
	
	public List<License> getLicenses() {
		return licenses;
	}
	
	public void clearLicenses() {
		licenses.clear();
	}
	
	public void addDeveloper(Person person) {
		developers.add(person);
	}
	
	public List<Person> getDevelopers() {
		return developers;
	}

	public void addContributor(Person person) {
		contributors.add(person);
	}
	
	public List<Person> getContributors() {
		return contributors;
	}

	public void addManagedDependency(Dependency dep, Scope scope) {
		dep.groupId = resolveProperties(dep.groupId);
		dep.version = resolveProperties(dep.version);

		if (dep.getManagementId().equals(getManagementId())) {
			System.out.println(MessageFormat.format("WARNING: ignoring circular managedDependency {0}", dep.getManagementId()));
			return;
		}

		if (!StringUtils.isEmpty(dep.type)) {
			dep.type = "jar";
		}
		
		managedVersions.put(dep.getManagementId(), dep.version);
		if (scope != null) {
			managedScopes.put(dep.getManagementId(), scope);
		}
	}
	
	String getManagedVersion(Dependency dep) {
		if (managedVersions.containsKey(dep.getManagementId())) {
			return managedVersions.get(dep.getManagementId());
		}
		return dep.version;
	}
	
	private Scope getManagedScope(Dependency dep) {
		if (managedScopes.containsKey(dep.getManagementId())) {
			return managedScopes.get(dep.getManagementId());
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
	
	public boolean addDependency(Dependency dep, Scope scope) {
		if (dep.isMavenObject()) {
			// determine group
			dep.groupId = resolveProperties(dep.groupId);
			
			// determine version
			if (StringUtils.isEmpty(dep.version)) {
				dep.version = getManagedVersion(dep);
			}			
			dep.version = resolveProperties(dep.version);

			// set default extension, if unspecified
			if (StringUtils.isEmpty(dep.type)) {
				dep.type = "jar";
			}
			
			if (dep.getManagementId().equals(getManagementId())) {
				System.out.println(MessageFormat.format("WARNING: ignoring circular dependency {0}", dep.getManagementId()));
				return false;
			}
		} else if ((dep instanceof SystemDependency)) {
			// System Dependency
			SystemDependency sys = (SystemDependency) dep;
			String path = resolveProperties(sys.path);
			dep = new SystemDependency(path);
		}
		
		// POM-level dependency exclusion is a Moxie feature
		if (hasDependency(dep) || excludes(dep)) {
			return false;
		}
		
		if (scope == null) {
			scope = getManagedScope(dep);
			// use default scope if completely unspecified
			if (scope == null) {
				scope = Scope.defaultScope;
			}
		}
		
		if (!dependencies.containsKey(scope)) {
			dependencies.put(scope, new ArrayList<Dependency>());
		}
		
		dependencies.get(scope).add(dep);
		return true;
	}
	
	void resolveProperties() {
		name = resolveProperties(name);
		description = resolveProperties(description);
		organization = resolveProperties(organization);		
		url = resolveProperties(url);
		issuesUrl = resolveProperties(issuesUrl);
	}
	
	String resolveProperties(String string) {
		if (string == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9-_\\.]+\\}");			
		StringBuilder sb = new StringBuilder(string);
		while (true) {
			Matcher m = p.matcher(sb.toString());
			if (m.find()) {
				String prop = m.group();
				prop = prop.substring(2, prop.length() - 1);
				String value = getProperty(prop);
				sb.replace(m.start(), m.end(), value);
			} else {
				return sb.toString();
			}
		}		
	}
	
	public List<Dependency> getDependencies(boolean ignoreDuplicates) {
		if (ignoreDuplicates) {
			// We just care about the unique dependencies
			Set<Dependency> uniques = new LinkedHashSet<Dependency>();
			for (Scope dependencyScope : dependencies.keySet()) {
				uniques.addAll(getDependencies(dependencyScope));
			}
			return new ArrayList<Dependency>(uniques);
		} else {
			// We care about all dependency objects, e.g. for alias resolution
			List<Dependency> all = new ArrayList<Dependency>();
			for (Scope dependencyScope : dependencies.keySet()) {
				all.addAll(getDependencies(dependencyScope));
			}
			return all;
		}
	}
	
	public List<Dependency> getDependencies(Scope scope) {
		return getDependencies(scope, Constants.RING1);
	}
	
	public List<Dependency> getDependencies(Scope scope, int ring) {
		Set<Dependency> set = new LinkedHashSet<Dependency>();		
		for (Scope dependencyScope : dependencies.keySet()) {
			boolean includeScope = false;
			if (ring == Constants.RING1) {
				// project-specified dependency
				includeScope = scope.includeOnClasspath(dependencyScope);
			} else if (ring > Constants.RING1) {
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
		return new Dependency(parentGroupId + ":" + parentArtifactId + ":" + parentVersion + "::" + Constants.POM);
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
	 * exclusions must be set within the dependency declaration.  Because Moxie
	 * supports direct dependency importing, Moxie also supports pom-level
	 * exclusion.  This method only makes sense for Moxie POMs.
	 * 
	 * @param dependency
	 * @return true of the dependency is excluded
	 */
	public boolean excludes(Dependency dependency) {
		return exclusions.contains(dependency.getMediationId()) 
				|| exclusions.contains(dependency.getManagementId())
				|| exclusions.contains(dependency.groupId);
	}

	/**
	 * Maven POMs do not have a notion of a pom-level exclusion list. In Maven,
	 * exclusions must be set within the dependency declaration.  Because Moxie
	 * supports direct dependency importing, Moxie also supports pom-level
	 * exclusion.  This method only makes sense for Moxie POMs.
	 * 
	 * @param exclusions
	 */
	public void addExclusions(Collection<String> exclusions) {
		exclusions.addAll(exclusions);
	}
	
	public boolean isPOM() {
		return !StringUtils.isEmpty(packaging) && packaging.equalsIgnoreCase(Constants.POM);
	}

	public boolean isJAR() {
		return StringUtils.isEmpty(packaging) || packaging.equalsIgnoreCase("jar");
	}

	public boolean isWAR() {
		return !StringUtils.isEmpty(packaging) && packaging.equalsIgnoreCase("war");
	}

	public void inherit(Pom pom) {
		nonDestructiveCopy(pom.managedVersions, managedVersions);
		nonDestructiveCopy(pom.managedScopes, managedScopes);
		nonDestructiveCopy(pom.properties, properties);
		
		// inherit groupId and version from parent, by default
		// if parent definition is at end of pom then we already
		// have this data so ignore
		if (StringUtils.isEmpty(groupId)) {
			groupId = pom.groupId;
		}
		if (StringUtils.isEmpty(version)) {
			version = pom.version;
		}
		if (StringUtils.isEmpty(name)) {
			name = pom.name;
		}
		if (StringUtils.isEmpty(pom.description)) {
			description = pom.description;
		}

		if (pom.licenses != null) {
			licenses.addAll(pom.licenses);
		}
		if (pom.developers!= null) {
			developers.addAll(pom.developers);
		}
		if (pom.contributors != null) {
			contributors.addAll(pom.contributors);
		}
		if (StringUtils.isEmpty(organization)) {
			organization = pom.organization;
		}
		if (StringUtils.isEmpty(url)) {
			url = pom.url;
		}
		if (StringUtils.isEmpty(issuesUrl)) {
			issuesUrl = pom.issuesUrl;
		}
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

	public String getManagementId() {
		return groupId + ":" + artifactId;
	}

	public String getCoordinates() {
		return groupId + ":" + artifactId + ":" + version + (classifier == null ? "" : (":" + classifier));
	}
	
	@Override
	public String toString() {
		return getCoordinates();
	}
	
	public String toXML() {
		return toXML(true, true);
	}
	
	public String toXML(boolean includeParent, boolean includeProperties) {
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
		if (includeParent && hasParentDependency()) {
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
		String org = StringUtils.toXML("name", organization).trim() + StringUtils.toXML("url", organizationUrl).trim();
		sb.append(StringUtils.toXML("organization", org));
		sb.append(StringUtils.toXML("url", url));
		sb.append(StringUtils.toXML("inceptionYear", inceptionYear));
		sb.append('\n');
		
		// licenses
		if (licenses.size() > 0) {
			StringBuilder node = new StringBuilder();
			node.append("<licenses>\n");
			for (License license : licenses) {
				node.append(StringUtils.insertTab(license.toXML()));
			}
			node.append("</licenses>\n");
			sb.append(StringUtils.insertTab(node.toString()));
			sb.append('\n');
		}

		// scm
		if (!scm.isEmpty()) {
			sb.append("\t<!-- project scm metadata-->\n");
			sb.append(StringUtils.insertTab(scm.toXML()));
			sb.append('\n');
		}
		
		// persons
		if (developers.size() > 0) {
			sb.append(StringUtils.insertTab(toXML("developer", developers)));
			sb.append('\n');
		}
		if (contributors.size() > 0) {
			sb.append(StringUtils.insertTab(toXML("contributor", contributors)));
			sb.append('\n');
		}

		// properties
		if (includeProperties && properties.size() > 0) {
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
			StringBuilder subnode = new StringBuilder();
			for (Map.Entry<String, String> entry : managedVersions.entrySet()) {
				String key = entry.getKey();
				String version = entry.getValue();
				Scope scope = managedScopes.get(key);
				Dependency dep = new Dependency(key + ":" + version);
				subnode.append(dep.toXML(scope));
			}
			node.append(StringUtils.insertTab(subnode.toString()));
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
				Scope scope = entry.getKey();
				if (!scope.isMavenScope()) {
					// skip non-Maven scopes
					continue;
				}
				node.append(MessageFormat.format("\t<!-- {0} dependencies -->\n", scope.name()));
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
	
	private String toXML(String nodename, List<Person> persons) {
		StringBuilder list = new StringBuilder();
		if (persons.size() > 0) {
			list.append(MessageFormat.format("<{0}s>\n", nodename));
			for (Person person : persons) {
				list.append(StringUtils.insertTab(person.toXML(nodename)));
			}
			list.append(MessageFormat.format("</{0}s>\n", nodename));
		}
		return list.toString();
	}
}