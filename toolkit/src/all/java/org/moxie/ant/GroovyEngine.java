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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.Script;
import groovy.util.AntBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.util.FileUtils;
import org.codehaus.groovy.ant.AntProjectPropertiesDelegate;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.tools.ErrorReporter;

public class GroovyEngine {

    private static final String PREFIX = "embedded_script_in_";
    private static final String SUFFIX = "groovy_Ant_task";

    protected void forkGroovy(MxGroovy task, final String txt) {
    	if ("".equals(txt.trim())) {
    		return;
    	}

    	try {
    		prepareGroovyScript(task, txt);
    		task.setClassname(task.useGroovyShell ? "groovy.lang.GroovyShell" : "org.codehaus.groovy.ant.Groovy");
    		task.executeFork();
    	} catch (Exception e) {
    		processError(task, e);
    	}
    	return;
    }
    
   /**
     * Exec the statement.
     *
     * @param txt the groovy source to exec
     * @param out not used?
     */
    protected void execGroovy(MxGroovy task, final String txt, final PrintStream out) {
        if ("".equals(txt.trim())) {
            return;
        }
        
        ClassLoader savedLoader = null;
        Thread thread = Thread.currentThread();
        ClassLoader baseClassLoader = GroovyShell.class.getClassLoader();
        if (task.contextClassLoader) {
            savedLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(baseClassLoader);
        }

        GroovyClassLoader classLoader = new GroovyClassLoader(baseClassLoader);
        if (task.getCommandLine().getClasspath() != null) {
            for (String path : task.getCommandLine().getClasspath().list()) {
                classLoader.addClasspath(path);
            }
        }
        
        CompilerConfiguration configuration = new CompilerConfiguration();
        if (task.stacktrace) {
        	configuration.setDebug(true);
        }
        GroovyShell groovy = new GroovyShell(classLoader, new Binding(), configuration);
        try {
            String scriptName = determineScriptName(task);
            parseAndRunScript(task, groovy, scriptName, txt, null);
        } finally {
            groovy.resetLoadedClasses();
            groovy.getClassLoader().clearCache();
            if (task.contextClassLoader) {
            	thread.setContextClassLoader(savedLoader);
            }
        }
    }

    private void parseAndRunScript(MxGroovy task, GroovyShell shell, String scriptName, String txt, File scriptFile) {
        try {
            final Script script;
            if (scriptFile != null) {
                script = shell.parse(scriptFile);
            } else {
                script = shell.parse(txt, scriptName);
            }
            final Project project = task.getProject();
            script.setProperty("ant", new AntBuilder(task));
            script.setProperty("project", project);
            script.setProperty("build", task.getBuild());
            script.setProperty("properties", new AntProjectPropertiesDelegate(project));
            script.setProperty("target", task.getOwningTarget());
            script.setProperty("task", this);
            script.setProperty("args", task.getCommandLine().getCommandline());
            script.setProperty("pom", task.getBuild().getPom());
            script.run();
        }
        catch (final MissingMethodException mme) {
            // not a script, try running through run method but properties will not be available
            if (scriptFile != null) {
                try {
                    shell.run(scriptFile, task.getCommandLine().getCommandline());
                } catch (IOException e) {
                    processError(task, e);
                }
            } else {
                shell.run(txt, scriptName, task.getCommandLine().getCommandline());
            }
        }
        catch (final CompilationFailedException e) {
            processError(task, e);
        } catch (IOException e) {
            processError(task, e);
        }
    }

    private void processError(MxGroovy task, Exception e) {
        StringWriter writer = new StringWriter();
        new ErrorReporter(e, false).write(new PrintWriter(writer));
        String message = writer.toString();
        throw new BuildException("Script Failed: " + message, e, task.getLocation());
    }

    private void prepareGroovyScript(MxGroovy task, String txt) throws IOException {
        String[] args = task.getCommandLine().getCommandline();
        // Temporary file - delete on exit, create (assured unique name).
        File tempFile = FileUtils.getFileUtils().createTempFile(PREFIX, SUFFIX, null, true, true);
        String[] commandline = new String[args.length + 1];
        DefaultGroovyMethods.write(tempFile, txt);
        commandline[0] = tempFile.getCanonicalPath();
        System.arraycopy(args, 0, commandline, 1, args.length);
        task.clearArgs();
        for (String arg : commandline) {
            Commandline.Argument argument = task.createArg();
            argument.setValue(arg);
        }
    }

    /**
     * Try to build a script name for the script of the groovy task to have an
     * helpful value in stack traces in case of exception.
     *
     * @return the name to use when compiling the script
     */
    private String determineScriptName(MxGroovy task) {
        if (task.srcFile != null) {
            return task.srcFile.getAbsolutePath();
        } else {
            String name = PREFIX;
            if (task.getLocation().getFileName().length() > 0)
                name += task.getLocation().getFileName().replaceAll("[^\\w_\\.]", "_").replaceAll("[\\.]", "_dot_");
            else
                name += SUFFIX;

            return name;
        }
    }
}
