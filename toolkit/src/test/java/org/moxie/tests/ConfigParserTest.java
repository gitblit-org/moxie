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
	public void testConfig() throws Exception {
		Config config = new Config(new File("build.moxie"), null, null);
		assertEquals("Moxie", config.getPom().name);
		assertEquals(8, config.getSourceFolders().size());
	}
}
