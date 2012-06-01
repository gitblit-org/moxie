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
package com.maxtk.ant;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.jar.Attributes;

/**
 * Represents one object (file) that is to be placed into the jar.
 * <p>
 * 
 * This includes all &lt;class&gt; names specified in the project file, all
 * &lt;resource&gt; files specified in the project file and all generated class
 * references.
 * <p>
 * 
 * 
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.2 $ $Date: 2003/02/23 10:43:21 $
 */
public class JarEntrySpec {
	Attributes atts;

	String jarName;

	File srcFile;

	/** Constructor for the JarEntrySpec object */
	JarEntrySpec() {
		atts = new Attributes();
	}

	/**
	 * create a new jar entry given a name (jar path) and a fully qualified file
	 * path.
	 * 
	 * @param jarName
	 *            Description of the Parameter
	 * @param srcFile
	 *            Description of the Parameter
	 */
	JarEntrySpec(String jarName, File srcFile) {
		this.jarName = jarName.replace('\\', '/');
		this.srcFile = srcFile;
		atts = new Attributes();

		if (srcFile != null) {
			setAttribute("Content-Location", srcFile.getAbsolutePath());
			setAttribute("Content-Length", srcFile.length());
			setAttribute("Last-Modified", new Date(srcFile.lastModified()));
		}
	}

	/**
	 * Gets the jarName attribute of the JarEntrySpec object
	 * 
	 * @return The jarName value
	 */
	String getJarName() {
		return jarName;
	}

	/**
	 * Sets the jarName attribute of the JarEntrySpec object
	 * 
	 * @param jn
	 *            The new jarName value
	 */
	void setJarName(String jn) {
		jarName = jn;
	}

	/**
	 * Gets the sourceFile attribute of the JarEntrySpec object
	 * 
	 * @return The sourceFile value
	 */
	File getSourceFile() {
		return srcFile;
	}

	/**
	 * Sets the sourceFile attribute of the JarEntrySpec object
	 * 
	 * @param f
	 *            The new sourceFile value
	 */
	void setSourceFile(File f) {
		srcFile = f;
	}

	/**
	 * Gets the attributes attribute of the JarEntrySpec object
	 * 
	 * @return The attributes value
	 */
	Attributes getAttributes() {
		return atts;
	}

	/**
	 * copy an Attributes into this entry's attributs
	 * 
	 * @param a
	 *            The feature to be added to the Attributes attribute
	 */
	public void addAttributes(Attributes a) {
		if (a != null) {
			for (Iterator<Object> it = a.keySet().iterator(); it.hasNext();) {
				String key = it.next().toString();
				atts.putValue(key, a.getValue(key));
			}
		}
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param val
	 *            The new attribute value
	 */
	void setAttribute(String name, int val) {
		setAttribute(name, Integer.toString(val));
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param val
	 *            The new attribute value
	 */
	void setAttribute(String name, long val) {
		setAttribute(name, Long.toString(val));
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param val
	 *            The new attribute value
	 */
	void setAttribute(String name, float val) {
		setAttribute(name, Float.toString(val));
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param val
	 *            The new attribute value
	 */
	void setAttribute(String name, double val) {
		setAttribute(name, Double.toString(val));
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param d
	 *            The new attribute value
	 */
	void setAttribute(String name, Date d) {
		atts.putValue(name, d.toString());
	}

	/**
	 * Sets the attribute attribute of the JarEntrySpec object
	 * 
	 * @param name
	 *            The new attribute value
	 * @param obj
	 *            The new attribute value
	 */
	void setAttribute(String name, Object obj) {
		atts.putValue(name, obj.toString());
	}

	/**
	 * Description of the Method
	 * 
	 * @param o
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */
	public boolean equals(Object o) {
		if (o == null || !(o instanceof JarEntrySpec)) {
			return false;
		}
		return jarName.equals(((JarEntrySpec) o).jarName);
	}

	/**
	 * Description of the Method
	 * 
	 * @return Description of the Return Value
	 */
	public String toString() {
		String key;
		StringBuffer sb = new StringBuffer("JarEntrySpec:");

		sb.append("\n\tJar Name:");
		sb.append(jarName);
		sb.append("\n\tStream  :");
		if (srcFile == null) {
			sb.append("** NOT RESOLVED **");
		} else {
			sb.append(srcFile.getAbsolutePath());
		}

		for (Iterator<Object> it = atts.keySet().iterator(); it.hasNext();) {
			sb.append("\n\tAtt: ");
			sb.append(key = it.next().toString());
			sb.append(": ");
			sb.append(atts.getValue(key));
		}
		return sb.toString();
	}
}
