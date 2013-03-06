/*
 * Copyright 2013 James Moger
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
package org.moxie.ant;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.DefaultLogger;
import org.moxie.ArtifactVersion;
import org.moxie.ArtifactVersion.NumberField;
import org.moxie.Constants;
import org.moxie.MoxieException;
import org.moxie.Substitute;
import org.moxie.Toolkit;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * MxVersion is used to build a release target where you want to publish a release
 * or to reset for the next development cycle.  It is something like the Maven
 * versions plugin.
 * 
 * @author James Moger
 *
 */
public class MxVersion extends MxTask {

	enum Stage {
		release, snapshot;
	}

	Stage stage;
	NumberField incrementNumber;
	File moxieFile;
	File releaseLog;
	boolean dryrun;

	public MxVersion() {
		super();
		setTaskName("mx:version");
	}
	
	public void setStage(String value) {
		for (Stage stage : Stage.values()) {
			if (value.equalsIgnoreCase(stage.name())) {
				this.stage = stage;
				break;
			}
		}
		if (stage ==  null) {
			throw new MoxieException(MessageFormat.format("Illegal stage {0}", value));
		}
	}
	
	public void setIncrementNumber(String value) {
		for (NumberField number : NumberField.values()) {
			if (value.equalsIgnoreCase(number.name())) {
				this.incrementNumber = number;
				break;
			}
		}
		if (stage ==  null) {
			throw new MoxieException(MessageFormat.format("Illegal stage {0}", value));
		}
	}
	
	public void setReleaselog(File file) {
		releaseLog = file;
	}
	
	public void setDryrun(boolean value) {
		this.dryrun = value;
	}

