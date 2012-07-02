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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;

import org.moxie.utils.StringUtils;

/**
 * Represents a proxy server definition.
 * 
 * @author James Moger
 * 
 */
public class Proxy {

	public String id;
	public boolean active;
	public String protocol;
	public String host;
	public int port;
	public String username;
	public String password;
	public List<String> proxyHosts = Collections.emptyList();
	public List<String> nonProxyHosts = Collections.emptyList();

	public boolean matches(String url) {
		if (url.startsWith(protocol)) {
			String host = StringUtils.getHost(url);

			if (proxyHosts.size() > 0) {
				for (String proxyHost : proxyHosts) {
					if (host.equalsIgnoreCase(proxyHost)
							|| host.endsWith(proxyHost)) {
						// proxy this host!
						return true;
					}
				}
				return false;
			}

			for (String nonProxyHost : nonProxyHosts) {
				if (host.equalsIgnoreCase(nonProxyHost)
						|| host.endsWith(nonProxyHost)) {
					// do not proxy this host!
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public SocketAddress getSocketAddress() {
		return new InetSocketAddress(host, port);
	}
}
