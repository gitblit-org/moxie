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
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.FileSet;

/**
 * Represents a &lt;resource&gt; element within the project file. </p>
 * <p>
 * 
 * In addition to holding the final <i>jar name</i> of the resource, it performs
 * the actual resolution of file names along with expansion of <i>filesets</i> .
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.4 $ $Date: 2003/03/06 01:22:01 $
 */
public class Resource extends DataType implements JarSpec {
	private Project project;

	private List<JarEntrySpec> jarEntries = new ArrayList<JarEntrySpec>();

	private List<FileSet> filesets = new ArrayList<FileSet>();

	private File file = null;

	private String pkg = null;

	/**
	 * Constructs a new Resource element.
	 * 
	 * @param proj
	 *            the owning project
	 */
	Resource(Project proj) {
		project = proj;
	}

	/**
	 * Returns a List of all JarEntry objects collected by this Resource
	 * 
	 * @return List all collected JarEntry objects
	 */
	public List<JarEntrySpec> getJarEntries() {
		return jarEntries;
	}

	/**
	 * Sets the value of the file attribute. When the resource element ha a
	 * single file attribute, Ant calls this to 'set' the value.
	 * 
	 * @param f
	 *            The file to be included in the jar as a resource.
	 */
	public void setFile(File f) {
		if (filesets.size() > 0) {
			throw new BuildException("can't add 'file' - fileset already used");
		}
		file = f;
	}

	/**
	 * Gets the name of the resource.
	 * 
	 * @return Thr name
	 */
	public String getName() {
		return file.getName();
	}

	/**
	 * synonym for 'file'
	 * 
	 * @param f
	 *            The new name value
	 * @deprecated Use "file" instead.
	 */
	public void setName(File f) {
		System.err.println("name is deprecated Use 'file' instead.");
		setFile(f);
	}

	/**
	 * set the package (path) that'll be applied to ALL resources in this
	 * resource set - make sure to handle the empty package properly
	 * 
	 * @param pkg
	 *            sets the value of the <code>package</code> attribute
	 */
	public void setPackage(String pkg) {
		pkg = pkg.replace('.', '/');
		if (pkg.length() > 0 && !pkg.endsWith("/")) {
			this.pkg = pkg + "/";
		} else {
			this.pkg = pkg;
		}
	}

	/**
	 * creates a FileSet - in response to the ant parse phase
	 * 
	 * @return Description of the Return Value
	 */
	public FileSet createFileset() {
		if (file != null) {
			throw new BuildException("can't add Fileset - file already set");
		}
		FileSet set = new FileSet();
		filesets.add(set);
		return set;
	}

	/**
	 * changes the path portion of the file if the <var>pkg</var> variable is
	 * not null
	 * 
	 * @param s
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */
	String repackage(String s) {
		return repackage(new File(s));
	}

	/**
	 * If <var>pkg</var> attrubute has been specified, it is prepended to the
	 * name of the file.
	 * 
	 * @param f
	 *            File to be operatred upon.
	 * @return The file name.
	 */
	private String repackage(File f) {
		if (pkg == null) {
			if (file == null) {
				// Using a fileset
				return f.getPath();
			} else {
				return f.getName();
			}
		} else {
			return pkg + f.getName();
		}
	}

	/**
	 * resolves the file attribute or fileset children into a collection of
	 * JarEntrySpec objects
	 * 
	 * @param gj
	 *            Description of the Parameter
	 * @throws IOException
	 *             Description of the Exception
	 */
	public void resolve(GenJar gj) throws IOException {
		if (file != null) {
			if (file.exists()) {
				jarEntries.add(new JarEntrySpec(repackage(file), file
						.getAbsoluteFile()));
			} else {
				jarEntries.add(new JarEntrySpec(repackage(file), null));
			}
		}

		for (FileSet fs : filesets) {
			File dir = fs.getDir(project);

			DirectoryScanner ds = fs.getDirectoryScanner(project);

			for (String a : ds.getIncludedFiles()) {
				jarEntries.add(new JarEntrySpec(repackage(a), new File(dir, a)));
			}
		}
	}

	/**
	 * return a string representation of this resource set
	 * 
	 * @return All the toString() methods form the jar entires.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (JarEntrySpec entry : jarEntries) {
			sb.append("\n");
			sb.append(entry);
		}
		return sb.toString();
	}
}