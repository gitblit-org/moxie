package org.moxie.tests;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.moxie.ToolkitConfig;


/**
 * Unit tests for the Moxie config parser.
 * 
 * @author James Moger
 * 
 */
public class ConfigParserTest extends Assert {

	@Test
	public void testBuildMoxie() throws Exception {
		ToolkitConfig config = new ToolkitConfig(new File("build.moxie"), null, null);
		assertEquals("Moxie-Toolkit", config.getPom().name);
		assertEquals(6, config.getSourceDirectories().size());
	}
	
	@Test
	public void testSettingsMoxie() throws Exception {
		ToolkitConfig config = new ToolkitConfig(new File("src/core/resources/settings.moxie"), null, null);
		assertEquals(2, config.getProxies().size());
		assertTrue(config.getProxies().get(1).matches("central", null));
	}

}
