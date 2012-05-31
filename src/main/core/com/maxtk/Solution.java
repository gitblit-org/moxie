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
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.maxtk.Constants.Key;
import com.maxtk.maxml.Maxml;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

/**
 * Reads a cached transitive dependency solution. 
 */
public class Solution implements Serializable {

	private static final long serialVersionUID = 1L;

	private final File file;
	
	private final Map<Scope, Set<Dependency>> dependencies;
	
	public Solution(File file) throws IOException, MaxmlException {
		this.file = file;
		this.dependencies = new TreeMap<Scope, Set<Dependency>>();
		parse();
	}

	@Override
	public String toString() {
		return "Solution (" + file + ")";
	}

	Solution parse() throws IOException, MaxmlException {
		
		Map<String, Object> map = new HashMap<String, Object>();
		if (file.exists()) {
			String content = FileUtils.readContent(file, "\n").trim();
			map = Maxml.parse(content);
		}
		parseDependencies(map, Key.dependencies);		
		return this;
	}

	void parseDependencies(Map<String, Object> map, Key key) {
		if (map.containsKey(key.name())) {
			List<?> values = (List<?>) map.get(key.name());			
			for (Object definition : values) {
				if (definition instanceof String) {
					String def = definition.toString();
					Scope scope = Scope.fromString(def.substring(0, def.indexOf(' ')));
					if (scope == null) {
						// default scope
						scope = Scope.defaultScope;
					} else {
						// trim out scope
						def = def.substring(scope.name().length()).trim();
					}					
					
					// pull ring from solution
					int ringIdx = def.indexOf(' ');
					int ring = Integer.parseInt(def.substring(0, ringIdx));
					
					def = StringUtils.stripQuotes(def.substring(ringIdx).trim());
					
					Dependency dep;
					if (Scope.system.equals(scope)) {
						dep = new SystemDependency(def);
					} else {
						dep = new Dependency(def);
					}
					dep.ring = ring;
					
					addDependency(dep, scope);
				} else {
					throw new RuntimeException("Illegal dependency " + definition);
				}
			}
		}		
	}
	
	private void addDependency(Dependency dep, Scope scope) {
		if (!dependencies.containsKey(scope)) {
			dependencies.put(scope, new LinkedHashSet<Dependency>());
		}
		
		dependencies.get(scope).add(dep);
	}
	
	public boolean hasScope(Scope scope) {
		return dependencies.containsKey(scope);
	}
	
	public List<Scope> getScopes() {
		return new ArrayList<Scope>(dependencies.keySet());
	}
	
	public Set<Dependency> getDependencies(Scope scope) {
		return dependencies.get(scope);
	}
	
	public void setDependencies(Scope scope, Collection<Dependency> dependencies) {
		if (dependencies.size() > 0) {
			this.dependencies.put(scope, new LinkedHashSet<Dependency>(dependencies));
		} else {
			this.dependencies.remove(scope);
		}
	}
	
	void keyError(Key key) {
		System.err.println(MessageFormat.format("{0} is improperly specified, using default", key.name()));
	}
	
	public String toMaxML() {		
		StringBuilder sb = new StringBuilder();
		if (dependencies.size() > 0) {
			sb.append(MessageFormat.format("{0} :\n", Key.dependencies.name()));
			for (Map.Entry<Scope, Set<Dependency>> entry : dependencies.entrySet()) {
				for (Dependency dep : entry.getValue()) {
					// - scope ring coordinates
					sb.append(MessageFormat.format("- {0} {1,number,0} {2}\n", entry.getKey(), dep.ring, dep.getDetailedCoordinates()));
				}
			}
		}
		return sb.toString();
	}
}
