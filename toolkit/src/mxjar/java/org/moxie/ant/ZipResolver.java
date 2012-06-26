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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Represents a zip file in the classpath.
 * <p>
 * 
 * When a Zip file is located in the classpath, a ZipResolver is instantiated
 * that encapsulates the path and performs searches in that zip. This class is
 * used primarily to allow easy association of the <i>zip file</i> with the jar
 * entry's attributes.
 * <p>
 * 
 * When a file is resolved from a JarEntrySpec, Attributes are added for the zip
 * file and last modification time.
 * <p>
 * 
 * 
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.4 $ $Date: 2003/02/23 10:06:10 $
 */
class ZipResolver extends PathResolver {
	private File file;

	private ZipFile zip;

	/**
	 * Constructor for the ZipResolver object
	 * 
	 * @param f
	 *            Description of the Parameter
	 * @param log
	 *            Description of the Parameter
	 * @throws IOException
	 *             Oops!
	 */
	ZipResolver(File f, Logger log) throws IOException {
		super(log);

		file = f;
		zip = new ZipFile(file);
		log.verbose("Resolver: " + file);
	}

	/**
	 * Closes the zip file to avoid locking files
	 * 
	 * @throws IOException
	 *             Oops!
	 */
	public void close() throws IOException {
		if (zip != null) {
			zip.close();
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
		ZipEntry ze = zip.getEntry(spec.getJarName());
		if (ze != null) {
			is = zip.getInputStream(ze);
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
		ZipEntry ze = zip.getEntry(fname);
		if (ze != null) {
			is = zip.getInputStream(ze);
			log.debug(fname + "->" + file);
		}
		return is;
	}
}