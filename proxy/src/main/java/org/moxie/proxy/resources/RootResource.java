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
package org.moxie.proxy.resources;

import java.util.HashMap;
import java.util.Map;

import org.moxie.proxy.Constants;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class RootResource extends BaseResource {

	@Override
	protected String getBasePath() {
		return "";
	}

	@Override
	protected String getBasePathName() {
		return "";
	}

	@Get
	public Representation toText() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("tagline", getTranslation().getString("mp.tagline"));
		map.put("content", "");
		return toHtml(map, "root.html");
	}
}
