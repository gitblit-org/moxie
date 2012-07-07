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

import org.moxie.proxy.AtomFeed;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class AtomResource extends BaseResource {

	@Override
	protected String getBasePath() {
		return "atom";
	}

	@Override
	protected String getBasePathName() {
		return getTranslation().getString("mp.recentArtifacts");
	}
	
	@Get
	public Representation getFeed() {
		String repository = getRequestAttribute("repository");
		int count = getQueryValue("count", getProxyConfig().getAtomCount());
		AtomFeed generator = new AtomFeed(getApplication(), getRootRef().toString());
		return generator.getFeed(repository, count);
	}
}
