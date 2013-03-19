/*
 * Copyright 2003-2011 the original author or authors.
 * Copyright 2013 James Moger
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * Executes a series of Groovy statements.
 * <p/>
 * <p>Statements can either be read in from a text file using
 * the <i>src</i> attribute or from between the enclosing groovy tags.</p>
 *
 * This class and it's companion, GroovyEngine, were originally harvested from
 * Groovy 1.8.8.  They have been split into two to allow dynamically loading
 * Groovy from the Moxie cache.  If they were one class, as originally written,
 * Ant would throw a ClassDefNotFoundException on the task unless Groovy is
 * part of the Ant runtime classpath at task classloading.
 * 
 */
public class MxGroovy extends Java {
	
	Boolean showtitle;
	
	Scope scope;
	
    Vector<FileSet> filesets = new Vector<FileSet>();

    File srcFile = null;

    String command = "";
    
    String subtitle = "embedded script";

    File output = null;

    boolean append = false;

    boolean fork = false;
    boolean includeAntRuntime = true;
    boolean useGroovyShell = false;
    boolean contextClassLoader;
    boolean stacktrace;

    public MxGroovy() {
    	super();
    	setTaskName("mx:groovy");
    }

	public void setShowtitle(boolean value) {
		this.showtitle = value;
	}
	
	public boolean isShowTitle() {
		return showtitle == null || showtitle;
	}
	
	public void setScope(String value) {
		this.scope = Scope.fromString(value);
	}

	public void setSubtitle(String value) {
		this.subtitle = value;
	}

    /**
     * Should the script be executed using a forked process. Defaults to false.
     *
     * @param fork true if the script should be executed in a forked process
     */
    public void setFork(boolean fork) {
    	super.setFork(fork);
        this.fork = fork;
    }

    /**
     * Should a new GroovyShell be used when forking. Special variables won't be available
     * but you don't need Ant in the classpath.
     *
     * @param useGroovyShell true if GroovyShell should be used to run the script directly
     */
    public void setUseGroovyShell(boolean useGroovyShell) {
        this.useGroovyShell = useGroovyShell;
    }

    /**
     * Should the system classpath be included on the classpath when forking. Defaults to true.
     *
     * @param includeAntRuntime true if the system classpath should be on the classpath
     */
    public void setIncludeAntRuntime(boolean includeAntRuntime) {
        this.includeAntRuntime = includeAntRuntime;
    }

    /**
     * Enable compiler to report stack trace information if a problem occurs
     * during compilation.
     *
     * @param stacktrace set to true to enable stacktrace reporting
     */
    public void setStacktrace(boolean stacktrace) {
        this.stacktrace = stacktrace;
    }

    /**
     * Set the name of the file to be run. The folder of the file is automatically added to the classpath.
     * Required unless statements are enclosed in the build file
     *
     * @param srcFile the file containing the groovy script to execute
     */
    public void setSrc(File srcFile) {
        this.srcFile = srcFile;
    }

    /**
     * Set an inline command to execute.
     * NB: Properties are not expanded in this text.
     *
     * @param txt the inline groovy ommands to execute
     */
    public void addText(String txt) {
        log("addText('" + txt + "')", Project.MSG_VERBOSE);
        this.command += txt;
    }

    /**
     * Adds a set of files (nested fileset attribute).
     *
     * @param set the fileset representing source files
     */
    public void addFileset(FileSet set) {
        filesets.addElement(set);
    }

    /**
     * Set the output file;
     * optional, defaults to the Ant log.
     *
     * @param output the output file
     */
    public void setOutput(File output) {
        this.output = output;
    }

    /**
     * Whether output should be appended to or overwrite
     * an existing file.  Defaults to false.
     *
     * @param append set to true to append
     */
    public void setAppend(boolean append) {
        this.append = append;
    }
    
    /**
     * Setting to true will cause the contextClassLoader to be set with
     * the classLoader of the shell used to run the script. Not used if
     * fork is true. Not allowed when running from Maven but in that
     * case the context classLoader is set appropriately for Maven.
     *
     * @param contextClassLoader set to true to set the context classloader
     */
    public void setContextClassLoader(boolean contextClassLoader) {
        this.contextClassLoader = contextClassLoader;
    }

    Build getBuild() {
    	return (Build) getProject().getReference(Key.build.referenceId());
    }

    Console getConsole() {
    	return getBuild().getConsole();
    }

