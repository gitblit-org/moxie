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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.moxie.Constants.Key;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


/**
 * Reads the build.maxml file 
 */
public class Config implements Serializable {

	private static final long serialVersionUID = 1L;

	File file;
	File baseFolder;
	Pom pom;
	long lastModified;
	
	List<Proxy> proxies;
	List<LinkedProject> linkedProjects;
	List<String> repositoryUrls;	
	
	File dependencyFolder;
	List<SourceFolder> sourceFolders;
	File outputFolder;
	File targetFolder;
	Set<String> apply;
	Map<String, Dependency> dependencyAliases;
	Map<Scope, Map<String, Pom>> dependencyOverrides;
	MaxmlMap mxjavac;
	MaxmlMap mxjar;
	MaxmlMap mxreport;
	Map<String, String> externalProperties;
	UpdatePolicy updatePolicy;

	public Config() {
		// default configuration
		sourceFolders = Arrays.asList(
				new SourceFolder("src/main/java", Scope.compile),
				new SourceFolder("src/main/webapp", Scope.compile),
				new SourceFolder("src/main/resources", Scope.compile),
				new SourceFolder("src/test/java", Scope.test),
				new SourceFolder("src/test/resources", Scope.test));
		outputFolder = new File("build");
		targetFolder = new File("target");
		linkedProjects = new ArrayList<LinkedProject>();
		repositoryUrls = new ArrayList<String>();		
		pom = new Pom();
		dependencyFolder = null;
		apply = new TreeSet<String>();
		proxies = new ArrayList<Proxy>();
		dependencyAliases = new HashMap<String, Dependency>();
		dependencyOverrides = new HashMap<Scope, Map<String, Pom>>();
		mxjavac = new MaxmlMap();
		mxjar = new MaxmlMap();
		mxreport = new MaxmlMap();
		externalProperties = new HashMap<String, String>();
		updatePolicy = UpdatePolicy.defaultPolicy;
	}
	
	public Config(File file, File baseFolder, String defaultResource) throws IOException, MaxmlException {
		this();
		parse(file, baseFolder, defaultResource);
	}
	
	private Config(String resource) throws IOException, MaxmlException {
		this();
		InputStream is = getClass().getResourceAsStream(resource);
		InputStreamReader reader = new InputStreamReader(is, "UTF-8");
		StringBuilder sb = new StringBuilder();
		char [] chars = new char[4096];
		int len = 0;
		while ((len = reader.read(chars)) >= 0) {
			sb.append(chars, 0, len);
		}
		parse(sb.toString(), null);
	}
	
	public Pom getPom() {
		return pom;
	}

	@Override
	public String toString() {
		return "Config (" + pom + ")";
	}

