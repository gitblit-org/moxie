package com.maxtk;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.maxtk.utils.StringUtils;

public class PomReader {

	public static void main(String[] args) throws Exception {
		File[] files = new File("ext").listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isFile() && file.getName().endsWith(".pom");
			}
		});

		for (File file : files) {
			List<PomDep> deps = readDependencies(file);
			System.out.println(file.getName());
			if (deps.size() == 0) {
				System.out.println("  none");
			} else {
				for (PomDep dep : deps) {
					System.out.println("  " + dep);
				}
			}
		}
	}

	public static List<PomDep> readDependencies(File file) throws Exception {
		List<PomDep> deps = new ArrayList<PomDep>();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(file);
		doc.getDocumentElement().normalize();
		NodeList projectNodes = doc.getDocumentElement().getChildNodes();
		for (int i = 0; i < projectNodes.getLength(); i++) {
			Node pNode = projectNodes.item(i);
			if (pNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) pNode;
				if ("dependencies".equals(element.getTagName())) {
					NodeList dependencies = (NodeList) element;
					for (int j = 0; j < dependencies.getLength(); j++) {
						Node node = dependencies.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE) {
							PomDep dep = new PomDep();
							dep.groupId = readStringTag(node, "groupId");
							dep.artifactId = readStringTag(node, "artifactId");
							dep.version = readStringTag(node, "version");
							dep.scope = readStringTag(node, "scope");
							if (StringUtils.isEmpty(dep.scope)) {
								dep.scope = "compile";
							}
							dep.optional = readBooleanTag(node, "optional");
							deps.add(dep);
						}
					}
				}
			}
		}
		return deps;
	}

	private static String readStringTag(Node node, String tag) {
		Element element = (Element) node;
		NodeList tagList = element.getElementsByTagName(tag);
		if (tagList == null || tagList.getLength() == 0) {
			return null;
		}
		Element tagElement = (Element) tagList.item(0);
		NodeList textList = tagElement.getChildNodes();
		String content = textList.item(0).getNodeValue().trim();
		return content;
	}

	private static boolean readBooleanTag(Node node, String tag) {
		String content = readStringTag(node, tag);
		if (StringUtils.isEmpty(content)) {
			return false;
		}
		return Boolean.parseBoolean(content);
	}

	public static class PomDep {
		String groupId;
		String artifactId;
		String version;
		String scope;
		boolean optional;

		public boolean resolveDependencies() {
			return !optional && ("compile".equals(scope) || "runtime".equals(scope));
		}
		
		@Override
		public String toString() {
			return  groupId + ":" + artifactId + (version == null ? "":(":" +version)) + " (" + scope + (optional ? ", optional)":")");
		}		
	}
}
