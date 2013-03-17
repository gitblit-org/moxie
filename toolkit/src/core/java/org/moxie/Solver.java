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
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.moxie.MoxieException.MissingParentPomException;
import org.moxie.console.Console;
import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.Parallel;
import org.moxie.utils.StringUtils;


/**
 * Solves transitive dependency chains, scoped classpaths, and linked projects.
 * <p>
 * Solver employs Maven 2 transitive dependency resolution based on "nearness"
 * and declaration order using a 2-pass scheme.  Pass one recursively retrieves
 * all referenced POMs. Pass two analyzes each retrieved POM for a particular
 * solving scope and builds a flat dependency list with ring scores.
 * <p>
 * Ring-0 is the project itself. Ring-1 are direct named dependencies. Ring > 1
 * are solved transitive dependencies.  Dependency conflicts are resolved by
 * choosing the dependency in the lowest ring OR the first declaration, if the
 * rings are equal.
 * <p>
 * Linked projects are treated as source folders of the build project.  Therefore
 * the dependencies linked projects are assimilated into the build project's
 * dependencies.
 */
public class Solver {

	private final BuildConfig config;
	private final MoxieCache moxieCache;
	private final Console console;
	
	private final Map<Scope, Collection<Dependency>> requiredDependencies;
	private final Map<Scope, Set<Dependency>> solutions;	
	private final Map<Scope, List<File>> classpaths;
	private final Set<String> registeredUrls;
	private List<Build> linkedModuleBuilds;
	
	private boolean silent;
	private boolean verbose;
	private boolean solutionBuilt;
	
	public Solver(Console console, BuildConfig config) {
		this.config = config;
		
		this.moxieCache = new MoxieCache(config.getMoxieRoot());
		this.requiredDependencies = new HashMap<Scope, Collection<Dependency>>();
		this.solutions = new HashMap<Scope, Set<Dependency>>();
		this.classpaths = new HashMap<Scope, List<File>>();
		this.linkedModuleBuilds = new ArrayList<Build>();
		this.registeredUrls = new HashSet<String>();
		this.console = console == null ? new Console(config.isColor()) : console;
		
		this.moxieCache.setMavenCacheStrategy(config.getMavenCacheStrategy());
		
		// define required dependencies
		requiredDependencies.put(Scope.build,resolveAliasedDependencies(
				new Dependency("mx:commons-net"),
				new Dependency("mx:oro")
				));
	}
	
	boolean isOnline() {
		String mxOnline = System.getProperty(Toolkit.MX_ONLINE, null);
		if (!StringUtils.isEmpty(mxOnline)) {
			// use system property to determine online
			return Boolean.parseBoolean(mxOnline);
		}
		return true;
	}
	
	boolean isUpdateMetadata() {
		String mxUpdateMetadata = System.getProperty(Toolkit.MX_UPDATEMETADATA, null);
		if (!StringUtils.isEmpty(mxUpdateMetadata)) {
			// use system property to force updating maven-metadata.xml
			return Boolean.parseBoolean(mxUpdateMetadata);
		}
		return false;
	}
	
	boolean isFailOnChecksumError() {
		String mxEnforceChecksums = System.getProperty(Toolkit.MX_ENFORCECHECKSUMS, null);
		if (!StringUtils.isEmpty(mxEnforceChecksums)) {
			// use system property to determine enforcing checksum verification
			return Boolean.parseBoolean(mxEnforceChecksums);
		}
		return true;
	}

	private boolean cache() {
		return config.getMoxieConfig().apply(Toolkit.APPLY_CACHE) || config.getProjectConfig().apply(Toolkit.APPLY_CACHE);
	}
	
	private void resolveAliasedDependencies() {
		resolveAliasedDependencies(config.getPom().getDependencies(false).toArray(new Dependency[0]));
	}
	
	private List<Dependency> resolveAliasedDependencies(Dependency... dependencies) {
		List<Dependency> list = new ArrayList<Dependency>();
		for (Dependency dep : dependencies) {
			list.add(dep);
			// check for alias
			String name = null;
			if (StringUtils.isEmpty(dep.artifactId) && config.getAliases().containsKey(dep.groupId)) {
				// alias by simple name
				name = dep.groupId;
			} else if (config.getAliases().containsKey(dep.getManagementId())) {
				// alias by groupId:artifactId
				name = dep.getManagementId();
			}

			if (name != null) {
				// we have an alias
				Dependency alias = config.getAliases().get(name);
				dep.groupId = alias.groupId;
				dep.artifactId = alias.artifactId;
				dep.version = alias.version;
				
				if (StringUtils.isEmpty(dep.version)) {
					dep.version = config.getPom().getManagedVersion(dep);
					if (StringUtils.isEmpty(dep.version)) {
						dep.version = config.getMoxieConfig().getPom().getManagedVersion(dep);
					}
				}
				if (StringUtils.isEmpty(dep.version)) {
					console.error("unable to resolve version for alias {0} = ", name, dep.getCoordinates());
				} else {
					console.debug("resolved dependency alias {0} = {1}", name, dep.getCoordinates());
				}
			}
		}
		return list;
	}
		
