/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000, 2001, 2002, 2003 Jesse Stockall.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Tomcat", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 */
package org.moxie.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Path;

/**
 * This class encapsulates the concept of a library - either a set of files in a
 * directory or a jar's content. If a directory is specified, all files in that
 * directory tree are loaded into the jar. If a jar is specified, then all files
 * from that jar are copied into the target jar (except the manifest file).
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.8 $ $Date: 2003/03/06 01:22:01 $
 */
public class LibrarySpec extends DataType implements JarSpec {
	private File baseDir = null;

	private Path classpath = null;

	private File jar = null;

	private File dir = null; // the actual dir to use

	private List<JarEntrySpec> jarEntries = new ArrayList<JarEntrySpec>();

	private String chopPath = null;

	/**
	 * Constructor for the LibrarySpec object
	 * 
	 * @param baseDir
	 *            Description of the Parameter
	 * @param classpath
	 *            Description of the Parameter
	 */
	public LibrarySpec(File baseDir, Path classpath) {
		this.baseDir = baseDir;
		this.classpath = classpath;
	}

	/**
	 * Gets the pathElement attribute of the LibrarySpec object
	 * 
	 * @return The pathElement value
	 */
	public Path getPathElement() {
		return classpath;
	}

	/**
	 * Gets the jarEntries attribute of the LibrarySpec object
	 * 
	 * @return The jarEntries value
	 */
	public List<JarEntrySpec> getJarEntries() {
		return jarEntries;
	}

	/**
	 * Gets the name of the resource.
	 * 
	 * @return Thr name
	 */
	public String getName() {
		return jar.getName();
	}

	/**
	 * Sets the jar attribute.
	 * 
	 * @param file
	 *            The file to include as a library.
	 * @throws BuildException
	 *             Description of the Exception
	 */
	public void setJar(String file) throws BuildException {
		if (dir != null) {
			throw new BuildException(
					"GenJar: Can't specify both file and dir in a <library> element");
		}

		File jarFile;
		//
		// try as an absolute path first - if not found
		// then try as a relative path - if still not
		// found then puke
		//
		jarFile = new File(file);
		if (!jarFile.exists()) {
			jarFile = new File(baseDir, file);
			if (!jarFile.exists()) {
				throw new BuildException(
						"GenJar: specified library jar not found (" + file
								+ ")");
			}
		}
		this.jar = jarFile;
		classpath.setLocation(jarFile);
	}

	/**
	 * Sets the dir attribute.
	 * 
	 * @param dir
	 *            The directory to load files from.
	 */
	public void setDir(String dir) {
		File dirFile = null;

		// Add the library dir path to the classpath
		// so files can be found with out a classpath being set.
		classpath.setLocation(new File(dir));

		if (jar != null) {
			throw new BuildException(
					"GenJar: Can't specify both file and dir in a <library> element");
		}
		//
		// if they speced an absolute path then handle it w/o the
		// project baseDir - otherwise we've gotta use the baseDir
		// to make sure we're refering to the right thing
		//
		// the chopPath string is used later to remove part of each file's
		// path so their 'jar names' are relative to the speced dir
		//
		if (dir.charAt(0) == '/' || dir.charAt(0) == '\\'
				|| dir.charAt(1) == ':') {
			if (dir.endsWith("/*")) {
				dir = dir.substring(0, dir.length() - 2);
				chopPath = dir;
			} else {
				chopPath = "";
			}
			dirFile = new File(dir);
		} else {
			if (dir.endsWith("/*") || dir.endsWith("\\*")) {
				dir = dir.substring(0, dir.length() - 2);
				File temp = new File(baseDir, dir);
				chopPath = temp.toString();
			} else {
				chopPath = baseDir.toString();
			}
			dirFile = new File(baseDir, dir);
		}
		if (!dirFile.exists()) {
			throw new BuildException(
					"GenJar: Specified library dir not found (" + jar + ")");
		}
		this.dir = dirFile;
	}

	/**
	 * Description of the Method
	 * 
	 * @param gj
	 *            Description of the Parameter
	 * @throws IOException
	 *             Description of the Exception
	 */
	public void resolve(GenJar gj) throws IOException {
		if (jar != null) {
			resolveJar();
		} else {
			resolveDir();
		}
	}

	/**
	 * Locate the library jar file and add all the entries to list of jar
	 * entries.
	 */
	private void resolveJar() {
		try {
			JarFile jarFile = new JarFile(jar);
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntrySpec je = new JarEntrySpec();
				ZipEntry zentry = (ZipEntry) entries.nextElement();
				//
				// zip directories are not allowed - they screw
				// up the file resolvers BIG TIME
				//
				if (zentry.isDirectory()) {
					continue;
				}

				// disallow the contents of the META-INF directory,
				// this means Manifests, Index lists and signing information
				String name = zentry.getName();
				if (name.startsWith("META-INF")) {
					if (name.toUpperCase().endsWith(".SF")
							|| name.toUpperCase().endsWith(".RSA")
							|| name.toUpperCase().endsWith(".MF"))
						continue;
				}

				je.setJarName(name);

				jarEntries.add(je);
			}
			jarFile.close();
		} catch (IOException ioe) {
			throw new BuildException("Error while reading library jar", ioe);
		}
	}

	/**
	 * resolves a dir into a slew of JarEntrySpec objects this just calls
	 * _resolveDir to start the recursive descent of the dir structure
	 */
	private void resolveDir() {
		resolveDir(dir);
	}

	/**
	 * Description of the Method
	 * 
	 * @param root
	 *            Description of the Parameter
	 */
	private void resolveDir(File root) {
		if (!root.isDirectory()) {
			throw new IllegalStateException("resolveDir:root is not a dir!");
		}

		for (File file: root.listFiles()) {
			if (file.isDirectory()) {
				resolveDir(file);
				continue;
			}
			JarEntrySpec je = new JarEntrySpec();
			je.setJarName(genJarName(file));
			je.setSourceFile(file);
			jarEntries.add(je);
		}
	}

	/**
	 * Strips the leading path back off of the file names we want the jar names
	 * to begin where specified in the build file - not absolute paths
	 * 
	 * @param f
	 *            File to strip.
	 * @return New path.
	 */
	private String genJarName(File f) {
		return f.getPath().replace('\\', '/').substring(chopPath.length() + 1);
	}
}