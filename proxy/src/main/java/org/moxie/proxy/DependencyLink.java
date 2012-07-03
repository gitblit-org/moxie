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

import java.io.Serializable;

public class DependencyLink implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public final String coordinates;
	public final String path;
	
	public DependencyLink(String coordinates, String path) {
		this.coordinates = coordinates;
		this.path = path;
	}
	
	public String getCoordinates() {
		return coordinates;
	}
	
	public String getPath() {
		return path;
	}
}