	public List<Build> getLinkedModules() {
		return linkedModuleBuilds;
	}

	public MoxieCache getMoxieCache() {
		return moxieCache;
	}
	
	public Console getConsole() {
		return console;
	}
	
	public BuildConfig getBuildConfig() {
		return config;
	}
	
	public Pom getPom(Dependency dependency) {
		return PomReader.readPom(moxieCache, dependency);
	}

	public File getArtifact(Dependency dependency) {
		return moxieCache.getArtifact(dependency, dependency.extension);
	}

	public Set<Dependency> getDependencies(Scope scope) {
		return solve(scope);
	}
	
	public boolean solve() {
		return solve(new LinkedHashSet<Build>());
	}
	
	private boolean solve(Set<Build> solvedProjects) {
		readProjectSolution();
		if (solutions.size() == 0) {
			// solve linked projects
			solveLinkedModules(solvedProjects);
			
			// substitute aliases with definitions
			resolveAliasedDependencies();

			// build solution
			retrievePOMs();
			importDependencyManagement();
			assimilateDependencies();
			retrieveDependencies();
			
			// cache built solution
			cacheProjectSolution();
			
			// flag new solution
			solutionBuilt = true;
		} else {
			// we may have a cached solution, but we need to confirm we have
			// the pom and artifacts
			Set<Dependency> all = new LinkedHashSet<Dependency>();
			for (Map.Entry<Scope, Set<Dependency>> entry : solutions.entrySet()) {
				all.addAll(entry.getValue());
			}
			Set<Dependency> retrieved = new HashSet<Dependency>();
			for (Dependency dep : all) {
				if (!retrieved.contains(dep)) {
					retrievePOM(dep, retrieved);					
				}
			}
			
			if (config.getProjectConfig().parallelDownloads) {
				// download artifacts in parallel
				Parallel.WaitFor(retrieved, new Parallel.Operation<Dependency>() {
					public void perform(Dependency dep) {
						retrieveArtifact(dep);					
					}
				});
			} else {
				// download artifacts in serial
				for (Dependency dep : retrieved) {
					retrieveArtifact(dep);
				}
			}
		}
		return solutionBuilt;
	}
	
	private void solveLinkedModules(Set<Build> solvedModules) {
		if (config.getProjectConfig().linkedModules.size() > 0) {
			console.separator();
			console.log("solving {0} modules", config.getPom().getManagementId());
			console.separator();
		}
		Set<Build> builds = new LinkedHashSet<Build>();
		for (Module linkedModule : config.getProjectConfig().linkedModules) {
			console.debug(Console.SEP);
			String resolvedName = config.getPom().resolveProperties(linkedModule.folder);
			if (resolvedName.equals(linkedModule.folder)) {
				console.debug("locating module {0}", linkedModule.folder);
			} else {
				console.debug("locating module {0} ({1})", linkedModule.folder, resolvedName);
			}
			File moduleDir = new File(resolvedName);
			console.debug(1, "trying {0}", moduleDir.getAbsolutePath());
			if (!moduleDir.exists()) {
				moduleDir = new File(config.getProjectDirectory().getParentFile(), resolvedName);
				console.debug(1, "trying {0}", moduleDir.getAbsolutePath());
				if (!moduleDir.exists()) {
					String msg = console.error("failed to find module \"{0}\".", linkedModule.folder);
					throw new MoxieException(msg);
				}
			}
			try {
				File file = new File(moduleDir, linkedModule.descriptor);
				if (file.exists()) {
					// use Moxie config
					console.debug("located module {0} ({1})", linkedModule.folder, file.getAbsolutePath());
					Build subModule = new Build(file.getAbsoluteFile(), null);
					if (solvedModules.contains(subModule)) {
						for (Build solvedProject: solvedModules) {
							if (solvedProject.equals(subModule)) {
								// add this submodule and it's dependent modules
								builds.add(subModule);
								builds.addAll(subModule.getSolver().getLinkedModules());
								break;
							}
						}
						console.log(1, "=> already solved module {0}", subModule.getPom().getCoordinates());
						continue;
					}
					console.log(1, "=> solving module {0}", subModule.getPom().getCoordinates());
					subModule.getSolver().silent = true;
					subModule.getSolver().solve(solvedModules);

					// add this subproject and it's dependent modules
					// as solvedModules for recursive, cross-linked modules
					solvedModules.add(subModule);
					solvedModules.addAll(subModule.getSolver().getLinkedModules());

					// add this submodule and it's dependent modules
					builds.add(subModule);
					builds.addAll(subModule.getSolver().getLinkedModules());
					
					// linked module dependencies are considered ring-1
					for (Scope scope : new Scope[] { Scope.compile }) {
						for (Dependency dep : subModule.getPom().getDependencies(scope, Constants.RING1)) {
							config.getPom().addDependency(dep, scope);
						}
					}
				} else {
					console.error("module {0} does not have a {1} descriptor!", linkedModule.folder, linkedModule.descriptor);
				}
			} catch (Exception e) {
				console.error(e, "failed to parse module {0}", linkedModule.folder);
				throw new RuntimeException(e);
			}
		}
		
		// add the list of unique builds
		linkedModuleBuilds.addAll(builds);
	}
	
