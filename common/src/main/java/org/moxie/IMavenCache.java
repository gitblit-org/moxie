package org.moxie;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

public abstract class IMavenCache {
	
	protected Logger logger;
	
	public abstract File getRootFolder();
	
	public abstract Collection<File> getFiles(String extension);

	public abstract File getArtifact(Dependency dep, String ext);

	public abstract File writeArtifact(Dependency dep, String ext, String content);

	public abstract File writeArtifact(Dependency dep, String ext, byte[] content);

	public abstract File getMetadata(Dependency dep, String ext);

	public abstract File writeMetadata(Dependency dep, String ext, String content);

	public abstract File writeMetadata(Dependency dep, String ext, byte[] content);
	
	public void setLogger(Logger logger) {
		this.logger = logger;
	}
	
	protected Dependency resolveRevision(Dependency dependency) {
		if ((dependency.isSnapshot() && StringUtils.isEmpty(dependency.revision))
				|| dependency.version.equalsIgnoreCase(Constants.RELEASE)
				|| dependency.version.equalsIgnoreCase(Constants.LATEST)
				|| dependency.isRangedVersion()) {
			// Support VERSION RANGE, SNAPSHOT, RELEASE and LATEST versions
			File metadataFile = getMetadata(dependency, Constants.XML);
			
			// read SNAPSHOT, LATEST, or RELEASE from metadata
			if (metadataFile != null && metadataFile.exists()) {
				Metadata metadata = MetadataReader.readMetadata(metadataFile);
				String version;
				String revision;
				if (Constants.RELEASE.equalsIgnoreCase(dependency.version)) {
					// RELEASE
					version = metadata.release;
					revision = version;
				} else if (Constants.LATEST.equalsIgnoreCase(dependency.version)) {
					// LATEST
					version = metadata.latest;
					revision = version;
				} else if (dependency.isSnapshot()) {
					// SNAPSHOT
					version = dependency.version;
					revision = metadata.getSnapshotRevision();
				} else {
					// VERSION RANGE
					version = metadata.resolveRangedVersion(dependency.version);
					revision = version;
				}
				
				dependency.version = version;
				dependency.revision = revision;
			}
		}

		// standard release
		return dependency;
	}
	
	public void purgeSnapshots(Dependency dep, PurgePolicy policy) {
		if (!dep.isSnapshot()) {
			return;
		}
		File metadataFile = getMetadata(dep, Constants.XML);
		if (metadataFile == null || !metadataFile.exists()) {
			return;
		}
		Metadata metadata = MetadataReader.readMetadata(metadataFile);
		List<String> purgedRevisions = metadata.purgeSnapshots(policy);
		if (purgedRevisions.size() > 0) {
			if (logger != null) {
				logger.debug("purging old snapshots of " + dep.getCoordinates());
			}
			for (String revision : purgedRevisions) {
				Dependency old = DeepCopier.copy(dep);
				old.revision = revision;
				purgeArtifacts(old, false);
			}
			// write purged metadata
			FileUtils.writeContent(metadataFile, metadata.toXML());

			// if this dependency has a parent, purge that too
			File pomFile = getArtifact(dep, Constants.POM);
			Pom pom = PomReader.readPom(this, pomFile);
			if (pom.hasParentDependency()) {
				Dependency parent = pom.getParentDependency();
				parent.setOrigin(dep.getOrigin());
				purgeSnapshots(parent, policy);
			}
		}
	}
	
