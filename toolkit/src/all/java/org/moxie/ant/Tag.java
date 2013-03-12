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
package org.moxie.ant;

import org.apache.tools.ant.Project;

public class Tag {

	protected String name;
	
	protected Message message;
	
	protected Project project;
	
	public void setProject(Project project) {
		this.project = project;		
	}
	
	public Message createMessage() {
		this.message = new Message();
		this.message.setProject(project);
		return message;
	}
	
	public String getMessage() {
		return message.getValue();
	}
	
	public void setName(String value) {
		this.name = value;
	}
	
	public String getName() {
		return project.replaceProperties(name);
	}
}