	@Override
	public void execute() {
		if (stage == null) {
			stage = Stage.release;
		}
		
		if (moxieFile == null) {
			moxieFile = new File(getProject().getBaseDir(), "build.moxie");
		}
		
		if (releaseLog == null) {
			releaseLog = new File(getProject().getBaseDir(), "releases.moxie");
		}
		
		MaxmlMap map = null;
		try {
			map = Maxml.parse(moxieFile);
		} catch (MaxmlException e) {
			throw new MoxieException(e);
		}

		String groupId = map.getString(Toolkit.Key.groupId.name(), "");
		String artifactId = map.getString(Toolkit.Key.artifactId.name(), "");
		String projectName = map.getString(Toolkit.Key.name.name(), null);
		if (StringUtils.isEmpty(projectName)) {
			projectName = groupId + ":" + artifactId;
		}

		String version;
		String releaseVersion = map.getString(Toolkit.Key.releaseVersion.name(), "");
		
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date releaseDate = map.getDate(Toolkit.Key.releaseDate.name(), null);
		String releaseDateStr = releaseDate == null ? null : df.format(releaseDate);
		Date buildDate = new Date();
		String buildDateStr = buildDate == null ? null : df.format(buildDate);
		
		ArtifactVersion artifactVersion = new ArtifactVersion(
				map.getString(Constants.Key.version.name(), "0.0.0-SNAPSHOT"));
		
		Map<String, String> properties = new HashMap<String, String>();
		List<Substitute> replacements = new ArrayList<Substitute>();
		switch (stage) {
		case release:
			if (artifactVersion.isSnapshot()) {
				// update development snapshot info to release info
				releaseVersion = artifactVersion.setSnapshot(false).toString();
			} else {
				// preserve major.minor.incremental, increment build number
				releaseVersion = artifactVersion.incrementBuildNumber().toString();
			}
			title("Preparing RELEASE", releaseVersion);
			version = releaseVersion;
			releaseDate = buildDate;
			releaseDateStr = buildDateStr;
			replacements.add(new Substitute(Toolkit.Key.version.name(), releaseVersion));
			replacements.add(new Substitute(Toolkit.Key.releaseVersion.name(), releaseVersion));
			replacements.add(new Substitute(Toolkit.Key.releaseDate.name(), releaseDateStr));
			updateDescriptor(replacements);
			
			// update release log
			properties.put(Toolkit.Key.name.projectId(), projectName);
			properties.put(Toolkit.Key.version.projectId(), releaseVersion);
			properties.put(Toolkit.Key.buildDate.projectId(), releaseDateStr);
			properties.put(Toolkit.Key.releaseVersion.projectId(), releaseVersion);
			properties.put(Toolkit.Key.releaseDate.projectId(), releaseDateStr);
			updateReleaseLog(stage, releaseLog, properties);
			break;
		case snapshot:
			// start a new minor version SNAPSHOT development cycle
			if (artifactVersion.isSnapshot()) {
				throw new MoxieException("The current version {0} is already a SNAPSHOT!", artifactVersion.toString());
			}
			artifactVersion.setSnapshot(true);
			if (incrementNumber == null) {
				// increment minor version, if unspecified
				incrementNumber = NumberField.minor;
			}
			switch (incrementNumber) {
			case major:
				artifactVersion.incrementMajorVersion();
				break;
			case minor:
				artifactVersion.incrementMinorVersion();
				break;
			case incremental:
				artifactVersion.incrementIncrementalVersion();
				break;
			default:
				artifactVersion.incrementMinorVersion();
				break;
			}
			
			version = artifactVersion.toString();
			title("Preparing SNAPSHOT", version);
			replacements.add(new Substitute(Toolkit.Key.version.name(), version));
			updateDescriptor(replacements);

			// update release log
			updateReleaseLog(stage, releaseLog, properties);
			break;
		default:
			throw new MoxieException("Unknown stage \"{0}\"", stage);
		}
		
		// update Ant project properties
		getProject().setProperty(Toolkit.Key.name.projectId(), projectName);
		getProject().setProperty(Toolkit.Key.groupId.projectId(), groupId);
		getProject().setProperty(Toolkit.Key.artifactId.projectId(), artifactId);
		getProject().setProperty(Toolkit.Key.version.projectId(), version);
		getProject().setProperty(Toolkit.Key.releaseVersion.projectId(), releaseVersion);
		getProject().setProperty(Toolkit.Key.releaseDate.projectId(), releaseDateStr);
		getProject().setProperty(Toolkit.Key.buildDate.projectId(), buildDateStr);
		
		if (isShowTitle()) {
			// 3 is for "[] "
			setConsoleOffset(getTaskName().length() + 3 - DefaultLogger.LEFT_COLUMN_SIZE);
		}
		
		// share these paths for consumption by another task (e.g. mx:Commit)
		if (releaseLog.exists()) {
			sharePaths(moxieFile.getAbsolutePath(), releaseLog.getAbsolutePath());
		} else {
			sharePaths(moxieFile.getAbsolutePath());
		}
	}
	
	
	/**
	 * Updates the Moxie descriptor with the specified key:value pairs.
	 * 
	 * @param replacements
	 */
	protected void updateDescriptor(List<Substitute> replacements) {
		String content = FileUtils.readContent(moxieFile, "\n");
		StringBuilder sb = new StringBuilder();
		String [] lines = content.split("\n");
		for (String line : lines) {
			String trimmed = line.trim();
			for (Substitute replacement : replacements) {				
				if (trimmed.startsWith(replacement.token)) {
					int start = line.indexOf(replacement.token) + replacement.token.length();
					int colon = line.indexOf(':', start) + 1;
					line = line.substring(0, colon) + " " + replacement.value;
				}
			}
			sb.append(line).append('\n');
		}
		
		if (dryrun) {
			System.out.println(sb.toString());
		} else {
			FileUtils.writeContent(moxieFile, sb.toString());
		}
	}
	
