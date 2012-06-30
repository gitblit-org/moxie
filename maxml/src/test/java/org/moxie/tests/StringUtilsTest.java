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

import org.junit.Assert;
import org.junit.Test;
import org.moxie.utils.StringUtils;


public class StringUtilsTest extends Assert {

	@Test
	public void testParsing1() {		
		assertEquals("repo1.apache.org_maven2", StringUtils.urlToFolder("http://repo1.apache.org/maven2"));
		assertEquals("repo1.apache.org_maven_2", StringUtils.urlToFolder("https://repo1.apache.org/maven/2"));
		assertEquals("maven.restlet.org", StringUtils.urlToFolder("https://maven.restlet.org"));
	}
}
