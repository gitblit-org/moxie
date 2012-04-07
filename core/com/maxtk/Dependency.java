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
import java.util.List;

import com.maxtk.utils.StringUtils;

/**
 * Dependency represents a retrievable artifact.
 */
public class Dependency {
		
	public static final String POM = ".pom";
	public static final String LIB = ".jar";
	public static final String SRC = "-sources.jar";

	final String group;
	final String artifact;
	final String version;
	final String classifier;
	final boolean resolveTransitiveDependencies;

	public Dependency(String def) {
		this(def.split(":"));		
	}
	
	public Dependency(String [] def) {
		this(def[0], def[1], def[2], def.length == 4 ? def[3] : null);
	}
	
	public Dependency(List<String> def) {
		this(def.get(0), def.get(1), def.get(2), def.size() == 4 ? def.get(3) : null);
	}
	
	public Dependency(String group, String artifact, String version) {
		this(group, artifact, version, null);
	}
	
	public Dependency(String group, String artifact, String version, String classifier) {
		this.group = group;
		this.artifact = artifact;		
		this.version = stripExtension(version);
		this.classifier = stripExtension(classifier);
		if (!StringUtils.isEmpty(this.classifier)) {
			// check for @extension on classifier
			resolveTransitiveDependencies = this.classifier.equals(classifier);
		} else {
			// check for @extension on version
			resolveTransitiveDependencies = this.version.equals(version);
		}
	}
	
	private String stripExtension(String def) {
		if (!StringUtils.isEmpty(def)) {			
			if (def.indexOf('@') > -1) {
				return def.substring(0, def.indexOf('@'));
			}
			return def;
		} else {
			return "";
		}
	}

	public String getArtifactPath(String fileType) {
		if ("<googlecode>".equals(group)) {
			return MessageFormat.format("http://{0}.googlecode.com/files/{1}",
					version, artifact);
		}
		return group.replace('.', '/') + "/" + artifact + "/" + version + "/"
				+ artifact + "-" + version + fileType;
	}

	public String getArtifactName(String fileType) {
		if ("<googlecode>".equals(group)) {
			return artifact;
		}
		return artifact + "-" + version + fileType;
	}

	public boolean isMavenObject() {
		return group.charAt(0) != '<';
	}

	@Override
	public String toString() {
		return artifact + " " + version;
	}
}