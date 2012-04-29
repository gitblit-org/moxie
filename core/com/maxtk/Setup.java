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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.maxtk.PomReader.PomDep;
import com.maxtk.maxml.MaxmlException;
import com.maxtk.utils.FileUtils;
import com.maxtk.utils.StringUtils;

public class Setup {

	public static PrintStream out = System.out;

	private static File mavenDir = new File(System.getProperty("user.home") + "/.m2/repository");
	private static File maxillaSettings = new File(System.getProperty("user.home") + "/.maxilla/settings.maxml");
	public static File maxillaDir = new File(System.getProperty("user.home") + "/.maxilla/repository");

	public static Config execute(String configFile, boolean verbose) throws IOException, MaxmlException {
		String file = "build.maxml";
		if (!StringUtils.isEmpty(configFile)) {
			file = configFile;
		}

		// make the maxilla folder
		maxillaSettings.getParentFile().mkdirs();

		if (!maxillaSettings.exists()) {
			// write default maxilla settings
			FileWriter writer = new FileWriter(maxillaSettings);
			writer.append("proxies:\n- { id: myproxy, active: false, protocol: http, host:proxy.somewhere.com, port:8080, username: proxyuser, password: somepassword }");
			writer.close();
		}

		Settings settings = Settings.load(maxillaSettings);

		if (verbose) {
			out.println("Maxilla - Project Build Toolkit v" + Constants.VERSION);
			out.println(Constants.HDR);
			settings.describe(out);
			out.println(Constants.SEP);
		}

		Config conf = Config.load(new File(file));
		if (verbose) {
			conf.describe(out);
		}

		// download or copy the dependencies, if necessary
		List<Dependency> allDependencies = new ArrayList<Dependency>();
		for (Dependency obj : conf.compileDependencies) {
			List<Dependency> set = retrieveArtifact(settings, conf.mavenUrls, conf.dependencyFolder, obj);
			allDependencies.addAll(set);
		}
		for (Dependency obj : conf.providedDependencies) {
			List<Dependency> set = retrieveArtifact(settings, conf.mavenUrls, conf.dependencyFolder, obj);
			allDependencies.addAll(set);
		}
		for (Dependency obj : conf.runtimeDependencies) {
			List<Dependency> set = retrieveArtifact(settings, conf.mavenUrls, conf.dependencyFolder, obj);
			allDependencies.addAll(set);
		}
		for (Dependency obj : conf.testDependencies) {
			List<Dependency> set = retrieveArtifact(settings, conf.mavenUrls, conf.dependencyFolder, obj);
			allDependencies.addAll(set);
		}
		if (allDependencies.size() > 0) {
			out.println(Constants.SEP);
		}		
		return conf;
	}

	/**
	 * Downloads an internal dependency to the local Maven repository and
	 * manually loads that dependency on the classpath.
	 * 
	 * @param config
	 * @param dependencies
	 */
	public static void retriveInternalDependency(Config config, Dependency... dependencies) throws IOException, MaxmlException {

		Settings settings = Settings.load(maxillaSettings);

		// download from Maven
		config.addMavenUrl(Constants.MAVENCENTRAL);
		for (Dependency dependency : dependencies) {
			retrieveArtifact(settings, config.mavenUrls, null, dependency);
		}

		// load classpath from local maven or maxilla repository
		Class<?>[] PARAMETERS = new Class[] { URL.class };
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		File[] cpFolders = new File[] { mavenDir, maxillaDir };
		for (Dependency dependency : dependencies) {
			for (File folder : cpFolders) {
				File localFile = new File(folder, dependency.getArtifactPath(Dependency.LIB));
				if (localFile.exists()) {
					try {
						URL u = localFile.toURI().toURL();
						Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
						method.setAccessible(true);
						method.invoke(sysloader, new Object[] { u });
					} catch (Throwable t) {
						System.err.println(MessageFormat
										.format("Error, could not add {0} to system classloader",
												localFile.getPath()));
						t.printStackTrace();
					}
					break;
				}
			}
		}
	}

