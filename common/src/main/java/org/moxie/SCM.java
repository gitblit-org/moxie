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
package org.moxie;

import org.moxie.utils.StringUtils;

public class SCM {
	public String connection;
	public String developerConnection;
	public String url;
	public String tag;
	
	public boolean isEmpty() {
		return StringUtils.isEmpty(connection) && StringUtils.isEmpty(developerConnection)
				&& StringUtils.isEmpty(url);
	}
	
	public String toXML() {
		StringBuilder node = new StringBuilder();
		node.append("<scm>\n");
		node.append(StringUtils.toXML("connection", connection));
		node.append(StringUtils.toXML("developerConnection", developerConnection));
		node.append(StringUtils.toXML("url", url));
		node.append(StringUtils.toXML("tag", tag));
		node.append("</scm>\n");
		return node.toString();
	}
}
