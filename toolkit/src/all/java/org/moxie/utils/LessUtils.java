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

import org.moxie.less.LessEngine;
import org.moxie.less.LessException;

/**
 * Aggregates the many Bootstrap LESS files into a monolithic LESS file with an
 * import for custom LESS overrides.
 * 
 * @author James Moger
 *
 */
public class LessUtils {

	public static void main(String... args) {
		
		consolidate(new File("src/all/config/bootstrap.less"), new File("src/all/resources/bootstrap/css/bootstrap.less"));
	}
	
	public static void consolidate(File source, File target) {
		long start = System.currentTimeMillis();
		System.out.println(MessageFormat.format("consolidating {0}...", source.getAbsolutePath()));
		String less = consolidate(source);
		
		less += "@import \"custom.less\";\n";
		
		FileUtils.writeContent(target, less);
		System.out.println(MessageFormat.format("generated in {0} msecs", System.currentTimeMillis() - start));
	}
	
	private static String consolidate(File bootstrap) {
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
	
	public static void compile(File source, File target, boolean minify) throws LessException {
		System.out.println(MessageFormat.format("reading {0}...", source.getAbsolutePath()));

		long start = System.currentTimeMillis();
		String less = FileUtils.readContent(source, "\n");
		
		StringBuilder aggregate = new StringBuilder();
		for (String line : less.split("\n")) {
			if (line.startsWith("@import")) {
				Pattern p = Pattern.compile("(\")(.+)(\")");
				Matcher m = p.matcher(line);
				if (m.find()) {
					String importLess = m.group(2);
					File importFile = new File(source.getParentFile(), importLess);
					String importContent = FileUtils.readContent(importFile, "\n");
					aggregate.append(importContent).append('\n');
				}

			} else {
				aggregate.append(line).append('\n');
			}
		}

		if (minify) {
			System.out.println(MessageFormat.format("compiling and minifying {0}...", target.getAbsolutePath()));
		} else {
			System.out.println(MessageFormat.format("compiling {0}...", target.getAbsolutePath()));
		}
		
		LessEngine engine = new LessEngine();

		// compile less into css and save
		String css = engine.compile(aggregate.toString(), minify);
		FileUtils.writeContent(target, css);
		System.out.println(MessageFormat.format("css generated in {0} msecs", System.currentTimeMillis() - start));
	}
}
