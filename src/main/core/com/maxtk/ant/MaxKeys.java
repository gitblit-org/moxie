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
package com.maxtk.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.tools.ant.Task;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.utils.StringUtils;

public class MaxKeys extends Task {
	
	File propertiesFile;
	
	String className;
	
	File outputFolder;
	
	public void setPropertiesfile(File file) {
		this.propertiesFile = file;
	}
	
	public void setOutputclass(String className) {
		this.className = className;
	}

	public void setOutputfolder(File outputFolder) {
		this.outputFolder = outputFolder;
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		
		if (outputFolder == null) {
			build.console.error("Please specify an output folder!");
			throw new RuntimeException();
		}

		if (className == null) {
			build.console.error("Please specify an output classname!");
			throw new RuntimeException();
		}
		
		if (propertiesFile == null) {
			build.console.error("Please specify an input properties file!");
			throw new RuntimeException();
		}

		// Load all keys
		Properties properties = new Properties();
		FileInputStream is = null;
		try {
			is = new FileInputStream(propertiesFile);
			properties.load(is);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (Throwable t) {
					// IGNORE
				}
			}
		}
		List<String> keys = new ArrayList<String>(properties.stringPropertyNames());
		Collections.sort(keys);

		KeyGroup root = new KeyGroup();
		for (String key : keys) {
			root.addKey(key);
		}
		
		// Save Keys class definition
		try {
			File file = new File(outputFolder, className.replace('.', '/') + ".java");
			file.getParentFile().mkdirs();
			FileWriter fw = new FileWriter(file, false);
			fw.write(root.generateClass(className));
			fw.close();
			build.console.header();
			build.console.title("MaxKeys", className);
			build.console.subheader();
			build.console.log("{0} generated from {1}", file, propertiesFile);
		} catch (Throwable t) {
			build.console.error(t);
		}
	}
	
	private static class KeyGroup {
		final KeyGroup parent;
		final String namespace;
		
		String name;
		List<KeyGroup> children;		
		List<String> fields;		
		
		KeyGroup() {
			this.parent = null;
			this.namespace = "";
			this.name = "";	
		}
		
		KeyGroup(String namespace, KeyGroup parent) {
			this.parent = parent;
			this.namespace = namespace;
			if (parent.children == null) {
				parent.children = new ArrayList<KeyGroup>();
			}
			parent.children.add(this);
		}
		
		void addKey(String key) {
			String keyspace = "";
			String field = key;
			if (key.indexOf('.') > -1) {
				keyspace = key.substring(0, key.lastIndexOf('.'));
				field = key.substring(key.lastIndexOf('.') + 1);
				KeyGroup group = addKeyGroup(keyspace);
				group.addKey(field);
			} else {
				if (fields == null) {
					fields = new ArrayList<String>();
				}
				fields.add(key);
			}
		}
				
		KeyGroup addKeyGroup(String keyspace) {
			KeyGroup parent = this;
			KeyGroup node = null;			
			String [] space = keyspace.split("\\.");
			for (int i = 0; i < space.length; i++) {
				StringBuilder namespace = new StringBuilder();
				for (int j = 0; j <= i; j++) {
					namespace.append(space[j]);
					if (j < i) {
						namespace.append('.');
					}
				}
				if (parent.children != null) {
					for (KeyGroup child : parent.children) {
						if (child.name.equals(space[i])) {
							node = child;					
						}
					}
				}
				if (node == null) {
					node = new KeyGroup(namespace.toString(), parent);
					node.name = space[i];
				}
				parent = node;
				node = null;
			}
			return parent;
		}		
		
		String fullKey(String field) {
			if (namespace.equals("")) {
				return field;
			}
			return namespace + "." + field;
		}
		
		String generateClass(String fqn) {
			String packageName = "";
			String className = fqn;
			if (fqn.indexOf('.') > -1) {
				packageName = fqn.substring(0, fqn.lastIndexOf('.'));
				className = fqn.substring(fqn.lastIndexOf('.') + 1);
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append("package ").append(packageName).append(";\n");
			sb.append('\n');
			sb.append("/*\n");
			sb.append(" * This class is auto-generated from a properties file.\n");
			sb.append(" * Do not version control!\n");
			sb.append(" */\n");
			sb.append(MessageFormat.format("public final class {0} '{'\n\n", className));
			sb.append(generateClass(this, 0));
			sb.append("}\n");
			return sb.toString();
		}
		
		String generateClass(KeyGroup group, int level) {
			String classIndent = StringUtils.leftPad("", level, '\t');
			String fieldIndent = StringUtils.leftPad("", level + 1, '\t');
			
			// begin class
			StringBuilder sb = new StringBuilder();
			if (!group.namespace.equals("")) {
				sb.append(classIndent).append(MessageFormat.format("public static final class {0} '{'\n\n", group.name));
				sb.append(fieldIndent).append(MessageFormat.format("public static final String _ROOT = \"{0}\";\n\n", group.namespace));
			}
			
			if (group.fields != null) {
				// fields
				for (String field : group.fields) {					
					sb.append(fieldIndent).append(MessageFormat.format("public static final String {0} = \"{1}\";\n\n", field, group.fullKey(field)));
				}
			}
			if (group.children != null) {
				// inner classes
				for (KeyGroup child : group.children) {
					sb.append(generateClass(child, level + 1));
				}
			}
			// end class
			if (!group.namespace.equals("")) {
				sb.append(classIndent).append("}\n\n");
			}
			return sb.toString();			
		}
	}
}
