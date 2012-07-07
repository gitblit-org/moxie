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
package org.moxie.proxy.resources;

import java.util.HashMap;
import java.util.Map;

import org.moxie.RemoteRepository;
import org.moxie.proxy.AllowDeny;
import org.moxie.proxy.Constants;
import org.moxie.proxy.Redirect;
import org.moxie.utils.StringUtils;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class RootResource extends BaseResource {

	@Override
	protected String getBasePath() {
		return "";
	}

	@Override
	protected String getBasePathName() {
		return "";
	}
	
	String reportConfig() {
		StringBuilder sb = new StringBuilder();

		// local repositories
		if (getProxyConfig().getLocalRepositories().size() > 0) {
			sb.append(StringUtils.toXML("h3", getTranslation().getString("mp.localRepositories")));
			sb.append("<ul class=\"unstyled\">\n");
			for (String repository : getProxyConfig().getLocalRepositories()) {
				sb.append("<li>").append(repository).append("</li>\n");
			}
			sb.append("</ul>\n");
		}
		
		// proxied/remote repositories
		if (getProxyConfig().getRemoteRepositories().size() > 0) {
			sb.append(StringUtils.toXML("h3", getTranslation().getString("mp.remoteRepositories")));
			sb.append("<ul class=\"unstyled\">\n");
			for (RemoteRepository repository : getProxyConfig().getRemoteRepositories()) {
				sb.append("<li>").append(repository.id).append(" &nbsp; => &nbsp; ").append(repository.url).append("</li>\n");
			}
			sb.append("</ul>\n");
		}
		
		// redirect rules
		if (getProxyConfig().getRedirects().size() > 0) {
			sb.append(StringUtils.toXML("h3", getTranslation().getString("mp.proxyRedirects")));
			sb.append("<table>\n");
			for (Redirect rule : getProxyConfig().getRedirects()) {
				sb.append("<tr><th>").append(getTranslation().getString("mp.from")).append(": &nbsp;</th><td>");
				sb.append(rule.getFrom());
				sb.append("</td><th>").append(getTranslation().getString("mp.to")).append(": &nbsp;</th><td>");
				sb.append(rule.getTo());
				sb.append("</td></tr>\n");
			}
			sb.append("</table><p>");
		}
		
		// allow/deny rules
		if (getProxyConfig().getAllowDeny().size() > 0) {
			sb.append(StringUtils.toXML("h3", getTranslation().getString("mp.proxyAllowDenyRepositories")));
			sb.append("<ol>\n");
			for (AllowDeny rule : getProxyConfig().getAllowDeny()) {
				sb.append("<li>");
				if (rule.isAllowed()) {
					sb.append("<span class='allow'>").append("allow");
				} else {
					sb.append("<span class='deny'>").append("deny");
				}
				sb.append("</span> ").append(rule.getURL()).append("</li>\n");

			}
			sb.append("</ol>\n");
		}
		return sb.toString();
	}
	
	@Get
	public Representation toText() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("tagline", getTranslation().getString("mp.tagline"));
		map.put("content", reportConfig());
		map.put("results", getApplication().getRecentArtifacts(null, 1, 10));
		return toHtml(map, "root.html");
	}
}
