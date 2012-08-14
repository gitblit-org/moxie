/*
 * Copyright 2012 James Moger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moxie.ant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;

public class Main extends org.apache.tools.ant.Main {

    /**
     * Command line entry point. This method kicks off the building
     * of a project object and executes a build using either a given
     * target or the default target.
     *
     * @param args Command line arguments. Must not be <code>null</code>.
     */
    public static void main(String[] args) {
        start(args, null, null);
    }
    
    /**
     * Creates a new instance of this class using the
     * arguments specified, gives it any extra user properties which have been
     * specified, and then runs the build using the classloader provided.
     *
     * @param args Command line arguments. Must not be <code>null</code>.
     * @param additionalUserProperties Any extra properties to use in this
     *        build. May be <code>null</code>, which is the equivalent to
     *        passing in an empty set of properties.
     * @param coreLoader Classloader used for core classes. May be
     *        <code>null</code> in which case the system classloader is used.
     */
    public static void start(String[] args, Properties additionalUserProperties,
                             ClassLoader coreLoader) {
        Main m = new Main();
        m.startAnt(args, additionalUserProperties, coreLoader);
    }


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tools.ant.Main#startAnt(java.lang.String[],
	 * java.util.Properties, java.lang.ClassLoader)
	 */
	@Override
	public void startAnt(String[] args, Properties additionalUserProperties, ClassLoader coreLoader) {
		boolean startAnt = true;
		 for (int i = 0; i < args.length; i++) {
	            String arg = args[i];

	            if (arg.equals("-help") || arg.equals("-h")) {
	                printUsage();
	                startAnt = false;
	            } else if (arg.equals("-version")) {
	                printVersion();
	                startAnt = false;
	            } else if (arg.equals("-new")) {
	            	String [] dest = new String[args.length - i - 1];
	            	System.arraycopy(args, i + 1, dest, 0, dest.length);
	                newProject(dest);
	                // initialize the project
	                args = new String[] { "moxie.init" };
	                break;
	            }
		 }
		 
		if (startAnt) {
			// specify the Moxie MainLogger instead of the Ant DefaultLogger
	        String [] moxieArgs = new String[args.length + 2];
	        moxieArgs[0] = "-logger";
	        moxieArgs[1] = MainLogger.class.getName();
	        System.arraycopy(args, 0, moxieArgs, 2, args.length);

			super.startAnt(moxieArgs, additionalUserProperties, coreLoader);
		}
	}
	
	private void printVersion() {
		System.out.println();
		System.out.println("Moxie+Ant v" + Toolkit.getVersion());
		System.out.println("based on " + getAntVersion());
		System.out.println();
	}
	
	   /**
     * Prints the usage information for this class to <code>System.out</code>.
     */
    private void printUsage() {
        String lSep = System.getProperty("line.separator");
        StringBuffer msg = new StringBuffer();
        msg.append("moxie [options] [target [target2 [target3] ...]]" + lSep);
        msg.append("Options: " + lSep);
        msg.append("  -help, -h              print this message" + lSep);
        msg.append("  -projecthelp, -p       print project help information" + lSep);
        msg.append("  -version               print the version information and exit" + lSep);
        msg.append("  -diagnostics           print information that might be helpful to" + lSep);
        msg.append("                         diagnose or report problems." + lSep);
        msg.append("  -quiet, -q             be extra quiet" + lSep);
        msg.append("  -verbose, -v           be extra verbose" + lSep);
        msg.append("  -debug, -d             print debugging information" + lSep);
        msg.append("  -emacs, -e             produce logging information without adornments"
                   + lSep);
        msg.append("  -lib <path>            specifies a path to search for jars and classes"
                   + lSep);
        msg.append("  -logfile <file>        use given file for log" + lSep);
        msg.append("    -l     <file>                ''" + lSep);
        msg.append("  -logger <classname>    the class which is to perform logging" + lSep);
        msg.append("  -listener <classname>  add an instance of class as a project listener"
                   + lSep);
        msg.append("  -noinput               do not allow interactive input" + lSep);
        msg.append("  -buildfile <file>      use given buildfile" + lSep);
        msg.append("    -file    <file>              ''" + lSep);
        msg.append("    -f       <file>              ''" + lSep);
        msg.append("  -D<property>=<value>   use value for given property" + lSep);
        msg.append("  -keep-going, -k        execute all targets that do not depend" + lSep);
        msg.append("                         on failed target(s)" + lSep);
        msg.append("  -propertyfile <name>   load all properties from file with -D" + lSep);
        msg.append("                         properties taking precedence" + lSep);
        msg.append("  -inputhandler <class>  the class which will handle input requests" + lSep);
        msg.append("  -find <file>           (s)earch for buildfile towards the root of" + lSep);
        msg.append("    -s  <file>           the filesystem and use it" + lSep);
        msg.append("  -nice  number          A niceness value for the main thread:" + lSep
                   + "                         1 (lowest) to 10 (highest); 5 is the default"
                   + lSep);
        msg.append("  -nouserlib             Run Moxie without using the jar files from" + lSep
                   + "                         ${user.home}/.ant/lib" + lSep);
        msg.append("  -noclasspath           Run Moxie without using CLASSPATH" + lSep);
        msg.append("  -autoproxy             Java1.5+: use the OS proxy settings"
                + lSep);
        msg.append("  -main <class>          override Moxie's normal entry point");
        System.out.println(msg.toString());
    }
    
    
    /**
     * Creates a new Moxie project in the current folder.
     * 
     * @param args
     */
    private void newProject(String [] args) {
    	File basedir = new File(System.getProperty("user.dir"));
    	File moxieFile = new File(basedir, "build.moxie");
    	if (moxieFile.exists()) {
    		log("build.moxie exists!  Can not create new project!");
    		System.exit(1);
    	}
    	String type = "jar";
    	String groupId = "mygroup";
    	String artifactId = "artifact";
    	String version = "0.0.0-SNAPSHOT";
    	
    	// parse args
    	if (args.length > 0) {
    		type = args[0];
    		if (args.length > 1) {
    			String [] fields = args[1].split(":");
    			switch (fields.length) {
    			case 2:
    				groupId = fields[0];
    				artifactId = fields[1];
    				break;
    			case 3:
    				groupId = fields[0];
    				artifactId = fields[1];
    				version = fields[2];
    				break;
    			default:
    				throw new BuildException("Illegal parameter " + args);
    			}
    		}
    	}
    	
    	InputStream is = getClass().getResourceAsStream(MessageFormat.format("/archetypes/{0}.moxie", type));
    	if (is == null) {
    		log("Unknown archetype " + type);
    		System.exit(1);
    	}
    	MaxmlMap map = null;
    	try {
			map = Maxml.parse(is);
		} catch (MaxmlException e) {
			e.printStackTrace();
		}
    	
    	// property substitution
    	Map<String, String> properties = new HashMap<String, String>();
    	properties.put(Key.groupId.name(), groupId);
    	properties.put(Key.artifactId.name(), artifactId);
    	properties.put(Key.version.name(), version);
    	for (String key : map.keySet()) {
    		Object o = map.get(key);
    		if (o instanceof String) {
    			String value = resolveProperties(o.toString(), properties);
    			map.put(key, value);
    		}
    	}
    	
    	// create the source folders
    	List<String> folders = map.getStrings(Key.sourceFolders.name(), Arrays.asList(new String [0]));
    	for (String scopedFolder : folders) {
    		String s = scopedFolder.substring(0, scopedFolder.indexOf(' ')).trim();
    		Scope scope = Scope.fromString(s);
    		if (scope.isValidSourceScope()) {
    			String folder = scopedFolder.substring(s.length() + 1).trim();
        		File file = new File(folder);
        		file.mkdirs();
    		} else {
    			log("Illegal source folder scope: " + s);
    		}
    	}

    	// write build.moxie
    	String maxml = map.toMaxml();
    	FileUtils.writeContent(moxieFile, maxml);
    	
    	// write build.xml
    	try {
    		is = getClass().getResourceAsStream("/archetypes/build.xml");
    		FileOutputStream os = new FileOutputStream(new File(basedir, "build.xml"));
    		byte [] buffer = new byte[4096];
    		int len = 0;
    		while((len = is.read(buffer)) > -1) {
    			os.write(buffer, 0, len);
    		}
    		os.close();
    		is.close();
    	} catch (Throwable t) {
    		t.printStackTrace();
    		System.exit(1);
    	}
    }
    
    String resolveProperties(String string, Map<String, String> properties) {
		if (string == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9-_\\.]+\\}");			
		StringBuffer sb = new StringBuffer(string);
		while (true) {
			Matcher m = p.matcher(sb.toString());
			if (m.find()) {
				String prop = m.group();
				prop = prop.substring(2, prop.length() - 1);
				String value = prop;
				if (properties.containsKey(prop)) {
					value = properties.get(prop);
				}
				sb.replace(m.start(), m.end(), value);
			} else {
				return sb.toString();
			}
		}		
	}
    
    private void log(String msg) {
    	System.out.println(msg);
    }
}
