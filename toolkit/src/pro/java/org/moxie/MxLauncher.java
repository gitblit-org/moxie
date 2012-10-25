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
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Launch helper class that adds all jars found in the local "lib" & "ext"
 * folders and then calls the application main. Using this technique we do not
 * have to specify a classpath and we can dynamically add jars to the
 * distribution.
 * 
 * @author James Moger
 * 
 */
public class MxLauncher {

	public static final boolean DEBUG = false;

	/**
	 * Parameters of the method to add an URL to the System classes.
	 */
	private static final Class<?>[] PARAMETERS = new Class[] { URL.class };

	public static void main(String[] args) throws Exception {
		if (DEBUG) {
			System.out.println("jcp=" + System.getProperty("java.class.path"));
			ProtectionDomain protectionDomain = MxLauncher.class.getProtectionDomain();
			System.out.println("launcher="
					+ protectionDomain.getCodeSource().getLocation().toExternalForm());
		}
		
		// Extract the mxMain- attributes from the manifest
		String appClassName = "";
		String appPaths = "lib, ext";
		Enumeration<URL> resources = MxLauncher.class.getClassLoader()
				.getResources("META-INF/MANIFEST.MF");
		while (resources.hasMoreElements()) {
			try {
				Manifest manifest = new Manifest(resources.nextElement().openStream());
				Attributes attributes = manifest.getMainAttributes();
				String className = attributes.getValue("mxMain-Class");
				if (className != null && className.trim().length() > 0) {
					appClassName = className;
				}
				String paths = attributes.getValue("mxMain-Paths");
				if (paths != null && paths.trim().length() > 0) {
					appPaths = paths;
				}

			} catch (IOException E) {
			}
		}

		if (appClassName == null || appClassName.trim().length() == 0) {
			// failed to find mxMain-Class
			System.err.println("Please define mxMain-Class in your launcher jar manifest!");
			System.exit(-1);
		}

		// Load the JARs in the lib and ext folder
		String[] folders = appPaths.split(",");
		List<File> jars = new ArrayList<File>();
		for (String folder : folders) {
			if (folder == null) {
				continue;
			}
			File libFolder = new File(folder.trim());
			if (!libFolder.exists()) {
				continue;
			}
			List<File> found = findJars(libFolder.getAbsoluteFile());
			jars.addAll(found);
		}
		// sort the jars by name and then reverse the order so the newer version
		// of the library gets loaded in the event that this is an upgrade
		Collections.sort(jars);
		Collections.reverse(jars);

		if (jars.size() == 0) {
			for (String folder : folders) {
				File libFolder = new File(folder);
				if (libFolder.exists()) {
					System.err.println("Failed to find any JARs in " + libFolder.getPath());
				}
			}
		} else {
			for (File jar : jars) {
				try {
					jar.canRead();
					addJarFile(jar);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}

		
		
		// launch the app class
		Class<?> appClass = Class.forName(appClassName);
		Method main = appClass.getMethod("main", String [].class);
		main.invoke(null, new Object[] { args });		
	}

	public static List<File> findJars(File folder) {
		List<File> jars = new ArrayList<File>();
		if (folder.exists()) {
			File[] libs = folder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return !file.isDirectory() && file.getName().toLowerCase().endsWith(".jar");
				}
			});
			if (libs != null && libs.length > 0) {
				jars.addAll(Arrays.asList(libs));
				if (DEBUG) {
					for (File jar : jars) {
						System.out.println("found " + jar);
					}
				}
			}
		}

		return jars;
	}

	/**
	 * Adds a file to the classpath
	 * 
	 * @param f
	 *            the file to be added
	 * @throws IOException
	 */
	public static void addJarFile(File f) throws IOException {
		if (f.getName().indexOf("-sources") > -1 || f.getName().indexOf("-javadoc") > -1) {
			// don't add source or javadoc jars to runtime classpath
			return;
		}
		URL u = f.toURI().toURL();
		if (DEBUG) {
			System.out.println("load=" + u.toExternalForm());
		}
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { u });
		} catch (Throwable t) {
			throw new IOException(MessageFormat.format(
					"Error, could not add {0} to system classloader", f.getPath()), t);
		}
	}
}
