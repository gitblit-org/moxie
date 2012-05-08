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
package com.maxtk.ant;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import com.maxtk.Build;
import com.maxtk.Constants.Key;
import com.maxtk.ant.Mft.MftAttr;
import com.maxtk.utils.StringUtils;

public class MaxJar extends GenJar {

	ClassSpec mainclass;

	boolean fatjar;

	/**
	 * Builds a <mainclass> element.
	 * 
	 * @return A <mainclass> element.
	 */
	public ClassSpec createMainclass() {
		if (mainclass == null) {
			ClassSpec cs = new ClassSpec(getProject());
			mainclass = cs;
			jarSpecs.add(cs);
			return cs;
		}
		throw new BuildException("Can only specify one main class");
	}

	public void setFatjar(boolean value) {
		this.fatjar = value;
	}

	@Override
	public void execute() throws BuildException {
		Build build = (Build) getProject().getReference(Key.build.maxId());
		build.console.header();
		build.console.log("MaxJar");
		build.console.header();

		// automatic manifest entries from Maxilla metadata
		setManifest("Created-By", "Maxilla");
		setManifest("Build-Jdk", System.getProperty("java.version"));
		setManifest("Build-Date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

		setManifest("Implementation-Title", Key.name);
		setManifest("Implementation-Vendor", Key.vendor);
		setManifest("Implementation-Vendor-Id", Key.groupId);
		setManifest("Implementation-Vendor-URL", Key.url);
		setManifest("Implementation-Version", Key.version);

		setManifest("Bundle-Name", Key.name);
		setManifest("Bundle-SymbolicName", Key.artifactId);
		setManifest("Bundle-Version", Key.version);
		setManifest("Bundle-Vendor", Key.vendor);

		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			setManifest("Main-Class", mc);
		}
		// if (splash != null) {
		// setManifest("SplashScreen-Image", splash);
		// }

		// TODO specify import-package? and export-package?
		
		// automatic classpath resolution, if not manually specified
		if (classpath == null) {
			Object o = getProject().getReference(Key.compile_classpath.maxId());
			if (o != null && o instanceof Path) {
				Path cp = (Path) o;
				if (fatjar) {
					// FatJar generation
					classpath = createClasspath();					
					for (String path : cp.list()) {
						if (path.toLowerCase().endsWith(".jar")) {
							LibrarySpec lib = createLibrary();
							lib.setJar(path);
						} else {
							PathElement element = classpath.createPathElement();
							element.setPath(path);
						}
					}
				} else {
					// standard GenJar class dependency resolution
					classpath = cp;
				}
			}
		}
		
		if (destFile == null) {
			// default output jar if file unspecified
			String name = getProject().getProperty(Key.artifactId.maxId());
			if (!StringUtils.isEmpty(getProject().getProperty(Key.version.maxId()))) {
				name += "-" + getProject().getProperty(Key.version.maxId());
			}
			destFile = new File(name + ".jar");
		}
		
		if (destFile != null) {
			destFile.getParentFile().mkdirs();
			build.console.log(1, destFile.getAbsolutePath());
		} else if (destDir != null) {
			destDir.mkdirs();
			build.console.log(1, "class structure => " + destDir);
		}
		
		long start = System.currentTimeMillis();
		super.execute();

		if (destFile != null) {
			build.console.log(1, "{0} KB, generated in {1} ms", (destFile.length()/1024), System.currentTimeMillis() - start);
		}
	}

	void setManifest(String key, String value) {
		// do not override a manual specification
		for (Object obj : mft.attrs) {
			MftAttr attr = (MftAttr) obj;
			if (attr.getName().equals(key)) {
				return;
			}
		}
		MftAttr attr = (MftAttr) mft.createAttribute();
		attr.setName(key);
		attr.setValue(value);
	}

	void setManifest(String key, Key prop) {
		String value = getProject().getProperty(prop.maxId());
		if (!StringUtils.isEmpty(value)) {
			setManifest(key, value);
		}
	}
}
