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

import org.moxie.proxy.Constants;
import org.moxie.proxy.Main;
import org.moxie.proxy.RemoteRepository;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ServerResource;

public abstract class BaseResource extends ServerResource {

	@Override
	public Main getApplication() {
		return (Main) super.getApplication();
	}

	private String getUser() {
		return "";
	}
	
	@Override
	public Representation handle() {
		getApplication().getProxyConfig().reload();
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
				.getConfiguration(), map, MediaType.TEXT_HTML);
		template.setCharacterSet(CharacterSet.UTF_8);
		return template;
	}

	private String buildMenu() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n<ul class='nav'>\n");
		for (String folder : getApplication().getProxyConfig().getLocalRepositories()) {
			String name = folder;
			try {
				name = getTranslation().getString("mp." + folder);
			} catch (Exception e) {				
			}
			sb.append(url(folder, name, false));
		}
		for (RemoteRepository remote : getApplication().getProxyConfig().getRemoteRepositories()) {
			sb.append(url(remote.id, remote.id, true));
		}
		sb.append("</ul>\n");
		return sb.toString();
	}

	protected String url(String basePath, String name, boolean isRemote) {
		String icon = "";
		String tooltip = getTranslation().getString("mp.localRepository");
		if (isRemote) {
			icon = "<i class=\"icon-download-alt icon-white\"></i>";
			tooltip = getTranslation().getString("mp.remoteRepository");
		}
		if (basePath.equals(getBasePath())) {
			return MessageFormat.format("\t\t<li class='active'><a title=''{4}'' href=''{0}/{1}''>{2} {3}</a></li>\n",
					getRootRef(), basePath, name, icon, tooltip);
		}
		return MessageFormat.format("\t\t<li><a title=''{4}'' href=''{0}/{1}''>{2} {3}</a></li>\n", getRootRef(), basePath,
				name, icon, tooltip);
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