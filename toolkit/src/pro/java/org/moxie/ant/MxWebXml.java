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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.moxie.Build;
import org.moxie.Substitute;
import org.moxie.utils.StringUtils;

/**
 * Generates a web.xml from a skeleton file and a properties file.
 */
public class MxWebXml extends MxTask {
	private String PARAMS = "<!-- PARAMS -->";

	private String[] STRIP_TOKENS = { "<!-- STRIP", "STRIP -->" };

	private String COMMENT_PATTERN = "\n\t<!-- {0} -->";

	private String PARAM_PATTERN = "\n\t<context-param>\n\t\t<param-name>{0}</param-name>\n\t\t<param-value>{1}</param-value>\n\t</context-param>\n";
	
	File prototypeFile;
	
	File propertiesFile;

	File destinationFile;
	
	List<String> skips;
	
	List<Substitute> substitutions;
	
	public Substitute createReplace() {
		Substitute sub = new Substitute();
		substitutions.add(sub);
		return sub;
	}
	
	public void setSourcefile(File source) {
		this.prototypeFile = source;
	}

	public void setDestfile(File dest) {
		this.destinationFile = dest;
	}

	public void setPropertiesfile(File props) {
		this.propertiesFile = props;
	}
	
	public void setSkip(String values) {
		String [] tokens = values.split(",|\\s");
		skips.addAll(Arrays.asList(tokens));
	}

	public MxWebXml() {
		super();
		setTaskName("mx:webxml");
		skips = new ArrayList<String>();
		substitutions = new ArrayList<Substitute>();
	}
	
	public void execute() {
		Build build = getBuild();

		if (prototypeFile == null) {
			getConsole().error("Please specify a source web.xml file!");
			throw new RuntimeException();
		}

		if (destinationFile == null) {
			getConsole().error("Please specify a destination file!");
			throw new RuntimeException();
		}

		// read properties file
		StringBuilder parameters = new StringBuilder();
		if (propertiesFile != null) {
			try {
				BufferedReader propertiesReader = new BufferedReader(new FileReader(propertiesFile));

				Vector<Setting> settings = new Vector<Setting>();
				List<String> comments = new ArrayList<String>();
				String line = null;
				while ((line = propertiesReader.readLine()) != null) {
					if (line.length() == 0) {
						comments.clear();
					} else {
						if (line.charAt(0) == '#') {
							if (line.length() > 1) {
								comments.add(line.substring(1).trim());
							}
						} else {
							String[] kvp = line.split("=", 2);
							String key = kvp[0].trim();
							if (!skipKey(key)) {
								Setting s = new Setting(key, kvp[1].trim(), comments);
								settings.add(s);
							}
							comments.clear();
						}
					}
				}
				propertiesReader.close();

				for (Setting setting : settings) {
					for (String comment : setting.comments) {
						parameters.append(MessageFormat.format(COMMENT_PATTERN, comment));
					}
					parameters.append(MessageFormat.format(PARAM_PATTERN, setting.name,
							StringUtils.escapeForHtml(setting.value, false)));
				}
			} catch (Throwable t) {
				getConsole().error(t);
				throw new BuildException(t);
			}
		}

		titleClass();
		getConsole().key("source", prototypeFile.getAbsolutePath());
		if (propertiesFile != null) {
			build.getConsole().key("properties", propertiesFile.getAbsolutePath());
		}
		getConsole().key("generated", destinationFile.getAbsolutePath());
		try {
			// Read the prototype web.xml file
			char[] buffer = new char[(int) prototypeFile.length()];
			FileReader webxmlReader = new FileReader(prototypeFile);
			webxmlReader.read(buffer);
			webxmlReader.close();
			String webXmlContent = new String(buffer);

			// Insert the properties into the prototype web.xml
			for (String stripToken : STRIP_TOKENS) {
				webXmlContent = webXmlContent.replace(stripToken, "");
			}
			int idx = webXmlContent.indexOf(PARAMS);
			StringBuilder sb = new StringBuilder();
			sb.append(webXmlContent.substring(0, idx));
			sb.append(parameters.toString());
			sb.append(webXmlContent.substring(idx + PARAMS.length()));
			
			String content = sb.toString();
			for (Substitute sub : substitutions) {
				content = content.replace(sub.token, sub.value);
			}

			// Save the merged web.xml to the destination file
			FileOutputStream os = new FileOutputStream(destinationFile, false);
			os.write(content.getBytes("UTF-8"));
			os.close();
		} catch (Throwable t) {
			build.getConsole().error(t);
			throw new BuildException(t);
		}
	}

	private boolean skipKey(String key) {
		for (String skip : skips) {
			if (key.matches(skip)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Setting represents a setting and its comments from the properties file.
	 */
	private static class Setting {
		final String name;
		final String value;
		final List<String> comments;

		Setting(String name, String value, List<String> comments) {
			this.name = name;
			this.value = value;
			this.comments = new ArrayList<String>(comments);
		}
	}
}
