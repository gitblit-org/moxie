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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Section;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.moxie.MoxieException;
import org.moxie.utils.StringUtils;


/**
 * Driver class for the <genjar> task.
 * <p>
 * 
 * This class is instantiated when Ant encounters the &lt;genjar&gt; element.
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler</a>
 * @author Jesse Stockall
 * @version $Revision: 1.11 $ $Date: 2003/03/06 01:22:00 $
 */
public class GenJar extends Task {

	protected List<JarSpec> jarSpecs = new ArrayList<JarSpec>();

	private List<LibrarySpec> libraries = new ArrayList<LibrarySpec>();

	protected Manifest mft = Manifest.getDefaultManifest();

	protected Path classpath = null;

	private ClassFilter classFilter = null;

	protected File destFile = null;

	protected Set<String> resolvedLocal = new TreeSet<String>();

	private List<PathResolver> resolvers = new ArrayList<PathResolver>();

	private Set<String> resolved = new TreeSet<String>();
	
	protected Set<String> exportedPackages = new TreeSet<String>();
	
	private Logger logger = null;
	
	protected String version;
	
	/**
	 * main execute for genjar
	 * <ol>
	 * <li>setup logger
	 * <li>ensure classpath is setup (with any additions from sub-elements
	 * <li>initialize file resolvers
	 * <li>initialize the manifest
	 * <li>resolve resource file paths resolve class file paths generate
	 * dependency graphs for class files and resolve those paths check for
	 * duplicates
	 * <li>generate manifest entries for all candidate files
	 * <li>build jar
	 * </ol>
	 * 
	 * 
	 * @throws BuildException
	 *             Oops!
	 */
	public void execute() throws BuildException {
		logger = new Logger(getProject());
		if (classFilter == null) {
			classFilter = new ClassFilter(logger);
		}

		//
		// set up the classpath & resolvers - file/jar/zip
		//
		try {
			if (classpath == null) {
				classpath = new Path(getProject());
			}
			if (!classpath.isReference()) {
				//
				// don't like this - I could find no way to build
				// the classpath dynamically from the LibrarySpec
				// objects - the path just disappeared (has something
				// with actual execution order I think) - so here's the
				// brute force approach - if the library is of jar type
				// then it'll return a Path object that we can insert
				//
				for (LibrarySpec lib : libraries) {
					Path p = lib.getPathElement();
					if (p != null) {
						classpath.addExisting(p);
					}
				}

				//
				// add the system path now - AFTER all other paths are
				// specified
				//
				classpath.addExisting(Path.systemClasspath);
			}
			logger.verbose("Initializing Path Resolvers");
			logger.verbose("Classpath:" + classpath);
			initPathResolvers();
		} catch (IOException ioe) {
			throw new MoxieException("Unable to process classpath: " + ioe,
					getLocation());
		}

		//
		// run over all the resource and class specifications
		// given in the project file
		// resources are resolved to full path names while
		// class specifications are exploded to dependency
		// graphs - when done, getJarEntries() returns a list
		// of all entries generated by this JarSpec
		//
		List<JarEntrySpec> entries = new ArrayList<JarEntrySpec>();

		for (JarSpec js : jarSpecs) {
			try {
				js.resolve(this);
			} catch (FileNotFoundException ioe) {
				throw new MoxieException("Unable to resolve: " + js.getName()
						+ "\nFileNotFound=" + ioe.getMessage(), ioe,
						getLocation());
			} catch (IOException ioe) {
				throw new MoxieException("Unable to resolve: " + js.getName()
						+ "\nMSG=" + ioe.getMessage(), ioe, getLocation());
			}
			//
			// before adding a new jarspec - see if it already exists
			// first entry added to jar always wins
			//
			for (JarEntrySpec spec : js.getJarEntries()) {
				if (!entries.contains(spec)) {
					entries.add(spec);
				} else {
					logger.verbose("Duplicate (ignored): " + spec.getJarName());
				}
			}
		}
		//
		// we have all the entries we're gonna jar - the manifest
		// must be fully built prior to jar generation, so run over
		// each entry and and add it to the manifest
		//
		for (JarEntrySpec jes : entries) {
			if (jes.getSourceFile() == null) {
				try {
					InputStream is = resolveEntry(jes);
					if (is != null) {
						is.close();
					}
				} catch (IOException ioe) {
					throw new MoxieException(
							"Error while generating manifest entry for: "
									+ jes.toString(), ioe, getLocation());
				}
			}
		}

		JarOutputStream jout = null;
		InputStream is = null;
		try {
			jout = new JarOutputStream(new FileOutputStream(destFile), createJarManifest());
			writeJarEntries(jout);
			
			for (JarEntrySpec jes : entries) {
				JarEntry entry = new JarEntry(jes.getJarName());
				is = resolveEntry(jes);

				if (is == null) {
					logger.error("Unable to locate previously resolved resource");
					logger.error("       Jar Name:" + jes.getJarName());
					logger.error("Resolved Source:" + jes.getSourceFile());
					try {
						if (jout != null) {
							jout.close();
						}
					} catch (IOException ioe) {
					}
					throw new MoxieException("Jar component not found: "
							+ jes.getJarName(), getLocation());
				}
				jout.putNextEntry(entry);
				byte[] buff = new byte[4096]; // stream copy buffer
				int len;
				while ((len = is.read(buff, 0, buff.length)) != -1) {
					jout.write(buff, 0, len);
				}
				jout.closeEntry();
				is.close();

				logger.verbose("Added: " + jes.getJarName());
			}
		} catch (IOException ioe) {
			throw new MoxieException("Unable to create jar: "
					+ destFile.getName(), ioe, getLocation());
		} finally {
			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException ioe) {
			}
			try {
				if (jout != null) {
					jout.close();
				}
			} catch (IOException ioe) {
			}
		}
		
