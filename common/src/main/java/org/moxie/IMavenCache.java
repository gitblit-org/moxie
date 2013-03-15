package org.moxie;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;

public abstract class IMavenCache {
	
	public abstract File getRootFolder();
	
	public abstract Collection<File> getFiles(String extension);

	public abstract File getArtifact(Dependency dep, String ext);

	public abstract File writeArtifact(Dependency dep, String ext, String content);

	public abstract File writeArtifact(Dependency dep, String ext, byte[] content);

	public abstract File getMetadata(Dependency dep, String ext);

	public abstract File writeMetadata(Dependency dep, String ext, String content);

	public abstract File writeMetadata(Dependency dep, String ext, byte[] content);
	
	protected abstract Dependency resolveRevision(Dependency dependency);
	
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
			System.out.println("purging old snapshots of " + dep.getCoordinates());
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
			System.out.println("   ! skipping non existent folder " + folder);
			return;
		}
		
		for (File file : folder.listFiles()) {
			if (file.isFile() && file.getName().contains(identifier)) {
				System.out.println("   - " + file.getName());
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
			artifact = artifact.replace("${artifact.name}", pom.getName());
			artifact = artifact.replace("${artifact.description}", pom.getDescription());
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
	
	private String getMavenPath(Dependency dep) {
		String path = Dependency.getMavenPath(dep, dep.extension, Constants.MAVEN2_PATTERN);
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
	
	private List<Pom> readAllPoms(File folder) {
		List<Pom> poms = new ArrayList<Pom>();
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				// recurse into this directory
				poms.addAll(readAllPoms(file));
			} else if (file.getName().endsWith(Constants.POM)) {
				// read this pom
				try {
					Pom pom = PomReader.readPom(this, file);
					poms.add(pom);
				} catch (Throwable t) {
					System.err.println("Failed to read POM " + file);
					t.printStackTrace(System.err);
				}
			}
		}
		return poms;
	}
}