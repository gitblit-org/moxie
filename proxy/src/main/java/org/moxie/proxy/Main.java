package org.moxie.proxy;

import java.io.File;
import java.io.FilenameFilter;
import java.text.MessageFormat;
import java.util.Map;
import java.util.logging.Level;

import org.moxie.MavenCache;
import org.moxie.Pom;
import org.moxie.PomReader;
import org.moxie.proxy.resources.ArtifactsResource;
import org.moxie.proxy.resources.RootResource;
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

public class Main extends Application {

	private final Config config;

	private Configuration configuration;

	public Main(Config config) {
		this.config = config;
	}
	
	@Override
	public Restlet createInboundRoot() {
		Context context = getContext();
		
		// initialize Freemarker templates		
		configuration = new Configuration();		
		configuration.setTemplateLoader(new ContextTemplateLoader(context, "clap://class/templates"));

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
	
	public Configuration getConfiguration() {
		return configuration;
	}
	
	public Config getProxyConfig() {
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
			MavenCache cache = config.getMavenCache(folder);
			return PomReader.readPom(cache, pomFile);
		}
		return null;
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
		
		Config config = new Config();
		
		// set defaults from command-line
		
		// parse config file, allow override
		config.parse(params.moxieConfig);

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
		c.setStatusService(new MxStatusService(c.getContext()));

		// get the default virtual host
		VirtualHost host = c.getDefaultHost();		

		Main app = new Main(config);

		// Guard Moxie Proxy with BASIC authentication.
		MxAuthenticator guard = new MxAuthenticator(app);
		host.attachDefault(guard);
		guard.setNext(app);		

		// start the http server
		c.start();
		
		// start the proxy server
        ProxyServer proxy = new ProxyServer(config);
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
