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
package com.maxtk;

import java.io.PrintStream;
import java.text.MessageFormat;

import com.maxtk.Dependency.Scope;
import com.maxtk.utils.StringUtils;

public class Console {

	private final PrintStream out;
	private final PrintStream err;
	
	private final Object [] emptyArray = new Object[0];

	public Console() {
		out = System.out;
		err = System.err;
	}
	
	public void header() {
		out.println(Constants.HDR);
	}

	public void separator() {
		out.println(Constants.SEP);
	}
	
	public void sourceFolder(SourceFolder sourceFolder) {
		out.append(Constants.INDENT);
		out.print(sourceFolder.folder.getName());
		out.print(" (");
		out.print(sourceFolder.scope);
		out.println(")");
	}

	public void scope(Scope scope) {
		out.print(scope);
		out.println(" dependencies");
	}

	public void dependency(Dependency dependency) {
		out.append(Constants.INDENT);
		out.println(dependency.getCoordinates());
	}
	
	public void download(String url) {
		out.append(Constants.INDENT);
		out.append("<= ");
		out.println(url);
	}
	
	public void log() {
		out.println();
	}

	public void log(String message) {
		log(0, message, emptyArray);
	}
	
	public void log(String message, Object... args) {
		log(0, message, args);
	}
	
	public void log(int indent, String message) {
		log(indent, message, emptyArray);
	}
	
	public void log(int indent, String message, Object... args) {
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(MessageFormat.format(message, args));		
	}
	
	public void key(String key, String value) {
		out.append(Constants.INDENT);
		out.print(key);
		if (key.trim().length() > 0) {
			out.print(": ");
		} else {
			out.print("  ");
		}
		out.println(value);
	}
	
	public final void warn(Throwable t) {
		warn(t, null, emptyArray);
	}
	
	public final void warn(String message) {
		warn(null, message, emptyArray);
	}
	
	public final void warn(String message, Object... args) {
		warn(null, message, args);
	}

	public void warn(Throwable t, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			out.println(MessageFormat.format(message, args));
		}
		if (t != null) {
			t.printStackTrace(out);
		}
	}

	public final void error(Throwable t) {
		error(t, null, emptyArray);
	}

	public final void error(String message) {
		error(null, message, emptyArray);
	}

	public void error(String message, Object... args) {
		error(null, message, args);
	}

	public final void error(Throwable t, String message) {
		error(t, message, emptyArray);
	}

	public void error(Throwable t, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			err.println(MessageFormat.format(message, args));
		}
		if (t != null) {
			t.printStackTrace(err);
		}
	}
}
