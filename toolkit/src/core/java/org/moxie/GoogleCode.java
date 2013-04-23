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



public class GoogleCode extends Repository {
	
	public final static String ID = "googlecode";

	public GoogleCode() {
		super("GoogleCode", "http://code.google.com", "files/[version].[ext]", null, null);
		affinity.add("<" + ID + ">:");
	}
	
	public String getArtifactUrl() {
		return "https://[artifactId].googlecode.com/" + artifactPattern;
	}

	@Override
	protected boolean calculateSHA1() {
		return false;
	}
	
	@Override
	protected boolean isMavenSource() {
		return false;
	}
}
