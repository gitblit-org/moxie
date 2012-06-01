/*
 * The Apache Software License, Version 1.1
 *
 * Copyright © 2002 Jesse Stockall.  All rights reserved.
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
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.maxtk.ant;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * <p>
 * 
 * A PathResolver is used for each component of the classpath.
 * </p>
 * <p>
 * 
 * Each type of component is a specialization of this base class. For example, a
 * jar file encountered in the classpath causes a JarResolver to be
 * instantiated. A JarResolver knows how to search within jar files for specific
 * files.
 * </p>
 * <p>
 * 
 * This approach is taken for two reasons:
 * <ol>
 * <li>To encapsulate the specifics of fetching streams and what attributes are
 * set on a jar entry's manifest entry.</li>
 * <li>To provide an association between the <i>source</i> of a class (or
 * resource) and the repository from which it came. This info is priceless when
 * trying to figure out why the wrong classes are being included in your jar.</li>
 * </ol>
 * </p>
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.2 $ $Date: 2003/02/23 10:06:10 $
 */
abstract class PathResolver {
	/** name of attribute for content's original location (path) */
	static final String CONTENT_LOC = "Content-Location";

	/** name of attribute for original content's modification time */
	static final String LAST_MOD = "Last-Modified";

	/** format string for RFC1123 date */
	static final String RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

	/** Description of the Field */
	protected Logger log;

	private static SimpleDateFormat dateFmt = null;

	/**
	 * Constructor for the PathResolver object
	 * 
	 * @param log
	 *            Description of the Parameter
	 */
	PathResolver(Logger log) {
		this.log = log;
		if (dateFmt == null) {
			dateFmt = new SimpleDateFormat(RFC1123, Locale.getDefault());
			//
			// true conformance to rfc1123 would have us using GMT
			// rather than local time - this doesn;t make sense to
			// me since the biggest reason to use the time stamps
			// is to compare against the source files - which are in
			// local time
			//
			/*
			 * dateFmt.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
			 */
		}
	}

	/**
	 * Given a JarEntrySpec, a path to the actual resource is generated and an
	 * input stream is returned on the path.
	 * 
	 * @param je
	 *            Description of the Parameter
	 * @return IOStream opened on the file referenced
	 * @exception IOException
	 *                if any errors are encountered
	 */
	abstract InputStream resolve(JarEntrySpec je) throws IOException;

	/**
	 * Given a file name (possibly relative), an InputStream is opened on that
	 * file or an exception is thrown.
	 * 
	 * @param fname
	 *            Description of the Parameter
	 * @return IOStream opened on the named file
	 * @throws IOException
	 *             Oops!
	 */
	abstract InputStream resolve(String fname) throws IOException;

	/**
	 * Closes any resources opened by the resolver.
	 * 
	 * @throws IOException
	 *             Oops!
	 */
	abstract void close() throws IOException;

	/**
	 * Formats a date compatable with RFC1123. Well, close anyway. To be truly
	 * conformant the time zone would always be GMT. This formats the date in
	 * local time.
	 * 
	 * @param d
	 *            Description of the Parameter
	 * @return String representation of the date
	 */
	protected String formatDate(Date d) {
		return dateFmt.format(d);
	}

	/**
	 * Description of the Method
	 * 
	 * @param l
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */
	protected String formatDate(long l) {
		return formatDate(new Date(l));
	}
}