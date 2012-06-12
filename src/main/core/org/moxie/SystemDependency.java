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

public class SystemDependency extends Dependency {

	private static final long serialVersionUID = 1L;
	
	public final String path;
	
	public SystemDependency(String path) {
		this.path = path;
		this.groupId = "system";
	}
	
	@Override
	public boolean isMavenObject() {
		return false;
	}

	@Override
	public String getMediationId() {
		return path;
	}

	@Override
	public String getManagementId() {
		return path;
	}

	@Override
	public String getDetailedCoordinates() {
		return path;
	}
	
	@Override
	public String toString() {
		return path;
	}
}
