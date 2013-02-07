/*
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

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Javadoc;
import org.apache.tools.ant.taskdefs.Redirector;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.RedirectorElement;
import org.moxie.Build;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;

/**
 * Mx:Javadoc's purpose is to swallow and redirect noisey Javadoc output.
 * 
 * @author James Moger
 * 
 */
public class MxJavadoc extends Javadoc {

	private boolean addedFileset;
	private File destDir;
	private boolean redirectedOutput;
	private String queuedLine = null;

	private JavadocRedirector redirector = new JavadocRedirector(this);
	private RedirectorElement redirectorElement;

	public MxJavadoc() {
		super();
		setTaskName("mx:javadoc");
	}
	
	@Override
	public void addFileset(FileSet fs) {
		addedFileset = true;
		super.addFileset(fs);
	}
	
	@Override
	public void setDestdir(File dir) {
		destDir = dir;
		super.setDestdir(dir);
	}
	
	/**
	 * Add a <code>RedirectorElement</code> to this task.
	 * 
	 * @param redirectorElement
	 *            <code>RedirectorElement</code>.
	 */
	public void addConfiguredRedirector(RedirectorElement redirectorElement) {
		if (this.redirectorElement != null) {
			throw new BuildException("cannot have > 1 nested redirectors");
		}
		this.redirectorElement = redirectorElement;
	}

	@Override
	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.refId());
		Console console = build.getConsole();
		
		if (destDir == null) {
			// use default output folder
			super.setDestdir(build.getConfig().getJavadocTargetDirectory());
		}
		
		if (!addedFileset) {
			// add all compile source folders from project
			for (File folder : build.getConfig().getSourceDirectories(Scope.compile)) {
				FileSet fs = new FileSet();
				fs.setProject(getProject());
				fs.setDir(folder);
				addFileset(fs);
			}
		}

		console.title(getClass(), build.getPom().getCoordinates());
		
		if (redirectorElement != null) {
			console.log(1, "Generating Javadoc... please wait");
			redirectedOutput = true;
			redirectorElement.configure(redirector);
			redirector.createStreams();
		}

		super.execute();

		try {
			if (redirectedOutput) {
				redirector.complete();
			}
		} catch (IOException e) {
			throw new MoxieException(e);
		}
	}

	//
	// Override the logging of output in order to filter out Generating
	// messages. Generating messages are set to a priority of VERBOSE
	// unless they appear after what could be an informational message.
	//
	public void log(String msg, int msgLevel) {
		if (msgLevel == Project.MSG_INFO && msg.startsWith("Generating ")) {
			if (queuedLine != null) {
				processLine(queuedLine, Project.MSG_VERBOSE);
			}
			queuedLine = msg;
		} else {
			if (queuedLine != null) {
				if (msg.startsWith("Building ")) {
					processLine(queuedLine, Project.MSG_VERBOSE);
				} else {
					processLine(queuedLine, Project.MSG_INFO);
				}
				queuedLine = null;
			}
			processLine(msg, msgLevel);
		}
	}

	protected void processLine(String msg, int msgLevel) {
		if (!redirectedOutput) {
			// default logging
			super.log(msg, msgLevel);
			return;
		}

		switch (msgLevel) {
		case Project.MSG_ERR:
			if (redirector.getErrorStream() != null) {
				redirector.handleErrorOutput(msg + "\n");
			}
			break;
		default:
			if (redirector.getOutputStream() != null) {
				redirector.handleOutput(msg + "\n");
			}
			break;
		}
	}

	/**
	 * Stupid class to increase the visibility of the handle methods.
	 * 
	 * @author James Moger
	 * 
	 */
	private class JavadocRedirector extends Redirector {

		public JavadocRedirector(Task managingTask) {
			super(managingTask);
		}

		@Override
		public void handleOutput(String output) {
			super.handleOutput(output);
		}

		@Override
		public void handleErrorOutput(String output) {
			super.handleErrorOutput(output);
		}
	}
}
