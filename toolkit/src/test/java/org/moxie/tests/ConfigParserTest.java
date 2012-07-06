package org.moxie.tests;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.moxie.Config;


/**
 * Unit tests for the Moxie config parser.
 * 
 * @author James Moger
 * 
 */
public class ConfigParserTest extends Assert {

	@Test
	public void testBuildMoxie() throws Exception {
		Config config = new Config(new File("build.moxie"), null, null);
		assertEquals("Moxie-Toolkit", config.getPom().name);
		assertEquals(8, config.getSourceFolders().size());
	}
	
	@Test
	public void testSettingsMoxie() throws Exception {
		Config config = new Config(new File("src/core/resources/settings.moxie"), null, null);
		assertEquals(2, config.getProxies().size());
		assertTrue(config.getProxies().get(1).matches("central", null));
	}

}
