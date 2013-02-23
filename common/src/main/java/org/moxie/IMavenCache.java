package org.moxie;

import java.io.File;
import java.util.Collection;
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
		File artifact = getArtifact(dep, dep.type);
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
}