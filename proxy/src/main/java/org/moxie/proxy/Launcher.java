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
package org.moxie.proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.routing.VirtualHost;
import org.restlet.util.Series;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Launcher class that starts a Restlet server.
 *  
 * @author James Moger
 *
 */
public class Launcher {

	public static void main(String[] args) throws Exception {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (Exception e) {
			e.printStackTrace();
			jc.usage();
			System.exit(-1);
		}
		
		ProxyConfig config = new ProxyConfig();
				
		// parse config file, allow override
		if (params.moxieConfig.exists()) {
			config.parse(params.moxieConfig);
		} else {
			// default to user directory
			config.setUserDefaults();			
		}

		// override defaults from command-line
		if (params.httpPort != null) {
			config.setHttpPort(params.httpPort);
		}
		if (params.httpsPort != null) {
			config.setHttpsPort(params.httpsPort);
		}
		if (params.proxyPort != null) {
			config.setProxyPort(params.proxyPort);
		}
		if (params.shutdownPort != null) {
			config.setShutdownPort(params.shutdownPort);
		}
		if (params.accesslog != null) {
			config.setAccessLog(params.accesslog);
		}
		if (params.moxieRoot != null) {
			config.setMoxieRoot(params.moxieRoot);
		}
		if (params.storePassword != null) {
			config.setStorePassword(params.storePassword);
		}

		// start or stop server
		if (params.stop) {
			stop(config);
		} else {
			start(config);
		}
	}
	
	/**
	 * Stop Moxie Proxy.
	 */
	static void stop(ProxyConfig config) {
		try {
			Socket s = new Socket(InetAddress.getByName("127.0.0.1"), config.getShutdownPort());
			OutputStream out = s.getOutputStream();
			System.out.println("Sending shutdown request to " + Constants.getName());
			out.write("\r\n".getBytes());
			out.flush();
			s.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start Moxie Proxy
	 * @param config
	 */
	static void start(ProxyConfig config) throws Exception {
		Engine.setRestletLogLevel(Level.INFO);

		Component c = new Component();
		
		// turn off Restlet url access logging
		c.getLogService().setEnabled(config.getAccessLog());

		// create a Restlet server
		if (config.getBindAddresses().size() == 0) {
			// start server on all available addresses
			if (config.getHttpPort() > 0) {
				c.getServers().add(Protocol.HTTP, config.getHttpPort());
			}
			if (config.getHttpsPort() > 0) {
				Server server = c.getServers().add(Protocol.HTTPS, config.getHttpsPort());
				configureHttps(server, config);
			}
		} else {
			// start server on specific address(es)
			for (String address : config.getBindAddresses()) {
				if (config.getHttpPort() > 0) {
					c.getServers().add(Protocol.HTTP, address, config.getHttpPort());
				}
				if (config.getHttpsPort() > 0) {
					Server server = c.getServers().add(Protocol.HTTPS, address, config.getHttpsPort());
					configureHttps(server, config);
				}
			}
		}

		// add client classpath protocol to enable resource loading from the jar
		c.getClients().add(Protocol.CLAP);

		// add client file protocol to enable serving artifacts from filesystem
		c.getClients().add(Protocol.FILE);

		// override the default error pages
		c.setStatusService(new ErrorStatusService(c.getContext()));

		// get the default virtual host
		VirtualHost host = c.getDefaultHost();		

		MoxieProxy app = new MoxieProxy(config);

		// Guard Moxie Proxy with BASIC authentication.
		Authenticator guard = new Authenticator(app);
		host.attachDefault(guard);
		guard.setNext(app);
		
		// start the shutdown monitor
		if (config.getShutdownPort() > 0) {
			Thread shutdownMonitor = new ShutdownMonitorThread(c, app, config);
			shutdownMonitor.start();
		}

		// start the Restlet http/https server
		c.start();
	}
	
	/**
	 * Configure the SSL keystore for an https server.
	 * 
	 * @param server
	 */
	static void configureHttps(Server server, ProxyConfig config) {
		Series<org.restlet.data.Parameter> parameters = server.getContext().getParameters();
		parameters.add("keystorePath", "keystore");
		parameters.add("keystorePassword", config.getKeystorePassword());
	}

	/**
	 * JCommander Parameters class.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--httpPort" }, description = "port for serving web interface", required = false)
		public Integer httpPort;

		@Parameter(names = { "--httpsPort" }, description = "secure port for serving web interface", required = false)
		public Integer httpsPort;

		@Parameter(names = { "--proxyPort" }, description = "port for processing proxy requests", required = false)
		public Integer proxyPort;

		@Parameter(names = { "--shutdownPort" }, description = "port for shutdown monitor to listen on", required = false)
		public Integer shutdownPort;

		@Parameter(names = { "--accesslog" }, description = "log all url requests", required = false)
		public Boolean accesslog;

		@Parameter(names = { "--root" }, description = "folder for Moxie metadata and artifacts", required = false)
		public File moxieRoot;

		@Parameter(names = { "--config" }, description = "config file for Moxie Proxy", required = false)
		public File moxieConfig = new File("proxy.moxie");

		@Parameter(names = { "--storePassword" }, description = "password for JKS keystore", required = false)
		public String storePassword;

		@Parameter(names = { "--stop" }, description = "stops the Moxie Proxy server", required = false)
		public boolean stop;

	}
	
	/**
	 * The ShutdownMonitorThread opens a socket on a specified port and waits
	 * for an incoming connection. When that connection is accepted a shutdown
	 * message is issued to the running Moxie Proxy server.
	 * 
	 * @author James Moger
	 * 
	 */
	private static class ShutdownMonitorThread extends Thread {

		private final ServerSocket socket;

		private final Component c;
		
		private final MoxieProxy app;

		private final Logger logger = Logger.getLogger(ShutdownMonitorThread.class.getSimpleName());

		public ShutdownMonitorThread(Component c, MoxieProxy app, ProxyConfig config) {
			this.c = c;
			this.app = app;
			setDaemon(true);
			setName("internal [SHUTDOWN] server");
			ServerSocket skt = null;
			try {
				skt = new ServerSocket(config.getShutdownPort(), 1, InetAddress.getByName("127.0.0.1"));
			} catch (Exception e) {
				logger.log(Level.WARNING, "Could not open shutdown monitor on port " + config.getShutdownPort(), e);
			}
			socket = skt;
		}

		@Override
		public void run() {
			logger.info(MessageFormat.format(Constants.MESSAGE_STARTUP,  "SHUTDOWN", socket.getLocalPort()));
			Socket accept;
			try {
				accept = socket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(accept.getInputStream()));
				reader.readLine();
				c.stop();
				logger.info(MessageFormat.format(Constants.MESSAGE_SHUTDOWN,  "PROXY"));
				accept.close();
				socket.close();
				logger.info(MessageFormat.format(Constants.MESSAGE_SHUTDOWN,  "SHUTDOWN"));
			} catch (Exception e) {
				logger.log(Level.WARNING, "Failed to shutdown " + Constants.getName(), e);
			}
		}
	}
}
