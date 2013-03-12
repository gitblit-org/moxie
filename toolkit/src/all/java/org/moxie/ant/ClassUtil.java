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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Examines a class file and returns a list of all referenced classes.
 * 
 * @author Original Code: <a href="mailto:jake@riggshill.com">John W. Kohler
 *         </a>
 * @author Jesse Stockall
 * @version $Revision: 1.1.1.1 $ $Date: 2002/09/26 20:27:16 $
 */
public class ClassUtil {

	//
	// ConstantPool entry identifiers
	//
	private static final int CONSTANT_Utf8 = 1;

	private static final int CONSTANT_Integer = 3;

	private static final int CONSTANT_Float = 4;

	private static final int CONSTANT_Long = 5;

	private static final int CONSTANT_Double = 6;

	private static final int CONSTANT_Class = 7;

	private static final int CONSTANT_String = 8;

	private static final int CONSTANT_Fieldref = 9;

	private static final int CONSTANT_Methodref = 10;

	private static final int CONSTANT_InterfaceMethodref = 11;

	private static final int CONSTANT_NameAndType = 12;

	/** no ctor necessary */
	private ClassUtil() {
	}

	/**
	 * Reads the indicated class file and returns a list of all referenced
	 * classes (dependency list). The list will not refer itself.
	 * 
	 * @param classFile
	 *            the name of the class file to read
	 * @return List a list of all referenced class names
	 * @throws IOException
	 *             when IO errors occur
	 */
	public static List<String> getDependencies(String classFile) throws IOException {
		return getDependencies(new File(classFile));
	}

	/**
	 * Reads the indicated class file and returns a list of all referenced
	 * classes (dependency list). The list will not refer itself.
	 * 
	 * @param classFile
	 *            a File indicating the class file to read
	 * @return List a list of all referenced class names
	 * @throws IOException
	 *             when IO errors occur
	 */
	public static List<String> getDependencies(File classFile) throws IOException {
		InputStream is = null;
		try {
			is = new FileInputStream(classFile);
			return getDependencies(is);
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	/**
	 * Reads the indicated class file and returns a list of all referenced
	 * classes (dependency list). The list will not refer itself.
	 * 
	 * @param is
	 *            an inputstream opened to the first byte of a class file
	 * @return List a list of all referenced class names
	 * @throws IOException
	 *             when IO errors occur
	 */
	public static List<String> getDependencies(InputStream is) throws IOException {
		return getDependencies(new DataInputStream(is));
	}

	/**
	 * Reads the indicated class file and returns a list of all referenced
	 * classes (dependency list). The list will not refer itself.
	 * 
	 * @param is
	 *            a DataInput opened to the first byte of a class file
	 * @return List a list of all referenced class names
	 * @throws IOException
	 *             when IO errors occur
	 */
	public static List<String> getDependencies(DataInputStream is) throws IOException {
		//
		// read the prefix stuff
		//
		if (is.readInt() != 0xcafebabe) {
			throw new IllegalStateException("NOT A CLASS FILE");
		}

		is.readUnsignedShort(); // minor
		is.readUnsignedShort(); // major

		int pool_count = is.readUnsignedShort();

		//
		// this array holds various objects from the constant pool
		// (in essence it IS the constant pool although we're
		// not populating with all types)
		//
		Object[] cp = new Object[pool_count];
		//
		// this list holds all class references from the pool
		// poolCount / 4 should be larger than we ever need
		// this holds indices into the UTFs for class class names
		//
		List<Integer> classRefs = new ArrayList<Integer>(pool_count / 4);
		//
		// now read the constant pool - storing only
		// UTF and CLASS entries
		//
		for (int i = 1; i < pool_count; ++i) {
			switch (is.readUnsignedByte()) {

			case CONSTANT_Utf8:
				cp[i] = is.readUTF();
				break;
			case CONSTANT_Integer:
			case CONSTANT_Float:
				is.readInt();
				break;
			case CONSTANT_Long:
			case CONSTANT_Double:
				is.readInt();
				is.readInt();
				// section 4.4.5 of the Java VM spec states that all 8
				// byte constants take up 2 entries in the constant
				// table - the next entry (n+1) is not used|usable
				// therefore we need to skip it - if we don't then
				// things sometimes get munged
				//
				++i;
				break;
			case CONSTANT_Class:
				//
				// class references are what we're really after so store
				// 'em in a seperate list for simple/fast retrieval
				// note: the ONLY reason I'm storing the idx object in the
				// cp array is so that we can deref the this_class index later
				//
				Integer idx = new Integer(is.readUnsignedShort());
				cp[i] = idx;
				classRefs.add(idx);
				break;
			case CONSTANT_String:
				is.readUnsignedShort();
				break;
			case CONSTANT_Fieldref:
			case CONSTANT_Methodref:
			case CONSTANT_InterfaceMethodref:
				is.readUnsignedShort();
				is.readUnsignedShort();
				break;
			case CONSTANT_NameAndType:
				is.readUnsignedShort();
				is.readUnsignedShort();
				break;
			default:
				break;
			}
		}

		int access = is.readUnsignedShort(); // access flags
		int thisClassIdx = ((Integer) (cp[is.readUnsignedShort()])).intValue();
		//
		// do we need to consume the entire class file? If we open the
		// file obviuously not but if the user is passing us a stream
		// couldn't they assume that the entire file was read? I can't
		// come up with a scenario where it'd matter, but...
		//
		List<String> classNames = new ArrayList<String>(classRefs.size());
		for (Integer idx : classRefs) {
			if (idx != thisClassIdx) {
				// strip array references here
				classNames.add(cp[idx].toString());
			}
		}
		return classNames;
	}
}
