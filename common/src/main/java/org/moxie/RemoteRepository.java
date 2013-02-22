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

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.moxie.utils.StringUtils;

public class RemoteRepository implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String id;
	public final String url;
	public final PurgePolicy purgePolicy;
	public final Set<String> affinity;

	public RemoteRepository(String id, String url) {
		this.id = id;
		this.url = url;
		this.purgePolicy = new PurgePolicy();
		affinity = new LinkedHashSet<String>();
	}

	public String getHost() {
		return StringUtils.getHost(url);
	}
	
	@Override
	public int hashCode() {
		return 11 + url.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof RemoteRepository) {
			return o.hashCode() == hashCode();
		}
		return false;
	}
}
