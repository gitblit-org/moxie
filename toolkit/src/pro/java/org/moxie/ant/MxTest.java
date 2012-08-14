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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.moxie.Build;
import org.moxie.BuildConfig;
import org.moxie.Scope;
import org.moxie.Toolkit.Key;
import org.moxie.mxtest.Cobertura;
import org.moxie.mxtest.Emma;
import org.moxie.mxtest.JUnit;
import org.moxie.mxtest.Jacoco;
import org.moxie.mxtest.TestNG;
import org.moxie.utils.FileUtils;

public class MxTest extends MxTask {

	File unitTestOutputFolder;
	File testReports;
	File coverageReports;
	File instrumentedBuild;
	File coberturaData;
	File emmaData;
	File jacocoData;
	File classesFolder;
	File testClassesFolder;
	Path unitTestClasspath;
	FileSet unitTests;
	boolean failOnError;
	
	public MxTest() {
		super();
		setTaskName("mx:test");
	}
	
	public File getUnitTestOutputFolder() {
		return unitTestOutputFolder;
	}
	
	public File getTestReports() {
		return testReports;
	}
	
	public File getCoverageReports() {
		return coverageReports;
	}
	
	public File getInstrumentedBuild() {
		return instrumentedBuild;
	}
	
	public File getCoberturaData() {
		return coberturaData;
	}
	
	public File getEmmaData() {
		return emmaData;
	}

	public File getJaCoCoData() {
		return jacocoData;
	}

	public File getClassesFolder() {
		return classesFolder;
	}
	
	public File getTestClassesFolder() {
		return testClassesFolder;
	}
	
	public Path getUnitTestClasspath() {
		return unitTestClasspath;
	}
	
	public FileSet getUnitTests() {
		return unitTests;
	}
	
	public String getFailureProperty() {
		return "unit.test.failed";
	}
	
	public boolean getFailOnError() {
		return failOnError;
	}
	
	public void setFailOnError(boolean value) {
		this.failOnError = value;
	}
	
	public Environment.Variable getCoberturaFileProperty() {
		return newFile("net.sourceforge.cobertura.datafile", coberturaData.getAbsolutePath());
	}
	
	public Environment.Variable getEmmaFileProperty() {
		return newFile("emma.coverage.out.file", emmaData.getAbsolutePath());
	}
	
	public Environment.Variable getEmmaMergeProperty() {
		return newValue("emma.coverage.out.merge", "true");
	}
	
	public static Environment.Variable newValue(String key, String value) {
		Environment.Variable v = new Environment.Variable();
		v.setKey(key);
		v.setValue(value);
		return v;
	}
	
	public static Environment.Variable newFile(String key, String value) {
		Environment.Variable v = new Environment.Variable();
		v.setKey(key);
		v.setFile(new File(value));
		return v;
	}	
	
	@Override
	public void execute() throws org.apache.tools.ant.BuildException {

		/*
		 * Prepare all variables and state.
		 */
		
		Build build = getBuild();
		BuildConfig config = build.getConfig();
		
		// generate unit test info into build/tests
		unitTestOutputFolder = new File(config.getOutputFolder(null), "tests");
		FileUtils.delete(unitTestOutputFolder);
		unitTestOutputFolder.mkdirs();

		// generate unit test info into target/tests
		testReports = new File(config.getReportsFolder(), "tests");
		FileUtils.delete(testReports);
		testReports.mkdirs();
		
		// instrument classes for code coverages into build/instrumented-classes
		instrumentedBuild = new File(config.getOutputFolder(null), "instrumented-classes");
		FileUtils.delete(instrumentedBuild);
		instrumentedBuild.mkdirs();

		// generate code coverage report into target/coverage
		coverageReports = new File(config.getReportsFolder(), "coverage");
		FileUtils.delete(coverageReports);
		coverageReports.mkdirs();

		// delete Corbertura metadata
		coberturaData = new File(config.getOutputFolder(null), "cobertura.ser");
		coberturaData.delete();

		// delete EMMA metadata
		emmaData = new File(config.getOutputFolder(null), "metadata.emma");
		emmaData.delete();

		// delete JaCoCo metadata
		jacocoData = new File(config.getOutputFolder(null), "jacoco.exec");
		jacocoData.delete();

		classesFolder = config.getOutputFolder(Scope.compile);
		testClassesFolder = config.getOutputFolder(Scope.test);

		// define the test class fileset
		unitTests = new FileSet();
		unitTests.setProject(getProject());
		unitTests.setDir(testClassesFolder);
		// TODO this needs to be a property
		unitTests.createInclude().setName("**/*Test.class");

		// classpath for tests
		// instrumented classes, unit test classes, and unit test libraries
		unitTestClasspath = new Path(getProject());
		unitTestClasspath.createPathElement().setPath(instrumentedBuild.getAbsolutePath());
		unitTestClasspath.createPath().setRefid(new Reference(getProject(), Key.test_classpath.refId()));
		unitTestClasspath.createPath().setRefid(new Reference(getProject(), Key.build_classpath.refId()));
		unitTestClasspath.createPathElement().setPath(testClassesFolder.getAbsolutePath());
		
		// log the unit test classpath to the console in debug mode		
		build.getConsole().debug("unit test classpath");
		for (String element : unitTestClasspath.toString().split(File.pathSeparator)) {
			build.getConsole().debug(1, element);
		}

		/*
		 * Do the work.
		 */
		
		// compile the code and unit test classes
		MxJavac compile = new MxJavac();
		compile.setProject(getProject());
		compile.setScope(Scope.test.name());
		compile.execute();

		// instrument code classes
		if (hasClass("net.sourceforge.cobertura.ant.InstrumentTask")) {			
			Cobertura.instrument(this);
		} else if (hasClass("com.vladium.emma.emmaTask")) {
			Emma.instrument(this);
		} else if (hasClass("org.jacoco.ant.AbstractCoverageTask")) {
			// jacoco wraps unit test tasks
		} else {
			build.getConsole().warn("SKIPPING code-coverage!");
			build.getConsole().warn("add \"- build jacoco\", \"- build cobertura\", or \"- build emma\" to your dependencies for code-coverage.");
		}
		
		// optional jvmarg for running unit tests
		String jvmarg = null;
		if (hasClass("org.jacoco.ant.AbstractCoverageTask")) {
			jvmarg = Jacoco.newJvmarg(this);
		}
		
		// execute unit tests
		if (hasClass("org.testng.TestNGAntTask")) {	
			TestNG.test(this, jvmarg);
		} else if (hasClass("junit.framework.Test")) {
			JUnit.test(this, jvmarg);
		} else {
			build.getConsole().warn("SKIPPING unit tests!");
			build.getConsole().warn("add \"- test junit\" or \"- test testng\" to your dependencies to execute unit tests.");
		}
		
		// generate code coverage reports
		if (hasClass("net.sourceforge.cobertura.ant.ReportTask")) {
			Cobertura.report(this);
		} else if (hasClass("com.vladium.emma.report.reportTask")) {
			Emma.report(this);
		} else if (hasClass("org.jacoco.ant.ReportTask")) {
			Jacoco.report(this);
		}
		
		if ((getProject().getProperty(getFailureProperty()) != null) && failOnError) {
			throw new BuildException(MessageFormat.format("{0} has failed unit tests! Build aborted!", build.getPom().getArtifactId()));
		}
	}
}
