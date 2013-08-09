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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;

import org.moxie.utils.StringUtils;

public class Person implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public String id;
	public String name;
	public String email;
	public String url;
	public String organization;
	public String organizationUrl;
	public List<String> roles;
	
	@Override
	public String toString() {
		return MessageFormat.format("{0} <{1}>", name, email == null ? "" : email);
	}
	
	public String toXML(String nodename) {
		StringBuilder node = new StringBuilder();
		node.append(MessageFormat.format("<{0}>\n", nodename));

		node.append(StringUtils.toXML("id", id));
		node.append(StringUtils.toXML("name", name));
		node.append(StringUtils.toXML("email", email));
		node.append(StringUtils.toXML("url", url));
		node.append(StringUtils.toXML("organization", organization));
		node.append(StringUtils.toXML("organizationUrl", organizationUrl));
		
		if (roles != null && roles.size() > 0) {
			StringBuilder rolesNode = new StringBuilder();
			rolesNode.append("<roles>\n");
			for (String role : roles) {
				rolesNode.append(StringUtils.toXML("role", role));
			}
			rolesNode.append("</roles>\n");
			node.append(StringUtils.insertHalfTab(rolesNode.toString()));
		}
		
		node.append(MessageFormat.format("</{0}>\n", nodename));
		return node.toString();
	}
}
