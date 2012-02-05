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
package com.maxtk.ant;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.maxtk.Config;
import com.maxtk.Dependency;
import com.maxtk.Doc;
import com.maxtk.Docs;
import com.maxtk.Link;
import com.maxtk.Load;
import com.maxtk.NoMarkdown;
import com.maxtk.Prop;
import com.maxtk.Regex;
import com.maxtk.Setup;
import com.maxtk.Substitute;
import com.maxtk.ant.MaxTask.Property;

public class MaxDoc extends Task {

	boolean verbose;

	Doc doc = new Doc();
	
	public Link createStructure() {
		Link link = new Link();
		doc.structure = link;
		return link;
	}

	public Substitute createSubstitute() {
		Substitute sub = new Substitute();
		doc.substitutions.add(sub);
		return sub;
	}

	public Load createLoad() {
		Load load = new Load();
		doc.loads.add(load);
		return load;
	}

	public Prop createProperties() {
		Prop prop = new Prop();
		doc.props.add(prop);
		return prop;
	}

	public NoMarkdown createNomarkdown() {
		NoMarkdown nomd = new NoMarkdown();
		doc.nomarkdowns.add(nomd);
		return nomd;
	}

	public Regex createRegex() {
		Regex regex = new Regex();
		doc.regexes.add(regex);
		return regex;
	}

	public void setName(String name) {
		doc.name = name;
	}
	
	public void setSourceFolder(File folder) {
		doc.sourceFolder = folder;
	}

	public void setOutputFolder(File folder) {
		doc.outputFolder = folder;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setInjectprettify(boolean value) {
		doc.injectPrettify = value;
	}

	public void setInjectfancybox(boolean value) {
		doc.injectFancybox = value;
	}

	public void setGoogleAnalyticsid(String value) {
		doc.googleAnalyticsId = value;
	}

	public void setGooglePlusid(String value) {
		doc.googlePlusId = value;
	}

	public void setGooglePlusOne(boolean value) {
		doc.googlePlusOne = value;
	}

		@Override
	public void execute() throws BuildException {
		Config conf = (Config) getProject().getReference(Property.max_conf.id());
		checkDependencies(conf);
		Docs.execute(conf, doc, verbose);
	}

	private void checkDependencies(Config config) {
		try {
			Class.forName("org.tautua.markdownpapers.Markdown");
		} catch (Throwable t) {
			Dependency markdownpapers = new Dependency("markdownpapers-core", "1.2.5",
					"org/tautua/markdownpapers");
			Setup.retriveInternalDependency(config, markdownpapers);
		}
	}
}