	private void retrievePOMs() {
		console.debug("locating POMs");
		
		// clear registered urls
		registeredUrls.clear();
		for (Repository repository : config.getRepositories()) {
			registeredUrls.add(repository.repositoryUrl);
		}
		
		// retrieve POMs serially for all dependencies in all scopes
		// parallelizing this is tricky because of recursive pom retrieval with
		// potential dependency overlap
		Set<Dependency> downloaded = new HashSet<Dependency>(); 
		for (Scope scope : config.getPom().getScopes()) {
			for (Dependency dependency : config.getPom().getDependencies(scope, Constants.RING1)) {
				retrievePOM(dependency, downloaded);
			}
		}
	}

	private void importDependencyManagement() {
		if (config.getPom().getScopes().contains(Scope.imprt)) {
			console.debug("importing dependency management");

			// This Moxie project imports a pom's dependencyManagement list.
			for (Dependency dependency : config.getPom().getDependencies(Scope.imprt, Constants.RING1)) {
				Pom pom = PomReader.readPom(moxieCache, dependency);
				config.getPom().importManagedDependencies(pom);
			}
		}
	}
	
	private void assimilateDependencies() {
		Map<Scope, List<Dependency>> assimilate = new LinkedHashMap<Scope, List<Dependency>>();
		if (config.getPom().getScopes().contains(Scope.assimilate)) {
			console.debug("assimilating dependencies");
			
			// This Moxie project integrates a pom's dependency list.
			for (Dependency dependency : config.getPom().getDependencies(Scope.assimilate, Constants.RING1)) {
				Pom pom = PomReader.readPom(moxieCache, dependency);
				for (Scope scope : pom.getScopes()) {
					if (!assimilate.containsKey(scope)) {
						assimilate.put(scope,  new ArrayList<Dependency>());
					}
					assimilate.get(scope).addAll(pom.getDependencies(scope));
				}
			}
			
			// merge unique, assimilated dependencies into the Moxie project pom
			for (Map.Entry<Scope, List<Dependency>> entry : assimilate.entrySet()) {
				for (Dependency dependency : entry.getValue()) {
					config.getPom().addDependency(dependency, entry.getKey());
				}
			}
		}
		
		// remove assimilate scope from the project pom, like it never existed
		config.getPom().removeScope(Scope.assimilate);
	}
	
	private void retrieveDependencies() {
		console.debug("retrieving artifacts");
		// solve dependencies for compile, runtime, test, and build scopes
		final Set<Dependency> retrieved = Collections.synchronizedSet(new HashSet<Dependency>());
		for (final Scope scope : new Scope [] { Scope.compile, Scope.runtime, Scope.test, Scope.build }) {
			if (!silent && verbose) {
				console.separator();
				console.scope(scope, 0);
				console.separator();
			}
			Set<Dependency> solution = solve(scope);
			if (solution.size() == 0) {
				if (!silent && verbose) {
					console.log(1, "none");
				}
			} else {
				Parallel.Operation<Dependency> worker = new Parallel.Operation<Dependency>() {
					public void perform(Dependency dependency) {
						if (retrieved.add(dependency)) {
							if (!silent && verbose) {
								console.dependency(dependency);
							}
							File artifactFile = retrieveArtifact(dependency);
							if (artifactFile == null && !Constants.POM.equals(dependency.extension)) {
								console.artifactResolutionFailed(dependency);
								if (config.isFailFastOnArtifactResolution()) {
									throw new MoxieException(MessageFormat.format("Failed to resolve {0}", dependency.getCoordinates()));
								}
							}
							if (!Scope.build.equals(scope)) {
								copyArtifact(dependency, artifactFile);
							}
						}
					}
				};
				
				if (config.getProjectConfig().parallelDownloads) {
					// Download artifacts in parallel
					Parallel.WaitFor(solution, worker);
				} else {
					// Download artifacts in serial
					for (Dependency dep : solution) {
						worker.perform(dep);
					}
				}
			}
		}
	}
	
