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

public class NoMarkdown {
	String startToken;
	
	String endToken;
	
	boolean escape;
	
	boolean prettify;
	
	boolean linenums;
	
	String lang;

	public void setStarttoken(String token) {
		this.startToken = token;
	}

	public void setEndtoken(String token) {
		this.endToken = token;
	}
	
	public void setEscape(boolean value) {
		this.escape = value;
	}
	
	public void setPrettify(boolean value) {
		this.prettify = true;
	}

	public void setLinenums(boolean value) {
		this.linenums = true;
	}

	public void setLang(String value) {
		this.lang = value;
	}

}