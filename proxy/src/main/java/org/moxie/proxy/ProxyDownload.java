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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.moxie.Proxy;

/**
 * Download a file via a proxy server and store it somewhere.
 * 
 * @author digulla
 * 
 */
public class ProxyDownload {
	public static final Logger log = Logger.getLogger(ProxyDownload.class.getSimpleName());
	private final Config config;
	private final URL url;
	private final File dest;

	/**
	 * Download <code>url</code> to <code>dest</code>.
	 * 
	 * <p>
	 * If the directory to store <code>dest</code> doesn't exist, it will be
	 * created.
	 * 
	 * @param url
	 *            The resource to download
	 * @param dest
	 *            Where to store it.
	 */
	public ProxyDownload(Config config, URL url, File dest) {
		this.config = config;
		this.url = url;
		this.dest = dest;
	}

	/**
	 * Create the neccessary paths to store the destination file.
	 * 
	 * @throws IOException
	 */
	public void mkdirs() throws IOException {
		File parent = dest.getParentFile();
		IOUtils.mkdirs(parent);
	}

	/**
	 * Do the download.
	 * 
	 * @throws IOException
	 * @throws DownloadFailed
	 */
	public void download() throws IOException, DownloadFailed {
		if (!config.isAllowed(url)) {
			throw new DownloadFailed("HTTP/1.1 " + HttpStatus.SC_FORBIDDEN
					+ " Download denied by rule in DSMP config");
		}

		// If there is a status file in the cache, return it instead of trying
		// it again. As usual with caches, this one is a key area which will
		// always cause trouble.
		// TODO There should be a simple way to get rid of the cached statuses
		// TODO Maybe retry a download after a certain time?
		File statusFile = new File(dest.getAbsolutePath() + ".status");
		if (statusFile.exists()) {
			try {
				FileReader r = new FileReader(statusFile);
				char[] buffer = new char[(int) statusFile.length()];
				int len = r.read(buffer);
				r.close();
				String status = new String(buffer, 0, len);
				throw new DownloadFailed(status);
			} catch (IOException e) {
				log.log(Level.WARNING,
						"Error writing 'File not found'-Status to " + statusFile.getAbsolutePath(), e);
			}
		}

		mkdirs();

		HttpClient client = new HttpClient();

		String msg = "";
		if (config.useProxy(url)) {
			Proxy proxy = config.getProxy(url);
			Credentials defaultcreds = new UsernamePasswordCredentials(proxy.username, proxy.password);
			AuthScope scope = new AuthScope(proxy.host, proxy.port, AuthScope.ANY_REALM);
			HostConfiguration hc = new HostConfiguration();
			hc.setProxy(proxy.host, proxy.port);
			client.setHostConfiguration(hc);
			client.getState().setProxyCredentials(scope, defaultcreds);
			msg = "via proxy ";
		}
		log.info("Downloading " + msg + "to " + dest.getAbsolutePath());

		GetMethod get = new GetMethod(url.toString());
		get.setFollowRedirects(true);
		try {
			int status = client.executeMethod(get);

			log.info("Download status: " + status);
			if (status == 1 && log.isLoggable(Level.FINE)) {
				Header[] header = get.getResponseHeaders();
				for (int i = 0; i < header.length; i++)
					log.fine(header[i].toString().trim());
			}

			log.info("Content: " + valueOf(get.getResponseHeader("Content-Length")) + " bytes; "
					+ valueOf(get.getResponseHeader("Content-Type")));

			if (status != HttpStatus.SC_OK) {
				// Remember "File not found"
				if (status == HttpStatus.SC_NOT_FOUND) {
					try {
						FileWriter w = new FileWriter(statusFile);
						w.write(get.getStatusLine().toString());
						w.close();
					} catch (IOException e) {
						log.log(Level.WARNING,
								"Error writing 'File not found'-Status to " + statusFile.getAbsolutePath(), e);
					}
				}
				throw new DownloadFailed(get);
			}

			File dl = new File(dest.getAbsolutePath() + ".new");
			OutputStream out = new BufferedOutputStream(new FileOutputStream(dl));
			IOUtils.copy(get.getResponseBodyAsStream(), out);
			out.close();

			File bak = new File(dest.getAbsolutePath() + ".bak");
			if (bak.exists())
				bak.delete();
			if (dest.exists())
				dest.renameTo(bak);
			dl.renameTo(dest);
		} finally {
			get.releaseConnection();
		}
	}

	private String valueOf(Header responseHeader) {
		return responseHeader == null ? "unknown" : responseHeader.getValue();
	}
}
