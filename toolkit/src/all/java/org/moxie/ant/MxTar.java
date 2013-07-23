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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.taskdefs.Tar;
import org.moxie.Build;
import org.moxie.Toolkit.Key;
import org.moxie.utils.StringUtils;


public class MxTar extends Tar {
	
	public MxTar() {
		super();
		setTaskName("mx:tar");
	}
	
	private TarCompressionMethod mode = new TarCompressionMethod();
	
	private List<ZipDependencies> dependencies = new ArrayList<ZipDependencies>();
	
	private File destFile;
	
    @Override
    public void setTarfile(File tarFile) {
        setDestFile(tarFile);
    }

    @Override
    public void setDestFile(File destFile) {
        super.setDestFile(destFile);
        this.destFile = destFile;
    }
    
    /**
     * Set compression method.
     * Allowable values are
     * <ul>
     * <li>  none - no compression
     * <li>  gzip - Gzip compression
     * <li>  bzip2 - Bzip2 compression
     * </ul>
     * @param mode the compression method.
     */
    @Override
    public void setCompression(TarCompressionMethod mode) {
        super.setCompression(mode);
        this.mode = mode;
    }


	public ZipDependencies createDependencies() {
		ZipDependencies deps = new ZipDependencies();
		dependencies.add(deps);
		return deps;
	}

	private List<ZipArtifact> artifacts = new ArrayList<ZipArtifact>();
	
	public ZipArtifact createArtifact() {
		ZipArtifact artifact = new ZipArtifact();
		artifacts.add(artifact);
		return artifact;
	}

	public void execute() {
		Build build = (Build) getProject().getReference(Key.build.referenceId());
		if (destFile == null) {
			// default output jar if file unspecified
			String name = build.getPom().artifactId;
			if (!StringUtils.isEmpty(build.getPom().version)) {
				name += "-" + build.getPom().version;
			}
			String ext = ".tar";
			if ("none".equals(mode.getValue())) {
				ext = ".tar";
			} else if ("gzip".equals(mode.getValue())) {
				ext = ".tar.gz";
			} else if ("bzip2".equals(mode.getValue())) {
				ext = ".tar.bzip2";
			}
			destFile = new File(build.getConfig().getTargetDirectory(), name + ext);
			super.setDestFile(destFile);
		}
		
		if (destFile.getParentFile() != null) {
			destFile.getParentFile().mkdirs();
		}
		
		for (ZipArtifact artifact : artifacts) {
			TarFileSet fs = createTarFileSet();
			File file = artifact.getFile();
			if (file == null) {
				file = build.getBuildArtifact(artifact.getClassifier());
			}
			fs.setDir(file.getParentFile());
			fs.setIncludes(file.getName());
			if (!StringUtils.isEmpty(artifact.getPrefix())) {
				fs.setPrefix(artifact.getPrefix());
			}
		}
		
		for (ZipDependencies deps : dependencies) {
			for (File jar : build.getSolver().getClasspath(deps.getScope(), deps.getTag())) {
				TarFileSet fs = createTarFileSet();
				if (!StringUtils.isEmpty(deps.getPrefix())) {
					fs.setPrefix(deps.getPrefix());
				}
				fs.setDir(jar.getParentFile());
				fs.setIncludes(jar.getName());
			}
		}
		super.execute();
	}
}