	/**
	 * Download an artifact from a local or remote Maven or Maxilla repository.
	 * 
	 * @param settings
	 * @param mavenUrls
	 *            the urls of available remote Maven repositories
	 * @param obj
	 *            the maven object to download.
	 * @return
	 */
	static List<Dependency> retrieveArtifact(Settings settings, List<String> mavenUrls, File libsFolder, Dependency obj) {
		List<Dependency> allDependencies = new ArrayList<Dependency>();
		allDependencies.add(obj);
		for (String mavenUrl : mavenUrls) {
			String[] jarTypes = { Dependency.POM, Dependency.LIB, Dependency.SRC };
			for (String fileType : jarTypes) {
				// check to see if we already have the artifact
				File localSource;
				if (libsFolder == null) {
					// retrieving internal dependency
					// use a local Maven artifact if we have one
					localSource = new File(mavenDir, obj.getArtifactPath(fileType));

					if (!localSource.exists()) {
						// download the artifact to the Maxilla repository
						localSource = new File(maxillaDir, obj.getArtifactPath(fileType));
					}
				} else if (maxillaDir.equals(libsFolder)) {
					// using artifact directly from maxilla cache
					localSource = new File(maxillaDir, obj.getArtifactPath(fileType));						
				} else {
					// retrieving artifact into project-specified folder
					localSource = new File(libsFolder, obj.getArtifactName(fileType));
				}

				if (localSource.exists()) {
					// we already have the artifact
					// this could be a project dependency OR an internal
					// dependency
					continue;
				}

				String projectIntro = "   " + localSource.getParentFile().toString() + " <= ";

				// check the local repositories for the artifact
				if (obj.isMavenObject()) {
					File mavenFile = new File(mavenDir, obj.getArtifactPath(fileType));
					File maxillaFile = new File(maxillaDir, obj.getArtifactPath(fileType));
					if (mavenFile.exists()) {
						// we have the artifact in the local Maven repository
						if (Dependency.POM.equals(fileType)) {
							// do not copy pom to project folder
							continue;
						}
						// copy existing artifact
						try {
							out.print(projectIntro);
							out.println(mavenFile);
							FileUtils.copy(libsFolder, mavenFile);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						continue;
					} else if (maxillaFile.exists()) {
						// we have the artifact in the local Maxilla repository
						if (Dependency.POM.equals(fileType)) {
							// do not copy pom to project folder
							continue;
						}
						// copy existing artifact
						try {
							out.print(projectIntro);
							out.println(maxillaFile);
							FileUtils.copy(libsFolder, maxillaFile);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						continue;
					}
				}

				String expectedSHA1 = null;
				if (obj.isMavenObject()) {
					if (StringUtils.isEmpty(obj.version)) {
						// TODO implement "latest version" determination
						out.println(MessageFormat.format("   SKIPPING {0}, dependencyManagement node not yet supported", obj.getArtifactName(fileType)));
						continue;
					}
					expectedSHA1 = downloadSHA1(mavenUrl, obj, fileType);
					if (StringUtils.isEmpty(expectedSHA1)) {
						// could not connect to the Maven repository or
						// the Maven repository does not have this artifact
						continue;
					}
				}

				String fullUrl = StringUtils.makeUrl(mavenUrl, obj.getArtifactPath(fileType));
				if (!localSource.getAbsoluteFile().getParentFile().exists()) {
					if (!localSource.getAbsoluteFile().getParentFile().mkdirs()) {
						throw new RuntimeException("Failed to create destination folder structure!");
					}
				}

				String maxillaIntro = "   " + maxillaDir.toString() + " <= ";
				out.print(maxillaIntro);
				out.println(fullUrl);

				ByteArrayOutputStream buff = new ByteArrayOutputStream();
				try {
					java.net.Proxy proxy = settings.getProxy(mavenUrl);
					URL url = new URL(fullUrl);
					URLConnection conn = url.openConnection(proxy);
					if (java.net.Proxy.Type.DIRECT != proxy.type()) {
						String auth = settings.getProxyAuthorization(mavenUrl);
						conn.setRequestProperty("Proxy-Authorization", auth);
					}
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
				} catch (IOException e) {
					throw new RuntimeException("Error downloading " + fullUrl
							+ " to " + localSource
							+ "\nDo you need to specify a proxy server in "
							+ maxillaSettings.getAbsolutePath() + "?", e);
				}
				byte[] data = buff.toByteArray();
				String calculatedSHA1 = StringUtils.getSHA1(data);

				if (!StringUtils.isEmpty(expectedSHA1) && !calculatedSHA1.equals(expectedSHA1)) {
					throw new RuntimeException("SHA1 checksum mismatch; got: " + calculatedSHA1);
				}

				// save artifact to the local Maxilla repository
				File maxillaFile = new File(maxillaDir, obj.getArtifactPath(fileType));
				try {
					maxillaFile.getParentFile().mkdirs();
					RandomAccessFile ra = new RandomAccessFile(maxillaFile, "rw");
					ra.write(data);
					ra.setLength(data.length);
					ra.close();
				} catch (IOException e) {
					throw new RuntimeException("Error writing to file " + maxillaFile, e);
				}

				// save artifact to the project dependency folder
				if (!Dependency.POM.equals(fileType) && libsFolder != null) {
					File projectFile = new File(libsFolder, obj.getArtifactName(fileType));
					try {
						out.println(StringUtils.leftPad("", maxillaIntro.length() - 3, ' ') + "=> " + projectFile);
						projectFile.getParentFile().mkdirs();
						RandomAccessFile ra = new RandomAccessFile(projectFile, "rw");
						ra.write(data);
						ra.setLength(data.length);
						ra.close();
					} catch (IOException e) {
						throw new RuntimeException("Error writing to file " + projectFile, e);
					}
				}
			}
		}

		// Read the POM and retrieve any transitive dependencies
		if (obj.isMavenObject() && obj.resolveTransitiveDependencies) {
			try {
				File pom = new File(maxillaDir, obj.getArtifactPath(Dependency.POM));
				if (pom.exists()) {
					List<PomDep> list = PomReader.readDependencies(pom);
					if (list.size() > 0) {
						out.append("resolving transitive dependencies for " + obj).append('\n');
					}
					for (PomDep dep : list) {
						out.append("   ").append(dep.toString()).append('\n');
						if (dep.resolveDependencies()) {
							Dependency dependency = new Dependency(dep.groupId, dep.artifactId, dep.version, null);
							List<Dependency> transitives = retrieveArtifact(settings, mavenUrls, libsFolder, dependency);
							obj.transitiveDependencies.addAll(transitives);
							allDependencies.addAll(transitives);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return allDependencies;
	}

	static String downloadSHA1(String mavenUrl, Dependency obj, String jarType) {
		try {
			File hashFile = new File(maxillaDir, obj.getArtifactPath(jarType) + ".sha1");
			if (hashFile.exists()) {
				return FileUtils.readContent(hashFile, "\n").trim();
			}
			hashFile.getParentFile().mkdirs();
			String hashUrl = StringUtils.makeUrl(mavenUrl, obj.getArtifactPath(jarType) + ".sha1");
			ByteArrayOutputStream buff = new ByteArrayOutputStream();
			URL url = new URL(hashUrl);
			InputStream in = new BufferedInputStream(url.openStream());
			byte[] buffer = new byte[80];
			while (true) {
				int len = in.read(buffer);
				if (len < 0) {
					break;
				}
				buff.write(buffer, 0, len);
			}
			in.close();
			String content = buff.toString("UTF-8").trim();
			String hashCode = content.substring(0, 40);
			FileUtils.writeContent(hashFile, hashCode);
			return hashCode;
		} catch (FileNotFoundException t) {
			// swallow these errors, this is how we tell if Maven does not have
			// the requested artifact
		} catch (IOException t) {
			if (t.getMessage().contains("400") || t.getMessage().contains("404")) {
				// disregard bad request and not found responses
			} else {
				t.printStackTrace();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}
}
