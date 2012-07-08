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
package org.moxie.proxy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.moxie.IMavenCache;
import org.moxie.Pom;
import org.moxie.PomReader;
import org.moxie.RemoteRepository;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;

/**
 * The Lucene executor handles indexing and searching POM files.
 * 
 * @author James Moger
 * 
 */
public class LuceneExecutor implements Runnable {
	
		
	private static final int INDEX_VERSION = 1;

	private static final String FIELD_PACKAGING = "type";
	private static final String FIELD_GROUPID = "groupid";
	private static final String FIELD_ARTIFACTID = "artifactid";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_NAME = "name";
	private static final String FIELD_DESCRIPTION = "description";
	private static final String FIELD_DATE = "date";

	private static final String LUCENE_DIR = "lucene";
	private static final String CONF_VERSION = "version";
		
	private static final Version LUCENE_VERSION = Version.LUCENE_35;
	
	private final Logger logger = Logger.getLogger(LuceneExecutor.class.getSimpleName());
	
	private final ProxyConfig config;
	private final File indexesFolder;
	
	private final Map<String, IndexSearcher> searchers = new ConcurrentHashMap<String, IndexSearcher>();
	private final Map<String, IndexWriter> writers = new ConcurrentHashMap<String, IndexWriter>();
	
	public LuceneExecutor(ProxyConfig config) {
		this.config = config;
		this.indexesFolder = new File(config.getMoxieRoot(), LUCENE_DIR);
	}

	/**
	 * Run is executed by the Moxie Proxy executor service.  Because this is called 
	 * by an executor service, calls will queue - i.e. there can never be
	 * concurrent execution of repository index updates.
	 */
	@Override
	public void run() {
		for (String repository : config.getLocalRepositories()) {
			index(repository);				
		}
		for (RemoteRepository repository : config.getRemoteRepositories()) {
			index(repository.id);
		}
		System.gc();
	}

	/**
	 * Reads the Lucene config file for the repository to check the index
	 * version. If the index version is different, then rebuild the repository
	 * index.
	 * 
	 * @param repository
	 * @return true of the on-disk index format is different than INDEX_VERSION
	 */
	private boolean shouldReindex(String repository) {
		try {
			File folder = new File(indexesFolder, LUCENE_DIR);
			File file = new File(folder, "config.properties");
			Properties props = new Properties();
			props.load(new FileReader(file));
			int indexVersion = Integer.parseInt(props.getProperty(CONF_VERSION, "0"));
			// reindex if versions do not match
			return indexVersion != INDEX_VERSION;
		} catch (Throwable t) {
		}
		return true;
	}

	/**
	 * Synchronously indexes a repository. This may build a complete index of a
	 * repository or it may update an existing index.
	 * 
	 * @param name
	 *            the name of the repository
	 * @param repository
	 *            the repository object
	 */
	private void index(String repository) {
		try {
			if (shouldReindex(repository)) {
				// (re)build the entire index
				IndexResult result = reindex(repository);

				if (result.success) {
					if (result.artifactCount > 0) {
						String msg = "Built {0} Lucene index from {1} artifacts in {2} secs";
						logger.info(MessageFormat.format(msg, repository, result.artifactCount, result.duration()));
					}
				} else {
					String msg = "Could not build {0} Lucene index!";
					logger.severe(MessageFormat.format(msg, repository));
				}
			} else {
				// update the index with latest artifacts
				IndexResult result = updateIndex(repository);
				if (result.success) {
					if (result.artifactCount > 0) {
						String msg = "Updated {0} Lucene index with {1} artifacts in {42 secs";
						logger.info(MessageFormat.format(msg, repository, result.artifactCount, result.duration()));
					}
				} else {
					String msg = "Could not update {0} Lucene index!";
					logger.severe(MessageFormat.format(msg, repository));
				}
			}
		} catch (Throwable t) {
			logger.log(Level.SEVERE, MessageFormat.format("Lucene indexing failure for {0}", repository), t);
		}
	}

	/**
	 * Updates a repository index incrementally from the last indexed artifacts.
	 * 
	 * @param repository
	 * @return IndexResult
	 */
	private IndexResult updateIndex(String repository) {
		IndexResult result = new IndexResult();
		return result;
	}
	
