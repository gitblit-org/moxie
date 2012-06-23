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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.tools.ant.filters.StringInputStream;
import org.moxie.Constants.Key;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class MetadataReader {

	/**
	 * Reads a maven-metadata.xml file from an artifact cache.
	 * 
	 * @param cache
	 * @param dependency
	 * @return
	 * @throws Exception
	 */
	public static Metadata readMetadata(File metadataFile) {
		if (!metadataFile.exists()) {
			return null;
		}
				
		Document doc = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(metadataFile);
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return parse(doc);
	}
	
	public static Metadata readMetadata(String content) {
		Document doc = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(new StringInputStream(content, "UTF-8"));
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return parse(doc);
	}
	
	private static Metadata parse(Document doc) {
		Metadata metadata = new Metadata();

		Element docElement = doc.getDocumentElement();		
		metadata.groupId = readStringTag(docElement, Key.groupId.name());
		metadata.artifactId = readStringTag(docElement, Key.artifactId.name());		
		metadata.version = readStringTag(docElement, Key.version.name());

		NodeList projectNodes = docElement.getChildNodes();
		for (int i = 0; i < projectNodes.getLength(); i++) {
			Node pNode = projectNodes.item(i);
			if (pNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) pNode;				
				if ("versioning".equalsIgnoreCase(element.getTagName())) {
					metadata.latest = readStringTag(pNode, Key.latest.name());
					metadata.release = readStringTag(pNode, Key.release.name());
					
					NodeList snapshots = element.getElementsByTagName("snapshot");
					if (snapshots != null) {
						for (int j = 0, jlen = snapshots.getLength(); j < jlen; j++) {
							Node node = snapshots.item(j);						
							String timestamp = readStringTag(node, "timestamp");
							String buildNumber = readStringTag(node, "buildNumber");						
							metadata.addSnapshot(timestamp, buildNumber);
						}
					}
					
					String lastUpdated = readStringTag(pNode, Key.lastUpdated.name());
					metadata.setLastUpdated(lastUpdated);
					
					NodeList versions = element.getElementsByTagName(Key.version.name());
					if (versions != null) {
						for (int j = 0, jlen = versions.getLength(); j < jlen; j++) {
							Node node = versions.item(j);						
							metadata.addVersion(node.getFirstChild().getTextContent());
						}
					}
				}
			}
		}
		return metadata;
	}
	
	private static String readStringTag(Node node, String tag) {
		Element element = (Element) node;
		NodeList tagList = element.getElementsByTagName(tag);
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
}
