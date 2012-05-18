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
package com.maxtk.tests;

import org.junit.Assert;
import org.junit.Test;

import com.maxtk.Dependency;

public class TestDependencies extends Assert {

	@Test
	public void testParsing1() {
		Dependency dep = new Dependency("com.maxtk:maxilla:1.0.0");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertNull(dep.classifier);
		assertEquals("jar", dep.type);
		assertTrue(dep.resolveDependencies);
		assertFalse(dep.optional);
	}

	@Test
	public void testParsing2() {
		Dependency dep = new Dependency("'com.maxtk:maxilla:1.0.0:jdk15'");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertEquals("jdk15", dep.classifier);
		assertEquals("jar", dep.type);
		assertTrue(dep.resolveDependencies);
		assertFalse(dep.optional);
	}

	@Test
	public void testParsing3() {
		Dependency dep = new Dependency("\"com.maxtk:maxilla:1.0.0:jdk15:jar\"");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertEquals("jdk15", dep.classifier);
		assertEquals("jar", dep.type);
		assertTrue(dep.resolveDependencies);
		assertFalse(dep.optional);
	}
	
	@Test
	public void testParsing4() {
		Dependency dep = new Dependency("com.maxtk:maxilla:1.0.0@zip");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertNull(dep.classifier);
		assertEquals("zip", dep.type);
		assertFalse(dep.resolveDependencies);
		assertFalse(dep.optional);
	}

	@Test
	public void testParsing5() {
		Dependency dep = new Dependency("com.maxtk:maxilla:1.0.0:jdk15@exe");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertEquals("jdk15", dep.classifier);
		assertEquals("exe", dep.type);
		assertFalse(dep.resolveDependencies);
		assertFalse(dep.optional);
	}

	@Test
	public void testParsing6() {
		Dependency dep = new Dependency("com/maxtk:maxilla:1.0.0::doc optional -com.maxtk.ignore:ignore1 -com.maxtk.ignore:ignore2");
		assertEquals("com.maxtk", dep.groupId);
		assertEquals("maxilla", dep.artifactId);
		assertEquals("1.0.0", dep.version);
		assertNull(dep.classifier);
		assertEquals("doc", dep.type);
		assertTrue(dep.resolveDependencies);
		assertTrue(dep.optional);
		
		assertTrue(dep.exclusions.contains("com.maxtk.ignore:ignore1"));
		assertTrue(dep.exclusions.contains("com.maxtk.ignore:ignore2"));
	}

}
