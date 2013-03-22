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
package org.moxie.proxy;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.moxie.Dependency;
import org.moxie.IMavenCache;
import org.moxie.MavenCache;
import org.moxie.MoxieCache;
import org.moxie.Proxy;
import org.moxie.RemoteRepository;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * Read and manage the configuration.
 */
public class ProxyConfig {
	public static final Logger log = Logger.getLogger(ProxyConfig.class.getSimpleName());

	private File configFile;
	private long configLastModified;
	private boolean isLoaded;

	private File moxieRoot;
	private File localArtifactsRoot;
	private File remoteArtifactsRoot;
	private int httpPort;
	private int httpsPort;
	private int proxyPort;
	private int shutdownPort;
	private List<String> bindAddresses;
	private boolean accesslog;
	private String dateFormat;
	private int atomCount;
	private int searchCount;
	private String keystorePassword;

	private List<String> localRepositories;
	private List<RemoteRepository> remoteRepositories;
	private Map<String, RemoteRepository> remoteRepositoryLookup;

	private List<Proxy> proxies;
	private List<Redirect> redirects;
	private List<AllowDeny> allowDeny;	
	
	private MoxieCache moxieCache;

	public ProxyConfig() {
		shutdownPort = 8079;
		httpPort = 8080;
		httpsPort = 8443;
		proxyPort = 8081;
		dateFormat = "yyyy-MM-dd";
		bindAddresses = Collections.emptyList();
		proxies = Collections.emptyList();
		redirects = Collections.emptyList();
		allowDeny = Collections.emptyList();
		localRepositories = Collections.emptyList();
		remoteRepositories = Collections.emptyList();
		remoteRepositoryLookup = new HashMap<String, RemoteRepository>();
		atomCount = 50;
		searchCount = 50;
		keystorePassword = "";
	}
	
	public void setUserDefaults() {		
		setMoxieRoot(new File(System.getProperty("user.home"), ".moxie"));
		moxieRoot.mkdirs();
		localArtifactsRoot.mkdirs();
		remoteArtifactsRoot.mkdirs();
		
		// default local repositories 
		localRepositories = Arrays.asList("releases", "snapshots");
		for (String repository : localRepositories) {
			new File(localArtifactsRoot, repository).mkdirs();
		}
		
		// default proxy of central
		remoteRepositories = new ArrayList<RemoteRepository>();
		remoteRepositories.add(new RemoteRepository("central", "http://repo1.maven.org/maven2", false));
		
		remoteRepositoryLookup = new HashMap<String, RemoteRepository>();
		for (RemoteRepository repository : remoteRepositories) {
			remoteRepositoryLookup.put(repository.id, repository);
			remoteRepositoryLookup.put(StringUtils.urlToFolder(repository.url), repository);
		}
		
		// default ssl keystore password
		keystorePassword = "moxieproxy";
	}

	public void parse(File file) {
		configFile = file;
		reload();
	}

	public synchronized void reload() {
		if (configFile == null || !configFile.exists()) {
			return;
		}
		long lastModified = configFile.lastModified();
		if (lastModified != configLastModified) {
			log.info((isLoaded ? "reloading" : "loading") + " config from " + configFile.getAbsolutePath());
			configLastModified = lastModified;

			try {
				MaxmlMap map = Maxml.parse(configFile);
				if (!isLoaded) {
					// only read these settings on a cold start
					httpPort = map.getInt("httpPort", httpPort);
					httpsPort = map.getInt("httpsPort", httpPort);
					proxyPort = map.getInt("proxyPort", proxyPort);
					shutdownPort = map.getInt("shutdownPort", shutdownPort);
					bindAddresses = map.getStrings("bindAddresses", bindAddresses);
					keystorePassword = map.getString("keystorePassword", keystorePassword);
					moxieRoot = new File(map.getString("rootFolder", "moxie"));
					setMoxieRoot(moxieRoot);
					localRepositories = map.getStrings("localRepositories", localRepositories);
					remoteRepositories = parseRemoteRepositories(map);
					for (RemoteRepository repository : remoteRepositories) {
						remoteRepositoryLookup.put(repository.id, repository);
						remoteRepositoryLookup.put(StringUtils.urlToFolder(repository.url), repository);
					}
					
					// do not reload these immutable settings
					isLoaded = true;
				}
				proxies = parseProxies(map);
				dateFormat = map.getString("dateFormat", dateFormat);
				redirects = parseRedirects(map);
				allowDeny = parseAllowDeny(map);
				atomCount = map.getInt("atomCount", atomCount);
				searchCount = map.getInt("searchCount", searchCount);
			} catch (Exception e) {
				log.log(Level.SEVERE, "failed to parse " + configFile, e);
			}
		}
	}