    /**
     * Load the file and then execute it
     */
    public void execute() throws BuildException {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		MxTask.loadRuntimeDependencies(build, new Dependency("mx:groovy"));
		
		if (scope != null) {
			createProjectClasspath(build);
		}
		
		if (fork) {
			createForkClasspath();
		}

        if (srcFile == null && StringUtils.isEmpty(command) && filesets.isEmpty()) {
            throw new BuildException("Source file does not exist!", getLocation());
        }

        if (srcFile != null && !srcFile.exists()) {
            throw new BuildException(MessageFormat.format("Source file {0} does not exist!", srcFile), getLocation());
        }
        
        // if there are no groovy statements between the enclosing Groovy tags
        // then read groovy statements in from a text file using the src attribute
        if (StringUtils.isEmpty(command)) {
            createClasspath().add(new Path(getProject(), srcFile.getParentFile().getAbsolutePath()));
            command = FileUtils.readContent(srcFile, "\n");
        }

        if (StringUtils.isEmpty(command)) {
            throw new BuildException("No Groovy statements to execute!", getLocation());
        }

		if (isShowTitle()) {
			build.getConsole().title(getClass(), srcFile != null ? srcFile.getAbsolutePath() : subtitle);
		}

        PrintStream out = System.out;
        try {
            GroovyEngine groovy = new GroovyEngine();
        	if (fork) {
        		groovy.forkGroovy(this, command);
        	} else {
        		if (output != null) {
        			build.getConsole().debug("Opening PrintStream to output file " + output);
        			out = new PrintStream(
        					new BufferedOutputStream(
        							new FileOutputStream(output.getAbsolutePath(), append)));
        		}
        		groovy.execGroovy(this, command, out);
        	}
        } catch (IOException e) {
        	throw new BuildException(e, getLocation());
        } finally {
        	if (out != null && out != System.out) {
        		out.close();
        	}
        }
    }
    
    void createProjectClasspath(Build build) {
    	Path classpath = createClasspath();
		// add project compiled output path
		classpath.createPathElement().setLocation(build.getConfig().getOutputDirectory(scope));
		if (!scope.isDefault()) {
			classpath.createPathElement().setLocation(build.getConfig().getOutputDirectory(Scope.compile));
		}
		
		// add resource directories
		for (File dir : build.getConfig().getResourceDirectories(scope)) {
			classpath.createPathElement().setLocation(dir);
		}
		if (!scope.isDefault()) {
			for (File dir : build.getConfig().getResourceDirectories(Scope.compile)) {
				classpath.createPathElement().setLocation(dir);
			}
		}
		
		// add jar classpaths
		for (File jarFile : build.getSolver().getClasspath(scope)) {
			classpath.createPathElement().setLocation(jarFile);
		}

		// add linked projects compiled output path, resource directories, and jar files
		for (Build linkedProject : build.getSolver().getLinkedModules()) {
			classpath.createPathElement().setLocation(linkedProject.getConfig().getOutputDirectory(Scope.compile));
			
			for (File dir : linkedProject.getConfig().getResourceDirectories(Scope.compile)) {
				classpath.createPathElement().setLocation(dir);
			}
			
			for (File jarFile : linkedProject.getSolver().getClasspath(Scope.compile)) {
				classpath.createPathElement().setLocation(jarFile);
			}
		}
    }
    
    void createForkClasspath() {
    	Path path;
		if (includeAntRuntime) {
			path = createClasspath();
			path.setPath(System.getProperty("java.class.path"));
		}
		String groovyHome = null;
		final String[] strings = getSysProperties().getVariables();
		if (strings != null) {
			for (String prop : strings) {
				if (prop.startsWith("-Dgroovy.home=")) {
					groovyHome = prop.substring("-Dgroovy.home=".length());
				}
			}
		}
		if (groovyHome == null) {
			groovyHome = System.getProperty("groovy.home");
		}
		if (groovyHome == null) {
			groovyHome = System.getenv("GROOVY_HOME");
		}
		if (groovyHome == null) {
			throw new IllegalStateException("Neither ${groovy.home} nor GROOVY_HOME defined.");
		}
		File jarDir = new File(groovyHome, "embeddable");
		if (!jarDir.exists()) {
			throw new IllegalStateException("GROOVY_HOME incorrectly defined. No embeddable directory found in: " + groovyHome);
		}
		final File[] files = jarDir.listFiles();
		for (File file : files) {
			try {
				getConsole().debug("Adding jar to classpath: " + file.getCanonicalPath());
			} catch (IOException e) {
				// ignore
			}
			path = createClasspath();
			path.setLocation(file);
		}
    }

    void executeFork() {
        getConsole().debug(getCommandLine().describeCommand());
    	super.execute();
    }    
}
