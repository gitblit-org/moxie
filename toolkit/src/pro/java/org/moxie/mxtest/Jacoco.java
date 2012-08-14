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
package org.moxie.mxtest;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.resources.Union;
import org.jacoco.agent.AgentJar;
import org.jacoco.ant.ReportTask;
import org.jacoco.ant.ReportTask.GroupElement;
import org.jacoco.ant.ReportTask.SourceFilesElement;
import org.jacoco.core.runtime.AgentOptions;
import org.moxie.Toolkit.Key;
import org.moxie.ant.MxTest;

/**
 * Utility class for JaCoCo code-coverage.
 */
public class Jacoco {

	public static String newJvmarg(MxTest mxtest) {
		AgentOptions options = new AgentOptions();
		options.setDestfile(mxtest.getJaCoCoData().getAbsolutePath());	
		// TODO reflective attributes here
		String vmarg = options.getVMArgument(getAgentFile(mxtest.getProject()));
		return vmarg;
	}
	
	private static File getAgentFile(Project project) {
		try {
			File agentFile = null;
			final String agentFileLocation = project.getProperty("_jacoco.agentFile");
			if (agentFileLocation != null) {
				agentFile = new File(agentFileLocation);
			} else {
				agentFile = AgentJar.extractToTempLocation();
				project.setProperty("_jacoco.agentFile", agentFile.toString());
			}

			return agentFile;
		} catch (final IOException e) {
			throw new BuildException("Unable to extract agent jar", e);
		}
	}

	public static void report(MxTest mxtest) {
		ReportTask task = new ReportTask();
		task.setTaskName("report");
		task.setProject(mxtest.getProject());
		task.init();
		
		// execution data
		Union executiondata = task.createExecutiondata();
		Path jacocodata = new Path(mxtest.getProject());
		jacocodata.setPath(mxtest.getJaCoCoData().getAbsolutePath());
		executiondata.add(jacocodata);
		
		GroupElement structure = task.createStructure();
		structure.setName(mxtest.getProjectTitle());
		
		// classfiles
		Union classfiles = structure.createClassfiles();
		Path outputpath = new Path(mxtest.getProject());
		outputpath.setPath(mxtest.getClassesFolder().getAbsolutePath());
		classfiles.add(outputpath);
		
		// source files
		// TODO encoding
		SourceFilesElement sourcefiles = structure.createSourcefiles();	
		Path sourcepath = new Path(mxtest.getProject());
		sourcepath.setRefid(new Reference(mxtest.getProject(), Key.compile_sourcepath.refId()));
		sourcefiles.add(sourcepath);
		
		// report output folder
		task.createHtml().setDestdir(mxtest.getCoverageReports());
		
		task.execute();
	}
}
