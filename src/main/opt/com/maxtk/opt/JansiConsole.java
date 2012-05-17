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

import java.io.PrintStream;
import java.text.MessageFormat;

import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;

import com.maxtk.Console;
import com.maxtk.Constants;
import com.maxtk.Dependency;
import com.maxtk.SourceFolder;
import com.maxtk.Scope;
import com.maxtk.utils.StringUtils;

public class JansiConsole extends Console {

	private final PrintStream out;
	private final PrintStream err;
	
	public JansiConsole() {
		AnsiConsole.systemInstall();
		out = AnsiConsole.out;
		err = AnsiConsole.err;
	}
	
	@Override
	public void header() {		
		out.println(ansi().fg(Color.CYAN).a(Constants.HDR).reset());
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