	Set<Dependency> solve(Scope solutionScope) {
		if (solutions.containsKey(solutionScope)) {
			return solutions.get(solutionScope);
		}
		
		console.debug("solving {0} dependency solution", solutionScope);
		
		// assemble the flat, ordered list of dependencies
		// this list may have duplicates/conflicts
		List<Dependency> all = new ArrayList<Dependency>();
		
		// add in required dependencies (designed for build scope)
		if (requiredDependencies.containsKey(solutionScope)) {
			all.addAll(requiredDependencies.get(solutionScope));
		}
		for (Dependency dependency : config.getPom().getDependencies(solutionScope, Constants.RING1)) {
			console.debug(dependency.getDetailedCoordinates());
			all.add(dependency);
			all.addAll(solve(solutionScope, dependency));
		}
		
		// dependency mediation based on artifact type and nearness (ring)
		Map<String, Dependency> uniques = new LinkedHashMap<String, Dependency>();		
		for (Dependency dependency : all) {
			if (uniques.containsKey(dependency.getMediationId())) {
				// we have another registration for this dependency
				Dependency registered = uniques.get(dependency.getMediationId());
				if (registered.ring > dependency.ring) {
					// this dependency is closer, use it instead
					uniques.put(dependency.getMediationId(), dependency);
				}
			} else {
				// register unique dependency
				uniques.put(dependency.getMediationId(), dependency);
			}
		}
		
		Set<Dependency> solution = new LinkedHashSet<Dependency>(uniques.values());		
		solutions.put(solutionScope, solution);
		return solution;
	}
	
	private List<Dependency> solve(Scope scope, Dependency dependency) {
		List<Dependency> resolved = new ArrayList<Dependency>();
		if (!dependency.resolveDependencies) {
			return resolved;
		}
		File pomFile = moxieCache.getArtifact(dependency, Constants.POM);
		if (pomFile == null || !pomFile.exists()) {
			return resolved;
		}
		
		List<Dependency> dependencies = null;
		
		// check to see if we have overridden the POM dependencies
		Pom override = config.getProjectConfig().getDependencyOverrides(scope, dependency.getCoordinates());
		if (override == null) {
			override = config.getMoxieConfig().getDependencyOverrides(scope, dependency.getCoordinates());
		}
		if (override != null) {
			if (Scope.build.equals(scope)) {
				// build scope overrides are normal
				console.debug("OVERRIDE: {0} {1} dependency {2}", config.getPom().getCoordinates(), scope.name().toUpperCase(), dependency.getCoordinates());
			} else {
				// notify on any other scope
				console.notice("OVERRIDE: {0} {1} dependency {2}", config.getPom().getCoordinates(), scope.name().toUpperCase(), dependency.getCoordinates());
			}
			dependencies = override.getDependencies(scope, dependency.ring + 1);
		}
		
		if (dependencies == null) {
			// try pre-resolved solution for this scope
			dependencies = readSolution(scope, dependency);
		}
		
		if (dependencies == null) {
			// solve the transitive dependencies for this scope
			Pom pom = PomReader.readPom(moxieCache, dependency);
			dependencies = pom.getDependencies(scope, dependency.ring + 1);

			// cache the scope's transitive dependency solution
			cacheSolution(scope, dependency, dependencies);
		}

		if (dependencies.size() > 0) {			
			for (Dependency dep : dependencies) {
				if (!dependency.excludes(dep)) {
					dep.tags.addAll(dependency.tags);
					dep.exclusions.addAll(dependency.exclusions);
					resolved.add(dep);
					resolved.addAll(solve(scope, dep));
				}
			}			
		}

		return resolved;
	}
	
	private List<Dependency> readSolution(Scope scope, Dependency dependency) {
		if (!cache() || !dependency.isMavenObject()) {
			// caching forbidden 
			return null;
		}
		if (dependency.isSnapshot()) {
			// do not use cached solution for snapshots
			return null;
		}
		MoxieData moxiedata = moxieCache.readMoxieData(dependency);
		if (moxiedata.isValidSolution() && 
				moxiedata.getLastSolved().getTime() == FileUtils.getLastModified(moxieCache.getArtifact(dependency, Constants.POM))) {
			// solution lastModified date must equal pom lastModified date
			try {
				console.debug(1, "=> reusing solution {0}", dependency.getDetailedCoordinates());				
				if (moxiedata.hasScope(scope)) {
					List<Dependency> list = new ArrayList<Dependency>(moxiedata.getDependencies(scope));
					for (Dependency dep : list) {
						// reset ring to be relative to the dependency
						dep.ring += dependency.ring + 1;
					}
					return list;
				}
			} catch (Exception e) {
				console.error(e, "Failed to read dependency solution {0}", dependency.getDetailedCoordinates());
			}
		}
		return null;
	}
	
