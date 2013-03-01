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
package org.moxie.ant;

import java.io.File;
import java.text.MessageFormat;
import java.util.Set;

import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Pom;
import org.moxie.Scope;
import org.moxie.console.Console;
import org.moxie.utils.FileUtils;

public class MxReport extends MxTask {
	
	private Scope scope;
	
	File destFile;
	
	public MxReport() {
		super();
		setTaskName("mx:report");
	}
	
	public void setScope(String scope) {
		this.scope = Scope.fromString(scope);
	}
	
	public void setDestfile(File file) {
		this.destFile = file;
	}
	
	public void execute() {
		Build build = getBuild();		
		titleClass(build.getPom().getCoordinates());

		Scope [] scopes;
		if (scope == null) {
			scopes = new Scope[] { Scope.compile, Scope.runtime, Scope.test, Scope.build };
		} else {
			scopes = new Scope[] { scope };
		}
		StringBuilder sb = new StringBuilder();
		for (Scope scope : scopes) {
			Set<Dependency> dependencies = build.getSolver().getDependencies(scope);
			if (dependencies.size() == 0) {
				continue;
			}

			sb.append(getConsole().scope(scope, dependencies.size()));
			sb.append('\n');
			long totalArtifactsSize = 0;
			for (Dependency dep : dependencies) {
				Pom depPom = build.getSolver().getPom(dep);
				File artifact = build.getSolver().getArtifact(dep);
				if (artifact != null && artifact.exists()) {
					totalArtifactsSize += artifact.length();
				}
				sb.append(getConsole().dependencyReport(dep, depPom, artifact));
			}
			if (totalArtifactsSize > 0) {
				String summary = MessageFormat.format("{0} artifacts totaling {1} for {2} scope", dependencies.size(), FileUtils.formatSize(totalArtifactsSize), scope);
				getConsole().separator();
				getConsole().log(1, summary);
				
				sb.append(Console.SEP).append('\n');
				sb.append(summary).append('\n');
			}
		}
		if (destFile != null) {
			if (isVerbose()) {
				getConsole().debug("generating {0}", destFile.getAbsolutePath());
			}
			FileUtils.writeContent(destFile, sb.toString());
		}
	}
}