	List<RemoteRepository> parseRemoteRepositories(MaxmlMap map) {
		List<RemoteRepository> remotes = new ArrayList<RemoteRepository>();
		if (map.containsKey("remoteRepositories")) {
			for (Object o : map.getList("remoteRepositories", Collections.emptyList())) {
				MaxmlMap repoMap = (MaxmlMap) o;
				String id = repoMap.getString("id", null);
				String url = repoMap.getString("url", null);
				boolean allowSnapshots = repoMap.getBoolean("allowSnapshots", false);
				RemoteRepository repo = new RemoteRepository(id, url, allowSnapshots);
				remotes.add(repo);
			}
		}
		return remotes;
	}
	
	@SuppressWarnings("unchecked")
	List<Proxy> parseProxies(MaxmlMap map) {
		List<Proxy> list = new ArrayList<Proxy>();
		if (map.containsKey("proxies")) {
			List<MaxmlMap> values = (List<MaxmlMap>) map.get("proxies");
			for (MaxmlMap definition : values) {
				Proxy proxy = new Proxy();
				proxy.id = definition.getString("id", "");
				proxy.active = definition.getBoolean("active", true);
				proxy.protocol = definition.getString("protocol", "http");
				proxy.host = definition.getString("host", "");
				proxy.port = definition.getInt("port", 80);
				proxy.username = definition.getString("username", "");
				proxy.password = definition.getString("password", "");
				proxy.repositories = definition.getStrings("repositories", new ArrayList<String>());
				proxy.proxyHosts = definition.getStrings("proxyHosts", new ArrayList<String>());
				proxy.nonProxyHosts = definition.getStrings("nonProxyHosts", new ArrayList<String>());
				list.add(proxy);
			}
		}
		return list;
	}
	
	@SuppressWarnings("unchecked")
	List<Redirect> parseRedirects(MaxmlMap map) {
		List<Redirect> list = new ArrayList<Redirect>();
		if (map.containsKey("redirects")) {
			List<MaxmlMap> values = (List<MaxmlMap>) map.get("redirects");
			for (MaxmlMap definition : values) {
				String from = definition.getString("from", null);
				String to = definition.getString("to", null);
				if (StringUtils.isEmpty(from) || StringUtils.isEmpty(to)) {
					log.warning(MessageFormat.format("Dropping incomplete redirect definition! from: \"{0}\" to: \"{1}\"", from, to));
					continue;
				}
				Redirect redirect = new Redirect(from, to);
				list.add(redirect);
			}
		}
		return list;
	}

	List<AllowDeny> parseAllowDeny(MaxmlMap map) {
		List<AllowDeny> list = new ArrayList<AllowDeny>();
		if (map.containsKey("allow")) {
			for (String value : map.getStrings("allow", new ArrayList<String>())) {
				AllowDeny rule = new AllowDeny(value, true);
				list.add(rule);
			}			
		}
		if (map.containsKey("deny")) {
			for (String value : map.getStrings("deny", new ArrayList<String>())) {
				AllowDeny rule = new AllowDeny(value, false);
				list.add(rule);
			}			
		}
		return list;
	}

	public int getAtomCount() {
		return atomCount;
	}

