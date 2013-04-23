/*
 * Copyright 2013 James Moger
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
package org.moxie;

public interface Logger {

	public void log();

	public void log(String message);

	public void log(String message, Object... args);

	public void log(int indent, String message);

	public void log(int indent, String message, Object... args);

	public void debug(String message);

	public void debug(String message, Object... args);

	public void debug(int indent, String message, Object... args);

	public void notice(String message);

	public void notice(String message, Object... args);

	public void notice(int indent, String message, Object... args);

	public void warn(Throwable t);

	public void warn(String message);

	public void warn(String message, Object... args);

	public void warn(int indent, String message, Object... args);

	public String warn(Throwable t, String message, Object... args);

	public void error(Throwable t);

	public String error(String message);

	public String error(String message, Object... args);

	public String error(Throwable t, String message);

	public String error(Throwable t, String message, Object... args);

}