package org.moxie.proxy;

import java.io.Serializable;
import java.util.Date;

/**
 * Model class that represents a search result.
 * 
 * @author James Moger
 * 
 */
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public int hitId;
	
	public int totalHits;

	public float score;

	public Date date;

	public String groupId;

	public String artifactId;

	public String version;
	
	public String name;
	
	public String description;

	public String packaging;
	
	public String repository;

	public SearchResult() {
	}
	
	public String getRepository() {
		return repository;
	}
	
	public String getPath() {
		return repository + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version;
	}

	public String getCoordinates() {
		return groupId + ":" + artifactId + ":" + version;
	}
	
	public Date getDate() {
		return date;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}

	@Override
	public String toString() {
		return  score + ":" + groupId + ":" + artifactId + ":" + version  + ":" + packaging;
	}
}