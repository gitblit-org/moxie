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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.moxie.RemoteRepository;
import org.moxie.proxy.Constants;
import org.moxie.proxy.MoxieProxy;
import org.moxie.proxy.ProxyConfig;
import org.moxie.utils.StringUtils;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ServerResource;

public abstract class BaseResource extends ServerResource {

	@Override
	public MoxieProxy getApplication() {
		return (MoxieProxy) super.getApplication();
	}
	
	public ProxyConfig getProxyConfig() {
		return getApplication().getProxyConfig();
	}

	private String getUser() {
		return "";
	}
	
	protected String getRequestAttribute(String attribute) {
		String value = null;
		if (getRequestAttributes().containsKey(attribute)) {
			value = getRequestAttributes().get(attribute).toString();
		}
		return value;
	}
	
	protected int getQueryValue(String parameter, int defaultValue) {
		if (!StringUtils.isEmpty(getQueryValue(parameter))) {
			String val = getQueryValue(parameter);
			try {
				return (int) Double.parseDouble(val);
			} catch (Exception e) {
			}
		}
		return defaultValue;
	}

	protected String getQueryValue(String parameter, String defaultValue) {
		if (!StringUtils.isEmpty(getQueryValue(parameter))) {
			return getQueryValue(parameter);
		}
		return defaultValue;
	}

	@Override
	public Representation handle() {
		getProxyConfig().reload();
		return super.handle();
	}

	protected abstract String getBasePath();
	
	protected abstract String getBasePathName();

	protected Representation toHtml(Map<String, Object> map, String templateName) {
		map.put("appName", Constants.getName());
		map.put("appVersion", Constants.getVersion());
		map.put("appUrl", Constants.getUrl());
		map.put("appMenu", buildMenu());
		map.put("rc", getTranslation());
		map.put("baseRef", getRootRef());
		map.put("user", getUser());
		map.put("pageRef", getRootRef() + "/" + getBasePath());
		TemplateRepresentation template = new TemplateRepresentation(templateName, getApplication()
				.getFreemarkerConfiguration(), map, MediaType.TEXT_HTML);
		template.setCharacterSet(CharacterSet.UTF_8);
		return template;
	}

	private String buildMenu() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n<ul class='nav'>\n");
		for (String folder : getProxyConfig().getLocalRepositories()) {
			String name = folder;
			try {
				name = getTranslation().getString("mp." + folder);
			} catch (Exception e) {				
			}
			sb.append(menuItem(folder, name, false));
		}
		if (getProxyConfig().isProxyEnabled()) {
			for (RemoteRepository remote : getProxyConfig().getRemoteRepositories()) {
				sb.append(menuItem(remote.id, remote.id, true));
			}
		}
		sb.append(menuItem("search", getTranslation().getString("mp.search"), "", "<i class=\"icon-search icon-white\"></i>", "hidden-desktop"));		
		sb.append("</ul>\n");
		return sb.toString();
	}

	protected String menuItem(String basePath, String name, boolean isRemote) {
		String icon = "";
		String tooltip = getTranslation().getString("mp.localRepository");
		if (isRemote) {
			icon = "<i class=\"icon-download-alt icon-white\"></i>";
			tooltip = getTranslation().getString("mp.remoteRepository");
		}
		return menuItem(basePath, name, tooltip, icon, "");
	}

	protected String menuItem(String basePath, String name, String tooltip, String icon, String cssClass) {
		if (basePath.equals(getBasePath())) {
			return MessageFormat.format("\t\t<li class=''active {5}''><a title=''{4}'' href=''{0}/{1}''>{2} {3}</a></li>\n",
					getRootRef(), basePath, name, icon, tooltip, cssClass);
		}
		return MessageFormat.format("\t\t<li class=''{5}''><a title=''{4}'' href=''{0}/{1}''>{2} {3}</a></li>\n", getRootRef(), basePath,
				name, icon, tooltip, cssClass);
	}

	protected ResourceBundle getTranslation() {
		// determine translation to load into template
		ResourceBundle bundle = null;
		for (Preference<Language> preference : getRequest().getClientInfo().getAcceptedLanguages()) {
			if (bundle != null) {
				// already loaded a bundle
				break;
			}
			// determine locale name and lang
			// e.g. en_us and en
			String localeName = preference.getMetadata().getName().replace('-', '_');
			String lang = localeName;
			if (lang.indexOf('_') > -1) {
				lang = lang.substring(0, lang.indexOf('_'));
			}
			for (Locale locale : Locale.getAvailableLocales()) {
				if (locale.toString().equalsIgnoreCase(localeName) || locale.toString().equals(lang)) {
					// exact locale match or available language match
					bundle = ResourceBundle.getBundle("translations", locale);
					if (bundle.getLocale().equals(locale)) {
						// this really is the requested locale
						break;
					}
				}
			}
		}

		if (bundle == null) {
			// default translation
			bundle = ResourceBundle.getBundle("translations");
		}
		return bundle;
	}
}