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
package org.moxie.ant;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.zip.ZipOutputStream;
import org.moxie.MoxieException;
import org.moxie.console.Console;

public class JarUtils {

	public static void mergeMetaInfServices(Console console, File destFile) {
		// collate service entries
		Map<String, String> services = new TreeMap<String, String>();
		Map<String, StringBuilder> merged = new TreeMap<String, StringBuilder>();
		ZipInputStream is = null;
		try {
			console.debug(1, "collating META-INF/services");
			is = new ZipInputStream(new BufferedInputStream(new FileInputStream(destFile)));
			ZipEntry entry = null;
			while ((entry = is.getNextEntry()) != null) {
				String name= entry.getName();
				if (!entry.isDirectory() && name.toLowerCase().startsWith("meta-inf/services")) {						
					console.debug(2, name);
					byte [] buffer = new byte[(int) entry.getSize()];						
					int len = is.read(buffer);
					String content = new String(buffer, 0, len, "UTF-8");
					
					if (!services.containsKey(name)) {
						// first hit on this service definition
						services.put(name, content);
					} else {
						// duplicate service definition
						if (!merged.containsKey(name)) {
							// inject first-hit definition
							StringBuilder sb = new StringBuilder();
							sb.append(services.get(name));
							sb.append('\n');
							merged.put(name, sb);
						}
						// append duplicate to in-memory cache 
						merged.get(name).append(content).append('\n');	
					}
				}
				is.closeEntry();
			}
		} catch (Exception e) {
			console.error("Failed to merge service definitions!");
			throw new MoxieException(e);
		} finally {
			// close input file
			if (is != null) {
				try {
					is.close();
				} catch (IOException e1) {
				}
			}
		}
		
		// write merged service definitions
		if (merged.size() > 0) {
			console.log(1, "merging {0} META-INF/service definitions", merged.size());
			File mergeFile = new File(destFile.getParentFile(), destFile.getName() + ".merge");
			if (mergeFile.exists()) {
				mergeFile.delete();
			}
			boolean success = false;
			List<String> skip = new ArrayList<String>();
			is = null;
			ZipOutputStream os = null;
			try {
				is = new ZipInputStream(new BufferedInputStream(new FileInputStream(destFile)));
				os = new ZipOutputStream(mergeFile);
				ZipEntry entry = null;
				while ((entry = is.getNextEntry()) != null) {
					String name = entry.getName();
					if (skip.contains(name)) {
						// already wrote merged service definition
						continue;
					}
					os.putNextEntry(new org.apache.tools.zip.ZipEntry(name));						
					if (merged.containsKey(name)) {
						// write merged service definition
						String def = merged.get(name).toString();
						// strip blank lines
						def = def.replace("\r\n", "\n").replace("\n\n", "\n");
						os.write(def.getBytes("UTF-8"));
						skip.add(name);
					} else {
						// pass-through
						int len = 0;
						byte [] buffer = new byte[4096];
						while ((len = is.read(buffer, 0, buffer.length)) != -1) {
		                    os.write(buffer, 0, len);
		                }							
					}
				}
				success = true;
			} catch (Exception e) {
				console.error(e);
			} finally {
				// close input file
				if (is != null) {
					try {
						is.close();
					} catch (Exception e) {
					}
				}
				// close merged output file
				if (os != null) {
					try {
						os.flush();
						os.close();
					} catch (Exception e) {
					}
				}
				
				// successfully wrote new jar with merged META-INF/services
				if (success) {
					destFile.delete();
					mergeFile.renameTo(destFile);
				}
			}
		}
	}
}
