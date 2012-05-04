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
package com.maxtk.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Common file utilities.
 * 
 * @author James Moger
 * 
 */
public class FileUtils {

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
		try {
			file.getAbsoluteFile().getParentFile().mkdirs();
			OutputStreamWriter os = new OutputStreamWriter(
					new FileOutputStream(file), Charset.forName("UTF-8"));
			BufferedWriter writer = new BufferedWriter(os);
			writer.append(content);
			writer.close();
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
		try {
			file.getAbsoluteFile().getParentFile().mkdirs();
			RandomAccessFile ra = new RandomAccessFile(file, "rw");
			ra.write(data);
			ra.setLength(data.length);
			ra.close();			
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
		for (File file : directory.listFiles()) {
			if (file.isFile()) {
				length += file.length();
			} else {
				length += folderSize(file);
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
		for (File file : filesOrFolders) {
			if (file.isDirectory()) {
				copy(new File(destinationFolder, file.getName()),
						file.listFiles());
			} else {
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
	 * Delete a file or recursively delete a folder.
	 * 
	 * @param fileOrFolder
	 * @return true, if successful
	 */
	public static boolean delete(File fileOrFolder) {
		boolean success = false;
		if (fileOrFolder.isDirectory()) {
			for (File file : fileOrFolder.listFiles()) {
				if (file.isDirectory()) {
					success |= delete(file);
				} else {
					success |= file.delete();
				}
			}
		}
		success |= fileOrFolder.delete();
		return success;
	}
}
