package org.moxie.proxy.resources;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.moxie.proxy.Constants;
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
	
	String getRepositoryUrl() {
		String repository = getBasePath();
		return getRootRef() + "/m2/" + repository;
	}
	
	String getRepositoryNote() {
		String repository = getBasePath();
		String message = getTranslation().getString("mp.repositoryNote");
		return MessageFormat.format(message, repository);
	}

	String getMoxieSnippet() {
		StringBuilder sb = new StringBuilder();
		sb.append("repositories:\n");
		sb.append(" - ").append(getRepositoryUrl());		
		return sb.toString();
	}

	String getMavenSnippet() {
		String repository = getBasePath();
		StringBuilder sb = new StringBuilder();
		sb.append("<repository>\n");
		sb.append(StringUtils.toXML("id", "moxie-" + repository));
		sb.append(StringUtils.toXML("name", Constants.getName() + " " + repository));
		sb.append(StringUtils.toXML("url", getRepositoryUrl()));		
		sb.append(StringUtils.toXML("layout", "default"));		
		sb.append("</repository>");
		return StringUtils.escapeForHtml(sb.toString(), false);
	}
	
	String getGradleSnippet() {
		StringBuilder sb = new StringBuilder();
		sb.append("repositories {\n");
		sb.append("  maven {\n");
		sb.append("    url \"").append(getRepositoryUrl()).append("\"\n");
		sb.append("  }\n");
		sb.append("}");
		return sb.toString();
	}
	
	File getArtifactRoot() {
		return getApplication().getProxyConfig().getArtifactRoot(getBasePath());
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
		String pattern = getApplication().getProxyConfig().getDateFormat();
		SimpleDateFormat df = new SimpleDateFormat(pattern);
		for (File file : folder.listFiles()) {
			String relativePath= StringUtils.getRelativePath(rootPath, file.getAbsolutePath());
			ListItem item = new ListItem(file.getName(), relativePath);
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
				list.add(new ListItem(paths[i], sb.toString()));
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
			// TODO 404
		}
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("crumbsRoot", getBasePathName());
		map.put("crumbs", getCrumbs(file));
		map.put("activeCrumb", file.getName());

		if (file.isFile()) {
			if (isText(file)) {
				// display file content
				String content = FileUtils.readContent(file, "\n").trim();
				String html = StringUtils.escapeForHtml(content, false);
				map.put("content", html);
				return toHtml(map, "artifact.html");
			}
			// TODO redirect to binary download
		}
		
		// list of files/folders
		map.put("pom", getApplication().readPom(file));
		map.put("repositoryUrl", getRepositoryUrl());
		map.put("repositoryNote", getRepositoryNote());
		map.put("moxieSnippet", getMoxieSnippet());
		map.put("mavenSnippet", getMavenSnippet());
		map.put("gradleSnippet", getGradleSnippet());
		map.put("items", getItems(file));
		return toHtml(map, "artifacts.html");
	}
	
	boolean isText(File file) {
		String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase();
		if ("jar".equals(ext) || "zip".equals(ext) || "war".equals(ext)) {
			return false;
		}
		return true;
	}
}
