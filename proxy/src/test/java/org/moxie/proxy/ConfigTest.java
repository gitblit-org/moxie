/*
 * Copyright 2002-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moxie.proxy;

import java.io.File;
import java.net.URL;

import junit.framework.TestCase;

public class ConfigTest extends TestCase {

	ProxyConfig config;

	// public void testGetNoProxy () throws Exception
	// {
	// String[] s = config.getNoProxy();
	// assertEquals("a", s[0]);
	// assertEquals("b", s[1]);
	// assertEquals("c", s[2]);
	// }

	public void testNoProxy1() throws Exception {
		assertFalse(config.useProxy(new URL("http://b/x/y/z")));
	}

	public void testNoProxy2() throws Exception {
		assertTrue(config.useProxy(new URL("http://some.doma.in/x/y/z")));
	}

	public void testRedirect() throws Exception {
		assertEquals("http://maven.sateh.com/maven2/org/apache/something",
				config.getRedirect(new URL("http://repo1.maven.org/maven2/org/apache/something")).toString());
	}

	public void testRedirect2() throws Exception {
		assertEquals("http://maven.sateh.com/maven2/org/apache/something",
				config.getRedirect(new URL("http://maven.sateh.com/repository/org/apache/something"))
						.toString());
	}

	public void testRedirect3() throws Exception {
		assertEquals("http://maven.sateh.com/maven2/aopalliance/x",
				config.getRedirect(new URL("http://m2.safehaus.org/org/aopalliance/x")).toString());
	}

	public void testRedirect4() throws Exception {
		assertEquals("http://maven.sateh.com/maven2/org/x",
				config.getRedirect(new URL("http://m2.safehaus.org/org/x")).toString());
	}

	public void testIsAllowed() throws Exception {
		assertTrue(config.isAllowed(new URL("http://maven.sateh.com/maven2/org/x")));
	}

	public void testIsAllowed1() throws Exception {
		assertTrue(config.isAllowed(new URL("http://maven.sateh.com/maven2/org/x")));
	}

	public void testIsAllowed2() throws Exception {
		assertTrue(config
				.isAllowed(new URL(
						"http://people.apache.org/maven-snapshot-repository/org/apache/maven/plugins/maven-deploy-plugin/2.3-SNAPSHOT/")));
	}

	public void testIsAllowed3() throws Exception {
		assertFalse(config
				.isAllowed(new URL(
						"http://people.apache.org/maven-snapshot-repository/org/apache/maven/plugins/maven-source-plugin/")));
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		config = new ProxyConfig();
		config.parse(new File("proxy.moxie"));
	}
}
