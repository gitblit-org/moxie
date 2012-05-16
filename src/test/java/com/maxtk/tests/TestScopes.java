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

import com.maxtk.Scope;

public class TestScopes extends Assert {

	@Test
	public void testClasspathScopes() {
		// compile classpath
		assertTrue(Scope.compile.includeOnClasspath(Scope.compile));
		assertTrue(Scope.compile.includeOnClasspath(Scope.provided));
		assertFalse(Scope.compile.includeOnClasspath(Scope.runtime));
		assertFalse(Scope.compile.includeOnClasspath(Scope.test));
		assertTrue(Scope.compile.includeOnClasspath(Scope.system));

		// provided classpath (unused)
		assertTrue(Scope.provided.includeOnClasspath(Scope.compile));
		assertTrue(Scope.provided.includeOnClasspath(Scope.provided));
		assertFalse(Scope.provided.includeOnClasspath(Scope.runtime));
		assertFalse(Scope.provided.includeOnClasspath(Scope.test));
		assertTrue(Scope.provided.includeOnClasspath(Scope.system));

		// runtime classpath
		assertTrue(Scope.runtime.includeOnClasspath(Scope.compile));
		assertFalse(Scope.runtime.includeOnClasspath(Scope.provided));
		assertTrue(Scope.runtime.includeOnClasspath(Scope.runtime));
		assertFalse(Scope.runtime.includeOnClasspath(Scope.test));
		assertTrue(Scope.runtime.includeOnClasspath(Scope.system));

		// test classpath
		assertTrue(Scope.test.includeOnClasspath(Scope.compile));
		assertTrue(Scope.test.includeOnClasspath(Scope.provided));
		assertTrue(Scope.test.includeOnClasspath(Scope.runtime));
		assertTrue(Scope.test.includeOnClasspath(Scope.test));
		assertTrue(Scope.test.includeOnClasspath(Scope.system));
		
		// check nulls
		assertFalse(Scope.compile.includeOnClasspath(null));
		assertFalse(Scope.provided.includeOnClasspath(null));
		assertFalse(Scope.runtime.includeOnClasspath(null));
		assertFalse(Scope.test.includeOnClasspath(null));
		assertFalse(Scope.system.includeOnClasspath(null));
	}
	
	@Test
	public void testTransitiveScopes() {
		// compile dependency
		assertEquals(Scope.compile, Scope.compile.getTransitiveScope(Scope.compile));
		assertEquals(null, Scope.compile.getTransitiveScope(Scope.provided));
		assertEquals(Scope.runtime, Scope.compile.getTransitiveScope(Scope.runtime));
		assertEquals(null, Scope.compile.getTransitiveScope(Scope.test));

		// provided dependency
		assertEquals(Scope.provided, Scope.provided.getTransitiveScope(Scope.compile));
		assertEquals(null, Scope.provided.getTransitiveScope(Scope.provided));
		assertEquals(Scope.provided, Scope.provided.getTransitiveScope(Scope.runtime));
		assertEquals(null, Scope.provided.getTransitiveScope(Scope.test));

		// runtime dependency
		assertEquals(Scope.runtime, Scope.runtime.getTransitiveScope(Scope.compile));
		assertEquals(null, Scope.runtime.getTransitiveScope(Scope.provided));
		assertEquals(Scope.runtime, Scope.runtime.getTransitiveScope(Scope.runtime));
		assertEquals(null, Scope.runtime.getTransitiveScope(Scope.test));

		// test dependency
		assertEquals(Scope.test, Scope.test.getTransitiveScope(Scope.compile));
		assertEquals(null, Scope.test.getTransitiveScope(Scope.provided));
		assertEquals(Scope.test, Scope.test.getTransitiveScope(Scope.runtime));
		assertEquals(null, Scope.test.getTransitiveScope(Scope.test));

		// system dependency, no transitives
		assertEquals(null, Scope.compile.getTransitiveScope(Scope.system));
		assertEquals(null, Scope.provided.getTransitiveScope(Scope.system));
		assertEquals(null, Scope.runtime.getTransitiveScope(Scope.system));
		assertEquals(null, Scope.test.getTransitiveScope(Scope.system));

		assertEquals(null, Scope.system.getTransitiveScope(Scope.compile));
		assertEquals(null, Scope.system.getTransitiveScope(Scope.provided));
		assertEquals(null, Scope.system.getTransitiveScope(Scope.runtime));
		assertEquals(null, Scope.system.getTransitiveScope(Scope.test));

		assertEquals(null, Scope.system.getTransitiveScope(Scope.system));
}
}