	private void cacheSolution(Scope scope, Dependency dependency, List<Dependency> transitiveDependencies) {
		if (transitiveDependencies.size() == 0) {
			return;
		}
		MoxieData moxiedata = moxieCache.readMoxieData(dependency);
		// copy transitives and reset the ring level relative to the dependency		
		List<Dependency> dependencies = new ArrayList<Dependency>();
		for (Dependency dep : transitiveDependencies) {
			dep = DeepCopier.copy(dep);
			dep.ring -= (dependency.ring + 1);
			dependencies.add(dep);
		}
		try {
			console.debug(1, "=> caching solution {0}", scope);			
			moxiedata.setDependencies(scope, dependencies);
			// solution date is lastModified of POM
			moxiedata.setLastSolved(new Date(FileUtils.getLastModified(moxieCache.getArtifact(dependency, Constants.POM))));
			moxieCache.writeMoxieData(dependency, moxiedata);
		} catch (Exception e) {
			console.error(e, "Failed to cache {0} solution {1}", scope, dependency.getDetailedCoordinates());
		}
	}
	
	private void readProjectSolution() {
		if (!cache()) {
			// caching forbidden 
			return;
		}
		String coordinates = config.getPom().getCoordinates();
		Dependency projectAsDep = new Dependency(coordinates);
		if (projectAsDep.isSnapshot()) {
			// do not use cached solution for snapshots
			return;
		}
		MoxieData moxiedata = moxieCache.readMoxieData(projectAsDep);
		if (moxiedata.getLastSolved().getTime() == config.getProjectConfig().lastModified) {
			try {
				console.debug("reusing project solution {0}", config.getPom());				
				for (Scope scope : moxiedata.getScopes()) {
					Set<Dependency> dependencies = new LinkedHashSet<Dependency>(moxiedata.getDependencies(scope));
					console.debug(1, "{0} {1} dependencies", dependencies.size(), scope);
					solutions.put(scope, dependencies);
				}
			} catch (Exception e) {
				console.error(e, "Failed to read project solution {0}", projectAsDep.getDetailedCoordinates());
			}
		}
	}
	
	private void cacheProjectSolution() {
		Dependency projectAsDep = new Dependency(config.getPom().toString());
		MoxieData moxiedata = moxieCache.readMoxieData(projectAsDep);
		try {
			console.debug("caching project solution {0}", config.getPom());			
			for (Map.Entry<Scope, Set<Dependency>> entry : solutions.entrySet()) {
				moxiedata.setDependencies(entry.getKey(), entry.getValue());
			}
			moxiedata.setLastSolved(new Date(config.getProjectConfig().lastModified));
			moxieCache.writeMoxieData(projectAsDep, moxiedata);
		} catch (Exception e) {
			console.error(e, "Failed to cache project solution {0}", projectAsDep.getDetailedCoordinates());
		}
	}
	
