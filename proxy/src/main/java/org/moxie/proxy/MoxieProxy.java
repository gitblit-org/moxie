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
import java.util.logging.Level;

import org.moxie.IMavenCache;
import org.moxie.Pom;
import org.moxie.PomReader;
import org.moxie.RemoteRepository;
import org.moxie.proxy.connection.ProxyConnectionServer;
import org.moxie.proxy.resources.ArtifactsResource;
import org.moxie.proxy.resources.AtomResource;
import org.moxie.proxy.resources.RootResource;
import org.moxie.proxy.resources.SearchResource;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.Variable;
import org.restlet.routing.VirtualHost;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import freemarker.template.Configuration;

public class MoxieProxy extends Application {

	private final ProxyConfig config;

	private final LuceneExecutor lucene;

	private Configuration configuration;
	
	public MoxieProxy(ProxyConfig config) {
		this.config = config;
		this.lucene = new LuceneExecutor(config);
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

		// Search
		router.attach("/search", SearchResource.class);
		
		// Atom feed
		router.attach("/atom", AtomResource.class);
		
		// Root 
		router.attach("/", RootResource.class);
		router.attach("", RootResource.class);

		// static resources
		Directory dir = new Directory(context, "clap://class");
		dir.setListingAllowed(true);
		dir.setDeeplyAccessible(true);
		router.attach("/", dir);

		getLogger().info(MessageFormat.format("Serving http on port {0,number,#####}.", config.getHttpPort()));
		if (config.getProxyPort() > 0) {
			getLogger().info(MessageFormat.format("Proxying on port {0,number,#####}.", config.getProxyPort()));
		}
		getLogger().info(MessageFormat.format("Root is {0}", config.getMoxieRoot()));
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
			IMavenCache cache = config.getMavenCache(folder);
			return PomReader.readPom(cache, pomFile);
		}
		return null;
	}
	
	void startLucene() {
	    // start the Lucene indexer
        lucene.run();
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
	
	public List<SearchResult> getRecentArtifacts(int page, int pageSize) {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, 1);
		String to = df.format(c.getTime());
		c.add(Calendar.DATE, -180);
		String from = df.format(c.getTime());

		String query = MessageFormat.format("date:[{0} TO {1}] AND NOT packaging:pom", from, to);
		List<SearchResult> list = search(query, page, pageSize);
		Collections.sort(list);
		return list;
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
	
	public static void main(String[] args) throws Exception {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (Exception e) {
			e.printStackTrace();
			jc.usage();
			System.exit(-1);
		}
		
		ProxyConfig config = new ProxyConfig();
				
		// parse config file, allow override
		if (params.moxieConfig.exists()) {
			config.parse(params.moxieConfig);
		} else {
			// default to user directory
			config.setUserDefaults();			
		}

		// override defaults from command-line
		if (params.httpPort != null) {
			config.setHttpPort(params.httpPort);
		}
		if (params.proxyPort != null) {
			config.setProxyPort(params.proxyPort);
		}
		if (params.accesslog != null) {
			config.setAccessLog(params.accesslog);
		}
		if (params.moxieRoot != null) {
			config.setMoxieRoot(params.moxieRoot);
		}

		Engine.setRestletLogLevel(Level.INFO);

		Component c = new Component();
		
		// turn off Restlet url access logging
		c.getLogService().setEnabled(config.getAccessLog());

		// create a Restlet server
		c.getServers().add(Protocol.HTTP, config.getHttpPort());

		// add client classpath protocol to enable resource loading from the jar
		c.getClients().add(Protocol.CLAP);

		// add client file protocol to enable serving artifacts from filesystem
		c.getClients().add(Protocol.FILE);

		// override the default error pages
		c.setStatusService(new ErrorStatusService(c.getContext()));

		// get the default virtual host
		VirtualHost host = c.getDefaultHost();		

		MoxieProxy app = new MoxieProxy(config);

		// Guard Moxie Proxy with BASIC authentication.
		Authenticator guard = new Authenticator(app);
		host.attachDefault(guard);
		guard.setNext(app);		

		// start the http server
		c.start();
		
        // start the Lucene indexer
        app.startLucene();

		// start the proxy server
        ProxyConnectionServer proxy = new ProxyConnectionServer(config);
        proxy.handleRequests();
	}

	/**
	 * JCommander Parameters class.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--httpPort" }, description = "port for serving web interface", required = false)
		public Integer httpPort;

		@Parameter(names = { "--proxyPort" }, description = "port for processing proxy requests", required = false)
		public Integer proxyPort;

		@Parameter(names = { "--accesslog" }, description = "log all url requests", required = false)
		public Boolean accesslog;

		@Parameter(names = { "--root" }, description = "folder for Moxie metadata and artifacts", required = false)
		public File moxieRoot;

		@Parameter(names = { "--config" }, description = "config file for Moxie Proxy", required = false)
		public File moxieConfig = new File("proxy.moxie");

	}
}
