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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.maxtk.Constants.Key;
import com.maxtk.Dependency.Scope;
import com.maxtk.utils.StringUtils;

public class PomReader {

	/**
	 * Reads a POM file from an artifact cache.  Parent POMs will be read and
	 * applied automatically, if they exist in the cache.
	 * 
	 * @param cache
	 * @param dependency
	 * @return
	 * @throws Exception
	 */
	public static Pom readPom(ArtifactCache cache, Dependency dependency) {
		File pomFile = cache.getFile(dependency, Dependency.EXT_POM);
		if (!pomFile.exists()) {
			return null;
		}
		
		Pom pom = new Pom();
		pom.groupId = dependency.group;
		pom.artifactId = dependency.artifact;
		pom.version = dependency.version;
		
		Document doc = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(pomFile);
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
				
		Element docElement = doc.getDocumentElement();
		pom.name = readStringTag(docElement, Key.name);
		pom.description = readStringTag(docElement, Key.description);
		pom.url = readStringTag(docElement, Key.url);
		pom.vendor = readStringTag(docElement, Key.vendor);
		
		pom.setProperty("project.groupId", pom.groupId);
		pom.setProperty("project.artifactId", pom.artifactId);
		pom.setProperty("project.version", pom.version);
		
		NodeList projectNodes = docElement.getChildNodes();
		for (int i = 0; i < projectNodes.getLength(); i++) {
			Node pNode = projectNodes.item(i);
			if (pNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) pNode;				
				if ("parent".equalsIgnoreCase(element.getTagName())) {
					// parent properties	
					pom.parentGroupId = readStringTag(pNode, Key.groupId);
					pom.parentArtifactId = readStringTag(pNode, Key.artifactId);
					pom.parentVersion = readStringTag(pNode, Key.version);
					
					// read parent pom
					Dependency parent = pom.getParentDependency();
					Pom parentPom = readPom(cache, parent);
					if (parentPom != null) {
						pom.inherit(parentPom);
					}					
				} else if ("properties".equalsIgnoreCase(element.getTagName())) {
					// pom properties
					NodeList properties = (NodeList) element;
					for (int j = 0; j < properties.getLength(); j++) {
						Node node = properties.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							String property = node.getNodeName();							
							if (node.getFirstChild() != null) {							
								pom.setProperty(property, node.getFirstChild().getNodeValue());
							}
						}						
					}
				} else if ("dependencyManagement".equalsIgnoreCase(element.getTagName())) {
					// dependencyManagement definitions
					NodeList dependencies = element.getElementsByTagName("dependency");
					for (int j = 0, jlen = dependencies.getLength(); j < jlen; j++) {
						Node node = dependencies.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							Dependency dep = new Dependency();
							dep.group = readStringTag(node, Key.groupId);
							dep.artifact = readStringTag(node, Key.artifactId);
							dep.version = readStringTag(node, Key.version);
							Scope scope = Scope.fromString(readStringTag(node, Key.scope));
							
							// artifact extension
							String type = readStringTag(node, Key.type);
							if (!StringUtils.isEmpty(type)) {
								dep.ext = "." + type;
							}
							
							// substitute version property
							dep.version = pom.getProperty(dep.version);
							
							// dependencyManagement import 
							if (Scope.imprt.equals(scope)) {
								Pom importPom = readPom(cache, dep);
								if (importPom != null) {
									pom.importManagedDependencies(importPom);
								}					
							} else {
								// add dependency management definition
								pom.addManagedDependency(dep, scope);
							}
						}
					}
				} else if ("dependencies".equalsIgnoreCase(element.getTagName())) {
					// read dependencies
					NodeList dependencies = (NodeList) element;
					for (int j = 0; j < dependencies.getLength(); j++) {
						Node node = dependencies.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							Dependency dep = new Dependency();
							dep.group = readStringTag(node, Key.groupId);
							dep.artifact = readStringTag(node, Key.artifactId);
							dep.version = readStringTag(node, Key.version);							
							dep.optional = readBooleanTag(node, Key.optional);
							
							// substitute group property
							dep.group = pom.getProperty(dep.group);
							
							if (StringUtils.isEmpty(dep.version)) {
								// try retrieving version from parent pom
								dep.version = pom.getManagedVersion(dep);
								if (StringUtils.isEmpty(dep.version) && !pom.hasParentDependency()) {
									System.err.println(MessageFormat.format("{0} dependency {1} does not specify a version!", pom, dep.getProjectId()));
								}
							}

							// substitute version property
							dep.version = pom.getProperty(dep.version);

							// determine scope
							Scope scope = Scope.fromString(readStringTag(node, Key.scope));
							if (scope == null) {
								// try retrieving scope from parent pom
								scope = pom.getManagedScope(dep);
								
								if (scope == null) {
									// scope is still undefined, use default
									scope = Scope.defaultScope;
								}
							}
							
							// artifact extension
							String type = readStringTag(node, Key.type);
							if (!StringUtils.isEmpty(type)) {
								dep.ext = "." + type;
							}
							
							// read exclusions
							dep.exclusions.addAll(readExclusions(node));
							
							// add dep object
							pom.addDependency(dep, scope);
						}
					}
				}
			}
		}
		return pom;
	}
	
	private static String readStringTag(Node node, Key tag) {
		Element element = (Element) node;
		NodeList tagList = element.getElementsByTagName(tag.name());
		if (tagList == null || tagList.getLength() == 0) {
			return null;
		}
		Element tagElement = (Element) tagList.item(0);
		NodeList textList = tagElement.getChildNodes();
		Node itemNode = textList.item(0);
		if (itemNode == null) {
			return null;
		}
		String content = itemNode.getNodeValue().trim();
		return content;
	}

	private static boolean readBooleanTag(Node node, Key tag) {
		String content = readStringTag(node, tag);
		if (StringUtils.isEmpty(content)) {
			return false;
		}
		return Boolean.parseBoolean(content);
	}
	
	private static Collection<String> readExclusions(Node node) {
		Set<String> exclusions = new LinkedHashSet<String>();
		Element element = (Element) node;
		NodeList exclusionList = element.getElementsByTagName("exclusion");
		if (exclusionList == null || exclusionList.getLength() == 0) {
			return exclusions;
		}
		
		for (int i = 0; i < exclusionList.getLength(); i++) {
			Node exclusionNode = exclusionList.item(i);
			String groupId = readStringTag(exclusionNode, Key.groupId);
			String artifactId = readStringTag(exclusionNode, Key.artifactId);
			if (StringUtils.isEmpty(artifactId)) {
				// group exclusion
				exclusions.add(groupId);
			} else {
				// artifact exclusion
				exclusions.add(groupId + ":" + artifactId);
			}
		}
		return exclusions;
	}
}
