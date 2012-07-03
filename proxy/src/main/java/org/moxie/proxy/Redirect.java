/*
 * Copyright 2002-2005 The Apache Software Foundation.
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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author digulla
 */
public class Redirect {
	final String from;
	final String to;

	public Redirect(String from, String to) {
		this.from = fix(from);
		this.to = fix(to);
	}

	private String fix(String s) {
		s = s.trim();
		if (!s.endsWith("/"))
			s += "/";
		return s;
	}
	
	public String getFrom() {
		return from;
	}
	
	public String getTo() {
		return to;
	}

	public URL getRedirectURL(String s) {
		if (s.startsWith(from)) {
			s = s.substring(from.length());
			s = to + s;
			try {
				return new URL(s);
			} catch (MalformedURLException e) {
				throw new RuntimeException("Couldn't create URL from " + s, e);
			}
		}

		return null;
	}
}