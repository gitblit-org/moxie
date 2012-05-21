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
package com.maxtk.opt;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO;
import static org.fusesource.jansi.internal.CLibrary.isatty;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;

import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiOutputStream;

import com.maxtk.Console;
import com.maxtk.Constants;
import com.maxtk.Dependency;
import com.maxtk.Scope;
import com.maxtk.SourceFolder;
import com.maxtk.utils.StringUtils;

public class JansiConsole extends Console {

	private final PrintStream out;
	private final PrintStream err;
	
	public JansiConsole() {
		out = new PrintStream(wrapOutputStream(System.out));
		err = new PrintStream(wrapOutputStream(System.err));
	}
	
	private static OutputStream wrapOutputStream(final OutputStream stream) {
		String os = System.getProperty("os.name");
		if( os.startsWith("Windows") ) {
			// return the stream and let Windows deal with the ANSI sequences
			// recommended to use ANSICON https://github.com/adoxa/ansicon
			// or in Eclipse http://www.mihai-nita.net/eclipse
			return stream;
		}
		
		// We must be on some unix variant..
		try {
			// If we can detect that stdout is not a tty.. then setup
			// to strip the ANSI sequences..
			int rc = isatty(STDOUT_FILENO);
			if( rc==0 ) {
				return new AnsiOutputStream(stream);
			}
			
        // These erros happen if the JNI lib is not available for your platform.
        } catch (NoClassDefFoundError ignore) {
		} catch (UnsatisfiedLinkError ignore) {
		}

		// By default we assume your Unix tty can handle ANSI codes.
		// Just wrap it up so that when we get closed, we reset the 
		// attributes.
		return new FilterOutputStream(stream) {
		    @Override
		    public void close() throws IOException {
		        write(AnsiOutputStream.REST_CODE);
		        flush();
		        super.close();
		    }
		};
	}
	
	@Override
	public void header() {		
		out.println(ansi().fg(Color.CYAN).a(Constants.HDR).reset());
	}
	
	@Override
	public void subheader() {		
		out.println(ansi().fg(Color.CYAN).a(Constants.SUB).reset());
	}

	@Override
	public void sourceFolder(SourceFolder sourceFolder) {
		out.append(Constants.INDENT);
		out.print(ansi().fg(Color.GREEN).a(sourceFolder.folder.getName()).reset());
		out.print(" (");
		out.print(ansi().fg(Color.MAGENTA).a(sourceFolder.scope).reset());
		out.println(")");
	}

	@Override
	public void separator() {
		out.println(ansi().fg(Color.RED).a(Constants.SEP).reset());
	}
	
	@Override
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

	@Override
	public void scope(Scope scope) {
		out.print(ansi().fg(Color.MAGENTA).a(scope).reset());
		out.println(" dependencies");
	}

	@Override
	public void dependency(Dependency dependency) {
		out.append(Constants.INDENT);
		out.println(ansi().fg(Color.GREEN).a(dependency.getCoordinates()).reset());
	}

	@Override
	public void download(String url) {
		out.append(Constants.INDENT);
		out.append("<= ");
		out.println(ansi().fg(Color.CYAN).a(url).reset());
	}

	@Override
	public void debug(int indent, String message, Object... args) {
		if (!debug) {
			return;
		}
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(ansi().fg(Color.BLUE).a(MessageFormat.format(message, args)).reset());		
	}
	
	@Override
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

	@Override
	public void warn(int indent, String message, Object... args) {
		for (int i = 0; i < indent; i++) {
			out.append(Constants.INDENT);
		}
		out.println(ansi().fg(Color.YELLOW).a(MessageFormat.format(message, args)).reset());
	}

	@Override
	public void warn(Throwable t, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			out.println(ansi().fg(Color.YELLOW).a(MessageFormat.format(message, args)).reset());
		}
		if (t != null) {
			t.printStackTrace(out);
		}
	}
	
	@Override
	public void error(Throwable t, String message, Object... args) {
		if (!StringUtils.isEmpty(message)) {
			err.println(ansi().fg(Color.RED).a(MessageFormat.format(message, args)).reset());
		}
		if (t != null) {
			t.printStackTrace(err);
		}
	}
}
