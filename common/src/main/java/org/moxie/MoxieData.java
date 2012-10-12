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
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import org.moxie.Constants.Key;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * Caches Moxie information about an artifact.
 */
public class MoxieData implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static final int currentSolutionVersion = 1;
	
	private final File file;

	private final Map<Scope, Set<Dependency>> dependencies;

	private int solutionVersion;

	private Date lastDownloaded;
	
	private Date lastChecked;
	
	private Date lastUpdated;
	
	private Date lastSolved;
	
	private String origin;
	
	private String groupId;
	
	private String artifactId;
	
	private String version;
	
	private String revision;
	
	private String release;
	
	private String latest;

	public MoxieData(File file) {
		this.file = file;
		this.dependencies = new TreeMap<Scope, Set<Dependency>>();
		this.lastDownloaded = new Date(0);
		this.lastChecked = new Date(0);
		this.lastUpdated = new Date(0);
		this.lastSolved = new Date(0);		
		try {
			parse();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "MoxieData (" + file + ")";
	}

	MoxieData parse() throws IOException, MaxmlException {

		MaxmlMap map = new MaxmlMap();
		if (file.exists()) {
			String content = FileUtils.readContent(file, "\n").trim();
			map = Maxml.parse(content);
		}
		parseDependencies(map, Key.dependencies);		
		lastDownloaded = parseDate(map, Key.lastDownloaded, lastDownloaded);
		lastChecked = parseDate(map, Key.lastChecked, lastChecked);
		lastUpdated = parseDate(map, Key.lastUpdated, lastUpdated);
		lastSolved = parseDate(map, Key.lastSolved, lastSolved);
		origin = map.getString(Key.origin.name(), null);
		solutionVersion = map.getInt("solutionVersion", 0);
		return this;
	}
	
	Date parseDate(MaxmlMap map, Key key, Date defaultValue) {
		String date = map.getString(key.name(), null);
		if (!StringUtils.isEmpty(date)) {
			try {
				Date aDate = getDateFormat().parse(date);
				// reset milliseconds to we compare to the second 
				Calendar c = Calendar.getInstance();
				c.setTime(aDate);
				c.set(Calendar.MILLISECOND, 0);				
				return c.getTime();
			} catch (ParseException e) {
			}
		}
		return defaultValue;
	}

	void parseDependencies(MaxmlMap map, Key key) {
		if (map.containsKey(key.name())) {
			List<?> values = (List<?>) map.get(key.name());			
			for (Object definition : values) {
				if (definition instanceof String) {
					String [] fields = definition.toString().split(" ");
					Scope solutionScope = Scope.fromString(fields[0]);
					int ring = Integer.parseInt(fields[1]);
					String def = StringUtils.stripQuotes(fields[2].trim());
					Scope dependencyScope;
					if (fields.length > 3) {
						dependencyScope = Scope.fromString(fields[3]);
					} else {
						// for backwards-compatibility, even though it is incorrect
						dependencyScope = solutionScope;
					}
					
					Dependency dep;
					if (Scope.system.equals(solutionScope)) {
						dep = new SystemDependency(def);
					} else {
						dep = new Dependency(def);
					}
					dep.ring = ring;
					dep.definedScope = dependencyScope;
					
					addDependency(dep, solutionScope);
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
	
	public void setArtifact(Dependency dep) {
		this.groupId = dep.groupId;
		this.artifactId = dep.artifactId;
		this.version = dep.version;
		this.revision = dep.revision;
	}

	public Date getLastDownloaded() {
		return lastDownloaded;
	}

	public void setLastDownloaded(Date date) {
		this.lastDownloaded = date;
	}
	
	public Date getLastChecked() {
		return lastChecked;
	}

	public void setLastChecked(Date date) {
		this.lastChecked = date;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Date date) {
		this.lastUpdated = date;
	}
	
	public Date getLastSolved() {
		return lastSolved;
	}
	
	public void setLastSolved(Date date) {
		this.lastSolved = date;
	}
	
	public String getOrigin() {
		return origin;
	}
	
	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getRELEASE() {
		return release;
	}
	
	public void setRELEASE(String release) {
		this.release = release;
	}

	public String getLATEST() {
		return latest;
	}
	
	public void setLATEST(String latest) {
		this.latest = latest;
	}

	public String getRevision() {
		return revision;
	}
	
	public boolean isValidSolution() {
		return solutionVersion == currentSolutionVersion;
	}
	
	public boolean isRefreshRequired() {
		return lastUpdated.after(lastDownloaded);
	}

	private String kvp(Object key, String value) {
		if (StringUtils.isEmpty(value)) {
			return "";
		}
		return MessageFormat.format("{0} : {1}\n", key, value);
	}

	private String kvp(Object key, int value) {
		return MessageFormat.format("{0} : {1,number,0}\n", key, value);
	}

	private String kvp(Object key, Date value) {
		if (value == null || value.getTime() == 0) {
			return "";
		}
		DateFormat df = getDateFormat();
		return MessageFormat.format("{0} : {1}\n", key, df.format(value));
	}

	public String toMaxML() {
		StringBuilder sb = new StringBuilder();
		DateFormat df = getDateFormat();
		sb.append(MessageFormat.format("# Moxie data generated {0}\n", df.format(new Date())));
		sb.append("\n# artifact metadata\n");
		sb.append(kvp(Key.groupId, groupId));
		sb.append(kvp(Key.artifactId, artifactId));
		sb.append(kvp(Key.version, version));
		sb.append(kvp(Key.revision, revision));
		sb.append(kvp(Key.release, release));
		sb.append(kvp(Key.latest, latest));
		
		sb.append("\n# Moxie metadata\n");
		sb.append(kvp(Key.solutionVersion, currentSolutionVersion));
		sb.append(kvp(Key.origin, origin));
		sb.append(kvp(Key.lastDownloaded, lastDownloaded));
		sb.append(kvp(Key.lastChecked, lastChecked));
		sb.append(kvp(Key.lastUpdated, lastUpdated));
		sb.append(kvp(Key.lastSolved, lastSolved));
		
		sb.append("\n# transitive solution\n");
		if (dependencies.size() > 0) {
			sb.append(MessageFormat.format("{0} :\n", Key.dependencies.name()));
			for (Map.Entry<Scope, Set<Dependency>> entry : dependencies.entrySet()) {
				for (Dependency dep : entry.getValue()) {
					// - solutionScope ring coordinates dependencyScope
					sb.append(MessageFormat.format("- {0} {1,number,0} {2} {3}\n", entry.getKey(), dep.ring, dep.getDetailedCoordinates(), dep.definedScope));
				}
			}
		}
		return sb.toString();
	}

	private DateFormat getDateFormat() {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df;
	}
}
