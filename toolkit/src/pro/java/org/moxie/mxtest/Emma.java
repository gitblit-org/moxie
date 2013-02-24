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

import org.apache.tools.ant.types.Reference;
import org.moxie.Toolkit.Key;
import org.moxie.ant.AttributeReflector;
import org.moxie.ant.MxTest;
import org.moxie.maxml.MaxmlMap;

import com.vladium.emma.emmaTask;
import com.vladium.emma.ant.XFileSet;
import com.vladium.emma.instr.instrTask;
import com.vladium.emma.report.reportTask;

/**
 * Utility class for EMMA code-coverage.
 */
public class Emma {

	public static void instrument(MxTest mxtest) {
		emmaTask emma = new emmaTask();
		emma.setTaskName("emma");
		emma.setProject(mxtest.getProject());
		emma.init();
		
		instrTask instr = (instrTask) emma.createInstr();
		instr.setTaskName("instr");
		instr.setProject(mxtest.getProject());
		instr.setMetadatafile(mxtest.getEmmaData());
		instr.setDestdir(mxtest.getInstrumentedBuild());
		instr.createInstrpath().setLocation(mxtest.getClassesDir());
		MaxmlMap instrAttributes = mxtest.getBuild().getConfig().getTaskAttributes("emma");
		if (instrAttributes != null) {
			AttributeReflector.setAttributes(mxtest.getProject(), instr, instrAttributes);
		}
		
		emma.execute();
	}
	
	public static void report(MxTest mxtest) {
		emmaTask emma = new emmaTask();
		emma.setTaskName("emma");
		emma.setProject(mxtest.getProject());
		emma.init();
		
		reportTask report = (reportTask) emma.createReport();
		report.setTaskName("report");
		report.setProject(mxtest.getProject());
		report.init();

		MaxmlMap reportAttributes = mxtest.getBuild().getConfig().getTaskAttributes("emmareport");
		if (reportAttributes != null) {
			AttributeReflector.setAttributes(mxtest.getProject(), report, reportAttributes);
		}

		report.setSourcepathRef(new Reference(mxtest.getProject(), Key.compileSourcePath.referenceId()));
		XFileSet fileSet = new XFileSet();
		fileSet.setProject(mxtest.getProject());
		fileSet.setFile(mxtest.getEmmaData());
		report.addFileset(fileSet);
		report.createHtml().setOutfile(new File(mxtest.getCoverageReports(), "index.html").getAbsolutePath());
		
		emma.execute();
	}
}