	public int getSearchCount() {
		return searchCount;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(int val) {
		this.httpPort = val;
	}

	public int getHttpsPort() {
		return httpsPort;
	}

	public void setHttpsPort(int val) {
		this.httpsPort = val;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int val) {
		this.proxyPort = val;
	}
	
	public boolean isProxyEnabled() {
		return proxyPort > 0;
	}
	
	public int getShutdownPort() {
		return shutdownPort;
	}

	public void setShutdownPort(int val) {
		this.shutdownPort = val;
	}
	
	public List<String> getBindAddresses() {
		return bindAddresses;
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

	public String getKeystorePassword() {
		return keystorePassword;
	}

	public void setStorePassword(String val) {
		this.keystorePassword = val;
	}

	public File getMoxieRoot() {
		return moxieRoot;
	}

	public void setMoxieRoot(File val) {
		this.moxieRoot = val;
		localArtifactsRoot = new File(moxieRoot, org.moxie.Constants.LOCAL);
		remoteArtifactsRoot = new File(moxieRoot, org.moxie.Constants.REMOTE);
	}

	public File getArtifactRoot(String relativePath) {
		String repo;
		if (relativePath.startsWith(org.moxie.Constants.REMOTE + "/")) {
			// strip out remote/
			relativePath = relativePath.substring(org.moxie.Constants.REMOTE.length() + 1);
		}
		if (relativePath.indexOf('/') > -1) {
			// strip out basepath
			repo = relativePath.substring(0,  relativePath.indexOf('/'));
		} else {
			repo = relativePath;
		}

		if (remoteRepositoryLookup.containsKey(repo)) {
			RemoteRepository repository = remoteRepositoryLookup.get(repo);
			return new File(remoteArtifactsRoot, StringUtils.urlToFolder(repository.url));			
		}
		return new File(localArtifactsRoot, repo);
	}
	
	public boolean isRemoteRepository(String repository) {
		return remoteRepositoryLookup.containsKey(repository);
	}
	
	public RemoteRepository getRemoteRepository(String repository) {
		return remoteRepositoryLookup.get(repository);
	}
	
	public String getRepositoryId(File artifactFile) {
		for (RemoteRepository repository : remoteRepositories) {
			String remote = StringUtils.urlToFolder(repository.url.toString());
			File remoteFolder = new File(remoteArtifactsRoot, remote);
			if (artifactFile.getAbsolutePath().startsWith(remoteFolder.getAbsolutePath())) {
				return repository.id;
			}
		}
		return null;
	}
	
	public String getRepositoryId(URL artifactUrl) {
		String url = artifactUrl.toExternalForm();
		for (RemoteRepository repository : remoteRepositories) {
			if (url.startsWith(repository.url)) {
				return repository.id;
			}
		}
		return null;
	}
	
	public File getRemoteArtifact(URL artifactUrl) {
		String url = artifactUrl.toExternalForm();
		for (RemoteRepository repository : remoteRepositories) {
			if (url.startsWith(repository.url)) {
				String remote = StringUtils.urlToFolder(repository.url.toString());
				File remoteFolder = new File(remoteArtifactsRoot, remote);
				String path = StringUtils.getRelativePath(repository.url, url);
				return new File(remoteFolder, path);
			}
		}
		return null;
	}
	
	public MoxieCache getMoxieCache() {
		if (moxieCache == null) {
			moxieCache = new MoxieCache(moxieRoot);
		}
		return moxieCache;
	}
	
	public IMavenCache getMavenCache(File file) {
		String path = FileUtils.getRelativePath(moxieRoot, file);
		File folder = getArtifactRoot(path);
		return new MavenCache(folder);
	}

	public IMavenCache getMavenCache(String repository) {
		for (String repo : localRepositories) {
			if (repo.equalsIgnoreCase(repository)) {
				File file = new File(localArtifactsRoot, repo);
				return new MavenCache(file);
			}
		}
		if (remoteRepositoryLookup.containsKey(repository)) {
			RemoteRepository repo = remoteRepositoryLookup.get(repository);
			String folder = StringUtils.urlToFolder(repo.url);
			File file = new File(remoteArtifactsRoot, folder);
			return new MavenCache(file);
		}
		return null;
	}

	public DependencyLink find(Dependency dependency) {
		String path = null;
		for (String repository : localRepositories) {
			File cacheRoot = new File(localArtifactsRoot, repository);
			IMavenCache cache = new MavenCache(cacheRoot);
			File file = cache.getArtifact(dependency, dependency.extension);
			if (file != null && file.exists()) {				
				path = repository + "/" + FileUtils.getRelativePath(cacheRoot, file.getParentFile());
			}
		}
		
		for (RemoteRepository repository : remoteRepositories) {
			String folder = StringUtils.urlToFolder(repository.url);
			File cacheRoot = new File(remoteArtifactsRoot, folder);
			IMavenCache cache = new MavenCache(cacheRoot);
			File file = cache.getArtifact(dependency, dependency.extension);
			if (file != null && file.exists()) {
				path = repository.id + "/" + FileUtils.getRelativePath(cacheRoot, file.getParentFile());
			}
		}
		if (StringUtils.isEmpty(path)) {
			return null;
		}
		return new DependencyLink(dependency.getCoordinates(), path);
	}

	public List<String> getLocalRepositories() {
		return localRepositories;
	}

	public Collection<RemoteRepository> getRemoteRepositories() {
		return remoteRepositories;
	}

	public List<Redirect> getRedirects() {
		return redirects;
	}

	public URL getRedirect(URL url) throws MalformedURLException {
		String s = url.toString();

		for (Redirect entry : getRedirects()) {
			URL to = entry.getRedirectURL(s);
			if (to != null) {
				log.info("Redirecting request to " + to.toString());
				return to;
			}
		}

		return url;
	}

	public boolean useProxy(URL url) {
		for (Proxy proxy : proxies) {
			if (proxy.active && proxy.matches(null, url.toExternalForm())) {
				return true;
			}
		}
		return false;
	}

	public Proxy getProxy(URL url) {
		for (Proxy proxy : proxies) {
			if (proxy.active && proxy.matches(null, url.toExternalForm())) {
				return proxy;
			}
		}
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