	private File retrievePOM(Dependency dependency, Set<Dependency> retrieved) {
		if (!dependency.isMavenObject()) {
			return null;
		}
		if (StringUtils.isEmpty(dependency.version)) {
			return null;
		}
		if (retrieved != null && retrieved.contains(dependency)) {
			return null;
		}
		
		if (dependency.isMetaVersion()) {
			// Support VERSION RANGE, SNAPSHOT, RELEASE, and LATEST versions
			File metadataFile = moxieCache.getMetadata(dependency, Constants.XML);
			boolean updateRequired = !metadataFile.exists() || isUpdateMetadata();
			
			if (!updateRequired) {
				UpdatePolicy policy = config.getProjectConfig().updatePolicy;
				MoxieData moxiedata = moxieCache.readMoxieData(dependency);
				// we have metadata, check update policy
				if (UpdatePolicy.daily.equals(policy)) {
					// daily is a special case
					SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
					String mdate = df.format(moxiedata.getLastChecked());
					String today = df.format(new Date());
					updateRequired = !mdate.equals(today);
				} else {
					// always, never, interval
					long msecs = policy.mins*60*1000L;
					updateRequired = Math.abs(System.currentTimeMillis() - moxiedata.getLastChecked().getTime()) > msecs;
				}
				
				if (updateRequired) {
					console.debug(1, "{0} maven-metadata.xml is STALE according to {1} update policy", dependency.getManagementId(), policy.toString());
				} else {
					console.debug(1, "{0} maven-metadata.xml is CURRENT according to {1} update policy", dependency.getManagementId(), policy.toString());
				}
			}
			
			if (updateRequired && isOnline()) {
				// download artifact maven-metadata.xml
				console.debug(1, "locating maven-metadata.xml for {0}", dependency.getManagementId());
				for (Repository repository : config.getRepositories()) {
					if (!repository.isMavenSource()) {
						// skip non-Maven repositories
						continue;
					}
					if (!repository.isSource(dependency)) {
						// try to match origins
						continue;
					}
					metadataFile = repository.downloadMetadata(this, dependency);
					if (metadataFile != null && metadataFile.exists()) {
						// downloaded the metadata
						break;
					}
				}
				
				// reset last checked date for next update check
				// after we have resolved RELEASE, LATEST, or SNAPSHOT
				MoxieData moxiedata = moxieCache.readMoxieData(dependency);
				moxiedata.setLastChecked(new Date());
				moxieCache.writeMoxieData(dependency, moxiedata);
			} else {
				console.debug(1, "reading maven-metadata.xml for {0}", dependency.getManagementId());
			}
		}
		
		MoxieData moxiedata = moxieCache.readMoxieData(dependency);
		File pomFile = moxieCache.getArtifact(dependency, Constants.POM);
		if ((!pomFile.exists() || (dependency.isSnapshot() && moxiedata.isRefreshRequired())) && isOnline()) {
			// download the POM
			console.debug(1, "locating POM for {0}", dependency.getDetailedCoordinates());
			for (Repository repository : config.getRepositories(dependency)) {
				if (!repository.isMavenSource()) {
					// skip non-Maven repositories
					continue;
				}
				if (!repository.isSource(dependency)) {
					// try to match origins
					continue;
				}
				File retrievedFile = repository.download(this, dependency, Constants.POM);
				if (retrievedFile != null && retrievedFile.exists()) {
					pomFile = retrievedFile;
					break;
				}
			}
		}

		// Read POM
		if (pomFile.exists()) {

			// mark as retrieved so we do not re-retrieve
			if (retrieved != null) {
				retrieved.add(dependency);
			}
			
			// confirm we have the repository url in our repository list
			if (!StringUtils.isEmpty(moxiedata.getOrigin()) 
					&& !registeredUrls.contains(moxiedata.getOrigin())
					&& !moxiedata.getOrigin().startsWith("file:/")) {
				console.missingOriginRepository(moxiedata.getOrigin(), dependency);
			}

			try {
				Pom pom = null;
				try {
					pom = PomReader.readPom(moxieCache, pomFile);
					if (pom.hasParentDependency()) {
						// we already have the parent POM locally 
						Dependency parent = pom.getParentDependency();
						parent.ring = dependency.ring;
						retrievePOM(parent, retrieved);
					}
				} catch (MissingParentPomException e) {
					// traverse up the graph and retrieve parent POM
					Dependency parent = e.getParent();
					parent.ring = dependency.ring;
					retrievePOM(parent, retrieved);
					
					// Re-read this POM now that we have the parent.
					// This allows for resolving properties defined in the
					// parent POM on first retrieval.
					pom = PomReader.readPom(moxieCache, pomFile);
				}

				// traverse down the graph and retrieve all dependent POMs
				for (Scope scope : pom.getScopes()) {
					for (Dependency dep : pom.getDependencies(scope, dependency.ring + 1)) {
						retrievePOM(dep, retrieved);
					}
				}
			} catch (Exception e) {
				console.error(e);
			}
			return pomFile;
		}		
		return null;
	}
	
	/**
	 * Download an artifact from a local or remote artifact repository.
	 * 
	 * @param dependency
	 *            the dependency to download
	 * @return
	 */
	private File retrieveArtifact(Dependency dependency) {
		if (Constants.POM.equals(dependency.extension)) {
			// POM dependencies do not have other artifacts
			if (dependency.isSnapshot()) {
				// ... but we still have to purge old pom snapshots
				for (Repository repository : config.getRepositories(dependency)) {
					if (!repository.isSource(dependency)) {
						// dependency incompatible with repository
						console.debug(1, "{0} is not source of {1}, skipping", repository.name, dependency.getCoordinates());
						continue;
					}

					// purge snapshots for this dependency			
					moxieCache.purgeSnapshots(dependency, repository.purgePolicy);
				}
			}
			return null;
		}
		
		// standard artifact
		for (Repository repository : config.getRepositories(dependency)) {
			if (!repository.isSource(dependency)) {
				// dependency incompatible with repository
				console.debug(1, "{0} is not source of {1}, skipping", repository.name, dependency.getCoordinates());
				continue;
			}
			
			// Determine to download/update the dependency
			File artifactFile = moxieCache.getArtifact(dependency, dependency.extension);
			boolean downloadDependency = !artifactFile.exists();				
			if (!downloadDependency && dependency.isSnapshot()) {
				MoxieData moxiedata = moxieCache.readMoxieData(dependency);
				downloadDependency = moxiedata.isRefreshRequired();
				if (downloadDependency) {
					console.debug(1, "{0} is STALE according to {1}", dependency.getManagementId(), moxiedata.getOrigin());
				} else {
					console.debug(1, "{0} is CURRENT according to {1}", dependency.getManagementId(), moxiedata.getOrigin());
				}
			}
			
			if (downloadDependency && isOnline()) {
				// Download primary artifact (e.g. jar)
				artifactFile = repository.download(this, dependency, dependency.extension);
				
				if (artifactFile != null) {
					// Download sources artifact (e.g. -sources.jar)
					Dependency sources = dependency.getSourcesArtifact();
					repository.download(this, sources, sources.extension);

					// Download javadoc artifact (e.g. -javadoc.jar)
					Dependency javadoc = dependency.getJavadocArtifact();
					repository.download(this, javadoc, javadoc.extension);
				}
			}
			
			// purge snapshots for this dependency			
			moxieCache.purgeSnapshots(dependency, repository.purgePolicy);
			
			// artifact retrieved
			if (artifactFile != null) {
				return artifactFile;
			}
		}
		
		return null;
	}
	
