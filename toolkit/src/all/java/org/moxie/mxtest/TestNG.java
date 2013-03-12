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

import org.moxie.ant.AttributeReflector;
import org.moxie.ant.MxTest;
import org.moxie.maxml.MaxmlMap;
import org.moxie.utils.StringUtils;
import org.testng.TestNGAntTask;

/**
 * Utility class to set TestNG test execution. 
 */
public class TestNG {

	public static void test(MxTest mxtest, String jvmarg) {
		
		// use default TestNG reports
		TestNGAntTask testng = new TestNGAntTask();
		testng.setTaskName("test");
		testng.setProject(mxtest.getProject());
		testng.init();
				
		if (!StringUtils.isEmpty(jvmarg)) {
			testng.createJvmarg().setValue(jvmarg);
		}

		testng.setWorkingDir(mxtest.getProject().getBaseDir());
		testng.setOutputDir(mxtest.getTestReports());
		testng.setFailureProperty(mxtest.getFailureProperty());
		
		testng.createClasspath().add(mxtest.getUnitTestClasspath());
		testng.addClassfileset(mxtest.getUnitTests());
		
		// Cobertura properties
		testng.addSysproperty(mxtest.getCoberturaFileProperty());
		
		// EMMA properties
		testng.addSysproperty(mxtest.getEmmaFileProperty());
		testng.addSysproperty(mxtest.getEmmaMergeProperty());
	
		// configure properties from Moxie file
		MaxmlMap attributes = mxtest.getBuild().getConfig().getTaskAttributes("testng");
		if (attributes != null) {
			AttributeReflector.setAttributes(mxtest.getProject(), testng, attributes);
		}

		testng.execute();
	}
}
