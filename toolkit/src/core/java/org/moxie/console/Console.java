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

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.moxie.Dependency;
import org.moxie.License;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.SourceDirectory;
import org.moxie.Toolkit.Key;
import org.moxie.console.Ansi.Color;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


public class Console {

	public static final String HDR = "=========================================================";

	public static final String SUB = "---------------------------------------------------------";
	
	public static final String SEP = "---------------------------------------------------------";

	public static final String INDENT = "   ";

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
		out.println(ansi().fg(Color.CYAN).a(HDR).reset());
	}
	
	public void subheader() {		
		out.println(ansi().fg(Color.CYAN).a(SUB).reset());
	}

	public void separator() {
		out.println(ansi().fg(Color.RED).a(SEP).reset());
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
	
	public void sourceFolder(SourceDirectory sourceFolder) {
		out.append(INDENT);
		out.print(ansi().fg(Color.GREEN).a(sourceFolder.name).reset());
		out.print(" (");
		out.print(ansi().bold().fg(Color.MAGENTA).a(sourceFolder.scope).boldOff().reset());
		out.println(")");
	}
	
	public String scope(Scope scope, int count) {
		out.print(ansi().bold().fg(Color.MAGENTA).a(scope).boldOff().reset());
		out.println(" dependencies" + (count > 0 ? (" (" + count + ")"):""));
		return MessageFormat.format("{0} dependencies ({1,number,0})", scope.name(), count);
	}

	public void dependency(Dependency dependency) {
		out.append(INDENT);
		out.println(ansi().fg(Color.GREEN).a(dependency.getDetailedCoordinates()).reset());
	}
	
	public String dependencyReport(Dependency dependency, Pom pom, File artifact) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < (dependency.ring + 1); i++) {
			sb.append("  ");
		}
		String dd = sb.toString();
		String md = dd + INDENT + " ";
		StringBuilder rs = new StringBuilder();
		out.append(dd).append(ansi().fg(Color.YELLOW).a(dependency.ring + ":").toString()).println(ansi().fg(Color.GREEN).a(dependency.getDetailedCoordinates()).reset());
		rs.append(dd).append(dependency.ring).append(':').append(dependency.getDetailedCoordinates());
		if (artifact != null && artifact.exists()) {
			// display size and last modified
			String size = FileUtils.formatSize(artifact.length());
			String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(artifact.lastModified()));
			out.append(md).append(ansi().fg(Color.YELLOW).a(size).reset().toString()).println(MessageFormat.format("  last modified {0}", date));
			rs.append(md).append(MessageFormat.format("{0}  last modified {1}", size, date)).append('\n');
		}
		if (pom.getLicenses().size() == 0) {
			out.append(md).println(ansi().bold().fg(Color.YELLOW).a("unknown!").boldOff().reset());
			rs.append(md).append("unknown!\n");
		}
		for (License license : pom.getLicenses()) {
			out.append(md).println(ansi().fg(Color.WHITE).a(license.name).reset());
			rs.append(md).append(license.name).append('\n');
			if (!StringUtils.isEmpty(license.url)) {
				out.append(md).println(ansi().fg(Color.CYAN).a(license.url).reset());
				rs.append(md).append(license.url).append('\n');
			}
		}
		if (!dependency.tags.isEmpty()) {
			out.append(md).println(ansi().fg(Color.YELLOW).a(dependency.tags).reset());
			rs.append(md).append(dependency.tags).append('\n');
		}
		return rs.toString();
	}
	
	public void download(String url) {
		out.append(INDENT);
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
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				for (int i = 0; i < indent; i++) {
					out.append(INDENT);
				}
				out.println(MessageFormat.format(line, args));
			}
		}
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
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				for (int i = 0; i < indent; i++) {
					out.append(INDENT);
				}
				out.println(ansi().bold().fg(Color.BLUE).a(MessageFormat.format(line, args)).boldOff().reset());
			}
		}
	}

	public void key(String key, String value) {
		out.append(INDENT);
		out.print(ansi().fg(Color.DEFAULT).a(key).reset());
		if (key.trim().length() > 0) {
			out.print(": ");
		} else {
			out.print("  ");
		}
		out.println(ansi().fg(Color.YELLOW).a(value).reset());
	}
	
	public void missingOriginRepository(String origin, Dependency dependency) {
		out.print(ansi().bold().fg(Color.YELLOW).a("WARNING: ").boldOff().reset());
		out.print("You should add ");
		out.print(ansi().fg(Color.CYAN).a(origin).reset());
		out.println(" to your");
		out.print("         ");
		out.print(ansi().bold().fg(Color.WHITE).a(Key.registeredRepositories).boldOff().reset());
		out.print(" setting for ");
		out.println(ansi().fg(Color.GREEN).a(dependency.getCoordinates()).reset());
	}
	
	public void artifactResolutionFailed(Dependency dependency) {
		out.print(ansi().bold().fg(Color.RED).a("ERROR: ").boldOff().reset());
		out.print("Failed to resolve ");
		out.print(ansi().fg(Color.GREEN).a(dependency.getCoordinates()).reset());
		out.println(" from the registered repositories!");
		out.print("       ");
		out.print("Please check the dependency coordinates, the ");
		out.print(ansi().bold().fg(Color.WHITE).a(Key.registeredRepositories).boldOff().reset());
		out.println(" setting,");
		out.print("       ");
		out.println("and your proxy server settings.");
	}
	
	public final void notice(String message) {
		notice(0, message, emptyArray);
	}

	public final void notice(String message, Object... args) {
		notice(0, message, args);
	}

	public void notice(int indent, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				for (int i = 0; i < indent; i++) {
					out.append(INDENT);
				}
				out.println(ansi().fg(Color.YELLOW).a(MessageFormat.format(line, args)).reset());
			}
		}
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
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				for (int i = 0; i < indent; i++) {
					out.append(INDENT);
				}
				out.println(ansi().bold().fg(Color.YELLOW).a(MessageFormat.format(line, args)).boldOff().reset());
			}
		}
	}

	public String warn(Throwable t, String message, Object... args) {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				String formatted = MessageFormat.format(line, args);
				sb.append(formatted).append('\n');
				out.println(ansi().bold().fg(Color.YELLOW).a(formatted).boldOff().reset());
			}
		}
		if (t != null) {
			t.printStackTrace(out);
		}
		if (sb.length() > 0) {
			// trim trailiing \n
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	public final void error(Throwable t) {
		error(t, null, emptyArray);
	}

	public final String error(String message) {
		return error(null, message, emptyArray);
	}

	public final String error(String message, Object... args) {
		return error(null, message, args);
	}

	public final String error(Throwable t, String message) {
		return error(t, message, emptyArray);
	}

	public String error(Throwable t, String message, Object... args) {
		StringBuilder sb = new StringBuilder();
		if (!StringUtils.isEmpty(message)) {
			String [] lines = message.split("\n");
			for (String line : lines) {
				String formatted = MessageFormat.format(line, args);
				err.println(ansi().bold().fg(Color.RED).a(formatted).boldOff().reset());
				sb.append(formatted).append('\n');
			}
		}
		if (t != null) {
			t.printStackTrace(err);
		}		
		if (sb.length() > 0) {
			// trim trailiing \n
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}
}
