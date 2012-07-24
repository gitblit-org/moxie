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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.moxie.Constants.Key;
import org.moxie.utils.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


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
	public static Pom readPom(IMavenCache cache, Dependency dependency) {
		File pomFile = cache.getArtifact(dependency, Constants.POM);
		if (!pomFile.exists()) {
			return null;
		}
		return readPom(cache, pomFile);
	}
	
	/**
	 * Reads a POM file from an artifact cache.  Parent POMs will be read and
	 * applied automatically, if they exist in the cache.
	 * 
	 * @param cache
	 * @param pomFile
	 * @return
	 * @throws Exception
	 */
	public static Pom readPom(IMavenCache cache, File pomFile) {		
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
		
		Pom pom = new Pom();				
		
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
					
					if (parentPom == null) {
						// we do not have the parent pom yet likely because we
						// are in the middle of downloading so make a fake one
						// to satisfy property inheritance
						parentPom = new Pom();
						parentPom.groupId = pom.parentGroupId;
						parentPom.artifactId = pom.parentArtifactId;
						parentPom.version = pom.parentVersion;
					}
					pom.inherit(parentPom);
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
							// dependencyManagement.dependency
							Dependency dep = readDependency(node);
							Scope scope = Scope.fromString(readStringTag(node, Key.scope));
							
							if (Scope.imprt.equals(scope)) {
								// dependencyManagement import 
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
							// dependencies.dependency
							Dependency dep = readDependency(node);							
							Scope scope = Scope.fromString(readStringTag(node, Key.scope));
							pom.addDependency(dep, scope);
						}
					}
				} else if ("licenses".equalsIgnoreCase(element.getTagName())) {
					// read licenses
					// do not inherit licenses as this pom defines them
					pom.clearLicenses();
					NodeList licenses = (NodeList) element;
					for (int j = 0; j < licenses.getLength(); j++) {
						Node node = licenses.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							// licenses.license
							String name = readStringTag(node, Key.name);
							String url = readStringTag(node, Key.url);
							License license = new License(name, url);
							pom.addLicense(license);
						}
					}
				} else if ("issueManagement".equalsIgnoreCase(element.getTagName())) {
					// extract the issue tracker url
					pom.issuesUrl = readStringTag(element, Key.url);
				} else if ("groupId".equalsIgnoreCase(element.getTagName())) {
					// extract the groupId
					pom.groupId = readStringTag(element);
				} else if ("artifactId".equalsIgnoreCase(element.getTagName())) {
					// extract the artifactId
					pom.artifactId = readStringTag(element);
				} else if ("version".equalsIgnoreCase(element.getTagName())) {
					// extract the version
					pom.version = readStringTag(element);
				} else if ("packaging".equalsIgnoreCase(element.getTagName())) {
					// extract the packaging
					pom.packaging = readStringTag(element);
				} else if ("name".equalsIgnoreCase(element.getTagName())) {
					// extract the name
					pom.name = readStringTag(element);
				} else if ("description".equalsIgnoreCase(element.getTagName())) {
					// extract the description
					pom.description = readStringTag(element);
				} else if ("url".equalsIgnoreCase(element.getTagName())) {
					// extract the url
					pom.url = readStringTag(element);
				} else if ("organization".equalsIgnoreCase(element.getTagName())) {
					// extract the organization name
					pom.organization = readStringTag(element, Key.name);
				}
			}
		}
		pom.resolveProperties();
		return pom;
	}
	
	private static Dependency readDependency(Node node) {
		Dependency dep = new Dependency();
		dep.groupId = readStringTag(node, Key.groupId);
		dep.artifactId = readStringTag(node, Key.artifactId);
		dep.version = readStringTag(node, Key.version);
		dep.classifier = readStringTag(node, Key.classifier);
		dep.type = readStringTag(node, Key.type);
		dep.optional = readBooleanTag(node, Key.optional);
		dep.exclusions.addAll(readExclusions(node));
		return dep;
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
	
	private static String readStringTag(Node node) {
		if (node == null) {
			return null;
		}
		Node tagElement = node.getFirstChild();
		if (tagElement == null) {
			return null;
		}
		String content = tagElement.getTextContent();
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
