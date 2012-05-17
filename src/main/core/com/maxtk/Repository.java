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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;

import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class Repository {

	final String name;
	final String repositoryUrl;
	final String artifactPattern;

	public Repository(String name, String mavenUrl) {
		this(name, mavenUrl, Constants.MAVEN2_PATTERN);
	}

	public Repository(String name, String mavenUrl, String pattern) {
		this.name = name;
		this.repositoryUrl = mavenUrl;
		this.artifactPattern = pattern;
	}

	@Override
	public int hashCode() {
		return repositoryUrl.toLowerCase().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Repository) {
			return ((Repository) o).repositoryUrl.equalsIgnoreCase(repositoryUrl);
		}
		return false;
	}
	
	@Override
	public String toString() {
		return StringUtils.isEmpty(name) ? repositoryUrl:name;
	}
	
	protected boolean calculateSHA1() {
		return true;
	}
	
	protected boolean isMavenSource() {
		return true;
	}
	
	public boolean isSource(Dependency dependency) {
		if (dependency.isMavenObject() && isMavenSource()) {
			// dependency is a Maven object AND the repository is a Maven source
			return true;
		} else if (!dependency.isMavenObject() && !isMavenSource()) {
			// dependency is NOT a Maven object AND the repository is NOT a Maven source
			return true;
		}
		return false;
	}
	
	public String getArtifactUrl() {
		return repositoryUrl + (repositoryUrl.endsWith("/") ? "":"/") + artifactPattern;
	}

	protected URL getURL(Dependency dep, String ext) throws MalformedURLException {
		String url = Dependency.getMavenPath(dep, ext, getArtifactUrl());
		return new URL(url);
	}

	protected String getSHA1(Build build, Dependency dep, String ext) {
		try {
			String extsha1 = ext + ".sha1";
			File hashFile = build.getArtifactCache().getFile(dep, extsha1);
			if (hashFile.exists()) {
				// read cached sha1
				return FileUtils.readContent(hashFile, "\n").trim();
			}

			URL url = getURL(dep, extsha1);
			DownloadData data = download(build, url);
			String content = new String(data.content, "UTF-8").trim();
			String hashCode = content.substring(0, 40);

			// cache this sha1 file
			File file = build.getArtifactCache().writeFile(dep, extsha1, hashCode);
			file.setLastModified(data.lastModified);
			return hashCode;
		} catch (FileNotFoundException t) {
			// swallow these errors, this is how we tell if Maven does not have
			// the requested artifact
		} catch (IOException t) {
			if (t.getMessage().contains("400") || t.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				build.console.error(t, "Error retrieving SHA1 for {0}", dep);
			}
		} catch (Throwable t) {
			build.console.error(t, "Error retrieving SHA1 for {0}", dep);
		}
		return null;
	}

	public File download(Build build, Dependency dep, String ext) {
		String expectedSHA1 = "";
		if (calculateSHA1()) {
			expectedSHA1 = getSHA1(build, dep, ext);
			if (expectedSHA1 == null) {
				return null;
			}
		}
		
		try {
			URL url = getURL(dep, ext);
			build.console.download(url.toString());
			DownloadData data = download(build, url);
			if (calculateSHA1()) {
				String calculatedSHA1 = StringUtils.getSHA1(data.content);
				if (!StringUtils.isEmpty(expectedSHA1) && !calculatedSHA1.equals(expectedSHA1)) {
					throw new RuntimeException("SHA1 checksum mismatch; got: " + calculatedSHA1);
				}
			}

			// save to the artifact cache
			File file = build.getArtifactCache().writeFile(dep, ext, data.content);
			file.setLastModified(data.lastModified);
			return file;
		} catch (MalformedURLException m) {
			m.printStackTrace();
		} catch (IOException e) {
			throw new RuntimeException(MessageFormat.format("Error downloading! Do you need to specify a proxy server in {0}?", build.maxilla.file.getAbsolutePath()), e);
		}
		return null;
	}
	
	private DownloadData download(Build build, URL url) throws IOException {
		long lastModified = System.currentTimeMillis();
		ByteArrayOutputStream buff = new ByteArrayOutputStream();

		java.net.Proxy proxy = build.getProxy(repositoryUrl);
		URLConnection conn = url.openConnection(proxy);
		if (java.net.Proxy.Type.DIRECT != proxy.type()) {
			String auth = build.getProxyAuthorization(repositoryUrl);
			conn.setRequestProperty("Proxy-Authorization", auth);
		}
		// try to get the server-specified last-modified date of this artifact
		lastModified = conn.getHeaderFieldDate("Last-Modified", lastModified);
		
		InputStream in = new BufferedInputStream(conn.getInputStream());
		byte[] buffer = new byte[32767];

		while (true) {
			int len = in.read(buffer);
			if (len < 0) {
				break;
			}
			buff.write(buffer, 0, len);
		}
		in.close();

		byte[] data = buff.toByteArray();
		return new DownloadData(data, lastModified);
	}
	
	private class DownloadData {
		final byte [] content;
		final long lastModified;
		
		DownloadData(byte [] content, long lastModified) {
			this.content = content;
			this.lastModified = lastModified;
		}
	}
}
