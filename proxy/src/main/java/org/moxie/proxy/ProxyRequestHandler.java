/*
 * Copyright 2002-2005 The Apache Software Foundation.
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
package org.moxie.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handle a connection from a maven.
 * 
 * @author digulla
 * 
 */
public class ProxyRequestHandler extends Thread {
	public static final Logger log = Logger.getLogger(ProxyRequestHandler.class.getSimpleName());

	private Config config;
	private Socket clientSocket;

	public ProxyRequestHandler(Config config, Socket clientSocket) {
		this.config = config;
		this.clientSocket = clientSocket;
	}

	@Override
	public void run() {
		if (clientSocket == null)
			throw new RuntimeException("Connection is already closed");

		try {
			log.fine("Got connection from " + clientSocket.getInetAddress());

			String line;
			boolean keepAlive = false;
			do {
				String downloadURL = null;
				StringBuffer fullRequest = new StringBuffer(1024);
				while ((line = readLine()) != null) {
					if (line.length() == 0)
						break;

					// log.debug ("Got: "+line);
					fullRequest.append(line);
					fullRequest.append('\n');

					if ("proxy-connection: keep-alive".equals(line.toLowerCase()))
						keepAlive = true;

					if (line.startsWith("GET ")) {
						int pos = line.lastIndexOf(' ');
						line = line.substring(4, pos);
						downloadURL = line;
					}
				}

				if (downloadURL == null) {
					if (line == null)
						break;

					log.severe("Found no URL to download in request:\n" + fullRequest.toString());
				} else {
					log.info("Got request for " + downloadURL);
					serveURL(downloadURL);
				}
			} while (line != null && keepAlive);

			log.fine("Terminating connection with " + clientSocket.getInetAddress());
		} catch (Exception e) {
			log.log(Level.SEVERE, "Conversation with client aborted", e);
		} finally {
			close();
		}
	}

	public void close() {
		try {
			if (out != null)
				out.close();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception while closing the outputstream", e);
		}
		out = null;

		try {
			if (in != null)
				in.close();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception while closing the inputstream", e);
		}
		in = null;

		try {
			if (clientSocket != null)
				clientSocket.close();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception while closing the socket", e);
		}
		clientSocket = null;
	}

	private void serveURL(String downloadURL) throws IOException {
		URL url = new URL(downloadURL);
		url = config.getMirror(url);

		if (!"http".equals(url.getProtocol()))
			throw new IOException("Can only handle HTTP requests, got " + downloadURL);

		File f = getCacheFile(url);

		if (!f.exists()) {
			ProxyDownload d = new ProxyDownload(config, url, f);
			try {
				d.download();
			} catch (DownloadFailed e) {
				log.severe(e.getMessage());

				println(e.getStatusLine());
				println();
				getOut().flush();
				return;
			}
		} else {
			log.fine("Serving from local cache " + f.getAbsolutePath());
		}

		println("HTTP/1.1 200 OK");
		print("Date: ");
		Date d = new Date(f.lastModified());
		println(INTERNET_FORMAT.format(d));
		print("Content-length: ");
		println(String.valueOf(f.length()));
		print("Content-type: ");
		String ext = downloadURL.substring(downloadURL.lastIndexOf('.') + 1).toLowerCase();
		String type = CONTENT_TYPES.get(ext);
		if (type == null) {
			log.warning("Unknown extension " + ext + ". Using content type text/plain.");
			type = "text/plain";
		}
		println(type);
		println();
		InputStream data = new BufferedInputStream(new FileInputStream(f));
		IOUtils.copy(data, out);
		data.close();
	}

	public File getCacheFile(URL url) {
		File dir = config.getCacheFolder();
		return getCacheFile(url, dir);
	}

	public File getCacheFile(URL url, File root) {
		root = new File(root, url.getHost());
		if (url.getPort() != -1 && url.getPort() != 80)
			root = new File(root, String.valueOf(url.getPort()));
		File f = new File(root, url.getPath());
		return f;
	}

	public final static HashMap<String, String> CONTENT_TYPES = new HashMap<String, String>();
	static {
		CONTENT_TYPES.put("xml", "application/xml");
		CONTENT_TYPES.put("pom", "application/xml");

		CONTENT_TYPES.put("jar", "application/java-archive");
		CONTENT_TYPES.put("zip", "application/zip");
		CONTENT_TYPES.put("war", "application/java-archive");

		CONTENT_TYPES.put("md5", "text/plain");
		CONTENT_TYPES.put("sha1", "text/plain");
		CONTENT_TYPES.put("asc", "text/plain");

	}

	private final static SimpleDateFormat INTERNET_FORMAT = new SimpleDateFormat(
			"EEE, d MMM yyyy HH:mm:ss zzz");
	private byte[] NEW_LINE = new byte[] { '\r', '\n' };

	private void println(String string) throws IOException {
		print(string);
		println();
	}

	private void println() throws IOException {
		getOut().write(NEW_LINE);
	}

	private void print(String string) throws IOException {
		getOut().write(string.getBytes("ISO-8859-1"));
	}

	private OutputStream out;

	protected OutputStream getOut() throws IOException {
		if (out == null)
			out = new BufferedOutputStream(clientSocket.getOutputStream());

		return out;
	}

	private BufferedInputStream in;

	private String readLine() throws IOException {
		if (in == null)
			in = new BufferedInputStream(clientSocket.getInputStream());

		StringBuffer buffer = new StringBuffer(256);
		int c;

		try {
			while ((c = in.read()) != -1) {
				if (c == '\r')
					continue;

				if (c == '\n')
					break;

				buffer.append((char) c);
			}
		} catch (SocketException e) {
			if ("connection reset".equals(e.getMessage().toLowerCase()))
				return null;

			throw e;
		}

		if (c == -1)
			return null;

		if (buffer.length() == 0)
			return "";

		return buffer.toString();
	}
}
