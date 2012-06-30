package org.moxie;

import java.io.File;

public interface IMavenCache {

	File getArtifact(Dependency dep, String ext);

	File writeArtifact(Dependency dep, String ext, String content);

	File writeArtifact(Dependency dep, String ext, byte[] content);

	File getMetadata(Dependency dep, String ext);

	File writeMetadata(Dependency dep, String ext, String content);

	File writeMetadata(Dependency dep, String ext, byte[] content);

}