	Config parse(File file, File baseFolder, String defaultResource) throws IOException, MaxmlException {
		String content = "";
		if (file != null && file.exists()) {
			this.file = file;
			if (baseFolder == null) {
				this.baseFolder = file.getAbsoluteFile().getParentFile();
			} else {
				this.baseFolder = baseFolder;
			}
			this.lastModified = FileUtils.getLastModified(file);
			content = FileUtils.readContent(file, "\n").trim();
		}
		return parse(content, defaultResource);		
	}
	
	
	Config parse(String content, String defaultResource) throws IOException, MaxmlException {
		Map<String, Object> map = Maxml.parse(content);
		
		if (!StringUtils.isEmpty(defaultResource)) {
			// build.moxie inheritance
			File parentConfig = readFile(map, Key.parent, null);
			if (parentConfig == null) {
				File defaultFile = new File(System.getProperty("user.home") + "/.moxie/" + defaultResource);
				if (this.file == null || this.file.equals(defaultFile) || !defaultFile.exists()) {
					// Moxie-shipped default resource
					Config parent = new Config("/" + defaultResource);
					setDefaultsFrom(parent);
				} else {
					// local filesystem default is available
					Config parent = new Config(defaultFile, baseFolder, defaultResource);
					setDefaultsFrom(parent);
				}
			} else {
				// parent has been specified
				Config parent = new Config(parentConfig, baseFolder, defaultResource);
				setDefaultsFrom(parent);

				// set parent properties
				pom.parentGroupId = pom.groupId;
				pom.parentArtifactId = pom.artifactId;
				pom.parentVersion = pom.version;
			}
		}
		
		// metadata (partially preserve inherited defaults)
		pom.name = readString(map, Key.name, null);
		pom.version = readString(map, Key.version, pom.version);
		pom.groupId = readString(map, Key.groupId, pom.groupId);
		pom.artifactId = readString(map, Key.artifactId, null);	
		pom.classifier = readString(map, Key.classifier, null);
		pom.description = readString(map, Key.description, null);
		pom.url = readString(map, Key.url, pom.url);
		pom.organization = readString(map, Key.organization, pom.organization);
		
		// set default name to artifact id
		if (StringUtils.isEmpty(pom.name)) {
			pom.name = pom.artifactId;
		}

		// build parameters
		apply = new TreeSet<String>(readStrings(map, Key.apply, new ArrayList<String>(), true));
		outputFolder = readFile(map, Key.outputFolder, new File(baseFolder, "build"));
		sourceFolders = readSourceFolders(map, Key.sourceFolders, sourceFolders);		
		targetFolder = readFile(map, Key.targetFolder, new File(baseFolder, "target"));
		linkedProjects = readLinkedProjects(map, Key.linkedProjects);
		dependencyFolder = readFile(map, Key.dependencyFolder, null);
		
		String policy = readString(map, Key.updatePolicy, null);
		if (!StringUtils.isEmpty(policy)) {
			int mins = 0;
			if (policy.indexOf(':') > -1) {
				mins = Integer.parseInt(policy.substring(policy.indexOf(':') + 1));
				policy = policy.substring(0, policy.indexOf(':'));
			}
			updatePolicy = UpdatePolicy.fromString(policy);
			if (UpdatePolicy.interval.equals(updatePolicy) && mins > 0) {
				updatePolicy.setMins(mins);
			}
		}
		
		if (map.containsKey(Key.properties.name())) {
			MaxmlMap props = (MaxmlMap) map.get(Key.properties);
			for (String key : props.keySet()) {				
				pom.setProperty(key, props.getString(key, null));
			}
		}

		List<String> managedDependencies = readStrings(map, Key.dependencyManagement, new ArrayList<String>());
		for (String dependency : managedDependencies) {
			Dependency dep = new Dependency(dependency);
			pom.addManagedDependency(dep, null);
		}

		repositoryUrls = readStrings(map, Key.repositories, repositoryUrls);
		parseDependencyAliases(map, Key.dependencyAliases);
		parseDependencies(map, Key.dependencies);
		parseDependencyOverrides(map, Key.dependencyOverrides);
		parseProxies(map, Key.proxies);
		
		pom.addExclusions(readStrings(map, Key.exclusions, new ArrayList<String>(), true));

		if (map.containsKey(Key.mxjavac.name())) {
			mxjavac.putAll((MaxmlMap) map.get(Key.mxjavac.name()));
		}
		if (map.containsKey(Key.mxjar.name())) {
			mxjar.putAll((MaxmlMap) map.get(Key.mxjar.name()));
		}
		if (map.containsKey(Key.mxreport.name())) {
			mxreport.putAll((MaxmlMap) map.get(Key.mxreport.name()));
		}
		
		// maxml build properties
		readExternalProperties();

		return this;
	}
	
