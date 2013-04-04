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
package org.moxie.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Common file utilities.
 * 
 * @author James Moger
 * 
 */
public class FileUtils {

	public final static long KILOBYTE = 1024L;
	public final static long MEGABYTE = 1024L*1024L;
	public final static long GIGABYTE = 1024L*1024L*1024L;
	
	
	/**
	 * Returns the byte content of the specified file.
	 * 
	 * @param file
	 * @return the byte content of the file
	 */
	public static byte [] readContent(File file) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream((int) file.length());
		try {
			
			FileInputStream fis = new FileInputStream(file);
			int len = 0;
			byte [] buffer = new byte[32767];
			while ((len = fis.read(buffer)) > -1) {
				bos.write(buffer, 0, len);
			}
			fis.close();
		} catch (Throwable t) {
			System.err.println("Failed to read content of "
					+ file.getAbsolutePath());
			t.printStackTrace();
		}
		return bos.toByteArray();
	}
	
	/**
	 * Returns the string content of the specified file.
	 * 
	 * @param file
	 * @param lineEnding
	 * @return the string content of the file
	 */
	public static String readContent(File file, String lineEnding) {
		StringBuilder sb = new StringBuilder();
		try {
			InputStreamReader is = new InputStreamReader(new FileInputStream(
					file), Charset.forName("UTF-8"));
			BufferedReader reader = new BufferedReader(is);
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				if (lineEnding != null) {
					sb.append(lineEnding);
				}
			}
			reader.close();
		} catch (Throwable t) {
			System.err.println("Failed to read content of "
					+ file.getAbsolutePath());
			t.printStackTrace();
		}
		return sb.toString();
	}

	/**
	 * Returns the string content of the specified file.
	 * 
	 * @param file
	 * @param lineEnding
	 * @return the string content of the file
	 */
	public static List<String> readLines(File file, String lineEnding) {
		List<String> lines = new ArrayList<String>();
		try {
			InputStreamReader is = new InputStreamReader(new FileInputStream(
					file), Charset.forName("UTF-8"));
			BufferedReader reader = new BufferedReader(is);
			String line = null;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			reader.close();
		} catch (Throwable t) {
			System.err.println("Failed to read content of "
					+ file.getAbsolutePath());
			t.printStackTrace();
		}
		return lines;
	}

	/**
	 * Writes the string content to the file.
	 * 
	 * @param file
	 * @param content
	 */
	public static void writeContent(File file, String content) {
		File tempFile = new File(file.getAbsolutePath() + "." + Long.toHexString(System.currentTimeMillis()));
		try {
			file.getAbsoluteFile().getParentFile().mkdirs();
			OutputStreamWriter os = new OutputStreamWriter(
					new FileOutputStream(tempFile), Charset.forName("UTF-8"));
			BufferedWriter writer = new BufferedWriter(os);
			writer.append(content);
			writer.close();
			if (file.exists()) {				
				if (!file.delete()) {
					System.err.println("Failed to delete " + file);	
				}
			}
			if (!tempFile.renameTo(file)) {
				System.err.println("Failed to rename temp file to " + file);	
			}
		} catch (Throwable t) {
			System.err.println("Failed to write content of " + file);
			t.printStackTrace();
		}
	}
	
	/**
	 * Writes the string content to the file.
	 * 
	 * @param file
	 * @param content
	 */
	public static void writeContent(File file, byte [] data) {
		File tempFile = new File(file.getAbsolutePath() + "." + Long.toHexString(System.currentTimeMillis()));
		try {
			file.getAbsoluteFile().getParentFile().mkdirs();
			FileOutputStream os = new FileOutputStream(tempFile);
			os.write(data);			
			os.close();
			if (file.exists()) {				
				file.delete();
			}
			tempFile.renameTo(file);
		} catch (IOException e) {
			throw new RuntimeException("Error writing to file " + file, e);
		}
	}

	/**
	 * Recursively traverses a folder and its subfolders to calculate the total
	 * size in bytes.
	 * 
	 * @param directory
	 * @return folder size in bytes
	 */
	public static long folderSize(File directory) {
		if (directory == null || !directory.exists()) {
			return -1;
		}
		if (directory.isFile()) {
			return directory.length();
		}
		long length = 0;
		File [] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isFile()) {
					length += file.length();
				} else {
					length += folderSize(file);
				}
			}
		}
		return length;
	}

	/**
	 * Copies a file or folder (recursively) to a destination folder.
	 * 
	 * @param destinationFolder
	 * @param filesOrFolders
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void copy(File destinationFolder, File... filesOrFolders)
			throws FileNotFoundException, IOException {
		destinationFolder.mkdirs();
		if (filesOrFolders == null) {
			return;
		}
		for (File file : filesOrFolders) {
			if (file.isDirectory()) {
				copy(new File(destinationFolder, file.getName()),
						file.listFiles());
			} else if (file.exists()) {
				File dFile = new File(destinationFolder, file.getName());
				BufferedInputStream bufin = null;
				FileOutputStream fos = null;
				try {
					bufin = new BufferedInputStream(new FileInputStream(file));
					fos = new FileOutputStream(dFile);
					int len = 8196;
					byte[] buff = new byte[len];
					int n = 0;
					while ((n = bufin.read(buff, 0, len)) != -1) {
						fos.write(buff, 0, n);
					}
				} finally {
					try {
						bufin.close();
					} catch (Throwable t) {
					}
					try {
						fos.close();
					} catch (Throwable t) {
					}
				}
				dFile.setLastModified(file.lastModified());
			}
		}
	}
	
	/**
	 * Copies a file to another file.
	 * 
	 * @param fromFile
	 * @param toFile
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void copyFile(File fromFile, File toFile)
			throws FileNotFoundException, IOException {
		toFile.getParentFile().mkdirs();
		BufferedInputStream bufin = null;
		FileOutputStream fos = null;
		try {
			bufin = new BufferedInputStream(new FileInputStream(fromFile));
			fos = new FileOutputStream(toFile);
			int len = 8196;
			byte[] buff = new byte[len];
			int n = 0;
			while ((n = bufin.read(buff, 0, len)) != -1) {
				fos.write(buff, 0, n);
			}
		} finally {
			try {
				bufin.close();
			} catch (Throwable t) {
			}
			try {
				fos.close();
			} catch (Throwable t) {
			}
		}
		toFile.setLastModified(fromFile.lastModified());
	}

	/**
	 * Delete a file or recursively delete a folder.
	 * 
	 * @param fileOrFolder
	 * @return true, if successful
	 */
	public static boolean delete(File fileOrFolder) {
		boolean success = false;
		if (fileOrFolder.isDirectory()) {
			File [] files = fileOrFolder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						success |= delete(file);
					} else {
						success |= file.delete();
					}
				}
			}
		}
		success |= fileOrFolder.delete();
		return success;
	}
	
	/**
	 * Java on Linux may only have second resolution
	 * 
	 * @param file
	 * @return lastModified rounded to seconds 
	 */
	public static long getLastModified(File file) {
		if (file.exists()) {
			return (file.lastModified()/1000L)*1000L;
		}
		return System.currentTimeMillis();
	}
	
	/**
	 * Determine the relative path between two files.  Takes into account
	 * canonical paths, if possible.
	 * 
	 * @param basePath
	 * @param path
	 * @return a relative path from basePath to path
	 */
	public static String getRelativePath(File basePath, File path) {
		File exactBase = getExactFile(basePath);
		File exactPath = getExactFile(path);
		if (path.getAbsolutePath().startsWith(basePath.getAbsolutePath())) {
			// absolute base-path match
			return StringUtils.getRelativePath(basePath.getAbsolutePath(), path.getAbsolutePath());
		} else if (exactPath.getPath().startsWith(exactBase.getPath())) {
			// canonical base-path match
			return StringUtils.getRelativePath(exactBase.getPath(), exactPath.getPath());
		} else if (exactPath.getPath().startsWith(basePath.getAbsolutePath())) {
			// mixed path match
			return StringUtils.getRelativePath(basePath.getAbsolutePath(), exactPath.getPath());
		} else if (path.getAbsolutePath().startsWith(exactBase.getPath())) {
			// mixed path match
			return StringUtils.getRelativePath(exactBase.getPath(), path.getAbsolutePath());
		}
		// no relative relationship
		return null;
	}
	
	/**
	 * Returns the exact path for a file. This path will be the canonical path
	 * unless an exception is thrown in which case it will be the absolute path.
	 * 
	 * @param path
	 * @return the exact file
	 */
	public static File getExactFile(File path) {
		try {
			return path.getCanonicalFile();
		} catch (IOException e) {
			return path.getAbsoluteFile();
		}
	}
	
	/**
	 * Formats a file size as a human-readable string.
	 * @param size
	 * @return human-readable file size
	 */
	public static String formatSize(long size) {
		if (size < 1024) {
			return size + " bytes";
		}
		double sz = size;
		String units;
		double nsz;
		String format = "0";
		if (size >= GIGABYTE) {
			nsz = sz/GIGABYTE;
			units = "GB";
			format = "0.0";
		} else if (size >= MEGABYTE) {
			nsz = sz/MEGABYTE;
			units = "MB";
			format = "0.0";
		} else {
			nsz = sz/KILOBYTE;
			units = "KB";
		}
		return new DecimalFormat(format).format(nsz) + " " + units;
	}
}
