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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class SettingsReader {

	/**
	 * Reads a settings.xml file from the user's home folder.
	 * 
	 * @param cache
	 * @param dependency
	 * @return
	 * @throws Exception
	 */
	public static Settings readSettings(File settingsFile) {
		if (!settingsFile.exists()) {
			return null;
		}
				
		Document doc = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(settingsFile);
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return parse(doc);
	}
	
	public static Settings readSettings(String content) {
		Document doc = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
			doc.getDocumentElement().normalize();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return parse(doc);
	}
	
	private static Settings parse(Document doc) {
		Settings settings = new Settings();

		Element docElement = doc.getDocumentElement();		

		NodeList projectNodes = docElement.getChildNodes();
		for (int i = 0; i < projectNodes.getLength(); i++) {
			Node pNode = projectNodes.item(i);
			if (pNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) pNode;				
				if ("proxies".equalsIgnoreCase(element.getTagName())) {
					
					NodeList proxies = element.getElementsByTagName("proxy");
					if (proxies != null) {
						for (int j = 0, jlen = proxies.getLength(); j < jlen; j++) {
							Node node = proxies.item(j);

							Proxy proxy = new Proxy();
							proxy.active = Boolean.parseBoolean(readStringTag(node, "active", "true"));
							proxy.protocol = readStringTag(node, "protocol", "http");
							proxy.host = readStringTag(node, "host", "");
							proxy.port = Integer.parseInt(readStringTag(node, "port", "80"));
							proxy.username = readStringTag(node, "username", "");
							proxy.password = readStringTag(node, "password", "");
							proxy.nonProxyHosts = Arrays.asList(readStringTag(node, "nonProxyHosts", "").split("|"));

							settings.addProxy(proxy);
						}
					}
				}
			}
		}
		return settings;
	}
	
	private static String readStringTag(Node node, String tag, String defaultValue) {
		String value = readStringTag(node, tag);
		if (value == null) {
			return defaultValue;			
		}
		return value;
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
