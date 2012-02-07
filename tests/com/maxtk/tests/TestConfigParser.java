package com.maxtk.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Test;

import com.maxtk.Config;
import com.maxtk.MaxmlParser;
import com.maxtk.MaxmlParser.MaxmlException;
import com.maxtk.utils.FileUtils;

public class TestConfigParser {
	String config = "name: Maxilla\ndescription: Project Build Toolkit\nversion: 0.1.0\nurl: http://github.com/gitblit/maxilla\nartifactId: maxilla\nvendor: James Moger\nconfigureEclipseClasspath: true\nsourceFolders: [core, maxjar, maxdoc]\nmap: { \na1: 12n\na2: 3.14f\na3 : {\nb1:100l\nb2 : {\nc1:6.023d\nc2:c2value\n}\nb3:b3value\n}\na4: a4value\n}\noutputFolder: bin\nmavenUrls: [mavencentral]\ndependencyFolder: ext\ndependencies:\n - [ant, 1.7.0, org/apache/ant]\n - [markdownpapers-core, 1.2.5, org/tautua/markdownpapers]\nsimpledate:2003-07-04\ncanonical:2001-07-04T16:08:56.235Z\niso8601:2002-07-04T12:08:56.235-0400";

	@SuppressWarnings("rawtypes")
	@Test
	public void testValueParsing() throws Exception {
		// strings
		assertEquals("string", MaxmlParser.parseValue("string"));
		assertEquals("string", MaxmlParser.parseValue("'string'"));
		assertEquals("string", MaxmlParser.parseValue("\"string\""));
		
		// numerics
		assertEquals(101, MaxmlParser.parseValue("101n"));
		assertEquals(200L, MaxmlParser.parseValue("200L"));
		assertEquals(2.3f, MaxmlParser.parseValue("2.3f"));
		assertEquals(4.6d, MaxmlParser.parseValue("4.6d"));
		
		// booleans
		assertEquals(true, MaxmlParser.parseValue("true"));
		assertEquals(true, MaxmlParser.parseValue("yes"));
		assertEquals(true, MaxmlParser.parseValue("on"));
		
		assertEquals(false, MaxmlParser.parseValue("false"));
		assertEquals(false, MaxmlParser.parseValue("no"));
		assertEquals(false, MaxmlParser.parseValue("off"));
		
		// null
		assertNull(MaxmlParser.parseValue("~"));
		
		// lists
		assertEquals("[a, b, c]", MaxmlParser.parseValue("[a, b, c]").toString());
		assertEquals(3, ((List) MaxmlParser.parseValue("[a, b, c]")).size());
		
		// dates
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		assertEquals("2003-07-04", df.format(((Date)MaxmlParser.parseValue("2003-07-04"))));
		
		df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		assertEquals("2003-07-04T15:15:15Z", df.format(((Date)MaxmlParser.parseValue("2003-07-04T15:15:15Z"))));
		
		df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		df.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		assertEquals("2003-07-04T15:15:15-0400", df.format(((Date)MaxmlParser.parseValue("2003-07-04T15:15:15-0400"))));
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void testParse() throws MaxmlException {
		Map<String, Object> map = MaxmlParser.parse(config);
		assertTrue(map.size() > 0);
		assertTrue(map.containsKey("name"));
		assertEquals("Maxilla", map.get("name"));
		assertEquals(3, ((List) map.get("sourcefolders")).size());
		assertEquals(2, ((List) map.get("dependencies")).size());
		assertEquals(4, ((Map) map.get("map")).size());
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void testParseFile() throws MaxmlException {
		String content = FileUtils.readContent(new File("build.max"), "\n");
		Map<String, Object> map = MaxmlParser.parse(content);
		assertTrue(map.size() > 0);
		assertTrue(map.containsKey("name"));
		assertEquals("Maxilla", map.get("name"));
		assertEquals(5, ((List) map.get("sourcefolders")).size());
		assertEquals(4, ((List) map.get("dependencies")).size());
	}
	
	@Test
	public void testConfig() throws Exception {
		Config config = Config.load("build.max");
		assertEquals("Maxilla", config.getName());
		assertEquals(5, config.getSourceFolders().size());
	}

	@Test
	public void testInstantiation() throws MaxmlException {
		TestObject test = MaxmlParser.parse(config, TestObject.class);
		assertNotNull(test);
		assertEquals("Maxilla", test.name);
		assertEquals(3, test.sourceFolders.size());
		assertEquals(2, test.dependencies.size());
		assertEquals(4, test.map.size());
	}
	
	public static class TestObject {

		public String name;
		public String description;
		public String version;
		public String url;
		public String artifactId;
		public String vendor;
		public boolean configureEclipseClasspath;
		public List<String> sourceFolders;
		public String outputFolder;
		public Map<String, Object> map;
		public List<String> mavenUrls;
		public String dependencyFolder;
		public List<List<String>> dependencies;
		public Date canonical;
		public Date iso8601;
		public Date simpleDate;
	}
}
