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

import java.util.Set;

import org.moxie.Build;
import org.moxie.Dependency;
import org.moxie.Pom;
import org.moxie.Scope;

public class MxReport extends MxTask {
	
	public void execute() {
		Build build = getBuild();		
		build.console.title(getClass(), build.getPom().getCoordinates());

		for (Scope scope : new Scope[] { Scope.compile, Scope.runtime, Scope.test, Scope.build }) {
			Set<Dependency> dependencies = build.getDependencies(scope);
			if (dependencies.size() == 0) {
				continue;
			}

			build.console.scope(scope, dependencies.size());

			for (Dependency dep : dependencies) {
				Pom depPom = build.getPom(dep);
				build.console.license(dep, depPom);
			}
		}
	}
}
