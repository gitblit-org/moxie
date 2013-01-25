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
import java.text.MessageFormat;
import java.util.Date;

import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;
import org.moxie.Build;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.utils.StringUtils;

public class MxRun extends Java {
	
	public MxRun() {
		super();
		setTaskName("mx:run");
		setFork(true);
	}
	
	@Override
	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.refId());
		Console console = build.getConsole();
		
		console.title(getClass(), build.getPom().getCoordinates());

		if (StringUtils.isEmpty(getCommandLine().getClassname())) {
			getCommandLine().setClassname(build.getConfig().getProjectConfig().getMainclass());
		}

		Date start = new Date();
		console.key("started", start.toString());
		console.key("mainclass", getCommandLine().getClassname());
		console.log();
		
		Path classpath = createClasspath();
		// add project compiled output path
		classpath.createPathElement().setLocation(build.getConfig().getOutputFolder(Scope.compile));

		// add jar classpaths
		for (File jarFile : build.getSolver().getClasspath(Scope.compile)) {
			classpath.createPathElement().setLocation(jarFile);
		}

		// add linked projects compiled output path and jar files
		for (Build linkedProject : build.getSolver().getLinkedModules()) {
			classpath.createPathElement().setLocation(linkedProject.getConfig().getOutputFolder(Scope.compile));
			for (File jarFile : linkedProject.getSolver().getClasspath(Scope.compile)) {
				classpath.createPathElement().setLocation(jarFile);
			}
		}

		console.debug(getCommandLine().describeCommand());
		
		super.execute();
		Date end = new Date();
		console.key("finished", MessageFormat.format("{0} ({1} secs)", end, (end.getTime() - start.getTime())/1000L));
	}
}
