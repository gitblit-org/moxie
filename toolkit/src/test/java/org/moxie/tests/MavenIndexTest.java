package org.moxie.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;
import org.moxie.IMavenCache;
import org.moxie.MavenCache;
import org.moxie.utils.FileUtils;

public class MavenIndexTest extends Assert {
	
	protected String readResource(String resource) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			InputStream is = getClass().getResourceAsStream("/" + resource);
			
			byte [] buffer = new byte[32767];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return os.toString();
	}

	@Test
	public void generateMoxieMavenIndex() {
		String template = readResource("maven/artifact.json");
		IMavenCache cache = new MavenCache(new File("../maven"));
		String index = cache.generatePomIndex(template.trim(), ",\n");
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		sb.append(index);
		sb.append("]\n");
		FileUtils.writeContent(new File("../maven/artifacts.json"), sb.toString());
	}
}
