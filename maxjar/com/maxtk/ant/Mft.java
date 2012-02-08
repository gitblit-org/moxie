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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.tools.ant.BuildException;

/**
 * Encapsulates the data destined for the jar's Manifest file.
 * <p>
 * 
 * An Mft instance represents the &lt;manifest&gt; element in the project file.
 * An Mft is always created by GenJar (whether or not there's a manifest
 * element) to handle the Manifest creation duties.
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.2 $ $Date: 2002/09/28 03:41:07 $
 */
public class Mft {
	File baseDir = null;

	File file = null;

	List attrs = new ArrayList();

	List mimes = new ArrayList();

	java.util.Map mimeMap = null;

	boolean genEntryAtts = true;

	Manifest man = null;

	/** */
	public Mft() {
		man = new Manifest();
		//
		// the version attribute MUST be present
		//
		Attributes atts = man.getMainAttributes();
		atts.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		atts.putValue("Created-By", "Jakarta Ant " + getAntVersion());
	}

	//
	// I lifted this directly from the ant Main class - there should be a way
	// for a task to determine what version of Ant it's operating within
	//
	/**
	 * Gets the antVersion attribute of the Mft object
	 * 
	 * @return The antVersion value
	 */
	String getAntVersion() {
		String ret = "Version Unavailable";

		try {
			Properties props = new Properties();
			InputStream in = org.apache.tools.ant.Main.class
					.getResourceAsStream("/org/apache/tools/ant/version.txt");
			props.load(in);
			in.close();
			StringBuffer msg = new StringBuffer();
			msg.append(props.getProperty("VERSION"));
			msg.append(" (");
			msg.append(props.getProperty("DATE"));
			msg.append(")");
			ret = msg.toString();
		} catch (Exception e) {
			;
		} finally {
			return ret;
		}
	}

	/**
	 * this simply adds all defined attributes into the manifest
	 * 
	 * @param logger
	 *            Description of the Parameter
	 * @throws BuildException
	 *             Description of the Exception
	 */
	void execute(Logger logger) throws BuildException {
		//
		// if they gave us a file, then load the file
		// first
		//
		if (file != null) {
			if (!file.exists()) {
				logger.error("specified manifest file not found:" + file);
				throw new BuildException("manifest file " + file + " not found");
			}
			try {
				man.read(new FileInputStream(file));
			} catch (IOException ioe) {
				logger.error("I/O Error loading manifest file: " + file);
				throw new BuildException("can't load manifest file " + file
						+ ":" + ioe);
			}
		}
		//
		// rip over our attribute values and
		// insert them into the manifest
		//
		for (Iterator it = attrs.iterator(); it.hasNext();) {
			MftAttr attr = (MftAttr) it.next();
			logger.verbose("Attribute:" + attr);
			attr.add(man);
		}

		//
		// we're done with our attributes so....
		//
		attrs = null;
		//
		// transfer all the mime type definitions to
		// a map for quick lookup
		//
		mimeMap = new HashMap();
		for (Iterator it = mimes.iterator(); it.hasNext();) {
			Mime m = (Mime) it.next();
			mimeMap.put(m.getExt(), m.getType());
		}
		mimes = null;
	}

	/**
	 * Sets the baseDir attribute of the Mft object
	 * 
	 * @param baseDir
	 *            The new baseDir value
	 */
	void setBaseDir(File baseDir) {
		this.baseDir = baseDir;
	}

	/**
	 * @return The manifest value
	 */
	public Manifest getManifest() {
		return man;
	}

	/**
	 * @param f
	 *            The new template value
	 */
	public void setTemplate(String f) {
		file = new File(f);
		if (!file.isAbsolute()) {
			file = new File(baseDir, f);
		}
		// file = new File( baseDir, f );
	}

