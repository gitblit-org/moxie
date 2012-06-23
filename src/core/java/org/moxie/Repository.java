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
import java.util.Date;

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class Repository {

	final String name;
	final String repositoryUrl;
	final String artifactPattern;
	final String metadataPattern;
	final String snapshotPattern;

	public Repository(String name, String mavenUrl) {
		this(name, mavenUrl, Constants.MAVEN2_PATTERN, Constants.MAVEN2_METADATA_PATTERN, Constants.MAVEN2_SNAPSHOT_PATTERN);
	}

	public Repository(String name, String mavenUrl, String pattern, String metadataPattern, String snapshotPattern) {
		this.name = name;
		this.repositoryUrl = mavenUrl;
		this.artifactPattern = pattern;
		this.metadataPattern = metadataPattern;
		this.snapshotPattern = snapshotPattern;
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

	public String getMetadataUrl(Dependency dep) {
		return repositoryUrl + (repositoryUrl.endsWith("/") ? "":"/") + (dep.isSnapshot() ? snapshotPattern : metadataPattern);
	}

	protected URL getURL(Dependency dep, String ext) throws MalformedURLException {
		String url = Dependency.getMavenPath(dep, ext, getArtifactUrl());
		return new URL(url);
	}

	protected String getSHA1(Build build, Dependency dep, String ext) {
		try {
			String extsha1 = ext + ".sha1";
			File hashFile = build.getArtifactCache().getArtifact(dep, extsha1);
			if (hashFile.exists()) {
				// read cached sha1
				return FileUtils.readContent(hashFile, "\n").trim();
			}

			URL url = getURL(dep, extsha1);
			DownloadData data = download(build, url);
			String content = new String(data.content, "UTF-8").trim();
			String hashCode = content.substring(0, 40);

			// cache this sha1 file
			File file = build.getArtifactCache().writeArtifact(dep, extsha1, hashCode);
			file.setLastModified(data.lastModified);
			return hashCode;
		} catch (FileNotFoundException t) {
			// this repository does not have the requested artifact
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
	
	protected String getMetadataSHA1(Build build, Dependency dep) {
		try {
			String extsha1 = Constants.XML + ".sha1";
			File hashFile = build.getArtifactCache().getMetadata(dep, extsha1);
			if (hashFile.exists()) {
				// read cached sha1
				return FileUtils.readContent(hashFile, "\n").trim();
			}

			URL url = new URL(Dependency.getMavenPath(dep, extsha1, getMetadataUrl(dep)));
			DownloadData data = download(build, url);
			String content = new String(data.content, "UTF-8").trim();
			String hashCode = content.substring(0, 40);

			// cache this sha1 file
			File file = build.getArtifactCache().writeMetadata(dep, extsha1, hashCode);
			file.setLastModified(data.lastModified);
			return hashCode;
		} catch (FileNotFoundException t) {
			// this repository does not have the requested metadata
		} catch (IOException t) {
			if (t.getMessage().contains("400") || t.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				build.console.error(t, "Error retrieving metadata SHA1 for {0}", dep);
			}
		} catch (Throwable t) {
			build.console.error(t, "Error retrieving metadata SHA1 for {0}", dep);
		}
		return null;
	}
	
	public File downloadMetadata(Build build, Dependency dep) {
		String expectedSHA1 = "";
		if (calculateSHA1()) {
			expectedSHA1 = getMetadataSHA1(build, dep);
			if (expectedSHA1 == null) {
				// there is no SHA1 for this artifact
				// check for the artifact just-in-case we can download w/o
				// checksum verification
				try {
					URL url = new URL(Dependency.getMavenPath(dep, Constants.XML, getMetadataUrl(dep)));
					URLConnection conn = url.openConnection();
					conn.connect();
				} catch (Throwable t) {
					return null;
				}
			}
		}
		
		try {
			URL url = new URL(Dependency.getMavenPath(dep, Constants.XML, getMetadataUrl(dep)));
			build.console.download(url.toString());
			DownloadData data = download(build, url);
			if (calculateSHA1()) {
				String calculatedSHA1 = StringUtils.getSHA1(data.content);
				if (!StringUtils.isEmpty(expectedSHA1) && !calculatedSHA1.equals(expectedSHA1)) {
					throw new RuntimeException("SHA1 checksum mismatch; got: " + calculatedSHA1);
				}
			}
			
			Metadata oldMetadata;
			File file = build.getArtifactCache().getMetadata(dep, Constants.XML);
			if (file != null && file.exists()) {
				oldMetadata = MetadataReader.readMetadata(file);				
			} else {
				oldMetadata = new Metadata();
			}
			
			// merge metadata
			Metadata newMetadata = MetadataReader.readMetadata(new String(data.content, "UTF-8"));				
			newMetadata.merge(oldMetadata);

			// save merged metadata to the artifact cache
			file = build.getArtifactCache().writeMetadata(dep, Constants.XML, newMetadata.toXML());
			file.setLastModified(data.lastModified);
					
			Date now = new Date();
			if (dep.isSnapshot()) {
				MoxieData moxiedata = build.getArtifactCache().readMoxieData(dep);
				moxiedata.setOrigin(repositoryUrl);
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(newMetadata.lastUpdated);
				build.getArtifactCache().writeMoxieData(dep, moxiedata);	
			} else {				
				// update the Moxie RELEASE metadata
				Dependency versions = DeepCopier.copy(dep);
				versions.version = Constants.RELEASE;
				
				MoxieData moxiedata = build.getArtifactCache().readMoxieData(versions);
				moxiedata.setOrigin(repositoryUrl);
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(now);
				moxiedata.setRELEASE(newMetadata.release);
				moxiedata.setLATEST(newMetadata.latest);
				build.getArtifactCache().writeMoxieData(dep, moxiedata);
				
				// update the Moxie LATEST metadata
				versions.version = Constants.LATEST;
				
				moxiedata = build.getArtifactCache().readMoxieData(versions);
				moxiedata.setOrigin(repositoryUrl);
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(now);
				moxiedata.setRELEASE(newMetadata.release);
				moxiedata.setLATEST(newMetadata.latest);
				build.getArtifactCache().writeMoxieData(dep, moxiedata);	
			}
			return file;
		} catch (MalformedURLException m) {
			m.printStackTrace();
		} catch (FileNotFoundException e) {
			// this repository does not have the requested artifact
		} catch (IOException e) {
			if (e.getMessage().contains("400") || e.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				throw new RuntimeException(MessageFormat.format("Do you need to specify a proxy in {0}?", build.moxie.file.getAbsolutePath()), e);
			}
		}
		return null;
	}

	public File download(Build build, Dependency dep, String ext) {
		String expectedSHA1 = "";
		if (calculateSHA1()) {
			expectedSHA1 = getSHA1(build, dep, ext);
			if (expectedSHA1 == null) {
				// there is no SHA1 for this artifact
				// check for the artifact just-in-case we can download w/o
				// checksum verification
				try {
					URL url = getURL(dep, ext);
					URLConnection conn = url.openConnection();
					conn.connect();
				} catch (Throwable t) {
					return null;
				}
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
			File file = build.getArtifactCache().writeArtifact(dep, ext, data.content);
			file.setLastModified(data.lastModified);
			
			// update Moxie metadata
			MoxieData moxiedata = build.getArtifactCache().readMoxieData(dep);
			moxiedata.setOrigin(repositoryUrl);
			
			Date now = new Date();
			if (Constants.POM.equals(ext)) {
				Pom pom = PomReader.readPom(build.getArtifactCache(), dep);
				if (pom.isPOM()) {
					// POM packaging, so no subsequent download check to mess up
					moxiedata.setLastDownloaded(now);
					moxiedata.setLastChecked(now);
				}
			} else {
				// set lastDownloaded on a non-POM download
				moxiedata.setLastDownloaded(now);
				moxiedata.setLastChecked(now);
				if (!dep.isSnapshot()) {
					// set lastUpdated to lastModified date as reported by server
					// for non-POM downloads. snapshot lastUpdated is set by
					// metadata extraction from maven-metadata.xml
					moxiedata.setLastUpdated(new Date(data.lastModified));
				}
			}
			build.getArtifactCache().writeMoxieData(dep, moxiedata);
			
			return file;
		} catch (MalformedURLException m) {
			m.printStackTrace();
		} catch (FileNotFoundException e) {
			// this repository does not have the requested artifact
		} catch (IOException e) {
			if (e.getMessage().contains("400") || e.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				throw new RuntimeException(MessageFormat.format("Do you need to specify a proxy in {0}?", build.moxie.file.getAbsolutePath()), e);
			}
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
