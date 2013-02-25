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

public class Substitute {
	public String token;
	public String value;
	public boolean isTemplate;

	public void setToken(String token) {
		this.token = token;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
	public void set(String token, String value) {
		this.token = token;
		this.value = value;
	}
	
	public boolean isProperty() {
		return token.startsWith("${") && token.endsWith("}");
	}
	
	public String getPropertyName() {
		return token.substring(2, token.length() - 1);
	}
}