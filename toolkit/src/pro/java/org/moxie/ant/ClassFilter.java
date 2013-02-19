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
package org.moxie.ant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.moxie.MoxieException;

/**
 * Determines whether a class is to be included in the jar.
 * <p>
 * 
 * Buildfile example:
 * 
 * <pre>
 *  &lt;classfilter&gt;
 *    &lt;include name=&quot;com.foo.&quot; &gt;&lt;br/&gt;
 *  &lt;exclude name=&quot;org&quot; &gt;&lt;br/&gt;
 *  &lt;/classfilter&gt;
 * </pre>
 * 
 * <p>
 * 
 * As a class' dependancy lists are generated, each class is checked against a
 * ClassFilter by calling the <code>include</code> method. This method checks
 * the class name against its list of <i>includes</i> and <i>excludes</i> .
 * </p>
 * <p>
 * 
 * If the class name starts with any of the strings in the <i>includes</i> list,
 * the class is included in the jar and its dependancy lists are checked. If the
 * class name starts with any of the strings in the <i>excludes</i> list, the
 * class is <b>not</b> included in the jar and the dependancy analysis halts
 * (for that class).
 * </p>
 * <p>
 * 
 * Upon instantiation the class will look for a resource named <code>
 *   site-excludes</code> which is expected to contain a list of exclusion
 * patterns that are to be applied to all projects. This file contains one
 * pattern per line, with lines beginning with '#' being comments.<br/>
 * Example:
 * 
 * <pre>
 *      ##
 *      ## site wide exclusions
 *      ##
 *      # never include apache stuff in our jars
 *      org.apache.
 *      # never include IBM's stuff either
 *      com.ibm.
 * </pre>
 * 
 * </p>
 * <hr/>
 * <p>
 * 
 * Note that the following packages are excluded by default:
 * <code>java javax sun sunw com.sun org.omg</code>
 * </p>
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.2 $ $Date: 2002/10/09 00:01:27 $
 */
public class ClassFilter {
	/** the list of include patterns */
	private List<String> includes = new ArrayList<String>();

	/** the list of exclude patterns */
	private List<String> excludes = new ArrayList<String>();

	/** accessor object for setting include patterns */
	private Setter includer = new Setter(includes);

	/** accessor object for setting exclude patterns */
	private Setter excluder = new Setter(excludes);

	private Logger log = null;

	/**
	 * Constructs a filter object with the default exclusions
	 * 
	 * @param log
	 *            Description of the Parameter
	 */
	ClassFilter(Logger log) {
		this.log = log;
		//
		// you really do NOT want these included in your
		// jar - if you do, then spcify 'em as includes
		//
		addExclude("java.");
		addExclude("javax.");
		addExclude("sun.");
		addExclude("sunw.");
		addExclude("com.sun.");
		addExclude("org.omg.");
		addExclude("org.w3c.");
		addExclude("com.ibm.jvm.");

		//
		// in some circumstances, these names will be included
		// in a class' constant pool as classes, so they're
		// excluded just to make sure
		//
		addExclude("boolean");
		addExclude("byte");
		addExclude("char");
		addExclude("short");
		addExclude("int");
		addExclude("long");
		addExclude("float");
		addExclude("double");

		loadSiteExcludes();
	}

	/**
	 * Add an inclusion pattern
	 * 
	 * @param patt
	 *            The inclusion pattern to add
	 */
	private void addExclude(String patt) {
		excludes.add(patt = patt.replace('/', '.'));
		log.debug("Exclude:" + patt);
	}

	/**
	 * Add an exclusion pattern
	 * 
	 * @param patt
	 *            The exclusion pattern to add
	 */
	private void addInclude(String patt) {
		includes.add(patt = patt.replace('/', '.'));
		log.debug("Include:" + patt);
	}

	/**
	 * loads the file 'site-excludes' as a resource (in this package). The file
	 * is expected to have one package prefix per line, and commects are denoted
	 * by '#' being the first non-white character on the line. Empty lines are
	 * ignored.
	 */
	private void loadSiteExcludes() {
		try {
			InputStream is = getClass().getResourceAsStream("site-excludes");
			if (is != null) {
				log.verbose("Loading site-excludes (resource=site-excludes)");
				String l;
				BufferedReader r = new BufferedReader(new InputStreamReader(is));
				while ((l = r.readLine()) != null) {

					l = l.trim();
					if (l.length() <= 0 || l.startsWith("#")) {
						continue;
					}
					addExclude(l);
				}
				r.close();
			} else {
				log.verbose("No site-excludes found");
			}
		} catch (IOException ioe) {
			throw new MoxieException("IOException loading site-excludes");
		}
	}

	/**
	 * Determines if a class is to included or not. See the class description
	 * for details.
	 * 
	 * @param cname
	 *            The name of the class test test for inclusion
	 * @return true if the class is to be included, otherwise false
	 */
	public boolean include(String cname) {
		// short circuit arrays immediately
		if (cname.charAt(0) == '[') {
			return false;
		}

		//
		// normalize class name to dotted notation for logging
		//
		cname = cname.replace('/', '.');
		//
		// if the class is explicitly included, then
		// say ok....
		//
		for (String ip : includes) {
			if (cname.startsWith(ip)) {
				log.debug("Explicit Include (" + ip + "):" + cname);
				return true;
			}
		}
		//
		// no explicit inclusion - check for an exclusion
		//
		for (String ip : excludes) {
			if (cname.startsWith(ip)) {
				log.debug("Explicit Exclude (" + ip + "):" + cname);
				return false;
			}
		}
		//
		// nothing explicit - include by default
		//
		log.debug("Implicit Include:" + cname);
		return true;
	}

	/**
	 * Called by Ant when &lt;include&gt; is encountered in the project. An
	 * instance of Setter is returned.
	 * 
	 * @return Description of the Return Value
	 */
	public Object createInclude() {
		return includer;
	}

	/**
	 * Called by Ant when &lt;exclude&gt; is encountered in the project. An
	 * instance of Setter is returned.
	 * 
	 * @return Description of the Return Value
	 */
	public Object createExclude() {
		return excluder;
	}

	/**
	 * Accessor class allowing access to ClassFilter properties from Ant
	 * Project.
	 * <p>
	 * 
	 * Instances of this class are returned to Ant when include/exclude elements
	 * are encountered.
	 * 
	 */
	public class Setter {
		boolean doInc;

		/**
		 * Constructor for the Setter object
		 * 
		 * @param set
		 *            Description of the Parameter
		 */
		public Setter(Collection<String> set) {
			doInc = set == includes;
		}

		public void setName(String n) {
			if (doInc) {
				addInclude(n);
			} else {
				addExclude(n);
			}
		}
	}
}
