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
package com.maxtk;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.maxtk.Constants;
import com.maxtk.Config.Key;
import com.maxtk.utils.StringUtils;

public class Doc implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;

	public File sourceFolder;

	public File outputFolder;

	public File header;

	public File footer;

	public String favicon;

	public String googleAnalyticsId;

	public String googlePlusId;

	public boolean googlePlusOne;

	public File ads;

	public Link structure;

	public List<String> keywords;

	public boolean injectPrettify;

	public boolean injectFancybox;

	public List<Substitute> substitutions = new ArrayList<Substitute>();

	public List<Load> loads = new ArrayList<Load>();

	public List<Prop> props = new ArrayList<Prop>();

	public List<NoMarkdown> nomarkdowns = new ArrayList<NoMarkdown>();

	public List<Regex> regexes = new ArrayList<Regex>();

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
				files.add(new File(sourceFolder, link.src));
			}
		}
		return files;
	}

	void describe(PrintStream out) {
		out.println("doc:");
		describe(out, Key.sourceFolder, sourceFolder.toString());
		describe(out, Key.outputFolder, outputFolder.toString());
		describe(out, Key.googleAnalyticsId, googleAnalyticsId);
		describe(out, Key.googlePlusId, googlePlusId);
		describe(out, "injectFancybox", String.valueOf(injectFancybox));
		describe(out, "injectPrettify", String.valueOf(injectPrettify));
		if (header != null && header.exists()) {
			describe(out, "header", header.toString());
		}
		if (footer != null && footer.exists()) {
			describe(out, "footer", footer.toString());
		}
		out.println("structure");
		for (Link link : structure.sublinks) {
			describe(out, link);
		}
	}

	void describe(PrintStream out, Key key, String value) {
		describe(out, key.name(), value);
	}

	void describe(PrintStream out, String key, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		out.print(Constants.INDENT);
		out.print(StringUtils.leftPad(key, 12, ' '));
		out.print(": ");
		out.println(value);
	}

	void describe(PrintStream out, Link link) {
		if (link.isPage || link.isLink) {
			// page link or external link
			out.print(Constants.INDENT);
			out.print(link.name);
			out.print(link.isPage ? " = " : " => ");
			out.println(link.src);
		} else if (link.isMenu) {
			// menu
			out.print(Constants.INDENT);
			out.println(link.name);
			for (Link sublink : link.sublinks) {
				out.print(Constants.INDENT);
				out.print(Constants.INDENT);
				if (sublink.isDivider) {
					out.println("--");
				} else if (sublink.isPage || sublink.isLink) {
					out.print(sublink.name);
					out.print(sublink.isPage ? " = " : " => ");
					out.println(sublink.src);
				}
			}
		} else if (link.isDivider) {
			out.println("--");
		}
	}
}
