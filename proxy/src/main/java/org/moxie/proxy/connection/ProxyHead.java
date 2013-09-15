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
 * Process the http HEAD request, derived from ProxyDownload
 */
public class ProxyHead {
	public static final Logger log = Logger.getLogger(ProxyHead.class.getSimpleName());
	private final ProxyConfig config;
	private final URL url;
        private Header[] header;
        private String statusLine;
        private byte[] responseBody;

	public ProxyHead(ProxyConfig config, URL url) {
		this.config = config;
		this.url = url;
	}

	/**
	 * Do the download (of the headers).
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
		log.info("Downloading " + msg + url);

		MyHeadMethod head = new MyHeadMethod(url.toString());
		head.setFollowRedirects(true);
		try {
			int status = client.executeMethod(head);

			log.info("Download status: " + status);
			this.header = head.getResponseHeaders();
			this.statusLine = head.getStatusLine().toString();
			this.responseBody = head.getResponseBody();
			if (status == 1 && log.isLoggable(Level.FINE)) {
				for (int i = 0; i < header.length; i++)
					log.fine(header[i].toString().trim());
			}

			log.info("Content: " + valueOf(head.getResponseHeader("Content-Length")) + " bytes; "
					+ valueOf(head.getResponseHeader("Content-Type")));

			if (status != HttpStatus.SC_OK) {
				throw new DownloadFailed(head);
			}

		} finally {
			head.releaseConnection();
		}
	}
	
        public Header[] getResponseHeaders() {
	    return this.header;
        }

        public String getStatusLine() {
	    return this.statusLine;
        }

        public byte[] getResponseBody() {
	    return this.responseBody;
        }

	private String valueOf(Header responseHeader) {
		return responseHeader == null ? "unknown" : responseHeader.getValue();
	}

    /*
     * My own HeadMethod. I gave  org.apache.commons.httpclient.methods.HeadMethod
     * a try first, but it doesn't seem to work OK. Gradle builds do fail with it.
     *
     * My head method uses the content of the URL as well as the headers.
     */
    private class MyHeadMethod extends GetMethod {
	public MyHeadMethod(String url) {
	    super(url);
	}
	public String getName() {
	    return("HEAD");
	}
    }
}