		// Close all the resolvers
		for (PathResolver resolver : resolvers) {
			try {
				resolver.close();
			} catch (IOException ioe) {
			}
		}
	}
	
	protected void writeJarEntries(JarOutputStream jos) {
	}
	
	/**
	 * Sets the classpath attribute.
	 * 
	 * @param s
	 *            The new classpath.
	 */
	public void setClasspath(Path s) {
		createClasspath().append(s);
	}

	/**
	 * Builds the classpath.
	 * 
	 * @return A <path>
	 * 
	 *         element.
	 */
	public Path createClasspath() {
		if (classpath == null) {
			classpath = new Path(getProject());
		}
		return classpath;
	}

	/**
	 * Sets the Classpathref attribute.
	 * 
	 * @param r
	 *            The new classpathRef.
	 */
	public void setClasspathRef(Reference r) {
		createClasspath().setRefid(r);
	}

	/**
	 * Builds a <class> element.
	 * 
	 * @return A <class> element.
	 */
	public ClassSpec createClass() {
		ClassSpec cs = new ClassSpec(getProject());
		jarSpecs.add(cs);
		return cs;
	}

	/**
	 * Builds a manifest element.
	 * 
	 * @return A <manifest> element.
	 */
	public Manifest createManifest() {
		return mft;
	}
	
	@SuppressWarnings("unchecked")
	private java.util.jar.Manifest createJarManifest() {
		java.util.jar.Manifest newManifest = new java.util.jar.Manifest();
		newManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		
		Section mainSection = mft.getMainSection();
		Enumeration<String> mainAttributes = mainSection.getAttributeKeys();	
		while (mainAttributes.hasMoreElements()) {
			String aname = mainAttributes.nextElement();
			String avalue = mainSection.getAttributeValue(aname);
			newManifest.getMainAttributes().putValue(aname, avalue);
		}
		
		// OSGI Export-Package
		if (!StringUtils.isEmpty(version)) {
			String vString = MessageFormat.format(";version=\"{0}\",", version);
			StringBuilder packages = new StringBuilder();
			for (String packageName : exportedPackages) {
				packages.append(packageName);
				packages.append(vString);
			}
			if (packages.length() > 0) {
				packages.setLength(packages.length() - 1);
				newManifest.getMainAttributes().putValue("Export-Package", packages.toString());
			}
		}

//		for (Map.Entry<Object, Object> entry : newManifest.getMainAttributes().entrySet()) {
//			System.out.println(entry.getKey() + "=" + entry.getValue());
//		}

		Enumeration<String> sections = mft.getSectionNames();
		while (sections.hasMoreElements()) {
			String sname = sections.nextElement();
			Section section = mft.getSection(sname);
			
			Attributes newAttributes = new Attributes();
			newManifest.getEntries().put(sname, newAttributes);
			
			Enumeration<String> attributes = section.getAttributeKeys();	
			while (attributes.hasMoreElements()) {
				String aname = attributes.nextElement();
				String avalue = section.getAttributeValue(aname);
				newAttributes.putValue(aname, avalue);
			}
			
//			for (Map.Entry<Object, Object> entry : newManifest.getAttributes(sname).entrySet()) {
//				System.out.println(entry.getKey() + "=" + entry.getValue());
//			}
		}
		
		return newManifest;
	}

	/**
	 * Builds a resource element.
	 * 
	 * @return A <resource> element.
	 */
	public Resource createResource() {
		Resource rsc = new Resource(getProject());
		jarSpecs.add(rsc);
		return rsc;
	}

	/**
	 * Builds a classfilter element.
	 * 
	 * @return A <classfilter> element.
	 */
	public ClassFilter createClassfilter() {
		if (classFilter == null) {
			classFilter = new ClassFilter(new Logger(getProject()));
		}
		return classFilter;
	}

	/**
	 * Builds a library element.
	 * 
	 * @return A <library> element.
	 */
	public LibrarySpec createLibrary() {
		LibrarySpec lspec = new LibrarySpec(getProject().getBaseDir(),
				new Path(getProject()));
		jarSpecs.add(lspec);
		libraries.add(lspec);
		return lspec;
	}

	/**
	 * Sets the name of the jar file to be created.
	 * 
	 * @param destFile
	 *            The new destfile value
	 */
	public void setDestfile(File destFile) {
		this.destFile = destFile;
	}

	/**
	 * Iterate through the classpath and create an array of all the
	 * <code>PathResolver</code>s
	 * 
	 * @throws IOException
	 *             Description of the Exception
	 */
	private void initPathResolvers() throws IOException {
		for (String pc : classpath.list()) {
			File f = new File(pc);
			if (!f.exists()) {
				continue;
			}

			if (f.isDirectory()) {
				resolvers.add(new FileResolver(f, logger));
			} else if (f.getName().toLowerCase().endsWith(".jar")) {
				resolvers.add(new JarResolver(f, logger));
			} else if (f.getName().toLowerCase().endsWith(".zip")) {
				resolvers.add(new ZipResolver(f, logger));
			} else {
				throw new MoxieException(f.getName()
						+ " is not a valid classpath component", getLocation());
			}
		}
	}

	/**
	 * Description of the Method
	 * 
	 * @param spec
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 * @throws IOException
	 *             Description of the Exception
	 */
	InputStream resolveEntry(JarEntrySpec spec) throws IOException {
		InputStream is = null;
		for (PathResolver resolver : resolvers) {
			is = resolver.resolve(spec);
			if (is != null) {
				if (resolver instanceof FileResolver) {
					// keep track of class files and packages added from output folders
					if (spec.getJarName().endsWith(".class")) {
						String className = spec.getJarName();
						resolvedLocal.add(className);
						
						if (className.lastIndexOf('/') > -1) {
							String packageName = className.substring(0, className.lastIndexOf('/'));						
							exportedPackages.add(packageName);
						}
					}
				}
				return is;
			}
		}
		return null;
	}

	/**
	 * Resolves a partial file name against the classpath elements
	 * 
	 * @param cname
	 *            Description of the Parameter
	 * @return An InputStream open on the named file or null
	 * @throws IOException
	 *             Description of the Exception
	 */
	InputStream resolveEntry(String cname) throws IOException {
		InputStream is = null;

		for (PathResolver resolver : resolvers) {
			is = resolver.resolve(cname);
			if (is != null) {
				return is;
			}
		}
		return null;
	}

	/**
	 * Generates a list of all classes upon which the list of classes depend.
	 * 
	 * @param entries
	 *            List of <code>JarEntrySpec</code>s used as a list of class
	 *            names from which to start.
	 * @exception IOException
	 *                If there's an error reading a class file
	 */
	void generateDependencies(List<JarEntrySpec> entries) throws IOException {
		Set<String> dependents = new TreeSet<String>();

		for (JarEntrySpec js : entries) {
			generateClassDependencies(js.getJarName(), dependents);
		}

		for (String dependent : dependents) {
			entries.add(new JarEntrySpec(dependent, null));
		}
	}

	/**
	 * Generates a list of classes upon which the named class is dependent.
	 * 
	 * @param classes
	 *            A List into which the class names are placed
	 * @param classFileName
	 *            Description of the Parameter
	 * @throws IOException
	 *             Description of the Exception
	 */
	void generateClassDependencies(String classFileName, Set<String> classes)
			throws IOException {
		if (!resolved.contains(classFileName)) {
			resolved.add(classFileName);
			InputStream is = resolveEntry(classFileName);
			if (is == null) {
				throw new FileNotFoundException(classFileName);
			}

			List<String> referenced = ClassUtil.getDependencies(is);

			for (String name : referenced) {
				String cname = name + ".class";

				if (!classFilter.include(cname) || resolved.contains(cname)) {
					continue;
				}

				classes.add(cname);
				generateClassDependencies(cname, classes);
			}
			is.close();
		}
	}
}
