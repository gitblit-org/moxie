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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * Represents a proxy server definition.
 * 
 * @author James Moger
 * 
 */
public class Proxy {

	String id;
	boolean active;
	String protocol;
	String host;
	int port;
	String username;
	String password;
	List<String> nonProxyHosts;

	public boolean matches(String url) {
		if (url.startsWith(protocol)) {
			for (String nonProxyHost : nonProxyHosts) {
				if (nonProxyHost.equals(url)) {
					// TODO improve this
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
