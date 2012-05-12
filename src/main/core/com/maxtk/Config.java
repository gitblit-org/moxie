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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.maxtk.Constants.Key;
import com.maxtk.Dependency.Scope;
import com.maxtk.maxml.Maxml;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.maxml.MaxmlMap;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

/**
 * Reads the build.maxml file 
 */
public class Config implements Serializable {

	private static final long serialVersionUID = 1L;

	File file;
	Pom pom;
	
	List<Proxy> proxies;
	List<String> projects;
	List<String> repositoryUrls;	
	
	File dependencyFolder;
	List<SourceFolder> sourceFolders;
	File outputFolder;
	File targetFolder;
	Set<String> apply;
	boolean debug;

	public static Config load(File file, boolean create) throws IOException, MaxmlException {
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			// write default maxilla settings
			FileWriter writer = new FileWriter(file);
			writer.append("proxies:\n- { id: myproxy, active: false, protocol: http, host:proxy.somewhere.com, port:8080, username: proxyuser, password: somepassword }");
			// TODO write repositories
			writer.close();
		}

		return new Config().parse(file);
	}

	public Config() {
		// default configuration
		sourceFolders = Arrays.asList(
				new SourceFolder(new File("src/main/java"), Scope.compile), 
				new SourceFolder(new File("src/main/resources"), Scope.compile),
				new SourceFolder(new File("src/test/java"), Scope.test));
		outputFolder = new File("build");
		targetFolder = new File("target");
		projects = new ArrayList<String>();
		repositoryUrls = new ArrayList<String>();		
		pom = new Pom();
		dependencyFolder = null;
		apply = new TreeSet<String>();
		proxies = new ArrayList<Proxy>();
	}
	
	public Pom getPom() {
		return pom;
	}

	@Override
	public String toString() {
		return "Config (" + pom + ")";
	}

	Config parse(File file) throws IOException, MaxmlException {
		if (!file.exists()) {
			throw new MaxmlException(MessageFormat.format("{0} does not exist!", file));			
		}
		
		this.file = file;
		
		String content = FileUtils.readContent(file, "\n").trim();
		Map<String, Object> map = Maxml.parse(content);

		// metadata
		pom.name = readString(map, Key.name, false);
		pom.version = readString(map, Key.version, false);
		pom.groupId = readString(map, Key.groupId, false);
		pom.artifactId = readString(map, Key.artifactId, false);
		pom.description = readString(map, Key.description, false);
		pom.url = readString(map, Key.url, false);
		pom.vendor = readString(map, Key.vendor, false);

		// build parameters
		apply = new TreeSet<String>(readStrings(map, Key.apply, new ArrayList<String>(), true));
		sourceFolders = readSourceFolders(map, Key.sourceFolders, sourceFolders);
		outputFolder = readFile(map, Key.outputFolder, outputFolder);
		targetFolder = readFile(map, Key.targetFolder, targetFolder);
		projects = readStrings(map, Key.projects, projects);
		dependencyFolder = readFile(map, Key.dependencyFolder, null);
		
		List<String> props = readStrings(map, Key.properties, new ArrayList<String>());
		for (String prop : props) {
			String [] values = prop.split(" ");
			pom.setProperty(values[0], values[1]);
		}
		pom.setProperty("project.groupId", pom.groupId);
		pom.setProperty("project.artifactId", pom.artifactId);
		pom.setProperty("project.version", pom.version);	
		
		repositoryUrls = readStrings(map, Key.dependencySources, repositoryUrls);
		parseDependencies(map, Key.dependencies);		
		parseProxies(map, Key.proxies);
		return this;
	}

	void parseDependencies(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			List<?> values = (List<?>) map.get(key.name());
			Scope scope;
			for (Object definition : values) {
				if (definition instanceof String) {
					String def = definition.toString();
					if (def.startsWith("compile")) {
						// compile-time dependency
						scope = Scope.compile;
						def = def.substring(Scope.compile.name().length()).trim();
					} else if (def.startsWith("provided")) {
						// provided dependency
						scope = Scope.provided;
						def = def.substring(Scope.provided.name().length()).trim();
					} else if (def.startsWith("runtime")) {
						// runtime dependency
						scope = Scope.runtime;
						def = def.substring(Scope.runtime.name().length()).trim();
					} else if (def.startsWith("test")) {
						// test dependency
						scope = Scope.test;
						def = def.substring(Scope.test.name().length()).trim();
					} else if (def.startsWith("system")) {
						// system dependency
						scope = Scope.system;
						def = def.substring(Scope.system.name().length()).trim();
						Dependency dep = new SystemDependency(def);
						pom.addDependency(dep, scope);
						continue;
					} else {
						// default to compile-time dependency
						scope = Scope.defaultScope;
					}
					
					def = StringUtils.stripQuotes(def);										
					Dependency dep = new Dependency(def);					
					pom.addDependency(dep, scope);
				} else {
					throw new RuntimeException("Illegal dependency " + definition);
				}
			}
		}		
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

	String readString(Map<String, Object> map, Key key, boolean required) {
		Object o = map.get(key.name());
		if (o != null && !StringUtils.isEmpty(o.toString())) {
			return o.toString();
		} else {
			if (required) {
				keyError(key);
			}
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
					return new File(dir);
				}
			}
		}
		return defaultValue;
	}

	@SuppressWarnings("unchecked")
	List<SourceFolder> readSourceFolders(Map<String, Object> map, Key key, List<SourceFolder> defaultValue) {
		if (map.containsKey(key.name())) {
			Object o = map.get(key.name());
			if (o instanceof List) {
				// list
				List<SourceFolder> values = new ArrayList<SourceFolder>();
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
								values.add(new SourceFolder(new File(dir), scope));
							}
						}
					} else if (value instanceof Map) {
						Map<String, Object> dirMap = (Map<String, Object>) value;
						String dir = readString(dirMap, Key.folder, true);
						Scope scope = Scope.fromString(readString(dirMap, Key.scope, true));
						if (scope == null) {
							scope = Scope.defaultScope;
						} else {
							if (!scope.isValidSourceScope()) {
								scope = Scope.defaultScope;
							}
						}
						if (!StringUtils.isEmpty(dir)) {
							values.add(new SourceFolder(new File(dir), scope));
						}
					}
				}
				if (values.size() == 0) {
					keyError(key);
				} else {
					return values;
				}
			} else {
				// string definition - all source folders are compile
				List<SourceFolder> values = new ArrayList<SourceFolder>();
				String list = o.toString();
				for (String value : StringUtils.breakCSV(list)) {
					if (!StringUtils.isEmpty(value)) {
						values.add(new SourceFolder(new File(value)));
					}
				}
				if (values.size() == 0) {
					keyError(key);
				} else {
					return values;
				}
			}
		}
		return defaultValue;
	}
	
	void keyError(Key key) {
		System.err.println(MessageFormat.format("{0} is improperly specified, using default", key.name()));
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
}
