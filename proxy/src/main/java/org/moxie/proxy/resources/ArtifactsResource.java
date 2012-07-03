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
package org.moxie.proxy.resources;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.moxie.Dependency;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.proxy.Constants;
import org.moxie.proxy.DependencyLink;
import org.moxie.proxy.RemoteRepository;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class ArtifactsResource extends BaseResource {

	@Override
	protected String getBasePath() {
		// grab snapshots/releases/whatever from request url
		String path = getRequest().getResourceRef().getPath();
		if (path.charAt(0) == '/') {
			path = path.substring(1);
		}
		if (path.indexOf('/') > -1) {
			return path.substring(0,  path.indexOf('/'));
		}
		return path;
	}

	@Override
	protected String getBasePathName() {
		String basepath = getBasePath();
		try {
			// return a translation of the base path, if available
			return getTranslation().getString("mp." + basepath);
		} catch (Throwable t) {
			return basepath;
		}
	}
	
	boolean isRemoteRepository() {
		String repository = getBasePath();
		return getProxyConfig().isRemoteRepository(repository);
	}
	
	String getRepositoryUrl() {
		String repository = getBasePath();
		return getRootRef() + "/m2/" + repository;
	}
	
	String getProxyUrl() {
		return getRootRef().getScheme() + "://" + getRootRef().getHostDomain() + ":" + getProxyConfig().getProxyPort();
	}
	
	String getRepositoryNote(Pom pom) {
		if (pom != null) {
			return null;
		} else if (isRemoteRepository()) {
			// remote/proxied repository
			RemoteRepository repository = getProxyConfig().getRemoteRepository(getBasePath());
			String message = getTranslation().getString("mp.remoteRepositoryNote");
			return MessageFormat.format(message, repository.id, repository.getHost(), getProxyUrl());
		} else {
			// local repository
			String repository = getBasePath();
			String message = getTranslation().getString("mp.localRepositoryNote");
			return MessageFormat.format(message, repository);
		}
	}

	String getMoxieSnippet(Pom pom) {		
		StringBuilder sb = new StringBuilder();
		if (pom != null) {
			// artifact
			sb.append("dependencies:\n");
			sb.append(" - compile ").append(pom.getCoordinates());
		} else if (isRemoteRepository()) {
			// proxy settings
			RemoteRepository repository = getProxyConfig().getRemoteRepository(getBasePath());			
			sb.append("proxies:\n");
			sb.append(MessageFormat.format("- '{'\n    active: true\n    id: moxieProxy\n    protocol: {0}\n    host: {1}\n    port: {2,number,0}\n    username: username\n    password: password\n    proxyHosts: {3}\n'}'", getRootRef().getScheme(), getRootRef().getHostDomain(), getProxyConfig().getProxyPort(), repository.getHost()));
		} else {
			// repository settings
			sb.append("repositories:\n");
			sb.append(" - ").append(getRepositoryUrl());
		}
		return sb.toString();
	}

	String getMavenSnippet(Pom pom) {
		String repository = getBasePath();
		StringBuilder sb = new StringBuilder();
		if (pom != null) {
			// artifact
			sb.append("<dependency>\n");
			sb.append(StringUtils.toXML("groupId", pom.groupId));
			sb.append(StringUtils.toXML("artifactId", pom.artifactId));
			sb.append(StringUtils.toXML("version", pom.version));
			sb.append(StringUtils.toXML("scope", Scope.compile.name()));
			sb.append("</dependency>");
		} else if (isRemoteRepository()) {
			// proxy settings
			sb.append("<proxy>\n");
			sb.append(StringUtils.toXML("id", "moxieProxy"));
			sb.append(StringUtils.toXML("active", "true"));
			sb.append(StringUtils.toXML("protocol", getRootRef().getScheme()));
			sb.append(StringUtils.toXML("host", getRootRef().getHostDomain()));
			sb.append(StringUtils.toXML("port", "" + getProxyConfig().getProxyPort()));
			sb.append(StringUtils.toXML("username", "username"));
			sb.append(StringUtils.toXML("password", "password"));
			sb.append(StringUtils.toXML("nonProxyHosts", "*.nonproxyrepos.com|localhost"));
			sb.append("</proxy>");
		} else {
			// repository settings
			sb.append("<repository>\n");
			sb.append(StringUtils.toXML("id", "moxie" + Character.toUpperCase(repository.charAt(0)) + repository.substring(1)));
			sb.append(StringUtils.toXML("name", Constants.getName() + " " + repository));
			sb.append(StringUtils.toXML("url", getRepositoryUrl()));		
			sb.append(StringUtils.toXML("layout", "default"));		
			sb.append("</repository>");
		}
		return StringUtils.escapeForHtml(sb.toString(), false);
	}
	
	String getGradleSnippet(Pom pom) {
		StringBuilder sb = new StringBuilder();
		if (pom != null) {
			// artifact
			sb.append("dependencies {\n");
			sb.append("  compile \"").append(pom.getCoordinates()).append("\"\n");
			sb.append("}");
		} else if (isRemoteRepository()) {
			// proxy settings
			String base = "systemProp." + getRootRef().getScheme();
			sb.append(base).append("proxyHost=").append(getRootRef().getHostDomain()).append('\n');
			sb.append(base).append("proxyPort=").append(getProxyConfig().getProxyPort()).append('\n');
			sb.append(base).append("proxyUser=username").append('\n');
			sb.append(base).append("proxyPassword=password\n");
			sb.append(base).append("nonProxyHosts=*.nonproxyrepos.com|localhost");
		} else {
			// repository settings
			sb.append("repositories {\n");
			sb.append("  maven {\n");
			sb.append("    url \"").append(getRepositoryUrl()).append("\"\n");
			sb.append("  }\n");
			sb.append("}");
		}
		return sb.toString();
	}
	
	File getArtifactRoot() {
		return getProxyConfig().getArtifactRoot(getBasePath());
	}

	File getFile(String path) {
		File root = getArtifactRoot();
		File file;
		if (StringUtils.isEmpty(path)) {
			file = root;
		} else {
			if (path.startsWith(getBasePath())) {
				// strip leading /releases or /snapshots
				path = path.substring(getBasePath().length() + 1);
			}
			file = new File(root, path);
		}
		return file;
	}

	List<ListItem> getItems(File folder) {
		String rootPath = getArtifactRoot().getAbsolutePath();
		List<ListItem> list = new ArrayList<ListItem>();
		String pattern = getProxyConfig().getDateFormat();
		SimpleDateFormat df = new SimpleDateFormat(pattern);
		for (File file : folder.listFiles()) {
			String relativePath= StringUtils.getRelativePath(rootPath, file.getAbsolutePath());
			ListItem item = new ListItem(file.getName(), relativePath, file.isFile() && !isText(file));
			if (file.isFile()) {
				item.size = FileUtils.formatSize(file.length());
				item.date = df.format(new Date(file.lastModified()));
			}
			list.add(item);
		}
		return list;
	}
	
	List<ListItem> getCrumbs(File folder) {		
		String rootPath = getArtifactRoot().getAbsolutePath();
		String folderPath = folder.getAbsolutePath();
		String relativePath = StringUtils.getRelativePath(rootPath, folderPath);
		
		List<ListItem> list = new ArrayList<ListItem>();
		String [] paths = relativePath.split("/");
		for (int i = 0; i < Math.max(0, paths.length - 1); i++) {
			// build relative path links
			StringBuilder sb = new StringBuilder();			
			for (int j = 0; j <= i; j++) {
				sb.append(paths[j].trim()).append('/');
			}
			// exclude breadcrumb root
			if (sb.length() > 1) {
				// trim out trailing /
				sb.setLength(sb.length() - 1);
				list.add(new ListItem(paths[i], sb.toString(), false));
			}
		}
		return list;
	}
	
	List<DependencyLink> getDependencies(Pom pom) {
		if (pom == null) {
			return null;
		}
		// find dependencies as they might be in another local/proxied repository
		List<DependencyLink> list = new ArrayList<DependencyLink>();
		for (Dependency dependency : pom.getDependencies(true)) {
			DependencyLink link = getProxyConfig().find(dependency);
			if (link != null) {
				list.add(link);
			}
		}
		return list;
	}

	@Get
	public Representation toText() {
		String path = null;
		if (getRequestAttributes().containsKey("path")) {
			path = getRequestAttributes().get("path").toString();
		}
		File file = getFile(path);
		if (!file.exists()) {
			// TODO proxy download?  do not know source repo, try all?
			getLogger().warning(path + " does not exist!");
		}
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("crumbsRoot", getBasePathName());
		map.put("crumbs", getCrumbs(file));
		
		boolean isRemote = isRemoteRepository();
		String activeCrumb = file.getName();
		if (isRemote && file.equals(getArtifactRoot())) {
			// show the proxied url as the active root crumb
			activeCrumb = getProxyConfig().getRemoteRepository(getBasePath()).url;
		}
		map.put("activeCrumb", activeCrumb);

		if (file.isFile()) {
			if (isText(file)) {
				// display file content
				String content = FileUtils.readContent(file, "\n").trim();
				String html = StringUtils.escapeForHtml(content, false);
				map.put("content", html);
				return toHtml(map, "artifact.html");
			}
		}
				
		// list of files/folders
		Pom pom = getApplication().readPom(file);
		map.put("pom", pom);
		map.put("dependencies", getDependencies(pom));
		map.put("isRemoteRepository", isRemote);
		map.put("repositoryUrl", getRepositoryUrl());
		map.put("repositoryNote", getRepositoryNote(pom));
		map.put("moxieSnippet", getMoxieSnippet(pom));
		map.put("mavenSnippet", getMavenSnippet(pom));
		map.put("gradleSnippet", getGradleSnippet(pom));
		map.put("items", getItems(file));
		return toHtml(map, "artifacts.html");
	}
	
	boolean isText(File file) {
		String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
		if ("pom".equals(ext) || "xml".equals(ext) || "sha1".equals(ext) || "md5".equals(ext) || "asc".equals(ext)) {
			return true;
		}
		return false;
	}
}
