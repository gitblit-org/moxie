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
package org.moxie.proxy;

import java.io.File;
import java.io.FilenameFilter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.moxie.IMavenCache;
import org.moxie.Pom;
import org.moxie.PomReader;
import org.moxie.RemoteRepository;
import org.moxie.proxy.connection.ProxyConnectionServer;
import org.moxie.proxy.resources.ArtifactsResource;
import org.moxie.proxy.resources.AtomResource;
import org.moxie.proxy.resources.RecentResource;
import org.moxie.proxy.resources.RootResource;
import org.moxie.proxy.resources.SearchResource;
import org.moxie.utils.StringUtils;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.Variable;

import freemarker.template.Configuration;

public class MoxieProxy extends Application {

	private final ProxyConfig config;

	private final LuceneExecutor lucene;
	
	private final ProxyConnectionServer proxy;
	
	private final ScheduledExecutorService executorService;

	private Configuration configuration;
	
	public MoxieProxy(ProxyConfig config) {
		this.config = config;
		this.lucene = new LuceneExecutor(config);
		this.proxy = new ProxyConnectionServer(config, lucene);
		this.executorService = Executors.newSingleThreadScheduledExecutor();
	}
	
	@Override
	public Restlet createInboundRoot() {
		Context context = getContext();
		
		// initialize Freemarker templates		
		configuration = new Configuration();		
		configuration.setTemplateLoader(new ContextTemplateLoader(context, "clap://class/templates"));
		configuration.setDateFormat(config.getDateFormat());

		// map the routes
		Router router = new Router(context);
		
		// map the local repository folders
		for (String folder : config.getLocalRepositories()) {
			attachMaven2(context, router, folder);
			attachBrowsing(context, router, folder);
		}

		// map the remote repository folders
		for (RemoteRepository repository : config.getRemoteRepositories()) {
			attachMaven2(context, router, repository.id);
			attachBrowsing(context, router, repository.id);
		}

		// Search artifacts
		router.attach("/search", SearchResource.class);

		// Recent artifacts
		router.attach("/recent/{repository}", RecentResource.class);
		router.attach("/recent", RecentResource.class);

		// Atom artifacts feed
		router.attach("/atom/{repository}", AtomResource.class);
		router.attach("/atom", AtomResource.class);
		
		// Root 
		router.attach("/", RootResource.class);
		router.attach("", RootResource.class);

		// static resources
		Directory dir = new Directory(context, "clap://class");
		dir.setListingAllowed(true);
		dir.setDeeplyAccessible(true);
		router.attach("/", dir);

		if (config.getProxyPort() > 0) {
			getLogger().info(MessageFormat.format(Constants.MESSAGE_STARTUP, "PROXY", config.getProxyPort()));
		}
		getLogger().info(MessageFormat.format("Moxie root is {0}", config.getMoxieRoot()));
		getLogger().info(MessageFormat.format("{0} is ready.", Constants.getName()));
		return router;
	}
	
	/**
	 * Attaches a folder for Maven 2 serving
	 * @param context
	 * @param router
	 * @param folder
	 */
	void attachMaven2(Context context, Router router, String folder) {
		// Maven 2 resource
		Directory m2 = new Directory(context, config.getArtifactRoot(folder).toURI().toString());
		m2.setListingAllowed(true);
		m2.setDeeplyAccessible(true);
		router.attach("/m2/" + folder, m2);
		
		config.getArtifactRoot(folder).mkdirs();
	}
	
	/**
	 * Attaches a folder for web browsing
	 * @param context
	 * @param router
	 * @param folder
	 */
	void attachBrowsing(Context context, Router router, String folder) {
		TemplateRoute route = router.attach("/" + folder + "/{path}", ArtifactsResource.class);
		Map<String, Variable> variables = route.getTemplate().getVariables();
		variables.put("path", new Variable(Variable.TYPE_URI_PATH));		
		router.attach("/" + folder, ArtifactsResource.class);
	}
	
	/**
	 * Start the Moxie Proxy app.
	 * 
	 * @throws Exception
	 */
	@Override
	public void start() throws Exception {		
		super.start();
		
        // reindex all artifacts
        lucene.reindex();

        // setup asynchronous incremental updates
        //
        // We could sychronously update indexes on pom retrieval BUT we run
        // into complications with parsing parent poms which we might not have
        // at index time.  So instead we queue poms to index and process
        // them after a modest delay most likely sufficient to have realized the
        // retrieval of the parent poms.
		executorService.scheduleAtFixedRate(lucene, 2, 2, TimeUnit.MINUTES);

		// start the proxy server
		if (config.isProxyEnabled()) {
			proxy.start();
		}
	}
	
	/**
	 * Stop the Moxie Proxy app.
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {
		super.stop();
		
		executorService.shutdown();
		proxy.shutdown();
	}
	
	public Configuration getFreemarkerConfiguration() {
		return configuration;
	}
	
	public ProxyConfig getProxyConfig() {
		return config;
	}
	
	public Pom readPom(File folder) {
		if (folder.isDirectory()) {
			File [] poms = folder.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(org.moxie.Constants.POM);
				}
			});
			if (poms == null || poms.length == 0) {
				return null;
			}
			File pomFile = poms[0];
			IMavenCache cache = config.getMoxieCache();
			return PomReader.readPom(cache, pomFile);
		}
		return null;
	}
		
	/**
	 * Searches the specified repositories for the given text or query
	 * 
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param page
	 *            the page number to retrieve. page is 1-indexed.
	 * @param pageSize
	 *            the number of elements to return for this page
	 * @return a list of SearchResults in order from highest to the lowest score
	 * 
	 */
	public List<SearchResult> search(String query, int page, int pageSize) {
		return lucene.search(query, page, pageSize, getAccessibleRepositories());
	}
	
	public List<SearchResult> getRecentArtifacts(String repository, int page, int pageSize) {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, 1);
		String to = df.format(c.getTime());
		c.add(Calendar.DATE, -180);
		String from = df.format(c.getTime());

		String query = MessageFormat.format("date:[{0} TO {1}] AND NOT packaging:pom", from, to);
		
		List<SearchResult> list;
		if (StringUtils.isEmpty(repository)) {
			// all accessible repositories
			list = lucene.search(query, page, pageSize, getAccessibleRepositories());
		} else {
			// specified repository
			list = lucene.search(query, page, pageSize, repository);
		}
		Collections.sort(list);
		return list;
	}
	
	public int getArtifactCount(String repository) {
		List<SearchResult> list = lucene.search("*", 1, 0, repository);
		return list.size();
	}
	
	List<String> getAccessibleRepositories() {
		// TODO filter repositories by login
		List<String> list = new ArrayList<String>();
		list.addAll(config.getLocalRepositories());
		for (RemoteRepository repository : config.getRemoteRepositories()) {
			list.add(repository.id);
		}
		return list;
	}
	
	boolean authenticate(String username, String password) {
		// TODO authenticate here
		return true;
	}
}
