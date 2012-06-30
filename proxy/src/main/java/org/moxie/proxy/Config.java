/*
 * Copyright 2002-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moxie.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.moxie.MavenCache;
import org.moxie.Proxy;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * Read and manage the configuration.
 * 
 * <p>
 * Unlike the standard config classes, this one allows to reload the config at
 * any convenient time.
 * 
 * @author digulla
 * 
 */
public class Config {
	public static final Logger log = Logger.getLogger(Config.class.getSimpleName());

	private File configFile;
	private long configLastModified;
	private boolean isLoaded;

	private File moxieRoot;
	private File localArtifactsRoot;
	private File remoteArtifactsRoot;
	private int httpPort;
	private int proxyPort;
	private boolean accesslog;
	private String dateFormat;

	private List<String> localRepositories;
	private Map<String, RemoteRepository> remoteRepositories;

	private List<Proxy> proxies;
	private List<MirrorEntry> mirrors;
	private List<AllowDeny> allowDeny;
	private List<String> noProxy;

	public Config() {
		proxies = Collections.emptyList();
		mirrors = Collections.emptyList();
		allowDeny = Collections.emptyList();
		noProxy = Collections.emptyList();
		localRepositories = Collections.emptyList();
		remoteRepositories = Collections.emptyMap();
		dateFormat = "yyyy-MM-dd";
	}

	public void parse(File file) {
		configFile = file;
		reload();
	}

	public synchronized void reload() {
		long lastModified = configFile.lastModified();
		if (lastModified != configLastModified) {
			log.info((isLoaded ? "reloading" : "loading") + " config from " + configFile.getAbsolutePath());
			configLastModified = lastModified;

			try {
				MaxmlMap map = Maxml.parse(new FileInputStream(configFile));
				if (!isLoaded) {
					// only read these settings on a cold start
					httpPort = map.getInt("httpPort", httpPort);
					proxyPort = map.getInt("proxyPort", proxyPort);
					moxieRoot = new File(map.getString("rootFolder", "moxie"));
					localArtifactsRoot = new File(moxieRoot, "local");
					remoteArtifactsRoot = new File(moxieRoot, "remote");
					localRepositories = map.getStrings("localRepositories", localRepositories);
					remoteRepositories = parseRemoteRepositories(map);
					dateFormat = map.getString("dateFormat", dateFormat);
				}
			} catch (Exception e) {
				log.log(Level.SEVERE, "failed to parse " + configFile, e);
			}
		}
	}

	Map<String, RemoteRepository> parseRemoteRepositories(MaxmlMap map) {
		Map<String, RemoteRepository> remotes = new LinkedHashMap<String, RemoteRepository>();
		if (map.containsKey("remoteRepositories")) {
			for (Object o : map.getList("remoteRepositories", Collections.emptyList())) {
				MaxmlMap repoMap = (MaxmlMap) o;
				String id = repoMap.getString("id", null);
				String url = repoMap.getString("url", null);
				RemoteRepository repo = new RemoteRepository(id, url);
				remotes.put(repo.id, repo);
			}
		}
		return remotes;
	}

	public File getCacheFolder() {
		return new File(moxieRoot, "cache");
	}

	public int getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(int val) {
		this.httpPort = val;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int val) {
		this.proxyPort = val;
	}

	public boolean getAccessLog() {
		return accesslog;
	}

	public void setAccessLog(boolean val) {
		this.accesslog = val;
	}
	
	public String getDateFormat() {
		return dateFormat;
	}

	public File getMoxieRoot() {
		return moxieRoot;
	}

	public void setMoxieRoot(File val) {
		this.moxieRoot = val;
	}

	public File getArtifactRoot(String relativePath) {
		String repo;
		if (relativePath.startsWith("remotes/")) {
			// strip out remotes/
			relativePath = relativePath.substring("remotes/".length());
		}
		if (relativePath.indexOf('/') > -1) {
			// strip out basepath
			repo = relativePath.substring(0,  relativePath.indexOf('/'));
		} else {
			repo = relativePath;
		}

		if (remoteRepositories.containsKey(repo)) {
			RemoteRepository repository = remoteRepositories.get(repo);
			return new File(remoteArtifactsRoot, StringUtils.urlToFolder(repository.url));			
		}
		return new File(localArtifactsRoot, repo);
	}
	
	public MavenCache getMavenCache(File file) {
		String path = FileUtils.getRelativePath(moxieRoot, file);
		File folder = getArtifactRoot(path);
		return new MavenCache(folder);
	}

	public List<String> getLocalRepositories() {
		return localRepositories;
	}

	public Collection<RemoteRepository> getRemoteRepositories() {
		return remoteRepositories.values();
	}

	public List<MirrorEntry> getMirrors() {
		return mirrors;
	}

	public URL getMirror(URL url) throws MalformedURLException {
		String s = url.toString();

		for (MirrorEntry entry : getMirrors()) {
			URL mirror = entry.getMirrorURL(s);
			if (mirror != null) {
				log.info("Redirecting request to mirror " + mirror.toString());
				return mirror;
			}
		}

		return url;
	}

	private String[] getNoProxy(Object root) {
		String s = null;// getStringProperty(root, "proxy", "no-proxy", null);
		if (s == null)
			return new String[0];

		String[] result = s.split(",");
		for (int i = 0; i < result.length; i++) {
			result[i] = result[i].trim();
		}

		return result;
	}

	public List<String> getNoProxy() {
		return noProxy;
	}

	public boolean useProxy(URL url) {
		// if (!hasProxy(config.getRootElement()))
		// return false;
		//
		// String host = url.getHost();
		// for (String postfix : getNoProxy()) {
		// if (host.endsWith(postfix))
		// return false;
		// }
		// return true;
		return false;
	}

	public Proxy getProxy(URL url) {
		return null;
	}

	public List<AllowDeny> getAllowDeny() {
		return allowDeny;
	}

	public boolean isAllowed(URL url) {
		String s = url.toString();
		for (AllowDeny rule : getAllowDeny()) {
			if (rule.matches(s)) {
				log.info((rule.isAllowed() ? "Allowing" : "Denying") + " access to " + url
						+ " because of config rule");
				return rule.isAllowed();
			}
		}

		return true;
	}
}
