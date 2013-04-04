/*
 * Copyright 2013 James Moger
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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.moxie.Build;

/**
 * Generates thumbnails from all PNG images in the source folder and saves
 * them to the destination folder.
 */
public class MxThumbs extends MxTask {
	
	File sourceDirectory;
	
	int maxDimension;
	
	File destinationDirectory;
	
	String input = "png";
	
	String output = "png";
	
	public MxThumbs() {
		super();
		setTaskName("mx:thumbnailer");
	}
	
	public void setMaximumDimension(int max) {
		this.maxDimension = max;
	}
	
	public void setSourcedir(File dir) {
		this.sourceDirectory = dir;
	}

	public void setDestdir(File dir) {
		this.destinationDirectory = dir;
	}

	public void setTodir(File dir) {
		this.destinationDirectory = dir;
	}

	public void setInput(String type) {
		this.input = type;
	}

	public void setOutput(String type) {
		this.output = type;
	}

	public void execute() {
		Build build = getBuild();
		
		if (destinationDirectory == null) {
			getConsole().error("Please specify a destination directory!");
			throw new RuntimeException();
		}

		if (maxDimension == 0) {
			getConsole().error("Please specify a maximum dimension!");
			throw new RuntimeException();
		}
		
		if (sourceDirectory == null) {
			getConsole().error("Please specify an source directory!");
			throw new RuntimeException();
		}

		destinationDirectory.mkdirs();
		File[] sourceFiles = sourceDirectory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith("." + input);
			}
		});
		
		if (sourceFiles != null) {
			build.getConsole().log("Generating {0} {1}", sourceFiles.length, sourceFiles.length == 1 ? "thumbnail" : "thumbnails");
			for (File sourceFile : sourceFiles) {
				String name = sourceFile.getName();
				name = name.substring(0, name.lastIndexOf('.') + 1) + output;
				File destinationFile = new File(destinationDirectory, name);
				try {
					Dimension sz = getImageDimensions(sourceFile);
					int w = 0;
					int h = 0;
					if (sz.width > maxDimension) {
						// Scale to Width
						w = maxDimension;
						float f = maxDimension;
						// normalize height
						h = (int) ((f / sz.width) * sz.height);
					} else if (sz.height > maxDimension) {
						// Scale to Height
						h = maxDimension;
						float f = maxDimension;
						// normalize width
						w = (int) ((f / sz.height) * sz.width);
					}
					build.getConsole().debug(1, "thumbnail for {0} as ({1,number,#}, {2,number,#})",
							sourceFile.getName(), w, h);
					BufferedImage image = ImageIO.read(sourceFile);
					Image scaledImage = image.getScaledInstance(w, h, BufferedImage.SCALE_SMOOTH);
					BufferedImage destImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
					destImage.createGraphics().drawImage(scaledImage, 0, 0, null);
					FileOutputStream fos = new FileOutputStream(destinationFile);
					ImageIO.write(destImage, output, fos);
					fos.flush();
					fos.getFD().sync();
					fos.close();
				} catch (Throwable t) {
					build.getConsole().error(t, "failed to generate thumbnail for {0}", sourceFile);
				}
			}
		}
	}

	/**
	 * Return the dimensions of the specified image file.
	 * 
	 * @param file
	 * @return dimensions of the image
	 * @throws IOException
	 */
	Dimension getImageDimensions(File file) throws IOException {
		ImageInputStream in = ImageIO.createImageInputStream(file);
		try {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					reader.setInput(in);
					return new Dimension(reader.getWidth(0), reader.getHeight(0));
				} finally {
					reader.dispose();
				}
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
		return null;
	}
}
