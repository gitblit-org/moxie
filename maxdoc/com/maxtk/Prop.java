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
package com.maxtk;

import java.util.HashSet;
import java.util.Set;

public class Prop {
	String token;
	String file;

	Set<String> keywords = new HashSet<String>();

	public void setToken(String token) {
		this.token = token;
	}

	public void setFile(String file) {
		this.file = file;
	}
	
	public void addKeyword(Keyword keyword) {
		keywords.add(keyword.value);
	}
	
	public boolean containsKeyword(String comment) {
		for (String keyword:keywords) {
			if (comment.contains(keyword)) {
				return true;
			}
		}
		return false;
	}
}