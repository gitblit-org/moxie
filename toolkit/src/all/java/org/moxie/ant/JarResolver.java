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
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Represents a jar file in the classpath.
 * <p>
 * 
 * When a Jar file is located in the classpath, a JarResolver is instantiated
 * that remembers the path and performs searches in that jar. This class is used
 * primarily to allow easy association of the <i>jar file</i> with the jar
 * entry's attributes.
 * <p>
 * 
 * When a file is resolved from a JarEntrySpec, Attributes are added for the jar
 * file and last modification time.
 * <p>
 * 
 * <p>
 * 
 * TODO: copy all entry-attributes from the source jar into our manifest
 * </p>
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.2 $ $Date: 2003/02/23 10:06:10 $
 */
class JarResolver extends PathResolver {
	File file = null;

	JarFile jarFile = null;

	String modified;

	/**
	 * Constructor for the JarResolver object
	 * 
	 * @param file
	 *            Description of the Parameter
	 * @param log
	 *            Description of the Parameter
	 * @throws IOException
	 *             Oops!
	 */
	JarResolver(File file, boolean excluded, Logger log) throws IOException {
		super(excluded, log);

		this.file = file;
		this.jarFile = new JarFile(file);
		modified = formatDate(file.lastModified());
		log.verbose("Resolver: " + file);
	}

	/**
	 * Close the jar file.
	 * 
	 * @throws IOException
	 *             Oops!
	 */
	public void close() throws IOException {
		if (jarFile != null) {
			jarFile.close();
		}
	}

	/**
	 * Description of the Method
	 * 
	 * @param spec
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 * @throws IOException
	 *             Oops!
	 */
	public InputStream resolve(JarEntrySpec spec) throws IOException {
		InputStream is = null;

		JarEntry je = jarFile.getJarEntry(spec.getJarName());
		if (je != null) {
			is = jarFile.getInputStream(je);
			log.debug(spec.getJarName() + "->" + file);
		}
		return is;
	}

	/**
	 * Description of the Method
	 * 
	 * @param fname
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 * @throws IOException
	 *             Oops!
	 */
	public InputStream resolve(String fname) throws IOException {
		InputStream is = null;

		JarEntry je = jarFile.getJarEntry(fname);
		if (je != null) {
			is = jarFile.getInputStream(je);
			log.debug(fname + "->" + file);
		}
		return is;
	}
	
	@Override
	public String toString() {
		return "Resolver " + (isExcluded() ? "EXCLUDED" : "") + " (" + file.getPath() + ")";
	}
}