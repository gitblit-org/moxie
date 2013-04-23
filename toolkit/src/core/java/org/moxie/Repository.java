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
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.moxie.utils.Base64;
import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class Repository {

	final String name;
	final String repositoryUrl;
	final String artifactPattern;
	final String metadataPattern;
	final String snapshotPattern;
	boolean allowSnapshots;
	PurgePolicy purgePolicy;
	final Set<String> affinity;
	int connectTimeout;
	int readTimeout;
	String username;
	String password;
	int checksumRetryWaitPeriod = 500;
	
	public Repository(RemoteRepository definition) {
		this(definition.id, definition.url);
		this.allowSnapshots = definition.allowSnapshots;
		this.purgePolicy = definition.purgePolicy;
		this.affinity.addAll(definition.affinity);
		this.connectTimeout = definition.connectTimeout;
		this.readTimeout = definition.readTimeout;
		this.username = definition.username;
		this.password = definition.password;
	}

	public Repository(String name, String mavenUrl) {
		this(name, mavenUrl, Constants.MAVEN2_ARTIFACT_PATTERN, Constants.MAVEN2_METADATA_PATTERN, Constants.MAVEN2_SNAPSHOT_PATTERN);
	}

	public Repository(String name, String mavenUrl, String pattern, String metadataPattern, String snapshotPattern) {
		this.name = name;
		this.repositoryUrl = mavenUrl;
		this.artifactPattern = pattern;
		this.metadataPattern = metadataPattern;
		this.snapshotPattern = snapshotPattern;
		this.purgePolicy = new PurgePolicy();
		this.affinity = new LinkedHashSet<String>();
		// timeout defaults of Maven 3.0.4
		this.connectTimeout = 20;
		this.readTimeout = 1800;
	}

	@Override
	public int hashCode() {
		return getRepositoryUrl().toLowerCase().hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Repository) {
			return ((Repository) o).getRepositoryUrl().equalsIgnoreCase(getRepositoryUrl());
		}
		return false;
	}
	
	@Override
	public String toString() {
		return StringUtils.isEmpty(name) ? getRepositoryUrl():name;
	}
	
	protected boolean calculateSHA1() {
		return true;
	}
	
	protected synchronized void verifySHA1(Solver solver, String expectedSHA1, DownloadData data, boolean isRetry) {
		if (calculateSHA1()) {
			String calculatedSHA1 = StringUtils.getSHA1(data.content);
			if (!StringUtils.isEmpty(expectedSHA1) && !calculatedSHA1.equals(expectedSHA1)) {
				String message = MessageFormat.format("SHA1 checksum mismatch for {0}\ncalculated: {1}\nretrieved: {2}", data.url.toExternalForm(), calculatedSHA1, expectedSHA1);
				if (isRetry) {
					// log warning on a retry
					for (String line : message.split("\n")) {
						solver.getConsole().warn(line);
					}
				}
				if (solver.isFailOnChecksumError()) {
					if (isRetry) {
						// log warning on a retry
						if (data.url.toString().endsWith(".xml")) {
							solver.getConsole().warn(MessageFormat.format("Checksum file may have been cached for performance by the repository server or by a proxy server.\nspecify \"-D{0}=false\" to disable checksum verification\nOR\nspecify \"-D{1}=true\" to force a metadata refresh.", Toolkit.MX_ENFORCECHECKSUMS, Toolkit.MX_UPDATEMETADATA));
						} else {
							solver.getConsole().warn(MessageFormat.format("Checksum file may have been cached for performance by the repository server or by a proxy server.\nspecify \"-D{0}=false\" to disable checksum verification.", Toolkit.MX_ENFORCECHECKSUMS));
						}
					}
					throw new MoxieException(message);
				}
			}
		}
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
	
	public boolean hasAffinity(Dependency dependency) {
		if (affinity.isEmpty()) {
			return false;
		}
		if (affinity.contains(dependency.getManagementId())) {
			return true;
		} else if (affinity.contains(dependency.groupId)) {
			return true;
		} else {
			for (String value : affinity) {
				if (dependency.groupId.startsWith(value)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean allowSnapshots() {
		return allowSnapshots;
	}
	
	public String getRepositoryUrl() {
		return repositoryUrl;
	}
	
	public String getArtifactUrl() {
		return repositoryUrl + (repositoryUrl.endsWith("/") ? "":"/") + artifactPattern;
	}

	public String getMetadataUrl(Dependency dep) {
		return repositoryUrl + (repositoryUrl.endsWith("/") ? "":"/") + (dep.isSnapshot() ? snapshotPattern : metadataPattern);
	}

	protected URL getURL(Dependency dep, String ext) throws MalformedURLException {
		String url = Dependency.getArtifactPath(dep, ext, getArtifactUrl());
		return new URL(url);
	}

	protected String getSHA1(Solver solver, Dependency dep, String ext) {
		try {
			String extsha1 = ext + ".sha1";
			File hashFile = solver.getMoxieCache().getArtifact(dep, extsha1);
			if (hashFile.exists()) {
				// read cached sha1
				return FileUtils.readContent(hashFile, "\n").trim();
			}

			URL url = getURL(dep, extsha1);
			DownloadData data = download(solver, url);
			String content = new String(data.content, "UTF-8").trim();
			String hashCode = content.substring(0, 40);
			
			// set origin so that we write the artifact into the proper cache
			dep.setOrigin(getRepositoryUrl());
			
			// cache this sha1 file
			File file = solver.getMoxieCache().writeArtifact(dep, extsha1, hashCode);
			file.setLastModified(data.lastModified);
			return hashCode;
		} catch (FileNotFoundException t) {
			// this repository does not have the requested artifact
		} catch (ConnectException t) {
			// this repository does not have the requested artifact
			solver.getConsole().error("Connection error retrieving SHA1 for \"{0}\": {1}", dep.getDetailedCoordinates(), t.getMessage());
		} catch (IOException t) {
			if (t.getMessage().contains("400") || t.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				solver.getConsole().error(t, "Error retrieving SHA1 for {0}", dep.getDetailedCoordinates());
			}
		} catch (Throwable t) {
			solver.getConsole().error(t, "Error retrieving SHA1 for {0}", dep.getDetailedCoordinates());
		}
		return null;
	}
	
	protected String downloadMetadataSHA1(Solver solver, Dependency dep) {
		try {
			String extsha1 = Constants.XML + ".sha1";
			URL url = new URL(Dependency.getArtifactPath(dep, extsha1, getMetadataUrl(dep)));
			DownloadData data = download(solver, url);
			String content = new String(data.content, "UTF-8").trim();
			String hashCode = content.substring(0, 40);

			// set origin so that we write the artifact into the proper cache
			dep.setOrigin(getRepositoryUrl());

			// cache this sha1 file
			File file = solver.getMoxieCache().writeMetadata(dep, extsha1, hashCode);
			file.setLastModified(data.lastModified);
			return hashCode;
		} catch (FileNotFoundException t) {
			// this repository does not have the requested metadata
		} catch (IOException t) {
			if (t.getMessage().contains("400") || t.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				solver.getConsole().error(t, "Error retrieving metadata SHA1 for {0}", dep);
			}
		} catch (Throwable t) {
			solver.getConsole().error(t, "Error retrieving metadata SHA1 for {0}", dep);
		}
		return null;
	}
	
	public File downloadMetadata(Solver solver, Dependency dep) {
		String expectedSHA1 = "";
		if (calculateSHA1()) {
			expectedSHA1 = downloadMetadataSHA1(solver, dep);
			if (expectedSHA1 == null) {
				// there is no SHA1 for this artifact
				// check for the artifact just-in-case we can download w/o
				// checksum verification
				try {
					URL url = new URL(Dependency.getArtifactPath(dep, Constants.XML, getMetadataUrl(dep)));
					URLConnection conn = url.openConnection();
					conn.connect();
				} catch (Throwable t) {
					return null;
				}
			}
		}
		
		try {
			URL url = new URL(Dependency.getArtifactPath(dep, Constants.XML, getMetadataUrl(dep)));
			solver.getConsole().download(url.toString());
			DownloadData data = download(solver, url);
			try {
				verifySHA1(solver, expectedSHA1, data, false);
			} catch (MoxieException verification) {
				// if SHA1 verificaton fails the first time
				// wait a little bit and then repeat because it is possible
				// the repository server, or a proxy in between, has cached
				// the file and we have received a stale checksum.
				try {
					Thread.sleep(checksumRetryWaitPeriod);
				} catch (InterruptedException i) {				
				}
				
				expectedSHA1 = downloadMetadataSHA1(solver, dep);
				verifySHA1(solver, expectedSHA1, data, true);
			}
			
			Metadata oldMetadata;
			File file = solver.getMoxieCache().getMetadata(dep, Constants.XML);
			if (file != null && file.exists()) {
				oldMetadata = MetadataReader.readMetadata(file);				
			} else {
				oldMetadata = new Metadata();
			}
			
			// merge metadata
			Metadata newMetadata = MetadataReader.readMetadata(new String(data.content, "UTF-8"));				
			newMetadata.merge(oldMetadata);

			// set origin so that we write the artifact into the proper cache
			dep.setOrigin(getRepositoryUrl());

			// save merged metadata to the artifact cache
			file = solver.getMoxieCache().writeMetadata(dep, Constants.XML, newMetadata.toXML());
			file.setLastModified(data.lastModified);
					
			Date now = new Date();
			if (dep.isSnapshot()) {
				MoxieData moxiedata = solver.getMoxieCache().readMoxieData(dep);
				moxiedata.setOrigin(getRepositoryUrl());
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(newMetadata.lastUpdated);
				solver.getMoxieCache().writeMoxieData(dep, moxiedata);	
			} else {				
				// update the Moxie RELEASE metadata
				Dependency versions = DeepCopier.copy(dep);
				versions.version = Constants.RELEASE;
				
				MoxieData moxiedata = solver.getMoxieCache().readMoxieData(versions);
				moxiedata.setOrigin(getRepositoryUrl());
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(now);
				moxiedata.setRELEASE(newMetadata.release);
				moxiedata.setLATEST(newMetadata.latest);
				solver.getMoxieCache().writeMoxieData(dep, moxiedata);
				
				// update the Moxie LATEST metadata
				versions.version = Constants.LATEST;
				
				moxiedata = solver.getMoxieCache().readMoxieData(versions);
				moxiedata.setOrigin(getRepositoryUrl());
				// do not set lastDownloaded for metadata retrieval
				moxiedata.setLastChecked(now);
				moxiedata.setLastUpdated(now);
				moxiedata.setRELEASE(newMetadata.release);
				moxiedata.setLATEST(newMetadata.latest);
				solver.getMoxieCache().writeMoxieData(dep, moxiedata);	
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
				throw new RuntimeException(MessageFormat.format("Do you need to specify a proxy in {0}?", solver.getBuildConfig().getMoxieConfig().file.getAbsolutePath()), e);
			}
		}
		return null;
	}

	public File download(Solver solver, Dependency dep, String ext) {
		String expectedSHA1 = "";
		if (calculateSHA1()) {
			expectedSHA1 = getSHA1(solver, dep, ext);
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
			DownloadData data = download(solver, url);
			try {
				verifySHA1(solver, expectedSHA1, data, false);
			} catch (MoxieException verification) {
				// if SHA1 verificaton fails the first time
				// wait a little bit and then repeat because it is possible
				// the repository server, or a proxy in between, has cached
				// the file and we have received a stale checksum.
				try {
					Thread.sleep(checksumRetryWaitPeriod);
				} catch (InterruptedException i) {				
				}

				try {
					expectedSHA1 = getSHA1(solver, dep, ext);
					verifySHA1(solver, expectedSHA1, data, true);
				} catch (MoxieException e) {
					// checksum verification failed
					// delete all artifacts for this dependency
					solver.getMoxieCache().purgeArtifacts(dep.getPomArtifact(), false);
					throw e;
				}
			}
			
			// log successes
			solver.getConsole().download(dep, ext, name);
			
			// set origin so that we write the artifact into the proper cache
			dep.setOrigin(getRepositoryUrl());

			// save to the artifact cache
			File file = solver.getMoxieCache().writeArtifact(dep, ext, data.content);
			file.setLastModified(data.lastModified);
			
			// update Moxie metadata
			MoxieData moxiedata = solver.getMoxieCache().readMoxieData(dep);
			moxiedata.setOrigin(getRepositoryUrl());
			
			Date now = new Date();
			if (Constants.POM.equals(ext)) {
				Pom pom = PomReader.readPom(solver.getMoxieCache(), file, PomReader.Requirements.LOOSE);
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
			solver.getMoxieCache().writeMoxieData(dep, moxiedata);
			
			return file;
		} catch (MalformedURLException m) {
			solver.getConsole().error(m);
		} catch (FileNotFoundException e) {
			// this repository does not have the requested artifact
			solver.getConsole().debug(2, "{0} not found @ {1} repository", dep.getDetailedCoordinates(), name);
		} catch (ConnectException t) {
			// this repository does not have the requested artifact
			solver.getConsole().error("Connection error retrieving \"{0}\": {1}", dep.getDetailedCoordinates(), t.getMessage());
		} catch (IOException e) {
			if (e.getMessage().contains("400") || e.getMessage().contains("404")) {
				// disregard bad request and not found responses
				solver.getConsole().debug(2, "{0} not found @ {1} repository", dep.getDetailedCoordinates(), name);
			} else {
				java.net.Proxy proxy = solver.getBuildConfig().getProxy(name, getRepositoryUrl());
				if (java.net.Proxy.Type.DIRECT == proxy.type()) {
					throw new RuntimeException(MessageFormat.format("Do you need to specify a proxy in {0}?", solver.getBuildConfig().getMoxieConfig().file.getAbsolutePath()), e);
				} else {
					throw new RuntimeException(MessageFormat.format("Failed to use proxy {0} for {1}", proxy, getRepositoryUrl()));
				}
			}
		}
		return null;
	}
	
	private DownloadData download(Solver solver, URL url) throws IOException {
		long lastModified = System.currentTimeMillis();
		ByteArrayOutputStream buff = new ByteArrayOutputStream();

		java.net.Proxy proxy = solver.getBuildConfig().getProxy(name, getRepositoryUrl());
		solver.getConsole().debug(2, "opening {0} ({1})", getRepositoryUrl(), proxy.toString());
		URLConnection conn = url.openConnection(proxy);
		if (java.net.Proxy.Type.DIRECT != proxy.type()) {
			String auth = solver.getBuildConfig().getProxyAuthorization(name, getRepositoryUrl());
			conn.setRequestProperty("Proxy-Authorization", auth);
		}
		
		if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
			// set basic authentication header
			String auth = Base64.encodeBytes((username + ":" + password).getBytes());
			conn.setRequestProperty("Authorization", "Basic " + auth);			
		}
		
		// configure timeouts
		conn.setConnectTimeout(connectTimeout*1000);
		conn.setReadTimeout(readTimeout*1000);

		// try to get the server-specified last-modified date of this artifact
		lastModified = conn.getHeaderFieldDate("Last-Modified", lastModified);

		solver.getConsole().debug(2, "trying " + url.toString());

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
		return new DownloadData(url, data, lastModified);
	}
	
	private class DownloadData {
		final URL url;
		final byte [] content;
		final long lastModified;
		
		DownloadData(URL url, byte [] content, long lastModified) {
			this.url = url;
			this.content = content;
			this.lastModified = lastModified;
		}
	}
}