	/**
	 * Updates the release log.
	 * 
	 * @param replacements
	 */
	protected void updateReleaseLog(Stage stage, File file, Map<String, String> properties) {
		if (!file.exists()) {
			return;
		}
		
		int currentRelease = 0;
		String releaseBase = "r";
		
		// identify the releaseBase, current release, and conditionally resolve properties
		StringBuilder sb = new StringBuilder();
		String content = FileUtils.readContent(file, "\n");
		for (String line : content.split("\n")) {
			
			if (Stage.release.equals(stage)) {
				// resolve properties for a release
				line = resolveProperties(line, properties);
			}

			// identify the current release and the releaseBase
			if (currentRelease == 0 && line.matches(getFieldRegex("release"))) {
				Pattern p = Pattern.compile(getObjectRegex("release"));
				Matcher m = p.matcher(line);
				if (m.find()) {
					releaseBase = m.group(1);
					currentRelease = Integer.parseInt(m.group(2));
				}
			}
			sb.append(line).append('\n');
		}
		
		content = sb.toString();
		sb.setLength(0);

		// update snapshot and releases
		// we do this in a separate loop in case the order of the keys is different
		if (Stage.release.equals(stage)) {
			// RELEASE
			int newRelease = currentRelease + 1;
			boolean updatedRelease = false;
			boolean updatedSnapshot = false;
			boolean updatedReleases = false;
			for (String line : content.split("\n")) {
				// update 
				if (line.matches(getFieldRegex("release"))) {
					// advance release number
					int idx = line.indexOf(':') + 1;
					String newline = line.substring(0, idx);
					line = newline + " &" + releaseBase + newRelease;
					updatedRelease = true;
				} else if (line.matches(getFieldRegex("snapshot"))) {
					// set snapshot to null
					int idx = line.indexOf(':') + 1;
					String newline = line.substring(0, idx);
					line = newline + " ~";
					updatedSnapshot = true;
				} else if (line.matches(getFieldRegex("releases"))) {
					// update releases array
					int idx = line.indexOf(':') + 1;
					String newline = line.substring(0, idx);
					line = newline + " &" + releaseBase + "[1.." + newRelease + "]";
					updatedReleases = true;
				}
				sb.append(line).append('\n');
			}
			if (!updatedRelease) {
				// insert release field
				sb.append("release: &" + releaseBase + newRelease).append('\n');
			}
			if (!updatedSnapshot) {
				// insert snapshot field
				sb.append("snapshot: ~\n");
			}
			if (!updatedReleases) {
				// insert releases field
				sb.append("releases:  &" + releaseBase + "[1.." + newRelease + "]").append('\n');
			}			
		} else if (Stage.snapshot.equals(stage)) {
			// RESET for development
			int snapshotRelease = currentRelease + 1;
			// insert log template for next release
			String indent = "    ";
			sb.append("#\n# ${project.version} release\n#\n");
			sb.append(releaseBase + snapshotRelease + ": {\n");
			sb.append(indent).append("title").append(": ${project.name} ${project.version} released\n");
			sb.append(indent).append("id").append(": ${project.version}\n");
			sb.append(indent).append("date").append(": ${project.buildDate}\n");
			sb.append(indent).append("note").append(": ~\n");
			sb.append(indent).append("text").append(": ~\n");
			sb.append(indent).append("security").append(": ~\n");
			sb.append(indent).append("fixes").append(": ~\n");
			sb.append(indent).append("changes").append(": ~\n");
			sb.append(indent).append("additions").append(": ~\n");
			sb.append(indent).append("dependencyChanges").append(": ~\n");
			sb.append(indent).append("contributors").append(": ~\n");
			sb.append("}\n\n");
			
			// update the snapshot field to point to the inserted template
			boolean updatedSnapshot = false;
			for (String line : content.split("\n")) {
				if (line.matches(getFieldRegex("snapshot"))) {
					int idx = line.indexOf(':') + 1;
					String newline = line.substring(0, idx);
					line = newline + " &" + releaseBase + snapshotRelease;
					updatedSnapshot= true;
				}
				sb.append(line).append('\n');
			}
			
			if (!updatedSnapshot) {
				// insert snapshot field
				sb.append("snapshot: &" + releaseBase + snapshotRelease).append('\n');
			}
		}
		
		if (dryrun) {
			System.out.println(sb.toString());
		} else {
			FileUtils.writeContent(file, sb.toString());
		}
	}
	
	protected String getFieldRegex(String name) {
		return "^\\s*(?:'|\")?" + name + "(?:'|\")?\\s*:.*";
	}
	
	protected String getObjectRegex(String name) {
		return "^\\s*(?:'|\")?" + name + "(?:'|\")?\\s*:\\s*&([a-zA-Z]+)(\\d+)";
	}
	
	protected String resolveProperties(String string, Map<String, String> properties) {
		if (string == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9-_\\.]+\\}");			
		StringBuilder sb = new StringBuilder(string);
		int start = 0;
		while (true) {
			Matcher m = p.matcher(sb.toString());
			if (m.find(start)) {
				String prop = m.group();
				prop = prop.substring(2, prop.length() - 1);
				String value = getProperty(prop, properties);
				if (value.equals(prop)) {
					// leave property intact, it will stand out
					start = m.end();
					continue;
				}
				sb.replace(m.start(), m.end(), value);
				start = m.start() + value.length();
			} else {
				return sb.toString();
			}
		}		
	}
	
	protected String getProperty(String key, Map<String, String> properties) {
		String value = key;
		if (properties.containsKey(key)) {
			value = properties.get(key);
		}
		return value;
	}
}
