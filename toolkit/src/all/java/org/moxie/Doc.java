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
package org.moxie;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.utils.StringUtils;


public class Doc implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;

	public File sourceDirectory;
	
	public File templateDirectory;

	public File outputDirectory;

	public File header;

	public File footer;

	public String googleAnalyticsId;

	public String googlePlusId;

	public boolean googlePlusOne;

	public File ads;

	public Link structure;
	
	public References references;

	public List<String> keywords;

	public boolean injectPrettify;
	
	public boolean minify;

	public List<Substitute> substitutions = new ArrayList<Substitute>();

	public List<Load> loads = new ArrayList<Load>();

	public List<Prop> props = new ArrayList<Prop>();

	public List<NoMarkdown> nomarkdowns = new ArrayList<NoMarkdown>();

	public List<Regex> regexes = new ArrayList<Regex>();
	
	public List<Link> freeformPages = new ArrayList<Link>();
	
	public Logo logo;
	
	public Logo favicon;
	
	public boolean isResponsiveLayout;
	
	public String rssFeed;
	
	public String atomFeed;
	
	public File customLessFile;

	public List<File> getSources() {
		List<File> files = new ArrayList<File>();
		files.addAll(getSources(structure.sublinks));
		return files;
	}

	private List<File> getSources(List<Link> links) {
		List<File> files = new ArrayList<File>();
		for (Link link : links) {
			if (link.sublinks != null) {
				files.addAll(getSources(link.sublinks));
			} else if (link.isPage) {
				files.add(new File(sourceDirectory, link.src));
			}
		}
		return files;
	}

	void describe(Console console) {
		console.log("generation settings");
		describe(console, Key.sourceDirectory, sourceDirectory.toString());
		describe(console, Key.outputDirectory, outputDirectory.toString());
		describe(console, Key.googleAnalyticsId, googleAnalyticsId);
		describe(console, Key.googlePlusId, googlePlusId);
		describe(console, "injectPrettify", String.valueOf(injectPrettify));
		if (header != null && header.exists()) {
			describe(console, "header", header.toString());
		}
		if (footer != null && footer.exists()) {
			describe(console, "footer", footer.toString());
		}
		console.separator();
		console.log("structure");
		for (Link link : structure.sublinks) {
			describe(console, link);
		}
		console.separator();
	}

	void describe(Console console, Key key, String value) {
		describe(console, key.name(), value);
	}

	void describe(Console console, String key, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		console.key(StringUtils.leftPad(key, 12, ' '), value);
	}

	void describe(Console console, Link link) {
		if (link.isPage || link.isLink) {
			// page link or external link
			console.log(1, link.name + (link.isPage ? " = " : " => ") + link.src);
		} else if (link.isMenu) {
			// menu
			console.log(1, link.name);
			for (Link sublink : link.sublinks) {
				if (sublink.isDivider) {
					console.log(2, "--");					
				} else if (sublink.isPage || sublink.isLink) {
					console.log(2, sublink.name + (sublink.isPage ? " = " : " => ") + sublink.src);
				}
			}
		} else if (link.isDivider) {
			console.log(1, "--");
		}
	}
}