	public void purgeArtifacts(Dependency dep, boolean includeDependencies) {
		String identifier = dep.version;
		if (dep.isSnapshot()) {
			identifier = dep.revision;
		}
		File artifact = getArtifact(dep, dep.extension);
		File folder = artifact.getParentFile();
		if (folder == null || !folder.exists()) {
			if (logger != null) {
				logger.debug(1, "! skipping non existent folder " + folder);
			}
			return;
		}
		File [] files = folder.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isFile() && file.getName().contains(identifier)) {
				if (logger != null) {
					logger.debug(1, "- " + file.getName());
				}
				file.delete();
			}
		}
	}
	
	/**
	 * Generates a POM index or list using the specified template.
	 *  
	 * @param pomTemplate
	 * @param separator separates pom entries
	 * @return the index/list
	 */
	public String generatePomIndex(String pomTemplate, String separator) {
		List<Pom> poms = readAllPoms();
		Collections.sort(poms);
		StringBuilder sb = new StringBuilder();		
		for (Pom pom : poms) {
			String artifact = pomTemplate;
			artifact = artifact.replace("${artifact.name}", pom.getName() == null ? pom.getArtifactId() : pom.getName());
			artifact = artifact.replace("${artifact.description}", pom.getDescription() == null ? "" : pom.getDescription());
			artifact = artifact.replace("${artifact.groupId}", pom.getGroupId());
			artifact = artifact.replace("${artifact.artifactId}", pom.getArtifactId());
			artifact = artifact.replace("${artifact.version}", pom.getVersion());
			
			Dependency dep = new Dependency(pom.getCoordinates());
			dep.extension = pom.getExtension();
			resolveRevision(dep);
			
			artifact = artifact.replace("${artifact.date}", getLastModified(dep));
			artifact = artifact.replace("${artifact.pom}", getMavenPath(dep.getPomArtifact()));
			artifact = artifact.replace("${artifact.package}", getMavenPath(dep));
			artifact = artifact.replace("${artifact.sources}", getMavenPath(dep.getSourcesArtifact()));
			artifact = artifact.replace("${artifact.javadoc}", getMavenPath(dep.getJavadocArtifact()));
			artifact = artifact.replace("${artifact.packageSize}", getArtifactSize(dep));
			artifact = artifact.replace("${artifact.sourcesSize}", getArtifactSize(dep.getSourcesArtifact()));
			artifact = artifact.replace("${artifact.javadocSize}", getArtifactSize(dep.getJavadocArtifact()));
			sb.append(artifact);
			sb.append(separator);
		}
		// trim trailing separator
		sb.setLength(sb.length() - separator.length());
		return sb.toString();
	}
	
	/**
	 * Reads the prefixes index.
	 * 
	 * @return a prefixes index.
	 */
	public Set<String> getPrefixes() {
		return readPrefixesIndex(new File(getRootFolder(), Constants.PREFIXES));
	}

	/**
	 * Reads the specified prefix index.
	 *  
	 * @param file
	 * @return the prefixes
	 */
	protected Set<String> readPrefixesIndex(File file) {
		Set<String> prefixes = new TreeSet<String>();
		if (file.exists()) {
			Scanner scanner = null;
			try {
				scanner = new Scanner(file);
				while (scanner.hasNext()) {
					prefixes.add(scanner.next());
				}
			} catch (Exception e) {
			} finally {
				if (scanner != null) {
					scanner.close();
				}
			}
		}
		return prefixes;
	}

	/**
	 * Creates/updates a prefixes index used by smart maven clients to do
	 * automatic dependency routing.
	 *  
	 * @return the index file
	 */
	public File updatePrefixesIndex() {
		return updatePrefixesIndex(getRootFolder());
	}
	
	/**
	 * Creates/updates a prefixes index used by smart Maven clients to do
	 * automatic dependency routing.
	 * 
	 * https://issues.sonatype.org/browse/NEXUS-5472
	 * https://github.com/restlet/restlet-framework-java/issues/449
	 *  
	 * @param dir directory to index
	 * @return the index file
	 */
	protected File updatePrefixesIndex(File dir) {
		File file = new File(dir, Constants.PREFIXES);
		Set<String> prefixes = readPrefixesIndex(file);
		
		// generate index first from discovered poms
		List<Pom> poms = readAllPoms(dir);
		Collections.sort(poms);
		for (Pom pom : poms) {
			String prefix = pom.getPrefix();
			prefixes.add(prefix);
		}
		
		// always add the .meta directory
		prefixes.add("/.meta");

		return writePrefixes(file, prefixes);
	}
	
	/**
	 * Writes the prefixes index.
	 * 
	 * @return a prefixes index file
	 */
	public File writePrefixes(Set<String> prefixes) {
		File file = new File(getRootFolder(), Constants.PREFIXES);
		return writePrefixes(file, prefixes);
	}

	/**
	 * Writes the prefixes index.
	 * 
	 * @param file the target file
	 * @param prefixes the set of prefixes
	 * 
	 */
	protected File writePrefixes(File file, Set<String> prefixes) {
		// create flat index content
		StringBuilder sb = new StringBuilder();
		for (String prefix : prefixes) {
			sb.append(prefix).append('\n');
		}

		// write the index file
		FileUtils.writeContent(file, sb.toString());
		return file;
	}
	
	private String getMavenPath(Dependency dep) {
		String path = Dependency.getArtifactPath(dep, dep.extension, Constants.MAVEN2_ARTIFACT_PATTERN);
		if (new File(getRootFolder(), path).exists()) {
			return path;
		}
		return "";
	}
	
	private String getLastModified(Dependency dep) {
		File file = getArtifact(dep, dep.extension);
		if (file != null && file.exists()) {
			return new SimpleDateFormat("yyyy-MM-dd").format(new Date(file.lastModified()));
		}
		return "";
	}
	
	private String getArtifactSize(Dependency dep) {
		File file = getArtifact(dep, dep.extension);
		if (file != null && file.exists()) {
			return FileUtils.formatSize(file.length());
		}
		return "";
	}
	
	public List<Pom> readAllPoms() {
		List<Pom> poms = new ArrayList<Pom>();
		poms.addAll(readAllPoms(getRootFolder()));
		return poms;
	}
	
	private List<Pom> readAllPoms(File dir) {
		List<Pom> poms = new ArrayList<Pom>();
		File [] files = dir.listFiles();
		if (files == null) {
			return poms;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				// recurse into this directory
				poms.addAll(readAllPoms(file));
			} else if (file.getName().endsWith(Constants.POM)) {
				// read this pom
				try {
					Pom pom = PomReader.readPom(this, file);
					poms.add(pom);
				} catch (Throwable t) {
					if (logger != null) {
						logger.error(t, "Failed to read POM " + file);
					}
				}
			}
		}
		return poms;
	}
}