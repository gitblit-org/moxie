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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlException;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

public class Main extends org.apache.tools.ant.Main implements BuildListener {
	
	NewProject newProject = null;

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
	            	// new project
	            	String [] dest = new String[args.length - i - 1];
	            	System.arraycopy(args, i + 1, dest, 0, dest.length);
	                newProject = newProject(dest);
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
	
	@Override
	protected void addBuildListeners(Project project) {
		project.addBuildListener(this);
		super.addBuildListeners(project);
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
        StringBuilder msg = new StringBuilder();
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
    private NewProject newProject(String [] args) {
    	File basedir = new File(System.getProperty("user.dir"));
    	File moxieFile = new File(basedir, "build.moxie");
    	if (moxieFile.exists()) {
    		log("build.moxie exists!  Can not create new project!");
    		System.exit(1);
    	}
    	
    	NewProject project = new NewProject();
    	project.dir = basedir;
    	
    	List<String> apply = new ArrayList<String>();
    	apply.add(Toolkit.APPLY_CACHE);
    	
    	// parse args
    	if (args.length > 0) {
    		List<String> projectArgs = new ArrayList<String>();
    		for (String arg : args) {
    			if (arg.startsWith("-git")) {
    				project.initGit = true;
    				if (arg.startsWith("-git:"))  {
    					project.gitOrigin = arg.substring(5);
    				}
    			} else if ("-eclipse".equals(arg)) {
    				// Eclipse uses hard-coded paths to MOXIE_HOME in USER_HOME
    				project.eclipse = Eclipse.user;
    				apply.add(Toolkit.APPLY_ECLIPSE);
    			} else if ("-eclipse-var".equals(arg)) {
    				// Eclipse uses MOXIE_HOME relative classpath
    				project.eclipse = Eclipse.var;
    				apply.add(Toolkit.APPLY_ECLIPSE_VAR);
                } else if ("-idea".equals(arg)) {
                    // IntelliJ IDEA uses USER_HOME relative paths
                    project.idea = IntelliJ.user;
                    apply.add(Toolkit.APPLY_INTELLIJ);
                } else if ("-idea-var".equals(arg)) {
                    // IntelliJ IDEA uses MOXIE_HOME relative classpath
                    project.idea = IntelliJ.var;
                    apply.add(Toolkit.APPLY_INTELLIJ_VAR);
    			} else if (arg.startsWith("-apply:")) {
    				// -apply:a,b,c,d
    				// -apply:a -apply:b
    				List<String> vals = new ArrayList<String>();
    				for (String val : arg.substring(arg.indexOf(':') + 1).split(",")) {
    					vals.add(val.trim());
    				}
    				apply.addAll(vals);
    				
    				// special apply cases
    				for (String val : vals) {
    					if (Toolkit.APPLY_ECLIPSE.equals(val)) {
    						// Eclipse uses hard-coded paths to MOXIE_HOME in USER_HOME
    						project.eclipse = Eclipse.user;
    					} else if (Toolkit.APPLY_ECLIPSE_VAR.equals(val)) {
    						// Eclipse uses MOXIE_HOME relative classpath
    						project.eclipse = Eclipse.var;
                        } else if (Toolkit.APPLY_INTELLIJ.equals(val)) {
                            // IntelliJ IDEA uses USER_HOME relative classpath
                            project.idea = IntelliJ.user;
                        } else if (Toolkit.APPLY_INTELLIJ_VAR.equals(val)) {
                            // IntelliJ IDEA uses MOXIE_HOME relative classpath
                            project.idea = IntelliJ.var;
                        }
    				}
    			} else {
    				projectArgs.add(arg);
    			}
    		}
    		
    		// parse 
    		project.type = projectArgs.get(0);
    		if (project.type.charAt(0) == '-') {
    			project.type = project.type.substring(1);
    		}
    		if (projectArgs.size() > 1) {
    			String [] fields = projectArgs.get(1).split(":");
    			switch (fields.length) {
    			case 2:
    				project.groupId = fields[0];
    				project.artifactId = fields[1];
    				break;
    			case 3:
    				project.groupId = fields[0];
    				project.artifactId = fields[1];
    				project.version = fields[2];
    				break;
    			default:
    				throw new MoxieException("Illegal parameter " + args);
    			}
    		}
    	}
    	
    	InputStream is = getClass().getResourceAsStream(MessageFormat.format("/archetypes/{0}.moxie", project.type));
    	if (is == null) {
    		log("Unknown archetype " + project.type);
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
    	properties.put(Key.groupId.name(), project.groupId);
    	properties.put(Key.artifactId.name(), project.artifactId);
    	properties.put(Key.version.name(), project.version);
    	properties.put(Key.apply.name(), StringUtils.flattenStrings(apply, ", "));
    	for (String key : map.keySet()) {
    		Object o = map.get(key);
    		if (o instanceof String) {
    			String value = resolveProperties(o.toString(), properties);
    			map.put(key, value);
    		}
    	}
    	
    	// create the source folders
    	List<String> folders = map.getStrings(Key.sourceDirectories.name(), Arrays.asList(new String [0]));
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
    	
    	// Eclipse-ext dependency folder
    	if (Eclipse.ext.equals(project.eclipse)) {
    		map.put(Toolkit.Key.dependencyDirectory.name(), "ext");
    	}

    	// write build.moxie
    	String maxml = map.toMaxml();
    	FileUtils.writeContent(moxieFile, maxml);
    	
    	// write build.xml
    	try {
    		is = getClass().getResourceAsStream(MessageFormat.format("/archetypes/{0}.build.xml", project.type));
    		
    		ByteArrayOutputStream os = new ByteArrayOutputStream();
    		byte [] buffer = new byte[4096];
    		int len = 0;
    		while((len = is.read(buffer)) > -1) {
    			os.write(buffer, 0, len);
    		}
    		String prototype = os.toString("UTF-8");
    		os.close();
    		is.close();
    		
    		String content = prototype.replace("%MOXIE_VERSION%", Toolkit.getVersion());
    		FileUtils.writeContent(new File(basedir, "build.xml"), content);
    	} catch (Throwable t) {
    		t.printStackTrace();
    		System.exit(1);
    	}
    	
    	return project;
    }
    
    private void initGit() throws GitAPIException {
    	// create the repository
    	InitCommand init = Git.init();
    	init.setBare(false);
    	init.setDirectory(newProject.dir);
    	Git git = init.call();
    	
    	if (!StringUtils.isEmpty(newProject.gitOrigin)) {
    		// set the origin and configure the master branch for merging 
    		StoredConfig config = git.getRepository().getConfig();
    		config.setString("remote", "origin", "url", getGitUrl());
    		config.setString("remote",  "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
    		config.setString("branch",  "master", "remote", "origin");
    		config.setString("branch",  "master", "merge", "refs/heads/master");
    		try {
				config.save();
			} catch (IOException e) {
				throw new MoxieException(e);
			}
    	}

    	// prepare a common gitignore file
    	StringBuilder sb = new StringBuilder();
    	sb.append("/.directory\n");
    	sb.append("/.DS_STORE\n");
    	sb.append("/.DS_Store\n");
    	sb.append("/.settings\n");
    	sb.append("/bin\n");
    	sb.append("/build\n");
    	sb.append("/ext\n");
    	sb.append("/target\n");
    	sb.append("/tmp\n");
    	sb.append("/temp\n");
    	if (!newProject.eclipse.includeClasspath()) {
    		// ignore hard-coded .classpath
    		sb.append("/.classpath\n");	
    	}
    	FileUtils.writeContent(new File(newProject.dir, ".gitignore"), sb.toString());

    	AddCommand add = git.add();
    	add.addFilepattern("build.xml");
    	add.addFilepattern("build.moxie");
    	add.addFilepattern(".gitignore");
    	if (newProject.eclipse.includeProject()) {
    		add.addFilepattern(".project");	
    	}
    	if (newProject.eclipse.includeClasspath()) {
    		// MOXIE_HOME relative dependencies in .classpath
    		add.addFilepattern(".classpath");	
    	}
        if (newProject.idea.includeProject()) {
            add.addFilepattern(".project");
        }
        if (newProject.idea.includeClasspath()) {
            // MOXIE_HOME relative dependencies in .iml
            add.addFilepattern("*.iml");
        }
    	try {
    		add.call();
    		CommitCommand commit = git.commit();
    		PersonIdent moxie = new PersonIdent("Moxie", "moxie@localhost");
    		commit.setAuthor(moxie);
    		commit.setCommitter(moxie);
    		commit.setMessage("Project structure created");
    		commit.call();
    	} catch (Exception e) {
    		throw new MoxieException(e);
    	}
    }
    
    String resolveProperties(String string, Map<String, String> properties) {
		if (string == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9-_\\.]+\\}");			
		StringBuilder sb = new StringBuilder(string);
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
    
    /**
     * Returns a git url from the specified url which may use aliases.
     * 
     * @return a url
     */
    private String getGitUrl() {
    	String url = newProject.gitOrigin;
    	String repo = newProject.gitOrigin.substring(url.indexOf("://") + 3);
    	
		File root = new File(System.getProperty("user.home") + "/.moxie");
		if (System.getProperty(Toolkit.MX_ROOT) != null) {
			String value = System.getProperty(Toolkit.MX_ROOT);
			if (!StringUtils.isEmpty(value)) {
				root = new File(value);
			}
		}
		
    	File gitAliases = new File(root, "git.moxie");
    	if (gitAliases.exists()) {
    		try {
    			FileInputStream is = new FileInputStream(gitAliases);
    			MaxmlMap map = Maxml.parse(is);
    			is.close();
    			// look for protocol alias matches
    			for (String alias : map.keySet()) {
    				// ensure alias is legal
    				if ("ftp".equals(alias)
    						|| "http".equals(alias)
    						|| "https".equals(alias)
    						|| "git".equals(alias)
    						|| "ssh".equals(alias)) {
    					error(MessageFormat.format("Illegal repository alias \"{0}\"!", alias));
    					continue;
    				}
    				
    				// look for alias match
    				String proto = alias + "://";
    				if (url.startsWith(proto)) {
    					String baseUrl = map.getString(alias, "");
    					if (baseUrl.charAt(baseUrl.length() - 1) != '/') {
    						return baseUrl + '/' + repo;
    					}
    					return baseUrl + repo;
    				}
    			}
    		} catch (Exception e) {
    			throw new MoxieException(e);
    		}
    	}
    	return url;
    }
    
    private void log(String msg) {
    	System.out.println(msg);
    }

    private void error(String msg) {
    	System.err.println(msg);
    }

    private enum Eclipse {
    	none, user, var, ext;
    	
    	boolean includeProject() {
    		return this.ordinal() > none.ordinal();
    	}
    	
    	boolean includeClasspath() {
    		return this.ordinal() > user.ordinal();
    	}
    }

    private enum IntelliJ {
        none, user, var;

        boolean includeProject() {
            return this.ordinal() > none.ordinal();
        }

        boolean includeClasspath() {
            return true;
        }
    }
    
    private class NewProject {
    	String type = "jar";
    	String groupId = "mygroup";
    	String artifactId = "artifact";
    	String version = "0.0.0-SNAPSHOT";
    	boolean initGit = false;
    	String gitOrigin;
    	Eclipse eclipse = Eclipse.none;
        IntelliJ idea = IntelliJ.none;
    	File dir;
    }

	@Override
	public void buildStarted(BuildEvent event) {
	}

	@Override
	public void buildFinished(BuildEvent event) {
		if (newProject != null && newProject.initGit) {
			// init Git repository after running moxie.init
			try {
				initGit();
			} catch (GitAPIException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void targetStarted(BuildEvent event) {
	}

	@Override
	public void targetFinished(BuildEvent event) {
	}

	@Override
	public void taskStarted(BuildEvent event) {
	}

	@Override
	public void taskFinished(BuildEvent event) {
	}

	@Override
	public void messageLogged(BuildEvent event) {
	}
}
