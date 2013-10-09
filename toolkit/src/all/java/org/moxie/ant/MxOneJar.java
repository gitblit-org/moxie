package org.moxie.ant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.Manifest.Attribute;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.ZipFileSet;
import org.moxie.Build;
import org.moxie.MoxieException;
import org.moxie.Scope;
import org.moxie.Toolkit;
import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.onejar.OneJarTask;
import org.moxie.utils.StringUtils;

public class MxOneJar extends OneJarTask {

	private Console console;
	private List<ZipDependencies> dependencies = new ArrayList<ZipDependencies>();
	private ClassSpec mainclass = null;

	boolean includeResources = true;
	boolean packageSources;
	String includes;
	String excludes;
	String tag;
	String classifier;
	Boolean showtitle;

	public boolean getIncludesresources() {
		return includeResources;
	}

	public void setIncluderesources(boolean copy) {
		this.includeResources = copy;
	}

	public String getIncludes() {
		return includes;
	}

	@Override
	public void setIncludes(String includes) {
		this.includes = includes;
	}

	public String getExcludes() {
		return excludes;
	}

	@Override
	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getClassifier() {
		return classifier;
	}

	public void setClassifier(String classifier) {
		this.classifier = classifier;
	}

	public boolean getPackagesources() {
		return packageSources;
	}

	public void setPackagesources(boolean sources) {
		this.packageSources = sources;
	}

	public void setShowtitle(boolean value) {
		this.showtitle = value;
	}

	public boolean isShowTitle() {
		return showtitle == null || showtitle;
	}

	public ZipDependencies createDependencies() {
		ZipDependencies deps = new ZipDependencies();
		dependencies.add(deps);
		return deps;
	}

	/**
	 * Builds a <mainclass> element.
	 *
	 * @return A <mainclass> element.
	 */
	public ClassSpec createMainclass() {
		if (mainclass == null) {
			ClassSpec cs = new ClassSpec(getProject());
			mainclass = cs;
			return cs;
		}
		throw new MoxieException("Can only specify one main class");
	}


	@Override
	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		console = build.getConsole();
		if (main == null && mainJars == null) {
			manifestSet = true;
			Main main = new Main();
			main.setProject(getProject());

			if (excludes == null) {
				excludes = Toolkit.DEFAULT_CODE_EXCLUDES;
			}

			// compiled output of this project
			File outputFolder = build.getConfig().getOutputDirectory(Scope.compile);
			ZipFileSet outputSet = new ZipFileSet();
			outputSet.setProject(getProject());
			outputSet.setDir(outputFolder);
			if (includes != null) {
				outputSet.setIncludes(includes);
			}
			outputSet.setExcludes(excludes);
			main.addFileSet(outputSet);

			// add the output and resource folders of modules
			for (Build module : build.getSolver().getLinkedModules()) {
				ZipFileSet projectOutputSet = new ZipFileSet();
				projectOutputSet.setProject(getProject());
				File dir = module.getConfig().getOutputDirectory(Scope.compile);
				projectOutputSet.setDir(dir);
				if (includes != null) {
					projectOutputSet.setIncludes(includes);
				}
				projectOutputSet.setExcludes(excludes);
				main.addFileSet(projectOutputSet);

				if (includeResources) {
					// add linked module resources
					for (File resDir : module.getConfig().getResourceDirectories(Scope.compile)) {
						ZipFileSet resSet = new ZipFileSet();
						resSet.setProject(getProject());
						resSet.setDir(resDir);
						resSet.setExcludes(Toolkit.DEFAULT_RESOURCE_EXCLUDES);
						main.addFileSet(resSet);
					}
				}
			}

			// resource directories
			if (includeResources) {
				for (File dir : build.getConfig().getResourceDirectories(Scope.compile, tag)) {
					ZipFileSet set = new ZipFileSet();
					set.setProject(getProject());
					set.setDir(dir);
					set.setExcludes(Toolkit.DEFAULT_RESOURCE_EXCLUDES);
					main.addFileSet(set);
				}
			}

			createDependencies().setTag(tag);

			if (getDestFile() == null) {
				setDestFile(build.getBuildArtifact(classifier));
			}

			File destFile = getDestFile();

			if (destFile.getParentFile() != null) {
				destFile.getParentFile().mkdirs();
			}

			if (mainclass == null) {
				String mc = build.getConfig().getProjectConfig().getMainclass();
				if (!StringUtils.isEmpty(mc)) {
					createMainclass().setName(mc);
				}
			}

			addMain(main);
		}

		if (mainclass != null) {
			String mc = mainclass.getName().replace('/', '.');
			if (mc.endsWith(".class")) {
				mc = mc.substring(0, mc.length() - ".class".length());
			}
			setOneJarMainClass(mc);
			manifestSet = true;
		}

		for (ZipDependencies deps : dependencies) {
			for (File jar : build.getSolver().getClasspath(deps.getScope(), deps.getTag())) {
				ZipFileSet fs = new ZipFileSet();
				fs.setProject(getProject());
				if (!StringUtils.isEmpty(deps.getPrefix())) {
					throw new MoxieException("Can not specify custom dependencies prefix for mx:onejar!");
				}
				fs.setPrefix("lib/");
				fs.setDir(jar.getParentFile());
				fs.setIncludes(jar.getName());
				addZipfileset(fs);
			}
		}

