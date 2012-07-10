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
package org.moxie.proxy;

public class Constants {

	public static String MESSAGE_STARTUP = "Starting the internal [{0}] server on port {1,number,0}";

	public static String MESSAGE_SHUTDOWN = "Stopping the internal [{0}] server";

	public static String getName() {
		return "Moxie Proxy";
	}
	
	public static String getUrl() {
		return "http://gitblit.github.com/moxie/";
	}
	
	public static String getVersion() {
		String v = Constants.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "DEVELOPMENT";
		}
		return "v" + v;
	}
}
