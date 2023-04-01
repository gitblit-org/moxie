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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.moxie.MoxieException;

public class JGitUtils {

	public static File findRepositoryDir(File dir) {
		File resolved = FileKey.resolve(dir, FS.detect());
		if (resolved != null) {
			return resolved;
		} else {
			resolved = FileKey.resolve(dir.getParentFile(), FS.detect());
			if (resolved != null) {
				return resolved;
			}
		}
		return null;
	}

	public static String getCommitId(File folder) {
		// try specified folder or subfolder
		File gitDir = FileKey.resolve(folder, FS.DETECTED);

		if (gitDir == null || !gitDir.exists()) {
			// try parent folder
			gitDir = FileKey.resolve(folder.getParentFile(), FS.DETECTED);
		}
		if (gitDir == null || !gitDir.exists()) {
			throw new MoxieException("Can not find .git folder for " + folder);
		}

		String hashid = "";
		try {
			Repository repository = new FileRepository(gitDir);
			ObjectId objectId = repository
					.resolve(org.eclipse.jgit.lib.Constants.HEAD);
			hashid = objectId.getName().toString();
			repository.close();
		} catch (IOException io) {
			io.printStackTrace();
			throw new MoxieException("IOException accessing "
					+ gitDir.getAbsolutePath(), io);
		}
		return hashid;
	}