	/**
	 * Sets the generateEntryAttributes attribute of the Mft object
	 * 
	 * @param val
	 *            The new generateEntryAttributes value
	 */
	public void setGenerateEntryAttributes(String val) {
		genEntryAtts = "yes".equalsIgnoreCase(val);
	}

	/**
	 * @return Description of the Return Value
	 */
	public Object createMime() {
		Mime m = new Mime();
		mimes.add(m);
		return m;
	}

	/**
	 * @return Description of the Return Value
	 */
	public Object createAttribute() {
		MftAttr attr = new MftAttr();
		attrs.add(attr);
		return attr;
	}

	/**
	 * @param name
	 *            The feature to be added to the Entry attribute
	 * @param newAtts
	 *            The feature to be added to the Entry attribute
	 */
	public void addEntry(String name, Attributes newAtts) {
		if (!genEntryAtts) {
			return;
		}

		Attributes atts = man.getAttributes(name);
		if (atts != null) {
			atts.putAll(newAtts);
		} else {
			atts = newAtts;
		}

		int idx = name.lastIndexOf('.');
		if (idx > 0) {
			String ext = name.substring(idx + 1);
			String type = (String) mimeMap.get(ext);
			if (type != null) {
				atts.putValue("Content-Type", type);
			}
		}
		//
		// replace the existing with itself or
		// the new ones
		//
		man.getEntries().put(name, atts);

	}

	/**
	 * @return Description of the Return Value
	 */
	public String toString() {
		String retVal = "";
		OutputStream out = new ByteArrayOutputStream();

		try {
			man.write(out);
			retVal = out.toString();
		} catch (IOException ioe) {
			retVal = "IO Exception writing manifest";
		} finally {
			return retVal;
		}
	}

	/**
	 * Instantiated when Ant encounters an &lt;attribute&gt; element.
	 * <p>
	 * 
	 * 
	 * 
	 * @author Original Code: <a href="mailto:jake@riggshill.com">John W.
	 *         Kohler</a>
	 * @version
	 * @version@
	 */
	public class MftAttr {
		private String key = null;

		private String name = null;

		private String value = null;

		/**
		 * @param man
		 *            Description of the Parameter
		 */
		public void add(Manifest man) {
			Attributes atts;

			if (key == null) {
				atts = man.getMainAttributes();
			} else {
				atts = man.getAttributes(key);
				if (atts == null) {
					atts = new Attributes();
					man.getEntries().put(key, atts);
				}
			}
			atts.putValue(name, value);
		}

		/**
		 * @param e
		 *            The new entry value
		 */
		public void setEntry(String e) {
			if (!"main".equals(e)) {
				this.key = e;
			}
		}

		/**
		 * @param n
		 *            The new name value
		 */
		public void setName(String n) {
			this.name = n;
		}

		/**
		 * @param v
		 *            The new value value
		 */
		public void setValue(String v) {
			value = v;
		}

		/**
		 * @return The name value
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return The value value
		 */
		public String getValue() {
			return value;
		}

		/**
		 * @return Description of the Return Value
		 */
		public String toString() {
			return "(" + (key == null ? "Main" : key) + ") " + name + ": "
					+ value;
		}
	}

	/**
	 * Instantiated when Ant encounters an &lt;mime&gt; element.
	 * <p>
	 * 
	 * This class is used to get the MIME mapping info from the project file
	 * into the Mft's type map.
	 * 
	 * @author Original Code: <a href="mailto:jake@riggshill.com">John W.
	 *         Kohler</a>
	 * @version
	 * @version@
	 */
	public class Mime {
		private String type = null;

		private String ext = null;

		/**
		 * @param t
		 *            The new type value
		 */
		public void setType(String t) {
			type = t;
		}

		/**
		 * @param e
		 *            The new ext value
		 */
		public void setExt(String e) {
			ext = e;
		}

		/**
		 * @return The type value
		 */
		public String getType() {
			return type;
		}

		/**
		 * @return The ext value
		 */
		public String getExt() {
			return ext;
		}
	}
}