	void parseDependencyAliases(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			MaxmlMap aliases = (MaxmlMap) map.get(key.name());
			for (Map.Entry<String, Object> entry : aliases.entrySet()) {
				String definition = entry.getValue().toString();
				Dependency dep = new Dependency(definition);
				dependencyAliases.put(entry.getKey(), dep);
			}
		}
	}

	void parseDependencies(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			List<?> values = (List<?>) map.get(key.name());
			for (Object definition : values) {
				if (definition instanceof String) {
					processDependency(definition.toString(), pom);
				} else {
					throw new RuntimeException("Illegal dependency " + definition);
				}
			}
		}		
	}
	
	void parseDependencyOverrides(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			MaxmlMap poms = (MaxmlMap) map.get(key.name());
			for (Map.Entry<String, Object> entry: poms.entrySet()) {
				String definition = entry.getKey();
				if (entry.getValue() instanceof MaxmlMap) {
					Dependency dep = new Dependency(definition);
					Pom override = new Pom();					
					override.groupId = dep.groupId;
					override.artifactId = dep.artifactId;
					override.version = dep.version;
					
					if (StringUtils.isEmpty(override.version)) {
						throw new RuntimeException(MessageFormat.format("{0} setting for {1} must specify a version!", Key.dependencyOverrides.name(), definition));
					}
					
					MaxmlMap depMap = (MaxmlMap) entry.getValue();
					for (String def : depMap.getStrings(Key.dependencies.name(), new ArrayList<String>())) {
						processDependency(def, override);
					}
					
					if (depMap.containsKey(Key.scope.name())) {
						// scopes specified
						for (String value : depMap.getStrings(Key.scope.name(), new ArrayList<String>())) {
							Scope scope = Scope.fromString(value);
							if (scope == null) {
								scope = Scope.defaultScope;
							}
							if (!dependencyOverrides.containsKey(scope)) {
								dependencyOverrides.put(scope, new TreeMap<String, Pom>());
							}
							dependencyOverrides.get(scope).put(override.getCoordinates(), override);
						}
					} else {
						// all scopes
						for (Scope scope : Scope.values()) {
							if (!dependencyOverrides.containsKey(scope)) {
								dependencyOverrides.put(scope, new TreeMap<String, Pom>());
							}
							dependencyOverrides.get(scope).put(override.getCoordinates(), override);
						}
					}
				} else {
					throw new RuntimeException(MessageFormat.format("Illegal {0} value {1} : {2}", Key.dependencyOverrides.name(), definition, entry.getValue()));
				}
			}
		}
	}
	
	void processDependency(String def, Pom pom) {
		Scope scope = Scope.fromString(def.substring(0, def.indexOf(' ')));
		if (scope == null) {
			// default scope
			scope = Scope.defaultScope;
		} else {
			// trim out scope
			def = def.substring(scope.name().length()).trim();
		}					
		def = StringUtils.stripQuotes(def);
		
		Dependency dep;
		if (Scope.system.equals(scope)) {
			dep = new SystemDependency(def);
		} else {
			dep = new Dependency(def);
		}
		pom.addDependency(dep, scope);
	}
	
	void parseProxies(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			List<MaxmlMap> values = (List<MaxmlMap>) map.get(key.name());
			List<Proxy> ps = new ArrayList<Proxy>();
			for (MaxmlMap definition : values) {
				Proxy proxy = new Proxy();
				proxy.id = definition.getString("id", "");
				proxy.active = definition.getBoolean("active", false);
				proxy.protocol = definition.getString("protocol", "http");
				proxy.host = definition.getString("host", "");
				proxy.port = definition.getInt("port", 80);
				proxy.username = definition.getString("username", "");
				proxy.password = definition.getString("password", "");
				proxy.nonProxyHosts = definition.getStrings("nonProxyHosts", new ArrayList<String>());
				ps.add(proxy);
			}
			proxies = ps;
		}
	}

	String readRequiredString(Map<String, Object> map, Key key) {
		Object o = map.get(key.name());
		if (o != null && !StringUtils.isEmpty(o.toString())) {
			return o.toString();
		} else {
			keyError(key);
			return null;
		}
	}

	String readString(Map<String, Object> map, Key key, String defaultValue) {
		Object o = map.get(key.name());
		if (o != null && !StringUtils.isEmpty(o.toString())) {
			return o.toString();
		} else {
			return defaultValue;
		}
	}

	List<String> readStrings(Map<String, Object> map, Key key, List<String> defaultValue) {
		return readStrings(map, key, defaultValue, false);
	}
	
	@SuppressWarnings("unchecked")
	List<String> readStrings(Map<String, Object> map, Key key, List<String> defaultValue, boolean toLowerCase) {
		if (map.containsKey(key.name())) {
			List<String> strings = new ArrayList<String>();
			Object o = map.get(key.name());
			if (o instanceof List) {
				List<String> values = (List<String>) o;
				if (toLowerCase) {
					for (String value : values) {
						strings.add(value.toLowerCase());
					}
				} else {
					strings.addAll(values);
				}
			} else if (o instanceof String) {
				String list = o.toString();
				for (String value : StringUtils.breakCSV(list)) {
					if (!StringUtils.isEmpty(value)) {
						if (toLowerCase) {
							strings.add(value.toLowerCase());
						} else {
							strings.add(value);
						}
					}
				}
			}
			if (strings.size() == 0) {
				keyError(key);
			} else {
				return strings;
			}
		}
		return defaultValue;
	}

	boolean readBoolean(Map<String, Object> map, Key key, boolean defaultValue) {
		if (map.containsKey(key.name())) {
			Object o = map.get(key.name());
			if (StringUtils.isEmpty(o.toString())) {
				keyError(key);
			} else {
				return Boolean.parseBoolean(o.toString());
			}
		}
		return defaultValue;
	}

	File readFile(Map<String, Object> map, Key key, File defaultValue) {
		if (map.containsKey(key.name())) {
			Object o = map.get(key.name());
			if (!(o instanceof String)) {
				keyError(key);
			} else {
				String dir = o.toString();
				if (StringUtils.isEmpty(dir)) {
					keyError(key);
				} else {
					org.apache.tools.ant.util.FileUtils futils = org.apache.tools.ant.util.FileUtils.getFileUtils();
					return futils.resolveFile(baseFolder, dir);
				}
			}
		}
		return defaultValue;
	}

	@SuppressWarnings("unchecked")
	List<SourceFolder> readSourceFolders(Map<String, Object> map, Key key, List<SourceFolder> defaultValue) {
		List<SourceFolder> values = new ArrayList<SourceFolder>();
		if (map.containsKey(key.name())) {
			Object o = map.get(key.name());
			if (o instanceof List) {
				// list
				List<Object> list = (List<Object>) o;
				for (Object value : list) {
					if (value == null) {
						continue;
					}
					if (value instanceof String) {
						String def = value.toString();
						String scopeString = def.substring(0, def.indexOf(' ')).trim();
						Scope scope = Scope.fromString(scopeString);
						if (scope == null) {
							scope = Scope.defaultScope;
						} else {
							if (!scope.isValidSourceScope()) {
								scope = Scope.defaultScope;
							}
						}
						def = def.substring(scopeString.length()).trim();
						for (String dir : StringUtils.breakCSV(def)) {
							if (!StringUtils.isEmpty(dir)) {
								values.add(new SourceFolder(dir, scope));
							}
						}
					} else if (value instanceof Map) {
						Map<String, Object> dirMap = (Map<String, Object>) value;
						String dir = readRequiredString(dirMap, Key.folder);
						Scope scope = Scope.fromString(readRequiredString(dirMap, Key.scope));
						if (scope == null) {
							scope = Scope.defaultScope;
						} else {
							if (!scope.isValidSourceScope()) {
								scope = Scope.defaultScope;
							}
						}
						if (!StringUtils.isEmpty(dir)) {
							values.add(new SourceFolder(dir, scope));
						}
					}
				}				
			} else {
				// string definition - all source folders are compile				
				String list = o.toString();
				for (String value : StringUtils.breakCSV(list)) {
					if (!StringUtils.isEmpty(value)) {
						values.add(new SourceFolder(value, Scope.compile));
					}
				}				
			}
		}
		
		if (values.size() == 0) {
			values.addAll(defaultValue);
		}

		// resolve source folders
		List<SourceFolder> resolved = new ArrayList<SourceFolder>();
		for (SourceFolder sf : values) {
			if (sf.resolve(baseFolder, outputFolder)) {
				resolved.add(sf);
			}			
		}
		return resolved;
	}
	
	List<LinkedProject> readLinkedProjects(Map<String, Object> map, Key key) {
		List<LinkedProject> list = new ArrayList<LinkedProject>();
		for (String def : readStrings(map, key, new ArrayList<String>())) {
			LinkedProject project = new LinkedProject(def);
			list.add(project);
		}
		return list;
	}
	
	void keyError(Key key) {
		System.err.println(MessageFormat.format("{0} is improperly specified in {1}, using default", key.name(), file.getAbsolutePath()));
	}
	
	public List<SourceFolder> getSourceFolders() {
		return sourceFolders;
	}
	
	boolean apply(String value) {
		return apply.contains(value.toLowerCase());
	}
	
	List<Proxy> getActiveProxies() {
		List<Proxy> activeProxies = new ArrayList<Proxy>();
		for (Proxy proxy : proxies) {
			if (proxy.active) {
				activeProxies.add(proxy);
			}
		}
		return activeProxies;
	}
	
	Pom getDependencyOverrides(Scope scope, String coordinates) {
		if (dependencyOverrides.containsKey(scope)) {
			return dependencyOverrides.get(scope).get(coordinates);
		}
		return null;
	}
	
	void readExternalProperties() {
		if (file == null) {
			// config object represents internal default resource
			return;
		}
		File propsFile = new File(baseFolder, file.getName().substring(0, file.getName().lastIndexOf('.')) + ".properties");
		if (propsFile.exists()) {
			try {
				Properties props = new Properties();
				FileInputStream is = new FileInputStream(propsFile);
				props.load(is);
				is.close();

				for (Map.Entry<Object, Object> entry : props.entrySet()) {
					externalProperties.put(entry.getKey().toString(), entry.getValue().toString());
				}
			} catch (Exception e) {
			}
		}
	}
	
	void setDefaultsFrom(Config parent) {
		pom = parent.pom;
		lastModified = Math.max(lastModified, parent.lastModified);

		proxies = parent.proxies;
		linkedProjects = parent.linkedProjects;
		repositoryUrls = parent.repositoryUrls;

		dependencyFolder = parent.dependencyFolder;
		sourceFolders = parent.sourceFolders;
		outputFolder = parent.outputFolder;
		targetFolder = parent.targetFolder;
		apply = parent.apply;
		mxjavac = parent.mxjavac;
		mxjar = parent.mxjar;
		mxreport = parent.mxreport;
		dependencyOverrides = parent.dependencyOverrides;
		dependencyAliases = parent.dependencyAliases;
		externalProperties = parent.externalProperties;
		updatePolicy = parent.updatePolicy;
	}
}