	/**
	 * Create an orphaned branch in a repository.
	 *
	 * @param repository
	 * @param branchName
	 * @param author
	 *            if unspecified, Moxie will be the author of this new branch
	 * @return true if successful
	 */
	public static boolean createOrphanBranch(Repository repository,
			String branchName, PersonIdent author) {
		boolean success = false;
		String message = "Created branch " + branchName;
		if (author == null) {
			author = new PersonIdent("Moxie", "moxie@localhost");
		}
		try {
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create a blob object to insert into a tree
				ObjectId blobId = odi.insert(Constants.OBJ_BLOB,
						message.getBytes(Constants.CHARACTER_ENCODING));

				// Create a tree object to reference from a commit
				TreeFormatter tree = new TreeFormatter();
				tree.append("NEWBRANCH", FileMode.REGULAR_FILE, blobId);
				ObjectId treeId = odi.insert(tree);

				// Create a commit object
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage(message);
				commit.setTreeId(treeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					if (!branchName.startsWith("refs/")) {
						branchName = "refs/heads/" + branchName;
					}
					RefUpdate ru = repository.updateRef(branchName);
					ru.setNewObjectId(commitId);
					ru.setRefLogMessage(
							"commit: " + revCommit.getShortMessage(), false);
					Result rc = ru.forceUpdate();
					switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						success = true;
						break;
					default:
						success = false;
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return success;
	}

	public static void updateGhPages(File repositoryFolder, File sourceFolder, boolean obliterate)
	{
		updateGhPages(repositoryFolder, sourceFolder, obliterate, Collections.emptyList());
	}



	public static void updateGhPages(File repositoryFolder, File sourceFolder, boolean obliterate, List<String> keepFiles)
	{
		String ghpages = "refs/heads/gh-pages";
		try {
			File gitDir = FileKey.resolve(repositoryFolder, FS.DETECTED);
			Repository repository = new FileRepository(gitDir);

			ObjectId objectId = repository.resolve(ghpages);
			if (objectId == null) {
				JGitUtils.createOrphanBranch(repository, "gh-pages", null);
			}

			System.out.println("Updating gh-pages branch...");
			ObjectId headId = repository.resolve(ghpages + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the new/updated issue.
				DirCache index = createIndex(repository, headId, sourceFolder, obliterate, keepFiles);
				ObjectId indexTreeId = index.writeTree(odi);

				// Create a commit object
				PersonIdent author = new PersonIdent("Moxie",
						"moxie@localhost");
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage("updated pages");
				commit.setParentId(headId);
				commit.setTreeId(indexTreeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					RefUpdate ru = repository.updateRef(ghpages);
					ru.setNewObjectId(commitId);
					ru.setExpectedOldObjectId(headId);
					ru.setRefLogMessage(
							"commit: " + revCommit.getShortMessage(), false);
					Result rc = ru.forceUpdate();
					switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						break;
					case REJECTED:
					case LOCK_FAILURE:
						throw new ConcurrentRefUpdateException(
								JGitText.get().couldNotLockHEAD, ru.getRef(),
								rc);
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().updatingRefFailed, ghpages,
								commitId.toString(), rc));
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
			System.out.println("gh-pages updated.");
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Creates an in-memory index of the issue change.
	 *
	 * @param repo
	 * @param headId
	 * @param sourceFolder
	 * @param obliterate
	 *            if true the source folder tree is used as the new tree for
	 *            gh-pages and non-existent files are considered deleted
	 * @param keepFiles
	 * 			List of files to keep from the last tree, if obliterate is true
	 * @return an in-memory index
	 * @throws IOException
	 */
	private static DirCache createIndex(Repository repo, ObjectId headId,
			File sourceFolder, boolean obliterate, List<String> keepFiles) throws IOException {

		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder dcBuilder = inCoreIndex.builder();
		ObjectInserter inserter = repo.newObjectInserter();

		try {
			// Add all files to the temporary index
			Set<String> ignorePaths = new TreeSet<String>();
			List<File> files = listFiles(sourceFolder);
			for (File file : files) {
				// create an index entry for the file
				final DirCacheEntry dcEntry = new DirCacheEntry(
						StringUtils.getRelativePath(sourceFolder.getPath(),
								file.getPath()));
				dcEntry.setLength(file.length());
				dcEntry.setLastModified(file.lastModified());
				dcEntry.setFileMode(FileMode.REGULAR_FILE);

				// add this entry to the ignore paths set
				ignorePaths.add(dcEntry.getPathString());

				// insert object
				InputStream inputStream = new FileInputStream(file);
				try {
					dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB,
							file.length(), inputStream));
				} finally {
					inputStream.close();
				}

				// add to temporary in-core index
				dcBuilder.add(dcEntry);
			}

			if (!obliterate || (keepFiles != null && !keepFiles.isEmpty())) {
				// Traverse HEAD to add all other paths
				TreeWalk treeWalk = new TreeWalk(repo);
				int hIdx = -1;
				if (headId != null)
					hIdx = treeWalk
							.addTree(new RevWalk(repo).parseTree(headId));
				treeWalk.setRecursive(true);

				while (treeWalk.next()) {
					String path = treeWalk.getPathString();
					CanonicalTreeParser hTree = null;
					if (hIdx != -1)
						hTree = treeWalk.getTree(hIdx,
								CanonicalTreeParser.class);
					if (!ignorePaths.contains(path) &&	(!obliterate || keepFiles.contains(path))) {
						// add entries from HEAD for all other paths to keep
						if (hTree != null) {
							// create a new DirCacheEntry with data retrieved
							// from
							// HEAD
							final DirCacheEntry dcEntry = new DirCacheEntry(
									path);
							dcEntry.setObjectId(hTree.getEntryObjectId());
							dcEntry.setFileMode(hTree.getEntryFileMode());

							// add to temporary in-core index
							dcBuilder.add(dcEntry);
						}
					}
				}

				// release the treewalk
				treeWalk.release();
			}

			// finish temporary in-core index used for this commit
			dcBuilder.finish();
		} finally {
			inserter.release();
		}
		return inCoreIndex;
	}

	private static List<File> listFiles(File folder) {
		List<File> list = new ArrayList<File>();
		File [] files = folder.listFiles();
		if (files == null) {
			return list;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				list.addAll(listFiles(file));
			} else {
				list.add(file);
			}
		}
		return list;
	}

	public static String commitFiles(File dir, List<String> files, String message,
			String tagName, String tagMessage) throws IOException, GitAPIException {
		Git git = Git.open(dir);
		AddCommand add = git.add();
		for (String file : files) {
			add.addFilepattern(file);
		}
		add.call();

		// execute the commit
		CommitCommand commit = git.commit();
		commit.setMessage(message);
		RevCommit revCommit = commit.call();

		if (!StringUtils.isEmpty(tagName) && !StringUtils.isEmpty(tagMessage)) {
			// tag the commit
			TagCommand tagCommand = git.tag();
			tagCommand.setName(tagName);
			tagCommand.setMessage(tagMessage);
			tagCommand.setForceUpdate(true);
			tagCommand.setObjectId(revCommit);
			tagCommand.call();
		}
		git.getRepository().close();
		return revCommit.getId().getName();
	}
}
