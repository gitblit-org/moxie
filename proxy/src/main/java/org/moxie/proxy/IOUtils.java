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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Various IO utils (copy streams, create directories)
 * 
 * @author digulla
 * 
 */
public class IOUtils {

	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024 * 100];
		int len;

		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}

		out.flush();
	}

	public static void mkdirs(File f) {
		if (f.exists()) {
			if (!f.isDirectory())
				throw new RuntimeException("Expected directory " + f.getAbsolutePath() + " but found a file.");

			return;
		}

		if (!f.mkdirs())
			throw new RuntimeException("Cannot create directory " + f.getAbsolutePath());
	}

}
