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
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.moxie.Constants.MavenCacheStrategy;
import org.moxie.Toolkit.Key;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


/**
 * Reads a Moxie tookit config file such as settings.moxie or build.moxie.
 */
public class ToolkitConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	File file;
	File baseDirectory;
	Pom pom;
	long lastModified;
	String mainclass;

	List<Proxy> proxies;
	List<Module> modules;
	List<Module> linkedModules;
	List<String> repositories;
	List<RemoteRepository> registeredRepositories;

	File dependencyDirectory;
	List<SourceDirectory> sourceDirectories;
	List<SourceDirectory> resourceDirectories;
	File outputDirectory;
	File targetDirectory;
	Set<String> apply;
	Map<String, Dependency> dependencyAliases;
	Map<Scope, Map<String, Pom>> dependencyOverrides;
	MaxmlMap tasks;
	Map<String, String> externalProperties;
	UpdatePolicy updatePolicy;
	int revisionRetentionCount;
	int revisionPurgeAfterDays;
	MavenCacheStrategy mavenCacheStrategy;
	boolean failFastOnArtifactResolution;
	boolean parallelDownloads;
	String dependencyNamePattern;

	public ToolkitConfig() {
		// default configuration
		sourceDirectories = Arrays.asList(
				new SourceDirectory("src/main/java", Scope.compile),
				new SourceDirectory("src/main/webapp", Scope.compile),
				new SourceDirectory("src/test/java", Scope.test),
				new SourceDirectory("src/java", Scope.compile),
				new SourceDirectory("src", Scope.compile),
				new SourceDirectory("src/test", Scope.test),
				new SourceDirectory("tests", Scope.test),
				new SourceDirectory("test", Scope.test),
				new SourceDirectory("src/site", Scope.site));
		resourceDirectories = Arrays.asList(
				new SourceDirectory("src/main/resources", Scope.compile),
				new SourceDirectory("resources/java", Scope.compile),
				new SourceDirectory("resources/main", Scope.compile),
				new SourceDirectory("resources", Scope.compile),
				new SourceDirectory("src/test/resources", Scope.test),
				new SourceDirectory("resources/test", Scope.test));
		outputDirectory = new File("build");
		targetDirectory = new File("build/target");
		linkedModules = new ArrayList<Module>();
		repositories = Arrays.asList("central");
		registeredRepositories = Arrays.asList(new RemoteRepository("central", "https://repo1.maven.org/maven2", false));
		pom = new Pom();
		dependencyDirectory = null;
		dependencyNamePattern = Toolkit.DEPENDENCY_FILENAME_PATTERN;
		apply = new TreeSet<String>();
		proxies = new ArrayList<Proxy>();
		modules = new ArrayList<Module>();
		dependencyAliases = new HashMap<String, Dependency>();
		dependencyOverrides = new HashMap<Scope, Map<String, Pom>>();
		tasks = new MaxmlMap();
		externalProperties = new HashMap<String, String>();
		updatePolicy = UpdatePolicy.defaultPolicy;
		revisionRetentionCount = 1;
		revisionPurgeAfterDays = 0;
	}

	public ToolkitConfig(File file, File baseDirectory, String defaultResource) throws IOException, MaxmlException {
		this();
		parse(file, baseDirectory, defaultResource);
	}

	private ToolkitConfig(String resource) throws IOException, MaxmlException {
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

	public String getMainclass() {
		return mainclass;
	}

	public PurgePolicy getPurgePolicy() {
		PurgePolicy  policy = new PurgePolicy();
		policy.retentionCount = revisionRetentionCount;
		policy.purgeAfterDays = revisionPurgeAfterDays;
		return policy;
	}

	@Override
	public String toString() {
		return "ToolkitConfig (" + pom + ")";
	}

	ToolkitConfig parse(File file, File baseDirectory, String defaultResource) throws IOException, MaxmlException {
		String content = "";
		if (file != null && file.exists()) {
			this.file = file;
			if (baseDirectory == null) {
				this.baseDirectory = file.getAbsoluteFile().getParentFile();
			} else {
				this.baseDirectory = baseDirectory;
			}
			this.lastModified = FileUtils.getLastModified(file);
			content = FileUtils.readContent(file, "\n").trim();
		}
		return parse(content, defaultResource);
	}


	ToolkitConfig parse(String content, String defaultResource) throws IOException, MaxmlException {
		MaxmlMap map = Maxml.parse(content);

		if (map.containsKey(Key.requires.name())) {
			// enforce a required minimum Moxie version
			String req = map.getString(Key.requires.name(), null);
			ArtifactVersion requires = new ArtifactVersion(req);
			ArtifactVersion running = new ArtifactVersion(Toolkit.getVersion());
			if (running.compareTo(requires) == -1) {
				throw new MoxieException("Moxie {0} required for this script.", requires);
			}
		}

		if (!StringUtils.isEmpty(defaultResource)) {
			// build.moxie inheritance
			File parentConfig = readFile(map, Key.parent, null);
			if (parentConfig == null) {
				File defaultFile = new File(Toolkit.getMxRoot(), defaultResource);
				if (this.file == null || this.file.equals(defaultFile) || !defaultFile.exists()) {
					// Moxie-shipped default resource
					ToolkitConfig parent = new ToolkitConfig("/" + defaultResource);
					setDefaultsFrom(parent);
				} else {
					// local filesystem default is available
					ToolkitConfig parent = new ToolkitConfig(defaultFile, baseDirectory, defaultResource);
					setDefaultsFrom(parent);
				}
			} else {
				// parent has been specified
				ToolkitConfig parent = new ToolkitConfig(parentConfig, baseDirectory, defaultResource);
				setDefaultsFrom(parent);
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
		pom.organizationUrl = readString(map, Key.organizationUrl, pom.organizationUrl);
		pom.issuesUrl = readString(map, Key.issuesUrl, pom.issuesUrl);
		pom.inceptionYear = readString(map, Key.inceptionYear, pom.inceptionYear);
		pom.packaging = readString(map, Key.packaging, pom.packaging);
		pom.getDevelopers().addAll(readPersons(map, Key.developers));
		pom.getContributors().addAll(readPersons(map, Key.contributors));
		pom.getLicenses().addAll(readLicenses(map, Key.licenses));
		pom.forumUrl = readString(map, Key.forumUrl, pom.forumUrl);
		pom.socialNetworkUrl = readString(map, Key.socialNetworkUrl, pom.socialNetworkUrl);
		pom.blogUrl = readString(map, Key.blogUrl, pom.blogUrl);
		pom.ciUrl = readString(map, Key.ciUrl, pom.ciUrl);
		pom.mavenUrl = readString(map, Key.mavenUrl, pom.mavenUrl);

		String parentPom = map.getString(Key.parentPom.name(), null);
		if (!StringUtils.isEmpty(parentPom)) {
			// config specifies a parent pom
			Dependency parent = new Dependency(parentPom);
			pom.parentGroupId = parent.groupId;
			pom.parentArtifactId = parent.artifactId;
			pom.parentVersion = parent.version;
		}

		// scm metadata
		if (map.containsKey(Key.scm)) {
			MaxmlMap scm = map.getMap(Key.scm.name());
			pom.scm.connection = scm.getString(Key.connection.name(), null);
			pom.scm.developerConnection = scm.getString(Key.developerConnection.name(), null);
			pom.scm.url = scm.getString(Key.url.name(), null);
			pom.scm.tag = scm.getString(Key.tag.name(), null);
		}

		// set default name to artifact id
		if (StringUtils.isEmpty(pom.name)) {
			pom.name = pom.artifactId;
		}

		mainclass = readString(map, Key.mainclass, null);
		pom.releaseVersion = readString(map, Key.releaseVersion, pom.releaseVersion);
		pom.releaseDate = map.getDate(Key.releaseDate.name(), pom.releaseDate);

		// build parameters
		mavenCacheStrategy = MavenCacheStrategy.fromString(map.getString(Key.mavenCacheStrategy.name(), mavenCacheStrategy == null ? null : mavenCacheStrategy.name()));
		parallelDownloads = map.getBoolean(Key.parallelDownloads.name(), parallelDownloads);
		failFastOnArtifactResolution = map.getBoolean(Key.failFastOnArtifactResolution.name(), failFastOnArtifactResolution);
		apply = new TreeSet<String>(readStrings(map, Key.apply, new ArrayList<String>(apply), true));
		outputDirectory = readFile(map, Key.outputDirectory, new File(baseDirectory, "build"));
		targetDirectory = readFile(map, Key.targetDirectory, new File(baseDirectory, "build/target"));
		sourceDirectories = readSourceDirectories(map, Key.sourceDirectories, sourceDirectories);
		if (sourceDirectories.isEmpty()) {
			// container descriptors (e.g. POM) can define modules
			modules = readModules(map, Key.modules);
		} else {
			// standard project/modules can define linked modules which are
			// treated as extensions of this projects sourceDirectories and
			// dependencies
			linkedModules = readModules(map, Key.modules);
		}
		resourceDirectories = readSourceDirectories(map, Key.resourceDirectories, resourceDirectories);
		dependencyDirectory = readFile(map, Key.dependencyDirectory, null);
		dependencyNamePattern = map.getString(Key.dependencyNamePattern.name(), dependencyNamePattern);

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

		// default snapshot purge policy
		revisionRetentionCount = Math.min(Toolkit.MAX_REVISIONS,
				Math.max(1, map.getInt(Key.revisionRetentionCount.name(), revisionRetentionCount)));
		revisionPurgeAfterDays = Math.min(Toolkit.MAX_PURGE_AFTER_DAYS,
				Math.max(0, map.getInt(Key.revisionPurgeAfterDays.name(), revisionPurgeAfterDays)));

		if (map.containsKey(Key.properties.name())) {
			MaxmlMap props = map.getMap(Key.properties.name());
			for (String key : props.keySet()) {
				pom.setProperty(key, props.getString(key, null));
			}
		}

		List<String> managedDependencies = readStrings(map, Key.dependencyManagement, new ArrayList<String>());
		for (String dependency : managedDependencies) {
			Dependency dep = new Dependency(dependency);
			pom.addManagedDependency(dep, null);
		}

		registeredRepositories = parseRemoteRepositories(map, Key.registeredRepositories, registeredRepositories);
		repositories = map.getStrings(Key.repositories.name(), repositories);
		parseDependencyAliases(map, Key.dependencyAliases);
		parseDependencies(map, Key.dependencies);
		parseDependencyOverrides(map, Key.dependencyOverrides);
		parseProxies(map, Key.proxies);

		pom.addExclusions(readStrings(map, Key.exclusions, new ArrayList<String>(), true));

		// all task attributes are inherited. individual attributes can be overridden
		if (map.containsKey("tasks")) {
			MaxmlMap taskOverrides = map.getMap("tasks");
			for (Map.Entry<String, Object> entry : taskOverrides.entrySet()) {
				String task = entry.getKey();
				MaxmlMap taskAttributes  = (MaxmlMap) entry.getValue();
				if (tasks.containsKey(task)) {
					// update existing entry with attribute overrides
					tasks.getMap(task).putAll(taskAttributes);
				} else {
					// new entry
					tasks.put(task, taskAttributes);
				}
			}
		}

		// maxml build properties
		if (file != null) {
			// config object represents actual file, not internal default resource
			importProperties(file.getParentFile());
		}

		return this;
	}

	void parseDependencyAliases(MaxmlMap map, Key key) {
		if (map.containsKey(key.name())) {
			MaxmlMap aliases = map.getMap(key.name());
			for (Map.Entry<String, Object> entry : aliases.entrySet()) {
				String definition = entry.getValue().toString();
				Dependency dep = new Dependency(definition);
				dependencyAliases.put(entry.getKey(), dep);
			}
		}
	}

	void parseDependencies(MaxmlMap map, Key key) {
		if (map.containsKey(key.name())) {
			List<?> values = map.getList(key.name(), null);
			for (Object definition : values) {
				if (definition instanceof String) {
					processDependency(definition.toString(), pom);
				} else {
					throw new RuntimeException("Illegal dependency " + definition);
				}
			}
		}
	}

	void parseDependencyOverrides(MaxmlMap map, Key key) {
		if (map.containsKey(key.name())) {
			MaxmlMap poms = map.getMap(key.name());
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
        dep.definedScope = scope;
		pom.addDependency(dep, scope);
	}

	List<RemoteRepository> parseRemoteRepositories(MaxmlMap map, Key key, List<RemoteRepository> defaultsValue) {
		List<RemoteRepository> remotes = new ArrayList<RemoteRepository>();
		if (map.containsKey(key.name())) {
			for (Object o : map.getList(key.name(), Collections.emptyList())) {
				if (o instanceof String) {
					remotes.add(new RemoteRepository("", o.toString(), false));
				} else if (o instanceof MaxmlMap) {
					MaxmlMap repoMap = (MaxmlMap) o;
					String id = repoMap.getString("id", null);
					String url = repoMap.getString("url", null).replace('\\', '/');
					boolean allowSnapshots = repoMap.getBoolean("allowSnapshots", false);
					RemoteRepository repo = new RemoteRepository(id, url, allowSnapshots);
					repo.purgePolicy.retentionCount = Math.min(Toolkit.MAX_REVISIONS,
							Math.max(1, repoMap.getInt(Key.revisionRetentionCount.name(), revisionRetentionCount)));
					repo.purgePolicy.purgeAfterDays = Math.min(Toolkit.MAX_PURGE_AFTER_DAYS,
							Math.max(0, repoMap.getInt(Key.revisionPurgeAfterDays.name(), revisionPurgeAfterDays)));
					for (String value : repoMap.getStrings("affinity", new ArrayList<String>())) {
						repo.affinity.add(value.toLowerCase());
					}
					repo.connectTimeout = repoMap.getInt(Key.connectTimeout.name(), repo.connectTimeout);
					repo.readTimeout = repoMap.getInt(Key.readTimeout.name(), repo.readTimeout);
					repo.username = repoMap.getString(Key.username.name(), null);
					repo.password = repoMap.getString(Key.password.name(), null);
					remotes.add(repo);
				}
			}
			return remotes;
		}
		return defaultsValue;
	}

	@SuppressWarnings("unchecked")
	void parseProxies(MaxmlMap map, Key key) {
		if (map.containsKey(key.name())) {
			List<MaxmlMap> values = (List<MaxmlMap>) map.getList(key.name(), null);
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
				proxy.repositories = definition.getStrings("repositories", new ArrayList<String>());
				proxy.proxyHosts = definition.getStrings("proxyHosts", new ArrayList<String>());
				proxy.nonProxyHosts = definition.getStrings("nonProxyHosts", new ArrayList<String>());
				ps.add(proxy);
			}
			proxies = ps;
		}
	}

	String readRequiredString(MaxmlMap map, Key key) {
		Object o = map.get(key.name());
		if (o != null && !StringUtils.isEmpty(o.toString())) {
			return o.toString();
		} else {
			keyError(key);
			return null;
		}
	}

	String readString(MaxmlMap map, Key key, String defaultValue) {
		Object o = map.get(key.name());
		if (o != null && !StringUtils.isEmpty(o.toString())) {
			return o.toString();
		} else {
			return defaultValue;
		}
	}

	List<String> readStrings(MaxmlMap map, Key key, List<String> defaultValue) {
		return readStrings(map, key, defaultValue, false);
	}

	@SuppressWarnings("unchecked")
	List<String> readStrings(MaxmlMap map, Key key, List<String> defaultValue, boolean toLowerCase) {
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

	boolean readBoolean(MaxmlMap map, Key key, boolean defaultValue) {
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

	File readFile(MaxmlMap map, Key key, File defaultValue) {
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
					return futils.resolveFile(baseDirectory, dir);
				}
			}
		}
		return defaultValue;
	}

	@SuppressWarnings("unchecked")
	List<SourceDirectory> readSourceDirectories(MaxmlMap map, Key key, List<SourceDirectory> defaultValue) {
		List<SourceDirectory> values = new ArrayList<SourceDirectory>();
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
						int x = def.indexOf(' ') == -1 ? def.length() - 1 : def.indexOf(' ');
						String dir = StringUtils.stripQuotes(def.substring(0, x));

						SourceDirectory sd = new SourceDirectory(dir, scope);
						values.add(sd);

						def = def.substring(x + 1);
						if (!StringUtils.isEmpty(def)) {
							def = def.substring(def.indexOf(' ') + 1);
							for (String defValue : def.split(" ")) {
								if (!StringUtils.isEmpty(defValue)) {
									switch (defValue.charAt(0)) {
									case ':':
										sd.tags.add(defValue.substring(1).toLowerCase());
										break;
									default:
										if ("apt".equalsIgnoreCase(defValue)) {
											sd.apt = true;
										}
									}
								}
							}
						}
					} else if (value instanceof MaxmlMap) {
						MaxmlMap dirMap = (MaxmlMap) value;
						String dir = StringUtils.stripQuotes(readRequiredString(dirMap, Key.dir));
						Scope scope = Scope.fromString(readRequiredString(dirMap, Key.scope));
						List<String> tags = dirMap.getStrings("tags", new ArrayList<String>());
						if (scope == null) {
							scope = Scope.defaultScope;
						} else {
							if (!scope.isValidSourceScope()) {
								scope = Scope.defaultScope;
							}
						}
						if (!StringUtils.isEmpty(dir)) {
							SourceDirectory sd = new SourceDirectory(dir, scope);
							for (String tag : tags) {
								sd.tags.add(tag.toLowerCase());
							}
							values.add(sd);
						}
					}
				}
			} else {
				// string definition - all source folders are compile
				String list = o.toString();
				for (String value : StringUtils.breakCSV(list)) {
					if (!StringUtils.isEmpty(value)) {
						values.add(new SourceDirectory(StringUtils.stripQuotes(value), Scope.compile));
					}
				}
			}
		}

		if (values.size() == 0) {
			values.addAll(defaultValue);
		}

		// resolve source directories
		List<SourceDirectory> resolved = new ArrayList<SourceDirectory>();
		for (SourceDirectory sd : values) {
			File outDir = outputDirectory;
			if (Scope.site.equals(sd.scope)) {
				outDir = targetDirectory;
			}
			if (sd.resolve(baseDirectory, outDir)) {
				// Only add this source directory if it is not the parent
				// of an already added source directory.  This is used to
				// automatically support standard Maven projects and standard
				// Eclipse projects without manual source directory definition.
				if (!isParentDir(resolved, sd.scope, sd.getSources())) {
					resolved.add(sd);
				}
			}
		}
		return resolved;
	}

	/**
	 * Returns true if the specified dir is the parent of one of the source
	 * directories in the list.
	 *
	 * @param dirs
	 * @param scope
	 * @param dir
	 * @return true if the dir is a parent of a source directory
	 */
	boolean isParentDir(List<SourceDirectory> dirs, Scope scope, File dir) {
		for (SourceDirectory sd : dirs) {
			if (scope.equals(sd.scope)) {
				if (FileUtils.getRelativePath(dir, sd.getSources()) != null) {
					// source directory is located relative to dir
					return true;
				}
			}
		}
		return false;
	}

	List<Module> readModules(MaxmlMap map, Key key) {
		List<Module> list = new ArrayList<Module>();
		for (String def : readStrings(map, key, new ArrayList<String>())) {
			Module module = new Module(def);
			list.add(module);
		}
		return list;
	}

	List<Person> readPersons(MaxmlMap map, Key key) {
		List<Person> list = new ArrayList<Person>();
		if (map.containsKey(key)) {
			for (Object o : map.getList(key.name(), new ArrayList<Object>())) {
				if (o instanceof MaxmlMap) {
					MaxmlMap m = (MaxmlMap) o;
					Person p = new Person();
					p.id = m.getString(Key.id.name(), null);
					p.name = m.getString(Key.name.name(), null);
					p.email = m.getString(Key.email.name(), null);
					p.organization = m.getString(Key.organization.name(), null);
					p.organizationUrl = m.getString(Key.organizationUrl.name(), null);
					p.url = m.getString(Key.url.name(), null);
					p.roles = m.getStrings(Key.roles.name(), new ArrayList<String>());
					list.add(p);
				}
			}
		}
		return list;
	}

	List<License> readLicenses(MaxmlMap map, Key key) {
		List<License> list = new ArrayList<License>();
		if (map.containsKey(key)) {
			for (Object o : map.getList(key.name(), new ArrayList<Object>())) {
				if (o instanceof MaxmlMap) {
					MaxmlMap m = (MaxmlMap) o;
					String name = m.getString(Key.name.name(), null);
					String url = m.getString(Key.url.name(), null);
					License license = new License(name, url);

					license.distribution = m.getString("distribution", null);
					license.comments = m.getString("comments", null);

					list.add(license);
				}
			}
		}
		return list;
	}

	void keyError(Key key) {
		System.err.println(MessageFormat.format("ERROR: {0} is improperly specified in {1}, using default", key.name(), file.getAbsolutePath()));
	}

	public List<SourceDirectory> getSourceDirectories() {
		return sourceDirectories;
	}

	public List<SourceDirectory> getResourceDirectories() {
		return resourceDirectories;
	}

	public File getDependencyDirectory() {
		return dependencyDirectory;
	}

	public File getDependencySourceDirectory() {
		return new File(dependencyDirectory, "src");
	}

	public File getProjectDependencyArtifact(Dependency dependency) {
		File baseFolder = getDependencyDirectory();
		String filename = Dependency.getFilename(dependency, dependency.extension, dependencyNamePattern);
		return new File(baseFolder, filename);
	}

	public File getProjectDependencySourceArtifact(Dependency dependency) {
		File baseFolder = getDependencySourceDirectory();
		String filename = Dependency.getFilename(dependency, dependency.extension, dependencyNamePattern);
		return new File(baseFolder, filename);
	}

	public List<Proxy> getProxies() {
		return proxies;
	}

	boolean apply(String value) {
		return apply.contains(value.toLowerCase());
	}

	EclipseSettings getEclipseSettings() {
		EclipseSettings settings = apply(EclipseSettings.class, Toolkit.APPLY_ECLIPSE);
		return settings;
	}

	IntelliJSettings getIntelliJSettings() {
		IntelliJSettings settings = apply(IntelliJSettings.class, Toolkit.APPLY_INTELLIJ);
		return settings;
	}

	<X> X apply(Class<X> clazz, String parameter) {
		for (String value : apply) {
			if (value.toLowerCase().startsWith(parameter)) {
				try {
					X x = clazz.newInstance();
					String switches = value.substring(parameter.length());
					if (switches.length() > 0 && switches.charAt(0) == ':') {
						switches = switches.substring(1);
						for (String sw : switches.split("\\+")) {
							sw = sw.trim();
							String [] kvp = sw.split("=");
							String key = kvp[0];
							if (StringUtils.isEmpty(key)) {
								continue;
							}
							String keyValue;
							if (kvp.length == 1) {
								keyValue = "true";
							} else {
								keyValue = kvp[1];
							}
							try {
								Field field = clazz.getDeclaredField(key);
								field.setAccessible(true);
								Class<?> fieldClass = field.getType();
								if ((boolean.class == fieldClass) || Boolean.class.isAssignableFrom(fieldClass)) {
									field.set(x, true);
								} else if (String.class == fieldClass) {
									field.set(x, keyValue);
								}
							} catch (NoSuchFieldException e) {
								System.out.println(MessageFormat.format("WARNING: Unrecognized switch \"{1}\" for apply parameter \"{0}\"", parameter, sw));
							}
						}
					}
					return x;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
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

	void importProperties(File directory) {
		if (directory == null || !directory.exists()) {
			return;
		}
		importExternalProperties(new File(directory, "moxie.properties"));
		importExternalProperties(new File(directory, "project.properties"));
		importExternalProperties(new File(directory, file.getName().substring(0, file.getName().lastIndexOf('.')) + ".properties"));
	}

	void importExternalProperties(File propsFile) {
		if (propsFile.exists()) {
			// System.out.println("importing " + propsFile.getAbsolutePath());
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

	void setDefaultsFrom(ToolkitConfig parent) {
		pom = parent.pom;
		lastModified = Math.max(lastModified, parent.lastModified);

		proxies = parent.proxies;
		linkedModules = parent.linkedModules;
		repositories = parent.repositories;
		registeredRepositories = parent.registeredRepositories;

		dependencyDirectory = parent.dependencyDirectory;
		sourceDirectories = parent.sourceDirectories;
		resourceDirectories = parent.resourceDirectories;
		outputDirectory = parent.outputDirectory;
		targetDirectory = parent.targetDirectory;
		apply = parent.apply;
		tasks = parent.tasks;
		dependencyOverrides = parent.dependencyOverrides;
		dependencyAliases = parent.dependencyAliases;
		externalProperties = parent.externalProperties;
		updatePolicy = parent.updatePolicy;
		revisionRetentionCount = parent.revisionRetentionCount;
		revisionPurgeAfterDays = parent.revisionPurgeAfterDays;
		mavenCacheStrategy = parent.mavenCacheStrategy;
		parallelDownloads = parent.parallelDownloads;
		failFastOnArtifactResolution = parent.failFastOnArtifactResolution;
	}
}
