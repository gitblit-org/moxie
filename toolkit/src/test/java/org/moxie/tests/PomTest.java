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
package org.moxie.tests;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.moxie.BuildConfig;
import org.moxie.Dependency;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.Solver;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlException;
import org.moxie.utils.FileUtils;


public class PomTest extends Assert {

	private Solver getSolver() throws IOException, MaxmlException {
		// set mx.root to a temporary folder
		File folder = new File(File.createTempFile("who", "cares").getParentFile(), "moxie");
		if (folder.exists()) {
			FileUtils.delete(folder);
		}
		folder.mkdirs();
		System.setProperty("mx.root", folder.getAbsolutePath());
		System.out.println("mx.root=" + folder.getAbsolutePath());
		BuildConfig config = new BuildConfig(new File("test.moxie"), null);		
		Solver solver = new Solver(new Console(), config);				
		return solver;
	}
	
	private Solver solve(Dependency dep) throws IOException, MaxmlException {
		Solver solver = getSolver();
		solver.getBuildConfig().getPom().addDependency(dep, Scope.compile);
		solver.solve();
		return solver;
	}
	
	@Test
	public void testParsing1() throws IOException, MaxmlException {
		Dependency dep = new Dependency("org.eclipse.jetty:jetty-ajp:7.4.2.v20110526");
		Solver solver = solve(dep);
		Pom pom = solver.getPom(dep);
		assertEquals("org.eclipse.jetty", pom.groupId);
		assertEquals("7.4.2.v20110526", pom.version);
	}
	
	@Test
	public void testParentProperties() throws Exception {
		Dependency dep = new Dependency("org.jboss.resteasy.mobile:resteasy-mobile:1.0.0");		
		Solver solver = solve(dep);
		// [httpcomponents-client:4.0.3]
		//    ${httpcore.version} = 4.0.1
		// [httpclient:4.0.3]
		//    [httpcore:${httpcore.version}]
		// [resteasy-mobile:1.0.0]
		Set<Dependency> deps = solver.getDependencies(Scope.compile);
		Dependency httpcore = new Dependency("org.apache.httpcomponents:httpcore:4.0.1");		
		assertTrue(deps.contains(httpcore));
	}

}
