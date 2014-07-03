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
	public final boolean allowSnapshots;
	public final Set<String> affinity;
	public int connectTimeout;
	public int readTimeout;
	public String username;
	public String password;

	public RemoteRepository(String id, String url, boolean allowSnapshots) {
		this.id = id;
		this.url = url;
		this.allowSnapshots = allowSnapshots;
		this.purgePolicy = new PurgePolicy();
		affinity = new LinkedHashSet<String>();
		// timeout defaults of Maven 3.0.4
		this.connectTimeout = 20;
		this.readTimeout = 12800;
	}

	public String getHost() {
		return StringUtils.getHost(url);
	}

	public String toXML() {
		StringBuilder sb = new StringBuilder();
		sb.append("<repository>\n");
		sb.append(StringUtils.toXML("id", id));
		sb.append(StringUtils.toXML("url", url));
		if (allowSnapshots) {
			sb.append("<snapshots>\n");
			sb.append(StringUtils.insertHalfTab(StringUtils.toXML("enabled", allowSnapshots)));
			sb.append("</snapshots>\n");
		}
		sb.append("</repository>\n");
		return sb.toString();
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
