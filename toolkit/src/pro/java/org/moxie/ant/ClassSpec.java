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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.FileSet;
import org.moxie.MoxieException;

/**
 * <p>
 * 
 * Represents a &lt;class&gt; element.
 * <p>
 * 
 * Buildfile example:
 * 
 * <pre>
 *    &lt;class name=&quot;com.riggshill.ant.genjar.GenJar&quot;/&gt;
 *    &lt;class name=&quot;com.riggshill.xml.Editor&quot; bean=&quot;yes&quot;/&gt;
 * </pre>
 * 
 * <p>
 * 
 * When the &lt;class&gt; element is encountered, a new ClassSpec is
 * instantiated to represent that element. The class is used hold the class'
 * name and manifest information.
 * </p>
 * <p>
 * 
 * The <code>resolve()</code> method is implemented to determine which classes
 * <i>this</i> class is dependent upon. A list of these classes is held for
 * later inclusion into the jar.
 * </p>
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.5 $ $Date: 2003/03/06 01:22:00 $
 */
public class ClassSpec extends DataType implements JarSpec {
	/** name of class */
	private String name = null;

	/** list of all dependent classes */
	private List<JarEntrySpec> jarEntries = new ArrayList<JarEntrySpec>();

	private List<FileSet> filesets = new ArrayList<FileSet>();

	private Project project;

	/**
	 * Constructor for the ClassSpec object
	 * 
	 * @param project
	 *            Description of the Parameter
	 */
	public ClassSpec(Project project) {
		this.project = project;
		setDescription("Representation of a java class");
	}

	/**
	 * Returns the list of classes upon which this class is dependent.
	 * 
	 * @return the list of all dependent classes
	 */
	public List<JarEntrySpec> getJarEntries() {
		return jarEntries;
	}

	/**
	 * Gets the name of the resource.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Invoked by Ant when the <code>name</code> attribute is encountered.
	 * 
	 * @param n
	 *            The new name value
	 */
	public void setName(String n) {
		name = n.replace('.', '/') + ".class";
		JarEntrySpec jes = new JarEntrySpec(name, null);

		jarEntries.add(jes);
	}

	/**
	 * Generates a list of all classes upon which this/these class is dependent.
	 * 
	 * @param gj
	 *            Description of the Parameter
	 * @throws IOException
	 *             Description of the Exception
	 */
	public void resolve(GenJar gj) throws IOException {
		//
		// handle any filesets
		//
		for (FileSet set : filesets) {
			DirectoryScanner ds = set.getDirectoryScanner(project);
			for (String file : ds.getIncludedFiles()) {
				if (file.endsWith(".class")) {
					jarEntries.add(new JarEntrySpec(file, null));
				}
			}
		}
		//
		// get depends on all class files
		//
		gj.generateDependencies(jarEntries);
	}

	/**
	 * add a fileset to be resolved later
	 * 
	 * @return Description of the Return Value
	 */
	public FileSet createFileset() {
		if (name != null) {
			throw new MoxieException(
					"Unable to add Fileset - class name already set");
		}
		FileSet set = new FileSet();
		filesets.add(set);
		return set;
	}
}