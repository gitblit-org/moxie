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
package org.moxie.console;

import static org.moxie.console.Ansi.ansi;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;

import org.moxie.Constants;
import org.moxie.Dependency;
import org.moxie.License;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.SourceFolder;
import org.moxie.console.Ansi.Color;
import org.moxie.utils.StringUtils;


public class Console {

	protected boolean debug;

	private final PrintStream out;
	private final PrintStream err;
	
	private final Object [] emptyArray = new Object[0];
	
	public Console() {
		this(false);
	}
	
	public Console(boolean useColor) {
		out = new PrintStream(wrapOutputStream(System.out, useColor));
		err = new PrintStream(wrapOutputStream(System.err, useColor));
	}
	
	private static OutputStream wrapOutputStream(final OutputStream stream, boolean useColor) {
		if (useColor) {
			// pass-through ANSI sequences
			return new FilterOutputStream(stream) {
				@Override
				public void close() throws IOException {
					write(AnsiOutputStream.REST_CODE);
					flush();
					super.close();
				}
			};
		}
		// strip ANSI sequences (no color)
		return new AnsiOutputStream(stream);
	}
	
	public void setDebug(boolean value) {
		debug = value;
	}
	
	public void header() {		
		out.println(ansi().fg(Color.CYAN).a(Constants.HDR).reset());
	}
	
	public void subheader() {		
		out.println(ansi().fg(Color.CYAN).a(Constants.SUB).reset());
	}

	public void separator() {
		out.println(ansi().fg(Color.RED).a(Constants.SEP).reset());
	}
	
	public void title(String name) {
		title(name, null);
	}
	
	public void title(Class<?> clazz) {
		title(clazz, null);
	}
	
	public void title(Class<?> clazz, String paranthesis) {
		String name = clazz.getSimpleName();
		if (name.toLowerCase().startsWith("mx")) {
			title("mx:" + name.substring(2), paranthesis);
		} else {
			title(name, paranthesis);
		}
	}
	
	public void title(String name, String paranthesis) {
		header();
		if (StringUtils.isEmpty(paranthesis)) {
			out.println(name);
		} else {
			out.append(name).append("  (");
			out.print(ansi().fg(Color.MAGENTA).a(paranthesis).reset());
			out.println(")");
		}
		subheader();
	}
	
	public void sourceFolder(SourceFolder sourceFolder) {
		out.append(Constants.INDENT);
		out.print(ansi().fg(Color.GREEN).a(sourceFolder.name).reset());
		out.print(" (");
		out.print(ansi().bold().fg(Color.MAGENTA).a(sourceFolder.scope).boldOff().reset());
		out.println(")");
	}
	
	public void scope(Scope scope, int count) {
		out.print(ansi().bold().fg(Color.MAGENTA).a(scope).boldOff().reset());
		out.println(" dependencies" + (count > 0 ? (" (" + count + ")"):""));
	}

	public void dependency(Dependency dependency) {
		out.append(Constants.INDENT);
		out.println(ansi().fg(Color.GREEN).a(dependency.getDetailedCoordinates()).reset());
	}
	
	public void license(Dependency dependency, Pom pom) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < (dependency.ring + 1); i++) {
			sb.append("  ");
		}
		String dd = sb.toString();
		String md = dd + Constants.INDENT + " ";
		out.append(dd).append(ansi().fg(Color.YELLOW).a(dependency.ring + ":").toString()).println(ansi().fg(Color.GREEN).a(dependency.getDetailedCoordinates()).reset());
		if (pom.getLicenses().size() == 0) {
			out.append(md).println(ansi().bold().fg(Color.YELLOW).a("unknown!").boldOff().reset());
		}
		for (License license : pom.getLicenses()) {
			out.append(md).println(ansi().fg(Color.WHITE).a(license.name).reset());
			if (!StringUtils.isEmpty(license.url)) {
				out.append(md).println(ansi().fg(Color.CYAN).a(license.url).reset());
			}
		}
	}
	
	public void download(String url) {
		out.append(Constants.INDENT);
		out.append("<= ");
		out.println(ansi().fg(Color.CYAN).a(url).reset());
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

	public void debug(String message) {
		debug(0, message, emptyArray);
	}

	public void debug(String message, Object... args) {
		debug(0, message, args);
	}
	
	public void debug(int indent, String message, Object... args) {
		if (!debug) {
			return;
		}
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(ansi().bold().fg(Color.BLUE).a(MessageFormat.format(message, args)).boldOff().reset());		
	}

	public void key(String key, String value) {
		out.append(Constants.INDENT);
		out.print(ansi().fg(Color.DEFAULT).a(key).reset());
		if (key.trim().length() > 0) {
			out.print(": ");
		} else {
			out.print("  ");
		}
		out.println(ansi().fg(Color.YELLOW).a(value).reset());
	}
	
	public final void notice(String message) {
		notice(0, message, emptyArray);
	}

	public final void notice(String message, Object... args) {
		notice(0, message, args);
	}

	public void notice(int indent, String message, Object... args) {
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(ansi().fg(Color.YELLOW).a(MessageFormat.format(message, args)).reset());
	}
	
	public final void warn(Throwable t) {
		warn(t, null, emptyArray);
	}
	
	public final void warn(String message) {
		warn(0, message, emptyArray);
	}
	
	public final void warn(String message, Object... args) {
		warn(0, message, args);
	}
	
	public void warn(int indent, String message, Object... args) {
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(ansi().bold().fg(Color.YELLOW).a(MessageFormat.format(message, args)).boldOff().reset());
	}

	public void warn(Throwable t, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			out.println(ansi().bold().fg(Color.YELLOW).a(MessageFormat.format(message, args)).boldOff().reset());
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
			err.println(ansi().bold().fg(Color.RED).a(MessageFormat.format(message, args)).boldOff().reset());
		}
		if (t != null) {
			t.printStackTrace(err);
		}
	}
}
