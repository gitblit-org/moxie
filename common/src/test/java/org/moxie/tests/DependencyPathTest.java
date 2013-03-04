package org.moxie.tests;

import org.junit.Assert;
import org.junit.Test;
import org.moxie.Constants;
import org.moxie.Dependency;

public class DependencyPathTest extends Assert {
	
	@Test
	public void testFilenames1() {		
		String pattern = "[artifactId]-[version](-[classifier])(-[revision]).[ext]";
		
		Dependency dep = new Dependency("org.moxie:moxie-toolkit:1.0.0");
		String filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("moxie-toolkit-1.0.0.jar", filename);
		
		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("moxie-toolkit-1.0.0-sources.jar", filename);

		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources:zip");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("moxie-toolkit-1.0.0-sources.zip", filename);

	}
	
	@Test
	public void testFilenames2() {		
		String pattern = "[groupId]-[artifactId]-[version](-[classifier])(-[revision]).[ext]";
		
		Dependency dep = new Dependency("org.moxie:moxie-toolkit:1.0.0");
		String filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("org.moxie-moxie-toolkit-1.0.0.jar", filename);
		
		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("org.moxie-moxie-toolkit-1.0.0-sources.jar", filename);

		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources:zip");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("org.moxie-moxie-toolkit-1.0.0-sources.zip", filename);
	}
	
	@Test
	public void testFilenames3() {		
		String pattern = "lib-[artifactId]-[version](-[classifier])(-[revision]).[ext]";
		
		Dependency dep = new Dependency("org.moxie:moxie-toolkit:1.0.0");
		String filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("lib-moxie-toolkit-1.0.0.jar", filename);
		
		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("lib-moxie-toolkit-1.0.0-sources.jar", filename);

		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources:zip");
		filename = Dependency.getFilename(dep, dep.extension, pattern);
		assertEquals("lib-moxie-toolkit-1.0.0-sources.zip", filename);
	}
	
	@Test
	public void testPaths1() {
		String pattern = Constants.MAVEN2_PATTERN;
		
		Dependency dep = new Dependency("org.moxie:moxie-toolkit:1.0.0");
		String path = Dependency.getMavenPath(dep, dep.extension, pattern);
		assertEquals("org/moxie/moxie-toolkit/1.0.0/moxie-toolkit-1.0.0.jar", path);
		
		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources");
		path = Dependency.getMavenPath(dep, dep.extension, pattern);
		assertEquals("org/moxie/moxie-toolkit/1.0.0/moxie-toolkit-1.0.0-sources.jar", path);
		
		dep = new Dependency("org.moxie:moxie-toolkit:1.0.0:sources:zip");
		path = Dependency.getMavenPath(dep, dep.extension, pattern);
		assertEquals("org/moxie/moxie-toolkit/1.0.0/moxie-toolkit-1.0.0-sources.zip", path);
	}

}