	private void copyArtifact(Dependency dependency, File artifactFile) {
		// optionally copy artifact to project-specified folder
		if (artifactFile != null && artifactFile.exists()) {
			if (config.getProjectConfig().getDependencyDirectory() != null) {
				// copy jar
				File projectFile = config.getProjectConfig().getProjectDependencyArtifact(dependency);
				if (dependency.isSnapshot() || !projectFile.exists()) {
					console.debug(1, "copying {0} to {1}", artifactFile.getName(), projectFile.getParent());
					try {
						projectFile.getParentFile().mkdirs();
						FileUtils.copyFile(artifactFile, projectFile);
					} catch (IOException e) {
						throw new RuntimeException("Error writing to file " + projectFile, e);
					}
				}
				
				// copy source jar
				Dependency source = dependency.getSourcesArtifact();
				File sourceFile = moxieCache.getArtifact(source, source.extension);					
				File projectSourceFile = config.getProjectConfig().getProjectDependencySourceArtifact(dependency);
				if (sourceFile.exists() && (dependency.isSnapshot() || !projectSourceFile.exists())) {
					console.debug(1, "copying {0} to {1}", sourceFile.getName(), projectSourceFile.getParent());
					try {
						projectSourceFile.getParentFile().mkdirs();
						FileUtils.copyFile(sourceFile, projectSourceFile);
					} catch (IOException e) {
						throw new RuntimeException("Error writing to file " + projectSourceFile, e);
					}
				}
				
				// remove obsolete artifacts
				removeObsoleteArtifacts(dependency, projectFile.getParentFile());
				removeObsoleteArtifacts(source, projectSourceFile.getParentFile());
			}
		}
	}
	
