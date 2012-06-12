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
package org.moxie.utils;
import java.io.File;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moxie.utils.FileUtils;

/**
 * Aggregates the many Bootstrap LESS files into a monolithic LESS file with an
 * import for custom LESS overrides.
 * 
 * @author James Moger
 *
 */
public class LessConsolidator {

	public static void main(String... args) {
		
		process(new File("src/less/resources/bootstrap.less"), new File("src/main/resources/bootstrap/css/bootstrap.less"));
	}
	
	private static void process(File source, File target) {
		long start = System.currentTimeMillis();
		System.out.println(MessageFormat.format("aggregating {0}...", source.getAbsolutePath()));
		String less = aggregate(source);
		
		less += "@import \"custom.less\";\n";
		
		FileUtils.writeContent(target, less);
		System.out.println(MessageFormat.format("generated in {0} msecs", System.currentTimeMillis() - start));
	}
	
	private static String aggregate(File bootstrap) {
		StringBuilder aggregate = new StringBuilder();
		String content = FileUtils.readContent(bootstrap, "\n");
		for (String line : content.split("\n")) {
			if (line.startsWith("@import")) {
				// import
				Pattern p = Pattern.compile("(\")(.+)(\")");
				Matcher m = p.matcher(line);
				if (m.find()) {
					String importLess = m.group(2);
					File importFile = new File(bootstrap.getParentFile(), importLess);
					String importContent = FileUtils.readContent(importFile, "\n");
					aggregate.append(importContent).append('\n');
				}
			} else {
				// pass-through
				aggregate.append(line).append('\n');
			}
		}
		
		return aggregate.toString();
	}
}
