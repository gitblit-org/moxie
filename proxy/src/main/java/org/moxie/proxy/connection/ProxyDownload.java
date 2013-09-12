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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
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
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.util.DateUtil;
import org.moxie.Proxy;
import org.moxie.proxy.ProxyConfig;

/**
 * Download a file via a proxy server and store it somewhere.
 * 
 * @author digulla
 * 
 */
public class ProxyDownload {
	public static final Logger log = Logger.getLogger(ProxyDownload.class.getSimpleName());
	private final ProxyConfig config;
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
	public ProxyDownload(ProxyConfig config, URL url, File dest) {
		this.config = config;
		this.url = url;
		this.dest = dest;
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
					+ " Download denied by rule in Moxie Proxy config");
		}

		HttpClient client = new HttpClient();
		String userAgent = config.getUserAgent();
		if (userAgent != null && userAgent.trim().length() > 0) {
		    client.getParams().setParameter(HttpMethodParams.USER_AGENT, userAgent);
		}

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
				throw new DownloadFailed(get);
			}

			File dl = File.createTempFile("moxie-", ".tmp");
			OutputStream out = new BufferedOutputStream(new FileOutputStream(dl));
			copy(get.getResponseBodyAsStream(), out);
			out.close();
			
			// create folder structure after successful download
			dest.getParentFile().mkdirs();

			if (dest.exists()) {
				dest.delete();
			}
			dl.renameTo(dest);
			
			// preserve last-modified, if possible
			try {
				Header lastModified = get.getResponseHeader("Last-Modified");
				if (lastModified != null) {				
					Date date = DateUtil.parseDate(lastModified.getValue());				
					dest.setLastModified(date.getTime());
				}
			} catch (Exception e) {
				log.log(Level.WARNING, "could not parse \"last-modified\" for " + url, e);
			}
		} finally {
			get.releaseConnection();
		}
	}
	
	void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024 * 100];
		int len;

		while ((len = in.read(buffer)) != -1) {
			out.write(buffer, 0, len);
		}
		out.flush();
	}


	private String valueOf(Header responseHeader) {
		return responseHeader == null ? "unknown" : responseHeader.getValue();
	}
}
