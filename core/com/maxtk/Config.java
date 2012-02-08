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
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.maxtk.maxml.Maxml;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class Config implements Serializable {

	private static final long serialVersionUID = 1L;

	enum Key {
		version, name, description, url, vendor, artifactId, sourceFolder, sourceFolders, outputFolder, dependencyFolder, mavenUrls, dependencies, configureEclipseClasspath, googleAnalyticsId, googlePlusId;
	}

	String name;
	String version;
	String description;
	String url;
	String vendor;
	String artifactId;
	List<String> mavenUrls;
	List<Dependency> dependencies;
	File dependencyFolder;
	List<File> sourceFolders;
	File outputFolder;
	boolean configureEclipseClasspath;

	public static Config load(String path) throws IOException, MaxmlException {
		return new Config().parse(path);
	}

	private Config() {
		// default configuration
		sourceFolders = Arrays.asList(new File("src"));
		outputFolder = new File("bin");
		mavenUrls = Arrays.asList("mavencentral");
		dependencies = new ArrayList<Dependency>();
		dependencyFolder = new File("lib");
	}

	@Override
	public String toString() {
		return "Config (" + name + " " + version + ")";
	}
	
	public void addMavenUrl(String url) {
		Set<String> urls = new LinkedHashSet<String>(mavenUrls);
		urls.add(url);
		mavenUrls = new ArrayList<String>(urls);
	}

	@SuppressWarnings("unchecked")
	Config parse(String path) throws IOException, MaxmlException {
		File conf = new File(path);
		if (!conf.exists()) {
			Setup.out.println(MessageFormat.format(
					"{0} does not exist, using defaults.", path));
			return this;
		}
		
		String content = FileUtils.readContent(conf, "\n").trim();
		Map<String, Object> map = Maxml.parse(content);

		// metadata
		name = readString(map, Key.name, false);
		version = readString(map, Key.version, false);
		description = readString(map, Key.description, false);
		url = readString(map, Key.url, false);
		vendor = readString(map, Key.vendor, false);
		artifactId = readString(map, Key.artifactId, false);

		// build parameters
		configureEclipseClasspath = readBoolean(map,
				Key.configureEclipseClasspath, false);
		sourceFolders = readFiles(map, Key.sourceFolders, sourceFolders);
		outputFolder = readFile(map, Key.outputFolder, outputFolder);
		dependencyFolder = readFile(map, Key.dependencyFolder, dependencyFolder);

		// allow shortcut names for maven repositories
		List<String> urls = readStrings(map, Key.mavenUrls, mavenUrls);
		mavenUrls = new ArrayList<String>();
		for (String url : urls) {
			if ("mavencentral".equalsIgnoreCase(url)) {
				mavenUrls.add("http://repo1.maven.org/maven2");
			} else {
				mavenUrls.add(url);
			}
		}

		// parse library dependencies
		if (map.containsKey(Key.dependencies.name())) {
			List<List<String>> values = (List<List<String>>) map.get(Key.dependencies.name());
			List<Dependency> libs = new ArrayList<Dependency>();
			for (List<String> lib : values) {
				Dependency mo = new Dependency(lib.get(0), lib.get(1), lib.get(2));
				libs.add(mo);
			}
			if (libs.size() == 0) {
				keyError(Key.dependencies);
			} else {
				dependencies = libs;
			}
		}
		return this;
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

	@SuppressWarnings("unchecked")
	List<String> readStrings(Map<String, Object> map, Key key,
			List<String> defaultValue) {
		if (map.containsKey(key.name())) {
			List<String> strings = new ArrayList<String>();
			Object o = map.get(key.name());
			if (o instanceof List) {
				List<String> values = (List<String>) o;
				strings.addAll(values);
			} else if (o instanceof String) {
				String value = o.toString();
				if (StringUtils.isEmpty(value)) {
					keyError(key);
				} else {
					strings.add(value);
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
	List<File> readFiles(Map<String, Object> map, Key key, List<File> defaultValue) {
		if (map.containsKey(key.name())) {
			Object o = map.get(key.name());
			if (o instanceof List) {
				// list
				List<File> values = new ArrayList<File>();
				List<String> list = (List<String>) o;
				for (String dir : list) {
					if (!StringUtils.isEmpty(dir)) {
						values.add(new File(dir));
					}
				}
				if (values.size() == 0) {
					keyError(key);
				} else {
					return values;
				}
			} else {
				// single definition
				String dir = o.toString();
				if (!StringUtils.isEmpty(dir)) {
					return Arrays.asList(new File(dir));
				}
			}
		}
		return defaultValue;
	}

	void keyError(Key key) {
		Setup.out.println(MessageFormat.format(
				"{0} is improperly specified, using default", key.name()));
	}

	void describe(PrintStream out) {
		out.println("metadata");
		describe(out, Key.name, name);
		describe(out, Key.description, description);
		describe(out, Key.version, version);
		describe(out, Key.vendor, vendor);
		describe(out, Key.url, url);
		describe(out, Key.artifactId, artifactId);
		out.println(Constants.SEP);

		out.println("source folders");
		for (File folder : sourceFolders) {
			out.print(Constants.INDENT);
			out.println(folder);
		}
		out.println(Constants.SEP);

		out.println("output folder");
		describe(out, Key.outputFolder, outputFolder.toString());
		out.println(Constants.SEP);

		out.println("maven urls");
		for (String url : mavenUrls) {
			out.print(Constants.INDENT);
			out.println(url);
		}
		out.println(Constants.SEP);
		out.println(MessageFormat.format("library dependencies (=> {0})",
				dependencyFolder));
		for (Dependency dep : dependencies) {
			out.print(Constants.INDENT);
			out.println(dep.toString());
		}
	}

	void describe(PrintStream out, Key key, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		out.print(Constants.INDENT);
		out.print(StringUtils.leftPad(key.name(), 12, ' '));
		out.print(": ");
		out.println(value);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getVersion() {
		return version;
	}

	public String getUrl() {
		return url;
	}

	public String getVendor() {
		return vendor;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public List<File> getClasspath() {
		List<File> files = new ArrayList<File>();
		for (Dependency obj : dependencies) {
			File file = new File(dependencyFolder, obj.getArtifactName(""));
			files.add(file);
		}
		files.add(outputFolder);
		return files;
	}

	public List<File> getSourceFolders() {
		return sourceFolders;
	}

	public File getOutputFolder() {
		return outputFolder;
	}
}