	/**
	 * Close the writer/searcher objects for a repository.
	 * 
	 * @param repositoryName
	 */
	public synchronized void close(String repositoryName) {
		try {
			IndexSearcher searcher = searchers.remove(repositoryName);
			if (searcher != null) {
				searcher.getIndexReader().close();
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to close index searcher for " + repositoryName, e);
		}
		
		try {
			IndexWriter writer = writers.remove(repositoryName);
			if (writer != null) {
				writer.close();
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to close index writer for " + repositoryName, e);
		}		
	}

	/**
	 * Close all Lucene indexers.
	 * 
	 */
	public synchronized void close() {
		// close all writers
		for (String writer : writers.keySet()) {
			try {
				writers.get(writer).close(true);
			} catch (Throwable t) {
				logger.log(Level.SEVERE, "Failed to close Lucene writer for " + writer, t);
			}
		}
		writers.clear();

		// close all searchers
		for (String searcher : searchers.keySet()) {
			try {
				searchers.get(searcher).getIndexReader().close();
			} catch (Throwable t) {
				logger.log(Level.SEVERE, "Failed to close Lucene searcher for " + searcher, t);
			}
		}
		searchers.clear();
	}

	
	/**
	 * Deletes the Lucene index for the specified repository.
	 * 
	 * @param repositoryName
	 * @return true, if successful
	 */
	public boolean deleteIndex(String repositoryName) {
		// close any open writer/searcher
		close(repositoryName);

		// delete the index folder
		File luceneIndex = new File(indexesFolder, repositoryName);
		if (luceneIndex.exists()) {
			FileUtils.delete(luceneIndex);
		}
		return true;
	}


	/**
	 * This completely indexes the repository and will destroy any existing
	 * index.
	 * 
	 * @param repositoryName
	 * @return IndexResult
	 */
	public IndexResult reindex(String repository) {
		IndexResult result = new IndexResult();		
		if (!deleteIndex(repository)) {
			return result;
		}
		try {
			IMavenCache cache = config.getMavenCache(repository);
			Collection<File> files = cache.getFiles("." + org.moxie.Constants.POM);
			IndexWriter writer = getIndexWriter(repository);

			for (File pomFile : files) {
				Pom pom = PomReader.readPom(cache, pomFile);
				String date = DateTools.timeToString(pomFile.lastModified(), Resolution.MINUTE);

				Document doc = new Document();
				doc.add(new Field(FIELD_PACKAGING, pom.packaging, Store.YES, Index.NOT_ANALYZED_NO_NORMS));
				doc.add(new Field(FIELD_GROUPID, pom.groupId, Store.YES, Index.ANALYZED));
				doc.add(new Field(FIELD_ARTIFACTID, pom.artifactId, Store.YES, Index.ANALYZED));
				doc.add(new Field(FIELD_VERSION, pom.version, Store.YES, Index.ANALYZED));
				if (!StringUtils.isEmpty(pom.name)) {
					doc.add(new Field(FIELD_NAME, pom.name, Store.YES, Index.ANALYZED));
				}
				if (!StringUtils.isEmpty(pom.description)) {
					doc.add(new Field(FIELD_DESCRIPTION, pom.description, Store.YES, Index.ANALYZED));
				}
				doc.add(new Field(FIELD_DATE, date, Store.YES, Index.ANALYZED));
				
				// add the pom to the index
				writer.addDocument(doc);
				
				result.artifactCount++;
			}
			
			writer.commit();
			resetIndexSearcher(repository);
			result.success();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Exception while reindexing " + repository, e);
		}
		return result;
	}
	
	/**
	 * Incrementally index an object for the repository.
	 * 
	 * @param repositoryName
	 * @param doc
	 * @return true, if successful
	 */
	private boolean index(String repository, Document doc) {
		try {			
			IndexWriter writer = getIndexWriter(repository);
			writer.addDocument(doc);
			writer.commit();
			resetIndexSearcher(repository);
			return true;
		} catch (Exception e) {
			logger.log(Level.SEVERE, MessageFormat.format("Exception while incrementally updating {0} Lucene index", repository), e);
		}
		return false;
	}

	private SearchResult createSearchResult(Document doc, int hitId, int totalHits) throws ParseException {
		SearchResult result = new SearchResult();
		result.hitId = hitId;
		result.totalHits = totalHits;
		result.date = DateTools.stringToDate(doc.get(FIELD_DATE));
		result.groupId = doc.get(FIELD_GROUPID);		
		result.artifactId = doc.get(FIELD_ARTIFACTID);
		result.version = doc.get(FIELD_VERSION);
		result.packaging = doc.get(FIELD_PACKAGING);
		result.name = doc.get(FIELD_NAME);
		result.description = doc.get(FIELD_DESCRIPTION);
		return result;
	}

	private synchronized void resetIndexSearcher(String repository) throws IOException {
		IndexSearcher searcher = searchers.remove(repository);
		if (searcher != null) {
			searcher.getIndexReader().close();
		}
	}

	/**
	 * Gets an index searcher for the repository.
	 * 
	 * @param repository
	 * @return
	 * @throws IOException
	 */
	private IndexSearcher getIndexSearcher(String repository) throws IOException {
		IndexSearcher searcher = searchers.get(repository);
		if (searcher == null) {
			IndexWriter writer = getIndexWriter(repository);
			searcher = new IndexSearcher(IndexReader.open(writer, true));
			searchers.put(repository, searcher);
		}
		return searcher;
	}

	/**
	 * Gets an index writer for the repository. The index will be created if it
	 * does not already exist or if forceCreate is specified.
	 * 
	 * @param repository
	 * @return an IndexWriter
	 * @throws IOException
	 */
	private IndexWriter getIndexWriter(String repository) throws IOException {
		IndexWriter indexWriter = writers.get(repository);				
		File indexFolder = new File(indexesFolder, repository);
		Directory directory = FSDirectory.open(indexFolder);		

		if (indexWriter == null) {
			if (!indexFolder.exists()) {
				indexFolder.mkdirs();
			}
			StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
			IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
			config.setOpenMode(OpenMode.CREATE_OR_APPEND);
			indexWriter = new IndexWriter(directory, config);
			writers.put(repository, indexWriter);
		}
		return indexWriter;
	}

	/**
	 * Searches the specified repositories for the given text or query
	 * 
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param page
	 *            the page number to retrieve. page is 1-indexed.
	 * @param pageSize
	 *            the number of elements to return for this page
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 * 
	 */
	public List<SearchResult> search(String text, int page, int pageSize, List<String> repositories) {
		if (repositories == null || repositories.size() == 0) {
			return null;
		}
		return search(text, page, pageSize, repositories.toArray(new String[0]));
	}
	
	/**
	 * Searches the specified repositories for the given text or query
	 * 
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param page
	 *            the page number to retrieve. page is 1-indexed.
	 * @param pageSize
	 *            the number of elements to return for this page
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 * 
	 */
	public List<SearchResult> search(String text, int page, int pageSize, String... repositories) {
		if (StringUtils.isEmpty(text)) {
			return null;
		}
		if (repositories == null || repositories.length == 0) {
			return null;
		}
		Set<SearchResult> results = new LinkedHashSet<SearchResult>();
		StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
		try {
			// default search checks groupId and artifactId
			BooleanQuery query = new BooleanQuery();
			QueryParser qp;
			qp = new QueryParser(LUCENE_VERSION, FIELD_GROUPID, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			qp = new QueryParser(LUCENE_VERSION, FIELD_ARTIFACTID, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			IndexSearcher searcher;
			if (repositories.length == 1) {
				// single repository search
				searcher = getIndexSearcher(repositories[0]);
			} else {
				// multiple repository search
				List<IndexReader> readers = new ArrayList<IndexReader>();
				for (String repository : repositories) {
					IndexSearcher repositoryIndex = getIndexSearcher(repository);
					readers.add(repositoryIndex.getIndexReader());
				}
				IndexReader[] rdrs = readers.toArray(new IndexReader[readers.size()]);
				MultiSourceReader reader = new MultiSourceReader(rdrs);
				searcher = new IndexSearcher(reader);
			}
			Query rewrittenQuery = searcher.rewrite(query);
			Sort sort = new Sort(new SortField(FIELD_DATE, SortField.STRING, true));
			TopFieldDocs topDocs = searcher.search(rewrittenQuery, 10000, sort);
			int offset = Math.max(0, (page - 1) * pageSize);
			ScoreDoc[] hits = topDocs.scoreDocs;
			int totalHits = topDocs.totalHits;
			if (totalHits > offset) {
				for (int i = offset, len = Math.min(offset + pageSize, hits.length); i < len; i++) {
					int docId = hits[i].doc;
					Document doc = searcher.doc(docId);
					SearchResult result = createSearchResult(doc, i + 1, totalHits);
					if (repositories.length == 1) {
						// single repository search
						result.repository = repositories[0];
					} else {
						// multi-repository search
						MultiSourceReader reader = (MultiSourceReader) searcher.getIndexReader();
						int index = reader.getSourceIndex(docId);
						result.repository = repositories[index];
					}
					results.add(result);
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, MessageFormat.format("Exception while searching for {0}", text), e);
		}
		return new ArrayList<SearchResult>(results);
	}
	
	/**
	 * Simple class to track the results of an index update. 
	 */
	private class IndexResult {
		long startTime = System.currentTimeMillis();
		long endTime = startTime;
		boolean success;
		int artifactCount;
		
		void add(IndexResult result) {
			this.artifactCount += result.artifactCount;			
		}
		
		void success() {
			success = true;
			endTime = System.currentTimeMillis();
		}
		
		float duration() {
			return (endTime - startTime)/1000f;
		}
	}
	
	/**
	 * Custom subclass of MultiReader to identify the source index for a given
	 * doc id.  This would not be necessary of there was a public method to
	 * obtain this information.
	 *  
	 */
	private class MultiSourceReader extends MultiReader {
		
		final Method method;
		
		MultiSourceReader(IndexReader[] subReaders) {
			super(subReaders);
			Method m = null;
			try {
				m = MultiReader.class.getDeclaredMethod("readerIndex", int.class);
				m.setAccessible(true);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error getting readerIndex method", e);
			}
			method = m;
		}
		
		int getSourceIndex(int docId) {
			int index = -1;
			try {
				Object o = method.invoke(this, docId);
				index = (Integer) o;
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error getting source index", e);
			}
			return index;
		}
	}
}
