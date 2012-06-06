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

	String jarName;

	File srcFile;

	/** Constructor for the JarEntrySpec object */
	JarEntrySpec() {
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
}
