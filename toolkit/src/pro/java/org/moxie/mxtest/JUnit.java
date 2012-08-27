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

import org.apache.tools.ant.taskdefs.optional.junit.AggregateTransformer;
import org.apache.tools.ant.taskdefs.optional.junit.BatchTest;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement.TypeAttribute;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator;
import org.apache.tools.ant.types.FileSet;
import org.moxie.ant.AttributeReflector;
import org.moxie.ant.MxTest;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.StringUtils;

/**
 * Utility class to setup JUnit test execution.
 */
public class JUnit {

	public static void test(MxTest mxtest, String jvmarg) {
		JUnitTask junit = null;
		try {
			// constructor throws Exception. Really??
			junit = new JUnitTask();
		} catch (Exception e) {
		}
		junit.setTaskName("test");
		junit.setProject(mxtest.getProject());
		junit.init();
		
		if (!StringUtils.isEmpty(jvmarg)) {
			junit.createJvmarg().setValue(jvmarg);
		}
	
		junit.setFailureProperty(mxtest.getFailureProperty());
		junit.createClasspath().add(mxtest.getUnitTestClasspath());
		
		junit.addConfiguredSysproperty(mxtest.getCoberturaFileProperty());
		junit.addConfiguredSysproperty(mxtest.getEmmaFileProperty());
		junit.addConfiguredSysproperty(mxtest.getEmmaMergeProperty());
		
		BatchTest batchTest = junit.createBatchTest();
		batchTest.setFork(true);
		batchTest.setTodir(mxtest.getUnitTestOutputFolder());
		batchTest.addFileSet(mxtest.getUnitTests());
		
		TypeAttribute xml = (TypeAttribute) TypeAttribute.getInstance(TypeAttribute.class, "xml");
		FormatterElement formatter = new FormatterElement();
		formatter.setProject(mxtest.getProject());
		formatter.setType(xml);
		junit.addFormatter(formatter);

		// configure properties from Moxie file
		MaxmlMap testAttributes = mxtest.getBuild().getConfig().getTaskAttributes("junit");
		if (testAttributes != null) {
			AttributeReflector.setAttributes(mxtest.getProject(), junit, testAttributes);
		}

		junit.execute();

		XMLResultAggregator junitReport = new XMLResultAggregator();
		junitReport.setTaskName("test");
		junitReport.setProject(mxtest.getProject());
		junitReport.init();
				
		FileSet fileSet = new FileSet();
		fileSet.setProject(mxtest.getProject());
		fileSet.setDir(mxtest.getUnitTestOutputFolder());
		fileSet.setIncludes("TEST-*.xml");	 	
		junitReport.addFileSet(fileSet);
		
		AggregateTransformer report = junitReport.createReport();
		// configure properties from Moxie file
		MaxmlMap reportAttributes = mxtest.getBuild().getConfig().getTaskAttributes("junitreport");
		if (reportAttributes != null) {
			AttributeReflector.setAttributes(mxtest.getProject(), report, reportAttributes);
		}
		report.setTodir(mxtest.getTestReports());
		
		junitReport.setTodir(mxtest.getUnitTestOutputFolder());

		junitReport.execute();
	}
}