		if (isShowTitle()) {
			console.title(getClass(), getDestFile().getName());
		}

		long start = System.currentTimeMillis();

		super.execute();

		console.log(1, "{0} KB, generated in {1} ms", (getDestFile().length()/1024), System.currentTimeMillis() - start);

		/*
		 * Build sources jar
		 */
		if (packageSources) {
			String name = getDestFile().getName();
			if (!StringUtils.isEmpty(classifier)) {
				// replace the classifier with "sources"
				name = name.replace(classifier, "sources");
			} else {
				// append -sources to the filename before the extension
				name = name.substring(0, name.lastIndexOf('.')) + "-sources" + name.substring(name.lastIndexOf('.'));
			}
			File sourcesFile = new File(getDestFile().getParentFile(), name);
			if (sourcesFile.exists()) {
				sourcesFile.delete();
			}

			Jar jar = new Jar();
			jar.setTaskName(getTaskName());
			jar.setProject(getProject());

			// set the destination file
			jar.setDestFile(sourcesFile);

			List<File> folders = build.getConfig().getSourceDirectories(Scope.compile, tag);
			for (File folder : folders) {
				FileSet srcSet = new FileSet();
				srcSet.setProject(getProject());
				srcSet.setDir(folder);
				srcSet.setIncludes("**/*.java");
				jar.addFileset(srcSet);

				// include source folder resources
				FileSet resSet = new FileSet();
				resSet.setProject(getProject());
				resSet.setDir(folder);
				resSet.setExcludes(excludes);
				jar.addFileset(resSet);
			}

			if (includeResources) {
				for (File dir : build.getConfig().getResourceDirectories(Scope.compile, tag)) {
					FileSet set = new FileSet();
					set.setDir(dir);
					set.setExcludes(Toolkit.DEFAULT_RESOURCE_EXCLUDES);
					jar.addFileset(set);
				}
			}

			// set the source jar manifest
			try {
				Manifest mft = new Manifest();
				configureManifest(mft);
				jar.addConfiguredManifest(mft);
			} catch (ManifestException e) {
				console.error(e);
			}

			start = System.currentTimeMillis();
			jar.execute();
			console.log(1, "{0} KB, generated in {1} ms", (sourcesFile.length()/1024), System.currentTimeMillis() - start);
		}
	}

	@Override
	protected void configureManifest(Manifest manifest) {
		// set manifest entries from Moxie metadata
		Manifest mft = new Manifest();
		setManifest(mft, "Manifest-Version", "1.0");
		setManifest(mft, "Created-By", "Moxie v" + Toolkit.getVersion());
		setManifest(mft, "Build-Jdk", System.getProperty("java.version"));
		setManifest(mft, "Build-Date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

		setManifest(mft, "Implementation-Title", Key.name);
		setManifest(mft, "Implementation-Vendor", Key.organization);
		setManifest(mft, "Implementation-Vendor-Id", Key.groupId);
		setManifest(mft, "Implementation-Vendor-URL", Key.url);
		setManifest(mft, "Implementation-Version", Key.version);

		setManifest(mft, "Bundle-Name", Key.name);
		setManifest(mft, "Bundle-SymbolicName", Key.artifactId);
		setManifest(mft, "Bundle-Version", Key.version);
		setManifest(mft, "Bundle-Vendor", Key.organization);

		setManifest(mft, "Maven-Url", Key.mavenUrl);
		setManifest(mft, "Commit-Id", Key.commitId);

		if (main.getManifest() == null) {
			// set the manifest of the embedded main jar
			try {
				File file = File.createTempFile("moxie-", ".mft");
				FileOutputStream os = new FileOutputStream(file);
				PrintWriter writer = new PrintWriter(os);
				mft.write(writer);
				writer.flush();
				os.close();
				main.setManifest(file);
			} catch (FileNotFoundException e) {
				console.error(e, "Failed to set main.jar manifest!");
			} catch (IOException e) {
				console.error(e, "Failed to set main.jar manifest!");
			}
		}

		setManifest(mft, "Premain-Class", "org.moxie.onejar.Boot");

		try {
			manifest.merge(mft, true);
		} catch (ManifestException e) {
			console.error(e, "Failed to configure manifest!");
		}
	}

	void setManifest(Manifest man, String key, Key prop) {
		// try project property
		String value = getProject().getProperty(prop.projectId());
		if (value == null) {
			return;
		}
		if (!StringUtils.isEmpty(value)) {
			setManifest(man, key, value);
		}
	}

	void setManifest(Manifest man, String key, String value) {
		if (!StringUtils.isEmpty(value)) {
			try {
				man.addConfiguredAttribute(new Attribute(key, value));
			} catch (ManifestException e) {
				console.error(e, "Failed to set manifest attribute \"{0}\"!", key);
			}
		}
	}
}