	/**
	 * If we are maintaining project-local artifacts then make sure to remove
	 * obsolete versions of these local artifacts.  For example if we upgrade
	 * from Lucene 3.6.0 to 3.6.1 we should remove the 3.6.0 artifacts.  Or the
	 * reverse, if we are downgrading.
	 * 
	 * @param dependency
	 * @param folder
	 */
	private void removeObsoleteArtifacts(final Dependency dependency, final File folder) {
		File [] removals = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				String n = name.toLowerCase();
				String dep = dependency.artifactId.toLowerCase();
				if (n.startsWith(dep)) {
					dep += "-" + dependency.version.toLowerCase();
					if (!n.startsWith(dep)) {
						String suffix;
						if (!StringUtils.isEmpty(dependency.classifier)) {
							suffix = "-" + dependency.classifier;
						} else {
							suffix = "";
						}
						suffix += "." + dependency.extension;
						suffix = suffix.toLowerCase();
						if (n.endsWith(suffix)) {
							// isolate middle section - which may be just
							// a version OR may be part of the artifact id
							String v = n.substring(dependency.artifactId.length());
							v = v.substring(0, v.length() - suffix.length());
							if (v.length() == 0) {
								return false;
							}
							if (v.charAt(0) == '-') {
								// strip leading - for artifacts like:
								// wicket-auth-roles when we are trying to delete
								// the 'wicket' artifact but not the 'wicket-auth-roles'
								// artifact
								v = v.substring(1);
							}
							// grab first element of middle section
							// 1.2.3-SNAPSHOT = 1.2.3
							// 1.2.3 = 1.2.3
							// core-1.2.3-SNAPSHOT = core
							String v0 = v.split("-")[0];
							ArtifactVersion version = new ArtifactVersion(v0);
							if (version.getQualifier() != null && version.getQualifier().equalsIgnoreCase(v0)) {
								// this file is a different artifact, not a different
								// version of the same artifact
								console.debug("keeping related artifact {0} in {1} when resolving {2}", n, folder, dependency.getCoordinates());
							} else {
								// the middle section is a version number and not
								// an artifact id fragment AND a version number
								console.debug("deleting obsolete artifact {0} from {1} when resolving {2}", n, folder, dependency.getCoordinates());
								console.debug("qualifier={0}, dep={1}", version.getQualifier(), dep);
								return true;
							}
						}
					}
				}
				return false;
			}
		});
		
		// delete any matches
		if (removals != null) {
			for (File file : removals) {
				file.delete();
			}
		}
	}
	
	/**
	 * Downloads an internal dependency needed for runtime operation of Moxie.
	 * This dependency is automatically loaded by the classloader.
	 * 
	 * @param dependencies
	 */
	public void loadDependency(Dependency... dependencies) {		
		// solve the classpath solution for the Moxie runtime dependencies
		Pom pom = new Pom();
		Set<Dependency> retrieved = new HashSet<Dependency>();
		for (Dependency dependency : dependencies) {
			resolveAliasedDependencies(dependency);
			retrievePOM(dependency, retrieved);
			pom.addDependency(dependency, Scope.compile);
		}
		Set<Dependency> solution = new LinkedHashSet<Dependency>();
		for (Dependency dependency : pom.getDependencies(Scope.compile, Constants.RING1)) {
			solution.add(dependency);
			solution.addAll(solve(Scope.compile, dependency));
		}		
		for (Dependency dependency : solution) {
			retrieveArtifact(dependency);
		}

		// load dependency onto executing classpath from Moxie cache
		Class<?>[] PARAMETERS = new Class[] { URL.class };
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		for (Dependency dependency : solution) {
			File file = moxieCache.getArtifact(dependency, dependency.extension);
			if (file.exists()) {
				try {
					URL u = file.toURI().toURL();
					Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
					method.setAccessible(true);
					method.invoke(sysloader, new Object[] { u });
				} catch (Throwable t) {
					console.error(t, "Error, could not add {0} to system classloader", file.getPath());					
				}
			}
		}
	}
	
	/**
	 * Solves the dependency solution for the specified artifacts and scope.
	 * 
	 * @param scope
	 * @param dependencies
	 * @return a list of artifacts
	 */
	public List<File> solve(Scope scope, Dependency... deps) {		
		Pom pom = new Pom();
		Set<Dependency> retrieved = new HashSet<Dependency>();
		for (Dependency dep : deps) {
			resolveAliasedDependencies(dep);
			getConsole().dependency(dep);
			retrievePOM(dep, retrieved);
			pom.addDependency(dep, scope);
		}

		Set<Dependency> solution = new LinkedHashSet<Dependency>();
		for (Dependency dependency : pom.getDependencies(scope, Constants.RING1)) {
			solution.add(dependency);
			solution.addAll(solve(scope, dependency));
		}
		List<File> artifacts = new ArrayList<File>();
		for (Dependency dependency : solution) {
			File artifact = retrieveArtifact(dependency);
			if (artifact != null) {
				artifacts.add(artifact);
			}
		}
		return artifacts;
	}
	
	public List<File> getClasspath(Scope scope) {
		return getClasspath(scope, null);
	}
	
	public List<File> getClasspath(Scope scope, String tag) {
		if (classpaths.containsKey(scope) && StringUtils.isEmpty(tag)) {
			// return scoped classpath for non-tag query
			return classpaths.get(scope);
		}
		
		File projectFolder = null;
		if (config.getProjectConfig().getDependencyDirectory() != null
				&& config.getProjectConfig().getDependencyDirectory().exists()) {
			projectFolder = config.getProjectConfig().getDependencyDirectory();
		}
		if (StringUtils.isEmpty(tag)) {
			console.debug("solving {0} classpath", scope);
		} else {
			console.debug("solving {0} {1} classpath", tag, scope);
		}
		Set<Dependency> dependencies = solve(scope);
		List<File> jars = new ArrayList<File>();
		for (Dependency dependency : dependencies) {
			if (!StringUtils.isEmpty(tag)) {
				// filter deps by tag
				if (!dependency.tags.contains(tag.toLowerCase())) {
					continue;
				}
			}
			File jar;
			if (dependency instanceof SystemDependency) {
				SystemDependency sys = (SystemDependency) dependency;				
				jar = new File(sys.path);
			} else {
				jar = moxieCache.getArtifact(dependency, dependency.extension); 
				if (projectFolder != null) {
					File pJar = config.getProjectConfig().getProjectDependencyArtifact(dependency);
					if (pJar.exists()) {
						jar = pJar;
					}
				}
			}
			jars.add(jar);
		}
		if (StringUtils.isEmpty(tag)) {
			// store scoped classpath if not filtering by tag
			classpaths.put(scope, jars);
		}
		return jars;
	}
}
