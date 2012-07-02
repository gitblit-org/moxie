/*
 * Copyright 2002-2005 The Apache Software Foundation.
 * Copyright 2012 James Moger
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moxie.proxy.connection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.moxie.proxy.ProxyConfig;

/**
 * Wait for connections from somewhere and pass them on to
 * <code>RequestHandler</code> for processing.
 * 
 * @author digulla
 * 
 */
public class ProxyConnectionServer {
	public static final Logger log = Logger.getLogger(ProxyConnectionServer.class.getSimpleName());

	private final ProxyConfig config;
	private int port;
	private ServerSocket socket;

	public ProxyConnectionServer(ProxyConfig config) {
		this.config = config;
		port = config.getProxyPort();
		try {
			socket = new ServerSocket(port);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean run = true;

	public void terminateAll() {
		// TODO Implement a way to gracefully stop the proxy
		run = false;
	}

	public void handleRequests() {
		while (run) {
			Socket clientSocket;

			try {
				clientSocket = socket.accept();
			} catch (IOException e) {
				log.log(Level.SEVERE, "Error acception connection from client", e);
				continue;
			}

			config.reload();
			Thread t = new ProxyRequestHandler(config, clientSocket);
			t.start();
		}

		try {
			socket.close();
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error closing server socket", e);
		}
	}
}
